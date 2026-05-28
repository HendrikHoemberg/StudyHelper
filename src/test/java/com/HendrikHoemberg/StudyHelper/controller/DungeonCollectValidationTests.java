package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonCollectValidationTests {

    @Test
    void tryCollectCoin_returnsNullWhenStateNull() {
        assertThat(DungeonController.tryCollectCoin(null)).isNull();
    }

    @Test
    void tryCollectCoin_incrementsScoreInExplorationState() {
        DungeonSessionState state = baseState().build();
        DungeonSessionState next = DungeonController.tryCollectCoin(state);
        assertThat(next).isNotNull();
        assertThat(next.score()).isEqualTo(state.score() + 1);
    }

    @Test
    void tryCollectCoin_rejectsWhenDefeated() {
        DungeonSessionState state = baseState().defeated(true).build();
        assertThat(DungeonController.tryCollectCoin(state)).isNull();
    }

    @Test
    void tryCollectCoin_rejectsWhenWon() {
        DungeonSessionState state = baseState().won(true).build();
        assertThat(DungeonController.tryCollectCoin(state)).isNull();
    }

    @Test
    void tryCollectCoin_rejectsWhenEncounterActive() {
        DungeonSessionState state = baseState().activeEncounterId("enc_1").build();
        assertThat(DungeonController.tryCollectCoin(state)).isNull();
    }

    @Test
    void tryCollectCoin_rejectsWhenRelicPickPending() {
        DungeonSessionState state = baseState()
            .pendingRelicPick(new PendingRelicPick(
                PendingPickType.TREASURE, "r0", List.of(RelicId.IRON_PLATE), null))
            .build();
        assertThat(DungeonController.tryCollectCoin(state)).isNull();
    }

    @Test
    void tryCollectShield_returnsNullWhenStateNull() {
        assertThat(DungeonController.tryCollectShield(null)).isNull();
    }

    @Test
    void tryCollectShield_incrementsShieldsBelowCap() {
        DungeonSessionState state = baseState().shields(0).shieldCap(2).build();
        DungeonSessionState next = DungeonController.tryCollectShield(state);
        assertThat(next).isNotNull();
        assertThat(next.shields()).isEqualTo(1);
    }

    @Test
    void tryCollectShield_returnsNullWhenAtCap() {
        DungeonSessionState state = baseState().shields(2).shieldCap(2).build();
        assertThat(DungeonController.tryCollectShield(state)).isNull();
    }

    @Test
    void tryCollectShield_rejectsWhenDefeated() {
        DungeonSessionState state = baseState().shields(0).shieldCap(2).defeated(true).build();
        assertThat(DungeonController.tryCollectShield(state)).isNull();
    }

    @Test
    void tryCollectShield_rejectsWhenWon() {
        DungeonSessionState state = baseState().shields(0).shieldCap(2).won(true).build();
        assertThat(DungeonController.tryCollectShield(state)).isNull();
    }

    @Test
    void tryCollectShield_rejectsWhenEncounterActive() {
        DungeonSessionState state = baseState()
            .shields(0).shieldCap(2).activeEncounterId("enc_1").build();
        assertThat(DungeonController.tryCollectShield(state)).isNull();
    }

    @Test
    void tryCollectShield_rejectsWhenRelicPickPending() {
        DungeonSessionState state = baseState()
            .shields(0).shieldCap(2)
            .pendingRelicPick(new PendingRelicPick(
                PendingPickType.TREASURE, "r0", List.of(RelicId.IRON_PLATE), null))
            .build();
        assertThat(DungeonController.tryCollectShield(state)).isNull();
    }

    private static StateBuilder baseState() {
        return new StateBuilder();
    }

    /** Minimal builder so each test only sets the fields it cares about. */
    private static final class StateBuilder {
        String activeEncounterId = null;
        int health = 5;
        int healthCap = 5;
        int shields = 1;
        int shieldCap = 2;
        int score = 10;
        boolean won = false;
        boolean defeated = false;
        PendingRelicPick pendingRelicPick = null;

        StateBuilder activeEncounterId(String v) { this.activeEncounterId = v; return this; }
        StateBuilder shields(int v) { this.shields = v; return this; }
        StateBuilder shieldCap(int v) { this.shieldCap = v; return this; }
        StateBuilder won(boolean v) { this.won = v; return this; }
        StateBuilder defeated(boolean v) { this.defeated = v; return this; }
        StateBuilder pendingRelicPick(PendingRelicPick v) { this.pendingRelicPick = v; return this; }

        DungeonSessionState build() {
            DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
                Map.of(), new GridPos(0, 0),
                true, true, null, List.of(), null, null, null, null);
            DungeonMap map = new DungeonMap(Map.of("r0", r0), "r0", "r0", 3);
            DungeonConfig config = new DungeonConfig(
                DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
            return new DungeonSessionState(
                config, map, "r0",
                Map.of(), List.of(), 0, activeEncounterId,
                health, healthCap, shields, shieldCap,
                score, 0, 0,
                won, defeated,
                0, List.of(),
                0, 0, 0, 0,
                List.of(), pendingRelicPick);
        }
    }
}
