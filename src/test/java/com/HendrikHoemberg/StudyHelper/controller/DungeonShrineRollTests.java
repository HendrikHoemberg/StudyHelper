package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonShrineRollTests {

    @Test
    void rollIsInRange1To6() {
        Random rng = new Random(0L);
        for (int i = 0; i < 200; i++) {
            DungeonController.ShrineRollOutcome outcome =
                DungeonController.computeShrineRollOutcome(stateOwning(List.of()), rng);
            assertThat(outcome.roll()).isBetween(1, 6);
        }
    }

    @Test
    void noRelicGrantedWhenRollIsNotSix() {
        // Random(2L) starts with a non-six roll under nextInt(6)+1; assert no relic.
        Random rng = new Random(2L);
        DungeonController.ShrineRollOutcome outcome =
            DungeonController.computeShrineRollOutcome(stateOwning(List.of()), rng);
        if (outcome.roll() != 6) {
            assertThat(outcome.grantedRelic()).isNull();
        }
    }

    @Test
    void grantedRelicIsAlwaysFromUnownedSetWhenRollIsSix() {
        // Force roll==6 by using a Random whose first nextInt(6) returns 5.
        // We can't predict that across seeds easily, so iterate seeds until we land on a 6.
        for (long seed = 0; seed < 100; seed++) {
            Random rng = new Random(seed);
            List<RelicId> owned = List.of(RelicId.IRON_PLATE, RelicId.COMPASS);
            DungeonController.ShrineRollOutcome outcome =
                DungeonController.computeShrineRollOutcome(stateOwning(owned), rng);
            if (outcome.roll() == 6) {
                assertThat(outcome.grantedRelic()).isNotNull();
                assertThat(owned).doesNotContain(outcome.grantedRelic());
                return;
            }
        }
        // Should be statistically impossible to never see a 6 in 100 tries.
        throw new AssertionError("Never observed a 6 across 100 seeds");
    }

    @Test
    void grantedRelicFallsBackToIronPlateWhenAllRelicsOwned() {
        List<RelicId> all = Arrays.asList(RelicId.values());
        for (long seed = 0; seed < 100; seed++) {
            Random rng = new Random(seed);
            DungeonController.ShrineRollOutcome outcome =
                DungeonController.computeShrineRollOutcome(stateOwning(all), rng);
            if (outcome.roll() == 6) {
                assertThat(outcome.grantedRelic()).isEqualTo(RelicId.IRON_PLATE);
                return;
            }
        }
        throw new AssertionError("Never observed a 6 across 100 seeds");
    }

    @Test
    void sameSeedProducesSameOutcome() {
        DungeonController.ShrineRollOutcome a =
            DungeonController.computeShrineRollOutcome(stateOwning(List.of()), new Random(42L));
        DungeonController.ShrineRollOutcome b =
            DungeonController.computeShrineRollOutcome(stateOwning(List.of()), new Random(42L));
        assertThat(a.roll()).isEqualTo(b.roll());
        assertThat(a.grantedRelic()).isEqualTo(b.grantedRelic());
    }

    private static DungeonSessionState stateOwning(List<RelicId> relics) {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0), "r0", "r0", 3);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return new DungeonSessionState(
            config, map, "r0",
            Map.of(), List.of(), 0, null,
            5, 5, 0, 2,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            relics, null);
    }
}
