package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonMapGeneratorTests {

    private final DungeonMapGenerator generator = new DungeonMapGenerator();

    @Test
    void generate_smallDungeonHasExpectedRoomTypeCounts() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(1));
        Map<RoomType, Long> counts = countByType(map);
        assertThat(counts.get(RoomType.ENTRANCE)).isEqualTo(1);
        assertThat(counts.get(RoomType.BOSS)).isEqualTo(1);
        assertThat(counts.get(RoomType.SHOP)).isEqualTo(1);
        assertThat(counts.get(RoomType.HEAL)).isEqualTo(1);
        assertThat(counts.get(RoomType.TREASURE)).isEqualTo(1);
        assertThat(counts.get(RoomType.ELITE)).isEqualTo(1);
        assertThat(counts.get(RoomType.COMBAT)).isEqualTo(6);
        assertThat(counts.getOrDefault(RoomType.SECRET, 0L)).isEqualTo(1);
    }

    @Test
    void generate_mediumDungeonHasExpectedRoomTypeCounts() {
        DungeonMap map = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(1));
        Map<RoomType, Long> counts = countByType(map);
        assertThat(counts.get(RoomType.COMBAT)).isEqualTo(9);
        assertThat(counts.get(RoomType.ELITE)).isEqualTo(2);
        assertThat(counts.get(RoomType.TREASURE)).isEqualTo(1);
    }

    @Test
    void generate_largeDungeonHasExpectedRoomTypeCounts() {
        DungeonMap map = generator.generate(
            DungeonSize.LARGE, normalIds(15), eliteGroups(3, 3), new Random(1));
        Map<RoomType, Long> counts = countByType(map);
        assertThat(counts.get(RoomType.COMBAT)).isEqualTo(15);
        assertThat(counts.get(RoomType.ELITE)).isEqualTo(3);
        assertThat(counts.get(RoomType.TREASURE)).isEqualTo(2);
    }

    @Test
    void generate_bossIsReachableFromEntrance() {
        DungeonMap map = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(2));
        Set<String> reachable = reach(map);
        assertThat(reachable).contains(map.bossRoomId());
    }

    @Test
    void generate_allNonSecretRoomsReachable() {
        DungeonMap map = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(3));
        Set<String> reachable = reach(map);
        Set<String> nonSecret = map.rooms().values().stream()
            .filter(r -> r.type() != RoomType.SECRET)
            .map(DungeonRoom::id)
            .collect(Collectors.toSet());
        assertThat(reachable).containsAll(nonSecret);
    }

    @Test
    void generate_secretRoomHasNoDoors() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(4));
        DungeonRoom secret = map.rooms().values().stream()
            .filter(r -> r.type() == RoomType.SECRET)
            .findFirst().orElseThrow();
        assertThat(secret.doors()).isEmpty();
    }

    @Test
    void generate_seedIsDeterministic() {
        DungeonMap a = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(99));
        DungeonMap b = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(99));
        assertThat(a.rooms().keySet()).isEqualTo(b.rooms().keySet());
        assertThat(a.bossRoomId()).isEqualTo(b.bossRoomId());
        for (String id : a.rooms().keySet()) {
            assertThat(a.rooms().get(id).type())
                .as("type of %s", id)
                .isEqualTo(b.rooms().get(id).type());
        }
    }

    @Test
    void generate_eliteRoomsCarryGauntletGroupOfCorrectSize() {
        DungeonMap map = generator.generate(
            DungeonSize.LARGE, normalIds(15), eliteGroups(3, 3), new Random(5));
        List<DungeonRoom> elites = map.rooms().values().stream()
            .filter(r -> r.type() == RoomType.ELITE)
            .toList();
        assertThat(elites).hasSize(3);
        for (DungeonRoom e : elites) {
            assertThat(e.gauntletGroup()).hasSize(3);
            assertThat(e.encounterId()).isEqualTo(e.gauntletGroup().get(0));
            assertThat(e.eliteOffer()).isNotNull();
            assertThat(e.eliteOffer().relics()).hasSize(2);
        }
    }

    @Test
    void generate_shopRoomHasOfferWithTwoEntriesAndConsumable() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(6));
        DungeonRoom shop = map.rooms().values().stream()
            .filter(r -> r.type() == RoomType.SHOP).findFirst().orElseThrow();
        assertThat(shop.shopOffer()).isNotNull();
        assertThat(shop.shopOffer().entries()).hasSize(2);
        assertThat(shop.shopOffer().consumable()).isNotNull();
    }

    @Test
    void generate_treasureRoomHasThreeRelics() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(7));
        DungeonRoom t = map.rooms().values().stream()
            .filter(r -> r.type() == RoomType.TREASURE).findFirst().orElseThrow();
        assertThat(t.treasureOffer()).isNotNull();
        assertThat(t.treasureOffer().relics()).hasSize(3);
    }

    @Test
    void generate_everyRoomHasTwoOrThreePotLoot() {
        DungeonMap map = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(7));
        for (DungeonRoom r : map.rooms().values()) {
            assertThat(r.potLoot().size()).isBetween(2, 3);
        }
    }

    @Test
    void generate_potLootDistributionRoughly65_10_25() {
        int coin = 0, shield = 0, empty = 0;
        for (int seed = 0; seed < 60; seed++) {
            DungeonMap map = generator.generate(
                DungeonSize.LARGE, normalIds(15), eliteGroups(3, 3), new Random(seed));
            for (DungeonRoom r : map.rooms().values()) {
                for (PotLoot loot : r.potLoot()) {
                    switch (loot) {
                        case COIN -> coin++;
                        case SHIELD -> shield++;
                        case EMPTY -> empty++;
                    }
                }
            }
        }
        int total = coin + shield + empty;
        assertThat(total).isGreaterThan(2000);
        assertThat((double) coin / total).isBetween(0.60, 0.70);
        assertThat((double) shield / total).isBetween(0.06, 0.14);
        assertThat((double) empty / total).isBetween(0.20, 0.30);
    }

    @Test
    void generate_entranceIsVisitedAndCleared() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(8));
        DungeonRoom entrance = map.rooms().get(map.entranceRoomId());
        assertThat(entrance.visited()).isTrue();
        assertThat(entrance.cleared()).isTrue();
    }

    private List<String> normalIds(int n) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add("enc_" + i);
        return ids;
    }

    private List<List<String>> eliteGroups(int groups, int cardsEach) {
        List<List<String>> result = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            List<String> group = new ArrayList<>();
            for (int c = 0; c < cardsEach; c++) group.add("elite_" + g + "_" + c);
            result.add(group);
        }
        return result;
    }

    private Map<RoomType, Long> countByType(DungeonMap map) {
        return map.rooms().values().stream()
            .collect(Collectors.groupingBy(DungeonRoom::type, Collectors.counting()));
    }

    private Set<String> reach(DungeonMap map) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(map.entranceRoomId());
        queue.add(map.entranceRoomId());
        while (!queue.isEmpty()) {
            String node = queue.poll();
            for (String neighbor : map.rooms().get(node).doors().values()) {
                if (seen.add(neighbor)) queue.add(neighbor);
            }
        }
        return seen;
    }
}
