package com.HendrikHoemberg.StudyHelper.dto;

import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;

import java.time.Instant;

public record SavedSessionSummary(
    SavedSessionType type,
    String title,
    String progressLabel,
    Instant updatedAt,
    String resumeUrl
) {
}
