package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonNavigationServiceTests {

    private final DungeonNavigationService nav = new DungeonNavigationService();

    @Test
    void move_intoRoomWithoutDoorReturnsUnchangedState() {
        DungeonSessionState state = twoRoomState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.UP);
        assertThat(result.state().currentRoomId()).isEqualTo("r0");
        assertThat(result.activatedEncounterRoomId()).isNull();
    }

    @Test
    void move_throughDoorUpdatesCurrentRoomAndMarksVisited() {
        DungeonSessionState state = twoRoomState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().currentRoomId()).isEqualTo("r1");
        assertThat(result.state().map().room("r1").visited()).isTrue();
    }

    @Test
    void move_intoCombatRoomSignalsEncounterActivation() {
        DungeonSessionState state = combatNeighborState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.activatedEncounterRoomId()).isEqualTo("r1");
    }

    @Test
    void move_intoHealRoomAppliesFullHealAndShieldGrantAndMarksCleared() {
        DungeonSessionState state = healNeighborStateWithLowHpAndNoShields();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().health()).isEqualTo(result.state().healthCap());
        assertThat(result.state().shields()).isEqualTo(1);
        assertThat(result.state().map().room("r1").cleared()).isTrue();
    }

    @Test
    void move_intoAlreadyClearedHealRoomDoesNothingExtra() {
        DungeonSessionState state = clearedHealNeighborState();
        int hpBefore = state.health();
        int shieldsBefore = state.shields();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().health()).isEqualTo(hpBefore);
        assertThat(result.state().shields()).isEqualTo(shieldsBefore);
    }

    @Test
    void move_blockedWhileEncounterActive() {
        DungeonSessionState state = encounterActiveState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state()).isSameAs(state);
    }

    @Test
    void move_blockedWhenRunIsComplete() {
        DungeonSessionState state = wonState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state()).isSameAs(state);
    }

    private DungeonSessionState twoRoomState() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            false, false, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 5, 0);
    }

    private DungeonSessionState combatNeighborState() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            false, false, "enc_0", List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 5, 0);
    }

    private DungeonSessionState healNeighborStateWithLowHpAndNoShields() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.HEAL,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            false, false, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 2, 0);
    }

    private DungeonSessionState clearedHealNeighborState() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.HEAL,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 3, 1);
    }

    private DungeonSessionState encounterActiveState() {
        DungeonSessionState base = combatNeighborState();
        return new DungeonSessionState(
            base.config(), base.map(), base.currentRoomId(),
            base.encounters(), base.bossEncounterIds(), base.bossIndex(),
            "enc_0",
            base.health(), base.healthCap(), base.shields(), base.shieldCap(),
            base.score(), base.answeredCount(), base.correctCount(),
            false, false,
            base.streak(), base.gauntletQueue(),
            base.longestStreak(), base.elitesCleared(), base.shieldsUsed(),
            base.luckyCoinsConsumed(), base.ownedRelics(), base.pendingRelicPick());
    }

    private DungeonSessionState wonState() {
        DungeonSessionState base = twoRoomState();
        return new DungeonSessionState(
            base.config(), base.map(), base.currentRoomId(),
            base.encounters(), base.bossEncounterIds(), base.bossIndex(),
            base.activeEncounterId(),
            base.health(), base.healthCap(), base.shields(), base.shieldCap(),
            base.score(), base.answeredCount(), base.correctCount(),
            true, false,
            base.streak(), base.gauntletQueue(),
            base.longestStreak(), base.elitesCleared(), base.shieldsUsed(),
            base.luckyCoinsConsumed(), base.ownedRelics(), base.pendingRelicPick());
    }

    private DungeonSessionState baseState(DungeonMap map, String currentRoomId,
                                            int hp, int shields) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return new DungeonSessionState(
            config, map, currentRoomId,
            Map.of(), List.of(), 0, null,
            hp, 5, shields, 2,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);
    }
}
