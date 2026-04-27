package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record StudySessionStats(
    int totalAnswered,
    int totalCards,
    int correctAnswers,
    int incorrectAnswers,
    int percentage
) implements Serializable {
}
