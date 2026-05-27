package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import com.HendrikHoemberg.StudyHelper.dto.RelicId;

class DungeonDamageTests {

    @Test
    void takeDamage_consumesShieldFirst() {
        DungeonSessionState s = state(5, 5, 1, 2);
        DungeonSessionState after = DungeonDamage.takeDamage(s, 1);
        assertThat(after.health()).isEqualTo(5);
        assertThat(after.shields()).isEqualTo(0);
        assertThat(after.shieldsUsed()).isEqualTo(1);
    }

    @Test
    void takeDamage_reducesHealthWhenNoShield() {
        DungeonSessionState s = state(5, 5, 0, 2);
        DungeonSessionState after = DungeonDamage.takeDamage(s, 1);
        assertThat(after.health()).isEqualTo(4);
        assertThat(after.defeated()).isFalse();
    }

    @Test
    void takeDamage_setsDefeatedAtZeroHealth() {
        DungeonSessionState s = state(1, 5, 0, 2);
        DungeonSessionState after = DungeonDamage.takeDamage(s, 1);
        assertThat(after.health()).isEqualTo(0);
        assertThat(after.defeated()).isTrue();
    }

    @Test
    void heal_capsAtHealthCap() {
        DungeonSessionState s = state(4, 5, 0, 2);
        DungeonSessionState after = DungeonDamage.heal(s, 99);
        assertThat(after.health()).isEqualTo(5);
    }

    @Test
    void heal_respectsHigherHealthCapFromIronPlate() {
        DungeonSessionState s = state(5, 7, 0, 2);
        DungeonSessionState after = DungeonDamage.heal(s, 99);
        assertThat(after.health()).isEqualTo(7);
    }

    @Test
    void takeDamage_luckyCoinAbsorbsFirstHitFreeOfShieldOrHp() {
        DungeonSessionState s = stateWithRelics(state(5, 5, 0, 2), List.of(RelicId.LUCKY_COIN));
        DungeonSessionState after = DungeonDamage.takeDamage(s, 1);
        assertThat(after.health()).isEqualTo(5);
        assertThat(after.shields()).isEqualTo(0);
        assertThat(after.luckyCoinsConsumed()).isEqualTo(1);
    }

    @Test
    void takeDamage_secondLuckyCoinAbsorbsSecondHitWhenStacked() {
        DungeonSessionState s = stateWithRelics(state(5, 5, 0, 2),
            List.of(RelicId.LUCKY_COIN, RelicId.LUCKY_COIN));
        DungeonSessionState afterFirst = DungeonDamage.takeDamage(s, 1);
        DungeonSessionState afterSecond = DungeonDamage.takeDamage(afterFirst, 1);
        assertThat(afterSecond.health()).isEqualTo(5);
        assertThat(afterSecond.luckyCoinsConsumed()).isEqualTo(2);
    }

    @Test
    void takeDamage_phoenixFeatherResurrectsAtOneHpOnLethal() {
        DungeonSessionState s = stateWithRelics(state(1, 5, 0, 2), List.of(RelicId.PHOENIX_FEATHER));
        DungeonSessionState after = DungeonDamage.takeDamage(s, 5);
        assertThat(after.health()).isEqualTo(1);
        assertThat(after.defeated()).isFalse();
        assertThat(after.ownedRelics()).doesNotContain(RelicId.PHOENIX_FEATHER);
    }

    private DungeonSessionState stateWithRelics(DungeonSessionState base, List<RelicId> relics) {
        return new DungeonSessionState(
            base.config(), base.map(), base.currentRoomId(),
            base.encounters(), base.bossEncounterIds(), base.bossIndex(),
            base.activeEncounterId(),
            base.health(), base.healthCap(), base.shields(), base.shieldCap(),
            base.score(), base.answeredCount(), base.correctCount(),
            base.won(), base.defeated(),
            base.streak(), base.gauntletQueue(),
            base.longestStreak(), base.elitesCleared(), base.shieldsUsed(),
            base.luckyCoinsConsumed(), relics, base.pendingRelicPick());
    }

    private DungeonSessionState state(int hp, int hpCap, int shields, int shieldCap) {
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
            hp, hpCap, shields, shieldCap,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);
    }
}
