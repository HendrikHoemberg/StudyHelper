package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record QuizQuestion(
    QuestionType type,
    String questionText,
    List<String> options,
    List<Integer> correctOptionIndices
) {}
