package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.service.DungeonSessionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonCollectValidationTests {

    private final DungeonSessionService service = new DungeonSessionService(null, null, null, null, null, null, null, null);

    @Test
    void collectCoin_rejectedWhenStateNull() {
        assertThat(service.collectCoin(null, "r0_coin_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectCoin_incrementsScoreInExplorationState() {
        DungeonSessionState state = baseState().build();
        CollectResult result = service.collectCoin(state, "r0_coin_0");
        assertThat(result.status()).isEqualTo(CollectStatus.OK);
        assertThat(result.state().resources().score()).isEqualTo(state.resources().score() + 1);
        assertThat(result.state().collectedItems()).contains("r0_coin_0");
    }

    @Test
    void collectCoin_duplicateRejected() {
        DungeonSessionState state = baseState().build()
            .withCollectedItems(Set.of("r0_coin_0"));
        CollectResult result = service.collectCoin(state, "r0_coin_0");
        assertThat(result.status()).isEqualTo(CollectStatus.DUPLICATE);
        assertThat(result.state().resources().score()).isEqualTo(state.resources().score());
    }

    @Test
    void collectCoin_rejectsMissingItemId() {
        DungeonSessionState state = baseState().build();
        assertThat(service.collectCoin(state, null).status())
            .isEqualTo(CollectStatus.REJECTED);
        assertThat(service.collectCoin(state, "").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectCoin_rejectsMalformedItemId() {
        DungeonSessionState state = baseState().build();
        assertThat(service.collectCoin(state, "r1_coin_0").status())
            .isEqualTo(CollectStatus.REJECTED);
        assertThat(service.collectCoin(state, "r0_shield_0").status())
            .isEqualTo(CollectStatus.REJECTED);
        assertThat(service.collectCoin(state, "r0_coin_abc").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectCoin_rejectsWhenDefeated() {
        DungeonSessionState state = baseState().defeated(true).build();
        assertThat(service.collectCoin(state, "r0_coin_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectCoin_rejectsWhenWon() {
        DungeonSessionState state = baseState().won(true).build();
        assertThat(service.collectCoin(state, "r0_coin_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectCoin_rejectsWhenEncounterActive() {
        DungeonSessionState state = baseState().activeEncounterId("enc_1").build();
        assertThat(service.collectCoin(state, "r0_coin_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectCoin_rejectsWhenRelicPickPending() {
        DungeonSessionState state = baseState()
            .pendingRelicPick(new PendingRelicPick(
                PendingPickType.TREASURE, "r0", List.of(RelicId.IRON_PLATE), null))
            .build();
        assertThat(service.collectCoin(state, "r0_coin_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectShield_rejectedWhenStateNull() {
        assertThat(service.collectShield(null, "r0_shield_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectShield_incrementsShieldsBelowCap() {
        DungeonSessionState state = baseState().shields(0).shieldCap(2).build();
        CollectResult result = service.collectShield(state, "r0_shield_1");
        assertThat(result.status()).isEqualTo(CollectStatus.OK);
        assertThat(result.state().resources().shields()).isEqualTo(1);
        assertThat(result.state().collectedItems()).contains("r0_shield_1");
    }

    @Test
    void collectShield_duplicateRejected() {
        DungeonSessionState state = baseState().shields(0).shieldCap(2).build()
            .withCollectedItems(Set.of("r0_shield_1"));
        CollectResult result = service.collectShield(state, "r0_shield_1");
        assertThat(result.status()).isEqualTo(CollectStatus.DUPLICATE);
    }

    @Test
    void collectShield_rejectedWhenAtCap() {
        DungeonSessionState state = baseState().shields(2).shieldCap(2).build();
        assertThat(service.collectShield(state, "r0_shield_1").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectShield_rejectsWhenDefeated() {
        DungeonSessionState state = baseState().shields(0).shieldCap(2).defeated(true).build();
        assertThat(service.collectShield(state, "r0_shield_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectShield_rejectsWhenWon() {
        DungeonSessionState state = baseState().shields(0).shieldCap(2).won(true).build();
        assertThat(service.collectShield(state, "r0_shield_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectShield_rejectsWhenEncounterActive() {
        DungeonSessionState state = baseState()
            .shields(0).shieldCap(2).activeEncounterId("enc_1").build();
        assertThat(service.collectShield(state, "r0_shield_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectShield_rejectsWhenRelicPickPending() {
        DungeonSessionState state = baseState()
            .shields(0).shieldCap(2)
            .pendingRelicPick(new PendingRelicPick(
                PendingPickType.TREASURE, "r0", List.of(RelicId.IRON_PLATE), null))
            .build();
        assertThat(service.collectShield(state, "r0_shield_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectCoin_rejectsOutOfRangeIndex() {
        DungeonSessionState state = baseState().build();
        assertThat(service.collectCoin(state, "r0_coin_5").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectCoin_rejectsIdPointingAtNonCoinPot() {
        // pot index 1 is a SHIELD, not a COIN
        DungeonSessionState state = baseState().build();
        assertThat(service.collectCoin(state, "r0_coin_1").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    @Test
    void collectShield_rejectsIdPointingAtNonShieldPot() {
        // pot index 0 is a COIN, not a SHIELD
        DungeonSessionState state = baseState().shields(0).shieldCap(2).build();
        assertThat(service.collectShield(state, "r0_shield_0").status())
            .isEqualTo(CollectStatus.REJECTED);
    }

    private static StateBuilder baseState() {
        return new StateBuilder();
    }

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
                true, true, null, List.of(), null, null, null, null,
                List.of(PotLoot.COIN, PotLoot.SHIELD));
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
                .combat(new DungeonCombat(activeEncounterId, List.of(), 0))
                .resources(new DungeonResources(health, healthCap, shields, shieldCap, score))
                .progress(new DungeonProgress(0, 0, 0, 0, 0, 0, 0))
                .won(won)
                .defeated(defeated)
                .loadout(new DungeonLoadout(List.of(), pendingRelicPick))
                .build();
        }
    }
}
