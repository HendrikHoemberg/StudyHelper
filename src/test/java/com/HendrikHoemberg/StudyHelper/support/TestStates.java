package com.HendrikHoemberg.StudyHelper.support;

import com.HendrikHoemberg.StudyHelper.dto.*;

import java.util.List;
import java.util.Map;

public final class TestStates {

    private TestStates() {}

    public static final int DEFAULT_HEALTH = 5;
    public static final int DEFAULT_HEALTH_CAP = 5;
    public static final int DEFAULT_SHIELD_CAP = 2;

    public static DungeonSessionState minimal() {
        return baseBuilder().build();
    }

    public static DungeonSessionState minimalWithHealth(int health) {
        return baseBuilder()
            .resources(new DungeonResources(health, DEFAULT_HEALTH_CAP, 0, DEFAULT_SHIELD_CAP, 0))
            .build();
    }

    public static DungeonSessionState minimalWithRelics(List<RelicId> relics) {
        return baseBuilder()
            .loadout(new DungeonLoadout(relics, null))
            .build();
    }

    public static DungeonSessionState minimalWithShields(int shields, int shieldCap) {
        return baseBuilder()
            .resources(new DungeonResources(DEFAULT_HEALTH, DEFAULT_HEALTH_CAP, shields, shieldCap, 0))
            .build();
    }

    private static DungeonSessionState.Builder baseBuilder() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0), true, true,
            null, List.of(), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0), "r0", "r0", 3);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return DungeonSessionState.builder()
            .config(config).map(map).currentRoomId("r0")
            .encounters(Map.of()).bossEncounterIds(List.of())
            .resources(new DungeonResources(DEFAULT_HEALTH, DEFAULT_HEALTH_CAP, 0, DEFAULT_SHIELD_CAP, 0));
    }
}
