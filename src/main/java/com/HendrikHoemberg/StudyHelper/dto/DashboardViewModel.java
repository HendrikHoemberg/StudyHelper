package com.HendrikHoemberg.StudyHelper.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record DashboardViewModel(
    String greetingName,
    Optional<SavedSessionSummary> resume,
    long reviewMistakesCount,
    List<DashboardDeckSummary> pinnedDecks,
    List<DashboardDeckSummary> recentDecks,
    int streakDays,
    long dueTodayCount,
    int todayMinutes,
    int dailyMinuteGoal,
    int weeklyAccuracyPercent,
    int cardsReviewedToday,
    int weeklyMinutes,
    List<HeatmapEntry> heatmap,
    int heatmapTotalSessions
) {
    public record HeatmapEntry(LocalDate date, int sessions, int level, boolean today) {}
}
