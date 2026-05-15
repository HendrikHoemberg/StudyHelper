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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds quiz validation, AI generation, and session-state assembly so that
 * StudyController stays thin. Mirrors {@link ExamSessionService}.
 */
@Service
public class QuizSessionService {

    private static final long MAX_TOTAL_CHARS = 150_000;

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
        List<Long> deckIds = normalizeIds(selectedDeckIds);
        List<Long> fileIds = normalizeIds(selectedFileIds);
        Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
        QuizGenerationInput input = validateRequestWithSources(deckIds, fileIds, pdfMode, user);

        int qCount = normalizeQuestionCount(requestedQuestionCount);
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
        validateRequestWithSources(normalizeIds(selectedDeckIds), normalizeIds(selectedFileIds), pdfMode, user);
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
        }

        if (totalChars > MAX_TOTAL_CHARS) {
            throw new IllegalArgumentException("Selection too large — please deselect some sources.");
        }
        return new QuizGenerationInput(flashcards, documents);
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

    private int normalizeQuestionCount(int questionCount) {
        return Math.max(1, Math.min(questionCount, 20));
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private record QuizGenerationInput(List<Flashcard> flashcards, List<DocumentInput> documents) {}
}
