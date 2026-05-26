package com.HendrikHoemberg.StudyHelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DungeonSessionState(
    DungeonConfig config,
    DungeonMap map,
    DungeonPosition playerPosition,
    Map<String, DungeonEncounter> encounters,
    List<String> bossEncounterIds,
    int bossIndex,
    String activeEncounterId,
    int health,
    int score,
    int answeredCount,
    int correctCount,
    Set<DungeonPosition> visibleTiles,
    boolean won,
    boolean defeated
) implements Serializable {
    @JsonIgnore
    public boolean isComplete() {
        return won || defeated;
    }

    public DungeonEncounter activeEncounter() {
        return activeEncounterId == null ? null : encounters.get(activeEncounterId);
    }
}
