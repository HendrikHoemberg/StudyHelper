package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Grade;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class SrsScheduler {

    public static final double INITIAL_EF = 2.5;
    public static final double MIN_EF = 1.3;
    public static final int FIRST_INTERVAL = 1;
    public static final int SECOND_INTERVAL = 6;
    public static final double HARD_MULTIPLIER = 1.2;
    public static final double EASY_BONUS = 1.3;

    public record SrsState(int intervalDays, double easeFactor, int repetitions, LocalDate dueDate) {}

    public SrsState next(int intervalDays, double easeFactor, int repetitions, Grade grade, LocalDate today) {
        throw new UnsupportedOperationException("not implemented");
    }
}
