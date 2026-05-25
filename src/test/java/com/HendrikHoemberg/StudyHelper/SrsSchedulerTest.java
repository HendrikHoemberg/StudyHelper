package com.HendrikHoemberg.StudyHelper;

import com.HendrikHoemberg.StudyHelper.dto.Grade;
import com.HendrikHoemberg.StudyHelper.service.SrsScheduler;
import com.HendrikHoemberg.StudyHelper.service.SrsScheduler.SrsState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SrsSchedulerTest {

    private final SrsScheduler scheduler = new SrsScheduler();
    private final LocalDate today = LocalDate.of(2026, 5, 22);

    @Test
    void newCardGoodAdvancesToTwoDays() {
        SrsState s = scheduler.next(0, 2.5, 0, Grade.GOOD, today);
        assertThat(s.repetitions()).isEqualTo(1);
        assertThat(s.intervalDays()).isEqualTo(2);
        assertThat(s.easeFactor()).isCloseTo(2.5, within(0.0001));
        assertThat(s.dueDate()).isEqualTo(today.plusDays(2));
    }

    @Test
    void newCardHardAdvancesToOneDay() {
        SrsState s = scheduler.next(0, 2.5, 0, Grade.HARD, today);
        assertThat(s.repetitions()).isEqualTo(1);
        assertThat(s.intervalDays()).isEqualTo(1);
        assertThat(s.dueDate()).isEqualTo(today.plusDays(1));
    }

    @Test
    void newCardEasyAdvancesToThreeDays() {
        SrsState s = scheduler.next(0, 2.5, 0, Grade.EASY, today);
        assertThat(s.repetitions()).isEqualTo(1);
        assertThat(s.intervalDays()).isEqualTo(3);
        assertThat(s.dueDate()).isEqualTo(today.plusDays(3));
    }

    @Test
    void secondGoodAdvancesToSixDays() {
        SrsState s = scheduler.next(1, 2.5, 1, Grade.GOOD, today);
        assertThat(s.repetitions()).isEqualTo(2);
        assertThat(s.intervalDays()).isEqualTo(6);
        assertThat(s.dueDate()).isEqualTo(today.plusDays(6));
    }

    @Test
    void secondHardAdvancesToThreeDays() {
        SrsState s = scheduler.next(1, 2.5, 1, Grade.HARD, today);
        assertThat(s.repetitions()).isEqualTo(2);
        assertThat(s.intervalDays()).isEqualTo(3);
        assertThat(s.dueDate()).isEqualTo(today.plusDays(3));
    }

    @Test
    void secondEasyAdvancesToEightDays() {
        SrsState s = scheduler.next(1, 2.5, 1, Grade.EASY, today);
        assertThat(s.repetitions()).isEqualTo(2);
        assertThat(s.intervalDays()).isEqualTo(8);
        assertThat(s.dueDate()).isEqualTo(today.plusDays(8));
    }

    @Test
    void thirdGoodMultipliesByEase() {
        SrsState s = scheduler.next(6, 2.5, 2, Grade.GOOD, today);
        assertThat(s.repetitions()).isEqualTo(3);
        assertThat(s.intervalDays()).isEqualTo(15);
        assertThat(s.dueDate()).isEqualTo(today.plusDays(15));
    }

    @Test
    void easyAddsBonusAndRaisesEase() {
        SrsState s = scheduler.next(6, 2.5, 2, Grade.EASY, today);
        assertThat(s.easeFactor()).isCloseTo(2.6, within(0.0001));
        assertThat(s.intervalDays()).isEqualTo(21);
    }

    @Test
    void hardUsesSmallerMultiplierAndLowersEase() {
        SrsState s = scheduler.next(15, 2.5, 3, Grade.HARD, today);
        assertThat(s.easeFactor()).isCloseTo(2.36, within(0.0001));
        assertThat(s.intervalDays()).isEqualTo(18);
        assertThat(s.repetitions()).isEqualTo(4);
    }

    @Test
    void againResetsAndIsDueToday() {
        SrsState s = scheduler.next(15, 2.5, 3, Grade.AGAIN, today);
        assertThat(s.repetitions()).isEqualTo(0);
        assertThat(s.intervalDays()).isEqualTo(0);
        assertThat(s.dueDate()).isEqualTo(today);
        assertThat(s.easeFactor()).isCloseTo(1.7, within(0.0001));
    }

    @Test
    void easeNeverDropsBelowFloor() {
        SrsState s = scheduler.next(1, 1.3, 0, Grade.AGAIN, today);
        assertThat(s.easeFactor()).isEqualTo(1.3);
    }
}
