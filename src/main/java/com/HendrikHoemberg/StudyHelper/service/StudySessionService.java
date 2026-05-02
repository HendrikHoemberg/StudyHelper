package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode;
import com.HendrikHoemberg.StudyHelper.dto.SessionMode;
import com.HendrikHoemberg.StudyHelper.dto.StudyCardView;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionConfig;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionStats;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
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
    private final Random random;

    @Autowired
    public StudySessionService(DeckService deckService, FlashcardService flashcardService) {
        this(deckService, flashcardService, new Random());
    }

    StudySessionService(DeckService deckService, FlashcardService flashcardService, Random random) {
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.random = random;
    }

    @Transactional(readOnly = true)
    public StudySessionState buildSession(StudySessionConfig rawConfig, User user) {
        StudySessionConfig config = normalizeConfig(rawConfig);
        List<Deck> orderedDecks = deckService.getValidatedDecksInRequestedOrder(config.selectedDeckIds(), user);
        Map<Long, List<Flashcard>> groupedCards = flashcardService.getFlashcardsGroupedByDeck(orderedDecks);
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

    public StudySessionState recordAnswer(StudySessionState state, Long cardId, boolean isCorrect) {
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

        int nextCorrect = state.correctAnswers() + (isCorrect ? 1 : 0);
        int nextIncorrect = state.incorrectAnswers() + (isCorrect ? 0 : 1);

        List<Long> newIncorrectIds = isCorrect
            ? state.incorrectCardIds()
            : appendId(state.incorrectCardIds(), cardId);

        return new StudySessionState(
            state.config(),
            state.cardsByDeck(),
            state.queue(),
            state.currentIndex() + 1,
            state.totalAnswered() + 1,
            nextCorrect,
            nextIncorrect,
            newIncorrectIds
        );
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

        return new StudySessionConfig(normalizedDeckIds, mode, deckOrder);
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
                    buildFolderPath(deck.getFolder())
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
}
