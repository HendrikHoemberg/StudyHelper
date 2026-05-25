package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record StudySessionStats(
    int totalAnswered,
    int totalCards,
    int correctAnswers,
    int incorrectAnswers,
    int percentage,
    int againCount,
    int hardCount,
    int goodCount,
    int easyCount
) implements Serializable {
    public StudySessionStats(
        int totalAnswered,
        int totalCards,
        int correctAnswers,
        int incorrectAnswers,
        int percentage
    ) {
        this(totalAnswered, totalCards, correctAnswers, incorrectAnswers, percentage,
             incorrectAnswers, 0, correctAnswers, 0);
    }

    public int againPercent() {
        return totalAnswered == 0 ? 0 : (int) Math.round((againCount * 100.0) / totalAnswered);
    }

    public int hardPercent() {
        return totalAnswered == 0 ? 0 : (int) Math.round((hardCount * 100.0) / totalAnswered);
    }

    public int goodPercent() {
        return totalAnswered == 0 ? 0 : (int) Math.round((goodCount * 100.0) / totalAnswered);
    }

    public int easyPercent() {
        return totalAnswered == 0 ? 0 : (int) Math.round((easyCount * 100.0) / totalAnswered);
    }
}
