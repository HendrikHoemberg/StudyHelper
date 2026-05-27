package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonSessionStateTests {

    @Test
    void newState_storesAllFieldsAndComputesActiveEncounterNull() {
        DungeonRoom entrance = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0), true, true,
            null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", entrance), "r0", "r0", 7);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");

        DungeonSessionState state = new DungeonSessionState(
            config, map, "r0",
            Map.of(), List.of(), 0, null,
            5, 5, 0, 2,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);

        assertThat(state.isComplete()).isFalse();
        assertThat(state.activeEncounter()).isNull();
        assertThat(state.currentRoom()).isEqualTo(entrance);
        assertThat(state.ownedRelics()).isEmpty();
    }

    @Test
    void isComplete_trueWhenWonOrDefeated() {
        DungeonRoom entrance = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0), true, true,
            null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", entrance), "r0", "r0", 7);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");

        DungeonSessionState won = new DungeonSessionState(
            config, map, "r0", Map.of(), List.of(), 0, null,
            5, 5, 0, 2, 0, 0, 0, true, false,
            0, List.of(), 0, 0, 0, 0, List.of(), null);
        DungeonSessionState lost = new DungeonSessionState(
            config, map, "r0", Map.of(), List.of(), 0, null,
            0, 5, 0, 2, 0, 0, 0, false, true,
            0, List.of(), 0, 0, 0, 0, List.of(), null);

        assertThat(won.isComplete()).isTrue();
        assertThat(lost.isComplete()).isTrue();
    }
}
