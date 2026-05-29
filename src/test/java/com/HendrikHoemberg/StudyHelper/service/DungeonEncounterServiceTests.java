package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonEncounterServiceTests {

    private final DungeonEncounterService svc = new DungeonEncounterService(new DungeonCombatService());

    @Test
    void activateAt_combatRoomMarksEncounterActive() {
        DungeonSessionState state = singleCombatRoomState();
        ActionResult result = svc.activateAt(state, "r1");
        assertThat(result.state().combat().activeEncounterId()).isEqualTo("enc_0");
    }

    @Test
    void activateAt_eliteRoomSetsGauntletQueueAndActivatesFirstEncounter() {
        DungeonSessionState state = eliteRoomState();
        ActionResult result = svc.activateAt(state, "r1");
        assertThat(result.state().combat().activeEncounterId()).isEqualTo("elite_0_0");
        assertThat(result.state().combat().gauntletQueue()).containsExactly("elite_0_1");
    }

    @Test
    void answerFlashcard_correctIncrementsStreakAndCorrectCount() {
        DungeonSessionState state = activeFlashcardState();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.progress().streak()).isEqualTo(1);
        assertThat(result.progress().correctCount()).isEqualTo(1);
        assertThat(result.combat().activeEncounterId()).isNull();
        assertThat(result.map().room("r1").cleared()).isTrue();
    }

    @Test
    void answerFlashcard_wrongTakesDamageAndResetsStreak() {
        DungeonSessionState state = activeFlashcardStateWithStreak(2);
        DungeonSessionState result = svc.answerFlashcard(state, false);
        assertThat(result.progress().streak()).isEqualTo(0);
        assertThat(result.resources().health()).isEqualTo(state.resources().health() - 1);
    }

    @Test
    void answerFlashcard_thirdCorrectAnswerBanksAShield() {
        DungeonSessionState state = activeFlashcardStateWithStreak(2);
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.progress().streak()).isEqualTo(3);
        assertThat(result.resources().shields()).isEqualTo(state.resources().shields() + 1);
    }

    @Test
    void answerFlashcard_eliteGauntletClearSetsPendingRelicPick() {
        DungeonSessionState state = activeEliteFinalStepState();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.loadout().pendingRelicPick()).isNotNull();
        assertThat(result.loadout().pendingRelicPick().type()).isEqualTo(PendingPickType.ELITE);
        assertThat(result.loadout().pendingRelicPick().roomId()).isEqualTo("r1");
        assertThat(result.progress().elitesCleared()).isEqualTo(1);
        assertThat(result.resources().health()).isEqualTo(result.resources().healthCap());
    }

    @Test
    void answerFlashcard_eliteGauntletMidWrongAnswerAbortsAndKeepsRoomActive() {
        DungeonSessionState state = activeEliteMidStepState();
        DungeonSessionState result = svc.answerFlashcard(state, false);
        assertThat(result.combat().activeEncounterId()).isNull();
        assertThat(result.combat().gauntletQueue()).isEmpty();
        assertThat(result.map().room("r1").cleared()).isFalse();
    }

    @Test
    void answerFlashcard_bossSequenceProgressesAcrossPrompts() {
        DungeonSessionState state = activeBossStateAtIndexZero();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.combat().bossIndex()).isEqualTo(1);
        assertThat(result.combat().activeEncounterId()).isEqualTo("boss_1");
        assertThat(result.won()).isFalse();
    }

    @Test
    void answerFlashcard_bossSequenceFinalPromptWinsTheRun() {
        DungeonSessionState state = activeBossStateAtFinalPrompt();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.won()).isTrue();
        assertThat(result.combat().activeEncounterId()).isNull();
    }

    @Test
    void answerCorrect_luckyCharmAdds5ToScore() {
        DungeonSessionState state = activeFlashcardState();
        state = withRelics(state, List.of(RelicId.LUCKY_CHARM));
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.resources().score()).isEqualTo(DungeonBalance.COMBAT_CLEAR_SCORE + DungeonBalance.LUCKY_CHARM_BONUS);
    }

    @Test
    void answerCorrect_sharpFocusBanksShieldEveryTwoCorrect() {
        DungeonSessionState state = activeFlashcardStateWithStreak(1);
        state = withRelics(state, List.of(RelicId.SHARP_FOCUS));
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.progress().streak()).isEqualTo(2);
        assertThat(result.resources().shields()).isEqualTo(state.resources().shields() + 1);
    }

    @Test
    void answerCorrect_warBannerGrantsShieldOnCombatClear() {
        DungeonSessionState state = activeFlashcardState();
        state = withRelics(state, List.of(RelicId.WAR_BANNER));
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.resources().shields()).isEqualTo(Math.min(state.resources().shieldCap(), state.resources().shields() + 1));
    }

    private DungeonSessionState withRelics(DungeonSessionState s, List<RelicId> relics) {
        return s.withLoadout(s.loadout().withOwnedRelics(relics));
    }

    // ===== fixtures =====

    private DungeonSessionState singleCombatRoomState() {
        DungeonEncounter enc = DungeonEncounter.flashcard(
            "enc_0", false, "SLIME", 1L, "Q", "A", null, null);
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, "enc_0", List.of(), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r1", Map.of("enc_0", enc), List.of(), 0, null);
    }

    private DungeonSessionState eliteRoomState() {
        DungeonEncounter e0 = DungeonEncounter.flashcard("elite_0_0", false, "CHAMPION", 1L, "Q1", "A1", null, null);
        DungeonEncounter e1 = DungeonEncounter.flashcard("elite_0_1", false, "CHAMPION", 2L, "Q2", "A2", null, null);
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.ELITE,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, "elite_0_0",
            List.of("elite_0_0", "elite_0_1"),
            null, new TreasureOffer(List.of(RelicId.PHOENIX_FEATHER, RelicId.SPECTACLES)),
            null);
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
        return s.withProgress(new DungeonProgress(s.progress().answeredCount(),
            s.progress().correctCount(), streak, Math.max(s.progress().longestStreak(), streak),
            s.progress().elitesCleared(), s.progress().shieldsUsed(), s.progress().luckyCoinsConsumed()));
    }

    private DungeonSessionState activeEliteFinalStepState() {
        DungeonSessionState base = eliteRoomState();
        DungeonEncounter first = base.encounters().get("elite_0_0").activate().clear(List.of(), true);
        DungeonEncounter second = base.encounters().get("elite_0_1").activate();
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(base.encounters());
        encs.put("elite_0_0", first);
        encs.put("elite_0_1", second);
        return withEncountersAndActive(base, encs, "elite_0_1")
            .withCombat(withEncountersAndActive(base, encs, "elite_0_1").combat().withGauntletQueue(List.of()));
    }

    private DungeonSessionState activeEliteMidStepState() {
        DungeonSessionState base = eliteRoomState();
        DungeonEncounter first = base.encounters().get("elite_0_0").activate();
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(base.encounters());
        encs.put("elite_0_0", first);
        return withEncountersAndActive(base, encs, "elite_0_0")
            .withCombat(withEncountersAndActive(base, encs, "elite_0_0").combat().withGauntletQueue(List.of("elite_0_1")));
    }

    private DungeonSessionState activeBossStateAtIndexZero() {
        DungeonEncounter b0 = DungeonEncounter.flashcard("boss_0", true, "DRAGON", 10L, "B0", "BA0", null, null).activate();
        DungeonEncounter b1 = DungeonEncounter.flashcard("boss_1", true, "DRAGON", 11L, "B1", "BA1", null, null);
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.BOSS,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, null, List.of(), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r1", Map.of("boss_0", b0, "boss_1", b1),
            List.of("boss_0", "boss_1"), 0, "boss_0");
    }

    private DungeonSessionState activeBossStateAtFinalPrompt() {
        DungeonSessionState base = activeBossStateAtIndexZero();
        return base
            .withEncounters(base.encounters())
            .withCombat(new DungeonCombat("boss_1", List.of(), 1));
    }

    private DungeonSessionState baseState(DungeonMap map, String currentRoomId,
                                            Map<String, DungeonEncounter> encs,
                                            List<String> bossIds, int bossIndex,
                                            String activeEncounterId) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return DungeonSessionState.builder()
            .config(config).map(map).currentRoomId(currentRoomId)
            .encounters(encs).bossEncounterIds(bossIds)
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .combat(new DungeonCombat(activeEncounterId, List.of(), bossIndex))
            .build();
    }

    private DungeonSessionState withEncountersAndActive(DungeonSessionState s,
                                                           Map<String, DungeonEncounter> encs,
                                                           String activeId) {
        return s.withEncounters(encs)
            .withCombat(s.combat().withActiveEncounterId(activeId));
    }

    @Test
    void wrongAnswer_luckyCoinAbsorbsHitWithoutShieldOrHpLoss() {
        DungeonSessionState state = activeFlashcardState();
        state = withRelics(state, List.of(RelicId.LUCKY_COIN));
        DungeonSessionState result = svc.answerFlashcard(state, false);
        assertThat(result.resources().health()).isEqualTo(5);
        assertThat(result.resources().shields()).isEqualTo(0);
        assertThat(result.progress().luckyCoinsConsumed()).isEqualTo(1);
        assertThat(result.progress().shieldsUsed()).isEqualTo(0);
        assertThat(result.defeated()).isFalse();
    }

    @Test
    void wrongAnswer_secondLuckyCoinAbsorbsSecondHitWhenStacked() {
        DungeonSessionState state = activeFlashcardState();
        state = withRelics(state, List.of(RelicId.LUCKY_COIN, RelicId.LUCKY_COIN));
        DungeonSessionState first = svc.answerFlashcard(state, false);
        assertThat(first.progress().luckyCoinsConsumed()).isEqualTo(1);
        assertThat(first.resources().health()).isEqualTo(5);
        DungeonEncounter enc = first.encounters().get("enc_0");
        Map<String, DungeonEncounter> resetEncs = new LinkedHashMap<>(first.encounters());
        resetEncs.put("enc_0", new DungeonEncounter(
            enc.id(), enc.type(), DungeonEncounterStatus.PENDING, enc.boss(),
            enc.monsterType(), enc.flashcardId(), enc.frontText(), enc.backText(),
            enc.frontImageUrl(), enc.backImageUrl(), enc.quizQuestion(),
            List.of(), null));
        DungeonSessionState reactivated = first
            .withEncounters(Map.copyOf(resetEncs))
            .withCombat(new DungeonCombat("enc_0", List.of(), 0));
        DungeonSessionState second = svc.answerFlashcard(reactivated, false);
        assertThat(second.progress().luckyCoinsConsumed()).isEqualTo(2);
        assertThat(second.resources().health()).isEqualTo(5);
        assertThat(second.defeated()).isFalse();
    }

    @Test
    void wrongAnswer_phoenixFeatherRevivesAtOneHpOnLethalHit() {
        DungeonSessionState state = activeFlashcardState();
        state = state.withResources(new DungeonResources(1, 5, 0, 2, 0));
        state = withRelics(state, List.of(RelicId.PHOENIX_FEATHER));
        DungeonSessionState result = svc.answerFlashcard(state, false);
        assertThat(result.resources().health()).isEqualTo(1);
        assertThat(result.defeated()).isFalse();
        assertThat(result.loadout().ownedRelics()).doesNotContain(RelicId.PHOENIX_FEATHER);
    }
}
