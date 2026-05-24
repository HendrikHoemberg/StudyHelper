package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardChunk;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationPlan;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationRisk;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardPdfOption;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJob;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJobStatus;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiFlashcardService;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationDiagnostics;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationException;
import com.HendrikHoemberg.StudyHelper.service.AiQuotaExceededException;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.DocumentExtractionService;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardGenerationJobService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardGenerationPersistenceService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardGenerationPlanService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardGenerationViewService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlashcardGenerationControllerTests {

    private AiFlashcardService aiFlashcardService;
    private FlashcardGenerationPersistenceService persistenceService;
    private FlashcardGenerationViewService viewService;
    private UserService userService;
    private FileEntryService fileEntryService;
    private DocumentExtractionService documentExtractionService;
    private DeckService deckService;
    private FolderService folderService;
    private AiRequestQuotaService aiRequestQuotaService;
    private FlashcardGenerationPlanService planService;
    private FlashcardGenerationJobService jobService;
    private FlashcardGenerationController controller;
    private User user;
    private FileEntry pdf;
    private FlashcardGenerationJob job;

    @BeforeEach
    void setUp() {
        aiFlashcardService = mock(AiFlashcardService.class);
        persistenceService = mock(FlashcardGenerationPersistenceService.class);
        viewService = mock(FlashcardGenerationViewService.class);
        userService = mock(UserService.class);
        fileEntryService = mock(FileEntryService.class);
        documentExtractionService = mock(DocumentExtractionService.class);
        deckService = mock(DeckService.class);
        folderService = mock(FolderService.class);
        aiRequestQuotaService = mock(AiRequestQuotaService.class);
        planService = mock(FlashcardGenerationPlanService.class);
        jobService = mock(FlashcardGenerationJobService.class);
        controller = new FlashcardGenerationController(
            aiFlashcardService,
            persistenceService,
            viewService,
            userService,
            fileEntryService,
            documentExtractionService,
            deckService,
            folderService,
            aiRequestQuotaService,
            planService,
            jobService
        );

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);

        pdf = new FileEntry();
        pdf.setId(99L);
        pdf.setOriginalFilename("lecture.pdf");
        pdf.setFileSizeBytes(100L);
        pdf.setUser(user);

        job = new FlashcardGenerationJob();
        job.setId(77L);
    }

    @Test
    void showGenerator_Htmx_ReturnsFragmentWithModelData() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.showGenerator(null, model, () -> "alice", "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(model).containsKeys("pdfOptions", "pdfFolderTree", "deckTree", "folderTree", "destinations", "documentModes");
    }

    @Test
    void showGenerator_WithSelectedPdf_PrefillsNewDeckNameFromPdfName() {
        when(viewService.getPdfOptions(user)).thenReturn(List.of(
            new FlashcardPdfOption(98L, "Other.pdf", 10L, "Algorithms", "#6366f1", 100L),
            new FlashcardPdfOption(99L, "Chapter 1.pdf", 10L, "Algorithms", "#6366f1", 100L)
        ));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.showGenerator(List.of(99L), model, () -> "alice", "true");

        assertThat(model.get("newDeckName")).isEqualTo("Chapter 1");
    }

    @Test
    void showGenerator_WithMultipleSelectedPdfs_UsesNeutralDeckName() {
        when(viewService.getPdfOptions(user)).thenReturn(List.of(
            new FlashcardPdfOption(99L, "Chapter 1.pdf", 10L, "Algorithms", "#6366f1", 100L),
            new FlashcardPdfOption(100L, "Chapter 2.pdf", 10L, "Algorithms", "#6366f1", 100L)
        ));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.showGenerator(List.of(99L, 100L), model, () -> "alice", "true");

        assertThat(model.get("newDeckName")).isEqualTo("Generated Flashcards");
        assertThat(model.get("selectedFileIds")).isEqualTo(List.of(99L, 100L));
    }

    @Test
    void estimateGenerate_ValidSelection_ReturnsEstimateFragment() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        FlashcardGenerationPlan plan = new FlashcardGenerationPlan(
            List.of(new FlashcardChunk("lecture.pdf", 1, 1, 2, "text", null)),
            1, 8, 12, 5, 15, FlashcardGenerationRisk.NORMAL
        );
        when(planService.plan(anyList(), eq(DocumentMode.TEXT))).thenReturn(plan);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.estimateGenerate(
            List.of(99L), DocumentMode.TEXT, model, () -> "alice", response
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: estimate");
        assertThat(model.get("generationPlan")).isEqualTo(plan);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void estimateGenerate_InvalidSelection_ReturnsError() throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.estimateGenerate(
            List.of(), DocumentMode.TEXT, model, () -> "alice", response
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: estimate");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Please select at least one PDF.");
    }

    @Test
    void generate_TextModeExistingDeck_createsJobAndReturnsProgressFragment() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        FlashcardGenerationPlan plan = new FlashcardGenerationPlan(
            List.of(new FlashcardChunk("lecture.pdf", 1, 1, 2, "text", null)),
            1, 8, 12, 5, 15, FlashcardGenerationRisk.NORMAL
        );
        when(planService.plan(anyList(), eq(DocumentMode.TEXT))).thenReturn(plan);
        when(jobService.acceptJob(eq(user), eq(List.of(99L)), eq(DocumentMode.TEXT), eq(FlashcardGenerationDestination.EXISTING_DECK), eq(20L), eq(null), eq(null), eq("focus on definitions"), eq(plan))).thenReturn(job);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.generate(
            List.of(99L), DocumentMode.TEXT, "focus on definitions",
            FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null,
            false, model, () -> "alice", response, "true"
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: progress");
        assertThat(model.get("generationJob")).isEqualTo(job);
        assertThat(response.getHeader("HX-Trigger")).isEqualTo("refresh-quota");
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generate_FullPdfModeNewDeck_createsJobAndReturnsProgressFragment() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        FlashcardGenerationPlan plan = new FlashcardGenerationPlan(
            List.of(new FlashcardChunk("lecture.pdf", 1, 1, 2, "", null)),
            1, 8, 12, 5, 15, FlashcardGenerationRisk.NORMAL
        );
        when(planService.plan(anyList(), eq(DocumentMode.FULL_PDF))).thenReturn(plan);
        when(jobService.acceptJob(eq(user), eq(List.of(99L)), eq(DocumentMode.FULL_PDF), eq(FlashcardGenerationDestination.NEW_DECK), eq(null), eq(10L), eq("Lecture Deck"), eq("cover visual concepts"), eq(plan))).thenReturn(job);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.generate(
            List.of(99L), DocumentMode.FULL_PDF, "cover visual concepts",
            FlashcardGenerationDestination.NEW_DECK, null, 10L, "Lecture Deck",
            false, model, () -> "alice", response, "true"
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: progress");
        assertThat(model.get("generationJob")).isEqualTo(job);
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generate_highRiskWithoutAck_throwsError() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        FlashcardGenerationPlan plan = new FlashcardGenerationPlan(
            List.of(new FlashcardChunk("lecture.pdf", 1, 1, 2, "text", null)),
            50, 400, 600, 250, 750, FlashcardGenerationRisk.HIGH
        );
        when(planService.plan(anyList(), eq(DocumentMode.TEXT))).thenReturn(plan);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(
            List.of(99L), DocumentMode.TEXT, null,
            FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null,
            false, model, () -> "alice", response, "true"
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Please confirm the high-risk generation warning before continuing.");
        assertThat(model.get("generationPlan")).isEqualTo(plan);
        verify(jobService, never()).acceptJob(any(), anyList(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generate_TextModeEmptyText_ReturnsGeneratorErrorWithoutCallingAi() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        when(planService.plan(anyList(), eq(DocumentMode.TEXT)))
            .thenThrow(new IllegalArgumentException("lecture.pdf has no extractable text. Try Full PDF mode."));

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(List.of(99L), DocumentMode.TEXT, null, FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null, false, model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("lecture.pdf has no extractable text. Try Full PDF mode.");
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generate_MissingPdf_ReturnsGeneratorError() throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(List.of(), DocumentMode.TEXT, null, FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null, false, model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Please select at least one PDF.");
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generate_NewDeckBlankName_ReturnsErrorBeforeCallingAi() throws Exception {
        doThrow(new IllegalArgumentException("Deck name is required."))
            .when(persistenceService).validateDestination(FlashcardGenerationDestination.NEW_DECK, null, 10L, "   ", user);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(List.of(99L), DocumentMode.TEXT, null, FlashcardGenerationDestination.NEW_DECK, null, 10L, "   ", false, model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Deck name is required.");
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generate_NewDeckDuplicateName_ReturnsErrorBeforeCallingAi() throws Exception {
        doThrow(new IllegalArgumentException("A deck named \"lecture\" already exists in this folder."))
            .when(persistenceService).validateDestination(FlashcardGenerationDestination.NEW_DECK, null, 10L, "lecture", user);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(List.of(99L), DocumentMode.TEXT, null, FlashcardGenerationDestination.NEW_DECK, null, 10L, "lecture", false, model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("A deck named \"lecture\" already exists in this folder.");
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generate_NonPdf_ReturnsGeneratorError() throws Exception {
        FileEntry md = new FileEntry();
        md.setId(55L);
        md.setOriginalFilename("notes.md");
        md.setFileSizeBytes(100L);
        md.setUser(user);
        when(fileEntryService.getByIdAndUser(55L, user)).thenReturn(md);
        when(documentExtractionService.isSupported(md)).thenReturn(true);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(List.of(55L), DocumentMode.TEXT, null, FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null, false, model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Please select only supported PDFs under 10 MB.");
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generate_AiFailure_DoesNotPersist() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        FlashcardGenerationPlan plan = new FlashcardGenerationPlan(
            List.of(new FlashcardChunk("lecture.pdf", 1, 1, 2, "text", null)),
            1, 8, 12, 5, 15, FlashcardGenerationRisk.NORMAL
        );
        when(planService.plan(anyList(), eq(DocumentMode.TEXT))).thenReturn(plan);
        when(jobService.acceptJob(any(), anyList(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new AiGenerationException(
                "AI request failed, please retry with a smaller PDF or Text mode.",
                AiGenerationDiagnostics.fromException("FLASHCARDS", "PROVIDER_REQUEST", new RuntimeException("provider offline"))
            ));

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(List.of(99L), DocumentMode.TEXT, null, FlashcardGenerationDestination.NEW_DECK, null, 10L, "Deck", false, model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("AI request failed, please retry with a smaller PDF or Text mode.");
        assertThat(model.get("generationDetails").toString())
            .contains("Generation ID:")
            .contains("Type: FLASHCARDS")
            .contains("Stage: PROVIDER_REQUEST")
            .contains("provider offline");
        verify(persistenceService, never()).saveGeneratedCards(any(), any(), any(), any(), any(), any());
    }

    @Test
    void generate_QuotaExceeded_ReturnsGeneratorErrorWithoutCallingAi() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        FlashcardGenerationPlan plan = new FlashcardGenerationPlan(
            List.of(new FlashcardChunk("lecture.pdf", 1, 1, 2, "text", null)),
            1, 8, 12, 5, 15, FlashcardGenerationRisk.NORMAL
        );
        when(planService.plan(anyList(), eq(DocumentMode.TEXT))).thenReturn(plan);
        when(jobService.acceptJob(any(), anyList(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new AiQuotaExceededException("Daily AI request limit reached."));

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(
            List.of(99L), DocumentMode.TEXT, null,
            FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null,
            false, model, () -> "alice", response, "true"
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Daily AI request limit reached.");
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void preflightGenerate_ValidRequest_ReturnsNoContentAndSkipsQuotaAndAi() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightGenerate(
            List.of(99L), DocumentMode.TEXT,
            FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null,
            new ExtendedModelMap(), () -> "alice", response, "true"
        );

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(view).isNull();
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void preflightGenerate_InvalidRequest_ReturnsGeneratorErrorAndSkipsQuotaAndAi() throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightGenerate(
            List.of(), DocumentMode.TEXT,
            FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null,
            model, () -> "alice", response, "true"
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Please select at least one PDF.");
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generate_TextModeMultiplePdfs_createsJobAndReturnsProgressFragment() throws Exception {
        FileEntry secondPdf = new FileEntry();
        secondPdf.setId(100L);
        secondPdf.setOriginalFilename("lecture-2.pdf");
        secondPdf.setFileSizeBytes(100L);
        secondPdf.setUser(user);

        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(fileEntryService.getByIdAndUser(100L, user)).thenReturn(secondPdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        when(documentExtractionService.isSupported(secondPdf)).thenReturn(true);
        FlashcardGenerationPlan plan = new FlashcardGenerationPlan(
            List.of(
                new FlashcardChunk("lecture.pdf", 1, 1, 2, "text one", null),
                new FlashcardChunk("lecture-2.pdf", 2, 1, 2, "text two", null)
            ),
            2, 16, 24, 10, 30, FlashcardGenerationRisk.NORMAL
        );
        when(planService.plan(anyList(), eq(DocumentMode.TEXT))).thenReturn(plan);
        when(jobService.acceptJob(eq(user), eq(List.of(99L, 100L)), eq(DocumentMode.TEXT), eq(FlashcardGenerationDestination.NEW_DECK), eq(null), eq(10L), eq("Combined"), eq(null), eq(plan))).thenReturn(job);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(
            List.of(99L, 100L), DocumentMode.TEXT, null,
            FlashcardGenerationDestination.NEW_DECK, null, 10L, "Combined",
            false, model, () -> "alice", response, "true"
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: progress");
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generate_FullPdfModeMultiplePdfs_createsJobAndReturnsProgressFragment() throws Exception {
        FileEntry secondPdf = new FileEntry();
        secondPdf.setId(100L);
        secondPdf.setOriginalFilename("lecture-2.pdf");
        secondPdf.setFileSizeBytes(100L);
        secondPdf.setUser(user);

        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(fileEntryService.getByIdAndUser(100L, user)).thenReturn(secondPdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        when(documentExtractionService.isSupported(secondPdf)).thenReturn(true);
        FlashcardGenerationPlan plan = new FlashcardGenerationPlan(
            List.of(
                new FlashcardChunk("lecture.pdf", 1, 1, 2, "", null),
                new FlashcardChunk("lecture-2.pdf", 2, 1, 2, "", null)
            ),
            2, 16, 24, 10, 30, FlashcardGenerationRisk.NORMAL
        );
        when(planService.plan(anyList(), eq(DocumentMode.FULL_PDF))).thenReturn(plan);
        when(jobService.acceptJob(eq(user), eq(List.of(99L, 100L)), eq(DocumentMode.FULL_PDF), eq(FlashcardGenerationDestination.EXISTING_DECK), eq(20L), eq(null), eq(null), eq(null), eq(plan))).thenReturn(job);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(
            List.of(99L, 100L), DocumentMode.FULL_PDF, null,
            FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null,
            false, model, () -> "alice", response, "true"
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: progress");
        verify(aiFlashcardService, never()).generate(anyList(), anyInt(), any());
    }

    @Test
    void generationStatus_Running_ReturnsProgress() {
        job.setStatus(FlashcardGenerationJobStatus.RUNNING);
        when(jobService.getJob(77L, user)).thenReturn(job);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generationStatus(77L, model, () -> "alice", response);

        assertThat(view).isEqualTo("fragments/flashcard-generator :: progress");
        assertThat(model.get("generationJob")).isEqualTo(job);
        assertThat(response.getHeader("HX-Redirect")).isNull();
    }

    @Test
    void generationStatus_Succeeded_RedirectsToDeck() {
        job.setStatus(FlashcardGenerationJobStatus.SUCCEEDED);
        job.setSavedDeckId(42L);
        when(jobService.getJob(77L, user)).thenReturn(job);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generationStatus(77L, model, () -> "alice", response);

        assertThat(view).isEqualTo("fragments/flashcard-generator :: progress");
        assertThat(response.getHeader("HX-Redirect")).isEqualTo("/decks/42");
    }

    @Test
    void generationStatus_Failed_RefreshesQuota() {
        job.setStatus(FlashcardGenerationJobStatus.FAILED);
        job.setFailureMessage("Something went wrong");
        when(jobService.getJob(77L, user)).thenReturn(job);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generationStatus(77L, model, () -> "alice", response);

        assertThat(view).isEqualTo("fragments/flashcard-generator :: progress");
        assertThat(response.getHeader("HX-Trigger")).isEqualTo("refresh-quota");
    }

    @Test
    void cancelJob_cancelsJobAndRefreshesQuota() {
        job.setStatus(FlashcardGenerationJobStatus.CANCELLED);
        when(jobService.getJob(77L, user)).thenReturn(job);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.cancelJob(77L, model, () -> "alice", response);

        assertThat(view).isEqualTo("fragments/flashcard-generator :: progress");
        assertThat(model.get("generationJob")).isEqualTo(job);
        assertThat(response.getHeader("HX-Trigger")).isEqualTo("refresh-quota");
        verify(jobService).cancelJob(77L, user);
    }
}
