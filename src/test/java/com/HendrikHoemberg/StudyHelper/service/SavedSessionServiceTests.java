package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.SavedSession;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.SavedSessionRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class SavedSessionServiceTests {

    private SavedSessionRepository repository;
    private FlashcardRepository flashcardRepository;
    private ObjectMapper objectMapper;
    private StudyLogService studyLogService;
    private SavedSessionService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(SavedSessionRepository.class);
        flashcardRepository = mock(FlashcardRepository.class);
        objectMapper = new ObjectMapper();
        studyLogService = mock(StudyLogService.class);
        service = new SavedSessionService(repository, flashcardRepository, objectMapper, studyLogService);

        user = new User();
        user.setId(7L);
        user.setUsername("alice");
    }

    @Test
    void saveFlashcards_createsRowWithJsonPayload() {
        StudySessionState state = sampleFlashcardState();
        when(repository.findByUser(user)).thenReturn(Optional.empty());
        when(repository.save(any(SavedSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveFlashcards(user, state);

        ArgumentCaptor<SavedSession> captor = ArgumentCaptor.forClass(SavedSession.class);
        verify(repository).save(captor.capture());
        SavedSession saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getType()).isEqualTo(SavedSessionType.FLASHCARDS);
        assertThat(saved.getPayload()).contains("\"currentIndex\":0");
        assertThat(saved.getProgressLabel()).isEqualTo("0 / 2 answered");
    }

    @Test
    void saveQuizOverExistingFlashcards_overwritesType() {
        SavedSession existing = new SavedSession();
        existing.setUser(user);
        existing.setType(SavedSessionType.FLASHCARDS);
        existing.setPayload("{}");
        existing.setTitle("old");
        existing.setProgressLabel("old");
        when(repository.findByUser(user)).thenReturn(Optional.of(existing));
        when(repository.save(any(SavedSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveQuiz(user, sampleQuizState());

        ArgumentCaptor<SavedSession> captor = ArgumentCaptor.forClass(SavedSession.class);
        verify(repository).save(captor.capture());
        SavedSession saved = captor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getType()).isEqualTo(SavedSessionType.QUIZ);
        assertThat(saved.getProgressLabel()).isEqualTo("0 / 1 answered");
    }

    @Test
    void loadFlashcards_roundTripsJson() {
        StudySessionState state = sampleFlashcardState();
        SavedSession row = new SavedSession();
        row.setUser(user);
        row.setType(SavedSessionType.FLASHCARDS);
        row.setPayload(asJson(state));
        row.setTitle("t");
        row.setProgressLabel("p");
        when(repository.findByUser(user)).thenReturn(Optional.of(row));

        StudySessionState loaded = service.loadFlashcards(user).orElseThrow();

        assertThat(loaded.queue()).hasSize(2);
        assertThat(loaded.config().selectedDeckIds()).containsExactly(10L);
    }

    @Test
    void discard_isIdempotent() {
        service.discard(user);
        service.discard(user);
        verify(repository, times(2)).deleteByUser(user);
    }

    @Test
    void discard_partialSession_logsProgress() {
        StudySessionState state = sampleFlashcardState();
        StudySessionState answeredState = new StudySessionState(
            state.config(),
            state.cardsByDeck(),
            state.queue(),
            1, 1, 1, 0,
            List.of()
        );
        SavedSession row = new SavedSession();
        row.setUser(user);
        row.setType(SavedSessionType.FLASHCARDS);
        row.setPayload(asJson(answeredState));
        row.setTitle("t");
        row.setProgressLabel("p");
        row.setUpdatedAt(java.time.Instant.now());

        when(repository.findByUser(user)).thenReturn(Optional.of(row));

        service.discard(user);

        verify(studyLogService).recordFlashcardsPartial(eq(user), any(StudySessionState.class), any(java.time.LocalDateTime.class));
        verify(repository).deleteByUser(user);
    }

    @Test
    void findForUser_buildsSummaryWithResumeUrl() {
        SavedSession row = new SavedSession();
        row.setUser(user);
        row.setType(SavedSessionType.QUIZ);
        row.setTitle("Quiz - Biology");
        row.setProgressLabel("3 / 10 answered");
        when(repository.findByUser(user)).thenReturn(Optional.of(row));

        SavedSessionSummary summary = service.findForUser(user).orElseThrow();
        assertThat(summary.type()).isEqualTo(SavedSessionType.QUIZ);
        assertThat(summary.resumeUrl()).isEqualTo("/quiz/resume");
        assertThat(summary.title()).isEqualTo("Quiz - Biology");
        assertThat(summary.progressLabel()).isEqualTo("3 / 10 answered");
    }

    @Test
    void reconcileFlashcards_dropsMissingCardsAndClampsIndex() {
        StudyCardView c1 = new StudyCardView(101L, "f1", "b1", 10L, "Deck A", "Root / A", "#fff", null, null, null);
        StudyCardView c2 = new StudyCardView(102L, "f2", "b2", 10L, "Deck A", "Root / A", "#fff", null, null, null);
        StudyCardView c3 = new StudyCardView(103L, "f3", "b3", 10L, "Deck A", "Root / A", "#fff", null, null, null);
        StudySessionConfig config = new StudySessionConfig(List.of(10L), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER, false, 20);
        StudySessionState state = new StudySessionState(
            config,
            Map.of(10L, List.of(c1, c2, c3)),
            List.of(c1, c2, c3),
            2, 0, 0, 0, List.of()
        );
        when(flashcardRepository.findExistingIdsByIdIn(anyCollection()))
            .thenReturn(List.of(102L, 103L));

        SavedSessionService.ReconcileResult result = service.reconcileFlashcards(state, user);

        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(result.state().queue()).extracting(StudyCardView::cardId).containsExactly(102L, 103L);
        assertThat(result.state().cardsByDeck().get(10L)).extracting(StudyCardView::cardId).containsExactly(102L, 103L);
        assertThat(result.state().currentIndex()).isEqualTo(1);
    }

    @Test
    void reconcileFlashcards_dropsEmptyDecks() {
        StudyCardView c1 = new StudyCardView(101L, "f1", "b1", 10L, "Deck A", "Root / A", "#fff", null, null, null);
        StudyCardView c2 = new StudyCardView(102L, "f2", "b2", 20L, "Deck B", "Root / B", "#fff", null, null, null);
        StudySessionConfig config = new StudySessionConfig(List.of(10L, 20L), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER, false, 20);
        Map<Long, List<StudyCardView>> by = new LinkedHashMap<>();
        by.put(10L, List.of(c1));
        by.put(20L, List.of(c2));
        StudySessionState state = new StudySessionState(
            config, by, List.of(c1, c2), 0, 0, 0, 0, List.of()
        );
        when(flashcardRepository.findExistingIdsByIdIn(anyCollection())).thenReturn(List.of(102L));

        SavedSessionService.ReconcileResult result = service.reconcileFlashcards(state, user);

        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(result.state().cardsByDeck()).containsOnlyKeys(20L);
        assertThat(result.state().queue()).extracting(StudyCardView::cardId).containsExactly(102L);
    }

    private String asJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private StudySessionState sampleFlashcardState() {
        StudyCardView c1 = new StudyCardView(101L, "f1", "b1", 10L, "Deck A", "Root / A", "#fff", null, null, null);
        StudyCardView c2 = new StudyCardView(102L, "f2", "b2", 10L, "Deck A", "Root / A", "#fff", null, null, null);
        StudySessionConfig config = new StudySessionConfig(List.of(10L), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER, false, 20);
        return new StudySessionState(
            config,
            Map.of(10L, List.of(c1, c2)),
            List.of(c1, c2),
            0, 0, 0, 0,
            List.of()
        );
    }

    private QuizSessionState sampleQuizState() {
        QuizConfig config = new QuizConfig(List.of(10L), List.of(), 1, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM);
        QuizQuestion q = new QuizQuestion(QuestionType.MULTIPLE_CHOICE, "Q?", List.of("a", "b"), List.of(0));
        return new QuizSessionState(config, List.of(q), 0, Map.of());
    }
}
