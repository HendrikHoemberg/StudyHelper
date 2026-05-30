package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonViewModelBuilderTests {

    private final DungeonMapGenerator mapGenerator = new DungeonMapGenerator();

    private DungeonSessionState base() {
        List<String> normalIds = IntStream.range(0, DungeonSize.SMALL.normalEncounterCount())
            .mapToObj(i -> "enc_" + i).toList();
        DungeonMap map = mapGenerator.generate(
            DungeonSize.SMALL, normalIds, List.of(List.of("e1_0", "e1_1")));
        return DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
                List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map).currentRoomId(map.entranceRoomId())
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .loadout(DungeonLoadout.empty())
            .build();
    }

    private Map.Entry<DungeonDirection, String> firstDoor(DungeonSessionState s) {
        return s.currentRoom().doors().entrySet().iterator().next();
    }

    @Test
    void revenantInAdjacentRevealedRoomYieldsItsDirection() {
        DungeonSessionState s = base();
        Map.Entry<DungeonDirection, String> door = firstDoor(s);
        DungeonSessionState withRev = s.withRevenants(
            List.of(new Revenant("enc_0", door.getValue(), door.getValue())));
        Set<String> revealed = Set.of(door.getValue());
        List<String> dirs = DungeonViewModelBuilder.revenantDoors(withRev, revealed);
        assertThat(dirs).containsExactly(door.getKey().name());
    }

    @Test
    void revenantInFoggedAdjacentRoomIsHidden() {
        DungeonSessionState s = base();
        Map.Entry<DungeonDirection, String> door = firstDoor(s);
        DungeonSessionState withRev = s.withRevenants(
            List.of(new Revenant("enc_0", door.getValue(), door.getValue())));
        List<String> dirs = DungeonViewModelBuilder.revenantDoors(withRev, Set.of());
        assertThat(dirs).isEmpty();
    }

    @Test
    void nonAdjacentRevenantYieldsNoDoor() {
        DungeonSessionState s = base();
        String farRoom = s.map().rooms().keySet().stream()
            .filter(id -> !id.equals(s.currentRoomId()))
            .filter(id -> !s.currentRoom().doors().containsValue(id))
            .findFirst().orElseThrow();
        DungeonSessionState withRev = s.withRevenants(
            List.of(new Revenant("enc_0", farRoom, farRoom)));
        List<String> dirs = DungeonViewModelBuilder.revenantDoors(withRev, Set.of(farRoom));
        assertThat(dirs).isEmpty();
    }
}
