package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record DungeonMap(
    int width,
    int height,
    DungeonPosition entrance,
    DungeonPosition boss,
    Map<DungeonPosition, DungeonTile> tiles,
    Map<String, List<String>> gauntletGroups
) implements Serializable {

    public DungeonMap(int width, int height, DungeonPosition entrance, DungeonPosition boss,
                       Map<DungeonPosition, DungeonTile> tiles) {
        this(width, height, entrance, boss, tiles, Map.of());
    }

    public DungeonTile tileAt(DungeonPosition position) {
        return tiles.get(position);
    }

    public boolean isInside(DungeonPosition position) {
        return position.x() >= 0 && position.y() >= 0 && position.x() < width && position.y() < height;
    }
}
