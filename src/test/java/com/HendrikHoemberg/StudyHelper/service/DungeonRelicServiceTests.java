package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonRelicServiceTests {

    private final DungeonRelicService svc = new DungeonRelicService();

    @Test
    void pick_addsRelicAndClearsPendingPick() {
        DungeonSessionState state = stateWithPick(PendingPickType.TREASURE,
            List.of(RelicId.LUCKY_CHARM, RelicId.SHARP_FOCUS, RelicId.COMPASS));
        DungeonSessionState after = svc.pick(state, RelicId.LUCKY_CHARM);
        assertThat(after.loadout().ownedRelics()).containsExactly(RelicId.LUCKY_CHARM);
        assertThat(after.loadout().pendingRelicPick()).isNull();
    }

    @Test
    void pick_relicNotInOfferIsRejected() {
        DungeonSessionState state = stateWithPick(PendingPickType.TREASURE,
            List.of(RelicId.LUCKY_CHARM, RelicId.SHARP_FOCUS, RelicId.COMPASS));
        DungeonSessionState after = svc.pick(state, RelicId.IRON_PLATE);
        assertThat(after).isSameAs(state);
    }

    @Test
    void pick_ironPlateIncreasesHealthCapAndHeals() {
        DungeonSessionState state = stateWithPick(PendingPickType.TREASURE,
            List.of(RelicId.IRON_PLATE, RelicId.BUCKLER, RelicId.COMPASS));
        DungeonSessionState after = svc.pick(state, RelicId.IRON_PLATE);
        assertThat(after.resources().healthCap()).isEqualTo(state.resources().healthCap() + 1);
        assertThat(after.resources().health()).isEqualTo(Math.min(state.resources().health() + 1, after.resources().healthCap()));
    }

    @Test
    void pick_bucklerIncreasesShieldCapAndGrantsShield() {
        DungeonSessionState state = stateWithPick(PendingPickType.TREASURE,
            List.of(RelicId.IRON_PLATE, RelicId.BUCKLER, RelicId.COMPASS));
        DungeonSessionState after = svc.pick(state, RelicId.BUCKLER);
        assertThat(after.resources().shieldCap()).isEqualTo(state.resources().shieldCap() + 1);
        assertThat(after.resources().shields()).isEqualTo(state.resources().shields() + 1);
    }

    @Test
    void buy_deductsScoreAndAddsRelic() {
        DungeonSessionState state = shopStateWithScore(150);
        DungeonSessionState after = svc.buy(state, RelicId.LUCKY_CHARM);
        assertThat(after.loadout().ownedRelics()).contains(RelicId.LUCKY_CHARM);
        assertThat(after.resources().score()).isEqualTo(150 - 75);
    }

    @Test
    void buy_rejectsWhenInsufficientScore() {
        DungeonSessionState state = shopStateWithScore(50);
        DungeonSessionState after = svc.buy(state, RelicId.LUCKY_CHARM);
        assertThat(after).isSameAs(state);
    }

    @Test
    void buy_doesNotClearShopPick_canMakeMultiplePurchases() {
        DungeonSessionState state = shopStateWithScore(150);
        DungeonSessionState after = svc.buy(state, RelicId.LUCKY_CHARM);
        assertThat(after.loadout().pendingRelicPick()).isNotNull();
        assertThat(after.loadout().pendingRelicPick().type()).isEqualTo(PendingPickType.SHOP);
    }

    @Test
    void skipShop_marksShopClearedAndClearsPick() {
        DungeonSessionState state = shopStateWithScore(150);
        DungeonSessionState after = svc.skipShop(state);
        assertThat(after.loadout().pendingRelicPick()).isNull();
        assertThat(after.map().room("r1").cleared()).isTrue();
    }

    private DungeonSessionState stateWithPick(PendingPickType type, List<RelicId> offer) {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.TREASURE,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, null, List.of(), new TreasureOffer(offer), null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r0", 3);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        PendingRelicPick pick = new PendingRelicPick(type, "r1", offer, null);
        return DungeonSessionState.builder()
            .config(config).map(map).currentRoomId("r1")
            .encounters(Map.of()).bossEncounterIds(List.of())
            .resources(new DungeonResources(4, 5, 0, 2, 0))
            .loadout(new DungeonLoadout(List.of(), pick))
            .build();
    }

    private DungeonSessionState shopStateWithScore(int score) {
        ShopOffer offer = new ShopOffer(
            List.of(new ShopOfferEntry(RelicId.LUCKY_CHARM, 75),
                    new ShopOfferEntry(RelicId.MAP_SENSE, 150)),
            new ShopConsumable("dungeon.shop.consumable.heal", 30, 2));
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.SHOP,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, null, List.of(), null, null, offer);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r0", 3);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        PendingRelicPick pick = new PendingRelicPick(PendingPickType.SHOP, "r1", List.of(), offer);
        return DungeonSessionState.builder()
            .config(config).map(map).currentRoomId("r1")
            .encounters(Map.of()).bossEncounterIds(List.of())
            .resources(new DungeonResources(5, 5, 0, 2, score))
            .loadout(new DungeonLoadout(List.of(), pick))
            .build();
    }
}
