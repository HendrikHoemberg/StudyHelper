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

        // --- Procedurally Place Traps ---
        int trapCount = switch (size) {
            case SMALL -> 1;
            case MEDIUM -> 2;
            case LARGE -> 3;
        };
        int trapPlaced = 0;
        int trapOffset = offset + 1;
        while (trapPlaced < trapCount && trapOffset < candidates.size()) {
            DungeonPosition trapPos = candidates.get(trapOffset);
            tiles.put(trapPos, new DungeonTile(trapPos, DungeonTileType.TRAP, false, false, null));
            trapPlaced++;
            trapOffset++;
        }

        // --- Procedurally Place Secret Walls and Chambers ---
        generateSecretCompartments(tiles, path, width, height, size);

        revealAround(tiles, entrance);
        return new DungeonMap(width, height, entrance, boss, Map.copyOf(tiles));
    }

    private void generateSecretCompartments(Map<DungeonPosition, DungeonTile> tiles,
                                            List<DungeonPosition> path,
                                            int width, int height,
                                            DungeonSize size) {
        int targetSecretCount = switch (size) {
            case SMALL -> 1;
            case MEDIUM -> 2;
            case LARGE -> 3;
        };
        int placedSecrets = 0;

        for (DungeonPosition p : path) {
            if (placedSecrets >= targetSecretCount) break;

            for (DungeonDirection dir : DungeonDirection.values()) {
                DungeonPosition n1 = p.move(dir);
                DungeonPosition n2 = n1.move(dir);

                if (isInside(n1, width, height) && isInside(n2, width, height)) {
                    DungeonTile t1 = tiles.get(n1);
                    DungeonTile t2 = tiles.get(n2);

                    if (t1 != null && t1.type() == DungeonTileType.WALL &&
                        t2 != null && t2.type() == DungeonTileType.WALL &&
                        !isAdjacentToPathOtherThan(n2, n1, path)) {

                        tiles.put(n1, new DungeonTile(n1, DungeonTileType.SECRET_WALL, false, false, null));
                        DungeonTileType secretType = (placedSecrets % 2 == 0) ? DungeonTileType.TREASURE : DungeonTileType.HEAL;
                        tiles.put(n2, new DungeonTile(n2, secretType, false, false, null));
                        placedSecrets++;
                        break;
                    }
                }
            }
        }
    }

    private boolean isInside(DungeonPosition p, int width, int height) {
        return p.x() >= 0 && p.x() < width && p.y() >= 0 && p.y() < height;
    }

    private boolean isAdjacentToPathOtherThan(DungeonPosition pos, DungeonPosition exclude, List<DungeonPosition> path) {
        for (DungeonDirection dir : DungeonDirection.values()) {
            DungeonPosition neighbor = pos.move(dir);
            if (!neighbor.equals(exclude) && path.contains(neighbor)) {
                return true;
            }
        }
        return false;
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
