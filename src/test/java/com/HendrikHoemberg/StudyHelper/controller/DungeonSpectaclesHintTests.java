package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.service.DungeonViewModelBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonSpectaclesHintTests {

    @Test
    void spectaclesHintMaskIndex_returnsAWrongOptionIndexWhenRelicOwned() {
        QuizQuestion q = new QuizQuestion(
            QuestionType.SINGLE_CHOICE, "q?", List.of("A", "B", "C", "D"), List.of(1));
        DungeonEncounter enc = DungeonEncounter.quiz("q_42", false, "SLIME", q);
        DungeonSessionState state = stateWithRelics(List.of(RelicId.SPECTACLES));
        Integer mask = DungeonViewModelBuilder.spectaclesHintMaskIndex(state, enc);
        assertThat(mask).isIn(0, 2, 3);
    }

    @Test
    void spectaclesHintMaskIndex_isNullWhenRelicNotOwned() {
        QuizQuestion q = new QuizQuestion(QuestionType.SINGLE_CHOICE, "q?", List.of("A", "B"), List.of(0));
        DungeonEncounter enc = DungeonEncounter.quiz("q_1", false, "SLIME", q);
        DungeonSessionState state = stateWithRelics(List.of());
        assertThat(DungeonViewModelBuilder.spectaclesHintMaskIndex(state, enc)).isNull();
    }

    private DungeonSessionState stateWithRelics(List<RelicId> relics) {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0), "r0", "r0", 3);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return DungeonSessionState.builder()
            .config(config)
            .map(map)
            .currentRoomId("r0")
            .encounters(Map.of())
            .bossEncounterIds(List.of())
            .combat(DungeonCombat.empty())
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .progress(new DungeonProgress(0, 0, 0, 0, 0, 0, 0))
            .loadout(new DungeonLoadout(relics, null))
            .build();
    }
}
