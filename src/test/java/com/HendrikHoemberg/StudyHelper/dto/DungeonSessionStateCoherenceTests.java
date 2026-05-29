package com.HendrikHoemberg.StudyHelper.dto;

import com.HendrikHoemberg.StudyHelper.support.TestStates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonSessionStateCoherenceTests {

    @Test
    void minimalStateIsCoherent() {
        assertThat(TestStates.minimal().isCoherent()).isTrue();
    }

    @Test
    void missingCurrentRoomIsIncoherent() {
        assertThat(TestStates.minimal().withCurrentRoomId("ghost").isCoherent()).isFalse();
    }

    @Test
    void danglingActiveEncounterIsIncoherent() {
        DungeonSessionState s = TestStates.minimal()
            .withCombat(DungeonCombat.empty().withActiveEncounterId("ghost"));
        assertThat(s.isCoherent()).isFalse();
    }

    @Test
    void danglingGauntletQueueMemberIsIncoherent() {
        DungeonSessionState s = TestStates.minimal()
            .withCombat(DungeonCombat.empty().withGauntletQueue(List.of("ghost")));
        assertThat(s.isCoherent()).isFalse();
    }

    @Test
    void pendingPickToMissingRoomIsIncoherent() {
        DungeonSessionState s = TestStates.minimal()
            .withLoadout(new DungeonLoadout(List.of(),
                new PendingRelicPick(PendingPickType.TREASURE, "ghost", List.of(), null)));
        assertThat(s.isCoherent()).isFalse();
    }

    @Test
    void wonAndDefeatedIsIncoherent() {
        assertThat(TestStates.minimal().withWon(true).withDefeated(true).isCoherent()).isFalse();
    }

    @Test
    void negativeBossIndexIsIncoherent() {
        DungeonSessionState s = TestStates.minimal()
            .withCombat(DungeonCombat.empty().withBossIndex(-1));
        assertThat(s.isCoherent()).isFalse();
    }

    @Test
    void bossIdNotInEncountersIsIncoherent() {
        DungeonSessionState s = TestStates.minimal().toBuilder()
            .bossEncounterIds(List.of("b0"))
            .build();
        assertThat(s.isCoherent()).isFalse();
    }
}
