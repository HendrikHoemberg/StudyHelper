package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DungeonCombatTests {

    @Test
    void empty_factoryReturnsNeutralCombatState() {
        DungeonCombat c = DungeonCombat.empty();
        assertThat(c.activeEncounterId()).isNull();
        assertThat(c.gauntletQueue()).isEmpty();
        assertThat(c.bossIndex()).isZero();
    }

    @Test
    void withSetters_replaceSingleFields() {
        DungeonCombat c = DungeonCombat.empty()
            .withActiveEncounterId("fc_3")
            .withGauntletQueue(List.of("a", "b"))
            .withBossIndex(2);
        assertThat(c.activeEncounterId()).isEqualTo("fc_3");
        assertThat(c.gauntletQueue()).containsExactly("a", "b");
        assertThat(c.bossIndex()).isEqualTo(2);
    }

    @Test
    void advanceBoss_incrementsBossIndex() {
        assertThat(new DungeonCombat(null, List.of(), 1, null).advanceBoss().bossIndex()).isEqualTo(2);
    }

    @Test
    void exitCombat_clearsActiveAndQueue() {
        DungeonCombat c = new DungeonCombat("fc_1", List.of("a"), 2, null).exitCombat();
        assertThat(c.activeEncounterId()).isNull();
        assertThat(c.gauntletQueue()).isEmpty();
        assertThat(c.bossIndex()).isEqualTo(2);
    }

    @Test
    void withGauntletQueue_makesDefensiveCopy() {
        java.util.List<String> input = new java.util.ArrayList<>(List.of("a", "b"));
        DungeonCombat c = DungeonCombat.empty().withGauntletQueue(input);
        input.add("c");
        assertThat(c.gauntletQueue()).containsExactly("a", "b");
    }

    @Test
    void gauntletQueue_isImmutable() {
        DungeonCombat c = new DungeonCombat(null, List.of("a"), 0, null);
        assertThatThrownBy(() -> c.gauntletQueue().add("x"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void activeRevenantIdSetAndClearedByExitCombat() {
        DungeonCombat c = DungeonCombat.empty().withActiveRevenantId("fc_3");
        assertThat(c.activeRevenantId()).isEqualTo("fc_3");
        assertThat(c.exitCombat().activeRevenantId()).isNull();
    }

    @Test
    void emptyCombatHasNoActiveRevenant() {
        assertThat(DungeonCombat.empty().activeRevenantId()).isNull();
    }
}
