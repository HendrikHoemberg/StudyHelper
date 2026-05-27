package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DungeonSessionService {

    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final QuizSessionService quizSessionService;
    private final DungeonMapGenerator dungeonMapGenerator;
    private final DungeonNavigationService navigationService;
    private final DungeonEncounterService encounterService;

    public DungeonSessionService(DeckService deckService,
                                 FlashcardService flashcardService,
                                 QuizSessionService quizSessionService,
                                 DungeonMapGenerator dungeonMapGenerator,
                                 DungeonNavigationService navigationService,
                                 DungeonEncounterService encounterService) {
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.quizSessionService = quizSessionService;
        this.dungeonMapGenerator = dungeonMapGenerator;
        this.navigationService = navigationService;
        this.encounterService = encounterService;
    }

    @Transactional(readOnly = true)
    public DungeonSessionState createFlashcardDungeon(List<Long> selectedDeckIds, DungeonSize size, User user) {
        List<Long> normalizedIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(normalizedIds, user);
        List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
        validateSize(size, flashcards.size(), "flashcards");

        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>();
        List<String> normalEncounterIds = new ArrayList<>();
        List<String> bossEncounterIds = new ArrayList<>();

        for (int i = 0; i < size.normalEncounterCount(); i++) {
            Flashcard card = flashcards.get(i);
            String id = "fc_" + i;
            encounters.put(id, DungeonEncounter.flashcard(id, false, card.getId(),
                card.getFrontText(), card.getBackText(),
                card.getFrontImageFilename(), card.getBackImageFilename()));
            normalEncounterIds.add(id);
        }

        int bossStart = size.normalEncounterCount();
        for (int i = 0; i < size.bossPromptCount(); i++) {
            Flashcard card = flashcards.get(bossStart + i);
            String id = "fc_boss_" + i;
            encounters.put(id, DungeonEncounter.flashcard(id, true, card.getId(),
                card.getFrontText(), card.getBackText(),
                card.getFrontImageFilename(), card.getBackImageFilename()));
            bossEncounterIds.add(id);
        }

        int eliteStart = bossStart + size.bossPromptCount();
        List<List<String>> eliteGauntlets = new ArrayList<>();
        int eliteCardsPerGroup = size.cardsPerEliteGauntlet();
        for (int g = 0; g < size.eliteGauntletCount(); g++) {
            List<String> group = new ArrayList<>();
            for (int c = 0; c < eliteCardsPerGroup; c++) {
                int cardIdx = eliteStart + (g * eliteCardsPerGroup) + c;
                Flashcard card = flashcards.get(cardIdx);
                String id = "fc_elite_" + g + "_" + c;
                encounters.put(id, DungeonEncounter.flashcard(id, false, card.getId(),
                    card.getFrontText(), card.getBackText(),
                    card.getFrontImageFilename(), card.getBackImageFilename()));
                group.add(id);
            }
            eliteGauntlets.add(group);
        }

        DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds, eliteGauntlets);
        return initialState(
            new DungeonConfig(DungeonMode.FLASHCARDS, size, normalizedIds,
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
            map, encounters, bossEncounterIds);
    }

    @Transactional
    public DungeonSessionState createAiQuizDungeon(List<Long> selectedDeckIds,
                                                    DungeonSize size,
                                                    QuizQuestionMode questionMode,
                                                    Difficulty difficulty,
                                                    String additionalInstructions,
                                                    HttpServletRequest request,
                                                    User user) throws Exception {
        List<Long> normalizedIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(normalizedIds, user);
        List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
        validateSize(size, flashcards.size(), "flashcards");

        int questionCount = size.totalPrompts();
        QuizSessionState quizState = quizSessionService.createSession(
            normalizedIds, List.of(), request, questionCount,
            questionMode, difficulty, additionalInstructions, user);

        List<QuizQuestion> questions = quizState.questions();

        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>();
        List<String> normalEncounterIds = new ArrayList<>();
        List<String> bossEncounterIds = new ArrayList<>();

        for (int i = 0; i < size.normalEncounterCount(); i++) {
            QuizQuestion q = questions.get(i);
            String id = "qz_" + i;
            encounters.put(id, DungeonEncounter.quiz(id, false, q));
            normalEncounterIds.add(id);
        }

        int bossStart = size.normalEncounterCount();
        for (int i = 0; i < size.bossPromptCount(); i++) {
            QuizQuestion q = questions.get(bossStart + i);
            String id = "qz_boss_" + i;
            encounters.put(id, DungeonEncounter.quiz(id, true, q));
            bossEncounterIds.add(id);
        }

        int eliteStart = bossStart + size.bossPromptCount();
        List<List<String>> eliteGauntlets = new ArrayList<>();
        int eliteCardsPerGroup = size.cardsPerEliteGauntlet();
        for (int g = 0; g < size.eliteGauntletCount(); g++) {
            List<String> group = new ArrayList<>();
            for (int c = 0; c < eliteCardsPerGroup; c++) {
                int idx = eliteStart + (g * eliteCardsPerGroup) + c;
                QuizQuestion q = questions.get(idx);
                String id = "qz_elite_" + g + "_" + c;
                encounters.put(id, DungeonEncounter.quiz(id, false, q));
                group.add(id);
            }
            eliteGauntlets.add(group);
        }

        DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds, eliteGauntlets);
        return initialState(
            new DungeonConfig(DungeonMode.AI_QUIZ, size, normalizedIds,
                questionMode, difficulty, additionalInstructions),
            map, encounters, bossEncounterIds);
    }

    public DungeonSessionState move(DungeonSessionState state, DungeonDirection direction) {
        DungeonNavigationService.MoveResult result = navigationService.move(state, direction);
        DungeonSessionState moved = result.state();
        if (result.encounterId() != null) {
            if (result.landedTileType() == DungeonTileType.ELITE) {
                moved = encounterService.activateElite(moved, result.encounterId());
            } else {
                boolean isBoss = result.landedTileType() == DungeonTileType.BOSS;
                moved = encounterService.activate(moved, result.encounterId(), isBoss);
            }
        }
        return moved;
    }

    public DungeonSessionState answerFlashcard(DungeonSessionState state, boolean gotIt) {
        return encounterService.answerFlashcard(state, gotIt);
    }

    public DungeonSessionState answerQuiz(DungeonSessionState state, List<Integer> selectedOptions) {
        return encounterService.answerQuiz(state, selectedOptions);
    }

    public DungeonRunStats buildStats(DungeonSessionState state) {
        return new DungeonRunStats(
            state.config().mode(),
            state.config().size(),
            state.config().size().totalPrompts(),
            state.answeredCount(),
            state.correctCount(),
            state.health(),
            state.won(),
            state.longestStreak(),
            state.elitesCleared(),
            state.shieldsUsed());
    }

    private DungeonSessionState initialState(DungeonConfig config, DungeonMap map,
                                              Map<String, DungeonEncounter> encounters,
                                              List<String> bossEncounterIds) {
        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>(map.tiles());
        Set<DungeonPosition> visible = navigationService.revealAroundEntrance(tiles, map.entrance());
        DungeonMap exploredMap = new DungeonMap(map.width(), map.height(),
            map.entrance(), map.boss(), Map.copyOf(tiles));

        return new DungeonSessionState(
            config, exploredMap, map.entrance(),
            Map.copyOf(encounters), List.copyOf(bossEncounterIds),
            0, null,
            DungeonDamage.STARTING_HEALTH, 0, 0, 0,
            visible, false, false,
            0, 0, List.of(), 0, 0, 0);
    }

    private void validateSize(DungeonSize size, int usableItems, String unit) {
        if (!size.isAvailableFor(usableItems)) {
            throw new IllegalArgumentException(
                "Not enough " + unit + " available for " + capitalize(size.name())
                    + " dungeon. Need " + size.totalPrompts() + ", have " + usableItems + ".");
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}
