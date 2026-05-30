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
    Set<String> collectedItems,
    List<Revenant> revenants,
    List<Revenant> revenantGraveyard
) implements Serializable {

    public DungeonSessionState {
        encounters = Map.copyOf(encounters);
        bossEncounterIds = List.copyOf(bossEncounterIds);
        collectedItems = collectedItems == null ? Set.of() : Set.copyOf(collectedItems);
        revenants = revenants == null ? List.of() : List.copyOf(revenants);
        revenantGraveyard = revenantGraveyard == null ? List.of() : List.copyOf(revenantGraveyard);
    }

    // === slice updaters ===
    public DungeonSessionState withResources(DungeonResources r) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, r, progress, combat, loadout, collectedItems, revenants, revenantGraveyard);
    }
    public DungeonSessionState withProgress(DungeonProgress p) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, p, combat, loadout, collectedItems, revenants, revenantGraveyard);
    }
    public DungeonSessionState withCombat(DungeonCombat c) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, c, loadout, collectedItems, revenants, revenantGraveyard);
    }
    public DungeonSessionState withLoadout(DungeonLoadout l) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, l, collectedItems, revenants, revenantGraveyard);
    }

    public DungeonSessionState withCollectedItems(Set<String> items) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, items, revenants, revenantGraveyard);
    }

    public DungeonSessionState withRevenants(List<Revenant> r) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, collectedItems, r, revenantGraveyard);
    }
    public DungeonSessionState withRevenantGraveyard(List<Revenant> g) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, collectedItems, revenants, g);
    }

    // === single-field top-level updaters ===
    public DungeonSessionState withMap(DungeonMap m) {
        return new DungeonSessionState(config, m, currentRoomId, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, collectedItems, revenants, revenantGraveyard);
    }
    public DungeonSessionState withCurrentRoomId(String id) {
        return new DungeonSessionState(config, map, id, encounters, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, collectedItems, revenants, revenantGraveyard);
    }
    public DungeonSessionState withEncounters(Map<String, DungeonEncounter> e) {
        return new DungeonSessionState(config, map, currentRoomId, e, bossEncounterIds,
            won, defeated, resources, progress, combat, loadout, collectedItems, revenants, revenantGraveyard);
    }
    public DungeonSessionState withWon(boolean w) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            w, defeated, resources, progress, combat, loadout, collectedItems, revenants, revenantGraveyard);
    }
    public DungeonSessionState withDefeated(boolean d) {
        return new DungeonSessionState(config, map, currentRoomId, encounters, bossEncounterIds,
            won, d, resources, progress, combat, loadout, collectedItems, revenants, revenantGraveyard);
    }

    // === views ===
    @JsonIgnore
    public boolean isComplete() {
        return won || defeated;
    }

    @JsonIgnore
    public boolean isCoherent() {
        if (currentRoomId == null || map == null || map.room(currentRoomId) == null) return false;
        String active = combat.activeEncounterId();
        if (active != null && !encounters.containsKey(active)) return false;
        for (String queuedId : combat.gauntletQueue()) {
            if (!encounters.containsKey(queuedId)) return false;
        }
        PendingRelicPick pick = loadout.pendingRelicPick();
        if (pick != null && map.room(pick.roomId()) == null) return false;
        if (won && defeated) return false;
        if (combat.bossIndex() < 0 || combat.bossIndex() > bossEncounterIds.size()) return false;
        for (String bossId : bossEncounterIds) {
            if (!encounters.containsKey(bossId)) return false;
        }
        for (Revenant rev : revenants) {
            if (map.room(rev.currentRoomId()) == null) return false;
            if (map.room(rev.originRoomId()) == null) return false;
            if (!encounters.containsKey(rev.encounterId())) return false;
        }
        for (Revenant rev : revenantGraveyard) {
            if (map.room(rev.originRoomId()) == null) return false;
            if (!encounters.containsKey(rev.encounterId())) return false;
        }
        String activeRevenant = combat.activeRevenantId();
        if (activeRevenant != null && !encounters.containsKey(activeRevenant)) return false;
        return true;
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
        private DungeonProgress progress = new DungeonProgress(0, 0, 0, 0, 0, 0, 0, 0);
        private DungeonCombat combat = DungeonCombat.empty();
        private DungeonLoadout loadout = DungeonLoadout.empty();
        private Set<String> collectedItems = Set.of();
        private List<Revenant> revenants = List.of();
        private List<Revenant> revenantGraveyard = List.of();

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
            this.revenants = s.revenants;
            this.revenantGraveyard = s.revenantGraveyard;
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
        public Builder revenants(List<Revenant> v) { this.revenants = v; return this; }
        public Builder revenantGraveyard(List<Revenant> v) { this.revenantGraveyard = v; return this; }

        public DungeonSessionState build() {
            return new DungeonSessionState(
                config, map, currentRoomId, encounters, bossEncounterIds,
                won, defeated, resources, progress, combat, loadout, collectedItems,
                revenants, revenantGraveyard);
        }
    }
}
