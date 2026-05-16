package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizSessionState;
import com.HendrikHoemberg.StudyHelper.dto.StudyMode;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles quiz session runtime endpoints (answering, navigation).
 * Setup and session creation flow through the unified StudyController.
 */
@Controller
public class QuizController {

    private static final String SESSION_KEY = "quizSessionState";
    private static final String VIEW_QUESTION = "question";
    private static final String VIEW_COMPLETE = "complete";

    @PostMapping("/quiz/answer")
    public String answer(
            @RequestParam(required = false) java.util.List<Integer> selectedOptions,
            Model model,
            HttpSession httpSession,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        QuizSessionState state = getState(httpSession);
        if (state == null) {
            return "redirect:/study/start?mode=QUIZ";
        }

        int idx = state.currentIndex();
        if (state.isAnswered(idx)) {
            return renderQuestion(model, state, hxRequest);
        }
        if (selectedOptions == null || selectedOptions.isEmpty()) {
            return renderQuestion(model, state, hxRequest);
        }

        java.util.List<Integer> cleaned = selectedOptions.stream()
            .filter(java.util.Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        Map<Integer, java.util.List<Integer>> newAnswers = new HashMap<>(state.answers());
        newAnswers.put(idx, cleaned);
        QuizSessionState newState = new QuizSessionState(
            state.config(), state.questions(), idx, newAnswers
        );
        httpSession.setAttribute(SESSION_KEY, newState);
        return renderQuestion(model, newState, hxRequest);
    }

    @GetMapping("/quiz/next")
    public String nextQuestion(
            Model model,
            HttpSession httpSession,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        QuizSessionState state = getState(httpSession);
        if (state == null) {
            return "redirect:/study/start?mode=QUIZ";
        }

        QuizSessionState newState = new QuizSessionState(
            state.config(), state.questions(), state.currentIndex() + 1, state.answers()
        );
        httpSession.setAttribute(SESSION_KEY, newState);

        if (newState.isComplete()) {
            return renderSummary(model, newState, hxRequest);
        }
        return renderQuestion(model, newState, hxRequest);
    }

    private String renderQuestion(Model model, QuizSessionState state, String hxRequest) {
        int idx = state.currentIndex();
        QuizQuestion q = state.questions().get(idx);
        boolean answered = state.isAnswered(idx);
        model.addAttribute("mode", StudyMode.QUIZ);
        model.addAttribute("currentQuestion", q);
        model.addAttribute("questionNumber", idx + 1);
        model.addAttribute("totalQuestions", state.questions().size());
        model.addAttribute("answered", answered);
        model.addAttribute("selectedOptions", answered ? state.answers().get(idx) : java.util.List.of());
        model.addAttribute("answerCorrect", answered && state.isCorrect(idx));
        if (hxRequest != null) return "fragments/quiz-question :: quizQuestion";
        model.addAttribute("studyStateView", VIEW_QUESTION);
        return "study-page";
    }

    private String renderSummary(Model model, QuizSessionState state, String hxRequest) {
        int correct = state.correctCount();
        int total = state.questions().size();
        int pct = total > 0 ? (int) Math.round(100.0 * correct / total) : 0;
        model.addAttribute("mode", StudyMode.QUIZ);
        model.addAttribute("questions", state.questions());
        model.addAttribute("answers", state.answers());
        model.addAttribute("correctCount", correct);
        model.addAttribute("totalQuestions", total);
        model.addAttribute("scorePercent", pct);
        if (hxRequest != null) return "fragments/quiz-summary :: quizSummary";
        model.addAttribute("studyStateView", VIEW_COMPLETE);
        return "study-page";
    }

    private QuizSessionState getState(HttpSession session) {
        Object raw = session.getAttribute(SESSION_KEY);
        return raw instanceof QuizSessionState s ? s : null;
    }
}
