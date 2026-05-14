package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record StudyDeckOption(
    Long deckId,
    String deckName,
    Long folderId,
    String folderPath,
    String folderColorHex,
    String deckColorHex,
    String deckIconName,
    int cardCount,
    int usableCardCount
) implements Serializable {
}
