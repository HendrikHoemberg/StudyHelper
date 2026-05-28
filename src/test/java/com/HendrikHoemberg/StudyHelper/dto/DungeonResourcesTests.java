package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DungeonResourcesTests {

    private DungeonResources of(int hp, int hpCap, int shields, int shieldCap, int score) {
        return new DungeonResources(hp, hpCap, shields, shieldCap, score);
    }

    @Test
    void withHealth_returnsNewInstanceWithReplacedHealth() {
        DungeonResources r = of(5, 5, 0, 2, 100).withHealth(3);
        assertThat(r.health()).isEqualTo(3);
        assertThat(r.healthCap()).isEqualTo(5);
        assertThat(r.score()).isEqualTo(100);
    }

    @Test
    void withShields_replacesShields() {
        assertThat(of(5, 5, 0, 2, 0).withShields(2).shields()).isEqualTo(2);
    }

    @Test
    void withScore_replacesScore() {
        assertThat(of(5, 5, 0, 2, 0).withScore(50).score()).isEqualTo(50);
    }

    @Test
    void withHealthCap_replacesCap() {
        assertThat(of(5, 5, 0, 2, 0).withHealthCap(7).healthCap()).isEqualTo(7);
    }

    @Test
    void withShieldCap_replacesCap() {
        assertThat(of(5, 5, 0, 2, 0).withShieldCap(3).shieldCap()).isEqualTo(3);
    }

    @Test
    void heal_increasesHealthCappedByHealthCap() {
        assertThat(of(3, 5, 0, 2, 0).heal(1).health()).isEqualTo(4);
        assertThat(of(4, 5, 0, 2, 0).heal(99).health()).isEqualTo(5);
        assertThat(of(5, 7, 0, 2, 0).heal(99).health()).isEqualTo(7);
    }

    @Test
    void heal_byZeroIsNoOp() {
        assertThat(of(3, 5, 0, 2, 0).heal(0).health()).isEqualTo(3);
    }

    @Test
    void addScore_addsToScore() {
        assertThat(of(5, 5, 0, 2, 10).addScore(15).score()).isEqualTo(25);
    }

    @Test
    void addShield_incrementsCappedByShieldCap() {
        assertThat(of(5, 5, 0, 2, 0).addShield().shields()).isEqualTo(1);
        assertThat(of(5, 5, 2, 2, 0).addShield().shields()).isEqualTo(2);
    }

    @Test
    void takeHealthDamage_subtractsCappedAtZero() {
        assertThat(of(5, 5, 0, 2, 0).takeHealthDamage(1).health()).isEqualTo(4);
        assertThat(of(1, 5, 0, 2, 0).takeHealthDamage(5).health()).isEqualTo(0);
        assertThat(of(0, 5, 0, 2, 0).takeHealthDamage(1).health()).isEqualTo(0);
    }

    @Test
    void takeHealthDamage_doesNotTouchShields() {
        DungeonResources r = of(5, 5, 2, 2, 0).takeHealthDamage(1);
        assertThat(r.shields()).isEqualTo(2);
        assertThat(r.health()).isEqualTo(4);
    }
}
