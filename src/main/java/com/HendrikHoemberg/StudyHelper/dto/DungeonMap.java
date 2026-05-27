package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.Map;

public record DungeonMap(
    Map<String, DungeonRoom> rooms,
    String entranceRoomId,
    String bossRoomId,
    int lattice
) implements Serializable {

    public DungeonMap {
        rooms = Map.copyOf(rooms);
    }

    public DungeonRoom room(String id) {
        return rooms.get(id);
    }
}
