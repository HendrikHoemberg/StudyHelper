package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode;
import com.HendrikHoemberg.StudyHelper.dto.Grade;
import com.HendrikHoemberg.StudyHelper.dto.SessionMode;
import com.HendrikHoemberg.StudyHelper.dto.StudyCardView;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionConfig;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionStats;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.ReviewLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.ReviewLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

@Service
public class StudySessionService {

    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final FlashcardRepository flashcardRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final SrsScheduler srsScheduler;
    private final Random random;

    @Autowired
    public StudySessionService(DeckService deckService,
                               FlashcardService flashcardService,
                               FlashcardRepository flashcardRepository,
                               ReviewLogRepository reviewLogRepository,
                               SrsScheduler srsScheduler) {
        this(deckService, flashcardService, flashcardRepository, reviewLogRepository, srsScheduler, new Random());
    }

    StudySessionService(DeckService deckService,
                        FlashcardService flashcardService,
                        FlashcardRepository flashcardRepository,
                        ReviewLogRepository reviewLogRepository,
                        SrsScheduler srsScheduler,
                        Random random) {
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.flashcardRepository = flashcardRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.srsScheduler = srsScheduler;
        this.random = random;
    }

    @Transactional(readOnly = true)
    public StudySessionState buildSession(StudySessionConfig rawConfig, User user) {
        StudySessionConfig config = normalizeConfig(rawConfig);
        List<Deck> orderedDecks = deckService.getValidatedDecksInRequestedOrder(config.selectedDeckIds(), user);
        Map<Long, List<Flashcard>> groupedCards = flashcardService.getFlashcardsGroupedByDeck(orderedDecks);
        if (!config.practice()) {
            java.time.LocalDate today = java.time.LocalDate.now();
            long introducedToday = reviewLogRepository.countNewIntroducedBetween(
                user, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            long remainingNew = Math.max(0, config.newCardsPerDay() - introducedToday);
            Map<Long, List<Flashcard>> filtered = new LinkedHashMap<>();
            for (Deck deck : orderedDecks) {
                List<Flashcard> deckCards = groupedCards.getOrDefault(deck.getId(), List.of());
                List<Flashcard> picked = selectScheduledCards(deckCards, today, (int) remainingNew, 0);
                long newlyTaken = picked.stream().filter(c -> c.getDueDate() == null).count();
                remainingNew = Math.max(0, remainingNew - newlyTaken);
                filtered.put(deck.getId(), picked);
            }
            groupedCards = filtered;
        }
        Map<Long, List<StudyCardView>> cardsByDeck = toCardsByDeck(orderedDecks, groupedCards);

        ensureCardPoolNotEmpty(cardsByDeck);

        List<StudyCardView> queue = buildQueue(config, cardsByDeck);
        if (queue.isEmpty()) {
            throw new IllegalArgumentException("No cards available for the selected decks.");
        }

        return new StudySessionState(
            config,
            cardsByDeck,
            queue,
            0,
            0,
            0,
            0,
            List.of()
        );
    }

    public StudyCardView nextCard(StudySessionState state) {
        if (state == null || isComplete(state)) {
            throw new NoSuchElementException("Session is complete.");
        }
        return state.queue().get(state.currentIndex());
    }

    @Transactional
    public StudySessionState recordAnswer(StudySessionState state, Long cardId, Grade grade) {
        if (state == null) {
            throw new IllegalArgumentException("No active study session.");
        }
        if (isComplete(state)) {
            throw new IllegalStateException("Study session is already complete.");
        }

        StudyCardView current = nextCard(state);
        if (!current.cardId().equals(cardId)) {
            throw new IllegalArgumentException("Answer does not match the current card.");
        }

        boolean isCorrect = grade != Grade.AGAIN;
        boolean practice = state.config().practice();

        if (!practice) {
            applySchedule(cardId, grade);
        }

        int nextCorrect = state.correctAnswers() + (isCorrect ? 1 : 0);
        int nextIncorrect = state.incorrectAnswers() + (isCorrect ? 0 : 1);

        List<StudyCardView> queue = state.queue();
        List<Long> newIncorrectIds = isCorrect
            ? state.incorrectCardIds()
            : appendId(state.incorrectCardIds(), cardId);

        if (grade == Grade.AGAIN) {
            List<StudyCardView> requeued = new ArrayList<>(queue);
            requeued.add(current);
            queue = List.copyOf(requeued);
        }

        return new StudySessionState(
            state.config(),
            state.cardsByDeck(),
            queue,
            state.currentIndex() + 1,
            state.totalAnswered() + 1,
            nextCorrect,
            nextIncorrect,
            newIncorrectIds
        );
    }

    private void applySchedule(Long cardId, Grade grade) {
        flashcardRepository.findById(cardId).ifPresent(fc -> {
            boolean wasNew = fc.getDueDate() == null;
            int interval = fc.getIntervalDays() == null ? 0 : fc.getIntervalDays();
            double ef = fc.getEaseFactor() == null ? SrsScheduler.INITIAL_EF : fc.getEaseFactor();
            int reps = fc.getRepetitions() == null ? 0 : fc.getRepetitions();

            SrsScheduler.SrsState result = srsScheduler.next(
                interval, ef, reps, grade, java.time.LocalDate.now());

            fc.setIntervalDays(result.intervalDays());
            fc.setEaseFactor(result.easeFactor());
            fc.setRepetitions(result.repetitions());
            fc.setDueDate(result.dueDate());
            fc.setLastReviewedAt(java.time.LocalDateTime.now());
            if (grade == Grade.AGAIN) {
                fc.setCorrectStreak(0);
            } else {
                Integer cur = fc.getCorrectStreak();
                fc.setCorrectStreak((cur == null ? 0 : cur) + 1);
            }
            flashcardRepository.save(fc);

            ReviewLog log = new ReviewLog();
            log.setFlashcard(fc);
            log.setUser(fc.getDeck().getUser());
            log.setReviewedAt(java.time.LocalDateTime.now());
            log.setGrade(grade);
            log.setIntervalDaysAfter(result.intervalDays());
            log.setEaseFactorAfter(result.easeFactor());
            log.setWasNew(wasNew);
            reviewLogRepository.save(log);
        });
    }

    public boolean isComplete(StudySessionState state) {
        return state == null || state.currentIndex() >= state.queue().size();
    }

    public StudySessionStats buildStats(StudySessionState state) {
        if (state == null) {
            return new StudySessionStats(0, 0, 0, 0, 0);
        }

        int answered = state.totalAnswered();
        int correct = state.correctAnswers();
        int incorrect = state.incorrectAnswers();
        int percentage = answered == 0 ? 0 : (int) Math.round((correct * 100.0) / answered);

        return new StudySessionStats(
            answered,
            state.queue().size(),
            correct,
            incorrect,
            percentage
        );
    }

    public StudySessionState redo(StudySessionState state) {
        if (state == null) {
            throw new IllegalArgumentException("No active study session.");
        }
        List<StudyCardView> queue = buildQueue(state.config(), state.cardsByDeck());
        if (queue.isEmpty()) {
            throw new IllegalArgumentException("No cards available for redo.");
        }

        return new StudySessionState(
            state.config(),
            state.cardsByDeck(),
            queue,
            0,
            0,
            0,
            0,
            List.of()
        );
    }

    public StudySessionState redoIncorrect(StudySessionState state) {
        if (state == null) {
            throw new IllegalArgumentException("No active study session.");
        }
        List<Long> incorrectIds = state.incorrectCardIds();
        if (incorrectIds.isEmpty()) {
            throw new IllegalArgumentException("No incorrect cards to redo.");
        }

        Set<Long> incorrectSet = new HashSet<>(incorrectIds);
        List<StudyCardView> queue = state.queue().stream()
            .filter(card -> incorrectSet.contains(card.cardId()))
            .toList();

        return new StudySessionState(
            state.config(), state.cardsByDeck(), List.copyOf(queue),
            0, 0, 0, 0,
            List.of()
        );
    }

    private List<Long> appendId(List<Long> list, Long id) {
        List<Long> next = new ArrayList<>(list);
        next.add(id);
        return List.copyOf(next);
    }

    private StudySessionConfig normalizeConfig(StudySessionConfig rawConfig) {
        if (rawConfig == null) {
            throw new IllegalArgumentException("Study session config is required.");
        }

        List<Long> normalizedDeckIds = normalizeDeckIds(rawConfig.selectedDeckIds());
        if (normalizedDeckIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one deck.");
        }

        SessionMode mode = rawConfig.sessionMode() == null ? SessionMode.DECK_BY_DECK : rawConfig.sessionMode();
        DeckOrderMode deckOrder = rawConfig.deckOrderMode() == null
            ? DeckOrderMode.SELECTED_ORDER
            : rawConfig.deckOrderMode();

        int newCardsPerDay = rawConfig.newCardsPerDay() <= 0 ? 20 : rawConfig.newCardsPerDay();
        return new StudySessionConfig(normalizedDeckIds, mode, deckOrder, rawConfig.practice(), newCardsPerDay);
    }

    private List<Long> normalizeDeckIds(List<Long> deckIds) {
        if (deckIds == null || deckIds.isEmpty()) {
            return List.of();
        }

        List<Long> normalized = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long deckId : deckIds) {
            if (deckId != null && seen.add(deckId)) {
                normalized.add(deckId);
            }
        }

        return List.copyOf(normalized);
    }

