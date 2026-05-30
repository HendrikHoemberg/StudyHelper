package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

import java.util.Optional;

class ShieldMitigator implements DamageMitigator {
    @Override
    public int priority() { return 20; }

    @Override
    public Optional<DungeonSessionState> tryAbsorb(DungeonSessionState s, int damage) {
        if (s.resources().shields() > 0) {
            return Optional.of(s
                .withResources(s.resources().withShields(s.resources().shields() - 1))
                .withProgress(s.progress().recordShieldUsed()));
        }
        return Optional.empty();
    }
}
