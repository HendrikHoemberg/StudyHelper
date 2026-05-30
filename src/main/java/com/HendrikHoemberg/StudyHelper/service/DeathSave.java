package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

import java.util.Optional;

public interface DeathSave {
    Optional<DungeonSessionState> trySave(DungeonSessionState s);
}
