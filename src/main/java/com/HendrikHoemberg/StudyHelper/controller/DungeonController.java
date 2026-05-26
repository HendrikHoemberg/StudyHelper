package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.*;

@Controller
public class DungeonController {

    private static final Logger log = LoggerFactory.getLogger(DungeonController.class);
    private static final String DUNGEON_SESSION_KEY = "dungeonSessionState";

    private final DungeonSessionService dungeonSessionService;
    private final SavedSessionService savedSessionService;
    private final StudyLogService studyLogService;
    private final UserService userService;
    private final FolderService folderService;
    private final DashboardService dashboardService;

    public DungeonController(DungeonSessionService dungeonSessionService,
                             SavedSessionService savedSessionService,
                             StudyLogService studyLogService,
                             UserService userService,
                             FolderService folderService,
                             DashboardService dashboardService) {
        this.dungeonSessionService = dungeonSessionService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
        this.userService = userService;
        this.folderService = folderService;
        this.dashboardService = dashboardService;
    }

    @PostMapping("/dungeon/start")
    public String start(@RequestParam DungeonMode dungeonMode,
                        @RequestParam DungeonSize dungeonSize,
                        @RequestParam(required = false) List<Long> selectedDeckIds,
                        @RequestParam(defaultValue = "MCQ_ONLY") QuizQuestionMode quizQuestionMode,
                        @RequestParam(defaultValue = "MEDIUM") Difficulty difficulty,
                        @RequestParam(required = false) String additionalInstructions,
                        @RequestParam(name = "confirmDiscard", defaultValue = "false") boolean confirmDiscard,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Model model,
                        Principal principal,
                        HttpSession session,
                        @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());

        if (!confirmDiscard) {
            var existing = savedSessionService.findForUser(user);
            if (existing.isPresent()) {
                model.addAttribute("savedSession", existing.get());
                model.addAttribute("startNewMode", StudyMode.DUNGEON);
                model.addAttribute("studyWizardCancelUrl", "/dashboard");
                response.setStatus(HttpServletResponse.SC_OK);
                response.setHeader("HX-Retarget", "#modal-placeholder");
                response.setHeader("HX-Reswap", "innerHTML");
                return "fragments/saved-session :: conflict";
            }
        }

