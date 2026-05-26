package com.HendrikHoemberg.StudyHelper.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;

public record DungeonPosition(int x, int y) implements Serializable {
    public DungeonPosition move(DungeonDirection direction) {
        return new DungeonPosition(x + direction.dx(), y + direction.dy());
    }

    @JsonValue
    public String toJsonValue() {
        return x + "," + y;
    }

    @JsonCreator
    public static DungeonPosition fromJsonValue(String value) {
        String[] parts = value.split(",");
        return new DungeonPosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
