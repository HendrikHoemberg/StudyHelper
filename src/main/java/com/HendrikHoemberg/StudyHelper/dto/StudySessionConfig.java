package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record StudySessionConfig(
    List<Long> selectedDeckIds,
    SessionMode sessionMode,
    DeckOrderMode deckOrderMode,
    boolean practice,
    int newCardsPerDay
) implements Serializable {
}
