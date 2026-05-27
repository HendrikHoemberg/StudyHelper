package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DungeonNavigationService {

    private static final int TREASURE_SCORE = 50;
    private static final int SECRET_WALL_SCORE = 25;
    private static final int HEAL_AMOUNT = 1;

    public record MoveResult(DungeonSessionState state, DungeonTileType landedTileType, String encounterId) {}

    public MoveResult move(DungeonSessionState state, DungeonDirection direction) {
        if (state.isComplete()) return new MoveResult(state, null, null);
        if (state.activeEncounterId() != null) return new MoveResult(state, null, null);

        DungeonPosition newPos = state.playerPosition().move(direction);
        if (!state.map().isInside(newPos)) return new MoveResult(state, null, null);

        DungeonTile newTile = state.map().tileAt(newPos);
        if (newTile == null) return new MoveResult(state, null, null);

        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>(state.map().tiles());
        int score = state.score();
        DungeonSessionState working = state;

        if (newTile.type() == DungeonTileType.SECRET_WALL) {
            newTile = newTile.withType(DungeonTileType.FLOOR, null).reveal().explore();
            tiles.put(newPos, newTile);
            score += SECRET_WALL_SCORE;
        } else if (!newTile.walkable()) {
            return new MoveResult(state, null, null);
        }

        revealAround(tiles, newPos);

        DungeonTile destinationTile = tiles.get(newPos);
        if (destinationTile != null) {
            destinationTile = destinationTile.explore();
            tiles.put(newPos, destinationTile);
        }

        DungeonTile activeTile = destinationTile != null ? destinationTile : newTile;
        String encounterIdHit = null;

        switch (activeTile.type()) {
            case ENCOUNTER, BOSS -> encounterIdHit = activeTile.encounterId();
            case HEAL -> {
                working = DungeonDamage.heal(working, HEAL_AMOUNT);
                tiles.put(newPos, activeTile.withType(DungeonTileType.FLOOR, null));
            }
            case TREASURE -> {
                score += TREASURE_SCORE;
                tiles.put(newPos, activeTile.withType(DungeonTileType.FLOOR, null));
            }
            case TRAP -> {
                working = DungeonDamage.takeDamage(working, 1);
                tiles.put(newPos, activeTile.withType(DungeonTileType.FLOOR, null));
            }
            default -> { /* no tile effect */ }
        }

        DungeonMap nextMap = new DungeonMap(
            state.map().width(), state.map().height(),
            state.map().entrance(), state.map().boss(), Map.copyOf(tiles));

        DungeonSessionState moved = new DungeonSessionState(
            working.config(), nextMap, newPos,
            working.encounters(), working.bossEncounterIds(), working.bossIndex(),
            working.activeEncounterId(),
            working.health(), score,
            working.answeredCount(), working.correctCount(),
            visibleFrom(tiles),
            working.won(), working.defeated());

        return new MoveResult(moved, activeTile.type(), encounterIdHit);
    }

    public Set<DungeonPosition> revealAroundEntrance(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition entrance) {
        revealAround(tiles, entrance);
        DungeonTile entranceTile = tiles.get(entrance);
        if (entranceTile != null) {
            tiles.put(entrance, entranceTile.explore());
        }
        return visibleFrom(tiles);
    }

    private void revealAround(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition center) {
        reveal(tiles, center);
        for (DungeonDirection direction : DungeonDirection.values()) {
            reveal(tiles, center.move(direction));
        }
    }

    private void reveal(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition position) {
        DungeonTile tile = tiles.get(position);
        if (tile != null) {
            tiles.put(position, tile.reveal());
        }
    }

    private Set<DungeonPosition> visibleFrom(Map<DungeonPosition, DungeonTile> tiles) {
        return tiles.values().stream()
            .filter(DungeonTile::revealed)
            .map(DungeonTile::position)
            .collect(Collectors.toSet());
    }
}
