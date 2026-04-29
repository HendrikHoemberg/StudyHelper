package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record SidebarFolderNode(
    Long id,
    String name,
    String colorHex,
    int deckCount,
    List<SidebarFolderNode> children
) {}
