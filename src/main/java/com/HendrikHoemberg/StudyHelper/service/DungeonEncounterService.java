package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DungeonEncounterService {

    static final int STREAK_FOR_SHIELD = 3;
    static final int WRONG_ANSWER_DAMAGE = 1;
    static final int COMBAT_CLEAR_SCORE = 10;
    static final int ELITE_CLEAR_SCORE = 50;
    static final int BOSS_PROMPT_SCORE = 20;

    public DungeonSessionState activateAt(DungeonSessionState state, String roomId) {
        if (state.combat().activeEncounterId() != null) return state;
        DungeonRoom room = state.map().room(roomId);
        if (room == null) return state;
        if (room.cleared()) return state;

        return switch (room.type()) {
            case COMBAT -> activateNormal(state, room.encounterId());
            case ELITE -> activateElite(state, room);
            case BOSS -> activateBoss(state);
            default -> state;
        };
    }

    public DungeonSessionState answerFlashcard(DungeonSessionState state, boolean gotIt) {
        DungeonEncounter enc = state.activeEncounter();
        if (enc == null) return state;
        if (enc.type() == DungeonEncounterType.QUIZ || enc.type() == DungeonEncounterType.BOSS_QUIZ) return state;
        return processAnswer(state, enc, gotIt ? List.of(1) : List.of(0), gotIt);
    }

    public DungeonSessionState answerQuiz(DungeonSessionState state, List<Integer> selectedOptions) {
        DungeonEncounter enc = state.activeEncounter();
        if (enc == null) return state;
        if (enc.type() == DungeonEncounterType.FLASHCARD || enc.type() == DungeonEncounterType.BOSS_FLASHCARD) return state;
        List<Integer> safe = selectedOptions == null ? List.of() : selectedOptions;
        QuizQuestion q = enc.quizQuestion();
        boolean correct = new HashSet<>(safe).equals(new HashSet<>(q.correctOptionIndices()));
        return processAnswer(state, enc, safe, correct);
    }

    // ===== activation =====

    private DungeonSessionState activateNormal(DungeonSessionState state, String encounterId) {
        if (encounterId == null) return state;
        DungeonEncounter enc = state.encounters().get(encounterId);
        if (enc == null || enc.status() != DungeonEncounterStatus.PENDING) return state;
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
        encs.put(encounterId, enc.activate());
        return state
            .withEncounters(Map.copyOf(encs))
            .withCombat(state.combat().withActiveEncounterId(encounterId));
    }

    private DungeonSessionState activateElite(DungeonSessionState state, DungeonRoom eliteRoom) {
        List<String> group = eliteRoom.gauntletGroup();
        if (group.isEmpty()) return state;
        String firstId = group.get(0);
        DungeonEncounter first = state.encounters().get(firstId);
        if (first == null) return state;
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
        encs.put(firstId, first.activate());
        List<String> remaining = new ArrayList<>(group.subList(1, group.size()));
        return state
            .withEncounters(Map.copyOf(encs))
            .withCombat(state.combat()
                .withActiveEncounterId(firstId)
                .withGauntletQueue(remaining));
    }

    private DungeonSessionState activateBoss(DungeonSessionState state) {
        if (state.bossEncounterIds().isEmpty()) return state;
        String bossId = state.bossEncounterIds().get(state.combat().bossIndex());
        return activateNormal(state, bossId);
    }

    // ===== answer pipeline =====

    private DungeonSessionState processAnswer(DungeonSessionState state, DungeonEncounter encounter,
                                                List<Integer> answer, boolean correct) {
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
        encs.put(encounter.id(), encounter.clear(answer, correct));

        int effectiveStreakThreshold = state.loadout().ownedRelics().contains(RelicId.SHARP_FOCUS)
            ? 2 : STREAK_FOR_SHIELD;

        DungeonProgress nextProgress = state.progress().recordAnswer(correct);

        DungeonResources nextResources = state.resources();
        if (correct && nextProgress.streak() % effectiveStreakThreshold == 0) {
            nextResources = nextResources.addShield();
        }
        if (correct) {
            int base = encounter.boss() ? BOSS_PROMPT_SCORE : COMBAT_CLEAR_SCORE;
            int bonus = state.loadout().ownedRelics().contains(RelicId.LUCKY_CHARM) ? 5 : 0;
            nextResources = nextResources.addScore(base + bonus);
        }

        DungeonSessionState working = state
            .withEncounters(Map.copyOf(encs))
            .withResources(nextResources)
            .withProgress(nextProgress);

        if (!correct) {
            working = applyWrongAnswerDamage(working);
        }

        if (isInGauntletRoom(working) || !working.combat().gauntletQueue().isEmpty()) {
            return resolveGauntlet(working, encounter, correct);
        }

        if (encounter.boss() && !working.defeated()) {
            return resolveBoss(working);
        }

        return clearCombatRoom(working);
    }

    private DungeonSessionState applyWrongAnswerDamage(DungeonSessionState s) {
        int luckyCoinsOwned = (int) s.loadout().ownedRelics().stream()
            .filter(r -> r == RelicId.LUCKY_COIN).count();
        if (s.progress().luckyCoinsConsumed() < luckyCoinsOwned) {
            return s.withProgress(s.progress().consumeLuckyCoin());
        }
        if (s.resources().shields() > 0) {
            return s
                .withResources(s.resources().withShields(s.resources().shields() - 1))
                .withProgress(s.progress().recordShieldUsed());
        }
        DungeonResources damaged = s.resources().takeHealthDamage(WRONG_ANSWER_DAMAGE);
        if (damaged.health() == 0 && s.loadout().ownedRelics().contains(RelicId.PHOENIX_FEATHER)) {
            return s
                .withResources(damaged.withHealth(1))
                .withLoadout(s.loadout().consumePhoenixFeather());
        }
        return s.withResources(damaged).withDefeated(damaged.health() == 0);
    }

    private boolean isInGauntletRoom(DungeonSessionState state) {
        DungeonRoom room = state.currentRoom();
        return room != null && room.type() == RoomType.ELITE && !room.cleared();
    }

    private DungeonSessionState resolveGauntlet(DungeonSessionState state,
                                                  DungeonEncounter encounter, boolean correct) {
        DungeonRoom room = state.currentRoom();
        List<String> group = room == null ? List.of() : room.gauntletGroup();

        if (!correct) {
            Map<String, DungeonEncounter> reset = new LinkedHashMap<>(state.encounters());
            for (String encId : group) {
                DungeonEncounter e = reset.get(encId);
                if (e != null) {
                    reset.put(encId, new DungeonEncounter(
                        e.id(), e.type(), DungeonEncounterStatus.PENDING, e.boss(),
                        e.flashcardId(), e.frontText(), e.backText(),
                        e.frontImageUrl(), e.backImageUrl(), e.quizQuestion(),
                        List.of(), null));
                }
            }
            return state
                .withEncounters(Map.copyOf(reset))
                .withCombat(state.combat().exitCombat());
        }

        if (!state.combat().gauntletQueue().isEmpty()) {
            String nextId = state.combat().gauntletQueue().get(0);
            List<String> remaining = new ArrayList<>(
                state.combat().gauntletQueue().subList(1, state.combat().gauntletQueue().size()));
            Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
            DungeonEncounter next = encs.get(nextId);
            if (next != null && next.status() == DungeonEncounterStatus.PENDING) {
                encs.put(nextId, next.activate());
            }
            return state
                .withEncounters(Map.copyOf(encs))
                .withCombat(state.combat()
                    .withActiveEncounterId(nextId)
                    .withGauntletQueue(remaining));
        }

        DungeonResources healed = state.resources()
            .heal(state.resources().healthCap())
            .addShield();
        DungeonProgress withElite = state.progress().withElitesCleared(state.progress().elitesCleared() + 1);
        DungeonResources scored = healed.addScore(ELITE_CLEAR_SCORE);

        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        DungeonRoom cleared = room.withCleared(true);
        rooms.put(cleared.id(), cleared);
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());

        PendingRelicPick pick = new PendingRelicPick(
            PendingPickType.ELITE, cleared.id(),
            cleared.eliteOffer() == null ? List.of() : cleared.eliteOffer().relics(),
            null);

        return state
            .withMap(nextMap)
            .withResources(scored)
            .withProgress(withElite)
            .withCombat(state.combat().exitCombat())
            .withLoadout(state.loadout().withPendingRelicPick(pick));
    }

    private DungeonSessionState resolveBoss(DungeonSessionState state) {
        int nextIndex = state.combat().bossIndex() + 1;
        if (nextIndex < state.bossEncounterIds().size()) {
            String nextBossId = state.bossEncounterIds().get(nextIndex);
            Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
            DungeonEncounter next = encs.get(nextBossId);
            if (next != null && next.status() == DungeonEncounterStatus.PENDING) {
                encs.put(nextBossId, next.activate());
            }
            return state
                .withEncounters(Map.copyOf(encs))
                .withCombat(state.combat()
                    .withBossIndex(nextIndex)
                    .withActiveEncounterId(nextBossId));
        }
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        DungeonRoom bossRoom = rooms.get(state.map().bossRoomId());
        if (bossRoom != null) {
            rooms.put(bossRoom.id(), bossRoom.withCleared(true));
        }
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());
        return state
            .withMap(nextMap)
            .withCombat(state.combat().withBossIndex(nextIndex).withActiveEncounterId(null))
            .withWon(true);
    }

    private DungeonSessionState clearCombatRoom(DungeonSessionState state) {
        DungeonRoom room = state.currentRoom();
        if (room == null) return state;
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        rooms.put(room.id(), room.withCleared(true));
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());
        DungeonResources withWarBanner =
            state.loadout().ownedRelics().contains(RelicId.WAR_BANNER)
                ? state.resources().addShield()
                : state.resources();
        return state
            .withMap(nextMap)
            .withResources(withWarBanner)
            .withCombat(state.combat().withActiveEncounterId(null));
    }
}
