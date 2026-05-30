package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonBalance;

class SharpFocusEffect implements RelicEffect {
    @Override
    public int modifyStreakThreshold(int base) {
        return Math.min(base, DungeonBalance.SHARP_FOCUS_STREAK);
    }
}
