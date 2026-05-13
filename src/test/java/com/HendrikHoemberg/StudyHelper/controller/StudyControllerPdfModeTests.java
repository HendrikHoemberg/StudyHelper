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

        ExamController examController = new ExamController(
                aiExamService, examService, userService, deckService, flashcardService,
                fileEntryService, documentExtractionService, aiRequestQuotaService);
        controller = new StudyController(
                studySessionService, aiQuizService, deckService, flashcardService, folderService,
                userService, documentExtractionService, fileEntryService, examController, aiRequestQuotaService);

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
        verify(aiQuizService).generate(anyList(), docsCaptor.capture(), eq(3), eq(QuizQuestionMode.MCQ_ONLY), eq(Difficulty.MEDIUM));
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
        verify(aiExamService).generate(anyList(), docsCaptor.capture(), eq(3), eq(ExamQuestionSize.MEDIUM));
        List<DocumentInput> docs = docsCaptor.getValue();
        assertThat(docs).hasSize(3);
        assertThat(docs.get(0)).isInstanceOf(TextDocument.class);
        assertThat(docs.get(1)).isInstanceOf(PdfDocument.class);
        assertThat(docs.get(2)).isInstanceOf(TextDocument.class);
        verify(documentExtractionService).loadResource(fullPdf);
        verify(documentExtractionService, never()).loadResource(md);
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
