package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record SidebarFolderNode(
    Long id,
    String name,
    String colorHex,
    String iconName,
    int deckCount,
    List<SidebarFolderNode> children,
    boolean isActive,
    boolean isOpen
) {}
