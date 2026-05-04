package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;
import java.util.Map;

public record TestSessionState(
    TestConfig config,
    List<TestQuestion> questions,
    int currentIndex,
    Map<Integer, Integer> answers
) {
    public boolean isAnswered(int index) {
        return answers.containsKey(index);
    }

    public boolean isComplete() {
        return currentIndex >= questions.size();
    }

    public int correctCount() {
        int count = 0;
        for (int i = 0; i < questions.size(); i++) {
            Integer answer = answers.get(i);
            if (answer != null && answer == questions.get(i).correctOptionIndex()) {
                count++;
            }
        }
        return count;
    }
}
