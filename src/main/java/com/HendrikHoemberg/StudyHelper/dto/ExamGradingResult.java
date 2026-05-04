package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record ExamGradingResult(
    List<PerQuestion> perQuestion,
    Overall overall
) {
    public record PerQuestion(int scorePercent, String feedback) {}
    public record Overall(
        int scorePercent,
        List<String> strengths,
        List<String> weaknesses,
        List<String> topicsToRevisit,
        List<String> suggestedNextSteps
    ) {}
}
