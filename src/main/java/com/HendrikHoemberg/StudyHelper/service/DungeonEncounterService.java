package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.dto.RelicId;
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
        if (state.activeEncounterId() != null) return state;
        DungeonRoom room = state.map().room(roomId);
        if (room == null) return state;
        if (room.cleared()) return state;

        return switch (room.type()) {
            case COMBAT -> activateNormal(state, room.encounterId(), false);
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

    // ===== activation paths =====

    private DungeonSessionState activateNormal(DungeonSessionState state, String encounterId, boolean boss) {
        if (encounterId == null) return state;
        DungeonEncounter enc = state.encounters().get(encounterId);
        if (enc == null || enc.status() != DungeonEncounterStatus.PENDING) return state;
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
        encs.put(encounterId, enc.activate());
        return withEncountersAndActive(state, encs, encounterId, state.gauntletQueue());
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
        return withEncountersAndActive(state, encs, firstId, remaining);
    }

    private DungeonSessionState activateBoss(DungeonSessionState state) {
        if (state.bossEncounterIds().isEmpty()) return state;
        String bossId = state.bossEncounterIds().get(state.bossIndex());
        return activateNormal(state, bossId, true);
    }

    // ===== answer pipeline =====

    private DungeonSessionState processAnswer(DungeonSessionState state, DungeonEncounter encounter,
                                                List<Integer> answer, boolean correct) {
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
        encs.put(encounter.id(), encounter.clear(answer, correct));

        int answeredCount = state.answeredCount() + 1;
        int correctCount = state.correctCount() + (correct ? 1 : 0);
        int effectiveStreakThreshold = state.ownedRelics().contains(RelicId.SHARP_FOCUS) ? 2 : STREAK_FOR_SHIELD;
        int newStreak = correct ? state.streak() + 1 : 0;
        int newShields = state.shields();
        if (correct && newStreak % effectiveStreakThreshold == 0 && newShields < state.shieldCap()) {
            newShields++;
        }
        int newLongest = Math.max(state.longestStreak(), newStreak);
        int newScore = state.score();
        if (correct) {
            int base = encounter.boss() ? BOSS_PROMPT_SCORE : COMBAT_CLEAR_SCORE;
            int bonus = state.ownedRelics().contains(RelicId.LUCKY_CHARM) ? 5 : 0;
            newScore += base + bonus;
        }

        DungeonSessionState working = new DungeonSessionState(
            state.config(), state.map(), state.currentRoomId(),
            Map.copyOf(encs), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            state.health(), state.healthCap(), newShields, state.shieldCap(),
            newScore, answeredCount, correctCount,
            state.won(), state.defeated(),
            newStreak, state.gauntletQueue(),
            newLongest, state.elitesCleared(), state.shieldsUsed(),
            state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());

        if (!correct) {
            working = DungeonDamage.takeDamage(working, WRONG_ANSWER_DAMAGE);
        }

        // Elite-gauntlet branch
        if (isInGauntletRoom(working) || !working.gauntletQueue().isEmpty()) {
            return resolveGauntlet(working, encounter, correct);
        }

        // Boss branch
        if (encounter.boss() && !working.defeated()) {
            return resolveBoss(working);
        }

        // Normal combat: clear the room
        return clearCombatRoom(working);
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
            return withEncountersAndActive(state, reset, null, List.of());
        }

        if (!state.gauntletQueue().isEmpty()) {
            String nextId = state.gauntletQueue().get(0);
            List<String> remaining = new ArrayList<>(state.gauntletQueue().subList(1, state.gauntletQueue().size()));
            Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
            DungeonEncounter next = encs.get(nextId);
            if (next != null && next.status() == DungeonEncounterStatus.PENDING) {
                encs.put(nextId, next.activate());
            }
            return withEncountersAndActive(state, encs, nextId, remaining);
        }

        // Gauntlet fully cleared: full heal + shield + queue ELITE relic pick + clear room
        DungeonSessionState healed = DungeonDamage.heal(state, state.healthCap());
        int grantedShields = Math.min(state.shieldCap(), healed.shields() + 1);
        int newElites = healed.elitesCleared() + 1;
        int eliteScore = healed.score() + ELITE_CLEAR_SCORE;

        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(healed.map().rooms());
        DungeonRoom cleared = room.withCleared(true);
        rooms.put(cleared.id(), cleared);
        DungeonMap nextMap = new DungeonMap(
            rooms, healed.map().entranceRoomId(), healed.map().bossRoomId(), healed.map().lattice());

        PendingRelicPick pick = new PendingRelicPick(
            PendingPickType.ELITE, cleared.id(),
            cleared.eliteOffer() == null ? List.of() : cleared.eliteOffer().relics(),
            null);

        return new DungeonSessionState(
            healed.config(), nextMap, healed.currentRoomId(),
            healed.encounters(), healed.bossEncounterIds(), healed.bossIndex(),
            null,
            healed.health(), healed.healthCap(), grantedShields, healed.shieldCap(),
            eliteScore, healed.answeredCount(), healed.correctCount(),
            healed.won(), healed.defeated(),
            healed.streak(), List.of(),
            healed.longestStreak(), newElites, healed.shieldsUsed(),
            healed.luckyCoinsConsumed(), healed.ownedRelics(), pick);
    }

    private DungeonSessionState resolveBoss(DungeonSessionState state) {
        int nextIndex = state.bossIndex() + 1;
        if (nextIndex < state.bossEncounterIds().size()) {
            String nextBossId = state.bossEncounterIds().get(nextIndex);
            Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
            DungeonEncounter next = encs.get(nextBossId);
            if (next != null && next.status() == DungeonEncounterStatus.PENDING) {
                encs.put(nextBossId, next.activate());
            }
            return new DungeonSessionState(
                state.config(), state.map(), state.currentRoomId(),
                Map.copyOf(encs), state.bossEncounterIds(), nextIndex,
                nextBossId,
                state.health(), state.healthCap(), state.shields(), state.shieldCap(),
                state.score(), state.answeredCount(), state.correctCount(),
                state.won(), state.defeated(),
                state.streak(), state.gauntletQueue(),
                state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
                state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());
        }
        // Final boss prompt cleared — mark won and clear boss room
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        DungeonRoom bossRoom = rooms.get(state.map().bossRoomId());
        if (bossRoom != null) {
            rooms.put(bossRoom.id(), bossRoom.withCleared(true));
        }
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());
        return new DungeonSessionState(
            state.config(), nextMap, state.currentRoomId(),
            state.encounters(), state.bossEncounterIds(), nextIndex,
            null,
            state.health(), state.healthCap(), state.shields(), state.shieldCap(),
            state.score(), state.answeredCount(), state.correctCount(),
            true, state.defeated(),
            state.streak(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
            state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());
    }

    private DungeonSessionState clearCombatRoom(DungeonSessionState state) {
        DungeonRoom room = state.currentRoom();
        if (room == null) return state;
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        rooms.put(room.id(), room.withCleared(true));
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());
        int shieldsAfter = state.shields();
        if (state.ownedRelics().contains(RelicId.WAR_BANNER) && shieldsAfter < state.shieldCap()) {
            shieldsAfter++;
        }
        return new DungeonSessionState(
            state.config(), nextMap, state.currentRoomId(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            null,
            state.health(), state.healthCap(), shieldsAfter, state.shieldCap(),
            state.score(), state.answeredCount(), state.correctCount(),
            state.won(), state.defeated(),
            state.streak(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
            state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());
    }

    private DungeonSessionState withEncountersAndActive(DungeonSessionState s,
                                                           Map<String, DungeonEncounter> encs,
                                                           String activeId,
                                                           List<String> gauntletQueue) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            Map.copyOf(encs), s.bossEncounterIds(), s.bossIndex(),
            activeId,
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), gauntletQueue,
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }
}
