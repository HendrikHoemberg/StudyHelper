package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckGroup;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckOption;
import com.HendrikHoemberg.StudyHelper.dto.TestConfig;
import com.HendrikHoemberg.StudyHelper.dto.TestQuestion;
import com.HendrikHoemberg.StudyHelper.dto.TestQuestionMode;
import com.HendrikHoemberg.StudyHelper.dto.TestSessionState;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiTestService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
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

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Controller
public class TestController {

    private static final String SESSION_KEY = "testSessionState";
    private static final String VIEW_SETUP = "setup";
    private static final String VIEW_QUESTION = "question";
    private static final String VIEW_COMPLETE = "complete";

    private final AiTestService aiTestService;
    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final FolderService folderService;
    private final UserService userService;

    public TestController(AiTestService aiTestService,
                          DeckService deckService,
                          FlashcardService flashcardService,
                          FolderService folderService,
                          UserService userService) {
        this.aiTestService = aiTestService;
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.folderService = folderService;
        this.userService = userService;
    }

    @GetMapping("/test/start")
    public String start(Model model, Principal principal,
                        @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        prepareSetupModel(model, user, List.of(), null);
        if (hxRequest != null) return "fragments/test-setup :: testSetup";
        model.addAttribute("testStateView", VIEW_SETUP);
        return "test-page";
    }

    @PostMapping("/test/setup/update")
    public String updateSetup(
            @RequestParam(required = false) List<Long> selectedDeckIds,
            @RequestParam(required = false) Long toggledFolderId,
            @RequestParam(required = false) Long removeId,
            @RequestParam(required = false, defaultValue = "false") boolean clearAll,
            Model model, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        List<Long> selected = new ArrayList<>(normalizeDeckIds(selectedDeckIds));

        if (clearAll) {
            selected.clear();
        } else if (removeId != null) {
            selected.remove(removeId);
        } else if (toggledFolderId != null) {
            List<StudyDeckOption> allInFolder = folderService.getAllDecksInFolder(toggledFolderId, user);
            List<Long> selectableIds = allInFolder.stream()
                .filter(d -> d.cardCount() > 0)
                .map(StudyDeckOption::deckId)
                .toList();
            boolean allSelected = !selectableIds.isEmpty() && new HashSet<>(selected).containsAll(selectableIds);
            if (allSelected) {
                selected.removeAll(selectableIds);
            } else {
                for (Long id : selectableIds) {
                    if (!selected.contains(id)) selected.add(id);
                }
            }
        }

        prepareSetupModel(model, user, selected, null);
        return "fragments/test-setup :: testSetupPicker";
    }

    @PostMapping("/test/session")
    public String createSession(
            @RequestParam(required = false) List<Long> selectedDeckIds,
            @RequestParam(defaultValue = "5") int questionCount,
            Model model, Principal principal,
            HttpSession httpSession, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());

