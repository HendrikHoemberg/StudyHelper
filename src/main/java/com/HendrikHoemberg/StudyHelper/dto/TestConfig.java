package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record TestConfig(
    List<Long> selectedDeckIds,
    List<Long> selectedFileIds,
    int questionCount,
    TestQuestionMode questionMode,
    Difficulty difficulty
) {}
