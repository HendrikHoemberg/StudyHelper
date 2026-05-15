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
        controller = new StudyController(
                studySessionService, quizSessionService, deckService, folderService,
                userService, documentExtractionService, fileEntryService, examSessionService);

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
                StudyMode.QUIZ,
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
    void updateSetup_withoutPrincipal_redirectsToLogin() {
        String view = controller.updateSetup(
                StudyMode.QUIZ,
                List.of(),
                List.of(),
                null,
                null,
                null,
                false,
                new MockHttpServletRequest(),
                new ExtendedModelMap(),
                null,
                new MockHttpSession());

        assertThat(view).isEqualTo("redirect:/login");
        verifyNoInteractions(userService);
    }

    @Test
    void updateSetup_removeFileId_removesFilePdfMode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[42]", "FULL_PDF");
        request.addParameter("pdfMode[43]", "TEXT");
        ExtendedModelMap model = new ExtendedModelMap();

        controller.updateSetup(
                StudyMode.QUIZ,
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
                StudyMode.QUIZ,
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
        when(folderService.getAllSourcesInFolder(99L, user))
                .thenReturn(new FolderService.FolderSources(List.of(10L), List.of(42L, 43L)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[42]", "FULL_PDF");
        request.addParameter("pdfMode[43]", "TEXT");
        request.addParameter("pdfMode[50]", "FULL_PDF");
        ExtendedModelMap model = new ExtendedModelMap();

        controller.updateSetup(
                StudyMode.QUIZ,
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
    void createSession_quizMixedPdfModes_passesCorrectDocumentInputs() throws Exception {
        FileEntry textPdf = file(1L, "text.pdf", "text.pdf");
        FileEntry fullPdf = file(2L, "visual.pdf", "visual.pdf");
        FileEntry md = file(3L, "notes.md", "notes.md");
        when(fileEntryService.getByIdAndUser(1L, user)).thenReturn(textPdf);
        when(fileEntryService.getByIdAndUser(2L, user)).thenReturn(fullPdf);
        when(fileEntryService.getByIdAndUser(3L, user)).thenReturn(md);
        when(documentExtractionService.isSupported(textPdf)).thenReturn(true);
        when(documentExtractionService.isSupported(fullPdf)).thenReturn(true);
        when(documentExtractionService.extractText(textPdf)).thenReturn("text pdf content");
        when(documentExtractionService.extractText(md)).thenReturn("markdown content");
        when(documentExtractionService.loadResource(fullPdf)).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));
        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user))).thenReturn(List.of());
        when(flashcardService.getFlashcardsFlattened(anyList())).thenReturn(List.of());
        when(aiQuizService.generate(anyList(), anyList(), anyInt(), any(), any()))
                .thenReturn(List.of(new QuizQuestion(QuestionType.MULTIPLE_CHOICE, "Q", List.of("a", "b", "c", "d"), 0)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[1]", "TEXT");
        request.addParameter("pdfMode[2]", "FULL_PDF");
        request.addParameter("pdfMode[3]", "FULL_PDF");

        controller.createSession(
                StudyMode.QUIZ,
                List.of(),
                List.of(1L, 2L, 3L),
                "quiz instructions",
                request,
                SessionMode.DECK_BY_DECK,
                DeckOrderMode.SELECTED_ORDER,
                null,
                QuizQuestionMode.MCQ_ONLY,
                Difficulty.MEDIUM,
                3,
                ExamQuestionSize.MEDIUM,
                5,
                null,
                ExamLayout.PER_PAGE,
                new ExtendedModelMap(),
                () -> "alice",
                new MockHttpSession(),
                new MockHttpServletResponse(),
                null);

        ArgumentCaptor<List<DocumentInput>> docsCaptor = ArgumentCaptor.captor();
        verify(aiQuizService).generate(anyList(), docsCaptor.capture(), eq(3), eq(QuizQuestionMode.MCQ_ONLY), eq(Difficulty.MEDIUM), eq("quiz instructions"));
        List<DocumentInput> docs = docsCaptor.getValue();
        assertThat(docs).hasSize(3);
        assertThat(docs.get(0)).isInstanceOf(TextDocument.class);
        assertThat(docs.get(1)).isInstanceOf(PdfDocument.class);
        assertThat(docs.get(2)).isInstanceOf(TextDocument.class);
        assertThat(((TextDocument) docs.get(0)).extractedText()).isEqualTo("text pdf content");
        assertThat(((TextDocument) docs.get(2)).extractedText()).isEqualTo("markdown content");
        verify(documentExtractionService).loadResource(fullPdf);
        verify(documentExtractionService, never()).loadResource(md);
    }

    @Test
    void createSession_examMixedPdfModes_passesCorrectDocumentInputs() throws Exception {
        FileEntry textPdf = file(1L, "text.pdf", "text.pdf");
        FileEntry fullPdf = file(2L, "visual.pdf", "visual.pdf");
        FileEntry md = file(3L, "notes.md", "notes.md");
        when(fileEntryService.getByIdAndUser(1L, user)).thenReturn(textPdf);
        when(fileEntryService.getByIdAndUser(2L, user)).thenReturn(fullPdf);
        when(fileEntryService.getByIdAndUser(3L, user)).thenReturn(md);
        when(documentExtractionService.isSupported(textPdf)).thenReturn(true);
        when(documentExtractionService.isSupported(fullPdf)).thenReturn(true);
        when(documentExtractionService.extractText(textPdf)).thenReturn("text pdf content");
        when(documentExtractionService.extractText(md)).thenReturn("markdown content");
        when(documentExtractionService.loadResource(fullPdf)).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));
        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user))).thenReturn(List.of());
        when(flashcardService.getFlashcardsFlattened(anyList())).thenReturn(List.of());
        when(aiExamService.generate(anyList(), anyList(), anyInt(), any()))
                .thenReturn(List.of(new ExamQuestion("Q", "rubric")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[1]", "TEXT");
        request.addParameter("pdfMode[2]", "FULL_PDF");
        request.addParameter("pdfMode[3]", "FULL_PDF");

        controller.createSession(
                StudyMode.EXAM,
                List.of(),
                List.of(1L, 2L, 3L),
                "exam instructions",
                request,
                SessionMode.DECK_BY_DECK,
                DeckOrderMode.SELECTED_ORDER,
                null,
                QuizQuestionMode.MCQ_ONLY,
                Difficulty.MEDIUM,
                5,
                ExamQuestionSize.MEDIUM,
                3,
                null,
                ExamLayout.PER_PAGE,
                new ExtendedModelMap(),
                () -> "alice",
                new MockHttpSession(),
                new MockHttpServletResponse(),
                null);

        ArgumentCaptor<List<DocumentInput>> docsCaptor = ArgumentCaptor.captor();
        verify(aiExamService).generate(anyList(), docsCaptor.capture(), eq(3), eq(ExamQuestionSize.MEDIUM), eq("exam instructions"));
        List<DocumentInput> docs = docsCaptor.getValue();
        assertThat(docs).hasSize(3);
        assertThat(docs.get(0)).isInstanceOf(TextDocument.class);
        assertThat(docs.get(1)).isInstanceOf(PdfDocument.class);
        assertThat(docs.get(2)).isInstanceOf(TextDocument.class);
        verify(documentExtractionService).loadResource(fullPdf);
        verify(documentExtractionService, never()).loadResource(md);
    }

    @Test
    void preflightCreateSession_examValidRequest_DoesNotCallQuotaOrAi() throws Exception {
        FileEntry file = file(1L, "text.pdf", "text.pdf");
        when(fileEntryService.getByIdAndUser(1L, user)).thenReturn(file);
        when(documentExtractionService.isSupported(file)).thenReturn(true);
        when(documentExtractionService.extractText(file)).thenReturn("exam text");
        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user))).thenReturn(List.of());
        when(flashcardService.getFlashcardsFlattened(anyList())).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[1]", "TEXT");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightCreateSession(
                StudyMode.EXAM,
                List.of(),
                List.of(1L),
                request,
                QuizQuestionMode.MCQ_ONLY,
                Difficulty.MEDIUM,
                5,
                ExamQuestionSize.MEDIUM,
                5,
                null,
                ExamLayout.PER_PAGE,
                new ExtendedModelMap(),
                () -> "alice",
                new MockHttpSession(),
                response,
                "true");

        assertThat(view).isNull();
        assertThat(response.getStatus()).isEqualTo(204);
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
        verify(aiExamService, never()).generate(anyList(), anyList(), anyInt(), any(), any());
    }

    @Test
    void preflightCreateSession_quizHighQuestionCount_UsesGenerationSemanticsAndSkipsQuotaAi() throws Exception {
        FileEntry file = file(1L, "quiz.pdf", "quiz.pdf");
        when(fileEntryService.getByIdAndUser(1L, user)).thenReturn(file);
        when(documentExtractionService.isSupported(file)).thenReturn(true);
        when(documentExtractionService.extractText(file)).thenReturn("quiz text");
        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user))).thenReturn(List.of());
        when(flashcardService.getFlashcardsFlattened(anyList())).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[1]", "TEXT");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightCreateSession(
                StudyMode.QUIZ,
                List.of(),
                List.of(1L),
                request,
                QuizQuestionMode.MCQ_ONLY,
                Difficulty.MEDIUM,
                999,
                ExamQuestionSize.MEDIUM,
                5,
                null,
                ExamLayout.PER_PAGE,
                new ExtendedModelMap(),
                () -> "alice",
                new MockHttpSession(),
                response,
                "true");

        assertThat(view).isNull();
        assertThat(response.getStatus()).isEqualTo(204);
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
        verify(aiQuizService, never()).generate(anyList(), anyList(), anyInt(), any(), any(), any());
    }

    @Test
    void createSession_quizNullExtractedText_ReturnsValidationErrorWithoutQuotaOrAi() throws Exception {
        FileEntry file = file(1L, "quiz.pdf", "quiz.pdf");
        when(fileEntryService.getByIdAndUser(1L, user)).thenReturn(file);
        when(documentExtractionService.isSupported(file)).thenReturn(true);
        when(documentExtractionService.extractText(file)).thenReturn(null);
        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user))).thenReturn(List.of());
        when(flashcardService.getFlashcardsFlattened(anyList())).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[1]", "TEXT");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
                StudyMode.QUIZ,
                List.of(),
                List.of(1L),
                null,
                request,
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
                "true");

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("HX-Trigger")).isNull();
        assertThat(model.get("studyError")).isEqualTo("This PDF has no extractable text. Try Full PDF mode.");
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
        verify(aiQuizService, never()).generate(anyList(), anyList(), anyInt(), any(), any(), any());
    }

    @Test
    void createSession_examBlankExtractedText_ReturnsValidationErrorWithoutQuotaOrAi() throws Exception {
        FileEntry file = file(1L, "exam.pdf", "exam.pdf");
        when(fileEntryService.getByIdAndUser(1L, user)).thenReturn(file);
        when(documentExtractionService.isSupported(file)).thenReturn(true);
        when(documentExtractionService.extractText(file)).thenReturn("   ");
        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user))).thenReturn(List.of());
        when(flashcardService.getFlashcardsFlattened(anyList())).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[1]", "TEXT");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
                StudyMode.EXAM,
                List.of(),
                List.of(1L),
                null,
                request,
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
                "true");

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("HX-Trigger")).isNull();
        assertThat(model.get("studyError")).isEqualTo("This PDF has no extractable text. Try Full PDF mode.");
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
        verify(aiExamService, never()).generate(anyList(), anyList(), anyInt(), any(), any());
    }

    @Test
    void createSession_examProviderFailureAfterQuotaRefreshesQuota() throws Exception {
        FileEntry file = file(1L, "exam.pdf", "exam.pdf");
        when(fileEntryService.getByIdAndUser(1L, user)).thenReturn(file);
        when(documentExtractionService.isSupported(file)).thenReturn(true);
        when(documentExtractionService.extractText(file)).thenReturn("exam text");
        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user))).thenReturn(List.of());
        when(flashcardService.getFlashcardsFlattened(anyList())).thenReturn(List.of());
        when(aiExamService.generate(anyList(), anyList(), anyInt(), any(), any()))
                .thenThrow(new AiGenerationException("provider unavailable",
                        AiGenerationDiagnostics.fromException("EXAM", "PROVIDER_REQUEST",
                                new RuntimeException("provider unavailable"))));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[1]", "TEXT");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
                StudyMode.EXAM,
                List.of(),
                List.of(1L),
                null,
                request,
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
                "true");

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("HX-Trigger")).isEqualTo("refresh-quota");
        assertThat(model.get("studyError")).isEqualTo("provider unavailable");
        verify(aiRequestQuotaService).checkAndRecord(user);
        verify(aiExamService).generate(anyList(), anyList(), eq(5), eq(ExamQuestionSize.MEDIUM), isNull());
    }

    @Test
    void createSession_examQuotaExceededReturnsErrorWithoutRefreshQuota() throws Exception {
        FileEntry file = file(1L, "exam.pdf", "exam.pdf");
        when(fileEntryService.getByIdAndUser(1L, user)).thenReturn(file);
        when(documentExtractionService.isSupported(file)).thenReturn(true);
        when(documentExtractionService.extractText(file)).thenReturn("exam text");
        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user))).thenReturn(List.of());
        when(flashcardService.getFlashcardsFlattened(anyList())).thenReturn(List.of());
        doThrow(new AiQuotaExceededException("Daily AI request limit reached."))
                .when(aiRequestQuotaService).checkAndRecord(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pdfMode[1]", "TEXT");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
                StudyMode.EXAM,
                List.of(),
                List.of(1L),
                null,
                request,
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
                "true");

        assertThat(view).isEqualTo("fragments/study-setup :: studySetup");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("HX-Trigger")).isNull();
        assertThat(model.get("studyError")).isEqualTo("Daily AI request limit reached.");
        verify(aiRequestQuotaService).checkAndRecord(user);
        verify(aiExamService, never()).generate(anyList(), anyList(), anyInt(), any(), any());
    }

    private FileEntry file(Long id, String originalFilename, String storedFilename) {
        FileEntry file = new FileEntry();
        file.setId(id);
        file.setOriginalFilename(originalFilename);
        file.setStoredFilename(storedFilename);
        file.setFileSizeBytes(100L);
        file.setUser(user);
        return file;
    }
}
