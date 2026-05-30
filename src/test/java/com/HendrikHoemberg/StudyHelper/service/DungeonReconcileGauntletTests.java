package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.support.DungeonStateInvariants;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DungeonReconcileGauntletTests {

    /** entrance r0 -> combat r1 (alive) -> elite r2 [e0,e1] -> boss r3. */
    private DungeonSessionState stateWithEliteGauntlet() {
        DungeonEncounter n0 = DungeonEncounter.flashcard("n0", false, "SLIME", 300L, "Q", "A", null, null);
        DungeonEncounter e0 = DungeonEncounter.flashcard("e0", false, "CHAMPION", 100L, "Q", "A", null, null);
        DungeonEncounter e1 = DungeonEncounter.flashcard("e1", false, "CHAMPION", 101L, "Q", "A", null, null);
        DungeonEncounter b0 = DungeonEncounter.flashcard("b0", true, "DRAGON", 200L, "Q", "A", null, null);

        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0), true, true,
            null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0", DungeonDirection.RIGHT, "r2"), new GridPos(1, 0), true, false,
            "n0", List.of(), null, null, null);
        DungeonRoom r2 = new DungeonRoom("r2", RoomType.ELITE,
            Map.of(DungeonDirection.LEFT, "r1", DungeonDirection.RIGHT, "r3"), new GridPos(2, 0), false, false,
            "e0", List.of("e0", "e1"), null, new TreasureOffer(List.of(RelicId.IRON_PLATE)), null);
        DungeonRoom r3 = new DungeonRoom("r3", RoomType.BOSS,
            Map.of(DungeonDirection.LEFT, "r2"), new GridPos(3, 0), false, false,
            null, List.of(), null, null, null);
        DungeonMap map = new DungeonMap(
            Map.of("r0", r0, "r1", r1, "r2", r2, "r3", r3), "r0", "r3", 5);

        return DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map).currentRoomId("r0")
            .encounters(Map.of("n0", n0, "e0", e0, "e1", e1, "b0", b0))
            .bossEncounterIds(List.of("b0"))
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .build();
    }

    private SavedSessionService serviceWithAlive(Long... aliveFlashcardIds) {
        FlashcardRepository fcRepo = mock(FlashcardRepository.class);
        when(fcRepo.findExistingIdsByIdIn(anyCollection())).thenReturn(List.of(aliveFlashcardIds));
        return new SavedSessionService(null, fcRepo, new ObjectMapper(), null, null);
    }

    @Test
    void deletedNonFirstGauntletMember_skipsWholeEliteRoom() {
        // e1 (101L) deleted; n0, e0, b0 alive.
        SavedSessionService svc = serviceWithAlive(300L, 100L, 200L);

        SavedSessionService.ReconcileDungeonResult result =
            svc.reconcileDungeonFlashcards(stateWithEliteGauntlet(), new User());
        DungeonSessionState next = result.state();

        assertThat(next.map().room("r2").cleared()).isTrue();
        assertThat(next.map().room("r2").visited()).isTrue();
        assertThat(next.encounters()).doesNotContainKeys("e0", "e1");
        assertThat(next.encounters()).containsKeys("n0", "b0");
        assertThat(result.canContinue()).isTrue();
        DungeonStateInvariants.assertValid(next);
    }

    @Test
    void deletedFirstGauntletMember_skipsWholeEliteRoom() {
        // e0 (100L) deleted; n0, e1, b0 alive.
        SavedSessionService svc = serviceWithAlive(300L, 101L, 200L);

        SavedSessionService.ReconcileDungeonResult result =
            svc.reconcileDungeonFlashcards(stateWithEliteGauntlet(), new User());
        DungeonSessionState next = result.state();

        assertThat(next.map().room("r2").cleared()).isTrue();
        assertThat(next.encounters()).doesNotContainKeys("e0", "e1");
        assertThat(next.encounters()).containsKeys("n0", "b0");
        DungeonStateInvariants.assertValid(next);
    }

    @Test
    void deletedGauntletMemberInQueue_clearsQueueEntry() {
        // e1 (101L) deleted; active encounter e0 (100L) alive but in same gauntlet → whole room skipped.
        // Combat has activeEncounterId="e0" and gauntletQueue=["e1"].
        SavedSessionService svc = serviceWithAlive(300L, 100L, 200L);

        DungeonSessionState base = stateWithEliteGauntlet();
        DungeonSessionState state = base.toBuilder()
            .combat(new DungeonCombat("e0", List.of("e1"), 0, null))
            .build();

        SavedSessionService.ReconcileDungeonResult result =
            svc.reconcileDungeonFlashcards(state, new User());
        DungeonSessionState next = result.state();

        assertThat(next.map().room("r2").cleared()).isTrue();
        assertThat(next.combat().activeEncounterId()).isNull();
        assertThat(next.combat().gauntletQueue()).isEmpty();
        assertThat(next.encounters()).doesNotContainKeys("e0", "e1");
        assertThat(next.encounters()).containsKeys("n0", "b0");
        assertThat(result.canContinue()).isTrue();
        DungeonStateInvariants.assertValid(next);
    }
}
