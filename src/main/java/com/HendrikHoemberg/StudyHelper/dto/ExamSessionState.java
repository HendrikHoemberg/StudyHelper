package com.HendrikHoemberg.StudyHelper.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExamSessionState(
    ExamConfig config,
    List<ExamQuestion> questions,
    Map<Integer, String> answers,
    Instant startedAt,
    String sourceSummary
) {
}
