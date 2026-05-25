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
        int q = grade.quality();
        double newEf = Math.max(MIN_EF,
            easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)));

        if (grade == Grade.AGAIN) {
            return new SrsState(0, newEf, 0, today);
        }

        int newReps = repetitions + 1;
        int newInterval;
        if (newReps == 1) {
            if (grade == Grade.HARD) {
                newInterval = 1;
            } else if (grade == Grade.GOOD) {
                newInterval = 2;
            } else { // Grade.EASY
                newInterval = 3;
            }
        } else if (newReps == 2) {
            if (grade == Grade.HARD) {
                newInterval = 3;
            } else if (grade == Grade.GOOD) {
                newInterval = SECOND_INTERVAL;
            } else { // Grade.EASY
                newInterval = (int) Math.round(SECOND_INTERVAL * EASY_BONUS);
            }
        } else {
            newInterval = (int) Math.round(intervalDays * newEf);
            if (grade == Grade.HARD) {
                newInterval = Math.max(intervalDays + 1, (int) Math.round(intervalDays * HARD_MULTIPLIER));
            }
            if (grade == Grade.EASY) {
                newInterval = (int) Math.round(newInterval * EASY_BONUS);
            }
        }

        return new SrsState(newInterval, newEf, newReps, today.plusDays(newInterval));
    }
}
