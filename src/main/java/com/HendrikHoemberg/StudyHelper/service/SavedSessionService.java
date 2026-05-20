package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.SavedSession;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.SavedSessionRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class SavedSessionService {

    private final SavedSessionRepository repository;
    private final FlashcardRepository flashcardRepository;
    private final ObjectMapper objectMapper;

    public SavedSessionService(SavedSessionRepository repository,
                               FlashcardRepository flashcardRepository,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.flashcardRepository = flashcardRepository;
        this.objectMapper = objectMapper.rebuild()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    }

    @Transactional(readOnly = true)
    public Optional<SavedSessionSummary> findForUser(User user) {
        return repository.findByUser(user).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Optional<StudySessionState> loadFlashcards(User user) {
        return loadTyped(user, SavedSessionType.FLASHCARDS, StudySessionState.class);
    }

    @Transactional(readOnly = true)
    public Optional<QuizSessionState> loadQuiz(User user) {
        return loadTyped(user, SavedSessionType.QUIZ, QuizSessionState.class);
    }

    @Transactional(readOnly = true)
    public Optional<ExamSessionState> loadExam(User user) {
        return loadTyped(user, SavedSessionType.EXAM, ExamSessionState.class);
    }

    public void saveFlashcards(User user, StudySessionState state) {
        upsert(user, SavedSessionType.FLASHCARDS, state,
            flashcardTitle(state), flashcardProgress(state));
    }

    public void saveQuiz(User user, QuizSessionState state) {
        upsert(user, SavedSessionType.QUIZ, state,
            quizTitle(state), quizProgress(state));
    }

    public void saveExam(User user, ExamSessionState state) {
        upsert(user, SavedSessionType.EXAM, state,
            examTitle(state), examProgress(state));
    }

    public void discard(User user) {
        repository.deleteByUser(user);
    }

    private <T> Optional<T> loadTyped(User user, SavedSessionType expected, Class<T> clazz) {
        return repository.findByUser(user)
            .filter(s -> s.getType() == expected)
            .map(s -> read(s.getPayload(), clazz));
    }

    private <T> T read(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize saved session", e);
        }
    }

    private void upsert(User user, SavedSessionType type, Object payload, String title, String progressLabel) {
        SavedSession row = repository.findByUser(user).orElseGet(SavedSession::new);
        row.setUser(user);
        row.setType(type);
        row.setPayload(write(payload));
        row.setTitle(truncate(title, 255));
        row.setProgressLabel(truncate(progressLabel, 64));
        repository.save(row);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize saved session", e);
        }
    }

    private SavedSessionSummary toSummary(SavedSession row) {
        return new SavedSessionSummary(
            row.getType(),
            row.getTitle(),
            row.getProgressLabel(),
            row.getUpdatedAt(),
            resumeUrlFor(row.getType())
        );
    }

    private String resumeUrlFor(SavedSessionType type) {
        return switch (type) {
            case FLASHCARDS -> "/study/resume";
            case QUIZ -> "/quiz/resume";
            case EXAM -> "/exam/resume";
        };
    }

    // ---- formatters (package-private for testing) ----

    String flashcardTitle(StudySessionState state) {
        List<String> deckNames = new ArrayList<>();
        for (List<StudyCardView> cards : state.cardsByDeck().values()) {
            if (!cards.isEmpty()) deckNames.add(cards.get(0).deckName());
        }
        return "Flashcards · " + String.join(", ", deckNames);
    }

    String flashcardProgress(StudySessionState state) {
        return state.totalAnswered() + " / " + state.queue().size() + " answered";
    }

    String quizTitle(QuizSessionState state) {
        int decks = state.config().selectedDeckIds() == null ? 0 : state.config().selectedDeckIds().size();
        int files = state.config().selectedFileIds() == null ? 0 : state.config().selectedFileIds().size();
        return "Quiz · " + decks + " deck(s), " + files + " file(s)";
    }

    String quizProgress(QuizSessionState state) {
        return state.answers().size() + " / " + state.questions().size() + " answered";
    }

    String examTitle(ExamSessionState state) {
        return "Exam · " + state.sourceSummary();
    }

    String examProgress(ExamSessionState state) {
        return state.answers().size() + " / " + state.questions().size() + " answered";
    }

    public record ReconcileResult(StudySessionState state, int removedCount) {}

    @Transactional(readOnly = true)
    public ReconcileResult reconcileFlashcards(StudySessionState state, User user) {
        Set<Long> savedIds = new HashSet<>();
        for (StudyCardView card : state.queue()) savedIds.add(card.cardId());
        if (savedIds.isEmpty()) {
            return new ReconcileResult(state, 0);
        }

        Set<Long> alive = new HashSet<>(flashcardRepository.findExistingIdsByIdIn(savedIds));
        int before = state.queue().size();

        List<StudyCardView> newQueue = state.queue().stream()
            .filter(c -> alive.contains(c.cardId()))
            .toList();

        Map<Long, List<StudyCardView>> newByDeck = new LinkedHashMap<>();
        for (var entry : state.cardsByDeck().entrySet()) {
            List<StudyCardView> filtered = entry.getValue().stream()
                .filter(c -> alive.contains(c.cardId()))
                .toList();
            if (!filtered.isEmpty()) newByDeck.put(entry.getKey(), filtered);
        }

        List<Long> newIncorrect = state.incorrectCardIds().stream()
            .filter(alive::contains)
            .toList();

        int clampedIndex = Math.min(state.currentIndex(), Math.max(0, newQueue.size() - 1));
        if (newQueue.isEmpty()) clampedIndex = 0;

        StudySessionState newState = new StudySessionState(
            state.config(),
            newByDeck,
            newQueue,
            clampedIndex,
            state.totalAnswered(),
            state.correctAnswers(),
            state.incorrectAnswers(),
            newIncorrect
        );

        return new ReconcileResult(newState, before - newQueue.size());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
