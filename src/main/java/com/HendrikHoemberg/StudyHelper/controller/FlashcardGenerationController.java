package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardPdfOption;
import com.HendrikHoemberg.StudyHelper.dto.PdfFolderNode;
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
import java.util.ArrayList;
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

    @GetMapping("/flashcards/generate")
    public String showGenerator(@RequestParam(name = "fileId", required = false) List<Long> fileIds,
                                Model model,
                                Principal principal,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        List<FlashcardPdfOption> pdfOptions = prepareGeneratorModel(model, user);
        List<Long> selectedFileIds = safeFileIds(fileIds);
        model.addAttribute("selectedFileIds", selectedFileIds);
        model.addAttribute("selectedFileId", firstFileId(fileIds));
        model.addAttribute("newDeckName", defaultDeckNameForSelectedPdfs(pdfOptions, selectedFileIds));
        if (hxRequest != null) return "fragments/flashcard-generator :: generator";
        model.addAttribute("username", user.getUsername());
        model.addAttribute("sidebarTree", folderService.getSidebarTree(user));
        return "flashcard-generator-page";
    }

    @PostMapping("/flashcards/generate")
    public String generate(@RequestParam(name = "fileId", required = false) List<Long> fileIds,
                           @RequestParam(defaultValue = "TEXT") DocumentMode documentMode,
                           @RequestParam(required = false) String additionalInstructions,
                           @RequestParam(required = false, defaultValue = "20") int cardCount,
                           @RequestParam(required = false) FlashcardGenerationDestination destination,
                           @RequestParam(required = false) Long existingDeckId,
                           @RequestParam(required = false) Long newDeckFolderId,
                           @RequestParam(required = false) String newDeckName,
                           Model model,
                           Principal principal,
                           HttpServletResponse response,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) throws Exception {
        User user = userService.getByUsername(principal.getName());
        try {
            List<DocumentInput> inputs = validateAndBuildInputs(fileIds, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, user);
            aiRequestQuotaService.checkAndRecord(user);
            response.addHeader("HX-Trigger", "refresh-quota");
            List<GeneratedFlashcard> generated = aiFlashcardService.generate(inputs, cardCount, additionalInstructions);
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
            model.addAttribute("successMessage", successMessage(generated.size(), inputs));
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
            model.addAttribute("selectedFileIds", safeFileIds(fileIds));
            model.addAttribute("selectedFileId", firstFileId(fileIds));
            model.addAttribute("selectedDocumentMode", documentMode);
            model.addAttribute("additionalInstructions", additionalInstructions);
            model.addAttribute("cardCount", cardCount);
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

    @PostMapping("/flashcards/generate/preflight")
    public String preflightGenerate(@RequestParam(name = "fileId", required = false) List<Long> fileIds,
                                    @RequestParam(defaultValue = "TEXT") DocumentMode documentMode,
                                    @RequestParam(required = false) FlashcardGenerationDestination destination,
                                    @RequestParam(required = false) Long existingDeckId,
                                    @RequestParam(required = false) Long newDeckFolderId,
                                    @RequestParam(required = false) String newDeckName,
                                    Model model,
                                    Principal principal,
                                    HttpServletResponse response,
                                    @RequestHeader(value = "HX-Request", required = false) String hxRequest) throws Exception {
        User user = userService.getByUsername(principal.getName());
        try {
            validateAndBuildInputs(fileIds, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, user);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareGeneratorModel(model, user);
            model.addAttribute("generationError", ex.getMessage());
            model.addAttribute("generationDetails", generationDetails("FLASHCARDS", ex));
            model.addAttribute("selectedFileIds", safeFileIds(fileIds));
            model.addAttribute("selectedFileId", firstFileId(fileIds));
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

    private List<DocumentInput> validateAndBuildInputs(List<Long> fileIds,
                                                       DocumentMode documentMode,
                                                       FlashcardGenerationDestination destination,
                                                       Long existingDeckId,
                                                       Long newDeckFolderId,
                                                       String newDeckName,
                                                       User user) throws Exception {
        List<Long> selectedIds = safeFileIds(fileIds);
        if (selectedIds.isEmpty()) throw new IllegalArgumentException("Please select at least one PDF.");
        if (destination == null) throw new IllegalArgumentException("Please choose where to save the generated flashcards.");
        persistenceService.validateDestination(destination, existingDeckId, newDeckFolderId, newDeckName, user);

        List<DocumentInput> inputs = new ArrayList<>(selectedIds.size());
        for (Long selectedId : selectedIds) {
            FileEntry file = fileEntryService.getByIdAndUser(selectedId, user);
            if (!isPdf(file) || !documentExtractionService.isSupported(file)) {
                throw new IllegalArgumentException("Please select only supported PDFs under 10 MB.");
            }
            inputs.add(buildDocumentInput(file, documentMode));
        }
        return inputs;
    }

    private List<Long> safeFileIds(List<Long> fileIds) {
        if (fileIds == null) return List.of();
        return fileIds.stream()
            .filter(id -> id != null)
            .distinct()
            .toList();
    }

    private Long firstFileId(List<Long> fileIds) {
        return safeFileIds(fileIds).stream().findFirst().orElse(null);
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
                    throw new IllegalArgumentException(file.getOriginalFilename() + " has no extractable text. Try Full PDF mode.");
                }
                yield new TextDocument(file.getOriginalFilename(), text);
            }
            case FULL_PDF -> new PdfDocument(file.getOriginalFilename(), documentExtractionService.loadResource(file));
        };
    }

    private List<FlashcardPdfOption> prepareGeneratorModel(Model model, User user) {
        List<FlashcardPdfOption> pdfOptions = viewService.getPdfOptions(user);
        model.addAttribute("pdfOptions", pdfOptions);
        model.addAttribute("pdfFolderTree", viewService.getPdfFolderTree(user));
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

    private String defaultDeckNameForSelectedPdfs(List<FlashcardPdfOption> pdfOptions, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return "";
        if (fileIds.size() > 1) return "Generated Flashcards";
        return defaultDeckNameForSelectedPdf(pdfOptions, fileIds.get(0));
    }

    private String deckNameFromPdfFilename(String filename) {
        if (filename == null) return "";
        return filename.replaceFirst("(?i)\\.pdf$", "");
    }

    private boolean isPdf(FileEntry file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    private String successMessage(int count, List<DocumentInput> inputs) {
        String cardLabel = " flashcard" + (count == 1 ? "" : "s");
        if (inputs.size() == 1) {
            return "Generated " + count + cardLabel + " from " + inputs.get(0).filename() + ".";
        }
        return "Generated " + count + cardLabel + " from " + inputs.size() + " PDFs.";
    }

}
