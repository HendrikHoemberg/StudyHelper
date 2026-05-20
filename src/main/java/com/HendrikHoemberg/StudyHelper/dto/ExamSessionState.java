package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExamSessionState(
    ExamConfig config,
    List<ExamQuestion> questions,
    Map<Integer, String> answers,
    Instant resumedAt,
    long elapsedBeforeResume,
    String sourceSummary
) implements Serializable {

    public long totalElapsedSeconds(Instant now) {
        return elapsedBeforeResume + Math.max(0, Duration.between(resumedAt, now).toSeconds());
    }
}
