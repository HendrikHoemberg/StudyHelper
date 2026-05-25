package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record StudySessionState(
    StudySessionConfig config,
    Map<Long, List<StudyCardView>> cardsByDeck,
    List<StudyCardView> queue,
    int currentIndex,
    int totalAnswered,
    int correctAnswers,
    int incorrectAnswers,
    List<Long> incorrectCardIds,
    int againCount,
    int hardCount,
    int goodCount,
    int easyCount
) implements Serializable {
    public StudySessionState(
        StudySessionConfig config,
        Map<Long, List<StudyCardView>> cardsByDeck,
        List<StudyCardView> queue,
        int currentIndex,
        int totalAnswered,
        int correctAnswers,
        int incorrectAnswers,
        List<Long> incorrectCardIds
    ) {
        this(config, cardsByDeck, queue, currentIndex, totalAnswered, correctAnswers, incorrectAnswers, incorrectCardIds,
             incorrectAnswers, 0, correctAnswers, 0);
    }
}
