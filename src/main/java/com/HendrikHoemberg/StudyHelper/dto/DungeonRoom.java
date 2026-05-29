package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record DungeonRoom(
    String id,
    RoomType type,
    Map<DungeonDirection, String> doors,
    GridPos gridPos,
    boolean visited,
    boolean cleared,
    String encounterId,
    List<String> gauntletGroup,
    TreasureOffer treasureOffer,
    TreasureOffer eliteOffer,
    ShopOffer shopOffer,
    List<PotLoot> potLoot
) implements Serializable {

    public DungeonRoom {
        doors = doors == null ? Map.of() : Map.copyOf(doors);
        gauntletGroup = gauntletGroup == null ? List.of() : List.copyOf(gauntletGroup);
        potLoot = potLoot == null ? List.of() : List.copyOf(potLoot);
    }

    // Backward-compatible constructor for call sites that don't set loot (tests, etc.).
    public DungeonRoom(String id, RoomType type, Map<DungeonDirection, String> doors,
                       GridPos gridPos, boolean visited, boolean cleared,
                       String encounterId, List<String> gauntletGroup,
                       TreasureOffer treasureOffer, TreasureOffer eliteOffer,
                       ShopOffer shopOffer) {
        this(id, type, doors, gridPos, visited, cleared, encounterId, gauntletGroup,
            treasureOffer, eliteOffer, shopOffer, List.of());
    }

    public DungeonRoom withDoors(Map<DungeonDirection, String> newDoors) {
        return new DungeonRoom(id, type, newDoors, gridPos, visited, cleared,
            encounterId, gauntletGroup, treasureOffer, eliteOffer, shopOffer, potLoot);
    }

    public DungeonRoom withVisited(boolean v) {
        return new DungeonRoom(id, type, doors, gridPos, v, cleared,
            encounterId, gauntletGroup, treasureOffer, eliteOffer, shopOffer, potLoot);
    }

    public DungeonRoom withCleared(boolean c) {
        return new DungeonRoom(id, type, doors, gridPos, visited, c,
            encounterId, gauntletGroup, treasureOffer, eliteOffer, shopOffer, potLoot);
    }

    public DungeonRoom withPotLoot(List<PotLoot> loot) {
        return new DungeonRoom(id, type, doors, gridPos, visited, cleared,
            encounterId, gauntletGroup, treasureOffer, eliteOffer, shopOffer, loot);
    }
}
