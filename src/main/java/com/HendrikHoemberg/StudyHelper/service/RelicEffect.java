package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonRoom;
import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

public interface RelicEffect {

    default DungeonSessionState onAcquire(DungeonSessionState s) { return s; }

    default DungeonSessionState onRoomClear(DungeonSessionState s, DungeonRoom room) { return s; }

    default int modifyStreakThreshold(int base) { return base; }

    default int bonusScoreOnCorrect() { return 0; }
}
