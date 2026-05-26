package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonSizeTests {

    @Test
    void availableForUsableItems_restrictsSizesByPromptCount() {
        assertThat(DungeonSize.availableForUsableItems(0)).isEmpty();
        assertThat(DungeonSize.availableForUsableItems(7)).isEmpty();
        assertThat(DungeonSize.availableForUsableItems(8)).containsExactly(DungeonSize.SMALL);
        assertThat(DungeonSize.availableForUsableItems(11)).containsExactly(DungeonSize.SMALL);
        assertThat(DungeonSize.availableForUsableItems(12)).containsExactly(DungeonSize.SMALL, DungeonSize.MEDIUM);
        assertThat(DungeonSize.availableForUsableItems(19)).containsExactly(DungeonSize.SMALL, DungeonSize.MEDIUM);
        assertThat(DungeonSize.availableForUsableItems(20)).containsExactly(DungeonSize.SMALL, DungeonSize.MEDIUM, DungeonSize.LARGE);
    }

    @Test
    void sizesExposePromptSplit() {
        assertThat(DungeonSize.SMALL.totalPrompts()).isEqualTo(8);
        assertThat(DungeonSize.SMALL.normalEncounterCount()).isEqualTo(6);
        assertThat(DungeonSize.SMALL.bossPromptCount()).isEqualTo(2);

        assertThat(DungeonSize.MEDIUM.totalPrompts()).isEqualTo(12);
        assertThat(DungeonSize.MEDIUM.normalEncounterCount()).isEqualTo(9);
        assertThat(DungeonSize.MEDIUM.bossPromptCount()).isEqualTo(3);

        assertThat(DungeonSize.LARGE.totalPrompts()).isEqualTo(20);
        assertThat(DungeonSize.LARGE.normalEncounterCount()).isEqualTo(15);
        assertThat(DungeonSize.LARGE.bossPromptCount()).isEqualTo(5);
    }

    @Test
    void isAvailableFor_isTrueOnlyWhenEnoughItemsExist() {
        assertThat(DungeonSize.SMALL.isAvailableFor(8)).isTrue();
        assertThat(DungeonSize.MEDIUM.isAvailableFor(8)).isFalse();
        assertThat(DungeonSize.MEDIUM.isAvailableFor(12)).isTrue();
        assertThat(DungeonSize.LARGE.isAvailableFor(19)).isFalse();
        assertThat(DungeonSize.LARGE.isAvailableFor(20)).isTrue();
    }
}
