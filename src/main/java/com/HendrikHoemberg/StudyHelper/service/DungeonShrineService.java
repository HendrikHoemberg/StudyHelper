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

    DungeonShrineService(Random rng) {
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
        DungeonResources nextResources = state.resources();
        DungeonLoadout nextLoadout = state.loadout();
        boolean defeated = false;

        if (outcome.roll() == 6) {
            if (outcome.grantedRelic() != null) {
                nextLoadout = nextLoadout.addRelic(outcome.grantedRelic());
                if (outcome.grantedRelic() == RelicId.IRON_PLATE) {
                    nextResources = nextResources
                        .withHealthCap(nextResources.healthCap() + 1)
                        .heal(1);
                } else if (outcome.grantedRelic() == RelicId.BUCKLER) {
                    nextResources = nextResources
                        .withShieldCap(nextResources.shieldCap() + 1)
                        .addShield();
                }
            }
        } else {
            nextResources = nextResources.takeHealthDamage(1);
            if (nextResources.health() == 0) defeated = true;
        }

        return state
            .withResources(nextResources)
            .withLoadout(nextLoadout)
            .withDefeated(defeated);
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
