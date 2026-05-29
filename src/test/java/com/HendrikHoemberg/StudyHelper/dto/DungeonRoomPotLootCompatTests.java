package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonRoomPotLootCompatTests {

    // Mirrors SavedSessionService's mapper: tolerate fields that no longer exist on the record.
    private final ObjectMapper mapper = new ObjectMapper().rebuild()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    @Test
    void legacyRoomJson_withRemovedSecretReward_andMissingPotLoot_deserializes() {
        String legacyJson = """
            {"id":"r0","type":"SECRET","doors":{},
             "gridPos":{"x":0,"y":0},"visited":true,"cleared":true,
             "encounterId":null,"gauntletGroup":[],
             "treasureOffer":null,"eliteOffer":null,"shopOffer":null,"secretReward":null}
            """;

        DungeonRoom room = mapper.readValue(legacyJson, DungeonRoom.class);

        assertThat(room.id()).isEqualTo("r0");
        assertThat(room.type()).isEqualTo(RoomType.SECRET); // enum constant retained for old saves
        assertThat(room.potLoot()).isEmpty();
    }

    @Test
    void roomRoundTripsWithPotLoot() {
        DungeonRoom room = new DungeonRoom("r1", RoomType.COMBAT,
            java.util.Map.of(), new GridPos(1, 1), false, false,
            "fc_0", java.util.List.of(), null, null, null,
            java.util.List.of(PotLoot.COIN, PotLoot.SHIELD, PotLoot.EMPTY));

        String json = mapper.writeValueAsString(room);
        DungeonRoom back = mapper.readValue(json, DungeonRoom.class);

        assertThat(back.potLoot())
            .containsExactly(PotLoot.COIN, PotLoot.SHIELD, PotLoot.EMPTY);
    }
}
