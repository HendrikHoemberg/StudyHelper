package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record ShopConsumable(String i18nKey, int price, int healAmount) implements Serializable {}
