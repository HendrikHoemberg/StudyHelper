package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonMinimapBuilderTests {

    private final DungeonMapGenerator mapGenerator = new DungeonMapGenerator();

    private DungeonSessionState stateWith(List<RelicId> relics) {
        List<String> normalIds = IntStream.range(0, DungeonSize.SMALL.normalEncounterCount())
            .mapToObj(i -> "enc_" + i).toList();
        DungeonMap map = mapGenerator.generate(
            DungeonSize.SMALL, normalIds, List.of(List.of("e1_0", "e1_1")));
        return DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
                List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map).currentRoomId(map.entranceRoomId())
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .loadout(new DungeonLoadout(relics, null))
            .build();
    }

    @Test
    void mapSenseRevealsEveryRoom() {
        DungeonSessionState state = stateWith(List.of(RelicId.MAP_SENSE));
        Set<String> revealed = DungeonMinimapBuilder.revealedRoomIds(state);
        assertThat(revealed).containsExactlyInAnyOrderElementsOf(state.map().rooms().keySet());
    }

    @Test
    void withoutRelicsOnlyTheCurrentRoomIsRevealed() {
        DungeonSessionState state = stateWith(List.of());
        Set<String> revealed = DungeonMinimapBuilder.revealedRoomIds(state);
        assertThat(revealed).contains(state.currentRoomId());
        String farRoom = state.map().rooms().keySet().stream()
            .filter(id -> !id.equals(state.currentRoomId()))
            .filter(id -> !state.currentRoom().doors().containsValue(id))
            .findFirst().orElseThrow();
        assertThat(revealed).doesNotContain(farRoom);
    }
}
