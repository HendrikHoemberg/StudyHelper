package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

@Service
public class DungeonCombatService {

    public DungeonSessionState processCorrectAnswer(DungeonSessionState state, DungeonEncounter encounter) {
        int effectiveStreakThreshold = RelicEffects.streakThreshold(state, DungeonBalance.STREAK_FOR_SHIELD);

        DungeonProgress nextProgress = state.progress().recordAnswer(true);
        DungeonResources nextResources = state.resources();

        if (nextProgress.streak() % effectiveStreakThreshold == 0) {
            nextResources = nextResources.addShield();
        }

        int base = encounter.boss() ? DungeonBalance.BOSS_PROMPT_SCORE : DungeonBalance.COMBAT_CLEAR_SCORE;
        int bonus = RelicEffects.bonusScoreOnCorrect(state);
        nextResources = nextResources.addScore(base + bonus);

        return state.withResources(nextResources).withProgress(nextProgress);
    }

    public DungeonSessionState processWrongAnswer(DungeonSessionState state) {
        DungeonSessionState withProgress = state.withProgress(state.progress().recordAnswer(false));
        return RelicEffects.applyWrongAnswerDamage(withProgress, DungeonBalance.WRONG_ANSWER_DAMAGE);
    }
}
