package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonCollectWithIdsTests {

    @Test
    void collectCoin_addsToCollectedSet() {
        DungeonSessionState state = baseState();
        Set<String> collected = new HashSet<>(state.collectedItems());
        collected.add("r0_coin_0");
        DungeonSessionState next = state.withCollectedItems(collected)
            .withResources(state.resources().addScore(1));
        assertThat(next.collectedItems()).contains("r0_coin_0");
    }

    @Test
    void collectCoin_rejectsDuplicateItem() {
        DungeonSessionState state = baseState();
        Set<String> collected = new HashSet<>(state.collectedItems());
        collected.add("r0_coin_0");
        DungeonSessionState afterFirst = state
            .withCollectedItems(collected)
            .withResources(state.resources().addScore(1));

        Set<String> secondAttempt = new HashSet<>(afterFirst.collectedItems());
        boolean isNew = secondAttempt.add("r0_coin_0");
        assertThat(isNew).isFalse();
    }

    private static DungeonSessionState baseState() {
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
            .loadout(new DungeonLoadout(List.of(), null))
            .build();
    }
}
