package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record TestQuestion(
    String questionText,
    List<String> options,
    int correctOptionIndex
) {}
