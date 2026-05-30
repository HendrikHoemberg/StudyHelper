package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonBalance;

class LuckyCharmEffect implements RelicEffect {
    @Override
    public int bonusScoreOnCorrect() {
        return DungeonBalance.LUCKY_CHARM_BONUS;
    }
}
