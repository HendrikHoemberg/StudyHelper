package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckGroup;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckOption;
import com.HendrikHoemberg.StudyHelper.dto.QuizConfig;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode;
import com.HendrikHoemberg.StudyHelper.dto.QuizSessionState;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiQuizService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
import com.HendrikHoemberg.StudyHelper.service.DocumentExtractionService;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Handles quiz session runtime endpoints (session creation, answering, navigation).
 * Setup entry points (GET /quiz/start, POST /quiz/setup/update) have been retired;
 * all setup flows now go through the unified StudyController.
 */
@Controller
public class QuizController {

    private static final String SESSION_KEY = "quizSessionState";
    private static final String VIEW_SETUP = "setup";
    private static final String VIEW_QUESTION = "question";
    private static final String VIEW_COMPLETE = "complete";

    private final AiQuizService aiQuizService;
    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final FolderService folderService;
    private final UserService userService;
    private final DocumentExtractionService documentExtractionService;
    private final FileEntryService fileEntryService;

    public QuizController(AiQuizService aiQuizService,
                          DeckService deckService,
                          FlashcardService flashcardService,
                          FolderService folderService,
                          UserService userService,
                          DocumentExtractionService documentExtractionService,
                          FileEntryService fileEntryService) {
        this.aiQuizService = aiQuizService;
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.folderService = folderService;
        this.userService = userService;
        this.documentExtractionService = documentExtractionService;
        this.fileEntryService = fileEntryService;
    }

    // --- Legacy GET /quiz/start and POST /quiz/setup/update removed ---
    // All setup flows now go through StudyController (GET /study/start, POST /study/setup/update)

    @PostMapping("/quiz/session")
    public String createSession(
            @RequestParam(required = false) List<Long> selectedDeckIds,
            @RequestParam(required = false) List<Long> selectedFileIds,
            @RequestParam(defaultValue = "MCQ_ONLY") QuizQuestionMode questionMode,
            @RequestParam(defaultValue = "MEDIUM") Difficulty difficulty,
            @RequestParam(defaultValue = "5") int questionCount,
            Model model, Principal principal,
            HttpSession httpSession, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());

        List<Long> normalizedDeckIds = normalizeIds(selectedDeckIds);
        List<Long> normalizedFileIds = normalizeIds(selectedFileIds);

