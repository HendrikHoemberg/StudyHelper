package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record StudyCardView(
    Long cardId,
    String frontText,
    String backText,
    Long deckId,
    String deckName,
    String deckPathLabel,
    String frontImageUrl,
    String backImageUrl
) implements Serializable {
}
