package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record TestSourceGroup(
    Long folderId,
    String folderName,
    String folderPath,
    String folderColor,
    String folderIcon,
    int totalSourceCount,
    int selectableSourceCount,
    List<StudyDeckOption> decks,
    List<TestFileOption> files,
    List<TestSourceGroup> subGroups,
    boolean isSelected,
    boolean isIndeterminate
) implements Serializable {
}
