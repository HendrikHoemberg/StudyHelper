package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

public final class DungeonDamage {

    public static final int STARTING_HEALTH = 5;
    public static final int STARTING_HEALTH_CAP = 5;
    public static final int STARTING_SHIELD_CAP = 2;

    private DungeonDamage() {}

    public static DungeonSessionState takeDamage(DungeonSessionState state, int amount) {
        if (state.shields() > 0) {
            return rebuild(state, state.health(), state.shields() - 1,
                state.defeated(), state.shieldsUsed() + 1);
        }
        int newHealth = Math.max(0, state.health() - amount);
        boolean defeated = state.defeated() || newHealth <= 0;
        return rebuild(state, newHealth, state.shields(), defeated, state.shieldsUsed());
    }

    public static DungeonSessionState heal(DungeonSessionState state, int amount) {
        int newHealth = Math.min(state.healthCap(), state.health() + amount);
        return rebuild(state, newHealth, state.shields(), state.defeated(), state.shieldsUsed());
    }

    private static DungeonSessionState rebuild(DungeonSessionState s, int hp, int shields,
                                                  boolean defeated, int shieldsUsed) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            hp, s.healthCap(), shields, s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), defeated,
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), shieldsUsed,
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }
}
