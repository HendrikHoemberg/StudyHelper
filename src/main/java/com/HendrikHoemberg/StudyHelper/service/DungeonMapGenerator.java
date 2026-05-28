package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DungeonMapGenerator {

    private static final int MAX_REGEN_ATTEMPTS = 10;
    private static final DungeonDirection[] DIRS = DungeonDirection.values();

    public DungeonMap generate(DungeonSize size,
                                List<String> normalEncounterIds,
                                List<List<String>> eliteGauntletGroups) {
        return generate(size, normalEncounterIds, eliteGauntletGroups, new Random());
    }

    public DungeonMap generate(DungeonSize size,
                                List<String> normalEncounterIds,
                                List<List<String>> eliteGauntletGroups,
                                Random rng) {
        if (normalEncounterIds.size() != size.normalEncounterCount()) {
            throw new IllegalArgumentException("Normal encounter count must match dungeon size.");
        }
        if (eliteGauntletGroups.size() != size.eliteGauntletCount()) {
            throw new IllegalArgumentException("Elite gauntlet group count must match dungeon size.");
        }

        for (int attempt = 0; attempt < MAX_REGEN_ATTEMPTS; attempt++) {
            try {
                return tryGenerate(size, normalEncounterIds, eliteGauntletGroups, rng);
            } catch (LayoutFailure ignored) {
            }
        }
        throw new IllegalStateException(
            "Could not generate a valid dungeon layout after " + MAX_REGEN_ATTEMPTS + " attempts.");
    }

    private DungeonMap tryGenerate(DungeonSize size,
                                    List<String> normalEncounterIds,
                                    List<List<String>> eliteGauntletGroups,
                                    Random rng) {
        int lattice = latticeSize(size);
        int targetRooms = totalRoomCount(size);

        // Step 1: random-walk room placement on the lattice
        Map<GridPos, String> latticeToRoomId = new LinkedHashMap<>();
        Map<String, GridPos> roomToLattice = new LinkedHashMap<>();
        Map<String, Map<DungeonDirection, String>> doors = new LinkedHashMap<>();

        GridPos center = new GridPos(lattice / 2, lattice / 2);
        String entranceId = "r0";
        latticeToRoomId.put(center, entranceId);
        roomToLattice.put(entranceId, center);
        doors.put(entranceId, new EnumMap<>(DungeonDirection.class));

        int placed = 1;
        int attempts = 0;
        int maxAttempts = targetRooms * 30;
        while (placed < targetRooms && attempts < maxAttempts) {
            attempts++;
            List<String> existing = new ArrayList<>(roomToLattice.keySet());
            String fromId = existing.get(rng.nextInt(existing.size()));
            GridPos from = roomToLattice.get(fromId);
            DungeonDirection dir = DIRS[rng.nextInt(DIRS.length)];
            GridPos to = move(from, dir);
            if (!inLattice(to, lattice) || latticeToRoomId.containsKey(to)) continue;

            String newId = "r" + placed;
            latticeToRoomId.put(to, newId);
            roomToLattice.put(newId, to);
            doors.put(newId, new EnumMap<>(DungeonDirection.class));
            connect(doors, fromId, newId, dir);
            placed++;
        }
        if (placed < targetRooms) throw new LayoutFailure();

        // Step 2: add 1-2 loop doors between adjacent-but-unconnected rooms
        addLoopDoors(latticeToRoomId, roomToLattice, doors, loopDoorCount(size), rng);

        // Step 3: assign room types
        Map<String, RoomType> types = assignRoomTypes(
            entranceId, roomToLattice, doors, size, rng);

        // Step 4: embed secret room (no doors; revealed via hosts)
        SecretRoomPlacement secret = embedSecretRoom(latticeToRoomId, roomToLattice, lattice, rng);
        String secretRoomId = null;
        if (secret != null) {
            secretRoomId = secret.id;
            latticeToRoomId.put(secret.pos, secret.id);
            roomToLattice.put(secret.id, secret.pos);
            doors.put(secret.id, new EnumMap<>(DungeonDirection.class));
            types.put(secret.id, RoomType.SECRET);
        }

        // Step 5: build rooms with offers + gauntlet groups
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>();
        Iterator<String> normalIter = new ArrayDeque<>(normalEncounterIds).iterator();
        Iterator<List<String>> eliteIter = new ArrayDeque<>(eliteGauntletGroups).iterator();

        String bossRoomId = pickBossRoom(entranceId, types, doors);
        types.put(bossRoomId, RoomType.BOSS);

        for (Map.Entry<String, RoomType> e : types.entrySet()) {
            String id = e.getKey();
            RoomType type = e.getValue();
            DungeonRoom room = buildRoom(id, type, doors.get(id), roomToLattice.get(id),
                normalIter, eliteIter, secretRoomId, secret, rng);
            rooms.put(id, room);
        }

        // Mark entrance visited+cleared
        DungeonRoom entranceRoom = rooms.get(entranceId).withVisited(true).withCleared(true);
        rooms.put(entranceId, entranceRoom);

        // Step 6: validate
        if (!bfsReachable(rooms, entranceId).contains(bossRoomId)) throw new LayoutFailure();
        if (normalIter.hasNext()) throw new LayoutFailure();
        if (eliteIter.hasNext()) throw new LayoutFailure();

        return new DungeonMap(rooms, entranceId, bossRoomId, lattice);
    }

    // ===== helpers =====

    private static class LayoutFailure extends RuntimeException {}

    private record SecretRoomPlacement(String id, GridPos pos, List<String> hostRoomIds) {}

    private int latticeSize(DungeonSize size) {
        return switch (size) { case SMALL -> 7; case MEDIUM -> 9; case LARGE -> 11; };
    }

    private int totalRoomCount(DungeonSize size) {
        return switch (size) { case SMALL -> 13; case MEDIUM -> 17; case LARGE -> 25; };
    }

    private int loopDoorCount(DungeonSize size) {
        return switch (size) { case SMALL, MEDIUM -> 1; case LARGE -> 2; };
    }

    private boolean inLattice(GridPos p, int lattice) {
        return p.x() >= 0 && p.y() >= 0 && p.x() < lattice && p.y() < lattice;
    }

    private GridPos move(GridPos p, DungeonDirection d) {
        return switch (d) {
            case UP -> new GridPos(p.x(), p.y() - 1);
            case DOWN -> new GridPos(p.x(), p.y() + 1);
            case LEFT -> new GridPos(p.x() - 1, p.y());
            case RIGHT -> new GridPos(p.x() + 1, p.y());
        };
    }

    private DungeonDirection opposite(DungeonDirection d) {
        return switch (d) {
            case UP -> DungeonDirection.DOWN;
            case DOWN -> DungeonDirection.UP;
            case LEFT -> DungeonDirection.RIGHT;
            case RIGHT -> DungeonDirection.LEFT;
        };
    }

    private void connect(Map<String, Map<DungeonDirection, String>> doors,
                          String a, String b, DungeonDirection aToB) {
        doors.get(a).put(aToB, b);
        doors.get(b).put(opposite(aToB), a);
    }

    private void addLoopDoors(Map<GridPos, String> latticeToRoomId,
                                Map<String, GridPos> roomToLattice,
                                Map<String, Map<DungeonDirection, String>> doors,
                                int loops, Random rng) {
        List<String[]> candidates = new ArrayList<>();
        for (Map.Entry<String, GridPos> e : roomToLattice.entrySet()) {
            String id = e.getKey();
            GridPos pos = e.getValue();
            for (DungeonDirection d : DIRS) {
                GridPos neighborPos = move(pos, d);
                String neighborId = latticeToRoomId.get(neighborPos);
                if (neighborId == null) continue;
                if (doors.get(id).containsKey(d)) continue;
                if (id.compareTo(neighborId) < 0) {
                    candidates.add(new String[]{id, neighborId, d.name()});
                }
            }
        }
        Collections.shuffle(candidates, rng);
        for (int i = 0; i < Math.min(loops, candidates.size()); i++) {
            String[] c = candidates.get(i);
            connect(doors, c[0], c[1], DungeonDirection.valueOf(c[2]));
        }
    }

    private Map<String, RoomType> assignRoomTypes(String entranceId,
                                                    Map<String, GridPos> roomToLattice,
                                                    Map<String, Map<DungeonDirection, String>> doors,
                                                    DungeonSize size, Random rng) {
        Map<String, RoomType> types = new LinkedHashMap<>();
        for (String id : roomToLattice.keySet()) types.put(id, RoomType.COMBAT);
        types.put(entranceId, RoomType.ENTRANCE);

        Map<String, Integer> dist = bfsDistances(doors, entranceId);
        List<String> leavesAsc = sortedLeaves(doors, dist, true);
        List<String> leavesDesc = sortedLeaves(doors, dist, false);
        List<String> nonCritical = nonCriticalRooms(entranceId, pickBossCandidate(entranceId, dist),
            doors, dist, types);

        // SHOP — nearest leaf to entrance
        String shop = pickFirstAvailable(leavesAsc, types, entranceId, Set.of(RoomType.ENTRANCE));
        if (shop == null) throw new LayoutFailure();
        types.put(shop, RoomType.SHOP);

        // TREASURE — farther leaf
        String treasure = pickFirstAvailable(leavesDesc, types, entranceId, Set.of(RoomType.ENTRANCE));
        if (treasure == null) throw new LayoutFailure();
        types.put(treasure, RoomType.TREASURE);

        // LARGE has a second TREASURE
        if (size == DungeonSize.LARGE) {
            String treasure2 = pickFirstAvailable(leavesDesc, types, entranceId, Set.of());
            if (treasure2 == null) throw new LayoutFailure();
            types.put(treasure2, RoomType.TREASURE);
        }

        // HEAL — non-critical mid-distance room
        String heal = pickMidpoint(nonCritical, dist, types);
        if (heal == null) throw new LayoutFailure();
        types.put(heal, RoomType.HEAL);

        // SHRINE — non-critical mid-distance room
        String shrine = pickMidpoint(nonCritical, dist, types);
        if (shrine == null) throw new LayoutFailure();
        types.put(shrine, RoomType.SHRINE);

        // ELITEs — additional leaves
        int eliteCount = size.eliteGauntletCount();
        List<String> remainingLeaves = new ArrayList<>(leavesDesc);
        remainingLeaves.removeAll(types.keySet().stream()
            .filter(id -> types.get(id) != RoomType.COMBAT && types.get(id) != RoomType.ENTRANCE)
            .toList());
        int assignedElites = 0;
        for (String leaf : remainingLeaves) {
            if (assignedElites >= eliteCount) break;
            if (types.get(leaf) == RoomType.COMBAT) {
                types.put(leaf, RoomType.ELITE);
                assignedElites++;
            }
        }
        if (assignedElites < eliteCount) {
            for (String id : nonCritical) {
                if (assignedElites >= eliteCount) break;
                if (types.get(id) == RoomType.COMBAT) {
                    types.put(id, RoomType.ELITE);
                    assignedElites++;
                }
            }
        }
        if (assignedElites < eliteCount) throw new LayoutFailure();

        return types;
    }

    private String pickBossCandidate(String entranceId, Map<String, Integer> dist) {
        String best = entranceId;
        int bestDist = -1;
        for (Map.Entry<String, Integer> e : dist.entrySet()) {
            if (e.getValue() > bestDist) {
                bestDist = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private String pickBossRoom(String entranceId,
                                  Map<String, RoomType> types,
                                  Map<String, Map<DungeonDirection, String>> doors) {
        Map<String, Integer> dist = bfsDistances(doors, entranceId);
        String best = entranceId;
        int bestDist = -1;
        for (Map.Entry<String, Integer> e : dist.entrySet()) {
            if (types.get(e.getKey()) != RoomType.COMBAT) continue;
            if (e.getValue() > bestDist) {
                bestDist = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private List<String> sortedLeaves(Map<String, Map<DungeonDirection, String>> doors,
                                        Map<String, Integer> dist, boolean ascending) {
        List<String> leaves = new ArrayList<>();
        for (Map.Entry<String, Map<DungeonDirection, String>> e : doors.entrySet()) {
            if (e.getValue().size() == 1) leaves.add(e.getKey());
        }
        leaves.sort(Comparator.comparingInt(dist::get));
        if (!ascending) Collections.reverse(leaves);
        return leaves;
    }

    private List<String> nonCriticalRooms(String entranceId, String bossId,
                                            Map<String, Map<DungeonDirection, String>> doors,
                                            Map<String, Integer> dist,
                                            Map<String, RoomType> types) {
        List<String> result = new ArrayList<>();
        for (String id : doors.keySet()) {
            if (!id.equals(entranceId) && !id.equals(bossId)) result.add(id);
        }
        return result;
    }

    private String pickFirstAvailable(List<String> ordered,
                                        Map<String, RoomType> types,
                                        String entranceId,
                                        Set<RoomType> blockedTypes) {
        for (String id : ordered) {
            if (id.equals(entranceId)) continue;
            RoomType t = types.get(id);
            if (t != RoomType.COMBAT) continue;
            return id;
        }
        return null;
    }

    private String pickMidpoint(List<String> candidates,
                                  Map<String, Integer> dist,
                                  Map<String, RoomType> types) {
        int max = candidates.stream().mapToInt(dist::get).max().orElse(0);
        int targetDist = max / 2;
        String best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (String id : candidates) {
            if (types.get(id) != RoomType.COMBAT) continue;
            int delta = Math.abs(dist.get(id) - targetDist);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = id;
            }
        }
        return best;
    }

    private SecretRoomPlacement embedSecretRoom(Map<GridPos, String> latticeToRoomId,
                                                  Map<String, GridPos> roomToLattice,
                                                  int lattice,
                                                  Random rng) {
        List<GridPos> candidates = new ArrayList<>();
        for (int x = 0; x < lattice; x++) {
            for (int y = 0; y < lattice; y++) {
                GridPos pos = new GridPos(x, y);
                if (latticeToRoomId.containsKey(pos)) continue;
                List<String> hosts = new ArrayList<>();
                for (DungeonDirection d : DIRS) {
                    String neighborId = latticeToRoomId.get(move(pos, d));
                    if (neighborId != null) hosts.add(neighborId);
                }
                if (hosts.size() >= 2) candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) return null;
        Collections.shuffle(candidates, rng);
        GridPos chosen = candidates.get(0);
        List<String> hosts = new ArrayList<>();
        for (DungeonDirection d : DIRS) {
            String neighborId = latticeToRoomId.get(move(chosen, d));
            if (neighborId != null) hosts.add(neighborId);
        }
        return new SecretRoomPlacement("secret", chosen, hosts);
    }

    private DungeonRoom buildRoom(String id, RoomType type,
                                    Map<DungeonDirection, String> roomDoors,
                                    GridPos gridPos,
                                    Iterator<String> normalIter,
                                    Iterator<List<String>> eliteIter,
                                    String secretRoomId,
                                    SecretRoomPlacement secret,
                                    Random rng) {
        String encounterId = null;
        List<String> gauntletGroup = List.of();
        TreasureOffer treasureOffer = null;
        TreasureOffer eliteOffer = null;
        ShopOffer shopOffer = null;
        SecretReward secretReward = null;

        switch (type) {
            case COMBAT -> encounterId = normalIter.next();
            case ELITE -> {
                gauntletGroup = eliteIter.next();
                encounterId = gauntletGroup.get(0);
                eliteOffer = new TreasureOffer(
                    RelicCatalog.sample(RelicPool.ELITE, 2, rng));
            }
            case TREASURE -> treasureOffer = new TreasureOffer(
                RelicCatalog.sample(RelicPool.COMMON, 3, rng));
            case SHOP -> {
                List<RelicId> picks = RelicCatalog.sampleFromUnion(
                    List.of(RelicPool.COMMON, RelicPool.SHOP_EXCLUSIVE), 2, rng);
                List<ShopOfferEntry> entries = new ArrayList<>();
                for (RelicId rid : picks) {
                    int price = (rid == RelicId.MAP_SENSE) ? 150 : 75;
                    entries.add(new ShopOfferEntry(rid, price));
                }
                ShopConsumable consumable = new ShopConsumable(
                    "dungeon.shop.consumable.heal", 30, 2);
                shopOffer = new ShopOffer(entries, consumable);
            }
            case SECRET -> {
                if (rng.nextBoolean()) {
                    List<RelicId> picks = RelicCatalog.sampleFromUnion(
                        List.of(RelicPool.COMMON, RelicPool.ELITE), 1, rng);
                    secretReward = new SecretReward.RelicReward(picks.get(0));
                } else {
                    secretReward = new SecretReward.Bundle(5, 1, 50);
                }
            }
            case ENTRANCE, HEAL, BOSS, SHRINE -> { }
        }

        return new DungeonRoom(id, type, roomDoors, gridPos,
            false, false,
            encounterId, gauntletGroup,
            treasureOffer, eliteOffer, shopOffer, secretReward);
    }

    private Map<String, Integer> bfsDistances(Map<String, Map<DungeonDirection, String>> doors, String from) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        dist.put(from, 0);
        queue.add(from);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            for (String neighbor : doors.get(node).values()) {
                if (dist.containsKey(neighbor)) continue;
                dist.put(neighbor, dist.get(node) + 1);
                queue.add(neighbor);
            }
        }
        return dist;
    }

    private Set<String> bfsReachable(Map<String, DungeonRoom> rooms, String from) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(from);
        queue.add(from);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            DungeonRoom r = rooms.get(node);
            for (String neighbor : r.doors().values()) {
                if (seen.add(neighbor)) queue.add(neighbor);
            }
        }
        return seen;
    }

    public List<MinimapRoom> buildMinimap(DungeonSessionState state) {
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
