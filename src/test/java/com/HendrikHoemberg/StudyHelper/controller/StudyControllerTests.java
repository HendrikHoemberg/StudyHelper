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
        aiRequestQuotaService = mock(AiRequestQuotaService.class);

        ExamController examController = new ExamController(
            aiExamService,
            examService,
            userService,
            deckService,
            flashcardService,
            fileEntryService,
            documentExtractionService,
            aiRequestQuotaService
        );
        controller = new StudyController(
            studySessionService,
            aiQuizService,
            deckService,
            flashcardService,
            folderService,
            userService,
            documentExtractionService,
            fileEntryService,
            examController,
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
    void preflightCreateSession_ExamWithoutSources_UsesExamErrorFragmentConvention() {
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

        assertThat(view).isEqualTo("fragments/ai-generation-error :: aiGenerationError");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("aiErrorMessage")).isEqualTo("Please select at least one source.");
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
