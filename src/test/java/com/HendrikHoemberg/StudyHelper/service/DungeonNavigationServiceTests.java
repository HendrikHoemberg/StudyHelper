package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.support.TestStates;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonNavigationServiceTests {

    private final DungeonNavigationService nav = new DungeonNavigationService();

    @Test
    void move_intoRoomWithoutDoorReturnsUnchangedState() {
        DungeonSessionState state = twoRoomState();
        ActionResult result = nav.move(state, DungeonDirection.UP);
        assertThat(result.state().currentRoomId()).isEqualTo("r0");
    }

    @Test
    void move_throughDoorUpdatesCurrentRoomAndMarksVisited() {
        DungeonSessionState state = twoRoomState();
        ActionResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().currentRoomId()).isEqualTo("r1");
        assertThat(result.state().map().room("r1").visited()).isTrue();
    }

    @Test
    void move_intoCombatRoomSignalsEncounterActivation() {
        DungeonSessionState state = combatNeighborState();
        ActionResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().currentRoomId()).isEqualTo("r1");
    }

    @Test
    void move_intoHealRoomAppliesFullHealAndShieldGrantAndClearsOnExit() {
        DungeonSessionState state = healNeighborStateWithLowHpAndNoShields();
        ActionResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().resources().health()).isEqualTo(result.state().resources().healthCap());
        assertThat(result.state().resources().shields()).isEqualTo(1);
        assertThat(result.state().map().room("r1").cleared()).isFalse(); // Uncleared initially so centerpiece is visible

        // Move out of HEAL room back to Entrance
        ActionResult exitResult = nav.move(result.state(), DungeonDirection.LEFT);
        assertThat(exitResult.state().map().room("r1").cleared()).isTrue(); // Marked cleared on exit
    }

    @Test
    void move_intoAlreadyClearedHealRoomDoesNothingExtra() {
        DungeonSessionState state = clearedHealNeighborState();
        int hpBefore = state.resources().health();
        int shieldsBefore = state.resources().shields();
        ActionResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().resources().health()).isEqualTo(hpBefore);
        assertThat(result.state().resources().shields()).isEqualTo(shieldsBefore);
    }

    @Test
    void move_blockedWhileEncounterActive() {
        DungeonSessionState state = encounterActiveState();
        ActionResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state()).isSameAs(state);
    }

    @Test
    void move_blockedWhenRunIsComplete() {
        DungeonSessionState state = wonState();
        ActionResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state()).isSameAs(state);
    }

    private DungeonSessionState twoRoomState() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            false, false, null, List.of(), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 5, 0);
    }

    private DungeonSessionState combatNeighborState() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            false, false, "enc_0", List.of(), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 5, 0);
    }

    private DungeonSessionState healNeighborStateWithLowHpAndNoShields() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.HEAL,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            false, false, null, List.of(), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 2, 0);
    }

    private DungeonSessionState clearedHealNeighborState() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.HEAL,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, true, null, List.of(), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 3, 1);
    }

    private DungeonSessionState encounterActiveState() {
        DungeonSessionState base = combatNeighborState();
        return base.withCombat(base.combat().withActiveEncounterId("enc_0"));
    }

    private DungeonSessionState wonState() {
        return twoRoomState().withWon(true);
    }

    private DungeonSessionState baseState(DungeonMap map, String currentRoomId,
                                            int hp, int shields) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return DungeonSessionState.builder()
            .config(config).map(map).currentRoomId(currentRoomId)
            .encounters(Map.of()).bossEncounterIds(List.of())
            .resources(new DungeonResources(hp, 5, shields, 2, 0))
            .build();
    }
}
