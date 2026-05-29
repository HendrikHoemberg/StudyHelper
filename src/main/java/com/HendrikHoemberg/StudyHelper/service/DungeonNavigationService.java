package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DungeonNavigationService {

    public ActionResult move(DungeonSessionState state, DungeonDirection direction) {
        if (state.isComplete()) return ActionResult.success(state);
        if (state.combat().activeEncounterId() != null) return ActionResult.success(state);
        if (state.loadout().pendingRelicPick() != null) return ActionResult.success(state);

        DungeonRoom current = state.currentRoom();
        if (current == null) return ActionResult.failure(state, "dungeon.error.noRoom");

        String nextRoomId = current.doors().get(direction);
        if (nextRoomId == null) return ActionResult.failure(state, "dungeon.error.wall");

        DungeonRoom nextRoom = state.map().room(nextRoomId);
        if (nextRoom == null) return ActionResult.failure(state, "dungeon.error.noRoom");

        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());

        if (current.type() == RoomType.HEAL && !current.cleared()) {
            current = current.withCleared(true);
            rooms.put(current.id(), current);
        }

        boolean firstEntry = !nextRoom.visited();
        nextRoom = nextRoom.withVisited(true);

        DungeonSessionState working = state;

        if (firstEntry && nextRoom.type() == RoomType.HEAL) {
            DungeonResources healed = working.resources().heal(working.resources().healthCap() - working.resources().health());
            DungeonResources withShield = healed.addShield();
            working = working.withResources(withShield);
        }

        rooms.put(nextRoomId, nextRoom);
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());

        return ActionResult.success(working.withMap(nextMap).withCurrentRoomId(nextRoomId));
    }
}
