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

        Set<String> secretRevealed = new HashSet<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            if (r.type() != RoomType.SECRET) continue;
            if (hasMapSense) {
                secretRevealed.add(r.id());
                continue;
            }
            int hostsVisited = 0;
            int hostsTotal = 0;
            for (DungeonRoom maybeHost : state.map().rooms().values()) {
                if (maybeHost.id().equals(r.id())) continue;
                GridPos a = r.gridPos();
                GridPos b = maybeHost.gridPos();
                if (Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) == 1) {
                    hostsTotal++;
                    if (visitedIds.contains(maybeHost.id())) hostsVisited++;
                }
            }
            if (hostsTotal > 0 && hostsVisited == hostsTotal) secretRevealed.add(r.id());
        }

        List<MinimapRoom> result = new ArrayList<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            boolean isVisited = visitedIds.contains(r.id());
            boolean isAdjacent = adjacentToVisited.contains(r.id());
            boolean isSecret = r.type() == RoomType.SECRET;
            boolean visible = isVisited || isAdjacent || (isSecret && secretRevealed.contains(r.id()));
            if (!visible) continue;

            boolean revealedType = isVisited
                || (isAdjacent && hasCompass)
                || (isSecret && secretRevealed.contains(r.id()));

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
