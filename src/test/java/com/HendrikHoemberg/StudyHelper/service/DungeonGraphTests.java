package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonGraphTests {

    private DungeonMap lineMap() {
        DungeonRoom a = new DungeonRoom("a", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "b"), new GridPos(0, 0), true, true,
            null, List.of(), null, null, null, List.of());
        DungeonRoom b = new DungeonRoom("b", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "a", DungeonDirection.RIGHT, "c"),
            new GridPos(1, 0), false, false, "fc_0", List.of(), null, null, null, List.of());
        DungeonRoom c = new DungeonRoom("c", RoomType.BOSS,
            Map.of(DungeonDirection.LEFT, "b"), new GridPos(2, 0), false, false,
            null, List.of(), null, null, null, List.of());
        return new DungeonMap(Map.of("a", a, "b", b, "c", c), "a", "c", 3);
    }

    @Test
    void stepsOneRoomTowardTarget() {
        DungeonMap map = lineMap();
        assertThat(DungeonGraph.nextStepToward(map, "a", "c")).isEqualTo("b");
        assertThat(DungeonGraph.nextStepToward(map, "c", "a")).isEqualTo("b");
        assertThat(DungeonGraph.nextStepToward(map, "b", "c")).isEqualTo("c");
    }

    @Test
    void staysPutWhenAlreadyAtTarget() {
        assertThat(DungeonGraph.nextStepToward(lineMap(), "b", "b")).isEqualTo("b");
    }

    @Test
    void staysPutWhenTargetUnreachable() {
        DungeonMap map = lineMap();
        assertThat(DungeonGraph.nextStepToward(map, "a", "ghost")).isEqualTo("a");
    }
}
