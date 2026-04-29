package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record StudyDeckGroup(
    Long folderId,
    String folderName,
    String folderPath,
    List<StudyDeckOption> decks,
    List<StudyDeckGroup> subGroups
) implements Serializable {
}
