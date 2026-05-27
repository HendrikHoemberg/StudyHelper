package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DungeonEncounterService {

    private static final int STREAK_FOR_SHIELD = 3;
    private static final int MAX_SHIELDS = 2;
    private static final int WRONG_ANSWER_DAMAGE = 1;

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

        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>(working.map().tiles());
        DungeonPosition playerPos = working.playerPosition();
        DungeonTile currentTile = tiles.get(playerPos);
        DungeonMap nextMap = working.map();
        if (currentTile != null
            && (currentTile.type() == DungeonTileType.ENCOUNTER || currentTile.type() == DungeonTileType.BOSS)
            && (!encounter.boss() || won)) {
            tiles.put(playerPos, currentTile.withType(DungeonTileType.FLOOR, null));
            nextMap = new DungeonMap(working.map().width(), working.map().height(),
                working.map().entrance(), working.map().boss(), Map.copyOf(tiles));
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
}
