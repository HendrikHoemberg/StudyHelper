package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonEncounterServiceTests {

    private final DungeonEncounterService svc = new DungeonEncounterService();

    @Test
    void activateAt_combatRoomMarksEncounterActive() {
        DungeonSessionState state = singleCombatRoomState();
        DungeonSessionState result = svc.activateAt(state, "r1");
        assertThat(result.activeEncounterId()).isEqualTo("enc_0");
    }

    @Test
    void activateAt_eliteRoomSetsGauntletQueueAndActivatesFirstEncounter() {
        DungeonSessionState state = eliteRoomState();
        DungeonSessionState result = svc.activateAt(state, "r1");
        assertThat(result.activeEncounterId()).isEqualTo("elite_0_0");
        assertThat(result.gauntletQueue()).containsExactly("elite_0_1");
    }

    @Test
    void answerFlashcard_correctIncrementsStreakAndCorrectCount() {
        DungeonSessionState state = activeFlashcardState();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.streak()).isEqualTo(1);
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.activeEncounterId()).isNull();
        assertThat(result.map().room("r1").cleared()).isTrue();
    }

    @Test
    void answerFlashcard_wrongTakesDamageAndResetsStreak() {
        DungeonSessionState state = activeFlashcardStateWithStreak(2);
        DungeonSessionState result = svc.answerFlashcard(state, false);
        assertThat(result.streak()).isEqualTo(0);
        assertThat(result.health()).isEqualTo(state.health() - 1);
    }

    @Test
    void answerFlashcard_thirdCorrectAnswerBanksAShield() {
        DungeonSessionState state = activeFlashcardStateWithStreak(2);
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.streak()).isEqualTo(3);
        assertThat(result.shields()).isEqualTo(state.shields() + 1);
    }

    @Test
    void answerFlashcard_eliteGauntletClearSetsPendingRelicPick() {
        DungeonSessionState state = activeEliteFinalStepState();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.pendingRelicPick()).isNotNull();
        assertThat(result.pendingRelicPick().type()).isEqualTo(PendingPickType.ELITE);
        assertThat(result.pendingRelicPick().roomId()).isEqualTo("r1");
        assertThat(result.elitesCleared()).isEqualTo(1);
        assertThat(result.health()).isEqualTo(result.healthCap());
    }

    @Test
    void answerFlashcard_eliteGauntletMidWrongAnswerAbortsAndKeepsRoomActive() {
        DungeonSessionState state = activeEliteMidStepState();
        DungeonSessionState result = svc.answerFlashcard(state, false);
        assertThat(result.activeEncounterId()).isNull();
        assertThat(result.gauntletQueue()).isEmpty();
        assertThat(result.map().room("r1").cleared()).isFalse();
    }

    @Test
    void answerFlashcard_bossSequenceProgressesAcrossPrompts() {
        DungeonSessionState state = activeBossStateAtIndexZero();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.bossIndex()).isEqualTo(1);
        assertThat(result.activeEncounterId()).isEqualTo("boss_1");
        assertThat(result.won()).isFalse();
    }

    @Test
    void answerFlashcard_bossSequenceFinalPromptWinsTheRun() {
        DungeonSessionState state = activeBossStateAtFinalPrompt();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.won()).isTrue();
        assertThat(result.activeEncounterId()).isNull();
    }

    // ===== fixtures =====

    private DungeonSessionState singleCombatRoomState() {
        DungeonEncounter enc = DungeonEncounter.flashcard(
            "enc_0", false, 1L, "Q", "A", null, null);
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, "enc_0", List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r1", Map.of("enc_0", enc), List.of(), 0, null);
    }

    private DungeonSessionState eliteRoomState() {
        DungeonEncounter e0 = DungeonEncounter.flashcard("elite_0_0", false, 1L, "Q1", "A1", null, null);
        DungeonEncounter e1 = DungeonEncounter.flashcard("elite_0_1", false, 2L, "Q2", "A2", null, null);
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.ELITE,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, "elite_0_0",
            List.of("elite_0_0", "elite_0_1"),
            null, new TreasureOffer(List.of(RelicId.PHOENIX_FEATHER, RelicId.SPECTACLES)),
            null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r1",
            Map.of("elite_0_0", e0, "elite_0_1", e1), List.of(), 0, null);
    }

    private DungeonSessionState activeFlashcardState() {
        DungeonSessionState base = singleCombatRoomState();
        DungeonEncounter enc = base.encounters().get("enc_0").activate();
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(base.encounters());
        encs.put("enc_0", enc);
        return withEncountersAndActive(base, encs, "enc_0");
    }

    private DungeonSessionState activeFlashcardStateWithStreak(int streak) {
        DungeonSessionState s = activeFlashcardState();
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            streak, s.gauntletQueue(),
            Math.max(s.longestStreak(), streak), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }

    private DungeonSessionState activeEliteFinalStepState() {
        DungeonSessionState base = eliteRoomState();
        DungeonEncounter first = base.encounters().get("elite_0_0").activate().clear(List.of(), true);
        DungeonEncounter second = base.encounters().get("elite_0_1").activate();
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(base.encounters());
        encs.put("elite_0_0", first);
        encs.put("elite_0_1", second);
        DungeonSessionState withAct = withEncountersAndActive(base, encs, "elite_0_1");
        return new DungeonSessionState(
            withAct.config(), withAct.map(), withAct.currentRoomId(),
            withAct.encounters(), withAct.bossEncounterIds(), withAct.bossIndex(),
            withAct.activeEncounterId(),
            withAct.health(), withAct.healthCap(), withAct.shields(), withAct.shieldCap(),
            withAct.score(), withAct.answeredCount(), withAct.correctCount(),
            withAct.won(), withAct.defeated(),
            withAct.streak(), List.of(),
            withAct.longestStreak(), withAct.elitesCleared(), withAct.shieldsUsed(),
            withAct.luckyCoinsConsumed(), withAct.ownedRelics(), withAct.pendingRelicPick());
    }

    private DungeonSessionState activeEliteMidStepState() {
        DungeonSessionState base = eliteRoomState();
        DungeonEncounter first = base.encounters().get("elite_0_0").activate();
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(base.encounters());
        encs.put("elite_0_0", first);
        DungeonSessionState withAct = withEncountersAndActive(base, encs, "elite_0_0");
        return new DungeonSessionState(
            withAct.config(), withAct.map(), withAct.currentRoomId(),
            withAct.encounters(), withAct.bossEncounterIds(), withAct.bossIndex(),
            withAct.activeEncounterId(),
            withAct.health(), withAct.healthCap(), withAct.shields(), withAct.shieldCap(),
            withAct.score(), withAct.answeredCount(), withAct.correctCount(),
            withAct.won(), withAct.defeated(),
            withAct.streak(), List.of("elite_0_1"),
            withAct.longestStreak(), withAct.elitesCleared(), withAct.shieldsUsed(),
            withAct.luckyCoinsConsumed(), withAct.ownedRelics(), withAct.pendingRelicPick());
    }

    private DungeonSessionState activeBossStateAtIndexZero() {
        DungeonEncounter b0 = DungeonEncounter.flashcard("boss_0", true, 10L, "B0", "BA0", null, null).activate();
        DungeonEncounter b1 = DungeonEncounter.flashcard("boss_1", true, 11L, "B1", "BA1", null, null);
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.BOSS,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r1", Map.of("boss_0", b0, "boss_1", b1),
            List.of("boss_0", "boss_1"), 0, "boss_0");
    }

    private DungeonSessionState activeBossStateAtFinalPrompt() {
        DungeonSessionState base = activeBossStateAtIndexZero();
        return new DungeonSessionState(
            base.config(), base.map(), base.currentRoomId(),
            base.encounters(), base.bossEncounterIds(), 1,
            "boss_1",
            base.health(), base.healthCap(), base.shields(), base.shieldCap(),
            base.score(), base.answeredCount(), base.correctCount(),
            base.won(), base.defeated(),
            base.streak(), base.gauntletQueue(),
            base.longestStreak(), base.elitesCleared(), base.shieldsUsed(),
            base.luckyCoinsConsumed(), base.ownedRelics(), base.pendingRelicPick());
    }

    private DungeonSessionState baseState(DungeonMap map, String currentRoomId,
                                            Map<String, DungeonEncounter> encs,
                                            List<String> bossIds, int bossIndex,
                                            String activeEncounterId) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return new DungeonSessionState(
            config, map, currentRoomId,
            encs, bossIds, bossIndex, activeEncounterId,
            5, 5, 0, 2,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);
    }

    private DungeonSessionState withEncountersAndActive(DungeonSessionState s,
                                                           Map<String, DungeonEncounter> encs,
                                                           String activeId) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            encs, s.bossEncounterIds(), s.bossIndex(),
            activeId,
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }
}
