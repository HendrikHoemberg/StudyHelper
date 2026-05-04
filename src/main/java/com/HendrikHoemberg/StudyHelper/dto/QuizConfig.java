package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record QuizConfig(
    List<Long> selectedDeckIds,
    List<Long> selectedFileIds,
    int questionCount,
    QuizQuestionMode questionMode,
    Difficulty difficulty
) {}
