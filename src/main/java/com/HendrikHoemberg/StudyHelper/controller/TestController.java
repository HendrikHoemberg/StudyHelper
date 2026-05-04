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
    private final DocumentExtractionService documentExtractionService;
    private final FileEntryService fileEntryService;

    public TestController(AiTestService aiTestService,
                          DeckService deckService,
                          FlashcardService flashcardService,
                          FolderService folderService,
                          UserService userService,
                          DocumentExtractionService documentExtractionService,
                          FileEntryService fileEntryService) {
        this.aiTestService = aiTestService;
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.folderService = folderService;
        this.userService = userService;
        this.documentExtractionService = documentExtractionService;
        this.fileEntryService = fileEntryService;
    }

    @GetMapping("/test/start")
    public String start(Model model, Principal principal, HttpSession session,
                        @RequestParam(required = false) Long deckId,
                        @RequestParam(required = false) Long fileId,
                        @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());

        List<Long> deckIds = new ArrayList<>();
        List<Long> fileIds = new ArrayList<>();
        String error = null;

        if (deckId != null) {
            try {
                deckService.getDeck(deckId, user);
                deckIds.add(deckId);
            } catch (Exception e) {
                error = "Could not find selected deck.";
            }
        }
        if (fileId != null) {
            try {
                com.HendrikHoemberg.StudyHelper.entity.FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                if (documentExtractionService.isSupported(file)) {
                    fileIds.add(fileId);
                } else {
                    error = "Selected file is not supported for AI testing.";
                }
            } catch (Exception e) {
                error = "Could not find selected file.";
            }
        }

        prepareSetupModel(model, user, deckIds, fileIds, error, session);
        if (hxRequest != null) return "fragments/test-setup :: testSetup";
        model.addAttribute("testStateView", VIEW_SETUP);
        return "test-page";
    }

    @PostMapping("/test/setup/update")
    public String updateSetup(
            @RequestParam(required = false) List<Long> selectedDeckIds,
            @RequestParam(required = false) List<Long> selectedFileIds,
            @RequestParam(required = false) Long toggledFolderId,
            @RequestParam(required = false) Long removeId,
            @RequestParam(required = false) Long removeFileId,
            @RequestParam(required = false, defaultValue = "false") boolean clearAll,
            Model model, Principal principal, HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        List<Long> selectedDecks = new ArrayList<>(normalizeIds(selectedDeckIds));
        List<Long> selectedFiles = new ArrayList<>(normalizeIds(selectedFileIds));

        if (clearAll) {
            selectedDecks.clear();
            selectedFiles.clear();
        } else if (removeId != null) {
            selectedDecks.remove(removeId);
        } else if (removeFileId != null) {
            selectedFiles.remove(removeFileId);
        } else if (toggledFolderId != null) {
            FolderService.FolderSources sources = folderService.getAllSourcesInFolder(toggledFolderId, user);
            List<Long> deckIds = sources.deckIds();
            List<Long> fileIds = sources.fileIds();

            boolean allDecksSelected = !deckIds.isEmpty() && new HashSet<>(selectedDecks).containsAll(deckIds);
            boolean allFilesSelected = !fileIds.isEmpty() && new HashSet<>(selectedFiles).containsAll(fileIds);
            boolean allSelected = (deckIds.isEmpty() || allDecksSelected) && (fileIds.isEmpty() || allFilesSelected);

            if (allSelected) {
                selectedDecks.removeAll(deckIds);
                selectedFiles.removeAll(fileIds);
            } else {
                for (Long id : deckIds) {
                    if (!selectedDecks.contains(id)) selectedDecks.add(id);
                }
                for (Long id : fileIds) {
                    if (!selectedFiles.contains(id)) selectedFiles.add(id);
                }
            }
        }

        prepareSetupModel(model, user, selectedDecks, selectedFiles, null, session);
        return "fragments/test-setup :: testSetupPicker";
    }

    @PostMapping("/test/session")
    public String createSession(
            @RequestParam(required = false) List<Long> selectedDeckIds,
            @RequestParam(required = false) List<Long> selectedFileIds,
            @RequestParam(defaultValue = "MCQ_ONLY") TestQuestionMode questionMode,
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
            if (hxRequest != null) return "fragments/test-setup :: testSetup";
            model.addAttribute("testStateView", VIEW_SETUP);
            return "test-page";
        }

        try {
            List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(normalizedDeckIds, user);
            List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
            
            List<AiTestService.DocumentInput> documents = new ArrayList<>();
            long totalChars = 0;
            for (Long fileId : normalizedFileIds) {
                com.HendrikHoemberg.StudyHelper.entity.FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                String text = documentExtractionService.extractText(file);
                documents.add(new AiTestService.DocumentInput(file.getOriginalFilename(), text));
                totalChars += text.length();
            }

            if (totalChars > 150_000) {
                throw new IllegalArgumentException("Selection too large — please deselect some sources.");
            }

            int count = Math.max(1, Math.min(questionCount, 20));
            List<TestQuestion> questions = aiTestService.generate(flashcards, documents, count, questionMode, difficulty);

            TestSessionState state = new TestSessionState(
                new TestConfig(normalizedDeckIds, normalizedFileIds, count, questionMode, difficulty),
                questions, 0, new HashMap<>()
            );
            httpSession.setAttribute(SESSION_KEY, state);
            return renderQuestion(model, state, hxRequest);

        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException | IOException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, normalizedDeckIds, normalizedFileIds, ex.getMessage(), httpSession);
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
            prepareSetupModel(model, user, List.of(), List.of(), "Session expired. Please start a new test.", httpSession);
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
            prepareSetupModel(model, user, List.of(), List.of(), "Session expired. Please start a new test.", httpSession);
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

    private void prepareSetupModel(Model model, User user, List<Long> preselectedDeckIds, List<Long> preselectedFileIds, String error, HttpSession session) {
        List<Long> normalizedDecks = normalizeIds(preselectedDeckIds);
        List<Long> normalizedFiles = normalizeIds(preselectedFileIds);
        
        List<com.HendrikHoemberg.StudyHelper.dto.TestSourceGroup> sourceGroups = folderService.getTestSourceTree(user, normalizedDecks, normalizedFiles);
        model.addAttribute("sourceGroups", sourceGroups);
        model.addAttribute("preselectedDeckIds", normalizedDecks);
        model.addAttribute("preselectedFileIds", normalizedFiles);
        model.addAttribute("testError", error);

        // Character count calculation and caching
        Map<Long, Integer> charCache = (Map<Long, Integer>) session.getAttribute("testFileCharCache");
        if (charCache == null) {
            charCache = new HashMap<>();
            session.setAttribute("testFileCharCache", charCache);
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

    private TestSessionState getState(HttpSession session) {
        Object raw = session.getAttribute(SESSION_KEY);
        return raw instanceof TestSessionState s ? s : null;
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
