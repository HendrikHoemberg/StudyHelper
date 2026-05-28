package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

@Service
public class DungeonCombatService {

    public DungeonSessionState processCorrectAnswer(DungeonSessionState state, DungeonEncounter encounter) {
        int effectiveStreakThreshold = state.loadout().ownedRelics().contains(RelicId.SHARP_FOCUS)
            ? DungeonBalance.SHARP_FOCUS_STREAK : DungeonBalance.STREAK_FOR_SHIELD;

        DungeonProgress nextProgress = state.progress().recordAnswer(true);
        DungeonResources nextResources = state.resources();

        if (nextProgress.streak() % effectiveStreakThreshold == 0) {
            nextResources = nextResources.addShield();
        }

        int base = encounter.boss() ? DungeonBalance.BOSS_PROMPT_SCORE : DungeonBalance.COMBAT_CLEAR_SCORE;
        int bonus = state.loadout().ownedRelics().contains(RelicId.LUCKY_CHARM) ? DungeonBalance.LUCKY_CHARM_BONUS : 0;
        nextResources = nextResources.addScore(base + bonus);

        return state.withResources(nextResources).withProgress(nextProgress);
    }

    public DungeonSessionState processWrongAnswer(DungeonSessionState state) {
        DungeonSessionState withProgress = state.withProgress(state.progress().recordAnswer(false));
        return applyWrongAnswerDamage(withProgress);
    }

    private DungeonSessionState applyWrongAnswerDamage(DungeonSessionState s) {
        int luckyCoinsOwned = (int) s.loadout().ownedRelics().stream()
            .filter(r -> r == RelicId.LUCKY_COIN).count();
        if (s.progress().luckyCoinsConsumed() < luckyCoinsOwned) {
            return s.withProgress(s.progress().consumeLuckyCoin());
        }
        if (s.resources().shields() > 0) {
            return s
                .withResources(s.resources().withShields(s.resources().shields() - 1))
                .withProgress(s.progress().recordShieldUsed());
        }
        DungeonResources damaged = s.resources().takeHealthDamage(DungeonBalance.WRONG_ANSWER_DAMAGE);
        if (damaged.health() == 0 && s.loadout().ownedRelics().contains(RelicId.PHOENIX_FEATHER)) {
            return s
                .withResources(damaged.withHealth(1))
                .withLoadout(s.loadout().consumePhoenixFeather());
        }
        return s.withResources(damaged).withDefeated(damaged.health() == 0);
    }
}
