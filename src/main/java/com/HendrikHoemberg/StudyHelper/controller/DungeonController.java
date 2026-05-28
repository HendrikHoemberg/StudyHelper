package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.exception.AiQuizGenerationException;
import com.HendrikHoemberg.StudyHelper.exception.DeckNotFoundException;
import com.HendrikHoemberg.StudyHelper.exception.MapGenerationException;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
public class DungeonController {

    private static final String DUNGEON_SESSION_KEY = "dungeonSessionState";

    private final DungeonSessionService dungeonSessionService;
    private final SavedSessionService savedSessionService;
    private final StudyLogService studyLogService;
    private final UserService userService;
    private final DungeonViewModelBuilder viewModelBuilder;
    private final DungeonShrineService shrineService;

    public DungeonController(DungeonSessionService dungeonSessionService,
                             SavedSessionService savedSessionService,
                             StudyLogService studyLogService,
                             UserService userService,
                             DungeonViewModelBuilder viewModelBuilder,
                             DungeonShrineService shrineService) {
        this.dungeonSessionService = dungeonSessionService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
        this.userService = userService;
        this.viewModelBuilder = viewModelBuilder;
        this.shrineService = shrineService;
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
            DungeonSessionState state = createDungeon(dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, request, user, response);
            savedSessionService.discard(user);
            session.setAttribute(DUNGEON_SESSION_KEY, state);
            savedSessionService.saveDungeon(user, state);
            return renderGame(model, state, hxRequest);
        } catch (DeckNotFoundException ex) {
            return handleStartError(model, user, dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, ex, response, hxRequest);
        } catch (AiQuizGenerationException ex) {
            return handleStartError(model, user, dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, ex, response, hxRequest);
        } catch (MapGenerationException ex) {
            return handleStartError(model, user, dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, ex, response, hxRequest);
        } catch (IllegalArgumentException ex) {
            return handleStartError(model, user, dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, ex, response, hxRequest);
        } catch (AiGenerationException | AiQuotaExceededException ex) {
            response.addHeader("HX-Trigger", "refresh-quota");
            return handleStartError(model, user, dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, ex, response, hxRequest);
        }
    }

    @GetMapping("/dungeon/resume")
    public String resume(Model model,
                         Principal principal,
                         HttpSession session,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        Optional<DungeonSessionState> loaded = savedSessionService.loadDungeon(user);
        if (loaded.isEmpty()) {
            savedSessionService.consumeIncompatibleDiscardFlag(user);
            return "redirect:/study/start?mode=DUNGEON";
        }

        DungeonSessionState state = loaded.get();
        if (state.config().mode() == DungeonMode.FLASHCARDS) {
            SavedSessionService.ReconcileDungeonResult result =
                savedSessionService.reconcileDungeonFlashcards(state, user);
            if (!result.canContinue()) {
                DungeonRunStats stats = dungeonSessionService.buildStats(result.state());
                studyLogService.recordDungeonAbandoned(user, stats, state.config().selectedDeckIds());
                savedSessionService.discard(user, false);
                return "redirect:/study/start?mode=DUNGEON";
            }
            state = result.state();
        }

        session.setAttribute(DUNGEON_SESSION_KEY, state);
        savedSessionService.saveDungeon(user, state);
        return renderGame(model, state, hxRequest);
    }

    @PostMapping("/dungeon/move")
    public String move(@RequestParam DungeonDirection direction,
                       Model model,
                       Principal principal,
                       HttpSession session,
                       HttpServletResponse response,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();
        ActionResult result = dungeonSessionService.move(state, direction);
        if (result instanceof ActionResult.Failure failure) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader("HX-Trigger", "dungeon-action-error");
            model.addAttribute("actionError", failure.message());
            return renderGame(model, failure.state(), hxRequest);
        }
        return stashAndRender(model, user, session, result.state(), hxRequest);
    }

    @PostMapping("/dungeon/answer/flashcard")
    public String answerFlashcard(@RequestParam boolean gotIt,
                                  Model model,
                                  Principal principal,
                                  HttpSession session,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();
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
        if (state == null) return redirectToStart();
        state = dungeonSessionService.answerQuiz(state, selectedOptions);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/relic/pick")
    public String pickRelic(@RequestParam RelicId relicId,
                            Model model, Principal principal, HttpSession session,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();
        state = dungeonSessionService.pickRelic(state, relicId);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/relic/buy")
    public String buyRelic(@RequestParam RelicId relicId,
                           Model model, Principal principal, HttpSession session,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();
        state = dungeonSessionService.buyRelic(state, relicId);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/relic/skip-shop")
    public String skipShop(Model model, Principal principal, HttpSession session,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();
        state = dungeonSessionService.skipShop(state);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/shrine/confirm")
    public String shrineConfirm(Model model, Principal principal, HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();
        model.addAttribute("state", state);
        return "fragments/dungeon-shrine-modal :: shrineConfirm";
    }

    @PostMapping("/dungeon/shrine/reset")
    public String shrineReset(Model model, Principal principal, HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();
        model.addAttribute("state", state);
        return "fragments/dungeon-shrine-modal :: shrineMain";
    }

    @PostMapping("/dungeon/shrine/roll")
    public String shrineRoll(Model model, Principal principal, HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();

        DungeonShrineService.ShrineRollOutcome outcome = shrineService.computeOutcome(state);
        DungeonSessionState nextState = shrineService.applyRoll(state, outcome);
        session.setAttribute(DUNGEON_SESSION_KEY, nextState);
        savedSessionService.saveDungeon(user, nextState);

        viewModelBuilder.prepareShrineResult(model, nextState, outcome.roll(), outcome.grantedRelic());
        return "fragments/dungeon-shrine-modal :: shrineResult";
    }

    @PostMapping("/dungeon/shrine/drink")
    public String shrineDrink(Model model, Principal principal, HttpSession session,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();
        state = shrineService.drink(state);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/shrine/leave")
    public String shrineLeave(Model model, Principal principal, HttpSession session,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return redirectToStart();
        state = shrineService.leave(state);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/collect/coin")
    @ResponseBody
    public String collectCoin(@RequestParam(required = false) String itemId,
                              HttpSession session, Principal principal,
                              HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        CollectResult result = dungeonSessionService.collectCoin(state, itemId);
        return handleCollectResult(result, user, session, response);
    }

    @PostMapping("/dungeon/collect/shield")
    @ResponseBody
    public String collectShield(@RequestParam(required = false) String itemId,
                                HttpSession session, Principal principal,
                                HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        CollectResult result = dungeonSessionService.collectShield(state, itemId);
        return handleCollectResult(result, user, session, response);
    }

    private String handleCollectResult(CollectResult result, User user,
                                        HttpSession session, HttpServletResponse response) {
        switch (result.status()) {
            case OK -> {
                session.setAttribute(DUNGEON_SESSION_KEY, result.state());
                savedSessionService.saveDungeon(user, result.state());
                return "success";
            }
            case DUPLICATE -> {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                return "";
            }
            default -> {
                return "";
            }
        }
    }

    private DungeonSessionState createDungeon(DungeonMode dungeonMode, DungeonSize dungeonSize,
                                                List<Long> selectedDeckIds, QuizQuestionMode quizQuestionMode,
                                                Difficulty difficulty, String additionalInstructions,
                                                HttpServletRequest request, User user,
                                                HttpServletResponse response) {
        if (dungeonMode == DungeonMode.FLASHCARDS) {
            return dungeonSessionService.createFlashcardDungeon(selectedDeckIds, dungeonSize, user);
        } else {
            DungeonSessionState state = dungeonSessionService.createAiQuizDungeon(
                selectedDeckIds, dungeonSize, quizQuestionMode, difficulty,
                additionalInstructions, request, user);
            response.addHeader("HX-Trigger", "refresh-quota");
            return state;
        }
    }

    private String stashAndRender(Model model, User user, HttpSession session,
                                   DungeonSessionState state, String hxRequest) {
        session.setAttribute(DUNGEON_SESSION_KEY, state);
        if (state.isComplete()) {
            savedSessionService.discard(user, false);
            DungeonRunStats stats = dungeonSessionService.buildStats(state);
            studyLogService.recordDungeon(user, stats, state.config().selectedDeckIds());
            viewModelBuilder.prepareComplete(model, state);
            if (hxRequest != null) {
                return "fragments/dungeon-complete :: dungeonComplete";
            }
            model.addAttribute("studyStateView", "dungeonComplete");
            return "study-page";
        }
        savedSessionService.saveDungeon(user, state);
        return renderGame(model, state, hxRequest);
    }

    private String renderGame(Model model, DungeonSessionState state, String hxRequest) {
        model.addAttribute("mode", StudyMode.DUNGEON);
        model.addAttribute("studyStateView", "dungeon");
        viewModelBuilder.prepareGame(model, state);
        if (hxRequest != null) return "fragments/dungeon-game :: dungeonGame";
        model.addAttribute("studyStateView", "dungeon");
        return "study-page";
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
                                     Exception ex, HttpServletResponse response, String hxRequest) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("mode", StudyMode.DUNGEON);
        model.addAttribute("dungeonMode", dungeonMode);
        model.addAttribute("dungeonSize", dungeonSize);
        model.addAttribute("selectedDeckIds", selectedDeckIds == null ? List.of() : selectedDeckIds);
        model.addAttribute("quizQuestionMode", quizQuestionMode);
        model.addAttribute("difficulty", difficulty);
        model.addAttribute("additionalInstructions", additionalInstructions == null ? "" : additionalInstructions);

        viewModelBuilder.prepareWizard(model, user, selectedDeckIds, ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());

        if (hxRequest != null) {
            return "fragments/study-setup :: studySetup";
        }
        model.addAttribute("studyStateView", "setup");
        return "study-page";
    }

    private String redirectToStart() {
        return "redirect:/study/start?mode=DUNGEON";
    }
}
