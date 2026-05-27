package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonDamageTests {

    private DungeonSessionState stateWithHealth(int health) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        DungeonPosition entrance = new DungeonPosition(0, 0);
        DungeonMap map = new DungeonMap(1, 1, entrance, entrance, Map.of());
        return new DungeonSessionState(
            config, map, entrance, Map.of(), List.of(), 0, null,
            health, 0, 0, 0, Set.of(), false, false,
            0, 0, List.of(), 0, 0, 0);
    }

    @Test
    void takeDamage_reducesHealthByAmount() {
        DungeonSessionState before = stateWithHealth(5);
        DungeonSessionState after = DungeonDamage.takeDamage(before, 1);
        assertThat(after.health()).isEqualTo(4);
        assertThat(after.defeated()).isFalse();
    }

    @Test
    void takeDamage_marksDefeatedWhenHealthHitsZero() {
        DungeonSessionState before = stateWithHealth(1);
        DungeonSessionState after = DungeonDamage.takeDamage(before, 1);
        assertThat(after.health()).isZero();
        assertThat(after.defeated()).isTrue();
    }

    @Test
    void takeDamage_clampsHealthAtZeroIfOverkill() {
        DungeonSessionState before = stateWithHealth(1);
        DungeonSessionState after = DungeonDamage.takeDamage(before, 5);
        assertThat(after.health()).isZero();
        assertThat(after.defeated()).isTrue();
    }

    @Test
    void heal_addsAmountCappedAtFive() {
        DungeonSessionState before = stateWithHealth(3);
        DungeonSessionState after = DungeonDamage.heal(before, 10);
        assertThat(after.health()).isEqualTo(5);
    }

    @Test
    void heal_doesNotChangeAtFullHealth() {
        DungeonSessionState before = stateWithHealth(5);
        DungeonSessionState after = DungeonDamage.heal(before, 2);
        assertThat(after.health()).isEqualTo(5);
    }

    @Test
    void takeDamage_consumesShieldBeforeHealth() {
        DungeonSessionState before = new DungeonSessionState(
            new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
            new DungeonMap(1, 1, new DungeonPosition(0,0), new DungeonPosition(0,0), Map.of()),
            new DungeonPosition(0,0), Map.of(), List.of(), 0, null,
            5, 0, 0, 0, Set.of(), false, false,
            0, 1, List.of(), 0, 0, 0);
        DungeonSessionState after = DungeonDamage.takeDamage(before, 1);
        assertThat(after.health()).isEqualTo(5);
        assertThat(after.shields()).isZero();
        assertThat(after.shieldsUsed()).isEqualTo(1);
    }
}
