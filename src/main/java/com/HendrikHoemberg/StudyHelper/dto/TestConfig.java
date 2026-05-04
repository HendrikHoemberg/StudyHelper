package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record TestConfig(
    List<Long> selectedDeckIds,
    int questionCount
) {}
