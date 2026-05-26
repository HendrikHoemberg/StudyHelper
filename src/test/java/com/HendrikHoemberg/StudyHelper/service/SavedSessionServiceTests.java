package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.SavedSession;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.SavedSessionRepository;
import com.HendrikHoemberg.StudyHelper.dto.DungeonConfig;
import com.HendrikHoemberg.StudyHelper.dto.DungeonEncounter;
import com.HendrikHoemberg.StudyHelper.dto.DungeonMap;
import com.HendrikHoemberg.StudyHelper.dto.DungeonMode;
import com.HendrikHoemberg.StudyHelper.dto.DungeonPosition;
import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.DungeonSize;
import com.HendrikHoemberg.StudyHelper.dto.DungeonTile;
import com.HendrikHoemberg.StudyHelper.dto.DungeonTileType;
import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode;
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

    @Test
    void saveDungeon_createsRowWithResumeUrlAndProgress() {
        DungeonSessionState state = sampleDungeonState();
        when(repository.findByUser(user)).thenReturn(Optional.empty());
        when(repository.save(any(SavedSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveDungeon(user, state);

        ArgumentCaptor<SavedSession> captor = ArgumentCaptor.forClass(SavedSession.class);
        verify(repository).save(captor.capture());
        SavedSession saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getType()).isEqualTo(SavedSessionType.DUNGEON);
        assertThat(saved.getTitle()).isEqualTo("Dungeon · Flashcards · Small");
        assertThat(saved.getProgressLabel()).isEqualTo("0 / 8 cleared");
    }

    @Test
    void loadDungeon_roundTripsPayload() {
        DungeonSessionState state = sampleDungeonState();
        SavedSession row = new SavedSession();
        row.setUser(user);
        row.setType(SavedSessionType.DUNGEON);
        row.setPayload(asJson(state));
        row.setTitle("t");
        row.setProgressLabel("p");
        when(repository.findByUser(user)).thenReturn(Optional.of(row));

        DungeonSessionState loaded = service.loadDungeon(user).orElseThrow();

        assertThat(loaded.config().mode()).isEqualTo(DungeonMode.FLASHCARDS);
        assertThat(loaded.config().size()).isEqualTo(DungeonSize.SMALL);
    }

    @Test
    void findForUser_dungeonUsesDungeonResumeUrl() {
        SavedSession row = new SavedSession();
        row.setUser(user);
        row.setType(SavedSessionType.DUNGEON);
        row.setTitle("Dungeon · Flashcards · Small");
        row.setProgressLabel("3 / 8 cleared");
        when(repository.findByUser(user)).thenReturn(Optional.of(row));

        SavedSessionSummary summary = service.findForUser(user).orElseThrow();

        assertThat(summary.type()).isEqualTo(SavedSessionType.DUNGEON);
        assertThat(summary.resumeUrl()).isEqualTo("/dungeon/resume");
    }

    @Test
    void reconcileDungeonFlashcards_dropsMissingNormalEncounterAndKeepsRunPlayable() {
        DungeonSessionState state = sampleDungeonStateWithFlashcards();
        when(flashcardRepository.findExistingIdsByIdIn(anyCollection()))
            .thenReturn(List.of(102L, 201L, 202L));

        SavedSessionService.ReconcileDungeonResult result = service.reconcileDungeonFlashcards(state, user);

        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(result.canContinue()).isTrue();
        assertThat(result.state().encounters()).doesNotContainKey("enc-0");
        assertThat(result.state().encounters()).containsKey("enc-1");
        assertThat(result.state().encounters()).containsKey("boss-0");
        assertThat(result.state().encounters()).containsKey("boss-1");
    }

    @Test
    void reconcileDungeonFlashcards_blocksRunWhenBossPromptIsMissing() {
        DungeonSessionState state = sampleDungeonStateWithFlashcards();
        when(flashcardRepository.findExistingIdsByIdIn(anyCollection()))
            .thenReturn(List.of(101L, 102L, 202L));

        SavedSessionService.ReconcileDungeonResult result = service.reconcileDungeonFlashcards(state, user);

        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(result.canContinue()).isFalse();
    }

    @Test
    void reconcileDungeonFlashcards_blocksRunWhenNoNormalPromptRemainsBeforeBoss() {
        DungeonSessionState state = sampleDungeonStateWithFlashcards();
        when(flashcardRepository.findExistingIdsByIdIn(anyCollection()))
            .thenReturn(List.of(201L, 202L));

        SavedSessionService.ReconcileDungeonResult result = service.reconcileDungeonFlashcards(state, user);

        assertThat(result.removedCount()).isEqualTo(2);
        assertThat(result.canContinue()).isFalse();
    }

    @Test
    void reconcileDungeonFlashcards_allowsRunWhenNormalPromptsAreAlreadyClearedAndBossRemains() {
        DungeonSessionState state = sampleDungeonStateWithFlashcards();
        Map<String, DungeonEncounter> clearedEncounters = new LinkedHashMap<>();
        for (var entry : state.encounters().entrySet()) {
            DungeonEncounter enc = entry.getValue();
            if (!enc.boss() && enc.status() == DungeonEncounterStatus.PENDING) {
                clearedEncounters.put(entry.getKey(),
                    new DungeonEncounter(enc.id(), enc.type(), DungeonEncounterStatus.CLEARED, enc.boss(),
                        enc.flashcardId(), enc.frontText(), enc.backText(), enc.frontImageUrl(), enc.backImageUrl(),
                        enc.quizQuestion(), enc.selectedOptions(), enc.correct()));
            } else {
                clearedEncounters.put(entry.getKey(), enc);
            }
        }
        DungeonSessionState clearedState = new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(), clearedEncounters,
            state.bossEncounterIds(), state.bossIndex(), state.activeEncounterId(),
            state.health(), state.score(), state.answeredCount(), state.correctCount(),
            state.visibleTiles(), state.won(), state.defeated()
        );

        when(flashcardRepository.findExistingIdsByIdIn(anyCollection()))
            .thenReturn(List.of(101L, 102L, 201L, 202L));

        SavedSessionService.ReconcileDungeonResult result = service.reconcileDungeonFlashcards(clearedState, user);

        assertThat(result.removedCount()).isEqualTo(0);
        assertThat(result.canContinue()).isTrue();
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

    private DungeonSessionState sampleDungeonState() {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(10L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""
        );
        DungeonPosition entrance = new DungeonPosition(0, 0);
        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>();
        tiles.put(entrance, new DungeonTile(entrance, DungeonTileType.ENTRANCE, true, true, null));
        DungeonMap map = new DungeonMap(5, 5, entrance, new DungeonPosition(4, 4), tiles);
        return new DungeonSessionState(
            config, map, entrance, new LinkedHashMap<>(), List.of(), 0, null, 10, 0, 0, 0, Set.of(), false, false
        );
    }

    private DungeonSessionState sampleDungeonStateWithFlashcards() {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(10L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""
        );
        DungeonPosition entrance = new DungeonPosition(0, 0);
        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>();
        encounters.put("enc-0", DungeonEncounter.flashcard("enc-0", false, 101L, "Q1", "A1", null, null));
        encounters.put("enc-1", DungeonEncounter.flashcard("enc-1", false, 102L, "Q2", "A2", null, null));
        encounters.put("boss-0", DungeonEncounter.flashcard("boss-0", true, 201L, "BQ1", "BA1", null, null));
        encounters.put("boss-1", DungeonEncounter.flashcard("boss-1", true, 202L, "BQ2", "BA2", null, null));
        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>();
        tiles.put(new DungeonPosition(0, 0), new DungeonTile(new DungeonPosition(0, 0), DungeonTileType.ENTRANCE, true, true, null));
        tiles.put(new DungeonPosition(1, 0), new DungeonTile(new DungeonPosition(1, 0), DungeonTileType.ENCOUNTER, true, true, "enc-0"));
        tiles.put(new DungeonPosition(2, 0), new DungeonTile(new DungeonPosition(2, 0), DungeonTileType.ENCOUNTER, true, true, "enc-1"));
        tiles.put(new DungeonPosition(3, 0), new DungeonTile(new DungeonPosition(3, 0), DungeonTileType.BOSS, true, true, "boss-0"));
        tiles.put(new DungeonPosition(4, 0), new DungeonTile(new DungeonPosition(4, 0), DungeonTileType.BOSS, true, true, "boss-1"));
        DungeonMap map = new DungeonMap(5, 5, entrance, new DungeonPosition(4, 4), tiles);
        return new DungeonSessionState(
            config, map, new DungeonPosition(0, 0), encounters,
            List.of("boss-0", "boss-1"), 0, null, 10, 0, 0, 0, Set.of(), false, false
        );
    }
}
