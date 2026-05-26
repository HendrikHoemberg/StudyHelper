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
    private final StudyLogService studyLogService;

    public SavedSessionService(SavedSessionRepository repository,
                               FlashcardRepository flashcardRepository,
                               ObjectMapper objectMapper,
                               StudyLogService studyLogService) {
        this.repository = repository;
        this.flashcardRepository = flashcardRepository;
        this.studyLogService = studyLogService;
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
        repository.findByUser(user).ifPresent(row -> {
            try {
                java.time.LocalDateTime completedAt = java.time.LocalDateTime.ofInstant(row.getUpdatedAt(), java.time.ZoneId.systemDefault());
                if (row.getType() == SavedSessionType.FLASHCARDS) {
                    StudySessionState state = read(row.getPayload(), StudySessionState.class);
                    if (state != null && state.totalAnswered() > 0) {
                        studyLogService.recordFlashcardsPartial(user, state, completedAt);
                    }
                } else if (row.getType() == SavedSessionType.QUIZ) {
                    QuizSessionState state = read(row.getPayload(), QuizSessionState.class);
                    if (state != null && state.answers() != null && !state.answers().isEmpty()) {
                        studyLogService.recordQuizPartial(user, state, completedAt);
                    }
                }
            } catch (Exception e) {
                // Keep it safe so discarding never crashes
            }
        });
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
            case DUNGEON -> "/dungeon/resume";
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

    String dungeonTitle(DungeonSessionState state) {
        String mode = state.config().mode() == DungeonMode.FLASHCARDS ? "Flashcards" : "AI Quiz";
        String size = switch (state.config().size()) {
            case SMALL -> "Small";
            case MEDIUM -> "Medium";
            case LARGE -> "Large";
        };
        return "Dungeon · " + mode + " · " + size;
    }

    String dungeonProgress(DungeonSessionState state) {
        return state.answeredCount() + " / " + state.config().size().totalPrompts() + " cleared";
    }

    public record ReconcileResult(StudySessionState state, int removedCount) {}

    public record ReconcileDungeonResult(DungeonSessionState state, int removedCount, boolean canContinue) {}

    @Transactional(readOnly = true)
    public Optional<DungeonSessionState> loadDungeon(User user) {
        return loadTyped(user, SavedSessionType.DUNGEON, DungeonSessionState.class);
    }

    public void saveDungeon(User user, DungeonSessionState state) {
        upsert(user, SavedSessionType.DUNGEON, state,
            dungeonTitle(state), dungeonProgress(state));
    }

    @Transactional(readOnly = true)
    public ReconcileDungeonResult reconcileDungeonFlashcards(DungeonSessionState state, User user) {
        if (state == null || state.config().mode() != DungeonMode.FLASHCARDS) {
            return new ReconcileDungeonResult(state, 0, true);
        }

        Set<Long> flashcardIds = new HashSet<>();
        for (DungeonEncounter encounter : state.encounters().values()) {
            if (encounter.flashcardId() != null) {
                flashcardIds.add(encounter.flashcardId());
            }
        }

        if (flashcardIds.isEmpty()) {
            return new ReconcileDungeonResult(state, 0, true);
        }

        Set<Long> aliveIds = new HashSet<>(flashcardRepository.findExistingIdsByIdIn(flashcardIds));

        Set<String> removedEncounterIds = new HashSet<>();
        Map<String, DungeonEncounter> remainingEncounters = new LinkedHashMap<>();
        for (Map.Entry<String, DungeonEncounter> entry : state.encounters().entrySet()) {
            DungeonEncounter enc = entry.getValue();
            if (enc.flashcardId() != null && !aliveIds.contains(enc.flashcardId())) {
                removedEncounterIds.add(entry.getKey());
            } else {
                remainingEncounters.put(entry.getKey(), enc);
            }
        }

        Map<DungeonPosition, DungeonTile> updatedTiles = new LinkedHashMap<>();
        for (Map.Entry<DungeonPosition, DungeonTile> entry : state.map().tiles().entrySet()) {
            DungeonTile tile = entry.getValue();
            if (tile.encounterId() != null && removedEncounterIds.contains(tile.encounterId())) {
                updatedTiles.put(entry.getKey(), tile.withType(DungeonTileType.FLOOR, null));
            } else {
                updatedTiles.put(entry.getKey(), tile);
            }
        }

        List<String> remainingBossIds = state.bossEncounterIds().stream()
            .filter(id -> remainingEncounters.containsKey(id))
            .toList();

        boolean allBossAlive = state.bossEncounterIds().stream()
            .allMatch(id -> remainingEncounters.containsKey(id));

        boolean hasUnresolvedNormal = remainingEncounters.values().stream()
            .anyMatch(e -> !e.boss() && e.status() != DungeonEncounterStatus.CLEARED);

        boolean hasAnyNormal = remainingEncounters.values().stream().anyMatch(e -> !e.boss());
        boolean allNormalsCleared = hasAnyNormal && remainingEncounters.values().stream()
            .filter(e -> !e.boss())
            .allMatch(e -> e.status() == DungeonEncounterStatus.CLEARED);

        boolean canContinue = allBossAlive && (state.bossIndex() > 0 || hasUnresolvedNormal || allNormalsCleared);

        DungeonSessionState newState = new DungeonSessionState(
            state.config(),
            new DungeonMap(state.map().width(), state.map().height(), state.map().entrance(), state.map().boss(), updatedTiles),
            state.playerPosition(),
            remainingEncounters,
            remainingBossIds,
            state.bossIndex(),
            removedEncounterIds.contains(state.activeEncounterId()) ? null : state.activeEncounterId(),
            state.health(),
            state.score(),
            state.answeredCount(),
            state.correctCount(),
            state.visibleTiles(),
            state.won(),
            state.defeated()
        );

        return new ReconcileDungeonResult(newState, removedEncounterIds.size(), canContinue);
    }

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
            newIncorrect,
            state.againCount(),
            state.hardCount(),
            state.goodCount(),
            state.easyCount()
        );

        return new ReconcileResult(newState, before - newQueue.size());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
