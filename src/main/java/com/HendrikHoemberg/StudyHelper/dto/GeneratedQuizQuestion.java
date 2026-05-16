package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record GeneratedQuizQuestion(
    String questionText,
    List<String> options,
    List<String> optionAnalysis,
    QuestionType type,
    List<Integer> correctOptionIndices
) {}
