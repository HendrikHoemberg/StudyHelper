package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record TreasureOffer(List<RelicId> relics) implements Serializable {
    public TreasureOffer {
        relics = List.copyOf(relics);
    }
}
