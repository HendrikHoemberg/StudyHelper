package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonDirection;
import com.HendrikHoemberg.StudyHelper.dto.DungeonMap;
import com.HendrikHoemberg.StudyHelper.dto.DungeonPosition;
import com.HendrikHoemberg.StudyHelper.dto.DungeonSize;
import com.HendrikHoemberg.StudyHelper.dto.DungeonTile;
import com.HendrikHoemberg.StudyHelper.dto.DungeonTileType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DungeonMapGenerator {

    public DungeonMap generate(DungeonSize size, List<String> normalEncounterIds) {
        if (normalEncounterIds.size() != size.normalEncounterCount()) {
            throw new IllegalArgumentException("Normal encounter count must match dungeon size.");
        }

        int width = switch (size) {
            case SMALL -> 9;
            case MEDIUM -> 11;
            case LARGE -> 13;
        };
        int height = switch (size) {
            case SMALL -> 9;
            case MEDIUM -> 11;
            case LARGE -> 13;
        };

        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                DungeonPosition p = new DungeonPosition(x, y);
                tiles.put(p, new DungeonTile(p, DungeonTileType.WALL, false, false, null));
            }
        }

        List<DungeonPosition> path = carveMainAndBranchingPaths(size, width, height);
        for (DungeonPosition p : path) {
            tiles.put(p, new DungeonTile(p, DungeonTileType.FLOOR, false, false, null));
        }

        DungeonPosition entrance = path.get(0);
        DungeonPosition boss = path.get(path.size() - 1);
        tiles.put(entrance, new DungeonTile(entrance, DungeonTileType.ENTRANCE, true, true, null));
        tiles.put(boss, new DungeonTile(boss, DungeonTileType.BOSS, false, false, null));

        List<DungeonPosition> candidates = path.stream()
            .filter(p -> !p.equals(entrance) && !p.equals(boss))
            .toList();

        for (int i = 0; i < normalEncounterIds.size(); i++) {
            DungeonPosition p = candidates.get(i);
            tiles.put(p, new DungeonTile(p, DungeonTileType.ENCOUNTER, false, false, normalEncounterIds.get(i)));
        }

        int offset = normalEncounterIds.size();
        if (offset < candidates.size()) {
            DungeonPosition heal = candidates.get(offset);
            tiles.put(heal, new DungeonTile(heal, DungeonTileType.HEAL, false, false, null));
            offset++;
        }
        if (offset < candidates.size()) {
            DungeonPosition treasure = candidates.get(offset);
            tiles.put(treasure, new DungeonTile(treasure, DungeonTileType.TREASURE, false, false, null));
        }

        revealAround(tiles, entrance);
        return new DungeonMap(width, height, entrance, boss, Map.copyOf(tiles));
    }

    private List<DungeonPosition> carveMainAndBranchingPaths(DungeonSize size, int width, int height) {
        List<DungeonPosition> path = new ArrayList<>();
        int mid = height / 2;
        for (int x = 1; x < width - 1; x++) {
            path.add(new DungeonPosition(x, mid));
        }
        for (int x = 2; x < width - 2; x += 2) {
            int branchLength = size == DungeonSize.SMALL ? 2 : 3;
            for (int y = 1; y <= branchLength; y++) {
                path.add(new DungeonPosition(x, mid - y));
            }
            for (int y = 1; y <= branchLength; y++) {
                path.add(new DungeonPosition(x, mid + y));
            }
        }
        addRoom(path, width / 2, mid - 2, size == DungeonSize.SMALL ? 1 : 2);
        addRoom(path, width - 4, mid + 2, size == DungeonSize.LARGE ? 2 : 1);
        return path.stream().distinct().toList();
    }

    private void addRoom(List<DungeonPosition> path, int centerX, int centerY, int radius) {
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                path.add(new DungeonPosition(x, y));
            }
        }
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
}
