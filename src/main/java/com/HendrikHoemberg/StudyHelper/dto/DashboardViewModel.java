package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;
import java.util.Optional;

public record DashboardViewModel(
    String greetingName,
    Optional<SavedSessionSummary> resume,
    long reviewMistakesCount,
    List<DashboardDeckSummary> pinnedDecks,
    List<DashboardDeckSummary> recentDecks,
    List<StudyLogSummary> recentActivity
) {}
