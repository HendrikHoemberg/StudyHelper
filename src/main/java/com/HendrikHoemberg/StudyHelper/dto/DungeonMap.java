package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.Map;

public record DungeonMap(
    int width,
    int height,
    DungeonPosition entrance,
    DungeonPosition boss,
    Map<DungeonPosition, DungeonTile> tiles
) implements Serializable {
    public DungeonTile tileAt(DungeonPosition position) {
        return tiles.get(position);
    }

    public boolean isInside(DungeonPosition position) {
        return position.x() >= 0 && position.y() >= 0 && position.x() < width && position.y() < height;
    }
}
