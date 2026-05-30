package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.support.TestStates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelicEffectsTests {

    @Test
    void onAcquire_forUnhandledRelic_returnsStateUnchanged() {
        DungeonSessionState s = TestStates.minimal();
        assertThat(RelicEffects.onAcquire(s, RelicId.MAP_SENSE)).isSameAs(s);
    }

    @Test
    void streakThreshold_withNoOwnedRelics_returnsBase() {
        DungeonSessionState s = TestStates.minimal();
        assertThat(RelicEffects.streakThreshold(s, DungeonBalance.STREAK_FOR_SHIELD))
            .isEqualTo(DungeonBalance.STREAK_FOR_SHIELD);
    }

    @Test
    void bonusScoreOnCorrect_withNoOwnedRelics_isZero() {
        DungeonSessionState s = TestStates.minimal();
        assertThat(RelicEffects.bonusScoreOnCorrect(s)).isEqualTo(0);
    }

    @Test
    void viewOnlyRelicsAreClassified() {
        assertThat(RelicEffects.isClassified(RelicId.SPECTACLES)).isTrue();
        assertThat(RelicEffects.isClassified(RelicId.MAP_SENSE)).isTrue();
        assertThat(RelicEffects.isClassified(RelicId.COMPASS)).isTrue();
    }
}
