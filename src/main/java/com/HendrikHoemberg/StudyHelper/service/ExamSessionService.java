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
        List<Long> deckIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Long> fileIds = StudySourceSupport.normalizeIds(selectedFileIds);
        validateSetup(questionSize, count, timerMinutes, layout);
        Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
        GenerationInput input = validateRequestWithSources(deckIds, fileIds, pdfMode, user);

        int qCount = StudySourceSupport.normalizeQuestionCount(count);
        aiRequestQuotaService.checkAndRecord(user);

        List<ExamQuestion> questions = aiExamService.generate(
            input.flashcards(),
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
            questions, new HashMap<>(), Instant.now(), 0L, sourceSummary
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
        List<Long> deckIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Long> fileIds = StudySourceSupport.normalizeIds(selectedFileIds);
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
    }

    private GenerationInput validateRequestWithSources(List<Long> deckIds,
                                                       List<Long> fileIds,
                                                       Map<Long, DocumentMode> pdfMode,
                                                       User user) throws Exception {
        if (!fileIds.isEmpty()) {
            throw new IllegalArgumentException("Exams can only be generated from card decks.");
        }
        if (deckIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one deck.");
        }

        List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(deckIds, user);
        List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
        List<String> sourceNames = new ArrayList<>();
        decks.forEach(d -> sourceNames.add(d.getName()));

        return new GenerationInput(flashcards, sourceNames);
    }

    private record GenerationInput(List<Flashcard> flashcards,
                                   List<String> sourceNames) {}
}
