package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public sealed interface SecretReward extends Serializable
    permits SecretReward.RelicReward, SecretReward.Bundle {

    record RelicReward(RelicId relic) implements SecretReward {}
    record Bundle(int healToFull, int shieldsGranted, int scoreBonus) implements SecretReward {}
}