    private Map<Long, List<StudyCardView>> toCardsByDeck(List<Deck> orderedDecks,
                                                          Map<Long, List<Flashcard>> groupedCards) {
        Map<Long, List<StudyCardView>> byDeck = new LinkedHashMap<>();

        for (Deck deck : orderedDecks) {
            List<Flashcard> cards = groupedCards.getOrDefault(deck.getId(), List.of());
            List<StudyCardView> views = cards.stream()
                .map(card -> new StudyCardView(
                    card.getId(),
                    card.getFrontText(),
                    card.getBackText(),
                    deck.getId(),
                    deck.getName(),
                    buildFolderPath(deck.getFolder()),
                    deck.getColorHex(),
                    deck.getIconName(),
                    card.getFrontImageFilename() != null ? "/flashcards/" + card.getId() + "/images/front" : null,
                    card.getBackImageFilename() != null ? "/flashcards/" + card.getId() + "/images/back" : null
                ))
                .toList();
            byDeck.put(deck.getId(), List.copyOf(views));
        }

        return Collections.unmodifiableMap(byDeck);
    }

    private List<StudyCardView> buildQueue(StudySessionConfig config,
                                           Map<Long, List<StudyCardView>> cardsByDeck) {
        if (config.sessionMode() == SessionMode.SHUFFLED) {
            List<StudyCardView> queue = new ArrayList<>();
            for (List<StudyCardView> bucket : cardsByDeck.values()) {
                queue.addAll(bucket);
            }
            shuffle(queue);
            return List.copyOf(queue);
        }

        List<Long> deckOrder = new ArrayList<>(config.selectedDeckIds());
        if (config.deckOrderMode() == DeckOrderMode.RANDOMIZED_ORDER) {
            shuffle(deckOrder);
        }

        List<StudyCardView> queue = new ArrayList<>();
        for (Long deckId : deckOrder) {
            List<StudyCardView> bucket = new ArrayList<>(cardsByDeck.getOrDefault(deckId, List.of()));
            shuffle(bucket);
            queue.addAll(bucket);
        }

        return List.copyOf(queue);
    }

