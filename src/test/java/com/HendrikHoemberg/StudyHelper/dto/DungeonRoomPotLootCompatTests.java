package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonRoomPotLootCompatTests {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roomJsonWithoutPotLoot_deserializesToEmptyList() {
        String legacyJson = """
            {"id":"r0","type":"ENTRANCE","doors":{},
             "gridPos":{"x":0,"y":0},"visited":true,"cleared":true,
             "encounterId":null,"gauntletGroup":[],
             "treasureOffer":null,"eliteOffer":null,"shopOffer":null,"secretReward":null}
            """;

        DungeonRoom room = mapper.readValue(legacyJson, DungeonRoom.class);

        assertThat(room.potLoot()).isEmpty();
        assertThat(room.id()).isEqualTo("r0");
    }

    @Test
    void roomRoundTripsWithPotLoot() {
        DungeonRoom room = new DungeonRoom("r1", RoomType.COMBAT,
            java.util.Map.of(), new GridPos(1, 1), false, false,
            "fc_0", java.util.List.of(), null, null, null, null,
            java.util.List.of(PotLoot.COIN, PotLoot.SHIELD, PotLoot.EMPTY));

        String json = mapper.writeValueAsString(room);
        DungeonRoom back = mapper.readValue(json, DungeonRoom.class);

        assertThat(back.potLoot())
            .containsExactly(PotLoot.COIN, PotLoot.SHIELD, PotLoot.EMPTY);
    }
}
