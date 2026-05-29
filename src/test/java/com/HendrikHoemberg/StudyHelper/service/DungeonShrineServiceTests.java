package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonShrineServiceTests {

    private DungeonShrineService shrineService;

    @BeforeEach
    void setUp() {
        shrineService = new DungeonShrineService(new Random(42L));
    }

    @Test
    void noArgConstructorWorks() {
        DungeonShrineService svc = new DungeonShrineService();
        assertThat(svc).isNotNull();
        DungeonShrineService.ShrineRollOutcome outcome = svc.computeOutcome(baseState());
        assertThat(outcome.roll()).isBetween(1, 6);
    }

    @Test
    void computeOutcome_rollIsBetween1And6() {
        for (int i = 0; i < 100; i++) {
            DungeonShrineService.ShrineRollOutcome outcome = shrineService.computeOutcome(baseState());
            assertThat(outcome.roll()).isBetween(1, 6);
        }
    }

    @Test
    void computeOutcome_noRelicWhenNotSix() {
        Random rng = new Random(2L);
        DungeonShrineService svc = new DungeonShrineService(rng);
        DungeonShrineService.ShrineRollOutcome outcome = svc.computeOutcome(baseState());
        if (outcome.roll() != 6) {
            assertThat(outcome.grantedRelic()).isNull();
        }
    }

    @Test
    void computeOutcome_grantedRelicFromUnownedWhenSix() {
        DungeonSessionState state = baseStateOwning(List.of(RelicId.IRON_PLATE, RelicId.COMPASS));
        for (long seed = 0; seed < 100; seed++) {
            DungeonShrineService svc = new DungeonShrineService(new Random(seed));
            DungeonShrineService.ShrineRollOutcome outcome = svc.computeOutcome(state);
            if (outcome.roll() == 6) {
                assertThat(outcome.grantedRelic()).isNotNull();
                assertThat(state.loadout().ownedRelics()).doesNotContain(outcome.grantedRelic());
                return;
            }
        }
    }

    @Test
    void computeOutcome_fallbackToIronPlateWhenAllOwned() {
        DungeonSessionState state = baseStateOwning(List.of(RelicId.values()));
        for (long seed = 0; seed < 100; seed++) {
            DungeonShrineService svc = new DungeonShrineService(new Random(seed));
            DungeonShrineService.ShrineRollOutcome outcome = svc.computeOutcome(state);
            if (outcome.roll() == 6) {
                assertThat(outcome.grantedRelic()).isEqualTo(RelicId.IRON_PLATE);
                return;
            }
        }
    }

    @Test
    void drink_healsOneHP() {
        DungeonSessionState state = baseState();
        int healthBefore = state.resources().health();
        DungeonSessionState next = shrineService.drink(state);
        assertThat(next.resources().health()).isEqualTo(Math.min(healthBefore + 1, state.resources().healthCap()));
    }

    @Test
    void leave_marksShrineCleared() {
        DungeonSessionState state = baseState();
        DungeonSessionState next = shrineService.leave(state);
        assertThat(next.currentRoom().cleared()).isTrue();
        assertThat(next.loadout().pendingRelicPick()).isNull();
    }

    @Test
    void applyRoll_takesDamageOnNonSix() {
        DungeonSessionState state = baseState();
        DungeonShrineService.ShrineRollOutcome outcome = new DungeonShrineService.ShrineRollOutcome(3, null);
        DungeonSessionState next = shrineService.applyRoll(state, outcome);
        assertThat(next.resources().health()).isEqualTo(state.resources().health() - 1);
    }

    @Test
    void applyRoll_grantsRelicOnSix() {
        DungeonSessionState state = baseStateOwning(List.of());
        DungeonShrineService.ShrineRollOutcome outcome = new DungeonShrineService.ShrineRollOutcome(6, RelicId.IRON_PLATE);
        DungeonSessionState next = shrineService.applyRoll(state, outcome);
        assertThat(next.loadout().ownedRelics()).contains(RelicId.IRON_PLATE);
        assertThat(next.resources().healthCap()).isEqualTo(state.resources().healthCap() + 1);
    }

    @Test
    void applyRoll_defeatedWhenHealthZero() {
        DungeonSessionState state = baseState()
            .withResources(new DungeonResources(1, 5, 0, 2, 0));
        DungeonShrineService.ShrineRollOutcome outcome = new DungeonShrineService.ShrineRollOutcome(3, null);
        DungeonSessionState next = shrineService.applyRoll(state, outcome);
        assertThat(next.defeated()).isTrue();
    }

    private static DungeonSessionState baseState() {
        return baseStateOwning(List.of());
    }

    private static DungeonSessionState baseStateOwning(List<RelicId> relics) {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.SHRINE,
            Map.of(), new GridPos(0, 0),
            true, false, null, List.of(), null, null, null);
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
