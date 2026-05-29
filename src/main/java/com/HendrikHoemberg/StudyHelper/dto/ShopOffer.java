package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record ShopOffer(List<ShopOfferEntry> entries) implements Serializable {
    public ShopOffer {
        entries = List.copyOf(entries);
    }
}
