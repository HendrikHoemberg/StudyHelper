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
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiFlashcardService;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationDiagnostics;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationException;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.DocumentExtractionService;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardGenerationPersistenceService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardGenerationViewService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

@Controller
public class FlashcardGenerationController {

    private final AiFlashcardService aiFlashcardService;
    private final FlashcardGenerationPersistenceService persistenceService;
    private final FlashcardGenerationViewService viewService;
    private final UserService userService;
    private final FileEntryService fileEntryService;
    private final DocumentExtractionService documentExtractionService;
    private final DeckService deckService;
    private final FolderService folderService;
    private final AiRequestQuotaService aiRequestQuotaService;

    @Autowired
    public FlashcardGenerationController(AiFlashcardService aiFlashcardService,
                                         FlashcardGenerationPersistenceService persistenceService,
                                         FlashcardGenerationViewService viewService,
                                         UserService userService,
                                         FileEntryService fileEntryService,
                                         DocumentExtractionService documentExtractionService,
                                         DeckService deckService,
                                         FolderService folderService,
                                         AiRequestQuotaService aiRequestQuotaService) {
        this.aiFlashcardService = aiFlashcardService;
        this.persistenceService = persistenceService;
        this.viewService = viewService;
        this.userService = userService;
        this.fileEntryService = fileEntryService;
        this.documentExtractionService = documentExtractionService;
        this.deckService = deckService;
        this.folderService = folderService;
        this.aiRequestQuotaService = aiRequestQuotaService;
    }

    public FlashcardGenerationController(AiFlashcardService aiFlashcardService,
                                         FlashcardGenerationPersistenceService persistenceService,
                                         FlashcardGenerationViewService viewService,
                                         UserService userService,
                                         FileEntryService fileEntryService,
                                         DocumentExtractionService documentExtractionService,
                                         DeckService deckService,
                                         FolderService folderService) {
        this(
            aiFlashcardService,
            persistenceService,
            viewService,
            userService,
            fileEntryService,
            documentExtractionService,
            deckService,
            folderService,
            null
        );
    }

    @GetMapping("/flashcards/generate")
    public String showGenerator(@RequestParam(required = false) Long fileId,
                                Model model,
                                Principal principal,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        List<FlashcardPdfOption> pdfOptions = prepareGeneratorModel(model, user);
        model.addAttribute("selectedFileId", fileId);
        model.addAttribute("newDeckName", defaultDeckNameForSelectedPdf(pdfOptions, fileId));
        if (hxRequest != null) return "fragments/flashcard-generator :: generator";
        model.addAttribute("username", user.getUsername());
        model.addAttribute("sidebarTree", folderService.getSidebarTree(user));
        return "flashcard-generator-page";
    }

