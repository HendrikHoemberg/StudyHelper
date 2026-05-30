package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.RelicId;

import java.util.Optional;

class PhoenixFeatherDeathSave implements DeathSave {
    @Override
    public Optional<DungeonSessionState> trySave(DungeonSessionState s) {
        if (s.loadout().ownedRelics().contains(RelicId.PHOENIX_FEATHER)) {
            return Optional.of(s
                .withResources(s.resources().withHealth(1))
                .withLoadout(s.loadout().consumePhoenixFeather()));
        }
        return Optional.empty();
    }
}
