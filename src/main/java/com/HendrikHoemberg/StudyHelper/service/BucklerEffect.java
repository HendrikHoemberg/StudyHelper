package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

class BucklerEffect implements RelicEffect {
    @Override
    public DungeonSessionState onAcquire(DungeonSessionState s) {
        return s.withResources(s.resources()
            .withShieldCap(s.resources().shieldCap() + 1)
            .addShield());
    }
}
