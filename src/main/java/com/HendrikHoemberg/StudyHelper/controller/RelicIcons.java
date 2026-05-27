package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.RelicId;

public final class RelicIcons {
    private RelicIcons() {}

    public static String iconFor(RelicId id) {
        return switch (id) {
            case IRON_PLATE -> "lucide:heart-plus";
            case BUCKLER -> "lucide:shield-plus";
            case LUCKY_CHARM -> "lucide:clover";
            case SHARP_FOCUS -> "lucide:target";
            case COMPASS -> "lucide:compass";
            case LUCKY_COIN -> "lucide:coin";
            case PHOENIX_FEATHER -> "lucide:feather";
            case SPECTACLES -> "lucide:glasses";
            case WAR_BANNER -> "lucide:flag";
            case MAP_SENSE -> "lucide:map";
        };
    }
}
