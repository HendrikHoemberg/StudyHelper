package com.HendrikHoemberg.StudyHelper.dto;

public enum Grade {
    AGAIN(0),
    HARD(3),
    GOOD(4),
    EASY(5);

    private final int quality;

    Grade(int quality) {
        this.quality = quality;
    }

    public int quality() {
        return quality;
    }
}
