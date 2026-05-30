package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record DungeonProgress(
    int answeredCount,
    int correctCount,
    int streak,
    int longestStreak,
    int elitesCleared,
    int shieldsUsed,
    int luckyCoinsConsumed,
    int revenantsMastered
) implements Serializable {

    public DungeonProgress {
        answeredCount = Math.max(0, answeredCount);
        correctCount = Math.max(0, correctCount);
        streak = Math.max(0, streak);
        longestStreak = Math.max(0, longestStreak);
        elitesCleared = Math.max(0, elitesCleared);
        shieldsUsed = Math.max(0, shieldsUsed);
        luckyCoinsConsumed = Math.max(0, luckyCoinsConsumed);
        revenantsMastered = Math.max(0, revenantsMastered);
    }

    public DungeonProgress withAnsweredCount(int v) {
        return new DungeonProgress(v, correctCount, streak, longestStreak, elitesCleared, shieldsUsed, luckyCoinsConsumed, revenantsMastered);
    }
    public DungeonProgress withCorrectCount(int v) {
        return new DungeonProgress(answeredCount, v, streak, longestStreak, elitesCleared, shieldsUsed, luckyCoinsConsumed, revenantsMastered);
    }
    public DungeonProgress withStreak(int v) {
        return new DungeonProgress(answeredCount, correctCount, v, longestStreak, elitesCleared, shieldsUsed, luckyCoinsConsumed, revenantsMastered);
    }
    public DungeonProgress withLongestStreak(int v) {
        return new DungeonProgress(answeredCount, correctCount, streak, v, elitesCleared, shieldsUsed, luckyCoinsConsumed, revenantsMastered);
    }
    public DungeonProgress withElitesCleared(int v) {
        return new DungeonProgress(answeredCount, correctCount, streak, longestStreak, v, shieldsUsed, luckyCoinsConsumed, revenantsMastered);
    }
    public DungeonProgress withShieldsUsed(int v) {
        return new DungeonProgress(answeredCount, correctCount, streak, longestStreak, elitesCleared, v, luckyCoinsConsumed, revenantsMastered);
    }
    public DungeonProgress withLuckyCoinsConsumed(int v) {
        return new DungeonProgress(answeredCount, correctCount, streak, longestStreak, elitesCleared, shieldsUsed, v, revenantsMastered);
    }
    public DungeonProgress withRevenantsMastered(int v) {
        return new DungeonProgress(answeredCount, correctCount, streak, longestStreak,
            elitesCleared, shieldsUsed, luckyCoinsConsumed, v);
    }

    public DungeonProgress recordAnswer(boolean correct) {
        int nextAnswered = answeredCount + 1;
        int nextCorrect  = correctCount + (correct ? 1 : 0);
        int nextStreak   = correct ? streak + 1 : 0;
        int nextLongest  = Math.max(longestStreak, nextStreak);
        return new DungeonProgress(nextAnswered, nextCorrect, nextStreak, nextLongest,
                                    elitesCleared, shieldsUsed, luckyCoinsConsumed, revenantsMastered);
    }

    public DungeonProgress recordShieldUsed() {
        return withShieldsUsed(shieldsUsed + 1);
    }

    public DungeonProgress consumeLuckyCoin() {
        return withLuckyCoinsConsumed(luckyCoinsConsumed + 1);
    }

    public DungeonProgress recordRevenantMastered() {
        return withRevenantsMastered(revenantsMastered + 1);
    }
}
