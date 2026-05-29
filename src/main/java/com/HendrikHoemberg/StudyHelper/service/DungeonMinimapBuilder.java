package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DungeonMinimapBuilder {

    public List<MinimapRoom> build(DungeonSessionState state) {
        boolean hasCompass = state.loadout().ownedRelics().contains(RelicId.COMPASS);
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

        List<MinimapRoom> result = new ArrayList<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            boolean isVisited = visitedIds.contains(r.id());
            boolean isAdjacent = adjacentToVisited.contains(r.id());
            boolean visible = isVisited || isAdjacent || hasMapSense;
            if (!visible) continue;

            boolean revealedType = isVisited || (isAdjacent && hasCompass) || hasMapSense;

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
                doors
            ));
        }
        return result;
    }
}
