package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonSessionStateTests {

    @Test
    void isComplete_returnsTrueForWonOrDefeatedRuns() {
        DungeonSessionState active = sampleState(false, false);
        DungeonSessionState won = sampleState(true, false);
        DungeonSessionState defeated = sampleState(false, true);

        assertThat(active.isComplete()).isFalse();
        assertThat(won.isComplete()).isTrue();
        assertThat(defeated.isComplete()).isTrue();
    }

    @Test
    void stateRoundTripsThroughJackson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DungeonSessionState state = sampleState(false, false);

        String json = mapper.writeValueAsString(state);
        DungeonSessionState restored = mapper.readValue(json, DungeonSessionState.class);

        assertThat(restored.config().mode()).isEqualTo(DungeonMode.FLASHCARDS);
        assertThat(restored.map().width()).isEqualTo(5);
        assertThat(restored.playerPosition()).isEqualTo(new DungeonPosition(1, 1));
        assertThat(restored.encounters()).containsKey("e1");
    }

    private DungeonSessionState sampleState(boolean won, boolean defeated) {
        DungeonPosition start = new DungeonPosition(1, 1);
        DungeonMap map = new DungeonMap(
            5,
            5,
            start,
            new DungeonPosition(3, 3),
            Map.of(start, new DungeonTile(start, DungeonTileType.ENTRANCE, true, true, null))
        );
        DungeonEncounter encounter = DungeonEncounter.flashcard(
            "e1",
            false,
            101L,
            "Front",
            "Back",
            null,
            null
        );
        return new DungeonSessionState(
            new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(10L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
            map,
            start,
            Map.of("e1", encounter),
            List.of("e1"),
            0,
            null,
            5,
            0,
            0,
            0,
            Set.of(start),
            won,
            defeated,
            0, 0, List.of(), 0, 0, 0
        );
    }
}
