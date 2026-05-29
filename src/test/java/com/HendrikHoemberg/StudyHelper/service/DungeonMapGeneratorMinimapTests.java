package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonMapGeneratorMinimapTests {

    @Test
    void buildMinimap_returnsAllReachableRooms() {
        DungeonMapGenerator gen = new DungeonMapGenerator();
        List<String> normalIds = IntStream.range(0, DungeonSize.SMALL.normalEncounterCount())
            .mapToObj(i -> "enc_" + i)
            .toList();
        List<List<String>> elites = List.of(
            List.of("e1_0", "e1_1")
        );
        DungeonMap map = gen.generate(DungeonSize.SMALL, normalIds, elites);

        Map<String, DungeonEncounter> encounters = Map.of();
        DungeonSessionState state = DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
                List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map)
            .currentRoomId(map.entranceRoomId())
            .encounters(encounters)
            .bossEncounterIds(List.of("boss_0"))
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .build();

        DungeonMinimapBuilder minimapBuilder = new DungeonMinimapBuilder();
        List<MinimapRoom> rooms = minimapBuilder.build(state);
        assertThat(rooms).isNotEmpty();
        assertThat(rooms).anyMatch(r -> r.id().equals(map.entranceRoomId()));
        for (MinimapRoom r : rooms) {
            assertThat(r.gridX()).isBetween(0, map.lattice() - 1);
            assertThat(r.gridY()).isBetween(0, map.lattice() - 1);
        }
    }

    @Test
    void buildMinimap_marksCurrentRoom() {
        DungeonMapGenerator gen = new DungeonMapGenerator();
        List<String> normalIds = IntStream.range(0, DungeonSize.SMALL.normalEncounterCount())
            .mapToObj(i -> "enc_" + i)
            .toList();
        List<List<String>> elites = List.of(
            List.of("e1_0", "e1_1")
        );
        DungeonMap map = gen.generate(DungeonSize.SMALL, normalIds, elites);

        DungeonSessionState state = DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
                List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map)
            .currentRoomId(map.entranceRoomId())
            .encounters(Map.of())
            .bossEncounterIds(List.of("boss_0"))
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .build();

        DungeonMinimapBuilder minimapBuilder = new DungeonMinimapBuilder();
        List<MinimapRoom> rooms = minimapBuilder.build(state);
        assertThat(rooms).anyMatch(r -> r.isCurrent() && r.id().equals(map.entranceRoomId()));
    }

    @Test
    void buildMinimap_withMapSense_revealsEveryRoomTyped() {
        DungeonMapGenerator gen = new DungeonMapGenerator();
        List<String> normalIds = IntStream.range(0, DungeonSize.SMALL.normalEncounterCount())
            .mapToObj(i -> "enc_" + i)
            .toList();
        DungeonMap map = gen.generate(DungeonSize.SMALL, normalIds, List.of(List.of("e1_0", "e1_1")));

        DungeonSessionState state = DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
                List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map)
            .currentRoomId(map.entranceRoomId())
            .encounters(Map.of())
            .bossEncounterIds(List.of("boss_0"))
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .loadout(new DungeonLoadout(List.of(RelicId.MAP_SENSE), null))
            .build();

        List<MinimapRoom> rooms = new DungeonMinimapBuilder().build(state);

        assertThat(rooms).hasSize(map.rooms().size());
        assertThat(rooms).noneMatch(r -> r.type().equals("UNKNOWN"));
    }

    @Test
    void buildMinimap_withoutMapSense_hidesUnvisitedNonAdjacentRooms() {
        DungeonMapGenerator gen = new DungeonMapGenerator();
        List<String> normalIds = IntStream.range(0, DungeonSize.SMALL.normalEncounterCount())
            .mapToObj(i -> "enc_" + i)
            .toList();
        DungeonMap map = gen.generate(DungeonSize.SMALL, normalIds, List.of(List.of("e1_0", "e1_1")));

        DungeonSessionState state = DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
                List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map)
            .currentRoomId(map.entranceRoomId())
            .encounters(Map.of())
            .bossEncounterIds(List.of("boss_0"))
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .build();

        List<MinimapRoom> rooms = new DungeonMinimapBuilder().build(state);

        assertThat(rooms.size()).isLessThan(map.rooms().size());
    }
}
