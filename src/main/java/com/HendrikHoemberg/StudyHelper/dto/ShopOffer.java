package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record ShopOffer(List<ShopOfferEntry> entries, ShopConsumable consumable) implements Serializable {
    public ShopOffer {
        entries = List.copyOf(entries);
    }
}
