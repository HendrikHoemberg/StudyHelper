package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuizSessionStateTests {

    private QuizQuestion mcq(int correct) {
        return new QuizQuestion(QuestionType.MULTIPLE_CHOICE, "Q",
            List.of("a", "b", "c", "d"), List.of(correct));
    }

    private QuizQuestion multi(Integer... correct) {
        return new QuizQuestion(QuestionType.MULTIPLE_SELECT, "Q",
            List.of("a", "b", "c", "d"), List.of(correct));
    }

    private QuizSessionState state(List<QuizQuestion> questions, Map<Integer, List<Integer>> answers) {
        return new QuizSessionState(
            new QuizConfig(List.of(), List.of(), questions.size(), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM),
            questions, questions.size(), answers);
    }

    @Test
    void isCorrect_SingleAnswer_MatchesExactly() {
        QuizSessionState s = state(List.of(mcq(2)), Map.of(0, List.of(2)));
        assertThat(s.isCorrect(0)).isTrue();
    }

    @Test
    void isCorrect_SingleAnswer_WrongPickIsFalse() {
        QuizSessionState s = state(List.of(mcq(2)), Map.of(0, List.of(1)));
        assertThat(s.isCorrect(0)).isFalse();
    }

    @Test
    void isCorrect_MultiSelect_ExactSetMatchIsCorrect_OrderIndependent() {
        QuizSessionState s = state(List.of(multi(0, 2)), Map.of(0, List.of(2, 0)));
        assertThat(s.isCorrect(0)).isTrue();
    }

    @Test
    void isCorrect_MultiSelect_PartialSelectionIsWrong() {
        QuizSessionState s = state(List.of(multi(0, 2)), Map.of(0, List.of(0)));
        assertThat(s.isCorrect(0)).isFalse();
    }

    @Test
    void isCorrect_MultiSelect_ExtraSelectionIsWrong() {
        QuizSessionState s = state(List.of(multi(0, 2)), Map.of(0, List.of(0, 1, 2)));
        assertThat(s.isCorrect(0)).isFalse();
    }

    @Test
    void isCorrect_Unanswered_IsFalse() {
        QuizSessionState s = state(List.of(mcq(0)), Map.of());
        assertThat(s.isCorrect(0)).isFalse();
    }

    @Test
    void correctCount_CountsOnlyExactMatches() {
        QuizSessionState s = state(
            List.of(mcq(1), multi(0, 2), mcq(3)),
            Map.of(0, List.of(1), 1, List.of(0), 2, List.of(3)));
        assertThat(s.correctCount()).isEqualTo(2);
    }
}
