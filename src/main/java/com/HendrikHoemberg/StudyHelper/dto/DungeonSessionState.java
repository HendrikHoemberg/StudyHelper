package com.HendrikHoemberg.StudyHelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DungeonSessionState(
    DungeonConfig config,
    DungeonMap map,
    String currentRoomId,
    Map<String, DungeonEncounter> encounters,
    List<String> bossEncounterIds,
    boolean won,
    boolean defeated,
    DungeonResources resources,
    DungeonProgress progress,
    DungeonCombat combat,
    DungeonLoadout loadout,
    Set<String> collectedItems
) implements Serializable {

    public DungeonSessionState {
        encounters = Map.copyOf(encounters);
        bossEncounterIds = List.copyOf(bossEncounterIds);
        collectedItems = collectedItems == null ? Set.of() : Set.copyOf(collectedItems);
    }

    // === slice updaters ===
    public DungeonSessionState withResources(DungeonResources r) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, r, progress, combat, loadout, collectedItems);
    }
    public DungeonSessionState withProgress(DungeonProgress p) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, p, combat, loadout, collectedItems);
    }
    public DungeonSessionState withCombat(DungeonCombat c) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, c, loadout, collectedItems);
    }
    public DungeonSessionState withLoadout(DungeonLoadout l) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, l, collectedItems);
    }

    public DungeonSessionState withCollectedItems(Set<String> items) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, items);
    }

    // === single-field top-level updaters ===
    public DungeonSessionState withMap(DungeonMap m) {
        return new DungeonSessionState(config, m, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, collectedItems);
    }
    public DungeonSessionState withCurrentRoomId(String id) {
        return new DungeonSessionState(config, map, id, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, collectedItems);
    }
    public DungeonSessionState withEncounters(Map<String, DungeonEncounter> e) {
        return new DungeonSessionState(config, map, currentRoomId, e, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, collectedItems);
    }
    public DungeonSessionState withWon(boolean w) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            w, defeated, resources, progress, combat, loadout, collectedItems);
    }
    public DungeonSessionState withDefeated(boolean d) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, d, resources, progress, combat, loadout, collectedItems);
    }

    // === views ===
    @JsonIgnore
    public boolean isComplete() {
        return won || defeated;
    }

    public DungeonEncounter activeEncounter() {
        String id = combat.activeEncounterId();
        return id == null ? null : encounters.get(id);
    }

    public DungeonRoom currentRoom() {
        return map.room(currentRoomId);
    }

    // === Builder for multi-field updates and initial state ===
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private DungeonConfig config;
        private DungeonMap map;
        private String currentRoomId;
        private Map<String, DungeonEncounter> encounters = Map.of();
        private List<String> bossEncounterIds = List.of();
        private boolean won;
        private boolean defeated;
        private DungeonResources resources;
        private DungeonProgress progress = new DungeonProgress(0, 0, 0, 0, 0, 0, 0);
        private DungeonCombat combat = DungeonCombat.empty();
        private DungeonLoadout loadout = DungeonLoadout.empty();
        private Set<String> collectedItems = Set.of();

        private Builder() {}

        private Builder(DungeonSessionState s) {
            this.config = s.config;
            this.map = s.map;
            this.currentRoomId = s.currentRoomId;
            this.encounters = s.encounters;
            this.bossEncounterIds = s.bossEncounterIds;
            this.won = s.won;
            this.defeated = s.defeated;
            this.resources = s.resources;
            this.progress = s.progress;
            this.combat = s.combat;
            this.loadout = s.loadout;
            this.collectedItems = s.collectedItems;
        }

        public Builder config(DungeonConfig v) { this.config = v; return this; }
        public Builder map(DungeonMap v) { this.map = v; return this; }
        public Builder currentRoomId(String v) { this.currentRoomId = v; return this; }
        public Builder encounters(Map<String, DungeonEncounter> v) { this.encounters = v; return this; }
        public Builder bossEncounterIds(List<String> v) { this.bossEncounterIds = v; return this; }
        public Builder won(boolean v) { this.won = v; return this; }
        public Builder defeated(boolean v) { this.defeated = v; return this; }
        public Builder resources(DungeonResources v) { this.resources = v; return this; }
        public Builder progress(DungeonProgress v) { this.progress = v; return this; }
        public Builder combat(DungeonCombat v) { this.combat = v; return this; }
        public Builder loadout(DungeonLoadout v) { this.loadout = v; return this; }
        public Builder collectedItems(Set<String> v) { this.collectedItems = v; return this; }

        public DungeonSessionState build() {
            return new DungeonSessionState(
                config, map, currentRoomId, encounters, bossEncounterIds,
                won, defeated, resources, progress, combat, loadout, collectedItems);
        }
    }
}
