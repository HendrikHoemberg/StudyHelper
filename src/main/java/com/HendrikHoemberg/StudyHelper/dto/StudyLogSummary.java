package com.HendrikHoemberg.StudyHelper.dto;

import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;

import java.time.LocalDateTime;

public record StudyLogSummary(
    SavedSessionType type,
    String title,
    int cardCount,
    int correctCount,
    LocalDateTime completedAt
) {}
