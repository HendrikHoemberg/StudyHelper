package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record StudyCardView(
    Long cardId,
    String frontText,
    String backText,
    Long deckId,
    String deckName,
    String deckPathLabel,
    String deckColorHex,
    String deckIconName,
    String frontImageUrl,
    String backImageUrl
) implements Serializable {
}
