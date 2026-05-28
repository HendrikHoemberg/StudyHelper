package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DungeonNavigationService {

    public record MoveResult(DungeonSessionState state, String activatedEncounterRoomId) {}

    public MoveResult move(DungeonSessionState state, DungeonDirection direction) {
        if (state.isComplete()) return new MoveResult(state, null);
        if (state.combat().activeEncounterId() != null) return new MoveResult(state, null);

        DungeonRoom current = state.currentRoom();
        if (current == null) return new MoveResult(state, null);

        String nextRoomId = current.doors().get(direction);
        if (nextRoomId == null) return new MoveResult(state, null);

        DungeonRoom nextRoom = state.map().room(nextRoomId);
        if (nextRoom == null) return new MoveResult(state, null);

        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());

        // If leaving an uncleared HEAL room, mark it cleared now so it's empty next time
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

        DungeonSessionState moved = working.withMap(nextMap).withCurrentRoomId(nextRoomId);

        String encounterRoomToActivate = null;
        if (nextRoom.type() == RoomType.COMBAT
            || nextRoom.type() == RoomType.ELITE
            || nextRoom.type() == RoomType.BOSS) {
            if (!nextRoom.cleared()) {
                encounterRoomToActivate = nextRoomId;
            }
        }

        return new MoveResult(moved, encounterRoomToActivate);
    }


}
