package com.HendrikHoemberg.StudyHelper.dto;

public record DungeonRunStats(
    DungeonMode mode,
    DungeonSize size,
    int totalPrompts,
    int answeredPrompts,
    int correctPrompts,
    int healthRemaining,
    boolean won,
    int longestStreak,
    int elitesCleared,
    int shieldsUsed,
    int relicsAcquired
) {}
