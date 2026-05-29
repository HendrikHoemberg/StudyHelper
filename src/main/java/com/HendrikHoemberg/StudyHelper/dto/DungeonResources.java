package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record DungeonResources(
    int health,
    int healthCap,
    int shields,
    int shieldCap,
    int score
) implements Serializable {

    public DungeonResources {
        healthCap = Math.max(0, healthCap);
        shieldCap = Math.max(0, shieldCap);
        health = Math.max(0, Math.min(health, healthCap));
        shields = Math.max(0, Math.min(shields, shieldCap));
        score = Math.max(0, score);
    }

    public DungeonResources withHealth(int v)     { return new DungeonResources(v, healthCap, shields, shieldCap, score); }
    public DungeonResources withShields(int v)    { return new DungeonResources(health, healthCap, v, shieldCap, score); }
    public DungeonResources withScore(int v)      { return new DungeonResources(health, healthCap, shields, shieldCap, v); }
    public DungeonResources withHealthCap(int v)  { return new DungeonResources(health, v, shields, shieldCap, score); }
    public DungeonResources withShieldCap(int v)  { return new DungeonResources(health, healthCap, shields, v, score); }

    public DungeonResources heal(int amount) {
        return withHealth(Math.min(healthCap, health + amount));
    }

    public DungeonResources addScore(int amount) {
        return withScore(score + amount);
    }

    public DungeonResources addShield() {
        if (shields >= shieldCap) return this;
        return withShields(shields + 1);
    }

    public DungeonResources takeHealthDamage(int amount) {
        return withHealth(Math.max(0, health - amount));
    }
}
