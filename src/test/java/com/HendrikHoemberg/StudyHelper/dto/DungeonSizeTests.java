package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonSizeTests {

    @Test
    void availableForUsableItems_restrictsSizesByPromptCount() {
        assertThat(DungeonSize.availableForUsableItems(0)).isEmpty();
        assertThat(DungeonSize.availableForUsableItems(7)).isEmpty();
        assertThat(DungeonSize.availableForUsableItems(8)).isEmpty();
        assertThat(DungeonSize.availableForUsableItems(10)).containsExactly(DungeonSize.SMALL);
        assertThat(DungeonSize.availableForUsableItems(15)).containsExactly(DungeonSize.SMALL);
        assertThat(DungeonSize.availableForUsableItems(16)).containsExactly(DungeonSize.SMALL, DungeonSize.MEDIUM);
        assertThat(DungeonSize.availableForUsableItems(28)).containsExactly(DungeonSize.SMALL, DungeonSize.MEDIUM);
        assertThat(DungeonSize.availableForUsableItems(29)).containsExactly(DungeonSize.SMALL, DungeonSize.MEDIUM, DungeonSize.LARGE);
    }

    @Test
    void sizesExposePromptSplit() {
        assertThat(DungeonSize.SMALL.totalPrompts()).isEqualTo(10);
        assertThat(DungeonSize.SMALL.normalEncounterCount()).isEqualTo(6);
        assertThat(DungeonSize.SMALL.bossPromptCount()).isEqualTo(2);

        assertThat(DungeonSize.MEDIUM.totalPrompts()).isEqualTo(16);
        assertThat(DungeonSize.MEDIUM.normalEncounterCount()).isEqualTo(9);
        assertThat(DungeonSize.MEDIUM.bossPromptCount()).isEqualTo(3);

        assertThat(DungeonSize.LARGE.totalPrompts()).isEqualTo(29);
        assertThat(DungeonSize.LARGE.normalEncounterCount()).isEqualTo(15);
        assertThat(DungeonSize.LARGE.bossPromptCount()).isEqualTo(5);
    }

    @Test
    void isAvailableFor_isTrueOnlyWhenEnoughItemsExist() {
        assertThat(DungeonSize.SMALL.isAvailableFor(10)).isTrue();
        assertThat(DungeonSize.MEDIUM.isAvailableFor(10)).isFalse();
        assertThat(DungeonSize.MEDIUM.isAvailableFor(16)).isTrue();
        assertThat(DungeonSize.LARGE.isAvailableFor(28)).isFalse();
        assertThat(DungeonSize.LARGE.isAvailableFor(29)).isTrue();
    }
}
