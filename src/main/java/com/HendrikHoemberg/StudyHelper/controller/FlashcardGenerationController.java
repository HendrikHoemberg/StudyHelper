package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationPlan;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardPdfOption;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJob;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJobStatus;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiFlashcardService;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationDiagnostics;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationException;
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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final FlashcardGenerationPlanService planService;
    private final FlashcardGenerationJobService jobService;

    public FlashcardGenerationController(AiFlashcardService aiFlashcardService,
                                         FlashcardGenerationPersistenceService persistenceService,
                                         FlashcardGenerationViewService viewService,
                                         UserService userService,
                                         FileEntryService fileEntryService,
                                         DocumentExtractionService documentExtractionService,
                                         DeckService deckService,
                                         FolderService folderService,
                                         AiRequestQuotaService aiRequestQuotaService,
                                         FlashcardGenerationPlanService planService,
                                         FlashcardGenerationJobService jobService) {
        this.aiFlashcardService = aiFlashcardService;
        this.persistenceService = persistenceService;
        this.viewService = viewService;
        this.userService = userService;
        this.fileEntryService = fileEntryService;
        this.documentExtractionService = documentExtractionService;
        this.deckService = deckService;
        this.folderService = folderService;
        this.aiRequestQuotaService = aiRequestQuotaService;
        this.planService = planService;
        this.jobService = jobService;
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

    @PostMapping("/flashcards/generate/estimate")
    public String estimateGenerate(@RequestParam(name = "fileId", required = false) List<Long> fileIds,
                                   @RequestParam(defaultValue = "TEXT") DocumentMode documentMode,
                                   Model model,
                                   Principal principal,
                                   HttpServletResponse response) throws Exception {
        User user = userService.getByUsername(principal.getName());
        try {
            List<FileEntry> files = validateSelectedPdfs(fileIds, user);
            FlashcardGenerationPlan plan = planService.plan(files, documentMode);
            model.addAttribute("generationPlan", plan);
            return "fragments/flashcard-generator :: estimate";
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("generationError", ex.getMessage());
            return "fragments/flashcard-generator :: estimate";
        }
    }

    @PostMapping("/flashcards/generate")
    public String generate(@RequestParam(name = "fileId", required = false) List<Long> fileIds,
                           @RequestParam(defaultValue = "TEXT") DocumentMode documentMode,
                           @RequestParam(required = false) String additionalInstructions,
                           @RequestParam(required = false) FlashcardGenerationDestination destination,
                           @RequestParam(required = false) Long existingDeckId,
                           @RequestParam(required = false) Long newDeckFolderId,
                           @RequestParam(required = false) String newDeckName,
                           @RequestParam(required = false, defaultValue = "false") boolean highRiskAcknowledged,
                           Model model,
                           Principal principal,
                           HttpServletResponse response,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) throws Exception {
        User user = userService.getByUsername(principal.getName());
        FlashcardGenerationPlan plan = null;
        try {
            List<FileEntry> files = validateAndResolveFiles(fileIds, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, user);
            List<Long> selectedIds = safeFileIds(fileIds);
            plan = planService.plan(files, documentMode);
            if (plan.highRisk() && !highRiskAcknowledged) {
                throw new IllegalArgumentException("Please confirm the high-risk generation warning before continuing.");
            }
            FlashcardGenerationJob job = jobService.acceptJob(user, selectedIds, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, additionalInstructions, plan);
            model.addAttribute("generationJob", job);
            model.addAttribute("generationPlan", plan);
            response.addHeader("HX-Trigger", "refresh-quota");
            return "fragments/flashcard-generator :: progress";
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareGeneratorModel(model, user);
            model.addAttribute("generationError", ex.getMessage());
            model.addAttribute("generationDetails", generationDetails("FLASHCARDS", ex));
            model.addAttribute("selectedFileIds", safeFileIds(fileIds));
            model.addAttribute("selectedFileId", firstFileId(fileIds));
            model.addAttribute("selectedDocumentMode", documentMode);
            model.addAttribute("additionalInstructions", additionalInstructions);
            model.addAttribute("selectedDestination", destination);
            model.addAttribute("selectedExistingDeckId", existingDeckId);
            model.addAttribute("selectedNewDeckFolderId", newDeckFolderId);
            model.addAttribute("newDeckName", newDeckName);
            if (plan != null) {
                model.addAttribute("generationPlan", plan);
            }
            if (hxRequest != null) return "fragments/flashcard-generator :: generator";
            model.addAttribute("username", user.getUsername());
            model.addAttribute("sidebarTree", folderService.getSidebarTree(user));
            return "flashcard-generator-page";
        }
    }

    @GetMapping("/flashcards/generate/jobs/{jobId}")
    public String generationStatus(@PathVariable Long jobId,
                                   Model model,
                                   Principal principal,
                                   HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        FlashcardGenerationJob job = jobService.getJob(jobId, user);
        model.addAttribute("generationJob", job);
        if (job.getStatus() == FlashcardGenerationJobStatus.SUCCEEDED && job.getSavedDeckId() != null) {
            response.setHeader("HX-Redirect", "/decks/" + job.getSavedDeckId());
        }
        if (job.getStatus() == FlashcardGenerationJobStatus.FAILED) {
            response.addHeader("HX-Trigger", "refresh-quota");
        }
        return "fragments/flashcard-generator :: progress";
    }

    @PostMapping("/flashcards/generate/jobs/{jobId}/cancel")
    public String cancelJob(@PathVariable Long jobId,
                            Model model,
                            Principal principal,
                            HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        jobService.cancelJob(jobId, user);
        FlashcardGenerationJob job = jobService.getJob(jobId, user);
        model.addAttribute("generationJob", job);
        response.addHeader("HX-Trigger", "refresh-quota");
        return "fragments/flashcard-generator :: progress";
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
            validateAndResolveFiles(fileIds, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, user);
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

    private List<FileEntry> validateAndResolveFiles(List<Long> fileIds,
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

        List<FileEntry> files = new ArrayList<>(selectedIds.size());
        for (Long selectedId : selectedIds) {
            FileEntry file = fileEntryService.getByIdAndUser(selectedId, user);
            if (!isPdf(file) || !documentExtractionService.isSupported(file)) {
                throw new IllegalArgumentException("Please select only supported PDFs under 10 MB.");
            }
            files.add(file);
        }
        return files;
    }

    private List<FileEntry> validateSelectedPdfs(List<Long> fileIds, User user) throws Exception {
        List<Long> selectedIds = safeFileIds(fileIds);
        if (selectedIds.isEmpty()) throw new IllegalArgumentException("Please select at least one PDF.");
        List<FileEntry> files = new ArrayList<>(selectedIds.size());
        for (Long selectedId : selectedIds) {
            FileEntry file = fileEntryService.getByIdAndUser(selectedId, user);
            if (!isPdf(file) || !documentExtractionService.isSupported(file)) {
                throw new IllegalArgumentException("Please select only supported PDFs under 10 MB.");
            }
            files.add(file);
        }
        return files;
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
}
