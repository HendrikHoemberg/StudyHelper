package com.HendrikHoemberg.StudyHelper.dto;

public record DashboardDeckSummary(
    Long deckId,
    String deckName,
    String folderPath,
    String colorHex,
    String iconName,
    long totalCards,
    long masteredCards,
    boolean pinned,
    String tagCode
) {}
