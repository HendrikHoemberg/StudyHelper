package com.HendrikHoemberg.StudyHelper.dto;

import java.util.Map;

public record MinimapRoom(
    String id,
    String type,
    int gridX,
    int gridY,
    boolean visited,
    boolean cleared,
    boolean isCurrent,
    Map<String, String> doors
) {}
