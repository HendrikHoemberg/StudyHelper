package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.RelicId;
import java.util.ArrayList;
import java.util.List;

public final class DungeonDamage {

    public static final int STARTING_HEALTH = 5;
    public static final int STARTING_HEALTH_CAP = 5;
    public static final int STARTING_SHIELD_CAP = 2;

    private DungeonDamage() {}

    public static DungeonSessionState takeDamage(DungeonSessionState state, int amount) {
        // Lucky Coin: first N wrong answers are free, where N = count(LUCKY_COIN in ownedRelics)
        int luckyCoinsOwned = (int) state.ownedRelics().stream()
            .filter(r -> r == RelicId.LUCKY_COIN).count();
        if (state.luckyCoinsConsumed() < luckyCoinsOwned) {
            return rebuild(state, state.health(), state.shields(),
                state.defeated(), state.shieldsUsed(), state.luckyCoinsConsumed() + 1);
        }
        // Shields first
        if (state.shields() > 0) {
            return rebuild(state, state.health(), state.shields() - 1,
                state.defeated(), state.shieldsUsed() + 1, state.luckyCoinsConsumed());
        }
        int newHealth = Math.max(0, state.health() - amount);
        if (newHealth <= 0) {
            // Phoenix Feather: consume one and revive at 1 HP
            int feathersOwned = (int) state.ownedRelics().stream()
                .filter(r -> r == RelicId.PHOENIX_FEATHER).count();
            if (feathersOwned > 0) {
                List<RelicId> remaining = new ArrayList<>(state.ownedRelics());
                remaining.remove(RelicId.PHOENIX_FEATHER);
                return new DungeonSessionState(
                    state.config(), state.map(), state.currentRoomId(),
                    state.encounters(), state.bossEncounterIds(), state.bossIndex(),
                    state.activeEncounterId(),
                    1, state.healthCap(), state.shields(), state.shieldCap(),
                    state.score(), state.answeredCount(), state.correctCount(),
                    state.won(), false,
                    state.streak(), state.gauntletQueue(),
                    state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
                    state.luckyCoinsConsumed(), remaining, state.pendingRelicPick());
            }
        }
        boolean defeated = state.defeated() || newHealth <= 0;
        return rebuild(state, newHealth, state.shields(), defeated, state.shieldsUsed(), state.luckyCoinsConsumed());
    }

    public static DungeonSessionState heal(DungeonSessionState state, int amount) {
        int newHealth = Math.min(state.healthCap(), state.health() + amount);
        return rebuild(state, newHealth, state.shields(), state.defeated(), state.shieldsUsed(), state.luckyCoinsConsumed());
    }

    private static DungeonSessionState rebuild(DungeonSessionState s, int hp, int shields,
                                                  boolean defeated, int shieldsUsed, int luckyCoinsConsumed) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            hp, s.healthCap(), shields, s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), defeated,
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), shieldsUsed,
            luckyCoinsConsumed, s.ownedRelics(), s.pendingRelicPick());
    }
}