        if (normalizedDeckIds.isEmpty() && normalizedFileIds.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, normalizedDeckIds, normalizedFileIds, "Please select at least one deck or document.", httpSession);
            if (hxRequest != null) return "fragments/quiz-setup :: quizSetup";
            model.addAttribute("quizStateView", VIEW_SETUP);
            return "quiz-page";
        }

        try {
            List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(normalizedDeckIds, user);
            List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
            
            List<AiQuizService.DocumentInput> documents = new ArrayList<>();
            long totalChars = 0;
            for (Long fileId : normalizedFileIds) {
                com.HendrikHoemberg.StudyHelper.entity.FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                String text = documentExtractionService.extractText(file);
                documents.add(new AiQuizService.DocumentInput(file.getOriginalFilename(), text));
                totalChars += text.length();
            }

            if (totalChars > 150_000) {
                throw new IllegalArgumentException("Selection too large — please deselect some sources.");
            }

            int count = Math.max(1, Math.min(questionCount, 20));
            List<QuizQuestion> questions = aiQuizService.generate(flashcards, documents, count, questionMode, difficulty);

            QuizSessionState state = new QuizSessionState(
                new QuizConfig(normalizedDeckIds, normalizedFileIds, count, questionMode, difficulty),
                questions, 0, new HashMap<>()
            );
            httpSession.setAttribute(SESSION_KEY, state);
            return renderQuestion(model, state, hxRequest);

        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException | IOException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, normalizedDeckIds, normalizedFileIds, ex.getMessage(), httpSession);
            if (hxRequest != null) return "fragments/quiz-setup :: quizSetup";
            model.addAttribute("quizStateView", VIEW_SETUP);
            return "quiz-page";
        }
    }

    @PostMapping("/quiz/answer")
    public String answer(
            @RequestParam int selectedOption,
            Model model, Principal principal,
            HttpSession httpSession, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());

        QuizSessionState state = getState(httpSession);
        if (state == null) {
            prepareSetupModel(model, user, List.of(), List.of(), "Session expired. Please start a new quiz.", httpSession);
            if (hxRequest != null) return "fragments/quiz-setup :: quizSetup";
            model.addAttribute("quizStateView", VIEW_SETUP);
            return "quiz-page";
        }

        int idx = state.currentIndex();
        if (state.isAnswered(idx)) {
            return renderQuestion(model, state, hxRequest);
        }

        Map<Integer, Integer> newAnswers = new HashMap<>(state.answers());
        newAnswers.put(idx, selectedOption);
        QuizSessionState newState = new QuizSessionState(
            state.config(), state.questions(), idx, newAnswers
        );
        httpSession.setAttribute(SESSION_KEY, newState);
        return renderQuestion(model, newState, hxRequest);
    }

    @GetMapping("/quiz/next")
    public String nextQuestion(
            Model model, Principal principal,
            HttpSession httpSession, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());

        QuizSessionState state = getState(httpSession);
        if (state == null) {
            prepareSetupModel(model, user, List.of(), List.of(), "Session expired. Please start a new quiz.", httpSession);
            if (hxRequest != null) return "fragments/quiz-setup :: quizSetup";
            model.addAttribute("quizStateView", VIEW_SETUP);
            return "quiz-page";
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
        model.addAttribute("currentQuestion", q);
        model.addAttribute("questionNumber", idx + 1);
        model.addAttribute("totalQuestions", state.questions().size());
        model.addAttribute("answered", answered);
        model.addAttribute("selectedOption", answered ? state.answers().get(idx) : null);
        if (hxRequest != null) return "fragments/quiz-question :: quizQuestion";
        model.addAttribute("quizStateView", VIEW_QUESTION);
        return "quiz-page";
    }

    private String renderSummary(Model model, QuizSessionState state, String hxRequest) {
        int correct = state.correctCount();
        int total = state.questions().size();
        int pct = total > 0 ? (int) Math.round(100.0 * correct / total) : 0;
        model.addAttribute("questions", state.questions());
        model.addAttribute("answers", state.answers());
        model.addAttribute("correctCount", correct);
        model.addAttribute("totalQuestions", total);
        model.addAttribute("scorePercent", pct);
        if (hxRequest != null) return "fragments/quiz-summary :: quizSummary";
        model.addAttribute("quizStateView", VIEW_COMPLETE);
        return "quiz-page";
    }

    private void prepareSetupModel(Model model, User user, List<Long> preselectedDeckIds, List<Long> preselectedFileIds, String error, HttpSession session) {
        List<Long> normalizedDecks = normalizeIds(preselectedDeckIds);
        List<Long> normalizedFiles = normalizeIds(preselectedFileIds);
        
        List<com.HendrikHoemberg.StudyHelper.dto.QuizSourceGroup> sourceGroups = folderService.getQuizSourceTree(user, normalizedDecks, normalizedFiles);
        model.addAttribute("sourceGroups", sourceGroups);
        model.addAttribute("preselectedDeckIds", normalizedDecks);
        model.addAttribute("preselectedFileIds", normalizedFiles);
        model.addAttribute("quizError", error);

        // Character count calculation and caching
        Map<Long, Integer> charCache = (Map<Long, Integer>) session.getAttribute("quizFileCharCache");
        if (charCache == null) {
            charCache = new HashMap<>();
            session.setAttribute("quizFileCharCache", charCache);
        }

        long totalChars = 0;
        for (Long fileId : normalizedFiles) {
            if (charCache.containsKey(fileId)) {
                totalChars += charCache.get(fileId);
            } else {
                try {
                    com.HendrikHoemberg.StudyHelper.entity.FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                    String text = documentExtractionService.extractText(file);
                    int length = text.length();
                    charCache.put(fileId, length);
                    totalChars += length;
                } catch (Exception e) {
                    // Ignore extraction errors for model prep, they'll be caught on session creation
                }
            }
        }

        model.addAttribute("selectionTotalChars", totalChars);
        model.addAttribute("selectionWarn", totalChars >= 50_000);
        model.addAttribute("selectionExceedsCap", totalChars > 150_000);
    }

    private QuizSessionState getState(HttpSession session) {
        Object raw = session.getAttribute(SESSION_KEY);
        return raw instanceof QuizSessionState s ? s : null;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> normalized = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id != null && seen.add(id)) normalized.add(id);
        }
        return List.copyOf(normalized);
    }
}
