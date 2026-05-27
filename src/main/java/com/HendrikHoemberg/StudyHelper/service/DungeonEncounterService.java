package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DungeonEncounterService {

    private static final int STREAK_FOR_SHIELD = 3;
    private static final int MAX_SHIELDS = 2;
    private static final int WRONG_ANSWER_DAMAGE = 1;

    public DungeonSessionState activateElite(DungeonSessionState state, String firstEncounterId) {
        List<String> group = state.map().gauntletGroups().get(firstEncounterId);
        if (group == null || group.isEmpty()) return state;
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
        DungeonEncounter first = encs.get(firstEncounterId);
        if (first == null || first.status() != DungeonEncounterStatus.PENDING) return state;
        encs.put(firstEncounterId, first.activate());
        List<String> remaining = new ArrayList<>(group.subList(1, group.size()));
        return new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            Map.copyOf(encs), state.bossEncounterIds(), state.bossIndex(),
            firstEncounterId,
            state.health(), state.score(),
            state.answeredCount(), state.correctCount(),
            state.visibleTiles(),
            state.won(), state.defeated(),
            state.streak(), state.shields(), List.copyOf(remaining),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed());
    }

    public DungeonSessionState activate(DungeonSessionState state, String encounterId, boolean boss) {
        if (encounterId == null) return state;
        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>(state.encounters());
        DungeonEncounter enc = encounters.get(encounterId);
        if (enc == null || enc.status() != DungeonEncounterStatus.PENDING) return state;
        encounters.put(encounterId, enc.activate());

        String activeId = encounterId;
        if (boss && !state.bossEncounterIds().isEmpty()) {
            activeId = state.bossEncounterIds().get(0);
        }

        return new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            Map.copyOf(encounters), state.bossEncounterIds(), state.bossIndex(),
            activeId,
            state.health(), state.score(),
            state.answeredCount(), state.correctCount(),
            state.visibleTiles(),
            state.won(), state.defeated(),
            state.streak(), state.shields(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed());
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
        List<Integer> safe = selectedOptions == null ? List.of() : selectedOptions;
        QuizQuestion q = enc.quizQuestion();
        boolean correct = new HashSet<>(safe).equals(new HashSet<>(q.correctOptionIndices()));
        return applyAnswer(state, enc, safe, correct);
    }

    private DungeonSessionState applyAnswer(DungeonSessionState state, DungeonEncounter encounter,
                                             List<Integer> answer, boolean correct) {
        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>(state.encounters());
        encounters.put(encounter.id(), encounter.clear(answer, correct));

        int answeredCount = state.answeredCount() + 1;
        int correctCount = state.correctCount() + (correct ? 1 : 0);

        int newStreak = correct ? state.streak() + 1 : 0;
        int newShields = state.shields();
        if (correct && newStreak % STREAK_FOR_SHIELD == 0 && newShields < MAX_SHIELDS) {
            newShields++;
        }
        int newLongest = Math.max(state.longestStreak(), newStreak);

        DungeonSessionState working = new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            Map.copyOf(encounters), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            state.health(), state.score(),
            answeredCount, correctCount,
            state.visibleTiles(),
            state.won(), state.defeated(),
            newStreak, newShields, state.gauntletQueue(),
            newLongest, state.elitesCleared(), state.shieldsUsed());

        if (!correct) {
            working = DungeonDamage.takeDamage(working, WRONG_ANSWER_DAMAGE);
        }

        // Elite gauntlet handling
        if (!state.gauntletQueue().isEmpty() || isPartOfActiveGauntlet(state, encounter)) {
            if (!correct) {
                return new DungeonSessionState(
                    working.config(), working.map(), working.playerPosition(),
                    working.encounters(), working.bossEncounterIds(), working.bossIndex(),
                    null,
                    working.health(), working.score(),
                    working.answeredCount(), working.correctCount(),
                    working.visibleTiles(),
                    working.won(), working.defeated(),
                    working.streak(), working.shields(), List.of(),
                    working.longestStreak(), working.elitesCleared(), working.shieldsUsed());
            }
            if (!working.gauntletQueue().isEmpty()) {
                String nextId = working.gauntletQueue().get(0);
                List<String> remaining = working.gauntletQueue().subList(1, working.gauntletQueue().size());
                Map<String, DungeonEncounter> encs2 = new LinkedHashMap<>(working.encounters());
                DungeonEncounter next = encs2.get(nextId);
                if (next != null && next.status() == DungeonEncounterStatus.PENDING) {
                    encs2.put(nextId, next.activate());
                }
                return new DungeonSessionState(
                    working.config(), working.map(), working.playerPosition(),
                    Map.copyOf(encs2), working.bossEncounterIds(), working.bossIndex(),
                    nextId,
                    working.health(), working.score(),
                    working.answeredCount(), working.correctCount(),
                    working.visibleTiles(),
                    working.won(), working.defeated(),
                    working.streak(), working.shields(), List.copyOf(remaining),
                    working.longestStreak(), working.elitesCleared(), working.shieldsUsed());
            }
            DungeonSessionState rewarded = DungeonDamage.heal(working, DungeonDamage.STARTING_HEALTH_CAP);
            int grantedShields = Math.min(MAX_SHIELDS, rewarded.shields() + 1);
            int newElites = rewarded.elitesCleared() + 1;
            Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>(rewarded.map().tiles());
            DungeonPosition pos = rewarded.playerPosition();
            DungeonTile cur = tiles.get(pos);
            if (cur != null && cur.type() == DungeonTileType.ELITE) {
                tiles.put(pos, cur.withType(DungeonTileType.FLOOR, null));
            }
            DungeonMap nextMap = new DungeonMap(rewarded.map().width(), rewarded.map().height(),
                rewarded.map().entrance(), rewarded.map().boss(),
                Map.copyOf(tiles), rewarded.map().gauntletGroups());
            return new DungeonSessionState(
                rewarded.config(), nextMap, rewarded.playerPosition(),
                rewarded.encounters(), rewarded.bossEncounterIds(), rewarded.bossIndex(),
                null,
                rewarded.health(), rewarded.score(),
                rewarded.answeredCount(), rewarded.correctCount(),
                rewarded.visibleTiles(),
                rewarded.won(), rewarded.defeated(),
                rewarded.streak(), grantedShields, List.of(),
                rewarded.longestStreak(), newElites, rewarded.shieldsUsed());
        }

        // Original boss/normal handling
        String nextActiveId = null;
        int bossIndex = working.bossIndex();
        boolean won = working.won();

        if (encounter.boss() && !working.defeated()) {
            bossIndex++;
            if (bossIndex < working.bossEncounterIds().size()) {
                String nextBossId = working.bossEncounterIds().get(bossIndex);
                Map<String, DungeonEncounter> encs2 = new LinkedHashMap<>(working.encounters());
                DungeonEncounter nextBoss = encs2.get(nextBossId);
                if (nextBoss != null && nextBoss.status() == DungeonEncounterStatus.PENDING) {
                    encs2.put(nextBossId, nextBoss.activate());
                    working = new DungeonSessionState(
                        working.config(), working.map(), working.playerPosition(),
                        Map.copyOf(encs2), working.bossEncounterIds(), bossIndex,
                        nextBossId,
                        working.health(), working.score(),
                        working.answeredCount(), working.correctCount(),
                        working.visibleTiles(),
                        working.won(), working.defeated(),
                        working.streak(), working.shields(), working.gauntletQueue(),
                        working.longestStreak(), working.elitesCleared(), working.shieldsUsed());
                    nextActiveId = nextBossId;
                }
            } else {
                won = true;
            }
        }

        Map<DungeonPosition, DungeonTile> tiles2 = new LinkedHashMap<>(working.map().tiles());
        DungeonPosition playerPos = working.playerPosition();
        DungeonTile currentTile = tiles2.get(playerPos);
        DungeonMap nextMap = working.map();
        if (currentTile != null
            && (currentTile.type() == DungeonTileType.ENCOUNTER || currentTile.type() == DungeonTileType.BOSS)
            && (!encounter.boss() || won)) {
            tiles2.put(playerPos, currentTile.withType(DungeonTileType.FLOOR, null));
            nextMap = new DungeonMap(working.map().width(), working.map().height(),
                working.map().entrance(), working.map().boss(), Map.copyOf(tiles2));
        }

        return new DungeonSessionState(
            working.config(), nextMap, working.playerPosition(),
            working.encounters(), working.bossEncounterIds(), bossIndex,
            nextActiveId,
            working.health(), working.score(),
            working.answeredCount(), working.correctCount(),
            working.visibleTiles(),
            won, working.defeated(),
            working.streak(), working.shields(), working.gauntletQueue(),
            working.longestStreak(), working.elitesCleared(), working.shieldsUsed());
    }

    private boolean isPartOfActiveGauntlet(DungeonSessionState state, DungeonEncounter encounter) {
        return state.map().gauntletGroups().values().stream()
            .anyMatch(group -> group.contains(encounter.id()));
    }
}
