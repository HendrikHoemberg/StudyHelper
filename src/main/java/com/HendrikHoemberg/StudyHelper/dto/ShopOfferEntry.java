package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record ShopOfferEntry(RelicId relic, int price) implements Serializable {}
