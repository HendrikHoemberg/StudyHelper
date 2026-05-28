package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.exception.AiQuizGenerationException;
import com.HendrikHoemberg.StudyHelper.exception.DeckNotFoundException;
import com.HendrikHoemberg.StudyHelper.exception.ResourceNotFoundException;
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
    private final DungeonShrineService shrineService;

    public DungeonSessionService(DeckService deckService,
                                 FlashcardService flashcardService,
                                 QuizSessionService quizSessionService,
                                 DungeonMapGenerator dungeonMapGenerator,
                                 DungeonNavigationService navigationService,
                                 DungeonEncounterService encounterService,
                                 DungeonRelicService relicService,
                                 DungeonShrineService shrineService) {
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.quizSessionService = quizSessionService;
        this.dungeonMapGenerator = dungeonMapGenerator;
        this.navigationService = navigationService;
        this.encounterService = encounterService;
        this.relicService = relicService;
        this.shrineService = shrineService;
    }

    @Transactional(readOnly = true)
    public DungeonSessionState createFlashcardDungeon(List<Long> selectedDeckIds, DungeonSize size, User user) {
        List<Long> normalizedIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Deck> decks = loadDecks(normalizedIds, user);
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
                                                     User user) {
        List<Long> normalizedIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Deck> decks = loadDecks(normalizedIds, user);
        List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
        validateSize(size, flashcards.size(), "flashcards");

        int questionCount = size.totalPrompts();
        List<QuizQuestion> questions = generateQuizQuestions(
            normalizedIds, request, questionCount, questionMode, difficulty, additionalInstructions, user);

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

    private List<Deck> loadDecks(List<Long> normalizedIds, User user) {
        try {
            return deckService.getValidatedDecksInRequestedOrder(normalizedIds, user);
        } catch (ResourceNotFoundException e) {
            throw new DeckNotFoundException(e.getMessage());
        }
    }

    private List<QuizQuestion> generateQuizQuestions(List<Long> normalizedIds,
                                                       HttpServletRequest request,
                                                       int questionCount,
                                                       QuizQuestionMode questionMode,
                                                       Difficulty difficulty,
                                                       String additionalInstructions,
                                                       User user) {
        try {
            QuizSessionState quizState = quizSessionService.createSession(
                normalizedIds, List.of(), request, questionCount,
                questionMode, difficulty, additionalInstructions, user);
            return quizState.questions();
        } catch (DeckNotFoundException | AiGenerationException | AiQuotaExceededException
                 | IllegalArgumentException e) {
            throw e;
        } catch (ResourceNotFoundException e) {
            throw new DeckNotFoundException(e.getMessage());
        } catch (Exception e) {
            throw new AiQuizGenerationException("AI quiz generation failed", e);
        }
    }

    public ActionResult move(DungeonSessionState state, DungeonDirection direction) {
        ActionResult navResult = navigationService.move(state, direction);
        if (navResult instanceof ActionResult.Failure failure) {
            return failure;
        }
        DungeonSessionState moved = navResult.state();

        DungeonRoom destination = moved.currentRoom();

        if (destination != null && !destination.cleared() && moved.loadout().pendingRelicPick() == null) {
            PendingRelicPick newPick = pickForRoom(destination);
            if (newPick != null) {
                moved = moved.withLoadout(moved.loadout().withPendingRelicPick(newPick));
            }
        }

        DungeonRoom destRoom = moved.currentRoom();
        if (destRoom != null && !destRoom.cleared()
            && (destRoom.type() == RoomType.COMBAT
                || destRoom.type() == RoomType.ELITE
                || destRoom.type() == RoomType.BOSS)) {
            ActionResult encResult = encounterService.activateAt(moved, destRoom.id());
            if (encResult instanceof ActionResult.Failure encFail) {
                return ActionResult.failure(moved, encFail.message());
            }
            moved = encResult.state();
        }
        return ActionResult.success(moved);
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

    public DungeonSessionState shrineLeave(DungeonSessionState state) {
        return shrineService.leave(state);
    }

    public DungeonSessionState shrineDrink(DungeonSessionState state) {
        return shrineService.drink(state);
    }

    public DungeonSessionState shrineRoll(DungeonSessionState state, int rollResult, RelicId grantedRelic) {
        return shrineService.applyRoll(state, new DungeonShrineService.ShrineRollOutcome(rollResult, grantedRelic));
    }

    public DungeonRunStats buildStats(DungeonSessionState state) {
        return new DungeonRunStats(
            state.config().mode(),
            state.config().size(),
            state.config().size().totalPrompts(),
            state.progress().answeredCount(),
            state.progress().correctCount(),
            state.resources().health(),
            state.won(),
            state.progress().longestStreak(),
            state.progress().elitesCleared(),
            state.progress().shieldsUsed(),
            state.loadout().ownedRelics().size());
    }

    // ===== collect =====

    public CollectResult collectCoin(DungeonSessionState state, String itemId) {
        if (!canCollectInteractiveItem(state)) return new CollectResult(CollectStatus.REJECTED, state);
        if (!isValidItemId(state, itemId, "coin")) return new CollectResult(CollectStatus.REJECTED, state);
        if (state.collectedItems().contains(itemId)) return new CollectResult(CollectStatus.DUPLICATE, state);
        Set<String> collected = new HashSet<>(state.collectedItems());
        collected.add(itemId);
        return new CollectResult(CollectStatus.OK,
            state.withResources(state.resources().addScore(1)).withCollectedItems(collected));
    }

    public CollectResult collectShield(DungeonSessionState state, String itemId) {
        if (!canCollectInteractiveItem(state)) return new CollectResult(CollectStatus.REJECTED, state);
        if (!isValidItemId(state, itemId, "shield")) return new CollectResult(CollectStatus.REJECTED, state);
        if (state.collectedItems().contains(itemId)) return new CollectResult(CollectStatus.DUPLICATE, state);
        DungeonResources nextResources = state.resources().addShield();
        if (nextResources == state.resources()) return new CollectResult(CollectStatus.REJECTED, state);
        Set<String> collected = new HashSet<>(state.collectedItems());
        collected.add(itemId);
        return new CollectResult(CollectStatus.OK,
            state.withResources(nextResources).withCollectedItems(collected));
    }

    private boolean canCollectInteractiveItem(DungeonSessionState state) {
        if (state == null) return false;
        if (state.defeated() || state.won()) return false;
        if (state.combat().activeEncounterId() != null) return false;
        if (state.loadout().pendingRelicPick() != null) return false;
        return true;
    }

    private boolean isValidItemId(DungeonSessionState state, String itemId, String expectedType) {
        if (itemId == null || itemId.isBlank()) return false;
        String prefix = state.currentRoomId() + "_" + expectedType + "_";
        if (!itemId.startsWith(prefix)) return false;
        String suffix = itemId.substring(prefix.length());
        if (suffix.isEmpty()) return false;
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) return false;
        }
        return true;
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
        return DungeonSessionState.builder()
            .config(config)
            .map(map)
            .currentRoomId(map.entranceRoomId())
            .encounters(Map.copyOf(encounters))
            .bossEncounterIds(List.copyOf(bossEncounterIds))
            .resources(new DungeonResources(
                DungeonBalance.INITIAL_HEALTH,
                DungeonBalance.INITIAL_HEALTH_CAP,
                0,
                DungeonBalance.INITIAL_SHIELD_CAP,
                0))
            .build();
    }

    private PendingRelicPick pickForRoom(DungeonRoom room) {
        return switch (room.type()) {
            case TREASURE -> room.treasureOffer() == null ? null :
                new PendingRelicPick(PendingPickType.TREASURE, room.id(), room.treasureOffer().relics(), null);
            case SHOP -> room.shopOffer() == null ? null :
                new PendingRelicPick(PendingPickType.SHOP, room.id(), List.of(), room.shopOffer());
            case SHRINE -> new PendingRelicPick(PendingPickType.SHRINE, room.id(), List.of(), null);
            case SECRET -> {
                if (room.secretReward() instanceof SecretReward.RelicReward r) {
                    yield new PendingRelicPick(PendingPickType.SECRET, room.id(), List.of(r.relic()), null);
                }
                yield null;
            }
            default -> null;
        };
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
