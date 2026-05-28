package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DungeonLoadoutTests {

    @Test
    void empty_factoryReturnsEmptyLoadout() {
        DungeonLoadout l = DungeonLoadout.empty();
        assertThat(l.ownedRelics()).isEmpty();
        assertThat(l.pendingRelicPick()).isNull();
    }

    @Test
    void addRelic_appendsToImmutableCopy() {
        DungeonLoadout l = DungeonLoadout.empty()
            .addRelic(RelicId.COMPASS)
            .addRelic(RelicId.SPECTACLES);
        assertThat(l.ownedRelics()).containsExactly(RelicId.COMPASS, RelicId.SPECTACLES);
    }

    @Test
    void addRelic_preservesPreviousInstance() {
        DungeonLoadout base = DungeonLoadout.empty().addRelic(RelicId.COMPASS);
        DungeonLoadout grown = base.addRelic(RelicId.MAP_SENSE);
        assertThat(base.ownedRelics()).containsExactly(RelicId.COMPASS);
        assertThat(grown.ownedRelics()).containsExactly(RelicId.COMPASS, RelicId.MAP_SENSE);
    }

    @Test
    void withPendingRelicPick_setsAndClears() {
        PendingRelicPick pick = new PendingRelicPick(PendingPickType.TREASURE, "r1",
            List.of(RelicId.COMPASS), null);
        DungeonLoadout l = DungeonLoadout.empty().withPendingRelicPick(pick);
        assertThat(l.pendingRelicPick()).isSameAs(pick);
        assertThat(l.clearPendingPick().pendingRelicPick()).isNull();
    }

    @Test
    void consumePhoenixFeather_removesFirstFeatherIfPresent() {
        DungeonLoadout l = new DungeonLoadout(
            List.of(RelicId.PHOENIX_FEATHER, RelicId.COMPASS), null);
        DungeonLoadout after = l.consumePhoenixFeather();
        assertThat(after.ownedRelics()).containsExactly(RelicId.COMPASS);
    }

    @Test
    void consumePhoenixFeather_isNoOpWhenAbsent() {
        DungeonLoadout l = new DungeonLoadout(List.of(RelicId.COMPASS), null);
        assertThat(l.consumePhoenixFeather().ownedRelics()).containsExactly(RelicId.COMPASS);
    }

    @Test
    void ownedRelics_isImmutable() {
        DungeonLoadout l = new DungeonLoadout(List.of(RelicId.COMPASS), null);
        assertThatThrownBy(() -> l.ownedRelics().add(RelicId.MAP_SENSE))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
