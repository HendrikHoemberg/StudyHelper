package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonMap;
import com.HendrikHoemberg.StudyHelper.dto.DungeonPosition;
import com.HendrikHoemberg.StudyHelper.dto.DungeonSize;
import com.HendrikHoemberg.StudyHelper.dto.DungeonTile;
import com.HendrikHoemberg.StudyHelper.dto.DungeonTileType;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonMapGeneratorTests {

    private final DungeonMapGenerator generator = new DungeonMapGenerator();

    @Test
    void generate_placesEntranceBossAndNormalEncountersForEachSize() {
        assertCounts(DungeonSize.SMALL);
        assertCounts(DungeonSize.MEDIUM);
        assertCounts(DungeonSize.LARGE);
    }

    @Test
    void generate_makesBossAndAllEncountersReachable() {
        DungeonMap map = generator.generate(DungeonSize.LARGE, encounterIds(DungeonSize.LARGE.normalEncounterCount()));

        Set<DungeonPosition> reachable = reachablePositions(map);

        assertThat(reachable).contains(map.boss());
        List<DungeonPosition> encounterPositions = map.tiles().values().stream()
            .filter(tile -> tile.type() == DungeonTileType.ENCOUNTER)
            .map(DungeonTile::position)
            .toList();
        assertThat(reachable).containsAll(encounterPositions);
    }

    @Test
    void generate_containsAtLeastOneRoomSizedOpenArea() {
        DungeonMap map = generator.generate(DungeonSize.MEDIUM, encounterIds(DungeonSize.MEDIUM.normalEncounterCount()));

        boolean hasRoom = map.tiles().values().stream()
            .filter(DungeonTile::walkable)
            .map(DungeonTile::position)
            .anyMatch(position ->
                walkable(map, position)
                    && walkable(map, new DungeonPosition(position.x() + 1, position.y()))
                    && walkable(map, new DungeonPosition(position.x(), position.y() + 1))
                    && walkable(map, new DungeonPosition(position.x() + 1, position.y() + 1))
            );

        assertThat(hasRoom).isTrue();
    }

    @Test
    void generate_revealsEntranceAndAdjacentTilesOnly() {
        DungeonMap map = generator.generate(DungeonSize.SMALL, encounterIds(DungeonSize.SMALL.normalEncounterCount()));

        assertThat(map.tileAt(map.entrance()).revealed()).isTrue();
        long revealed = map.tiles().values().stream().filter(DungeonTile::revealed).count();

        assertThat(revealed).isGreaterThanOrEqualTo(3);
        assertThat(revealed).isLessThan(12);
    }

    @Test
    void generate_isDeterministicGivenSameSeed() {
        DungeonMap a = generator.generate(DungeonSize.MEDIUM,
            encounterIds(DungeonSize.MEDIUM.normalEncounterCount()),
            List.of(), new Random(42L));
        DungeonMap b = generator.generate(DungeonSize.MEDIUM,
            encounterIds(DungeonSize.MEDIUM.normalEncounterCount()),
            List.of(), new Random(42L));
        assertThat(a.tiles().keySet()).isEqualTo(b.tiles().keySet());
        for (DungeonPosition p : a.tiles().keySet()) {
            assertThat(a.tiles().get(p).type()).isEqualTo(b.tiles().get(p).type());
        }
        assertThat(a.entrance()).isEqualTo(b.entrance());
        assertThat(a.boss()).isEqualTo(b.boss());
    }

    @Test
    void generate_placesElitesWhenProvided() {
        List<List<String>> gauntlets = List.of(List.of("e1a", "e1b"));
        DungeonMap map = generator.generate(DungeonSize.MEDIUM,
            encounterIds(DungeonSize.MEDIUM.normalEncounterCount()),
            gauntlets, new Random(7L));
        long eliteTiles = map.tiles().values().stream()
            .filter(t -> t.type() == DungeonTileType.ELITE).count();
        assertThat(eliteTiles).isEqualTo(1);
        assertThat(map.gauntletGroups()).hasSize(1);
        assertThat(map.gauntletGroups().values().iterator().next()).containsExactly("e1a", "e1b");
    }

    private void assertCounts(DungeonSize size) {
        DungeonMap map = generator.generate(size, encounterIds(size.normalEncounterCount()));

        assertThat(map.tileAt(map.entrance()).type()).isEqualTo(DungeonTileType.ENTRANCE);
        assertThat(map.tileAt(map.boss()).type()).isEqualTo(DungeonTileType.BOSS);
        assertThat(map.tiles().values().stream().filter(tile -> tile.type() == DungeonTileType.ENCOUNTER)).hasSize(size.normalEncounterCount());
        assertThat(map.tiles().values().stream().filter(tile -> tile.type() == DungeonTileType.HEAL).count()).isGreaterThanOrEqualTo(1);
        assertThat(map.tiles().values().stream().filter(tile -> tile.type() == DungeonTileType.TREASURE).count()).isGreaterThanOrEqualTo(1);
    }

    private List<String> encounterIds(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> "e" + i)
            .toList();
    }

    private boolean walkable(DungeonMap map, DungeonPosition position) {
        DungeonTile tile = map.tileAt(position);
        return tile != null && tile.walkable();
    }

    private Set<DungeonPosition> reachablePositions(DungeonMap map) {
        Set<DungeonPosition> seen = new HashSet<>();
        ArrayDeque<DungeonPosition> queue = new ArrayDeque<>();
        queue.add(map.entrance());
        seen.add(map.entrance());
        while (!queue.isEmpty()) {
            DungeonPosition cur = queue.removeFirst();
            for (var direction : com.HendrikHoemberg.StudyHelper.dto.DungeonDirection.values()) {
                DungeonPosition next = cur.move(direction);
                DungeonTile tile = map.tileAt(next);
                if (tile != null && tile.walkable() && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }
}
