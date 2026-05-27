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
    private final DungeonRelicService relicService;

    public DungeonSessionService(DeckService deckService,
                                 FlashcardService flashcardService,
                                 QuizSessionService quizSessionService,
                                 DungeonMapGenerator dungeonMapGenerator,
                                 DungeonNavigationService navigationService,
                                 DungeonEncounterService encounterService,
                                 DungeonRelicService relicService) {
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.quizSessionService = quizSessionService;
        this.dungeonMapGenerator = dungeonMapGenerator;
        this.navigationService = navigationService;
        this.encounterService = encounterService;
        this.relicService = relicService;
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
        List<List<String>> eliteGauntlets = buildFlashcardEncounters(
            flashcards, size, encounters, normalEncounterIds, bossEncounterIds);

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
        List<List<String>> eliteGauntlets = buildQuizEncounters(
            questions, size, encounters, normalEncounterIds, bossEncounterIds);

        DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds, eliteGauntlets);
        return initialState(
            new DungeonConfig(DungeonMode.AI_QUIZ, size, normalizedIds,
                questionMode, difficulty, additionalInstructions),
            map, encounters, bossEncounterIds);
    }

    public DungeonSessionState move(DungeonSessionState state, DungeonDirection direction) {
        DungeonNavigationService.MoveResult result = navigationService.move(state, direction);
        DungeonSessionState moved = result.state();

        DungeonRoom destination = moved.currentRoom();

        // Set pending picks for TREASURE / SHOP / SECRET on first entry
        if (destination != null && !destination.cleared() && moved.pendingRelicPick() == null) {
            PendingRelicPick newPick = pickForRoom(destination);
            if (newPick != null) {
                moved = withPendingPick(moved, newPick);
            }
        }

        if (result.activatedEncounterRoomId() != null) {
            moved = encounterService.activateAt(moved, result.activatedEncounterRoomId());
        }
        return moved;
    }

    public DungeonSessionState answerFlashcard(DungeonSessionState state, boolean gotIt) {
        return encounterService.answerFlashcard(state, gotIt);
    }

    public DungeonSessionState answerQuiz(DungeonSessionState state, List<Integer> selectedOptions) {
        return encounterService.answerQuiz(state, selectedOptions);
    }

    public DungeonSessionState pickRelic(DungeonSessionState state, RelicId relicId) {
        return relicService.pick(state, relicId);
    }

    public DungeonSessionState buyRelic(DungeonSessionState state, RelicId relicId) {
        return relicService.buy(state, relicId);
    }

    public DungeonSessionState skipShop(DungeonSessionState state) {
        return relicService.skipShop(state);
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
            state.shieldsUsed(),
            state.ownedRelics().size());
    }

    // ===== helpers =====

    private List<List<String>> buildFlashcardEncounters(List<Flashcard> flashcards, DungeonSize size,
                                                           Map<String, DungeonEncounter> encs,
                                                           List<String> normalIds, List<String> bossIds) {
        for (int i = 0; i < size.normalEncounterCount(); i++) {
            Flashcard card = flashcards.get(i);
            String id = "fc_" + i;
            encs.put(id, DungeonEncounter.flashcard(id, false, card.getId(),
                card.getFrontText(), card.getBackText(),
                card.getFrontImageFilename(), card.getBackImageFilename()));
            normalIds.add(id);
        }
        int bossStart = size.normalEncounterCount();
        for (int i = 0; i < size.bossPromptCount(); i++) {
            Flashcard card = flashcards.get(bossStart + i);
            String id = "fc_boss_" + i;
            encs.put(id, DungeonEncounter.flashcard(id, true, card.getId(),
                card.getFrontText(), card.getBackText(),
                card.getFrontImageFilename(), card.getBackImageFilename()));
            bossIds.add(id);
        }
        int eliteStart = bossStart + size.bossPromptCount();
        List<List<String>> groups = new ArrayList<>();
        int cardsPerGroup = size.cardsPerEliteGauntlet();
        for (int g = 0; g < size.eliteGauntletCount(); g++) {
            List<String> group = new ArrayList<>();
            for (int c = 0; c < cardsPerGroup; c++) {
                int cardIdx = eliteStart + (g * cardsPerGroup) + c;
                Flashcard card = flashcards.get(cardIdx);
                String id = "fc_elite_" + g + "_" + c;
                encs.put(id, DungeonEncounter.flashcard(id, false, card.getId(),
                    card.getFrontText(), card.getBackText(),
                    card.getFrontImageFilename(), card.getBackImageFilename()));
                group.add(id);
            }
            groups.add(group);
        }
        return groups;
    }

    private List<List<String>> buildQuizEncounters(List<QuizQuestion> questions, DungeonSize size,
                                                      Map<String, DungeonEncounter> encs,
                                                      List<String> normalIds, List<String> bossIds) {
        for (int i = 0; i < size.normalEncounterCount(); i++) {
            String id = "qz_" + i;
            encs.put(id, DungeonEncounter.quiz(id, false, questions.get(i)));
            normalIds.add(id);
        }
        int bossStart = size.normalEncounterCount();
        for (int i = 0; i < size.bossPromptCount(); i++) {
            String id = "qz_boss_" + i;
            encs.put(id, DungeonEncounter.quiz(id, true, questions.get(bossStart + i)));
            bossIds.add(id);
        }
        int eliteStart = bossStart + size.bossPromptCount();
        List<List<String>> groups = new ArrayList<>();
        int cardsPerGroup = size.cardsPerEliteGauntlet();
        for (int g = 0; g < size.eliteGauntletCount(); g++) {
            List<String> group = new ArrayList<>();
            for (int c = 0; c < cardsPerGroup; c++) {
                int idx = eliteStart + (g * cardsPerGroup) + c;
                String id = "qz_elite_" + g + "_" + c;
                encs.put(id, DungeonEncounter.quiz(id, false, questions.get(idx)));
                group.add(id);
            }
            groups.add(group);
        }
        return groups;
    }

    private DungeonSessionState initialState(DungeonConfig config, DungeonMap map,
                                              Map<String, DungeonEncounter> encounters,
                                              List<String> bossEncounterIds) {
        return new DungeonSessionState(
            config, map, map.entranceRoomId(),
            Map.copyOf(encounters), List.copyOf(bossEncounterIds),
            0, null,
            DungeonDamage.STARTING_HEALTH, DungeonDamage.STARTING_HEALTH_CAP, 0, DungeonDamage.STARTING_SHIELD_CAP,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);
    }

    private PendingRelicPick pickForRoom(DungeonRoom room) {
        return switch (room.type()) {
            case TREASURE -> room.treasureOffer() == null ? null :
                new PendingRelicPick(PendingPickType.TREASURE, room.id(), room.treasureOffer().relics(), null);
            case SHOP -> room.shopOffer() == null ? null :
                new PendingRelicPick(PendingPickType.SHOP, room.id(), List.of(), room.shopOffer());
            case SECRET -> {
                if (room.secretReward() instanceof SecretReward.RelicReward r) {
                    yield new PendingRelicPick(PendingPickType.SECRET, room.id(), List.of(r.relic()), null);
                }
                yield null;
            }
            default -> null;
        };
    }

    private DungeonSessionState withPendingPick(DungeonSessionState s, PendingRelicPick pick) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), pick);
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
