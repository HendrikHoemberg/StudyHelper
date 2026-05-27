package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record PendingRelicPick(
    PendingPickType type,
    String roomId,
    List<RelicId> offer,
    ShopOffer shopOffer
) implements Serializable {
    public PendingRelicPick {
        offer = offer == null ? List.of() : List.copyOf(offer);
    }
}
