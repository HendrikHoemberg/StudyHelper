package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record FlashcardGenerationPlan(
    List<FlashcardChunk> chunks,
    int requestCost,
    int estimatedSecondsLow,
    int estimatedSecondsHigh,
    int estimatedCardsLow,
    int estimatedCardsHigh,
    FlashcardGenerationRisk risk
) {
    public boolean highRisk() {
        return risk == FlashcardGenerationRisk.HIGH;
    }
}
