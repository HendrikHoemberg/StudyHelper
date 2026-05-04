package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record ExamReport(
    int scorePercent,
    List<String> strengths,
    List<String> weaknesses,
    List<String> topicsToRevisit,
    List<String> suggestedNextSteps
) {
}
