package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DungeonSessionServiceTests {

    private DeckService deckService;
    private FlashcardService flashcardService;
    private QuizSessionService quizSessionService;
    private DungeonMapGenerator dungeonMapGenerator;
    private DungeonSessionService dungeonSessionService;
    private User user;

    @BeforeEach
    void setUp() {
        deckService = mock(DeckService.class);
        flashcardService = mock(FlashcardService.class);
        quizSessionService = mock(QuizSessionService.class);
        dungeonMapGenerator = mock(DungeonMapGenerator.class);
        DungeonNavigationService navigationService = new DungeonNavigationService();
        DungeonEncounterService encounterService = new DungeonEncounterService();
        dungeonSessionService = new DungeonSessionService(
            deckService, flashcardService, quizSessionService, dungeonMapGenerator,
            navigationService, encounterService);

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
    }

    // ---- helpers ----

    private List<Flashcard> mockFlashcards(int count) {
        Deck deck = new Deck();
        deck.setId(1L);
        deck.setName("Test Deck");

        List<Flashcard> flashcards = IntStream.range(0, count)
            .mapToObj(i -> {
                Flashcard c = new Flashcard();
                c.setId((long) (100 + i));
                c.setFrontText("Front " + i);
                c.setBackText("Back " + i);
                c.setDeck(deck);
                return c;
            })
            .toList();

        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user)))
            .thenReturn(List.of(deck));
        when(flashcardService.getFlashcardsFlattened(anyList()))
            .thenReturn(flashcards);

        return flashcards;
    }

    private DungeonMap simpleMap(DungeonSize size) {
        DungeonPosition entrance = new DungeonPosition(1, 1);
        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>();
        tiles.put(entrance, new DungeonTile(entrance, DungeonTileType.ENTRANCE, true, true, null));
        return new DungeonMap(5, 5, entrance, new DungeonPosition(3, 3), tiles);
    }

    private DungeonPosition p(int x, int y) {
        return new DungeonPosition(x, y);
    }

    private DungeonTile tile(DungeonPosition pos, DungeonTileType type, boolean revealed, boolean explored, String encounterId) {
        return new DungeonTile(pos, type, revealed, explored, encounterId);
    }

    private DungeonMap fiveByFiveWithWalls(DungeonPosition entrance, java.util.function.Consumer<Map<DungeonPosition, DungeonTile>> customizer) {
        int w = 5, h = 5;
        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                DungeonPosition pos = p(x, y);
                boolean isWall = x == 0 || x == w - 1 || y == 0 || y == h - 1;
                tiles.put(pos, new DungeonTile(pos, isWall ? DungeonTileType.WALL : DungeonTileType.FLOOR, false, false, null));
            }
        }
        tiles.put(entrance, new DungeonTile(entrance, DungeonTileType.ENTRANCE, true, true, null));
        customizer.accept(tiles);
        return new DungeonMap(w, h, entrance, p(3, 2), tiles);
    }

    // ---- flashcard dungeon creation tests ----

    @Test
    void validateSize_rejectsMediumWhenOnlyEightCardsSelected() {
        mockFlashcards(8);

        assertThatThrownBy(() ->
            dungeonSessionService.createFlashcardDungeon(List.of(1L), DungeonSize.MEDIUM, user))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createFlashcardDungeon_buildsPracticeOnlySessionWithoutQuizGeneration() throws Exception {
        mockFlashcards(29);
        when(dungeonMapGenerator.generate(eq(DungeonSize.LARGE), anyList()))
            .thenReturn(simpleMap(DungeonSize.LARGE));

        DungeonSessionState state = dungeonSessionService.createFlashcardDungeon(
            List.of(1L), DungeonSize.LARGE, user);

        assertThat(state.config().mode()).isEqualTo(DungeonMode.FLASHCARDS);
        assertThat(state.config().size()).isEqualTo(DungeonSize.LARGE);
        assertThat(state.health()).isEqualTo(5);
        assertThat(state.encounters()).hasSize(DungeonSize.LARGE.normalEncounterCount() + DungeonSize.LARGE.bossPromptCount());
        assertThat(state.bossEncounterIds()).hasSize(DungeonSize.LARGE.bossPromptCount());
        verify(quizSessionService, never()).createSession(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void createAiQuizDungeon_usesSizeAsQuestionCount() throws Exception {
        Deck deck = new Deck();
        deck.setId(1L);
        List<Flashcard> flashcards = IntStream.range(0, 20)
            .mapToObj(i -> {
                Flashcard c = new Flashcard();
                c.setId((long) (100 + i));
                c.setFrontText("F" + i);
                c.setBackText("B" + i);
                c.setDeck(deck);
                return c;
            })
            .toList();

        when(deckService.getValidatedDecksInRequestedOrder(anyList(), eq(user)))
            .thenReturn(List.of(deck));
        when(flashcardService.getFlashcardsFlattened(anyList()))
            .thenReturn(flashcards);

        List<QuizQuestion> questions = IntStream.range(0, DungeonSize.SMALL.totalPrompts())
            .mapToObj(i -> new QuizQuestion(QuestionType.MULTIPLE_CHOICE, "Q" + i,
                List.of("A", "B"), List.of(0)))
            .toList();

        QuizSessionState quizState = new QuizSessionState(null, questions, 0, new LinkedHashMap<>());

        when(quizSessionService.createSession(anyList(), anyList(), any(), anyInt(), any(), any(), any(), any()))
            .thenReturn(quizState);

        when(dungeonMapGenerator.generate(any(), anyList()))
            .thenReturn(simpleMap(DungeonSize.SMALL));

        HttpServletRequest request = mock(HttpServletRequest.class);

        dungeonSessionService.createAiQuizDungeon(
            List.of(1L), DungeonSize.SMALL, QuizQuestionMode.MCQ_ONLY,
            Difficulty.EASY, "", request, user);

        verify(quizSessionService).createSession(
            eq(List.of(1L)), eq(List.of()), eq(request),
            eq(DungeonSize.SMALL.totalPrompts()),
            eq(QuizQuestionMode.MCQ_ONLY), eq(Difficulty.EASY), eq(""), eq(user));
    }

    // ---- move tests ----

    @Test
    void move_revealsFogAndDoesNotWalkThroughWalls() {
        DungeonPosition entrance = p(2, 2);
        DungeonMap map = fiveByFiveWithWalls(entrance, tiles -> {
            tiles.put(p(1, 2), tile(p(1, 2), DungeonTileType.WALL, false, false, null));
        });

        Set<DungeonPosition> initialVisible = Set.of(entrance, p(1, 2), p(3, 2), p(2, 1), p(2, 3));

        DungeonSessionState state = new DungeonSessionState(
            config(DungeonMode.FLASHCARDS),
            map,
            entrance,
            Map.of(),
            List.of(),
            0,
            null,
            5,
            0,
            0,
            0,
            initialVisible,
            false,
            false,
            0, 0, List.of(), 0, 0, 0
        );

        DungeonSessionState afterLeft = dungeonSessionService.move(state, DungeonDirection.LEFT);
        assertThat(afterLeft.playerPosition()).isEqualTo(entrance);

        DungeonSessionState afterRight = dungeonSessionService.move(state, DungeonDirection.RIGHT);
        assertThat(afterRight.playerPosition()).isEqualTo(p(3, 2));
        assertThat(afterRight.visibleTiles()).contains(p(3, 1), p(3, 3));
    }

    @Test
    void moveOntoHealTileRestoresOneHealthAndClearsTile() {
        DungeonSessionState state = stateWithAdjacentSpecialTile(DungeonTileType.HEAL, 4, 0);

        DungeonSessionState moved = dungeonSessionService.move(state, DungeonDirection.RIGHT);

        assertThat(moved.health()).isEqualTo(5);
        DungeonTile tile = moved.map().tileAt(p(3, 2));
        assertThat(tile.type()).isEqualTo(DungeonTileType.FLOOR);
    }

    @Test
    void moveOntoTreasureTileAddsScoreAndClearsTile() {
        DungeonSessionState state = stateWithAdjacentSpecialTile(DungeonTileType.TREASURE, 5, 0);

        DungeonSessionState moved = dungeonSessionService.move(state, DungeonDirection.RIGHT);

        assertThat(moved.score()).isEqualTo(50);
        DungeonTile tile = moved.map().tileAt(p(3, 2));
        assertThat(tile.type()).isEqualTo(DungeonTileType.FLOOR);
    }

    // ---- flashcard answer tests ----

    @Test
    void answerFlashcard_marksCorrectAndDoesNotCallScheduling() {
        DungeonEncounter enc = DungeonEncounter.flashcard("e1", false, 101L, "Front", "Back", null, null).activate();
        DungeonSessionState state = stateWithActiveFlashcard(enc);

        DungeonSessionState result = dungeonSessionService.answerFlashcard(state, true);

        assertThat(result.activeEncounter()).isNull();
        assertThat(result.encounters().get("e1").status()).isEqualTo(DungeonEncounterStatus.CLEARED);
        assertThat(result.encounters().get("e1").correct()).isTrue();
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.answeredCount()).isEqualTo(1);
    }

    @Test
    void missedFlashcardCostsHealth() {
        DungeonEncounter enc = DungeonEncounter.flashcard("e1", false, 101L, "Front", "Back", null, null).activate();
        DungeonSessionState state = stateWithActiveFlashcard(enc);

        DungeonSessionState result = dungeonSessionService.answerFlashcard(state, false);

        assertThat(result.health()).isEqualTo(4);
        assertThat(result.encounters().get("e1").correct()).isFalse();
        assertThat(result.correctCount()).isZero();
        assertThat(result.answeredCount()).isEqualTo(1);
    }

    @Test
    void staleFlashcardAnswerReturnsUnchangedState() {
        DungeonPosition pos = p(2, 2);
        DungeonMap map = fiveByFiveWithWalls(pos, tiles -> {});
        Set<DungeonPosition> visible = Set.of(pos, p(1, 2), p(3, 2), p(2, 1), p(2, 3));
        DungeonSessionState state = new DungeonSessionState(
            config(DungeonMode.FLASHCARDS), map, pos, Map.of(), List.of(), 0, null, 5, 0, 0, 0, visible, false, false,
            0, 0, List.of(), 0, 0, 0);

        DungeonSessionState result = dungeonSessionService.answerFlashcard(state, true);

        assertThat(result).isSameAs(state);
    }

    // ---- quiz answer tests ----

    @Test
    void answerQuiz_gradesSelectionAndStartsNextBossPrompt() {
        QuizQuestion q1 = new QuizQuestion(QuestionType.MULTIPLE_CHOICE, "Q1",
            List.of("A", "B", "C"), List.of(0));
        QuizQuestion q2 = new QuizQuestion(QuestionType.MULTIPLE_CHOICE, "Q2",
            List.of("A", "B", "C"), List.of(1));

        DungeonPosition pos = p(2, 2);
        DungeonMap map = fiveByFiveWithWalls(pos, tiles ->
            tiles.put(pos, tile(pos, DungeonTileType.BOSS, true, true, null)));

        String id1 = "boss1";
        String id2 = "boss2";
        DungeonEncounter enc1 = DungeonEncounter.quiz(id1, true, q1).activate();
        DungeonEncounter enc2 = DungeonEncounter.quiz(id2, true, q2);

        Set<DungeonPosition> visible = Set.of(pos, p(1, 2), p(3, 2), p(2, 1), p(2, 3));
        DungeonSessionState state = new DungeonSessionState(
            config(DungeonMode.AI_QUIZ),
            map, pos,
            Map.of(id1, enc1, id2, enc2),
            List.of(id1, id2),
            0,
            id1,
            5, 0, 0, 0, visible, false, false,
            0, 0, List.of(), 0, 0, 0
        );

        DungeonSessionState result = dungeonSessionService.answerQuiz(state, List.of(0));

        assertThat(result.encounters().get(id1).status()).isEqualTo(DungeonEncounterStatus.CLEARED);
        assertThat(result.encounters().get(id1).correct()).isTrue();
        assertThat(result.bossIndex()).isEqualTo(1);
        assertThat(result.activeEncounterId()).isEqualTo(id2);
        assertThat(result.encounters().get(id2).status()).isEqualTo(DungeonEncounterStatus.ACTIVE);
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.answeredCount()).isEqualTo(1);
    }

    @Test
    void answerQuiz_gradesMultipleSelectWithoutDependingOnOrder() {
        QuizQuestion q = new QuizQuestion(QuestionType.MULTIPLE_SELECT, "Q",
            List.of("A", "B", "C", "D"), List.of(0, 2, 3));
        DungeonSessionState state = stateWithActiveQuiz(q);

        DungeonSessionState result = dungeonSessionService.answerQuiz(state, List.of(3, 0, 2));

        assertThat(result.encounters().get("qz1").correct()).isTrue();
        assertThat(result.correctCount()).isEqualTo(1);
    }

    @Test
    void answerQuiz_gradesTrueFalseQuestions() {
        QuizQuestion q = new QuizQuestion(QuestionType.TRUE_FALSE, "TF",
            List.of("True", "False"), List.of(0));
        DungeonSessionState state = stateWithActiveQuiz(q);

        DungeonSessionState result = dungeonSessionService.answerQuiz(state, List.of(1));

        assertThat(result.encounters().get("qz1").correct()).isFalse();
        assertThat(result.health()).isEqualTo(4);
        assertThat(result.correctCount()).isZero();
    }

    @Test
    void staleQuizAnswerReturnsUnchangedState() {
        DungeonPosition pos = p(2, 2);
        DungeonMap map = fiveByFiveWithWalls(pos, tiles -> {});
        Set<DungeonPosition> visible = Set.of(pos, p(1, 2), p(3, 2), p(2, 1), p(2, 3));
        DungeonSessionState state = new DungeonSessionState(
            config(DungeonMode.AI_QUIZ), map, pos, Map.of(), List.of(), 0, null, 5, 0, 0, 0, visible, false, false,
            0, 0, List.of(), 0, 0, 0);

        DungeonSessionState result = dungeonSessionService.answerQuiz(state, List.of(0));
        assertThat(result).isSameAs(state);
    }

    // ---- state factories ----

    private DungeonSessionState stateWithAdjacentSpecialTile(DungeonTileType type, int health, int score) {
        DungeonPosition pos = p(2, 2);
        DungeonPosition specialPos = p(3, 2);
        DungeonMap map = fiveByFiveWithWalls(pos, tiles -> {
            tiles.put(specialPos, new DungeonTile(specialPos, type, true, false, null));
        });
        Set<DungeonPosition> visible = Set.of(pos, p(1, 2), p(3, 2), p(2, 1), p(2, 3));
        return new DungeonSessionState(
            config(DungeonMode.FLASHCARDS), map, pos,
            Map.of(), List.of(), 0, null, health, score, 0, 0, visible, false, false,
            0, 0, List.of(), 0, 0, 0);
    }

    private DungeonSessionState stateWithActiveFlashcard(DungeonEncounter enc) {
        DungeonPosition pos = p(2, 2);
        DungeonMap map = fiveByFiveWithWalls(pos, tiles ->
            tiles.put(pos, tile(pos, DungeonTileType.ENCOUNTER, true, true, enc.id())));
        Set<DungeonPosition> visible = Set.of(pos, p(1, 2), p(3, 2), p(2, 1), p(2, 3));
        return new DungeonSessionState(
            config(DungeonMode.FLASHCARDS), map, pos,
            Map.of(enc.id(), enc), List.of(), 0, enc.id(),
            5, 0, 0, 0, visible, false, false,
            0, 0, List.of(), 0, 0, 0);
    }

    private DungeonSessionState stateWithActiveQuiz(QuizQuestion question) {
        DungeonPosition pos = p(2, 2);
        DungeonMap map = fiveByFiveWithWalls(pos, tiles ->
            tiles.put(pos, tile(pos, DungeonTileType.ENCOUNTER, true, true, null)));
        String id = "qz1";
        DungeonEncounter enc = DungeonEncounter.quiz(id, false, question).activate();
        Set<DungeonPosition> visible = Set.of(pos, p(1, 2), p(3, 2), p(2, 1), p(2, 3));
        return new DungeonSessionState(
            config(DungeonMode.AI_QUIZ), map, pos,
            Map.of(id, enc), List.of(), 0, id,
            5, 0, 0, 0, visible, false, false,
            0, 0, List.of(), 0, 0, 0);
    }

    private DungeonConfig config(DungeonMode mode) {
        return new DungeonConfig(mode, DungeonSize.SMALL, List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
    }
}