    @PostMapping("/flashcards/generate")
    public String generate(@RequestParam(required = false) Long fileId,
                           @RequestParam(defaultValue = "TEXT") DocumentMode documentMode,
                           @RequestParam(required = false) FlashcardGenerationDestination destination,
                           @RequestParam(required = false) Long existingDeckId,
                           @RequestParam(required = false) Long newDeckFolderId,
                           @RequestParam(required = false) String newDeckName,
                           Model model,
                           Principal principal,
                           HttpServletResponse response,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) throws Exception {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        try {
            if (fileId == null) throw new IllegalArgumentException("Please select one PDF.");
            if (destination == null) throw new IllegalArgumentException("Please choose where to save the generated flashcards.");
            persistenceService.validateDestination(destination, existingDeckId, newDeckFolderId, newDeckName, user);

            FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
            if (!isPdf(file) || !documentExtractionService.isSupported(file)) {
                throw new IllegalArgumentException("Please select a supported PDF under 10 MB.");
            }

            DocumentInput input = buildDocumentInput(file, documentMode);
            requireQuotaService().checkAndRecord(user);
            List<GeneratedFlashcard> generated = aiFlashcardService.generate(input);
            Deck savedDeck = persistenceService.saveGeneratedCards(
                destination,
                existingDeckId,
                newDeckFolderId,
                newDeckName,
                user,
                generated
            );

            Deck deck = deckService.getDeck(savedDeck.getId(), user);
            model.addAttribute("deck", deck);
            model.addAttribute("flashcards", deck.getFlashcards());
            model.addAttribute("username", principal.getName());
            model.addAttribute("refreshSidebar", true);
            model.addAttribute("sidebarTree", folderService.getSidebarTree(user, deck.getFolder().getId()));
            model.addAttribute("successMessage", successMessage(generated.size(), file.getOriginalFilename()));
            if (hxRequest != null) {
                response.setHeader("HX-Push-Url", "/decks/" + deck.getId());
                return "fragments/deck :: deckDetail";
            }
            return "redirect:/decks/" + deck.getId();
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareGeneratorModel(model, user);
            model.addAttribute("generationError", ex.getMessage());
            model.addAttribute("generationDetails", generationDetails("FLASHCARDS", ex));
            model.addAttribute("selectedFileId", fileId);
            model.addAttribute("selectedDocumentMode", documentMode);
            model.addAttribute("selectedDestination", destination);
            model.addAttribute("selectedExistingDeckId", existingDeckId);
            model.addAttribute("selectedNewDeckFolderId", newDeckFolderId);
            model.addAttribute("newDeckName", newDeckName);
            if (hxRequest != null) return "fragments/flashcard-generator :: generator";
            model.addAttribute("username", user.getUsername());
            model.addAttribute("sidebarTree", folderService.getSidebarTree(user));
            return "flashcard-generator-page";
        }
    }

    private String generationDetails(String type, Exception ex) {
        if (ex instanceof AiGenerationException aiEx && aiEx.diagnostics() != null) {
            return aiEx.diagnostics().toDisplayString();
        }
        return AiGenerationDiagnostics.fromException(type, "REQUEST_VALIDATION", ex).toDisplayString();
    }

    private DocumentInput buildDocumentInput(FileEntry file, DocumentMode mode) throws Exception {
        return switch (mode) {
            case TEXT -> {
                String text = documentExtractionService.extractText(file);
                if (text == null || text.isBlank()) {
                    throw new IllegalArgumentException("This PDF has no extractable text. Try Full PDF mode.");
                }
                yield new TextDocument(file.getOriginalFilename(), text);
            }
            case FULL_PDF -> new PdfDocument(file.getOriginalFilename(), documentExtractionService.loadResource(file));
        };
    }

    private List<FlashcardPdfOption> prepareGeneratorModel(Model model, User user) {
        List<FlashcardPdfOption> pdfOptions = viewService.getPdfOptions(user);
        model.addAttribute("pdfOptions", pdfOptions);
        model.addAttribute("deckTree", folderService.getStudyFolderTree(user));
        model.addAttribute("folderTree", folderService.getFolderPickerTree(user));
        model.addAttribute("destinations", FlashcardGenerationDestination.values());
        model.addAttribute("documentModes", DocumentMode.values());
        return pdfOptions;
    }

    private String defaultDeckNameForSelectedPdf(List<FlashcardPdfOption> pdfOptions, Long fileId) {
        if (fileId == null || pdfOptions == null) return "";
        return pdfOptions.stream()
            .filter(pdf -> fileId.equals(pdf.id()))
            .map(FlashcardPdfOption::filename)
            .findFirst()
            .map(this::deckNameFromPdfFilename)
            .orElse("");
    }

    private String deckNameFromPdfFilename(String filename) {
        if (filename == null) return "";
        return filename.replaceFirst("(?i)\\.pdf$", "");
    }

    private boolean isPdf(FileEntry file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    private String successMessage(int count, String filename) {
        return "Generated " + count + " flashcard" + (count == 1 ? "" : "s") + " from " + filename + ".";
    }

    private AiRequestQuotaService requireQuotaService() {
        if (aiRequestQuotaService == null) {
            throw new IllegalStateException("AI quota service is not configured.");
        }
        return aiRequestQuotaService;
    }
}
