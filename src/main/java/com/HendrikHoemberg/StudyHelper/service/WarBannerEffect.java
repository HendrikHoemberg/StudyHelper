package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonRoom;
import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.RoomType;

class WarBannerEffect implements RelicEffect {
    @Override
    public DungeonSessionState onRoomClear(DungeonSessionState s, DungeonRoom room) {
        if (room.type() != RoomType.COMBAT) return s;
        return s.withResources(s.resources().addShield());
    }
}
