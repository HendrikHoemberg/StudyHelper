package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.QuizConfig;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode;
import com.HendrikHoemberg.StudyHelper.dto.QuizSessionState;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds quiz validation, AI generation, and session-state assembly so that
 * StudyController stays thin. Mirrors {@link ExamSessionService}.
 */
@Service
public class QuizSessionService {

    private static final Logger log = LoggerFactory.getLogger(QuizSessionService.class);

    /** Total extracted-text length above which the wizard shows a "large selection" warning. */
    private static final long SELECTION_WARN_CHARS = 50_000;

    private final AiQuizService aiQuizService;
    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final FileEntryService fileEntryService;
    private final DocumentExtractionService documentExtractionService;
    private final AiRequestQuotaService aiRequestQuotaService;

    public QuizSessionService(AiQuizService aiQuizService,
                              DeckService deckService,
                              FlashcardService flashcardService,
                              FileEntryService fileEntryService,
                              DocumentExtractionService documentExtractionService,
                              AiRequestQuotaService aiRequestQuotaService) {
        this.aiQuizService = aiQuizService;
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.fileEntryService = fileEntryService;
        this.documentExtractionService = documentExtractionService;
        this.aiRequestQuotaService = aiRequestQuotaService;
    }

    public QuizSessionState createSession(List<Long> selectedDeckIds,
                                          List<Long> selectedFileIds,
                                          HttpServletRequest request,
                                          int requestedQuestionCount,
                                          QuizQuestionMode mode,
                                          Difficulty difficulty,
                                          String additionalInstructions,
                                          User user) throws Exception {
        List<Long> deckIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Long> fileIds = StudySourceSupport.normalizeIds(selectedFileIds);
        Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
        QuizGenerationInput input = validateRequestWithSources(deckIds, fileIds, pdfMode, user);

        int qCount = StudySourceSupport.normalizeQuestionCount(requestedQuestionCount);
        aiRequestQuotaService.checkAndRecord(user);

        List<QuizQuestion> questions = aiQuizService.generate(
            input.flashcards(),
            input.documents(),
            qCount,
            mode,
            difficulty,
            additionalInstructions
        );

        return new QuizSessionState(
            new QuizConfig(deckIds, fileIds, qCount, mode, difficulty),
            questions,
            0,
            new HashMap<>()
        );
    }

    public void validateForPreflight(List<Long> selectedDeckIds,
                                     List<Long> selectedFileIds,
                                     HttpServletRequest request,
                                     QuizQuestionMode mode,
                                     Difficulty difficulty,
                                     User user) throws Exception {
        validateSetup(mode, difficulty);
        Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
        validateRequestWithSources(
            StudySourceSupport.normalizeIds(selectedDeckIds),
            StudySourceSupport.normalizeIds(selectedFileIds),
            pdfMode, user);
    }

    /** Selection-size estimate for the study wizard's "large selection" warning. */
    public record SelectionSize(long totalChars, boolean warn, boolean exceedsCap) {}

    /**
     * Best-effort estimate of the combined extracted-text size of the selected
     * documents. Files that cannot be read are skipped — this drives a wizard
     * warning, not validation. {@code charCache} is a caller-owned cache (e.g.
     * session-scoped) keyed by file id; an entry is reused only while the file's
     * byte size is unchanged, so a replaced file is recounted.
     */
    public SelectionSize estimateSelectionSize(List<Long> fileIds,
                                               Map<Long, DocumentMode> pdfMode,
                                               User user,
                                               Map<Long, long[]> charCache) {
        long totalChars = 0;
        for (Long fileId : StudySourceSupport.normalizeIds(fileIds)) {
            totalChars += charCountForFile(fileId, pdfMode, user, charCache);
        }
        return new SelectionSize(
            totalChars,
            totalChars >= SELECTION_WARN_CHARS,
            totalChars > StudySourceSupport.MAX_SELECTION_CHARS
        );
    }

    private int charCountForFile(Long fileId,
                                 Map<Long, DocumentMode> pdfMode,
                                 User user,
                                 Map<Long, long[]> charCache) {
        try {
            FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
            if (DocumentModeResolver.resolve(file, pdfMode) == DocumentMode.FULL_PDF) {
                return 0;
            }
            long sizeBytes = file.getFileSizeBytes() == null ? -1 : file.getFileSizeBytes();
            long[] cached = charCache.get(fileId);
            if (cached != null && cached[0] == sizeBytes) {
                return (int) cached[1];
            }
            if (StudySourceSupport.isPdf(file) && !documentExtractionService.isSupported(file)) {
                throw new IllegalArgumentException("Please select a supported PDF under 10 MB.");
            }
            int chars = StudySourceSupport.requireExtractedText(documentExtractionService, file).length();
            charCache.put(fileId, new long[]{sizeBytes, chars});
            return chars;
        } catch (Exception e) {
            log.debug("Skipping char-count for fileId={}: {}", fileId, e.getMessage());
            return 0;
        }
    }

    private void validateSetup(QuizQuestionMode mode, Difficulty difficulty) {
        if (mode == null) {
            throw new IllegalArgumentException("Please select a quiz mode.");
        }
        if (difficulty == null) {
            throw new IllegalArgumentException("Please select a difficulty.");
        }
    }

    private QuizGenerationInput validateRequestWithSources(List<Long> deckIds,
                                                           List<Long> fileIds,
                                                           Map<Long, DocumentMode> pdfMode,
                                                           User user) throws Exception {
        if (deckIds.isEmpty() && fileIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one deck or document.");
        }

        List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(deckIds, user);
        List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
        List<DocumentInput> documents = new ArrayList<>();

        long totalChars = 0;
        for (Long fileId : fileIds) {
            FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
            if (StudySourceSupport.isPdf(file) && !documentExtractionService.isSupported(file)) {
                throw new IllegalArgumentException("Please select a supported PDF under 10 MB.");
            }
            DocumentMode docMode = DocumentModeResolver.resolve(file, pdfMode);
            switch (docMode) {
                case TEXT -> {
                    String text = StudySourceSupport.requireExtractedText(documentExtractionService, file);
                    documents.add(new TextDocument(file.getOriginalFilename(), text));
                    totalChars += text.length();
                }
                case FULL_PDF -> documents.add(new PdfDocument(file.getOriginalFilename(),
                    documentExtractionService.loadResource(file)));
            }
        }

        if (totalChars > StudySourceSupport.MAX_SELECTION_CHARS) {
            throw new IllegalArgumentException("Selection too large — please deselect some sources.");
        }
        return new QuizGenerationInput(flashcards, documents);
    }

    private record QuizGenerationInput(List<Flashcard> flashcards, List<DocumentInput> documents) {}
}