    private void ensureCardPoolNotEmpty(Map<Long, List<StudyCardView>> cardsByDeck) {
        int total = cardsByDeck.values().stream().mapToInt(List::size).sum();
        if (total == 0) {
            throw new IllegalArgumentException("Selected decks do not contain any cards.");
        }
    }

    private <T> void shuffle(List<T> values) {
        if (values.size() > 1) {
            Collections.shuffle(values, random);
        }
    }

    private String buildFolderPath(Folder folder) {
        List<String> segments = new ArrayList<>();
        Folder current = folder;
        while (current != null) {
            segments.add(0, current.getName());
            current = current.getParentFolder();
        }
        return String.join(" / ", segments);
    }

    public static List<Flashcard> selectScheduledCards(List<Flashcard> cards,
                                                java.time.LocalDate today,
                                                int newCardsPerDay,
                                                long newAlreadyIntroducedToday) {
        List<Flashcard> dueReviews = new ArrayList<>();
        List<Flashcard> newCards = new ArrayList<>();
        for (Flashcard c : cards) {
            if (c.getDueDate() == null) {
                newCards.add(c);
            } else if (!c.getDueDate().isAfter(today)) {
                dueReviews.add(c);
            }
        }
        long remainingNew = Math.max(0, newCardsPerDay - newAlreadyIntroducedToday);
        List<Flashcard> selected = new ArrayList<>(dueReviews);
        selected.addAll(newCards.stream().limit(remainingNew).toList());
        return selected;
    }
}
