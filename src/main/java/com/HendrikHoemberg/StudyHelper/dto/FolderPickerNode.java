package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record FolderPickerNode(
    Long id,
    String name,
    String colorHex,
    String iconName,
    List<FolderPickerNode> children
) implements Serializable {
}
