package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.service.DungeonShrineService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonShrineRollTests {

    @Test
    void rollIsInRange1To6() {
        DungeonShrineService svc = new DungeonShrineService(new Random(0L));
        for (int i = 0; i < 200; i++) {
            DungeonShrineService.ShrineRollOutcome outcome = svc.computeOutcome(stateOwning(List.of()));
            assertThat(outcome.roll()).isBetween(1, 6);
        }
    }

    @Test
    void noRelicGrantedWhenRollIsNotSix() {
        DungeonShrineService svc = new DungeonShrineService(new Random(2L));
        DungeonShrineService.ShrineRollOutcome outcome = svc.computeOutcome(stateOwning(List.of()));
        if (outcome.roll() != 6) {
            assertThat(outcome.grantedRelic()).isNull();
        }
    }

    @Test
    void grantedRelicIsAlwaysFromUnownedSetWhenRollIsSix() {
        for (long seed = 0; seed < 100; seed++) {
            DungeonShrineService svc = new DungeonShrineService(new Random(seed));
            List<RelicId> owned = List.of(RelicId.IRON_PLATE, RelicId.COMPASS);
            DungeonShrineService.ShrineRollOutcome outcome = svc.computeOutcome(stateOwning(owned));
            if (outcome.roll() == 6) {
                assertThat(outcome.grantedRelic()).isNotNull();
                assertThat(owned).doesNotContain(outcome.grantedRelic());
                return;
            }
        }
        throw new AssertionError("Never observed a 6 across 100 seeds");
    }

    @Test
    void grantedRelicFallsBackToIronPlateWhenAllRelicsOwned() {
        List<RelicId> all = Arrays.asList(RelicId.values());
        for (long seed = 0; seed < 100; seed++) {
            DungeonShrineService svc = new DungeonShrineService(new Random(seed));
            DungeonShrineService.ShrineRollOutcome outcome = svc.computeOutcome(stateOwning(all));
            if (outcome.roll() == 6) {
                assertThat(outcome.grantedRelic()).isEqualTo(RelicId.IRON_PLATE);
                return;
            }
        }
        throw new AssertionError("Never observed a 6 across 100 seeds");
    }

    private static DungeonSessionState stateOwning(List<RelicId> relics) {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0), "r0", "r0", 3);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return DungeonSessionState.builder()
            .config(config)
            .map(map)
            .currentRoomId("r0")
            .encounters(Map.of())
            .bossEncounterIds(List.of())
            .combat(DungeonCombat.empty())
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .progress(new DungeonProgress(0, 0, 0, 0, 0, 0, 0))
            .loadout(new DungeonLoadout(relics, null))
            .build();
    }
}
