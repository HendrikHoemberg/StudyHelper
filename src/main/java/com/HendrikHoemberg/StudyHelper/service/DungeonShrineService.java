package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DungeonShrineService {

    public record ShrineRollOutcome(int roll, RelicId grantedRelic) {}

    private final Random rng;

    public DungeonShrineService() {
        this(new Random());
    }

    public DungeonShrineService(Random rng) {
        this.rng = rng;
    }

    public ShrineRollOutcome computeOutcome(DungeonSessionState state) {
        int roll = rng.nextInt(6) + 1;
        if (roll != 6) return new ShrineRollOutcome(roll, null);

        List<RelicId> unowned = new ArrayList<>();
        for (RelicId r : RelicId.values()) {
            if (!state.loadout().ownedRelics().contains(r)) unowned.add(r);
        }
        RelicId grantedRelic = unowned.isEmpty()
            ? RelicId.IRON_PLATE
            : unowned.get(rng.nextInt(unowned.size()));
        return new ShrineRollOutcome(roll, grantedRelic);
    }

    public DungeonSessionState applyRoll(DungeonSessionState state, ShrineRollOutcome outcome) {
        if (outcome.roll() == 6) {
            if (outcome.grantedRelic() == null) {
                return state.withDefeated(false);
            }
            DungeonSessionState granted = state.withLoadout(state.loadout().addRelic(outcome.grantedRelic()));
            return RelicEffects.onAcquire(granted, outcome.grantedRelic()).withDefeated(false);
        }
        DungeonResources damaged = state.resources().takeHealthDamage(1);
        return state.withResources(damaged).withDefeated(damaged.health() == 0);
    }

    public DungeonSessionState drink(DungeonSessionState state) {
        DungeonResources nextResources = state.resources().heal(1);
        return leave(state).withResources(nextResources);
    }

    public DungeonSessionState leave(DungeonSessionState state) {
        DungeonRoom room = state.map().room(state.currentRoomId());
        if (room == null) return state;

        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        rooms.put(room.id(), room.withCleared(true));
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());

        return state
            .withMap(nextMap)
            .withLoadout(state.loadout().clearPendingPick());
    }
}
