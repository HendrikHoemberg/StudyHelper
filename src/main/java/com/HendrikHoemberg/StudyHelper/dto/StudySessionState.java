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
    int incorrectAnswers
) implements Serializable {
}
