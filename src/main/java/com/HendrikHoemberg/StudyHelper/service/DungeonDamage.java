package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

public final class DungeonDamage {

    public static final int STARTING_HEALTH = 5;
    public static final int STARTING_HEALTH_CAP = 5;

    private DungeonDamage() {}

    public static DungeonSessionState takeDamage(DungeonSessionState state, int amount) {
        int newHealth = Math.max(0, state.health() - amount);
        boolean defeated = state.defeated() || newHealth <= 0;
        return new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            newHealth, state.score(),
            state.answeredCount(), state.correctCount(),
            state.visibleTiles(),
            state.won(), defeated,
            state.streak(), state.shields(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed());
    }

    public static DungeonSessionState heal(DungeonSessionState state, int amount) {
        int newHealth = Math.min(STARTING_HEALTH_CAP, state.health() + amount);
        return new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            newHealth, state.score(),
            state.answeredCount(), state.correctCount(),
            state.visibleTiles(),
            state.won(), state.defeated(),
            state.streak(), state.shields(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed());
    }
}
