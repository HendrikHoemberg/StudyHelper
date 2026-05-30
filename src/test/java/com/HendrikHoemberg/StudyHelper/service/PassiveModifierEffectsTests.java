package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.support.TestStates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PassiveModifierEffectsTests {

    @Test
    void sharpFocus_lowersThresholdToMinimum() {
        assertThat(new SharpFocusEffect().modifyStreakThreshold(DungeonBalance.STREAK_FOR_SHIELD))
            .isEqualTo(DungeonBalance.SHARP_FOCUS_STREAK);
        assertThat(new SharpFocusEffect().modifyStreakThreshold(1)).isEqualTo(1);
    }

    @Test
    void luckyCharm_grantsBonusScore() {
        assertThat(new LuckyCharmEffect().bonusScoreOnCorrect()).isEqualTo(DungeonBalance.LUCKY_CHARM_BONUS);
    }

    @Test
    void registry_foldsOwnedPassiveModifiers() {
        DungeonSessionState withSharp = TestStates.minimal()
            .withLoadout(new DungeonLoadout(List.of(RelicId.SHARP_FOCUS), null));
        assertThat(RelicEffects.streakThreshold(withSharp, DungeonBalance.STREAK_FOR_SHIELD))
            .isEqualTo(DungeonBalance.SHARP_FOCUS_STREAK);

        DungeonSessionState withCharm = TestStates.minimal()
            .withLoadout(new DungeonLoadout(List.of(RelicId.LUCKY_CHARM), null));
        assertThat(RelicEffects.bonusScoreOnCorrect(withCharm)).isEqualTo(DungeonBalance.LUCKY_CHARM_BONUS);
    }
}
