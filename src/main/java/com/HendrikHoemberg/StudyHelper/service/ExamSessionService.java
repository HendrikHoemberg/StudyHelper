package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.ExamConfig;
import com.HendrikHoemberg.StudyHelper.dto.ExamLayout;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestion;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionSize;
import com.HendrikHoemberg.StudyHelper.dto.ExamSessionState;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Holds exam validation, AI generation, and session-state assembly so that
 * controllers can stay thin and so StudyController does not need to inject
 * ExamController to handle EXAM mode.
 */
@Service
public class ExamSessionService {

    private final AiExamService aiExamService;
    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final FileEntryService fileEntryService;
    private final DocumentExtractionService documentExtractionService;
    private final AiRequestQuotaService aiRequestQuotaService;

    @Autowired
    public ExamSessionService(AiExamService aiExamService,
                              DeckService deckService,
                              FlashcardService flashcardService,
                              FileEntryService fileEntryService,
                              DocumentExtractionService documentExtractionService,
                              AiRequestQuotaService aiRequestQuotaService) {
        this.aiExamService = aiExamService;
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.fileEntryService = fileEntryService;
        this.documentExtractionService = documentExtractionService;
        this.aiRequestQuotaService = aiRequestQuotaService;
    }

    public record ExamSessionResult(ExamSessionState state, ExamLayout layout) {}

    public ExamSessionResult createSession(List<Long> selectedDeckIds,
                                           List<Long> selectedFileIds,
                                           String additionalInstructions,
                                           HttpServletRequest request,
                                           ExamQuestionSize questionSize,
                                           int count,
                                           Integer timerMinutes,
                                           ExamLayout layout,
                                           User user) throws Exception {
        List<Long> deckIds = normalizeIds(selectedDeckIds);
        List<Long> fileIds = normalizeIds(selectedFileIds);
        validateSetup(questionSize, count, timerMinutes, layout);
        Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
        GenerationInput input = validateRequestWithSources(deckIds, fileIds, pdfMode, user);

        int qCount = normalizeQuestionCount(count);
        requireQuotaService().checkAndRecord(user);

        List<ExamQuestion> questions = aiExamService.generate(
            input.flashcards(),
            input.documents(),
            qCount,
            questionSize,
            additionalInstructions
        );

        String sourceSummary = input.sourceNames().stream().limit(3).collect(Collectors.joining(", "));
        if (input.sourceNames().size() > 3) {
            sourceSummary += " + " + (input.sourceNames().size() - 3) + " more";
        }

        ExamSessionState state = new ExamSessionState(
            new ExamConfig(deckIds, fileIds, questionSize, qCount, timerMinutes, layout),
            questions, new HashMap<>(), Instant.now(), sourceSummary
        );
        return new ExamSessionResult(state, layout);
    }

    public void validateForPreflight(List<Long> selectedDeckIds,
                                     List<Long> selectedFileIds,
                                     HttpServletRequest request,
                                     ExamQuestionSize questionSize,
                                     int count,
                                     Integer timerMinutes,
                                     ExamLayout layout,
                                     User user) throws Exception {
        List<Long> deckIds = normalizeIds(selectedDeckIds);
        List<Long> fileIds = normalizeIds(selectedFileIds);
        validateSetup(questionSize, count, timerMinutes, layout);
        Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
        validateRequestWithSources(deckIds, fileIds, pdfMode, user);
    }

    private void validateSetup(ExamQuestionSize questionSize,
                               int count,
                               Integer timerMinutes,
                               ExamLayout layout) {
        if (questionSize == null) {
            throw new IllegalArgumentException("Please select an exam question size.");
        }
        if (layout == null) {
            throw new IllegalArgumentException("Please select an exam layout.");
        }
        normalizeQuestionCount(count);
    }

    private GenerationInput validateRequestWithSources(List<Long> deckIds,
                                                       List<Long> fileIds,
                                                       Map<Long, DocumentMode> pdfMode,
                                                       User user) throws Exception {
        if (deckIds.isEmpty() && fileIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one source.");
        }

        List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(deckIds, user);
        List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
        List<DocumentInput> documents = new ArrayList<>();
        List<String> sourceNames = new ArrayList<>();
        decks.forEach(d -> sourceNames.add(d.getName()));

        long totalChars = 0;
        for (Long fileId : fileIds) {
            FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
            if (isPdf(file) && !documentExtractionService.isSupported(file)) {
                throw new IllegalArgumentException("Please select a supported PDF under 10 MB.");
            }
            DocumentMode docMode = DocumentModeResolver.resolve(file, pdfMode);
            switch (docMode) {
                case TEXT -> {
                    String text = requireExtractedText(file);
                    documents.add(new TextDocument(file.getOriginalFilename(), text));
                    totalChars += text.length();
                }
                case FULL_PDF -> documents.add(new PdfDocument(file.getOriginalFilename(),
                    documentExtractionService.loadResource(file)));
            }
            sourceNames.add(file.getOriginalFilename());
        }

        if (totalChars > 150_000) {
            throw new IllegalArgumentException("Selection too large — please deselect some sources.");
        }

        return new GenerationInput(flashcards, documents, sourceNames);
    }

    private String requireExtractedText(FileEntry file) throws Exception {
        String text = documentExtractionService.extractText(file);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("This PDF has no extractable text. Try Full PDF mode.");
        }
        return text;
    }

    private boolean isPdf(FileEntry file) {
        String filename = file == null ? null : file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    private int normalizeQuestionCount(int count) {
        return Math.max(1, Math.min(count, 20));
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private AiRequestQuotaService requireQuotaService() {
        if (aiRequestQuotaService == null) {
            throw new IllegalStateException("AI quota service is not configured.");
        }
        return aiRequestQuotaService;
    }

    private record GenerationInput(List<Flashcard> flashcards,
                                   List<DocumentInput> documents,
                                   List<String> sourceNames) {}
}
