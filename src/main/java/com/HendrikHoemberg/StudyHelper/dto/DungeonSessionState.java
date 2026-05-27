package com.HendrikHoemberg.StudyHelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record DungeonSessionState(
    DungeonConfig config,
    DungeonMap map,
    String currentRoomId,
    Map<String, DungeonEncounter> encounters,
    List<String> bossEncounterIds,
    int bossIndex,
    String activeEncounterId,
    int health,
    int healthCap,
    int shields,
    int shieldCap,
    int score,
    int answeredCount,
    int correctCount,
    boolean won,
    boolean defeated,
    int streak,
    List<String> gauntletQueue,
    int longestStreak,
    int elitesCleared,
    int shieldsUsed,
    int luckyCoinsConsumed,
    List<RelicId> ownedRelics,
    PendingRelicPick pendingRelicPick
) implements Serializable {

    public DungeonSessionState {
        ownedRelics = ownedRelics == null ? List.of() : List.copyOf(ownedRelics);
        gauntletQueue = gauntletQueue == null ? List.of() : List.copyOf(gauntletQueue);
        encounters = Map.copyOf(encounters);
        bossEncounterIds = List.copyOf(bossEncounterIds);
    }

    @JsonIgnore
    public boolean isComplete() {
        return won || defeated;
    }

    public DungeonEncounter activeEncounter() {
        return activeEncounterId == null ? null : encounters.get(activeEncounterId);
    }

    public DungeonRoom currentRoom() {
        return map.room(currentRoomId);
    }
}
