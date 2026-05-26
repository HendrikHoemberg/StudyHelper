package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record DungeonTile(
    DungeonPosition position,
    DungeonTileType type,
    boolean revealed,
    boolean explored,
    String encounterId
) implements Serializable {
    public boolean walkable() {
        return type != DungeonTileType.WALL && type != DungeonTileType.SECRET_WALL;
    }

    public DungeonTile reveal() {
        return new DungeonTile(position, type, true, explored, encounterId);
    }

    public DungeonTile explore() {
        return new DungeonTile(position, type, true, true, encounterId);
    }

    public DungeonTile withType(DungeonTileType nextType, String nextEncounterId) {
        return new DungeonTile(position, nextType, revealed, explored, nextEncounterId);
    }
}
