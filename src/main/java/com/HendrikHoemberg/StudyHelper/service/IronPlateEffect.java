package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

class IronPlateEffect implements RelicEffect {
    @Override
    public DungeonSessionState onAcquire(DungeonSessionState s) {
        return s.withResources(s.resources()
            .withHealthCap(s.resources().healthCap() + 1)
            .heal(1));
    }
}
