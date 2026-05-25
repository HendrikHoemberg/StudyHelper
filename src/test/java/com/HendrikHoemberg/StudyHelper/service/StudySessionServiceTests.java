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
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.ReviewLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudySessionServiceTests {

    private DeckService deckService;
    private FlashcardService flashcardService;
    private FlashcardRepository flashcardRepository;
    private ReviewLogRepository reviewLogRepository;
    private SrsScheduler srsScheduler;
    private StudySessionService studySessionService;
    private User user;

    private static final SrsScheduler.SrsState DUMMY_SRS_STATE = new SrsScheduler.SrsState(1, 2.5, 1, java.time.LocalDate.now().plusDays(1));

    @BeforeEach
    void setUp() {
        deckService = mock(DeckService.class);
        flashcardService = mock(FlashcardService.class);
        flashcardRepository = mock(FlashcardRepository.class);
        reviewLogRepository = mock(ReviewLogRepository.class);
        srsScheduler = mock(SrsScheduler.class);
        when(srsScheduler.next(anyInt(), anyDouble(), anyInt(), any(), any())).thenReturn(DUMMY_SRS_STATE);
        studySessionService = new StudySessionService(deckService, flashcardService, flashcardRepository, reviewLogRepository, srsScheduler, new Random(42));

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
    }

    @Test
    void buildSession_DeckByDeck_UsesManualOrder() {
        Deck deckA = deck(10L, "Deck A", "Root / A");
        Deck deckB = deck(20L, "Deck B", "Root / B");

        Map<Long, List<Flashcard>> grouped = new LinkedHashMap<>();
        grouped.put(20L, List.of(card(201L, deckB), card(202L, deckB)));
        grouped.put(10L, List.of(card(101L, deckA)));

        StudySessionConfig config = new StudySessionConfig(
            List.of(20L, 10L),
            SessionMode.DECK_BY_DECK,
            DeckOrderMode.SELECTED_ORDER,
            false,
            20
        );

        when(deckService.getValidatedDecksInRequestedOrder(config.selectedDeckIds(), user))
            .thenReturn(List.of(deckB, deckA));
        when(flashcardService.getFlashcardsGroupedByDeck(List.of(deckB, deckA)))
            .thenReturn(grouped);

        StudySessionState state = studySessionService.buildSession(config, user);

        assertThat(state.queue()).hasSize(3);
        assertThat(state.queue().subList(0, 2)).allMatch(card -> card.deckId().equals(20L));
        assertThat(state.queue().get(2).deckId()).isEqualTo(10L);
    }

    @Test
    void buildSession_DeckByDeck_RandomizedDeckOrderBranch() {
        Deck deck1 = deck(1L, "Deck 1", "Root / One");
        Deck deck2 = deck(2L, "Deck 2", "Root / Two");
        Deck deck3 = deck(3L, "Deck 3", "Root / Three");

        Map<Long, List<Flashcard>> grouped = new LinkedHashMap<>();
        grouped.put(1L, List.of(card(11L, deck1)));
        grouped.put(2L, List.of(card(22L, deck2)));
        grouped.put(3L, List.of(card(33L, deck3)));

        StudySessionConfig config = new StudySessionConfig(
            List.of(1L, 2L, 3L),
            SessionMode.DECK_BY_DECK,
            DeckOrderMode.RANDOMIZED_ORDER,
            false,
            20
        );

        when(deckService.getValidatedDecksInRequestedOrder(config.selectedDeckIds(), user))
            .thenReturn(List.of(deck1, deck2, deck3));
        when(flashcardService.getFlashcardsGroupedByDeck(List.of(deck1, deck2, deck3)))
            .thenReturn(grouped);

        StudySessionState state = studySessionService.buildSession(config, user);

        assertThat(state.queue()).hasSize(3);
        assertThat(state.queue().stream().map(card -> card.deckId()).collect(Collectors.toSet()))
            .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void buildSession_ShuffledMode_BuildsCombinedQueue() {
        Deck deck1 = deck(1L, "Deck 1", "Root / One");
        Deck deck2 = deck(2L, "Deck 2", "Root / Two");

        Map<Long, List<Flashcard>> grouped = new LinkedHashMap<>();
        grouped.put(1L, List.of(card(11L, deck1), card(12L, deck1)));
        grouped.put(2L, List.of(card(21L, deck2), card(22L, deck2)));

        StudySessionConfig config = new StudySessionConfig(
            List.of(1L, 2L),
            SessionMode.SHUFFLED,
            DeckOrderMode.SELECTED_ORDER,
            false,
            20
        );

        when(deckService.getValidatedDecksInRequestedOrder(config.selectedDeckIds(), user))
            .thenReturn(List.of(deck1, deck2));
        when(flashcardService.getFlashcardsGroupedByDeck(List.of(deck1, deck2)))
            .thenReturn(grouped);

        StudySessionState state = studySessionService.buildSession(config, user);

        assertThat(state.queue()).hasSize(4);
        assertThat(state.queue().stream().map(card -> card.cardId()).collect(Collectors.toSet()))
            .containsExactlyInAnyOrder(11L, 12L, 21L, 22L);
    }

    @Test
    void buildStats_ComputesTotalsAndPercentage() {
        StudySessionState state = new StudySessionState(
            new StudySessionConfig(List.of(1L), SessionMode.SHUFFLED, DeckOrderMode.SELECTED_ORDER, false, 20),
            Map.of(),
            List.of(),
            2,
            2,
            1,
            1,
            List.of()
        );

        StudySessionStats stats = studySessionService.buildStats(state);

        assertThat(stats.totalAnswered()).isEqualTo(2);
        assertThat(stats.correctAnswers()).isEqualTo(1);
        assertThat(stats.incorrectAnswers()).isEqualTo(1);
        assertThat(stats.percentage()).isEqualTo(50);
    }

    @Test
    void redo_UsesSameConfigAndReshufflesQueue() {
        Deck deck1 = deck(1L, "Deck 1", "Root / One");
        Deck deck2 = deck(2L, "Deck 2", "Root / Two");

        Map<Long, List<Flashcard>> grouped = new LinkedHashMap<>();
        grouped.put(1L, List.of(card(11L, deck1), card(12L, deck1), card(13L, deck1), card(14L, deck1)));
        grouped.put(2L, List.of(card(21L, deck2), card(22L, deck2), card(23L, deck2), card(24L, deck2)));

        StudySessionConfig config = new StudySessionConfig(
            List.of(1L, 2L),
            SessionMode.SHUFFLED,
            DeckOrderMode.SELECTED_ORDER,
            false,
            20
        );

        when(deckService.getValidatedDecksInRequestedOrder(config.selectedDeckIds(), user))
            .thenReturn(List.of(deck1, deck2));
        when(flashcardService.getFlashcardsGroupedByDeck(List.of(deck1, deck2)))
            .thenReturn(grouped);

        StudySessionState first = studySessionService.buildSession(config, user);
        StudySessionState redo = studySessionService.redo(first);

        assertThat(redo.config()).isEqualTo(first.config());
        assertThat(redo.currentIndex()).isZero();
        assertThat(redo.totalAnswered()).isZero();
        assertThat(redo.correctAnswers()).isZero();
        assertThat(redo.incorrectAnswers()).isZero();
        assertThat(redo.queue().stream().map(card -> card.cardId()).collect(Collectors.toSet()))
            .isEqualTo(first.queue().stream().map(card -> card.cardId()).collect(Collectors.toSet()));
        assertThat(redo.queue()).isNotEqualTo(first.queue());
    }

    @Test
    void recordAnswer_incorrect_persistsStreakZeroImmediately() {
        Deck deck = deck(1L, "Test Deck", "Root");
        Flashcard card = card(100L, deck);
        StudyCardView view = new StudyCardView(
            card.getId(), "Front", "Back",
            deck.getId(), deck.getName(), "Root",
            "#000", "layers", null, null
        );

        StudySessionState state = new StudySessionState(
            new StudySessionConfig(List.of(1L), SessionMode.SHUFFLED, DeckOrderMode.SELECTED_ORDER, false, 20),
            Map.of(1L, List.of(view)),
            List.of(view),
            0, 0, 0, 0, List.of()
        );

        when(flashcardRepository.findById(100L)).thenReturn(Optional.of(card));

        studySessionService.recordAnswer(state, 100L, Grade.AGAIN);

        assertThat(card.getCorrectStreak()).isZero();
        verify(flashcardRepository).save(card);
    }

    @Test
    void recordAnswer_correct_persistsIncrementedStreak() {
        Deck deck = deck(1L, "Test Deck", "Root");
        Flashcard card = card(100L, deck);
        StudyCardView view = new StudyCardView(
            card.getId(), "Front", "Back",
            deck.getId(), deck.getName(), "Root",
            "#000", "layers", null, null
        );

        StudySessionState state = new StudySessionState(
            new StudySessionConfig(List.of(1L), SessionMode.SHUFFLED, DeckOrderMode.SELECTED_ORDER, false, 20),
            Map.of(1L, List.of(view)),
            List.of(view),
            0, 0, 0, 0, List.of()
        );

        when(flashcardRepository.findById(100L)).thenReturn(Optional.of(card));

        studySessionService.recordAnswer(state, 100L, Grade.GOOD);

        assertThat(card.getCorrectStreak()).isEqualTo(1);
        verify(flashcardRepository).save(card);
    }

    @Test
    void recordAnswer_correct_incrementsExistingStreak() {
        Deck deck = deck(1L, "Test Deck", "Root");
        Flashcard card = card(100L, deck);
        card.setCorrectStreak(3);
        StudyCardView view = new StudyCardView(
            card.getId(), "Front", "Back",
            deck.getId(), deck.getName(), "Root",
            "#000", "layers", null, null
        );

        StudySessionState state = new StudySessionState(
            new StudySessionConfig(List.of(1L), SessionMode.SHUFFLED, DeckOrderMode.SELECTED_ORDER, false, 20),
            Map.of(1L, List.of(view)),
            List.of(view),
            0, 0, 0, 0, List.of()
        );

        when(flashcardRepository.findById(100L)).thenReturn(Optional.of(card));

        studySessionService.recordAnswer(state, 100L, Grade.GOOD);

        assertThat(card.getCorrectStreak()).isEqualTo(4);
        verify(flashcardRepository).save(card);
    }

    @Test
    void recordAnswer_tracksDetailedGrades() {
        Deck deck = deck(1L, "Test Deck", "Root");
        Flashcard card = card(100L, deck);
        StudyCardView view = new StudyCardView(
            card.getId(), "Front", "Back",
            deck.getId(), deck.getName(), "Root",
            "#000", "layers", null, null
        );

        StudySessionState state = new StudySessionState(
            new StudySessionConfig(List.of(1L), SessionMode.SHUFFLED, DeckOrderMode.SELECTED_ORDER, false, 20),
            Map.of(1L, List.of(view)),
            List.of(view),
            0, 0, 0, 0, List.of()
        );

        when(flashcardRepository.findById(100L)).thenReturn(Optional.of(card));

        // Test AGAIN
        StudySessionState stateAgain = studySessionService.recordAnswer(state, 100L, Grade.AGAIN);
        assertThat(stateAgain.againCount()).isEqualTo(1);
        assertThat(stateAgain.hardCount()).isZero();

        // Test HARD
        StudySessionState stateHard = studySessionService.recordAnswer(state, 100L, Grade.HARD);
        assertThat(stateHard.hardCount()).isEqualTo(1);
        assertThat(stateHard.againCount()).isZero();

        // Test GOOD
        StudySessionState stateGood = studySessionService.recordAnswer(state, 100L, Grade.GOOD);
        assertThat(stateGood.goodCount()).isEqualTo(1);

        // Test EASY
        StudySessionState stateEasy = studySessionService.recordAnswer(state, 100L, Grade.EASY);
        assertThat(stateEasy.easyCount()).isEqualTo(1);
        
        // Test stats mapping
        StudySessionStats stats = studySessionService.buildStats(stateEasy);
        assertThat(stats.easyCount()).isEqualTo(1);
        assertThat(stats.easyPercent()).isEqualTo(100);
    }

    private Deck deck(Long id, String name, String pathTail) {
        String[] parts = pathTail.split(" / ");
        Folder current = null;
        for (String part : parts) {
            Folder folder = new Folder();
            folder.setName(part);
            folder.setParentFolder(current);
            current = folder;
        }

        Deck deck = new Deck();
        deck.setId(id);
        deck.setName(name);
        deck.setFolder(current);
        deck.setUser(user);
        return deck;
    }

    private Flashcard card(Long id, Deck deck) {
        Flashcard flashcard = new Flashcard();
        flashcard.setId(id);
        flashcard.setFrontText("Front " + id);
        flashcard.setBackText("Back " + id);
        flashcard.setDeck(deck);
        return flashcard;
    }
}
