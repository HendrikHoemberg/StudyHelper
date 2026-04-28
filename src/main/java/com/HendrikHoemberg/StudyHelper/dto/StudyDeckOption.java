package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record StudyDeckOption(
    Long deckId,
    String deckName,
    String folderPath,
    String folderColorHex,
    int cardCount
) implements Serializable {
}
