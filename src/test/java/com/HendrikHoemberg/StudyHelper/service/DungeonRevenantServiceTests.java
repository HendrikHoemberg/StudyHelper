package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonRevenantServiceTests {

    private final DungeonRevenantService svc = new DungeonRevenantService();

    /** a(entrance) -- b(combat) -- c(boss); player starts at a. SMALL => cap 2. */
    private DungeonSessionState lineState() {
        DungeonRoom a = new DungeonRoom("a", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "b"), new GridPos(0, 0), true, true,
            null, List.of(), null, null, null, List.of());
        DungeonRoom b = new DungeonRoom("b", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "a", DungeonDirection.RIGHT, "c"),
            new GridPos(1, 0), true, false, "fc_0", List.of(), null, null, null, List.of());
        DungeonRoom c = new DungeonRoom("c", RoomType.BOSS,
            Map.of(DungeonDirection.LEFT, "b"), new GridPos(2, 0), false, false,
            null, List.of(), null, null, null, List.of());
        DungeonMap map = new DungeonMap(Map.of("a", a, "b", b, "c", c), "a", "c", 3);

        Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
        encs.put("fc_0", DungeonEncounter.flashcard("fc_0", false, "SLIME", 1L, "Q0", "A0", null, null));
        encs.put("fc_1", DungeonEncounter.flashcard("fc_1", false, "GHOST", 2L, "Q1", "A1", null, null));
        encs.put("fc_2", DungeonEncounter.flashcard("fc_2", false, "GOBLIN", 3L, "Q2", "A2", null, null));

        return DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map).currentRoomId("a").encounters(encs)
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .build();
    }

    @Test
    void spawnAddsRoamerUnderCap() {
        DungeonSessionState s = svc.spawnFrom(lineState(), "fc_0", "b");
        assertThat(s.revenants()).hasSize(1);
        assertThat(s.revenants().get(0).encounterId()).isEqualTo("fc_0");
        assertThat(s.revenants().get(0).originRoomId()).isEqualTo("b");
        assertThat(s.revenants().get(0).currentRoomId()).isEqualTo("b");
        assertThat(s.revenantGraveyard()).isEmpty();
    }

    @Test
    void spawnBeyondCapGoesToGraveyard() {
        DungeonSessionState s = lineState();        // SMALL cap = 2
        s = svc.spawnFrom(s, "fc_0", "b");
        s = svc.spawnFrom(s, "fc_1", "b");
        s = svc.spawnFrom(s, "fc_2", "c");          // 3rd -> graveyard
        assertThat(s.revenants()).hasSize(2);
        assertThat(s.revenantGraveyard()).hasSize(1);
        assertThat(s.revenantGraveyard().get(0).encounterId()).isEqualTo("fc_2");
    }

    @Test
    void advanceStepsRoamerOneRoomTowardPlayer() {
        DungeonSessionState s = lineState().withCurrentRoomId("a");
        s = svc.spawnFrom(s, "fc_0", "c"); // roamer at far end
        DungeonRevenantService.AdvanceResult r = svc.advance(s);
        assertThat(r.contacted()).isFalse();
        assertThat(r.state().revenants().get(0).currentRoomId()).isEqualTo("b"); // c -> b
    }

    @Test
    void advanceDetectsContactWhenRoamerReachesPlayer() {
        DungeonSessionState s = lineState().withCurrentRoomId("a");
        s = svc.spawnFrom(s, "fc_0", "b"); // one room away
        DungeonRevenantService.AdvanceResult r = svc.advance(s);
        assertThat(r.contacted()).isTrue();
        assertThat(r.state().combat().activeRevenantId()).isEqualTo("fc_0");
        assertThat(r.state().combat().activeEncounterId()).isEqualTo("fc_0"); // poses the card
        assertThat(r.state().revenants().get(0).currentRoomId()).isEqualTo("a");
    }

    @Test
    void advanceDetectsContactWhenPlayerWalksOntoRoamer() {
        DungeonSessionState s = lineState().withCurrentRoomId("b");
        s = svc.spawnFrom(s, "fc_0", "b"); // already co-located
        DungeonRevenantService.AdvanceResult r = svc.advance(s);
        assertThat(r.contacted()).isTrue();
        assertThat(r.state().combat().activeRevenantId()).isEqualTo("fc_0");
    }

    @Test
    void resolveCorrectBanishesAndScoresAndMasters() {
        DungeonSessionState s = lineState().withCurrentRoomId("a");
        s = svc.spawnFrom(s, "fc_0", "b");
        s = svc.advance(s).state();                 // contact, activeRevenantId = fc_0
        DungeonSessionState after = svc.resolveReTest(s, true);
        assertThat(after.revenants()).isEmpty();
        assertThat(after.combat().activeRevenantId()).isNull();
        assertThat(after.combat().activeEncounterId()).isNull();
        assertThat(after.progress().revenantsMastered()).isEqualTo(1);
        assertThat(after.resources().score()).isEqualTo(DungeonBalance.REVENANT_MASTERY_SCORE);
    }

    @Test
    void resolveCorrectPullsNextFromGraveyardAtOrigin() {
        DungeonSessionState s = lineState().withCurrentRoomId("a"); // cap 2
        s = svc.spawnFrom(s, "fc_0", "b");
        s = svc.spawnFrom(s, "fc_1", "b");
        s = svc.spawnFrom(s, "fc_2", "c");          // graveyard
        s = svc.advance(s).state();                 // a roamer reaches/contacts; activeRevenantId set
        DungeonSessionState after = svc.resolveReTest(s, true);
        assertThat(after.revenantGraveyard()).isEmpty();
        assertThat(after.revenants()).hasSize(2);
        assertThat(after.revenants()).anyMatch(rev -> rev.encounterId().equals("fc_2")
            && rev.currentRoomId().equals("c"));
    }

    @Test
    void resolveWrongBitesAndRepelsToOrigin() {
        DungeonSessionState s = lineState().withCurrentRoomId("a");
        s = svc.spawnFrom(s, "fc_0", "c");          // origin = c (far)
        // walk it in: advance twice so it reaches the player at a
        s = svc.advance(s).state();                 // c -> b
        s = svc.advance(s).state();                 // b -> a => contact
        assertThat(s.combat().activeRevenantId()).isEqualTo("fc_0");
        int hpBefore = s.resources().health();
        DungeonSessionState after = svc.resolveReTest(s, false);
        assertThat(after.resources().health()).isEqualTo(hpBefore - DungeonBalance.WRONG_ANSWER_DAMAGE);
        assertThat(after.combat().activeRevenantId()).isNull();
        assertThat(after.revenants()).hasSize(1);
        assertThat(after.revenants().get(0).currentRoomId()).isEqualTo("c"); // repelled to origin
    }

    @Test
    void advanceWithNoRevenantsIsNoOp() {
        DungeonSessionState s = lineState();
        DungeonRevenantService.AdvanceResult r = svc.advance(s);
        assertThat(r.contacted()).isFalse();
        assertThat(r.state()).isSameAs(s);
    }
}
