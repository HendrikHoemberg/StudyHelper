package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DungeonSessionService {

    private static final int STARTING_HEALTH = 5;
    private static final int HEAL_AMOUNT = 1;
    private static final int TREASURE_SCORE = 50;

    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final QuizSessionService quizSessionService;
    private final DungeonMapGenerator dungeonMapGenerator;

    public DungeonSessionService(DeckService deckService,
                                 FlashcardService flashcardService,
                                 QuizSessionService quizSessionService,
                                 DungeonMapGenerator dungeonMapGenerator) {
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.quizSessionService = quizSessionService;
        this.dungeonMapGenerator = dungeonMapGenerator;
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

        DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds);
        return initialState(
            new DungeonConfig(DungeonMode.FLASHCARDS, size, normalizedIds,
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
            map,
            encounters,
            bossEncounterIds
        );
    }

    @Transactional
    public DungeonSessionState createAiQuizDungeon(List<Long> selectedDeckIds,
                                                   DungeonSize size,
                                                   QuizQuestionMode questionMode,
                                                   Difficulty difficulty,
                                                   String additionalInstructions,
                                                   HttpServletRequest request,
                                                   User user) {
        List<Long> normalizedIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(normalizedIds, user);
        List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
        validateSize(size, flashcards.size(), "flashcards");

        int questionCount = size.totalPrompts();
        QuizSessionState quizState;
        try {
            quizState = quizSessionService.createSession(
                normalizedIds, List.of(), request, questionCount,
                questionMode, difficulty, additionalInstructions, user);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create quiz session", e);
        }

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

        DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds);
        return initialState(
            new DungeonConfig(DungeonMode.AI_QUIZ, size, normalizedIds,
                questionMode, difficulty, additionalInstructions),
            map,
            encounters,
            bossEncounterIds
        );
    }

    public DungeonSessionState move(DungeonSessionState state, DungeonDirection direction) {
        if (state.isComplete()) return state;
        if (state.activeEncounterId() != null) return state;

        DungeonPosition newPos = state.playerPosition().move(direction);
        if (!state.map().isInside(newPos)) return state;

        DungeonTile newTile = state.map().tileAt(newPos);
        if (newTile == null || !newTile.walkable()) return state;

        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>(state.map().tiles());
        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>(state.encounters());

        revealAround(tiles, newPos);

        int health = state.health();
        int score = state.score();
        String activeEncounterId = null;

        switch (newTile.type()) {
            case ENCOUNTER -> {
                String eid = newTile.encounterId();
                if (eid != null) {
                    DungeonEncounter enc = encounters.get(eid);
                    if (enc != null && enc.status() == DungeonEncounterStatus.PENDING) {
                        encounters.put(eid, enc.activate());
                        activeEncounterId = eid;
                    }
                }
            }
            case BOSS -> {
                if (!state.bossEncounterIds().isEmpty()) {
                    String eid = state.bossEncounterIds().get(0);
                    DungeonEncounter enc = encounters.get(eid);
                    if (enc != null && enc.status() == DungeonEncounterStatus.PENDING) {
                        encounters.put(eid, enc.activate());
                        activeEncounterId = eid;
                    }
                }
            }
            case HEAL -> {
                health = Math.min(STARTING_HEALTH, health + HEAL_AMOUNT);
                tiles.put(newPos, newTile.withType(DungeonTileType.FLOOR, null));
            }
            case TREASURE -> {
                score += TREASURE_SCORE;
                tiles.put(newPos, newTile.withType(DungeonTileType.FLOOR, null));
            }
        }

        return new DungeonSessionState(
            state.config(),
            new DungeonMap(state.map().width(), state.map().height(),
                state.map().entrance(), state.map().boss(), Map.copyOf(tiles)),
            newPos,
            Map.copyOf(encounters),
            state.bossEncounterIds(),
            state.bossIndex(),
            activeEncounterId,
            health,
            score,
            state.answeredCount(),
            state.correctCount(),
            visibleFrom(tiles),
            state.won(),
            state.defeated()
        );
    }

    public DungeonSessionState answerFlashcard(DungeonSessionState state, boolean gotIt) {
        DungeonEncounter enc = state.activeEncounter();
        if (enc == null) return state;
        if (enc.type() == DungeonEncounterType.QUIZ || enc.type() == DungeonEncounterType.BOSS_QUIZ) return state;
        return applyAnswer(state, enc, gotIt ? List.of(1) : List.of(0), gotIt);
    }

    public DungeonSessionState answerQuiz(DungeonSessionState state, List<Integer> selectedOptions) {
        DungeonEncounter enc = state.activeEncounter();
        if (enc == null) return state;
        if (enc.type() == DungeonEncounterType.FLASHCARD || enc.type() == DungeonEncounterType.BOSS_FLASHCARD) return state;

        List<Integer> safeOptions = selectedOptions == null ? List.of() : selectedOptions;
        QuizQuestion q = enc.quizQuestion();
        boolean correct = new HashSet<>(safeOptions).equals(new HashSet<>(q.correctOptionIndices()));
        return applyAnswer(state, enc, safeOptions, correct);
    }

    public DungeonRunStats buildStats(DungeonSessionState state) {
        return new DungeonRunStats(
            state.config().mode(),
            state.config().size(),
            state.config().size().totalPrompts(),
            state.answeredCount(),
            state.correctCount(),
            state.health(),
            state.won()
        );
    }

    private DungeonSessionState initialState(DungeonConfig config, DungeonMap map,
                                              Map<String, DungeonEncounter> encounters,
                                              List<String> bossEncounterIds) {
        return new DungeonSessionState(
            config,
            map,
            map.entrance(),
            Map.copyOf(encounters),
            List.copyOf(bossEncounterIds),
            0,
            null,
            STARTING_HEALTH,
            0,
            0,
            0,
            visibleFrom(map.tiles()),
            false,
            false
        );
    }

    private void validateSize(DungeonSize size, int usableItems, String unit) {
        if (!size.isAvailableFor(usableItems)) {
            throw new IllegalArgumentException(
                "Not enough " + unit + " available for " + capitalize(size.name())
                    + " dungeon. Need " + size.totalPrompts() + ", have " + usableItems + "."
            );
        }
    }

    private DungeonSessionState applyAnswer(DungeonSessionState state, DungeonEncounter encounter,
                                             List<Integer> answer, boolean correct) {
        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>(state.encounters());

        String id = encounter.id();
        encounters.put(id, encounter.clear(answer, correct));

        int answeredCount = state.answeredCount() + 1;
        int correctCount = state.correctCount() + (correct ? 1 : 0);
        int health = correct ? state.health() : state.health() - 1;
        boolean defeated = health <= 0;

        String activeEncounterId = null;
        int bossIndex = state.bossIndex();
        boolean won = state.won();

        if (encounter.boss() && !defeated) {
            bossIndex++;
            if (bossIndex < state.bossEncounterIds().size()) {
                String nextBossId = state.bossEncounterIds().get(bossIndex);
                DungeonEncounter nextBoss = encounters.get(nextBossId);
                if (nextBoss != null && nextBoss.status() == DungeonEncounterStatus.PENDING) {
                    encounters.put(nextBossId, nextBoss.activate());
                    activeEncounterId = nextBossId;
                }
            } else {
                won = true;
            }
        }

        return new DungeonSessionState(
            state.config(),
            state.map(),
            state.playerPosition(),
            Map.copyOf(encounters),
            state.bossEncounterIds(),
            bossIndex,
            activeEncounterId,
            health,
            state.score(),
            answeredCount,
            correctCount,
            state.visibleTiles(),
            won,
            defeated
        );
    }

    private void revealAround(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition center) {
        reveal(tiles, center);
        for (DungeonDirection direction : DungeonDirection.values()) {
            reveal(tiles, center.move(direction));
        }
    }

    private void reveal(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition position) {
        DungeonTile tile = tiles.get(position);
        if (tile != null) {
            tiles.put(position, tile.reveal());
        }
    }

    private Set<DungeonPosition> visibleFrom(Map<DungeonPosition, DungeonTile> tiles) {
        return tiles.values().stream()
            .filter(DungeonTile::revealed)
            .map(DungeonTile::position)
            .collect(Collectors.toSet());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}
