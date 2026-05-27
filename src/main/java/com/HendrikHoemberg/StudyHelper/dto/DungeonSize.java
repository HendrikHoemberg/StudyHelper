package com.HendrikHoemberg.StudyHelper.dto;

import java.util.Arrays;
import java.util.List;

public enum DungeonSize {
    SMALL(10, 6, 2),
    MEDIUM(16, 9, 3),
    LARGE(29, 15, 5);

    private final int totalPrompts;
    private final int normalEncounterCount;
    private final int bossPromptCount;

    DungeonSize(int totalPrompts, int normalEncounterCount, int bossPromptCount) {
        this.totalPrompts = totalPrompts;
        this.normalEncounterCount = normalEncounterCount;
        this.bossPromptCount = bossPromptCount;
    }

    public int totalPrompts() {
        return totalPrompts;
    }

    public int normalEncounterCount() {
        return normalEncounterCount;
    }

    public int bossPromptCount() {
        return bossPromptCount;
    }

    public boolean isAvailableFor(int usableItems) {
        return usableItems >= totalPrompts;
    }

    public static List<DungeonSize> availableForUsableItems(int usableItems) {
        return Arrays.stream(values())
            .filter(size -> size.isAvailableFor(usableItems))
            .toList();
    }
}
