package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonRevenantCompatTests {

    private final ObjectMapper mapper = new ObjectMapper().rebuild()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    @Test
    void revenantRoundTrips() {
        Revenant r = new Revenant("fc_3", "r5", "r9");
        Revenant back = mapper.readValue(mapper.writeValueAsString(r), Revenant.class);
        assertThat(back.encounterId()).isEqualTo("fc_3");
        assertThat(back.originRoomId()).isEqualTo("r5");
        assertThat(back.currentRoomId()).isEqualTo("r9");
    }

    @Test
    void revenantWithCurrentRoomReplacesOnlyPosition() {
        Revenant r = new Revenant("fc_3", "r5", "r5").withCurrentRoomId("r6");
        assertThat(r.encounterId()).isEqualTo("fc_3");
        assertThat(r.originRoomId()).isEqualTo("r5");
        assertThat(r.currentRoomId()).isEqualTo("r6");
    }
}
