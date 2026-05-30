package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.support.TestStates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarBannerEffectTests {

    private DungeonRoom room(RoomType type) {
        return new DungeonRoom("r1", type, Map.of(), new GridPos(0, 0),
            true, false, null, List.of(), null, null, null);
    }

    @Test
    void addsShieldWhenCombatRoomCleared() {
        DungeonSessionState s = TestStates.minimal()
            .withResources(new DungeonResources(5, 5, 0, 2, 0));
        DungeonSessionState after = new WarBannerEffect().onRoomClear(s, room(RoomType.COMBAT));
        assertThat(after.resources().shields()).isEqualTo(1);
    }

    @Test
    void doesNothingForNonCombatRoom() {
        DungeonSessionState s = TestStates.minimal()
            .withResources(new DungeonResources(5, 5, 0, 2, 0));
        DungeonSessionState after = new WarBannerEffect().onRoomClear(s, room(RoomType.HEAL));
        assertThat(after.resources().shields()).isEqualTo(0);
    }

    @Test
    void registry_appliesWarBannerOnlyWhenOwned() {
        DungeonSessionState owned = TestStates.minimal()
            .withResources(new DungeonResources(5, 5, 0, 2, 0))
            .withLoadout(new DungeonLoadout(List.of(RelicId.WAR_BANNER), null));
        assertThat(RelicEffects.onRoomClear(owned, room(RoomType.COMBAT)).resources().shields()).isEqualTo(1);

        DungeonSessionState notOwned = TestStates.minimal()
            .withResources(new DungeonResources(5, 5, 0, 2, 0));
        assertThat(RelicEffects.onRoomClear(notOwned, room(RoomType.COMBAT)).resources().shields()).isEqualTo(0);
    }
}