        try {
            DungeonSessionState state;
            if (dungeonMode == DungeonMode.FLASHCARDS) {
                state = dungeonSessionService.createFlashcardDungeon(selectedDeckIds, dungeonSize, user);
            } else {
                state = dungeonSessionService.createAiQuizDungeon(
                    selectedDeckIds, dungeonSize, quizQuestionMode, difficulty,
                    additionalInstructions, request, user);
            }
            savedSessionService.discard(user);
            session.setAttribute(DUNGEON_SESSION_KEY, state);
            savedSessionService.saveDungeon(user, state);
            return prepareGame(model, state, hxRequest);
        } catch (IllegalArgumentException ex) {
            return handleStartError(model, user, dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, ex, session, response, hxRequest);
        } catch (Exception ex) {
            return handleStartError(model, user, dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, ex, session, response, hxRequest);
        }
    }

    @GetMapping("/dungeon/resume")
    public String resume(Model model,
                         Principal principal,
                         HttpSession session,
                         RedirectAttributes redirectAttributes,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        Optional<DungeonSessionState> loaded = savedSessionService.loadDungeon(user);
        if (loaded.isEmpty()) {
            return "redirect:/study/start?mode=DUNGEON";
        }

        DungeonSessionState state = loaded.get();
        if (state.config().mode() == DungeonMode.FLASHCARDS) {
            SavedSessionService.ReconcileDungeonResult result =
                savedSessionService.reconcileDungeonFlashcards(state, user);
            if (!result.canContinue()) {
                DungeonRunStats stats = dungeonSessionService.buildStats(result.state());
                studyLogService.recordDungeonAbandoned(user, stats, state.config().selectedDeckIds());
                savedSessionService.discard(user);
                redirectAttributes.addFlashAttribute("errorMessage",
                    dungeonResumeDiscardMessage(result.removedCount()));
                return "redirect:/study/start?mode=DUNGEON";
            }
            state = result.state();
        }

        session.setAttribute(DUNGEON_SESSION_KEY, state);
        savedSessionService.saveDungeon(user, state);
        return prepareGame(model, state, hxRequest);
    }

    @PostMapping("/dungeon/move")
    public String move(@RequestParam DungeonDirection direction,
                       Model model,
                       Principal principal,
                       HttpSession session,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) {
            return "redirect:/study/start?mode=DUNGEON";
        }
        state = dungeonSessionService.move(state, direction);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/answer/flashcard")
    public String answerFlashcard(@RequestParam boolean gotIt,
                                  Model model,
                                  Principal principal,
                                  HttpSession session,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) {
            return "redirect:/study/start?mode=DUNGEON";
        }
        state = dungeonSessionService.answerFlashcard(state, gotIt);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/answer/quiz")
    public String answerQuiz(@RequestParam(required = false) List<Integer> selectedOptions,
                             Model model,
                             Principal principal,
                             HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) {
            return "redirect:/study/start?mode=DUNGEON";
        }
        state = dungeonSessionService.answerQuiz(state, selectedOptions);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    private String stashAndRender(Model model, User user, HttpSession session,
                                   DungeonSessionState state, String hxRequest) {
        session.setAttribute(DUNGEON_SESSION_KEY, state);
        if (state.isComplete()) {
            savedSessionService.discard(user);
            DungeonRunStats stats = dungeonSessionService.buildStats(state);
            studyLogService.recordDungeon(user, stats, state.config().selectedDeckIds());
            model.addAttribute("mode", StudyMode.DUNGEON);
            model.addAttribute("stats", stats);
            if (hxRequest != null) {
                return "fragments/dungeon-complete :: dungeonComplete";
            }
            model.addAttribute("studyStateView", "dungeonComplete");
            return "study-page";
        }
        savedSessionService.saveDungeon(user, state);
        return prepareGame(model, state, hxRequest);
    }

    private DungeonSessionState getState(HttpSession session, User user) {
        DungeonSessionState state = (DungeonSessionState) session.getAttribute(DUNGEON_SESSION_KEY);
        if (state == null) {
            state = savedSessionService.loadDungeon(user).orElse(null);
        }
        return state;
    }

    private String handleStartError(Model model, User user, DungeonMode dungeonMode, DungeonSize dungeonSize,
                                     List<Long> selectedDeckIds, QuizQuestionMode quizQuestionMode,
                                     Difficulty difficulty, String additionalInstructions,
                                     Exception ex, HttpSession session, HttpServletResponse response, String hxRequest) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        if (ex instanceof AiGenerationException || ex.getCause() instanceof AiGenerationException) {
            response.addHeader("HX-Trigger", "refresh-quota");
        }
        model.addAttribute("mode", StudyMode.DUNGEON);
        model.addAttribute("dungeonMode", dungeonMode);
        model.addAttribute("dungeonSize", dungeonSize);
        model.addAttribute("selectedDeckIds", selectedDeckIds == null ? List.of() : selectedDeckIds);
        model.addAttribute("quizQuestionMode", quizQuestionMode);
        model.addAttribute("difficulty", difficulty);
        model.addAttribute("additionalInstructions", additionalInstructions == null ? "" : additionalInstructions);

        prepareWizardModel(model, user, selectedDeckIds, ex.getMessage(), session);
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("aiErrorDetails", generationDetails(ex));

        if (hxRequest != null) {
            return "fragments/study-setup :: studySetup";
        }
        model.addAttribute("studyStateView", "setup");
        return "study-page";
    }

    private void prepareWizardModel(Model model, User user, List<Long> deckIds, String error, HttpSession session) {
        List<Long> normalizedDecks = StudySourceSupport.normalizeIds(deckIds);

        model.addAttribute("deckGroups", folderService.getStudyFolderTree(user, normalizedDecks));
        model.addAttribute("preselectedDeckIds", normalizedDecks);
        model.addAttribute("preselectedFileIds", List.of());
        model.addAttribute("pdfMode", Map.of());
        model.addAttribute("studyError", error);
        model.addAttribute("sessionModes", SessionMode.values());
        model.addAttribute("deckOrderModes", DeckOrderMode.values());
        model.addAttribute("quizQuestionModes", QuizQuestionMode.values());
        model.addAttribute("difficulties", Difficulty.values());

        long dueTodaySessionCount = dashboardService.buildFor(user).dueTodaySessionCount();
        model.addAttribute("dueTodaySessionCount", dueTodaySessionCount);

        model.addAttribute("selectionTotalChars", 0L);
        model.addAttribute("selectionWarn", false);
        model.addAttribute("selectionExceedsCap", false);
    }

    private String generationDetails(Exception ex) {
        if (ex instanceof AiGenerationException aiEx && aiEx.diagnostics() != null) {
            return aiEx.diagnostics().toDisplayString();
        }
        return AiGenerationDiagnostics.fromException("DUNGEON", "REQUEST_VALIDATION", ex).toDisplayString();
    }

    private String prepareGame(Model model, DungeonSessionState state, String hxRequest) {
        model.addAttribute("mode", StudyMode.DUNGEON);
        model.addAttribute("studyStateView", "dungeon");
        model.addAttribute("state", state);
        model.addAttribute("activeEncounter", state.activeEncounter());
        model.addAttribute("stats", dungeonSessionService.buildStats(state));
        model.addAttribute("mapTiles", mapTiles(state));
        if (hxRequest != null) {
            return "fragments/dungeon-game :: dungeonGame";
        }
        model.addAttribute("studyStateView", "dungeon");
        return "study-page";
    }

    private List<Map<String, Object>> mapTiles(DungeonSessionState state) {
        return state.map().tiles().values().stream()
            .map(tile -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("x", tile.position().x());
                m.put("y", tile.position().y());
                m.put("type", tile.type());
                m.put("revealed", tile.revealed());
                m.put("explored", tile.explored());
                m.put("encounterId", tile.encounterId());
                return m;
            })
            .toList();
    }

    private String dungeonResumeDiscardMessage(int removedCount) {
        return "Your saved Dungeon run could not continue because "
            + removedCount + " flashcard(s) are no longer available.";
    }
}
