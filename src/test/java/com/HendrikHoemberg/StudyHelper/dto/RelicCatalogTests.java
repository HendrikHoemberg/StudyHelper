package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

class RelicCatalogTests {

    @Test
    void definition_returnsRelicForEveryId() {
        for (RelicId id : RelicId.values()) {
            RelicDefinition def = RelicCatalog.definition(id);
            assertThat(def).isNotNull();
            assertThat(def.id()).isEqualTo(id);
            assertThat(def.nameKey()).startsWith("dungeon.relic.");
            assertThat(def.descKey()).startsWith("dungeon.relic.");
        }
    }

    @Test
    void poolMembership_isExclusive() {
        assertThat(RelicCatalog.idsInPool(RelicPool.COMMON))
            .containsExactlyInAnyOrder(
                RelicId.IRON_PLATE, RelicId.BUCKLER, RelicId.LUCKY_CHARM,
                RelicId.SHARP_FOCUS, RelicId.COMPASS, RelicId.LUCKY_COIN);
        assertThat(RelicCatalog.idsInPool(RelicPool.ELITE))
            .containsExactlyInAnyOrder(
                RelicId.PHOENIX_FEATHER, RelicId.SPECTACLES, RelicId.WAR_BANNER);
        assertThat(RelicCatalog.idsInPool(RelicPool.SHOP_EXCLUSIVE))
            .containsExactlyInAnyOrder(RelicId.MAP_SENSE);
    }

    @Test
    void sample_returnsDistinctRelicsFromPool() {
        List<RelicId> picks = RelicCatalog.sample(RelicPool.COMMON, 3, new Random(42));
        assertThat(picks).hasSize(3).doesNotHaveDuplicates();
        assertThat(picks).allMatch(id ->
            RelicCatalog.definition(id).pool() == RelicPool.COMMON);
    }

    @Test
    void sample_seedIsDeterministic() {
        List<RelicId> a = RelicCatalog.sample(RelicPool.COMMON, 3, new Random(42));
        List<RelicId> b = RelicCatalog.sample(RelicPool.COMMON, 3, new Random(42));
        assertThat(a).isEqualTo(b);
    }
}
