package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

import java.util.Optional;

public interface DamageMitigator {
    int priority();
    Optional<DungeonSessionState> tryAbsorb(DungeonSessionState s, int damage);
}
