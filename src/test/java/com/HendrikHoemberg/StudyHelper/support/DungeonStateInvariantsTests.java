package com.HendrikHoemberg.StudyHelper.support;

import com.HendrikHoemberg.StudyHelper.dto.DungeonCombat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DungeonStateInvariantsTests {

    @Test
    void assertValid_passesForCoherentState() {
        assertThatCode(() -> DungeonStateInvariants.assertValid(TestStates.minimal()))
            .doesNotThrowAnyException();
    }

    @Test
    void assertValid_throwsForDanglingActiveEncounter() {
        assertThatThrownBy(() -> DungeonStateInvariants.assertValid(
                TestStates.minimal().withCombat(DungeonCombat.empty().withActiveEncounterId("ghost"))))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("ghost");
    }
}
