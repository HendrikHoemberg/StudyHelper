package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.time.Instant;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyControllerTests {

    private StudyController controller;
    private StudySessionService studySessionService;
    private ExamSessionService examSessionService;
    private AiRequestQuotaService aiRequestQuotaService;
    private User user;

    @BeforeEach
    void setUp() {
        studySessionService = mock(StudySessionService.class);
        AiQuizService aiQuizService = mock(AiQuizService.class);
        AiExamService aiExamService = mock(AiExamService.class);
        ExamService examService = mock(ExamService.class);
        UserService userService = mock(UserService.class);
        DeckService deckService = mock(DeckService.class);
        FlashcardService flashcardService = mock(FlashcardService.class);
        FolderService folderService = mock(FolderService.class);
        DocumentExtractionService documentExtractionService = mock(DocumentExtractionService.class);
        FileEntryService fileEntryService = mock(FileEntryService.class);
        examSessionService = mock(ExamSessionService.class);
        aiRequestQuotaService = mock(AiRequestQuotaService.class);

        controller = new StudyController(
            studySessionService,
            aiQuizService,
            deckService,
            flashcardService,
            folderService,
            userService,
            documentExtractionService,
            fileEntryService,
            examSessionService,
            aiRequestQuotaService
        );

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);
        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user))).thenReturn(List.of());
        when(flashcardService.getFlashcardsFlattened(anyList())).thenReturn(List.of());
    }

    @Test
    void createSession_ExamDelegatesToExamSessionServiceAndStoresSession() throws Exception {
        ExamSessionState state = new ExamSessionState(
            new ExamConfig(List.of(10L), List.of(), ExamQuestionSize.MEDIUM, 5, null, ExamLayout.PER_PAGE),
            List.of(new ExamQuestion("Q", "rubric")),
            Map.of(),
            Instant.now(),
            "source"
        );
        when(examSessionService.createSession(
            eq(List.of(10L)),
            eq(List.of()),
            eq("exam instructions"),
            any(MockHttpServletRequest.class),
            eq(ExamQuestionSize.MEDIUM),
            eq(5),
            eq(null),
            eq(ExamLayout.PER_PAGE),
            eq(user)
        )).thenReturn(new ExamSessionService.ExamSessionResult(state, ExamLayout.PER_PAGE));

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
            StudyMode.EXAM,
            List.of(10L),
            List.of(),
            "exam instructions",
            new MockHttpServletRequest(),
            SessionMode.DECK_BY_DECK,
            DeckOrderMode.SELECTED_ORDER,
            null,
            QuizQuestionMode.MCQ_ONLY,
            Difficulty.MEDIUM,
            5,
            ExamQuestionSize.MEDIUM,
            5,
            null,
            ExamLayout.PER_PAGE,
            model,
            () -> "alice",
            session,
            response,
            "true"
        );

        assertThat(view).isEqualTo("fragments/exam-question :: exam-question");
        assertThat(session.getAttribute("examSession")).isSameAs(state);
        assertThat(response.getHeader("HX-Trigger")).isEqualTo("refresh-quota");
        verify(examSessionService).createSession(
            eq(List.of(10L)),
            eq(List.of()),
            eq("exam instructions"),
            any(MockHttpServletRequest.class),
            eq(ExamQuestionSize.MEDIUM),
            eq(5),
            eq(null),
            eq(ExamLayout.PER_PAGE),
            eq(user)
        );
    }

    @Test
    void createSession_QuizQuotaExceeded_RendersStudyErrorPath() {
        doThrow(new AiQuotaExceededException("Daily AI request limit reached."))
            .when(aiRequestQuotaService).checkAndRecord(user);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
            StudyMode.QUIZ,
            List.of(1L),
            List.of(),
            null,
            new MockHttpServletRequest(),
            SessionMode.DECK_BY_DECK,
            DeckOrderMode.SELECTED_ORDER,
            null,
            QuizQuestionMode.MCQ_ONLY,
            Difficulty.MEDIUM,
            5,
            ExamQuestionSize.MEDIUM,
            5,
            null,
            ExamLayout.PER_PAGE,
            model,
            () -> "alice",
            new MockHttpSession(),
            response,
            "true"
        );

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("studyError")).isEqualTo("Daily AI request limit reached.");
        verify(aiRequestQuotaService).checkAndRecord(user);
    }

    @Test
    void preflightCreateSession_QuizWithoutSources_ReturnsStudyErrorAndSkipsQuota() {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightCreateSession(
            StudyMode.QUIZ,
            List.of(),
            List.of(),
            new MockHttpServletRequest(),
            QuizQuestionMode.MCQ_ONLY,
            Difficulty.MEDIUM,
            5,
            ExamQuestionSize.MEDIUM,
            5,
            null,
            ExamLayout.PER_PAGE,
            model,
            () -> "alice",
            new MockHttpSession(),
            response,
            "true"
        );

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("studyError")).isEqualTo("Please select at least one deck or document.");
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
    }

    @Test
    void preflightCreateSession_ExamWithoutSources_UsesStudyErrorPath() throws Exception {
        doThrow(new IllegalArgumentException("Please select at least one source."))
            .when(examSessionService).validateForPreflight(
                eq(List.of()),
                eq(List.of()),
                any(MockHttpServletRequest.class),
                eq(ExamQuestionSize.MEDIUM),
                eq(5),
                eq(null),
                eq(ExamLayout.PER_PAGE),
                eq(user)
            );

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightCreateSession(
            StudyMode.EXAM,
            List.of(),
            List.of(),
            new MockHttpServletRequest(),
            QuizQuestionMode.MCQ_ONLY,
            Difficulty.MEDIUM,
            5,
            ExamQuestionSize.MEDIUM,
            5,
            null,
            ExamLayout.PER_PAGE,
            model,
            () -> "alice",
            new MockHttpSession(),
            response,
            "true"
        );

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("studyError")).isEqualTo("Please select at least one source.");
        verify(examSessionService).validateForPreflight(
            eq(List.of()),
            eq(List.of()),
            any(MockHttpServletRequest.class),
            eq(ExamQuestionSize.MEDIUM),
            eq(5),
            eq(null),
            eq(ExamLayout.PER_PAGE),
            eq(user)
        );
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
    }

    @Test
    void preflightCreateSession_FlashcardsInvalidSelection_ValidatesStudySessionAndSkipsQuota() {
        doThrow(new IllegalArgumentException("Please select at least one deck."))
            .when(studySessionService).buildSession(any(StudySessionConfig.class), eq(user));

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightCreateSession(
            StudyMode.FLASHCARDS,
            List.of(),
            List.of(),
            new MockHttpServletRequest(),
            QuizQuestionMode.MCQ_ONLY,
            Difficulty.MEDIUM,
            5,
            ExamQuestionSize.MEDIUM,
            5,
            null,
            ExamLayout.PER_PAGE,
            model,
            () -> "alice",
            new MockHttpSession(),
            response,
            "true"
        );

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("studyError")).isEqualTo("Please select at least one deck.");
        verify(studySessionService).buildSession(any(StudySessionConfig.class), eq(user));
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
    }
}
