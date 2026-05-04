package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record ExamConfig(
    List<Long> deckIds,
    List<Long> fileIds,
    ExamQuestionSize size,
    int count,
    Integer timerMinutes,
    ExamLayout layout
) {
}
