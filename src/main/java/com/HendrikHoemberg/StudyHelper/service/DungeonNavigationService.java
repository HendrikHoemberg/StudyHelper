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
        if (state.activeEncounterId() != null) return new MoveResult(state, null);

        DungeonRoom current = state.currentRoom();
        if (current == null) return new MoveResult(state, null);

        String nextRoomId = current.doors().get(direction);
        if (nextRoomId == null) return new MoveResult(state, null);

        DungeonRoom nextRoom = state.map().room(nextRoomId);
        if (nextRoom == null) return new MoveResult(state, null);

        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        boolean firstEntry = !nextRoom.visited();
        nextRoom = nextRoom.withVisited(true);

        DungeonSessionState working = state;

        // First-entry effect for HEAL
        if (firstEntry && nextRoom.type() == RoomType.HEAL) {
            int healed = working.healthCap() - working.health();
            if (healed > 0) {
                working = DungeonDamage.heal(working, healed);
            }
            if (working.shields() < working.shieldCap()) {
                working = withShields(working, working.shields() + 1);
            }
            nextRoom = nextRoom.withCleared(true);
        }

        rooms.put(nextRoomId, nextRoom);
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());

        DungeonSessionState moved = withMapAndPosition(working, nextMap, nextRoomId);

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

    private DungeonSessionState withMapAndPosition(DungeonSessionState s, DungeonMap map, String roomId) {
        return new DungeonSessionState(
            s.config(), map, roomId,
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }

    private DungeonSessionState withShields(DungeonSessionState s, int shields) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            s.health(), s.healthCap(), shields, s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }
}
