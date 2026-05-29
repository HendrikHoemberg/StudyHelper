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

    private final DungeonSessionService dungeonSessionService;
    private final SavedSessionService savedSessionService;
    private final StudyLogService studyLogService;
    private final UserService userService;
    private final DungeonViewModelBuilder viewModelBuilder;

    public DungeonController(DungeonSessionService dungeonSessionService,
                             SavedSessionService savedSessionService,
                             StudyLogService studyLogService,
                             UserService userService,
                             DungeonViewModelBuilder viewModelBuilder) {
        this.dungeonSessionService = dungeonSessionService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
        this.userService = userService;
        this.viewModelBuilder = viewModelBuilder;
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
            return DungeonControllerAccess.withSessionLock(session, () -> {
                savedSessionService.discard(user);
                session.setAttribute(DungeonControllerAccess.DUNGEON_SESSION_KEY, state);
                savedSessionService.saveDungeon(user, state);
                return renderGame(model, state, hxRequest);
            });
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
        return DungeonControllerAccess.withSessionLock(session, () -> {
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

            session.setAttribute(DungeonControllerAccess.DUNGEON_SESSION_KEY, state);
            savedSessionService.saveDungeon(user, state);
            return renderGame(model, state, hxRequest);
        });
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

    private String renderGame(Model model, DungeonSessionState state, String hxRequest) {
        return DungeonControllerAccess.renderGame(model, state, hxRequest, viewModelBuilder);
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
}
