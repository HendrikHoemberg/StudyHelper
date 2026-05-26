package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record DungeonConfig(
    DungeonMode mode,
    DungeonSize size,
    List<Long> selectedDeckIds,
    QuizQuestionMode quizQuestionMode,
    Difficulty difficulty,
    String additionalInstructions
) implements Serializable {
    public DungeonConfig {
        selectedDeckIds = selectedDeckIds == null ? List.of() : List.copyOf(selectedDeckIds);
        additionalInstructions = additionalInstructions == null ? "" : additionalInstructions;
    }
}
