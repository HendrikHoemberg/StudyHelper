package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record DungeonCombat(
    String activeEncounterId,
    List<String> gauntletQueue,
    int bossIndex,
    String activeRevenantId
) implements Serializable {

    public DungeonCombat {
        gauntletQueue = gauntletQueue == null ? List.of() : List.copyOf(gauntletQueue);
    }

    public static DungeonCombat empty() {
        return new DungeonCombat(null, List.of(), 0, null);
    }

    public DungeonCombat withActiveEncounterId(String id) {
        return new DungeonCombat(id, gauntletQueue, bossIndex, activeRevenantId);
    }
    public DungeonCombat withGauntletQueue(List<String> q) {
        return new DungeonCombat(activeEncounterId, q, bossIndex, activeRevenantId);
    }
    public DungeonCombat withBossIndex(int idx) {
        return new DungeonCombat(activeEncounterId, gauntletQueue, idx, activeRevenantId);
    }

    public DungeonCombat withActiveRevenantId(String id) {
        return new DungeonCombat(activeEncounterId, gauntletQueue, bossIndex, id);
    }

    public DungeonCombat advanceBoss() {
        return withBossIndex(bossIndex + 1);
    }

    public DungeonCombat exitCombat() {
        return new DungeonCombat(null, List.of(), bossIndex, null);
    }
}
