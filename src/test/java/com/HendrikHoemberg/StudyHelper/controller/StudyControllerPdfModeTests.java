package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudyControllerPdfModeTests {

    private StudySessionService studySessionService;
    private AiQuizService aiQuizService;
    private AiExamService aiExamService;
    private ExamService examService;
    private DeckService deckService;
    private FlashcardService flashcardService;
    private FolderService folderService;
    private UserService userService;
    private DocumentExtractionService documentExtractionService;
    private FileEntryService fileEntryService;
    private AiRequestQuotaService aiRequestQuotaService;
    private StudyController controller;
    private User user;

    @BeforeEach
    void setUp() {
        studySessionService = mock(StudySessionService.class);
        aiQuizService = mock(AiQuizService.class);
        aiExamService = mock(AiExamService.class);
        examService = mock(ExamService.class);
        deckService = mock(DeckService.class);
        flashcardService = mock(FlashcardService.class);
        folderService = mock(FolderService.class);
        userService = mock(UserService.class);
        documentExtractionService = mock(DocumentExtractionService.class);
        fileEntryService = mock(FileEntryService.class);
        aiRequestQuotaService = mock(AiRequestQuotaService.class);

        QuizSessionService quizSessionService = new QuizSessionService(
                aiQuizService, deckService, flashcardService, fileEntryService,
                documentExtractionService, aiRequestQuotaService);
        ExamSessionService examSessionService = new ExamSessionService(
                aiExamService, deckService, flashcardService, fileEntryService,
                documentExtractionService, aiRequestQuotaService);
        SavedSessionService savedSessionService = mock(SavedSessionService.class);
        SrsScheduler srsScheduler = mock(SrsScheduler.class);
        controller = new StudyController(
                studySessionService, quizSessionService, deckService, folderService,
                userService, documentExtractionService, fileEntryService, examSessionService, savedSessionService,
                flashcardService, srsScheduler);

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setDailyAiRequestLimit(100);
        when(userService.getByUsername("alice")).thenReturn(user);
        when(folderService.getStudyFolderTree(eq(user), anyList())).thenReturn(List.of());
        when(folderService.getQuizSourceTree(eq(user), anyList(), anyList())).thenReturn(List.of());
    }

    @Test
    void updateSetup_preservesPdfModeMapInModel() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[42]", "FULL_PDF");
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.updateSetup(
                StudyMode.FLASHCARDS,
                List.of(),
                List.of(42L),
                null,
                null,
                null,
                false,
                request,
                model,
                () -> "alice",
                new MockHttpSession());

        assertThat(view).isEqualTo("fragments/wizard-source-picker :: setupPicker");
        assertThat((Map<Long, DocumentMode>) model.get("pdfMode"))
                .containsEntry(42L, DocumentMode.FULL_PDF);
    }

    @Test
    void updateSetup_removeFileId_removesFilePdfMode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[42]", "FULL_PDF");
        request.addParameter("pdfMode[43]", "TEXT");
        ExtendedModelMap model = new ExtendedModelMap();

        controller.updateSetup(
                StudyMode.FLASHCARDS,
                List.of(),
                List.of(42L, 43L),
                null,
                null,
                42L,
                false,
                request,
                model,
                () -> "alice",
                new MockHttpSession());

        assertThat((List<Long>) model.get("preselectedFileIds")).containsExactly(43L);
        assertThat((Map<Long, DocumentMode>) model.get("pdfMode"))
                .doesNotContainKey(42L)
                .containsEntry(43L, DocumentMode.TEXT);
    }

    @Test
    void updateSetup_clearAll_removesAllPdfMode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[42]", "FULL_PDF");
        ExtendedModelMap model = new ExtendedModelMap();

        controller.updateSetup(
                StudyMode.FLASHCARDS,
                List.of(10L),
                List.of(42L),
                null,
                null,
                null,
                true,
                request,
                model,
                () -> "alice",
                new MockHttpSession());

        assertThat((List<Long>) model.get("preselectedDeckIds")).isEmpty();
        assertThat((List<Long>) model.get("preselectedFileIds")).isEmpty();
        assertThat((Map<Long, DocumentMode>) model.get("pdfMode")).isEmpty();
    }

    @Test
    void updateSetup_folderUnselect_removesFolderFilePdfModes() {
        when(folderService.getAllSourcesInFolder(99L, StudyMode.FLASHCARDS, user))
                .thenReturn(new FolderService.FolderSources(List.of(10L), List.of(42L, 43L)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[42]", "FULL_PDF");
        request.addParameter("pdfMode[43]", "TEXT");
        request.addParameter("pdfMode[50]", "FULL_PDF");
        ExtendedModelMap model = new ExtendedModelMap();

        controller.updateSetup(
                StudyMode.FLASHCARDS,
                List.of(10L, 11L),
                List.of(42L, 43L, 50L),
                99L,
                null,
                null,
                false,
                request,
                model,
                () -> "alice",
                new MockHttpSession());

        assertThat((List<Long>) model.get("preselectedDeckIds")).containsExactly(11L);
        assertThat((List<Long>) model.get("preselectedFileIds")).containsExactly(50L);
        assertThat((Map<Long, DocumentMode>) model.get("pdfMode"))
                .doesNotContainKeys(42L, 43L)
                .containsEntry(50L, DocumentMode.FULL_PDF);
    }

    @Test
    void createSession_quizWithFiles_returnsValidationError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
                StudyMode.QUIZ,
                List.of(10L),
                List.of(1L),
                null,
                request,
                SessionMode.DECK_BY_DECK,
                DeckOrderMode.SELECTED_ORDER,
                false,
                null,
                QuizQuestionMode.MCQ_ONLY,
                Difficulty.MEDIUM,
                5,
                ExamQuestionSize.MEDIUM,
                5,
                null,
                ExamLayout.PER_PAGE,
                false,
                model,
                () -> "alice",
                new MockHttpSession(),
                response,
                "true");

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("studyError")).isEqualTo("Quizzes can only be generated from card decks.");
    }

    @Test
    void createSession_examWithFiles_returnsValidationError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
                StudyMode.EXAM,
                List.of(10L),
                List.of(1L),
                null,
                request,
                SessionMode.DECK_BY_DECK,
                DeckOrderMode.SELECTED_ORDER,
                false,
                null,
                QuizQuestionMode.MCQ_ONLY,
                Difficulty.MEDIUM,
                5,
                ExamQuestionSize.MEDIUM,
                5,
                null,
                ExamLayout.PER_PAGE,
                false,
                model,
                () -> "alice",
                new MockHttpSession(),
                response,
                "true");

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("studyError")).isEqualTo("Exams can only be generated from card decks.");
    }
}
