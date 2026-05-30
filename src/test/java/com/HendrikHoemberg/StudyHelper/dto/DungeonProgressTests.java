package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DungeonProgressTests {

    private DungeonProgress zero() {
        return new DungeonProgress(0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void withSetters_replaceSingleFields() {
        DungeonProgress p = zero()
            .withAnsweredCount(3)
            .withCorrectCount(2)
            .withStreak(2)
            .withLongestStreak(5)
            .withElitesCleared(1)
            .withShieldsUsed(4)
            .withLuckyCoinsConsumed(1);
        assertThat(p.answeredCount()).isEqualTo(3);
        assertThat(p.correctCount()).isEqualTo(2);
        assertThat(p.streak()).isEqualTo(2);
        assertThat(p.longestStreak()).isEqualTo(5);
        assertThat(p.elitesCleared()).isEqualTo(1);
        assertThat(p.shieldsUsed()).isEqualTo(4);
        assertThat(p.luckyCoinsConsumed()).isEqualTo(1);
    }

    @Test
    void recordAnswer_correct_incrementsAnsweredCorrectStreakAndLongest() {
        DungeonProgress p = zero().recordAnswer(true);
        assertThat(p.answeredCount()).isEqualTo(1);
        assertThat(p.correctCount()).isEqualTo(1);
        assertThat(p.streak()).isEqualTo(1);
        assertThat(p.longestStreak()).isEqualTo(1);
    }

    @Test
    void recordAnswer_correctAfterStreak_preservesLongest() {
        DungeonProgress p = new DungeonProgress(2, 2, 2, 4, 0, 0, 0, 0).recordAnswer(true);
        assertThat(p.streak()).isEqualTo(3);
        assertThat(p.longestStreak()).isEqualTo(4);
    }

    @Test
    void recordAnswer_wrong_incrementsAnsweredAndResetsStreakKeepingLongest() {
        DungeonProgress p = new DungeonProgress(5, 4, 3, 5, 0, 0, 0, 0).recordAnswer(false);
        assertThat(p.answeredCount()).isEqualTo(6);
        assertThat(p.correctCount()).isEqualTo(4);
        assertThat(p.streak()).isEqualTo(0);
        assertThat(p.longestStreak()).isEqualTo(5);
    }

    @Test
    void recordShieldUsed_increments() {
        assertThat(zero().recordShieldUsed().shieldsUsed()).isEqualTo(1);
        assertThat(new DungeonProgress(0,0,0,0,0,3,0,0).recordShieldUsed().shieldsUsed()).isEqualTo(4);
    }

    @Test
    void consumeLuckyCoin_increments() {
        assertThat(zero().consumeLuckyCoin().luckyCoinsConsumed()).isEqualTo(1);
    }

    @Test
    void constructor_clampsNegativeFieldsToZero() {
        DungeonProgress p = new DungeonProgress(-1, -1, -1, -1, -1, -1, -1, -1);
        assertThat(p.answeredCount()).isEqualTo(0);
        assertThat(p.correctCount()).isEqualTo(0);
        assertThat(p.streak()).isEqualTo(0);
        assertThat(p.longestStreak()).isEqualTo(0);
        assertThat(p.elitesCleared()).isEqualTo(0);
        assertThat(p.shieldsUsed()).isEqualTo(0);
        assertThat(p.luckyCoinsConsumed()).isEqualTo(0);
    }

    @Test
    void recordRevenantMasteredIncrements() {
        DungeonProgress p = new DungeonProgress(0, 0, 0, 0, 0, 0, 0, 0);
        DungeonProgress next = p.recordRevenantMastered();
        assertThat(next.revenantsMastered()).isEqualTo(1);
        assertThat(p.revenantsMastered()).isEqualTo(0); // immutable
    }
}