        List<Long> normalizedIds = normalizeDeckIds(selectedDeckIds);
        if (normalizedIds.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, normalizedIds, "Please select at least one deck.");
            if (hxRequest != null) return "fragments/test-setup :: testSetup";
            model.addAttribute("testStateView", VIEW_SETUP);
            return "test-page";
        }

        try {
            List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(normalizedIds, user);
            List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
            int count = Math.max(1, Math.min(questionCount, 20));

            List<TestQuestion> questions = aiTestService.generate(flashcards, List.of(), count, TestQuestionMode.MCQ_ONLY, Difficulty.MEDIUM);

            TestSessionState state = new TestSessionState(
                new TestConfig(normalizedIds, List.of(), count, TestQuestionMode.MCQ_ONLY, Difficulty.MEDIUM),
                questions, 0, new HashMap<>()
            );
            httpSession.setAttribute(SESSION_KEY, state);
            return renderQuestion(model, state, hxRequest);

        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, normalizedIds, ex.getMessage());
            if (hxRequest != null) return "fragments/test-setup :: testSetup";
            model.addAttribute("testStateView", VIEW_SETUP);
            return "test-page";
        }
    }

    @PostMapping("/test/answer")
    public String answer(
            @RequestParam int selectedOption,
            Model model, Principal principal,
            HttpSession httpSession, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());

        TestSessionState state = getState(httpSession);
        if (state == null) {
            prepareSetupModel(model, user, List.of(), "Session expired. Please start a new test.");
            if (hxRequest != null) return "fragments/test-setup :: testSetup";
            model.addAttribute("testStateView", VIEW_SETUP);
            return "test-page";
        }

        int idx = state.currentIndex();
        if (state.isAnswered(idx)) {
            return renderQuestion(model, state, hxRequest);
        }

        Map<Integer, Integer> newAnswers = new HashMap<>(state.answers());
        newAnswers.put(idx, selectedOption);
        TestSessionState newState = new TestSessionState(
            state.config(), state.questions(), idx, newAnswers
        );
        httpSession.setAttribute(SESSION_KEY, newState);
        return renderQuestion(model, newState, hxRequest);
    }

    @GetMapping("/test/next")
    public String nextQuestion(
            Model model, Principal principal,
            HttpSession httpSession, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());

        TestSessionState state = getState(httpSession);
        if (state == null) {
            prepareSetupModel(model, user, List.of(), "Session expired. Please start a new test.");
            if (hxRequest != null) return "fragments/test-setup :: testSetup";
            model.addAttribute("testStateView", VIEW_SETUP);
            return "test-page";
        }

        TestSessionState newState = new TestSessionState(
            state.config(), state.questions(), state.currentIndex() + 1, state.answers()
        );
        httpSession.setAttribute(SESSION_KEY, newState);

        if (newState.isComplete()) {
            return renderSummary(model, newState, hxRequest);
        }
        return renderQuestion(model, newState, hxRequest);
    }

    private String renderQuestion(Model model, TestSessionState state, String hxRequest) {
        int idx = state.currentIndex();
        TestQuestion q = state.questions().get(idx);
        boolean answered = state.isAnswered(idx);
        model.addAttribute("currentQuestion", q);
        model.addAttribute("questionNumber", idx + 1);
        model.addAttribute("totalQuestions", state.questions().size());
        model.addAttribute("answered", answered);
        model.addAttribute("selectedOption", answered ? state.answers().get(idx) : null);
        if (hxRequest != null) return "fragments/test-question :: testQuestion";
        model.addAttribute("testStateView", VIEW_QUESTION);
        return "test-page";
    }

    private String renderSummary(Model model, TestSessionState state, String hxRequest) {
        int correct = state.correctCount();
        int total = state.questions().size();
        int pct = total > 0 ? (int) Math.round(100.0 * correct / total) : 0;
        model.addAttribute("questions", state.questions());
        model.addAttribute("answers", state.answers());
        model.addAttribute("correctCount", correct);
        model.addAttribute("totalQuestions", total);
        model.addAttribute("scorePercent", pct);
        if (hxRequest != null) return "fragments/test-summary :: testSummary";
        model.addAttribute("testStateView", VIEW_COMPLETE);
        return "test-page";
    }

    private void prepareSetupModel(Model model, User user, List<Long> preselectedIds, String error) {
        List<Long> normalized = normalizeDeckIds(preselectedIds);
        List<StudyDeckGroup> deckGroups = folderService.getStudyFolderTree(user, normalized);
        model.addAttribute("deckGroups", deckGroups);
        model.addAttribute("preselectedDeckIds", normalized);
        model.addAttribute("testError", error);
    }

    private TestSessionState getState(HttpSession session) {
        Object raw = session.getAttribute(SESSION_KEY);
        return raw instanceof TestSessionState s ? s : null;
    }

    private List<Long> normalizeDeckIds(List<Long> deckIds) {
        if (deckIds == null || deckIds.isEmpty()) return List.of();
        List<Long> normalized = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : deckIds) {
            if (id != null && seen.add(id)) normalized.add(id);
        }
        return List.copyOf(normalized);
    }
}
