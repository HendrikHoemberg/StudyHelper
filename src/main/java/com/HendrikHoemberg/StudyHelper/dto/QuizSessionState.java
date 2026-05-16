package com.HendrikHoemberg.StudyHelper.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public record QuizSessionState(
    QuizConfig config,
    List<QuizQuestion> questions,
    int currentIndex,
    Map<Integer, List<Integer>> answers
) {
    public boolean isAnswered(int index) {
        return answers.containsKey(index);
    }

    public boolean isComplete() {
        return currentIndex >= questions.size();
    }

    public boolean isCorrect(int index) {
        List<Integer> answer = answers.get(index);
        if (answer == null) {
            return false;
        }
        return new HashSet<>(answer).equals(new HashSet<>(questions.get(index).correctOptionIndices()));
    }

    public int correctCount() {
        int count = 0;
        for (int i = 0; i < questions.size(); i++) {
            if (isCorrect(i)) {
                count++;
            }
        }
        return count;
    }
}
