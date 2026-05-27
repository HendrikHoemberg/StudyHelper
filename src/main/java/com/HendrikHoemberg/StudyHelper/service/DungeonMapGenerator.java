package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DungeonMapGenerator {

    private static final int MAX_REGEN_ATTEMPTS = 10;
    private static final int MAX_ROOM_ATTEMPTS = 50;

    public DungeonMap generate(DungeonSize size, List<String> normalEncounterIds) {
        return generate(size, normalEncounterIds, List.of(), new Random());
    }

    public DungeonMap generate(DungeonSize size, List<String> normalEncounterIds,
                                List<List<String>> eliteGauntletGroups) {
        return generate(size, normalEncounterIds, eliteGauntletGroups, new Random());
    }

    public DungeonMap generate(DungeonSize size, List<String> normalEncounterIds,
                                List<List<String>> eliteGauntletGroups, Random rng) {
        if (normalEncounterIds.size() != size.normalEncounterCount()) {
            throw new IllegalArgumentException("Normal encounter count must match dungeon size.");
        }

        int width = gridSize(size);
        int height = gridSize(size);
        int targetRoomCount = targetRooms(size);
        int trapCount = trapCount(size);
        int secretCount = secretCount(size);

        for (int attempt = 0; attempt < MAX_REGEN_ATTEMPTS; attempt++) {
            try {
                return tryGenerate(width, height, targetRoomCount, trapCount, secretCount,
                    normalEncounterIds, eliteGauntletGroups, rng);
            } catch (LayoutFailure ignored) {
            }
        }
        throw new IllegalStateException("Could not generate a valid dungeon layout after "
            + MAX_REGEN_ATTEMPTS + " attempts.");
    }

    private DungeonMap tryGenerate(int width, int height, int targetRoomCount,
                                    int trapCount, int secretCount,
                                    List<String> normalEncounterIds,
                                    List<List<String>> eliteGauntletGroups,
                                    Random rng) {
        Map<DungeonPosition, DungeonTile> tiles = filledWithWalls(width, height);
        List<Rect> rooms = placeRooms(width, height, targetRoomCount, rng);
        if (rooms.size() < 3) throw new LayoutFailure();
        carveRoomFloors(tiles, rooms);
        List<Edge> mstEdges = minimumSpanningTree(rooms);
        for (Edge e : mstEdges) carveLCorridor(tiles, rooms.get(e.a).center(), rooms.get(e.b).center(), rng);
        addExtraLoops(tiles, rooms, mstEdges, rng);

        DungeonPosition entrance = pickEntrance(rooms, tiles);
        DungeonPosition boss = pickBossFarthest(rooms, tiles, entrance);
        if (entrance.equals(boss)) throw new LayoutFailure();

        Rect entranceRoom = roomContaining(rooms, entrance);
        Rect bossRoom = roomContaining(rooms, boss);

        Map<String, List<String>> gauntletGroups = new LinkedHashMap<>();
        for (List<String> group : eliteGauntletGroups) {
            if (group.isEmpty()) continue;
            DungeonPosition elitePos = pickElitePosition(rooms, entranceRoom, bossRoom, tiles, rng);
            if (elitePos == null) throw new LayoutFailure();
            tiles.put(elitePos, new DungeonTile(elitePos, DungeonTileType.ELITE, false, false, group.get(0)));
            gauntletGroups.put(group.get(0), List.copyOf(group));
        }

        tiles.put(entrance, new DungeonTile(entrance, DungeonTileType.ENTRANCE, true, true, null));
        tiles.put(boss, new DungeonTile(boss, DungeonTileType.BOSS, false, false, null));

        placeEncounters(tiles, rooms, entrance, boss, normalEncounterIds, rng);
        placeRestTiles(tiles, rooms, entranceRoom, bossRoom, trapCount, rng);
        placeSecretCompartments(tiles, rooms, secretCount, width, height, rng);

        revealAround(tiles, entrance);
        return new DungeonMap(width, height, entrance, boss, Map.copyOf(tiles), Map.copyOf(gauntletGroups));
    }

    // ===== Geometry primitives =====

    private record Rect(int x, int y, int w, int h) {
        DungeonPosition center() { return new DungeonPosition(x + w / 2, y + h / 2); }
        boolean contains(DungeonPosition p) {
            return p.x() >= x && p.x() < x + w && p.y() >= y && p.y() < y + h;
        }
        boolean touches(Rect other) {
            return !(x + w + 1 <= other.x || other.x + other.w + 1 <= x
                  || y + h + 1 <= other.y || other.y + other.h + 1 <= y);
        }
    }
    private record Edge(int a, int b, int distance) {}
    private static class LayoutFailure extends RuntimeException {}

    private int gridSize(DungeonSize size) {
        return switch (size) { case SMALL -> 9; case MEDIUM -> 11; case LARGE -> 13; };
    }
    private int targetRooms(DungeonSize size) {
        return switch (size) { case SMALL -> 4; case MEDIUM -> 6; case LARGE -> 8; };
    }
    private int trapCount(DungeonSize size) {
        return switch (size) { case SMALL -> 1; case MEDIUM -> 2; case LARGE -> 3; };
    }
    private int secretCount(DungeonSize size) {
        return switch (size) { case SMALL -> 1; case MEDIUM -> 2; case LARGE -> 3; };
    }

    private Map<DungeonPosition, DungeonTile> filledWithWalls(int w, int h) {
        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                DungeonPosition p = new DungeonPosition(x, y);
                tiles.put(p, new DungeonTile(p, DungeonTileType.WALL, false, false, null));
            }
        return tiles;
    }

    private List<Rect> placeRooms(int width, int height, int target, Random rng) {
        List<Rect> rooms = new ArrayList<>();
        int placed = 0;
        int attempts = 0;
        while (placed < target && attempts < target * MAX_ROOM_ATTEMPTS) {
            attempts++;
            int w = 2 + rng.nextInt(3);
            int h = 2 + rng.nextInt(2);
            int x = 1 + rng.nextInt(width - w - 1);
            int y = 1 + rng.nextInt(height - h - 1);
            Rect candidate = new Rect(x, y, w, h);
            boolean overlaps = rooms.stream().anyMatch(r -> r.touches(candidate));
            if (!overlaps) { rooms.add(candidate); placed++; }
        }
        return rooms;
    }

    private void carveRoomFloors(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms) {
        for (Rect r : rooms) {
            for (int y = r.y; y < r.y + r.h; y++)
                for (int x = r.x; x < r.x + r.w; x++) {
                    DungeonPosition p = new DungeonPosition(x, y);
                    tiles.put(p, new DungeonTile(p, DungeonTileType.FLOOR, false, false, null));
                }
        }
    }

    private List<Edge> minimumSpanningTree(List<Rect> rooms) {
        int n = rooms.size();
        List<Edge> all = new ArrayList<>();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                DungeonPosition a = rooms.get(i).center(), b = rooms.get(j).center();
                int d = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
                all.add(new Edge(i, j, d));
            }
        all.sort(Comparator.comparingInt(e -> e.distance));
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        List<Edge> mst = new ArrayList<>();
        for (Edge e : all) {
            int ra = find(parent, e.a), rb = find(parent, e.b);
            if (ra != rb) { parent[ra] = rb; mst.add(e); }
        }
        return mst;
    }

    private int find(int[] parent, int x) {
        while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x]; }
        return x;
    }

    private void carveLCorridor(Map<DungeonPosition, DungeonTile> tiles,
                                 DungeonPosition a, DungeonPosition b, Random rng) {
        boolean horizFirst = rng.nextBoolean();
        if (horizFirst) {
            carveHoriz(tiles, a.x(), b.x(), a.y());
            carveVert(tiles, a.y(), b.y(), b.x());
        } else {
            carveVert(tiles, a.y(), b.y(), a.x());
            carveHoriz(tiles, a.x(), b.x(), b.y());
        }
    }

    private void carveHoriz(Map<DungeonPosition, DungeonTile> tiles, int x1, int x2, int y) {
        int lo = Math.min(x1, x2), hi = Math.max(x1, x2);
        for (int x = lo; x <= hi; x++) {
            DungeonPosition p = new DungeonPosition(x, y);
            if (tiles.get(p).type() == DungeonTileType.WALL) {
                tiles.put(p, new DungeonTile(p, DungeonTileType.FLOOR, false, false, null));
            }
        }
    }

    private void carveVert(Map<DungeonPosition, DungeonTile> tiles, int y1, int y2, int x) {
        int lo = Math.min(y1, y2), hi = Math.max(y1, y2);
        for (int y = lo; y <= hi; y++) {
            DungeonPosition p = new DungeonPosition(x, y);
            if (tiles.get(p).type() == DungeonTileType.WALL) {
                tiles.put(p, new DungeonTile(p, DungeonTileType.FLOOR, false, false, null));
            }
        }
    }

    private void addExtraLoops(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms,
                                List<Edge> mst, Random rng) {
        int loops = Math.min(2, Math.max(0, rooms.size() - 3));
        Set<Long> mstSet = new HashSet<>();
        for (Edge e : mst) mstSet.add(((long) Math.min(e.a, e.b) << 32) | Math.max(e.a, e.b));
        List<Edge> candidates = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++)
            for (int j = i + 1; j < rooms.size(); j++) {
                long key = ((long) i << 32) | j;
                if (mstSet.contains(key)) continue;
                DungeonPosition a = rooms.get(i).center(), b = rooms.get(j).center();
                int d = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
                if (d <= 8) candidates.add(new Edge(i, j, d));
            }
        Collections.shuffle(candidates, rng);
        for (int i = 0; i < Math.min(loops, candidates.size()); i++) {
            Edge e = candidates.get(i);
            carveLCorridor(tiles, rooms.get(e.a).center(), rooms.get(e.b).center(), rng);
        }
    }

    private DungeonPosition pickEntrance(List<Rect> rooms, Map<DungeonPosition, DungeonTile> tiles) {
        return rooms.get(0).center();
    }

    private DungeonPosition pickBossFarthest(List<Rect> rooms,
                                              Map<DungeonPosition, DungeonTile> tiles,
                                              DungeonPosition entrance) {
        DungeonPosition best = entrance;
        int bestDist = -1;
        for (Rect r : rooms) {
            DungeonPosition c = r.center();
            int d = bfsDistance(tiles, entrance, c);
            if (d > bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    private int bfsDistance(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition from, DungeonPosition to) {
        Map<DungeonPosition, Integer> dist = new HashMap<>();
        Deque<DungeonPosition> queue = new ArrayDeque<>();
        dist.put(from, 0); queue.add(from);
        while (!queue.isEmpty()) {
            DungeonPosition p = queue.poll();
            if (p.equals(to)) return dist.get(p);
            for (DungeonDirection d : DungeonDirection.values()) {
                DungeonPosition n = p.move(d);
                DungeonTile t = tiles.get(n);
                if (t == null || t.type() == DungeonTileType.WALL) continue;
                if (dist.containsKey(n)) continue;
                dist.put(n, dist.get(p) + 1);
                queue.add(n);
            }
        }
        return -1;
    }

    private Rect roomContaining(List<Rect> rooms, DungeonPosition p) {
        for (Rect r : rooms) if (r.contains(p)) return r;
        return null;
    }

    private DungeonPosition pickElitePosition(List<Rect> rooms, Rect entranceRoom, Rect bossRoom,
                                               Map<DungeonPosition, DungeonTile> tiles, Random rng) {
        List<Rect> candidates = new ArrayList<>();
        for (Rect r : rooms) {
            if (r.equals(entranceRoom) || r.equals(bossRoom)) continue;
            candidates.add(r);
        }
        if (candidates.isEmpty()) return null;
        Collections.shuffle(candidates, rng);
        Rect chosen = candidates.get(0);
        return chosen.center();
    }

    private void placeEncounters(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms,
                                  DungeonPosition entrance, DungeonPosition boss,
                                  List<String> ids, Random rng) {
        List<DungeonPosition> slots = new ArrayList<>();
        for (Rect r : rooms) {
            for (int y = r.y; y < r.y + r.h; y++)
                for (int x = r.x; x < r.x + r.w; x++) {
                    DungeonPosition p = new DungeonPosition(x, y);
                    if (p.equals(entrance) || p.equals(boss)) continue;
                    DungeonTile t = tiles.get(p);
                    if (t.type() == DungeonTileType.FLOOR) slots.add(p);
                }
        }
        Collections.shuffle(slots, rng);
        if (slots.size() < ids.size()) throw new LayoutFailure();
        for (int i = 0; i < ids.size(); i++) {
            DungeonPosition p = slots.get(i);
            tiles.put(p, new DungeonTile(p, DungeonTileType.ENCOUNTER, false, false, ids.get(i)));
        }
    }

    private void placeRestTiles(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms,
                                 Rect entranceRoom, Rect bossRoom, int trapCount, Random rng) {
        List<DungeonPosition> free = new ArrayList<>();
        for (Rect r : rooms) {
            if (r.equals(entranceRoom) || r.equals(bossRoom)) continue;
            for (int y = r.y; y < r.y + r.h; y++)
                for (int x = r.x; x < r.x + r.w; x++) {
                    DungeonPosition p = new DungeonPosition(x, y);
                    if (tiles.get(p).type() == DungeonTileType.FLOOR) free.add(p);
                }
        }
        Collections.shuffle(free, rng);
        int idx = 0;
        if (idx < free.size()) {
            DungeonPosition heal = free.get(idx++);
            tiles.put(heal, new DungeonTile(heal, DungeonTileType.HEAL, false, false, null));
        }
        if (idx < free.size()) {
            DungeonPosition tre = free.get(idx++);
            tiles.put(tre, new DungeonTile(tre, DungeonTileType.TREASURE, false, false, null));
        }
        for (int t = 0; t < trapCount && idx < free.size(); t++) {
            DungeonPosition trap = free.get(idx++);
            tiles.put(trap, new DungeonTile(trap, DungeonTileType.TRAP, false, false, null));
        }
    }

    private void placeSecretCompartments(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms,
                                          int secretCount, int width, int height, Random rng) {
        int placed = 0;
        List<Rect> shuffled = new ArrayList<>(rooms);
        Collections.shuffle(shuffled, rng);
        for (Rect r : shuffled) {
            if (placed >= secretCount) break;
            for (DungeonDirection dir : DungeonDirection.values()) {
                DungeonPosition n1 = r.center().move(dir);
                DungeonPosition n2 = n1.move(dir);
                if (!isInside(n1, width, height) || !isInside(n2, width, height)) continue;
                DungeonTile t1 = tiles.get(n1), t2 = tiles.get(n2);
                if (t1.type() == DungeonTileType.WALL && t2.type() == DungeonTileType.WALL) {
                    tiles.put(n1, new DungeonTile(n1, DungeonTileType.SECRET_WALL, false, false, null));
                    DungeonTileType inner = (placed % 2 == 0) ? DungeonTileType.TREASURE : DungeonTileType.HEAL;
                    tiles.put(n2, new DungeonTile(n2, inner, false, false, null));
                    placed++;
                    break;
                }
            }
        }
    }

    private boolean isInside(DungeonPosition p, int width, int height) {
        return p.x() >= 0 && p.x() < width && p.y() >= 0 && p.y() < height;
    }

    private void revealAround(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition center) {
        reveal(tiles, center);
        for (DungeonDirection direction : DungeonDirection.values()) reveal(tiles, center.move(direction));
    }

    private void reveal(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition position) {
        DungeonTile tile = tiles.get(position);
        if (tile != null) tiles.put(position, tile.reveal());
    }
}
