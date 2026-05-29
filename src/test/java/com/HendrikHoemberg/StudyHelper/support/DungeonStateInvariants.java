package com.HendrikHoemberg.StudyHelper.support;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.PendingRelicPick;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the cross-record coherence invariants of a {@link DungeonSessionState} with
 * per-clause messages, so a failing playthrough/reconcile test names the violated invariant.
 * Mirrors {@link DungeonSessionState#isCoherent()} but reports which clause failed.
 */
public final class DungeonStateInvariants {

    private DungeonStateInvariants() {}

    public static void assertValid(DungeonSessionState s) {
        assertThat(s.currentRoomId()).as("currentRoomId is set").isNotNull();
        assertThat(s.map()).as("map is set").isNotNull();
        assertThat(s.map().room(s.currentRoomId()))
            .as("current room %s exists in map", s.currentRoomId()).isNotNull();

        String active = s.combat().activeEncounterId();
        if (active != null) {
            assertThat(s.encounters()).as("active encounter %s exists", active).containsKey(active);
        }
        for (String queuedId : s.combat().gauntletQueue()) {
            assertThat(s.encounters()).as("gauntlet-queue encounter %s exists", queuedId).containsKey(queuedId);
        }

        PendingRelicPick pick = s.loadout().pendingRelicPick();
        if (pick != null) {
            assertThat(s.map().room(pick.roomId()))
                .as("pending-pick room %s exists", pick.roomId()).isNotNull();
        }

        assertThat(s.won() && s.defeated()).as("state is not simultaneously won and defeated").isFalse();
        assertThat(s.combat().bossIndex())
            .as("bossIndex within [0, %d]", s.bossEncounterIds().size())
            .isBetween(0, s.bossEncounterIds().size());
        for (String bossId : s.bossEncounterIds()) {
            assertThat(s.encounters()).as("boss encounter %s exists", bossId).containsKey(bossId);
        }
    }
}
