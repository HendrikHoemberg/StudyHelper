package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardPdfOption;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiFlashcardService;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationDiagnostics;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationException;
import com.HendrikHoemberg.StudyHelper.service.AiQuotaExceededException;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.DocumentExtractionService;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardGenerationPersistenceService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardGenerationViewService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private FlashcardGenerationController controller;
    private User user;
    private FileEntry pdf;
    private Deck deck;
    private Folder folder;

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
        controller = new FlashcardGenerationController(
            aiFlashcardService,
            persistenceService,
            viewService,
            userService,
            fileEntryService,
            documentExtractionService,
            deckService,
            folderService,
            aiRequestQuotaService
        );

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);

        folder = new Folder();
        folder.setId(10L);
        folder.setName("Algorithms");
        folder.setUser(user);

        pdf = new FileEntry();
        pdf.setId(99L);
        pdf.setOriginalFilename("lecture.pdf");
        pdf.setFileSizeBytes(100L);
        pdf.setFolder(folder);
        pdf.setUser(user);

        deck = new Deck();
        deck.setId(20L);
        deck.setName("Generated");
        deck.setFolder(folder);
        deck.setUser(user);
        deck.setFlashcards(new ArrayList<Flashcard>());
    }

    @Test
    void showGenerator_Htmx_ReturnsFragmentWithModelData() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.showGenerator(null, model, () -> "alice", "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(model).containsKeys("pdfOptions", "deckTree", "folderTree", "destinations", "documentModes");
    }

    @Test
    void showGenerator_WithSelectedPdf_PrefillsNewDeckNameFromPdfName() {
        when(viewService.getPdfOptions(user)).thenReturn(List.of(
            new FlashcardPdfOption(98L, "Other.pdf", 10L, "Algorithms", "#6366f1", 100L),
            new FlashcardPdfOption(99L, "Chapter 1.pdf", 10L, "Algorithms", "#6366f1", 100L)
        ));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.showGenerator(99L, model, () -> "alice", "true");

        assertThat(model.get("newDeckName")).isEqualTo("Chapter 1");
    }

    @Test
    void generate_TextModeExistingDeck_SavesAndReturnsDeckFragment() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        when(documentExtractionService.extractText(pdf)).thenReturn("Lecture text");
        when(aiFlashcardService.generate(any(DocumentInput.class), anyInt(), any())).thenReturn(List.of(new GeneratedFlashcard("Q", "A")));
        when(persistenceService.saveGeneratedCards(eq(FlashcardGenerationDestination.EXISTING_DECK), eq(20L), eq(null), eq(null), eq(user), any())).thenReturn(deck);
        when(deckService.getDeck(20L, user)).thenReturn(deck);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.generate(
            99L,
            DocumentMode.TEXT,
            "focus on definitions",
            20,
            FlashcardGenerationDestination.EXISTING_DECK,
            20L,
            null,
            null,
            model,
            () -> "alice",
            response,
            "true"
        );

        ArgumentCaptor<DocumentInput> docCaptor = ArgumentCaptor.forClass(DocumentInput.class);
        verify(aiFlashcardService).generate(docCaptor.capture(), eq(20), eq("focus on definitions"));
        assertThat(docCaptor.getValue()).isInstanceOf(TextDocument.class);
        assertThat(view).isEqualTo("fragments/deck :: deckDetail");
        assertThat(response.getHeader("HX-Push-Url")).isEqualTo("/decks/20");
        assertThat(model.get("successMessage")).isEqualTo("Generated 1 flashcard from lecture.pdf.");
    }

    @Test
    void generate_FullPdfModeNewDeck_SendsPdfDocumentAndSaves() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        when(documentExtractionService.loadResource(pdf)).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));
        when(aiFlashcardService.generate(any(DocumentInput.class), anyInt(), any())).thenReturn(List.of(new GeneratedFlashcard("Q", "A"), new GeneratedFlashcard("Q2", "A2")));
        when(persistenceService.saveGeneratedCards(eq(FlashcardGenerationDestination.NEW_DECK), eq(null), eq(10L), eq("Lecture Deck"), eq(user), any())).thenReturn(deck);
        when(deckService.getDeck(20L, user)).thenReturn(deck);

        controller.generate(99L, DocumentMode.FULL_PDF, "cover visual concepts", 20, FlashcardGenerationDestination.NEW_DECK, null, 10L, "Lecture Deck", new ExtendedModelMap(), () -> "alice", new MockHttpServletResponse(), "true");

        ArgumentCaptor<DocumentInput> docCaptor = ArgumentCaptor.forClass(DocumentInput.class);
        verify(aiFlashcardService).generate(docCaptor.capture(), eq(20), eq("cover visual concepts"));
        assertThat(docCaptor.getValue()).isInstanceOf(PdfDocument.class);
        verify(documentExtractionService, never()).extractText(pdf);
    }

    @Test
    void generate_TextModeEmptyText_ReturnsGeneratorErrorWithoutCallingAi() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        when(documentExtractionService.extractText(pdf)).thenReturn(" ");

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(99L, DocumentMode.TEXT, null, 20, FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null, model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("This PDF has no extractable text. Try Full PDF mode.");
        verify(aiFlashcardService, never()).generate(any(DocumentInput.class), anyInt(), any());
    }

    @Test
    void generate_MissingPdf_ReturnsGeneratorError() throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(null, DocumentMode.TEXT, null, 20, FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null, model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Please select one PDF.");
        verify(aiFlashcardService, never()).generate(any(DocumentInput.class), anyInt(), any());
    }

    @Test
    void generate_NewDeckBlankName_ReturnsErrorBeforeCallingAi() throws Exception {
        doThrow(new IllegalArgumentException("Deck name is required."))
            .when(persistenceService).validateDestination(FlashcardGenerationDestination.NEW_DECK, null, 10L, "   ", user);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(99L, DocumentMode.TEXT, null, 20, FlashcardGenerationDestination.NEW_DECK, null, 10L, "   ", model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Deck name is required.");
        verify(aiFlashcardService, never()).generate(any(DocumentInput.class), anyInt(), any());
    }

    @Test
    void generate_NewDeckDuplicateName_ReturnsErrorBeforeCallingAi() throws Exception {
        doThrow(new IllegalArgumentException("A deck named \"lecture\" already exists in this folder."))
            .when(persistenceService).validateDestination(FlashcardGenerationDestination.NEW_DECK, null, 10L, "lecture", user);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(99L, DocumentMode.TEXT, null, 20, FlashcardGenerationDestination.NEW_DECK, null, 10L, "lecture", model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("A deck named \"lecture\" already exists in this folder.");
        verify(aiFlashcardService, never()).generate(any(DocumentInput.class), anyInt(), any());
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

        String view = controller.generate(55L, DocumentMode.TEXT, null, 20, FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null, model, () -> "alice", response, "true");

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Please select a supported PDF under 10 MB.");
        verify(aiFlashcardService, never()).generate(any(DocumentInput.class), anyInt(), any());
    }

    @Test
    void generate_AiFailure_DoesNotPersist() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        when(documentExtractionService.extractText(pdf)).thenReturn("Lecture text");
        when(aiFlashcardService.generate(any(DocumentInput.class), anyInt(), any()))
            .thenThrow(new AiGenerationException(
                "AI request failed, please retry with a smaller PDF or Text mode.",
                AiGenerationDiagnostics.fromException("FLASHCARDS", "PROVIDER_REQUEST", new RuntimeException("provider offline"))
            ));

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(99L, DocumentMode.TEXT, null, 20, FlashcardGenerationDestination.NEW_DECK, null, 10L, "Deck", model, () -> "alice", response, "true");

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
        when(documentExtractionService.extractText(pdf)).thenReturn("Lecture text");
        doThrow(new AiQuotaExceededException("Daily AI request limit reached."))
            .when(aiRequestQuotaService).checkAndRecord(user);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.generate(
            99L,
            DocumentMode.TEXT,
            null,
            20,
            FlashcardGenerationDestination.EXISTING_DECK,
            20L,
            null,
            null,
            model,
            () -> "alice",
            response,
            "true"
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Daily AI request limit reached.");
        verify(aiFlashcardService, never()).generate(any(DocumentInput.class), anyInt(), any());
    }

    @Test
    void preflightGenerate_ValidRequest_ReturnsNoContentAndSkipsQuotaAndAi() throws Exception {
        when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
        when(documentExtractionService.isSupported(pdf)).thenReturn(true);
        when(documentExtractionService.extractText(pdf)).thenReturn("Lecture text");

        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightGenerate(
            99L,
            DocumentMode.TEXT,
            FlashcardGenerationDestination.EXISTING_DECK,
            20L,
            null,
            null,
            new ExtendedModelMap(),
            () -> "alice",
            response,
            "true"
        );

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(view).isNull();
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
        verify(aiFlashcardService, never()).generate(any(DocumentInput.class), anyInt(), any());
    }

    @Test
    void preflightGenerate_InvalidRequest_ReturnsGeneratorErrorAndSkipsQuotaAndAi() throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightGenerate(
            null,
            DocumentMode.TEXT,
            FlashcardGenerationDestination.EXISTING_DECK,
            20L,
            null,
            null,
            model,
            () -> "alice",
            response,
            "true"
        );

        assertThat(view).isEqualTo("fragments/flashcard-generator :: generator");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("generationError")).isEqualTo("Please select one PDF.");
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
        verify(aiFlashcardService, never()).generate(any(DocumentInput.class), anyInt(), any());
    }
}
