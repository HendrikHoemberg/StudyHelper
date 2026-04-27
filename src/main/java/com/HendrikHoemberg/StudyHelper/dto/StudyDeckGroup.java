package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record StudyDeckGroup(
    String folderPath,
    List<StudyDeckOption> decks
) implements Serializable {
}
