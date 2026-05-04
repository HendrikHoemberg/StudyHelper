package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record TestQuestion(
    QuestionType type,
    String questionText,
    List<String> options,
    int correctOptionIndex
) {}
