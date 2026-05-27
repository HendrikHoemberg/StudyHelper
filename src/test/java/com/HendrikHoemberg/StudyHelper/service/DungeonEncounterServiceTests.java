package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonEncounterServiceTests {

    private DungeonEncounterService service;

    @BeforeEach
    void setUp() { service = new DungeonEncounterService(); }

    private DungeonSessionState stateWith(DungeonEncounter active, int streak, int shields) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        DungeonPosition pos = new DungeonPosition(0, 0);
        DungeonMap map = new DungeonMap(1, 1, pos, pos, Map.of());
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
        encs.put(active.id(), active);
        return new DungeonSessionState(
            config, map, pos, encs, List.of(), 0,
            active.id(),
            5, 0, 0, 0, Set.of(),
            false, false,
            streak, shields, List.of(), streak, 0, 0);
    }

    private DungeonEncounter flashcard() {
        return DungeonEncounter.flashcard("f1", false, 100L, "Q", "A", null, null).activate();
    }

    @Test
    void answerCorrect_incrementsStreak() {
        DungeonSessionState before = stateWith(flashcard(), 0, 0);
        DungeonSessionState after = service.answerFlashcard(before, true);
        assertThat(after.streak()).isEqualTo(1);
        assertThat(after.longestStreak()).isEqualTo(1);
        assertThat(after.shields()).isZero();
    }

    @Test
    void thirdCorrectInRow_grantsShield() {
        DungeonSessionState s = stateWith(flashcard(), 2, 0);
        DungeonSessionState after = service.answerFlashcard(s, true);
        assertThat(after.streak()).isEqualTo(3);
        assertThat(after.shields()).isEqualTo(1);
    }

    @Test
    void shieldsCapAtTwo() {
        DungeonSessionState s = stateWith(flashcard(), 5, 2);
        DungeonSessionState after = service.answerFlashcard(s, true);
        assertThat(after.streak()).isEqualTo(6);
        assertThat(after.shields()).isEqualTo(2);
    }

    @Test
    void wrongAnswer_resetsStreakAndDealsDamage() {
        DungeonSessionState s = stateWith(flashcard(), 4, 0);
        DungeonSessionState after = service.answerFlashcard(s, false);
        assertThat(after.streak()).isZero();
        assertThat(after.health()).isEqualTo(4);
    }

    @Test
    void wrongAnswerWithShield_absorbsDamageAndStillResetsStreak() {
        DungeonSessionState s = stateWith(flashcard(), 4, 1);
        DungeonSessionState after = service.answerFlashcard(s, false);
        assertThat(after.streak()).isZero();
        assertThat(after.health()).isEqualTo(5);
        assertThat(after.shields()).isZero();
        assertThat(after.shieldsUsed()).isEqualTo(1);
    }

    @Test
    void activateElite_setsGauntletQueueWithRemainingIds() {
        DungeonEncounter e1 = DungeonEncounter.flashcard("e1", false, 1L, "Q1", "A1", null, null);
        DungeonEncounter e2 = DungeonEncounter.flashcard("e2", false, 2L, "Q2", "A2", null, null);
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
        encs.put("e1", e1); encs.put("e2", e2);
        DungeonConfig config = new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
            List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        DungeonPosition pos = new DungeonPosition(0, 0);
        DungeonMap map = new DungeonMap(1, 1, pos, pos, Map.of(),
            Map.of("e1", List.of("e1", "e2")));
        DungeonSessionState before = new DungeonSessionState(
            config, map, pos, encs, List.of(), 0, null,
            5, 0, 0, 0, Set.of(), false, false,
            0, 0, List.of(), 0, 0, 0);

        DungeonSessionState after = service.activateElite(before, "e1");
        assertThat(after.activeEncounterId()).isEqualTo("e1");
        assertThat(after.gauntletQueue()).containsExactly("e2");
    }

    @Test
    void correctElite_advancesQueueAndActivatesNext() {
        DungeonEncounter e1 = DungeonEncounter.flashcard("e1", false, 1L, "Q1", "A1", null, null).activate();
        DungeonEncounter e2 = DungeonEncounter.flashcard("e2", false, 2L, "Q2", "A2", null, null);
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
        encs.put("e1", e1); encs.put("e2", e2);
        DungeonSessionState before = new DungeonSessionState(
            new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
            new DungeonMap(1, 1, new DungeonPosition(0,0), new DungeonPosition(0,0), Map.of(),
                Map.of("e1", List.of("e1", "e2"))),
            new DungeonPosition(0,0), encs, List.of(), 0, "e1",
            5, 0, 0, 0, Set.of(), false, false,
            0, 0, List.of("e2"), 0, 0, 0);

        DungeonSessionState after = service.answerFlashcard(before, true);
        assertThat(after.activeEncounterId()).isEqualTo("e2");
        assertThat(after.gauntletQueue()).isEmpty();
    }

    @Test
    void wrongElite_abortsGauntletAndDamagesAndResetsEncountersToPending() {
        DungeonEncounter e1 = DungeonEncounter.flashcard("e1", false, 1L, "Q1", "A1", null, null).activate();
        DungeonEncounter e2 = DungeonEncounter.flashcard("e2", false, 2L, "Q2", "A2", null, null);
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
        encs.put("e1", e1); encs.put("e2", e2);
        DungeonSessionState before = new DungeonSessionState(
            new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
            new DungeonMap(1, 1, new DungeonPosition(0,0), new DungeonPosition(0,0), Map.of(),
                Map.of("e1", List.of("e1", "e2"))),
            new DungeonPosition(0,0), encs, List.of(), 0, "e1",
            5, 0, 0, 0, Set.of(), false, false,
            0, 0, List.of("e2"), 0, 0, 0);

        DungeonSessionState after = service.answerFlashcard(before, false);
        assertThat(after.activeEncounterId()).isNull();
        assertThat(after.gauntletQueue()).isEmpty();
        assertThat(after.health()).isEqualTo(4);
        
        // Assert that the encounters have been reset to PENDING
        assertThat(after.encounters().get("e1").status()).isEqualTo(DungeonEncounterStatus.PENDING);
        assertThat(after.encounters().get("e2").status()).isEqualTo(DungeonEncounterStatus.PENDING);
        assertThat(after.encounters().get("e1").correct()).isNull();
    }

    @Test
    void clearingFinalElite_grantsFullHealAndShield() {
        DungeonEncounter e1 = DungeonEncounter.flashcard("e1", false, 1L, "Q1", "A1", null, null).activate();
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
        encs.put("e1", e1);
        DungeonSessionState before = new DungeonSessionState(
            new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
            new DungeonMap(1, 1, new DungeonPosition(0,0), new DungeonPosition(0,0), Map.of(),
                Map.of("e1", List.of("e1"))),
            new DungeonPosition(0,0), encs, List.of(), 0, "e1",
            2, 0, 0, 0, Set.of(), false, false,
            0, 0, List.of(), 0, 0, 0);

        DungeonSessionState after = service.answerFlashcard(before, true);
        assertThat(after.health()).isEqualTo(5);
        assertThat(after.shields()).isEqualTo(1);
        assertThat(after.elitesCleared()).isEqualTo(1);
    }
}
