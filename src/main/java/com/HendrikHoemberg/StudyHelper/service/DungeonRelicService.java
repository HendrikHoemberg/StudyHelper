package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.RelicId;
import org.springframework.stereotype.Service;

@Service
public class DungeonRelicService {

    public DungeonSessionState pick(DungeonSessionState state, RelicId relicId) {
        return state;
    }

    public DungeonSessionState buy(DungeonSessionState state, RelicId relicId) {
        return state;
    }

    public DungeonSessionState skipShop(DungeonSessionState state) {
        return state;
    }
}
