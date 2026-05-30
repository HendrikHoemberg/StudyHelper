package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.support.TestStates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcquireEffectsTests {

    @Test
    void ironPlate_raisesHealthCapByOneAndHealsOne() {
        DungeonSessionState before = TestStates.minimal()
            .withResources(new DungeonResources(3, 5, 0, 2, 0));
        DungeonSessionState after = new IronPlateEffect().onAcquire(before);
        assertThat(after.resources().healthCap()).isEqualTo(6);
        assertThat(after.resources().health()).isEqualTo(4);
    }

    @Test
    void buckler_raisesShieldCapByOneAndAddsShield() {
        DungeonSessionState before = TestStates.minimal()
            .withResources(new DungeonResources(5, 5, 0, 2, 0));
        DungeonSessionState after = new BucklerEffect().onAcquire(before);
        assertThat(after.resources().shieldCap()).isEqualTo(3);
        assertThat(after.resources().shields()).isEqualTo(1);
    }

    @Test
    void registry_routesIronPlateThroughOnAcquire() {
        DungeonSessionState before = TestStates.minimal()
            .withResources(new DungeonResources(3, 5, 0, 2, 0));
        DungeonSessionState after = RelicEffects.onAcquire(before, RelicId.IRON_PLATE);
        assertThat(after.resources().healthCap()).isEqualTo(6);
        assertThat(RelicEffects.isClassified(RelicId.IRON_PLATE)).isTrue();
        assertThat(RelicEffects.isClassified(RelicId.BUCKLER)).isTrue();
    }
}
