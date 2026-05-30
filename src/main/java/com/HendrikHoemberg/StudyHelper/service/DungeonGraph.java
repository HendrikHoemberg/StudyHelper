package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonDirection;
import com.HendrikHoemberg.StudyHelper.dto.DungeonMap;
import com.HendrikHoemberg.StudyHelper.dto.DungeonRoom;

import java.util.*;

public final class DungeonGraph {

    private DungeonGraph() {}

    public static String nextStepToward(DungeonMap map, String fromId, String toId) {
        if (map == null || fromId == null || toId == null || fromId.equals(toId))
            return fromId;
        DungeonRoom fromRoom = map.room(fromId);
        if (fromRoom == null) return fromId;

        Map<String, String> firstHop = new HashMap<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(fromId);

        for (DungeonDirection d : DungeonDirection.values()) {
            String n = fromRoom.doors().get(d);
            if (n == null || !seen.add(n)) continue;
            firstHop.put(n, n);
            if (n.equals(toId)) return n;
            queue.add(n);
        }

        while (!queue.isEmpty()) {
            String node = queue.poll();
            DungeonRoom room = map.room(node);
            if (room == null) continue;
            for (DungeonDirection d : DungeonDirection.values()) {
                String n = room.doors().get(d);
                if (n == null || !seen.add(n)) continue;
                firstHop.put(n, firstHop.get(node));
                if (n.equals(toId)) return firstHop.get(n);
                queue.add(n);
            }
        }

        return fromId;
    }
}
