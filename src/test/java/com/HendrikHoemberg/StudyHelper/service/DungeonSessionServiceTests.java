package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.support.TestStates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DungeonSessionServiceTests {

    @Test
    void move_routesNavigationResultAndActivatesEncounter() {
        DungeonNavigationService nav = mock(DungeonNavigationService.class);
        DungeonEncounterService enc = mock(DungeonEncounterService.class);
        DungeonRelicService relic = mock(DungeonRelicService.class);
        DungeonSessionState before = stateOnRoom("r0", RoomType.ENTRANCE);
        DungeonSessionState afterMove = stateOnRoom("r1", RoomType.COMBAT);
        DungeonSessionState afterActivate = afterMove;
        when(nav.move(before, DungeonDirection.RIGHT))
            .thenReturn(new DungeonNavigationService.MoveResult(afterMove, "r1"));
        when(enc.activateAt(afterMove, "r1")).thenReturn(afterActivate);

        DungeonSessionService svc = new DungeonSessionService(
            null, null, null, null, nav, enc, relic);
        DungeonSessionState result = svc.move(before, DungeonDirection.RIGHT);

        assertThat(result).isSameAs(afterActivate);
        verify(enc).activateAt(afterMove, "r1");
    }

    @Test
    void move_intoTreasureSetsPendingPick() {
        DungeonNavigationService nav = mock(DungeonNavigationService.class);
        DungeonEncounterService enc = mock(DungeonEncounterService.class);
        DungeonRelicService relic = mock(DungeonRelicService.class);

        TreasureOffer offer = new TreasureOffer(List.of(RelicId.IRON_PLATE, RelicId.BUCKLER, RelicId.LUCKY_CHARM));
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.TREASURE,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, null, List.of(), offer, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r0", 3);
        DungeonSessionState before = stateOnRoomMap(map, "r0");
        DungeonSessionState afterMove = stateOnRoomMap(map, "r1");
        when(nav.move(any(), any()))
            .thenReturn(new DungeonNavigationService.MoveResult(afterMove, null));

        DungeonSessionService svc = new DungeonSessionService(
            null, null, null, null, nav, enc, relic);
        DungeonSessionState result = svc.move(before, DungeonDirection.RIGHT);

        assertThat(result.loadout().pendingRelicPick()).isNotNull();
        assertThat(result.loadout().pendingRelicPick().type()).isEqualTo(PendingPickType.TREASURE);
        assertThat(result.loadout().pendingRelicPick().offer()).hasSize(3);
        verifyNoInteractions(enc);
    }

    @Test
    void buildStats_includesRelicsAcquired() {
        DungeonSessionService svc = new DungeonSessionService(
            null, null, null, null, null, null, null);
        DungeonSessionState s = stateWithRelics(List.of(RelicId.IRON_PLATE, RelicId.BUCKLER));
        DungeonRunStats stats = svc.buildStats(s);
        assertThat(stats.relicsAcquired()).isEqualTo(2);
    }

    private DungeonSessionState stateOnRoom(String roomId, RoomType type) {
        DungeonRoom room = new DungeonRoom(roomId, type,
            Map.of(), new GridPos(0, 0),
            true, false, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of(roomId, room), roomId, roomId, 3);
        return stateOnRoomMap(map, roomId);
    }

    private DungeonSessionState stateOnRoomMap(DungeonMap map, String roomId) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return DungeonSessionState.builder()
            .config(config)
            .map(map)
            .currentRoomId(roomId)
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .build();
    }

    private DungeonSessionState stateWithRelics(List<RelicId> relics) {
        return TestStates.minimalWithRelics(relics);
    }

    @Test
    void shrineLeave_clearsPendingPickAndMarksRoomCleared() {
        DungeonSessionService svc = new DungeonSessionService(
            null, null, null, null, null, null, null);
        DungeonSessionState s = stateOnRoom("r0", RoomType.SHRINE);
        s = s.withLoadout(s.loadout().withPendingRelicPick(
            new PendingRelicPick(PendingPickType.SHRINE, "r0", List.of(), null)));

        DungeonSessionState after = svc.shrineLeave(s);
        assertThat(after.loadout().pendingRelicPick()).isNull();
        assertThat(after.map().room("r0").cleared()).isTrue();
    }

    @Test
    void shrineDrink_healsAndClearsRoom() {
        DungeonSessionService svc = new DungeonSessionService(
            null, null, null, null, null, null, null);
        DungeonSessionState s = stateOnRoom("r0", RoomType.SHRINE);
        s = s.withResources(new DungeonResources(3, 5, s.resources().shields(), s.resources().shieldCap(), s.resources().score()));

        DungeonSessionState after = svc.shrineDrink(s);
        assertThat(after.resources().health()).isEqualTo(4);
        assertThat(after.map().room("r0").cleared()).isTrue();
    }

    @Test
    void shrineRoll_onSixGrantsRelic_onOthersDealsDamage() {
        DungeonSessionService svc = new DungeonSessionService(
            null, null, null, null, null, null, null);
        DungeonSessionState s = stateOnRoom("r0", RoomType.SHRINE);

        // Test roll 6
        DungeonSessionState afterSix = svc.shrineRoll(s, 6, RelicId.IRON_PLATE);
        assertThat(afterSix.loadout().ownedRelics()).contains(RelicId.IRON_PLATE);
        assertThat(afterSix.resources().healthCap()).isEqualTo(6); // iron plate increases cap by 1
        assertThat(afterSix.defeated()).isFalse();

        // Test roll 1-5
        DungeonSessionState afterFail = svc.shrineRoll(s, 3, null);
        assertThat(afterFail.resources().health()).isEqualTo(4); // 5 - 1 = 4
        assertThat(afterFail.defeated()).isFalse();
    }
}
