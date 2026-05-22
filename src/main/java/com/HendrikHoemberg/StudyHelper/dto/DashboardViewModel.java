package com.HendrikHoemberg.StudyHelper.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record DashboardViewModel(
    String greetingName,
    Optional<SavedSessionSummary> resume,
    List<DashboardDeckSummary> pinnedDecks,
    List<DashboardDeckSummary> recentDecks,
    int streakDays,
    long dueTodayCount,
    long dueTodaySessionCount,
    int todayMinutes,
    int dailyMinuteGoal,
    Integer todayAccuracyPercent,
    int cardsReviewedToday,
    int weeklyMinutes,
    List<HeatmapEntry> heatmap,
    int heatmapTotalSessions
) {
    public record HeatmapEntry(LocalDate date, int sessions, int level, boolean today) {}
}
