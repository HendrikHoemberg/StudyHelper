package com.HendrikHoemberg.StudyHelper.dto;

public enum StudyMode {
    FLASHCARDS(1),
    QUIZ(1),
    EXAM(1),
    DUNGEON(DungeonSize.SMALL.totalPrompts());

    private final int minimumCardsRequired;
    StudyMode(int minimumCardsRequired) { this.minimumCardsRequired = minimumCardsRequired; }
    public int minimumCardsRequired() { return minimumCardsRequired; }
}
