package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DungeonControllerTests {

    private DungeonController controller;
    private DungeonSessionService dungeonSessionService;
    private SavedSessionService savedSessionService;
    private StudyLogService studyLogService;
    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        dungeonSessionService = mock(DungeonSessionService.class);
        savedSessionService = mock(SavedSessionService.class);
        studyLogService = mock(StudyLogService.class);
        userService = mock(UserService.class);
        controller = new DungeonController(
            dungeonSessionService, savedSessionService, studyLogService, userService
        );

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);
    }

    private DungeonSessionState sampleState(boolean won, boolean defeated) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        DungeonPosition entrance = new DungeonPosition(0, 0);
        DungeonPosition pos1 = new DungeonPosition(1, 0);
        DungeonPosition bossPos = new DungeonPosition(1, 1);

        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>();
        tiles.put(entrance, new DungeonTile(entrance, DungeonTileType.ENTRANCE, true, true, null));
        tiles.put(pos1, new DungeonTile(pos1, DungeonTileType.ENCOUNTER, true, true, "fc_0"));
        tiles.put(bossPos, new DungeonTile(bossPos, DungeonTileType.BOSS, false, false, "boss_0"));

        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>();
        encounters.put("fc_0",
            DungeonEncounter.flashcard("fc_0", false, 100L, "front", "back", null, null));
        encounters.put("boss_0",
            DungeonEncounter.flashcard("boss_0", true, 200L, "boss front", "boss back", null, null));

        return new DungeonSessionState(
            config,
            new DungeonMap(2, 2, entrance, bossPos, tiles),
            entrance,
            encounters,
            List.of("boss_0"),
            0,
            won || defeated ? null : "fc_0",
            5,
            0,
            won || defeated ? 1 : 0,
            won ? 1 : 0,
            Set.of(entrance, pos1),
            won,
            defeated
        );
    }

    @Test
    void startFlashcardDungeon_storesSessionAndSavesIt() {
        DungeonSessionState state = sampleState(false, false);
        when(dungeonSessionService.createFlashcardDungeon(List.of(1L), DungeonSize.SMALL, user))
            .thenReturn(state);
        DungeonRunStats stats = new DungeonRunStats(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 0, 0, 5, false);
        when(dungeonSessionService.buildStats(state)).thenReturn(stats);

        MockHttpSession session = new MockHttpSession();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.start(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, null,
            new MockHttpServletRequest(), model, () -> "alice", session, "true");

        assertThat(view).isEqualTo("fragments/dungeon-game :: dungeonGame");
        assertThat(session.getAttribute("dungeonSessionState")).isSameAs(state);
        verify(savedSessionService).saveDungeon(eq(user), eq(state));
    }

    @Test
    void startFlashcardDungeon_missingDecksReturnsSetupErrorWithOptionsPreserved() {
        List<Long> emptyDecks = List.of();
        when(dungeonSessionService.createFlashcardDungeon(emptyDecks, DungeonSize.MEDIUM, user))
            .thenThrow(new IllegalArgumentException("Please select at least one deck."));

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpSession session = new MockHttpSession();

        String view = controller.start(
            DungeonMode.FLASHCARDS, DungeonSize.MEDIUM, emptyDecks,
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, null,
            new MockHttpServletRequest(), model, () -> "alice", session, "true");

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(model.get("mode")).isEqualTo(StudyMode.DUNGEON);
        assertThat(model.get("dungeonMode")).isEqualTo(DungeonMode.FLASHCARDS);
        assertThat(model.get("dungeonSize")).isEqualTo(DungeonSize.MEDIUM);
        assertThat(model.get("selectedDeckIds")).isEqualTo(emptyDecks);
        assertThat(model.get("errorMessage")).isEqualTo("Please select at least one deck.");
        verify(savedSessionService, never()).saveDungeon(any(), any());
    }

    @Test
    void startFlashcardDungeon_insufficientCardsReturnsSetupErrorWithOptionsPreserved() {
        String errorMsg = "Not enough flashcards available for Small dungeon. Need 8, have 3.";
        when(dungeonSessionService.createFlashcardDungeon(List.of(1L), DungeonSize.SMALL, user))
            .thenThrow(new IllegalArgumentException(errorMsg));

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpSession session = new MockHttpSession();

        String view = controller.start(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, null,
            new MockHttpServletRequest(), model, () -> "alice", session, "true");

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(model.get("errorMessage")).isEqualTo(errorMsg);
        verify(savedSessionService, never()).saveDungeon(any(), any());
    }

    @Test
    void startAiQuizDungeon_generationOrQuotaFailureReturnsSetupErrorWithOptionsPreserved() {
        when(dungeonSessionService.createAiQuizDungeon(
            eq(List.of(1L)), eq(DungeonSize.SMALL), eq(QuizQuestionMode.MCQ_ONLY),
            eq(Difficulty.MEDIUM), eq(null), any(), eq(user)))
            .thenThrow(new IllegalStateException("AI generation failed"));

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpSession session = new MockHttpSession();

        String view = controller.start(
            DungeonMode.AI_QUIZ, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, null,
            new MockHttpServletRequest(), model, () -> "alice", session, "true");

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(model.get("dungeonMode")).isEqualTo(DungeonMode.AI_QUIZ);
        assertThat(model.get("dungeonSize")).isEqualTo(DungeonSize.SMALL);
        assertThat(model.get("quizQuestionMode")).isEqualTo(QuizQuestionMode.MCQ_ONLY);
        assertThat(model.get("difficulty")).isEqualTo(Difficulty.MEDIUM);
        assertThat(model.get("selectedDeckIds")).isEqualTo(List.of(1L));
        assertThat(model.get("errorMessage")).isEqualTo("AI generation failed");
        verify(savedSessionService, never()).saveDungeon(any(), any());
    }

    @Test
    void move_updatesSavedState() {
        DungeonSessionState before = sampleState(false, false);
        DungeonSessionState after = sampleState(false, false);
        when(dungeonSessionService.move(before, DungeonDirection.RIGHT)).thenReturn(after);
        DungeonRunStats stats = new DungeonRunStats(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 0, 0, 5, false);
        when(dungeonSessionService.buildStats(after)).thenReturn(stats);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("dungeonSessionState", before);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.move(
            DungeonDirection.RIGHT, model, () -> "alice", session, "true");

        assertThat(view).isEqualTo("fragments/dungeon-game :: dungeonGame");
        assertThat(session.getAttribute("dungeonSessionState")).isSameAs(after);
        verify(savedSessionService).saveDungeon(eq(user), eq(after));
    }

    @Test
    void invalidMoveRerendersCurrentRunWithoutProgressingCompletion() {
        DungeonSessionState state = sampleState(false, false);
        when(dungeonSessionService.move(state, DungeonDirection.UP)).thenReturn(state);
        DungeonRunStats stats = new DungeonRunStats(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 0, 0, 5, false);
        when(dungeonSessionService.buildStats(state)).thenReturn(stats);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("dungeonSessionState", state);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.move(
            DungeonDirection.UP, model, () -> "alice", session, "true");

        assertThat(view).isEqualTo("fragments/dungeon-game :: dungeonGame");
        verify(savedSessionService, never()).discard(any());
        verify(studyLogService, never()).recordDungeon(any(), any(), any());
    }

    @Test
    void staleFlashcardAnswerRerendersCurrentRunWithoutLoggingCompletion() {
        DungeonSessionState state = sampleState(false, false);
        when(dungeonSessionService.answerFlashcard(state, true)).thenReturn(state);
        DungeonRunStats stats = new DungeonRunStats(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 0, 0, 5, false);
        when(dungeonSessionService.buildStats(state)).thenReturn(stats);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("dungeonSessionState", state);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.answerFlashcard(
            true, model, () -> "alice", session, "true");

        assertThat(view).isEqualTo("fragments/dungeon-game :: dungeonGame");
        verify(savedSessionService, never()).discard(any());
        verify(studyLogService, never()).recordDungeon(any(), any(), any());
    }

    @Test
    void answerCompletedRun_discardsAndLogs() {
        DungeonSessionState before = sampleState(false, false);
        DungeonSessionState won = sampleState(true, false);
        when(dungeonSessionService.answerFlashcard(before, true)).thenReturn(won);
        DungeonRunStats stats = new DungeonRunStats(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 1, 1, 5, true);
        when(dungeonSessionService.buildStats(won)).thenReturn(stats);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("dungeonSessionState", before);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.answerFlashcard(
            true, model, () -> "alice", session, "true");

        assertThat(view).isEqualTo("fragments/dungeon-complete :: dungeonComplete");
        verify(savedSessionService).discard(user);
        verify(studyLogService).recordDungeon(eq(user), eq(stats), eq(List.of(1L)));
    }

    @Test
    void answerCompletedRun_setsCompletionViewForFullPageRequest() {
        DungeonSessionState before = sampleState(false, false);
        DungeonSessionState won = sampleState(true, false);
        when(dungeonSessionService.answerFlashcard(before, true)).thenReturn(won);
        DungeonRunStats stats = new DungeonRunStats(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 1, 1, 5, true);
        when(dungeonSessionService.buildStats(won)).thenReturn(stats);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("dungeonSessionState", before);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.answerFlashcard(
            true, model, () -> "alice", session, null);

        assertThat(view).isEqualTo("study-page");
        assertThat(model.get("studyStateView")).isEqualTo("dungeonComplete");
        assertThat(model.get("stats")).isEqualTo(stats);
        verify(savedSessionService).discard(user);
        verify(studyLogService).recordDungeon(eq(user), eq(stats), eq(List.of(1L)));
    }

    @Test
    void resumeLoadsSavedDungeonAfterReconciliation() {
        DungeonSessionState saved = sampleState(false, false);
        when(savedSessionService.loadDungeon(user)).thenReturn(Optional.of(saved));
        SavedSessionService.ReconcileDungeonResult result =
            new SavedSessionService.ReconcileDungeonResult(saved, 0, true);
        when(savedSessionService.reconcileDungeonFlashcards(saved, user)).thenReturn(result);
        DungeonRunStats stats = new DungeonRunStats(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 0, 0, 5, false);
        when(dungeonSessionService.buildStats(saved)).thenReturn(stats);

        MockHttpSession session = new MockHttpSession();
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);

        String view = controller.resume(
            model, () -> "alice", session, redirectAttributes, "true");

        assertThat(view).isEqualTo("fragments/dungeon-game :: dungeonGame");
        assertThat(session.getAttribute("dungeonSessionState")).isSameAs(saved);
        verify(savedSessionService).saveDungeon(eq(user), eq(saved));
    }

    @Test
    void resumeDiscardsWhenReconciliationCannotContinue() {
        DungeonSessionState saved = sampleState(false, false);
        when(savedSessionService.loadDungeon(user)).thenReturn(Optional.of(saved));
        SavedSessionService.ReconcileDungeonResult result =
            new SavedSessionService.ReconcileDungeonResult(saved, 2, false);
        when(savedSessionService.reconcileDungeonFlashcards(saved, user)).thenReturn(result);
        DungeonRunStats stats = new DungeonRunStats(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 0, 0, 5, false);
        when(dungeonSessionService.buildStats(saved)).thenReturn(stats);

        MockHttpSession session = new MockHttpSession();
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributes redirectAttributes = mock(RedirectAttributes.class);

        String view = controller.resume(
            model, () -> "alice", session, redirectAttributes, "true");

        assertThat(view).isEqualTo("redirect:/study/start?mode=DUNGEON");
        verify(studyLogService).recordDungeonAbandoned(eq(user), eq(stats), eq(List.of(1L)));
        verify(savedSessionService).discard(user);
        verify(redirectAttributes).addFlashAttribute(
            eq("errorMessage"),
            eq("Your saved Dungeon run could not continue because 2 flashcard(s) are no longer available."));
    }
}
