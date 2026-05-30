package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.RelicId;

import java.util.Optional;

class LuckyCoinMitigator implements DamageMitigator {
    @Override
    public int priority() { return 10; }

    @Override
    public Optional<DungeonSessionState> tryAbsorb(DungeonSessionState s, int damage) {
        int owned = (int) s.loadout().ownedRelics().stream()
            .filter(r -> r == RelicId.LUCKY_COIN).count();
        if (s.progress().luckyCoinsConsumed() < owned) {
            return Optional.of(s.withProgress(s.progress().consumeLuckyCoin()));
        }
        return Optional.empty();
    }
}
