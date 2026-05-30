package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DungeonMinimapBuilder {

    public List<MinimapRoom> build(DungeonSessionState state) {
        boolean hasMapSense = state.loadout().ownedRelics().contains(RelicId.MAP_SENSE);

        Set<String> visitedIds = new HashSet<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            if (r.visited()) visitedIds.add(r.id());
        }
        Set<String> adjacentToVisited = new HashSet<>();
        for (String id : visitedIds) {
            DungeonRoom r = state.map().room(id);
            for (String neighbor : r.doors().values()) {
                if (!visitedIds.contains(neighbor)) adjacentToVisited.add(neighbor);
            }
        }

        Set<String> revenantRooms = new HashSet<>();
        for (Revenant rev : state.revenants()) revenantRooms.add(rev.currentRoomId());

        Set<String> revealed = revealedRoomIds(state);

        List<MinimapRoom> result = new ArrayList<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            boolean isVisited = visitedIds.contains(r.id());
            boolean isAdjacent = adjacentToVisited.contains(r.id());
            boolean visible = isVisited || isAdjacent || hasMapSense;
            if (!visible) continue;

            boolean revealedType = revealed.contains(r.id());

            Map<String, String> doors = new LinkedHashMap<>();
            for (Map.Entry<DungeonDirection, String> e : r.doors().entrySet()) {
                doors.put(e.getKey().name(), e.getValue());
            }

            result.add(new MinimapRoom(
                r.id(),
                revealedType ? r.type().name() : "UNKNOWN",
                r.gridPos().x(),
                r.gridPos().y(),
                isVisited,
                r.cleared(),
                r.id().equals(state.currentRoomId()),
                doors,
                revealedType && revenantRooms.contains(r.id())));
        }
        return result;
    }

    public static Set<String> revealedRoomIds(DungeonSessionState state) {
        boolean hasCompass = state.loadout().ownedRelics().contains(RelicId.COMPASS);
        boolean hasMapSense = state.loadout().ownedRelics().contains(RelicId.MAP_SENSE);

        Set<String> visited = new HashSet<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            if (r.visited()) visited.add(r.id());
        }
        Set<String> adjacent = new HashSet<>();
        for (String id : visited) {
            DungeonRoom r = state.map().room(id);
            for (String neighbor : r.doors().values()) {
                if (!visited.contains(neighbor)) adjacent.add(neighbor);
            }
        }
        Set<String> revealed = new HashSet<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            boolean isVisited = visited.contains(r.id());
            boolean isAdjacent = adjacent.contains(r.id());
            if (isVisited || (isAdjacent && hasCompass) || hasMapSense) revealed.add(r.id());
        }
        return revealed;
    }
}
