package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.SessionMode;
import com.HendrikHoemberg.StudyHelper.dto.StudyCardView;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckGroup;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckOption;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionConfig;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionStats;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.StudySessionService;
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
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Handles flashcard session runtime endpoints (next, answer, redo).
 * Setup routes (GET /study/start, POST /study/session, etc.) are in StudyController.
 */
@Controller
public class StudySessionController {

    private static final String SESSION_KEY = "studySessionState";
    private static final String VIEW_SETUP = "setup";
    private static final String VIEW_CARD = "card";
    private static final String VIEW_COMPLETE = "complete";

    private final StudySessionService studySessionService;
    private final FlashcardService flashcardService;
    private final UserService userService;
    private final FolderService folderService;

    public StudySessionController(StudySessionService studySessionService,
                                  FlashcardService flashcardService,
                                  UserService userService,
                                  FolderService folderService) {
        this.studySessionService = studySessionService;
        this.flashcardService = flashcardService;
        this.userService = userService;
        this.folderService = folderService;
    }

    // --- Setup routes removed: now handled by StudyController ---

    @GetMapping("/session/next")
    public String nextCard(Model model,
                           Principal principal,
                           HttpSession httpSession,
                           HttpServletResponse response,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        StudySessionState state = getState(httpSession);

        if (state == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, List.of(), "Session expired. Start a new study session.");
            if (hxRequest != null) {
                return "fragments/study-setup :: studySetup";
            }
            model.addAttribute("studyStateView", VIEW_SETUP);
            return "study-page";
        }

        try {
            return renderCurrentState(model, user, state, hxRequest);
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, state.config().selectedDeckIds(), ex.getMessage());
            if (hxRequest != null) {
                return "fragments/study-setup :: studySetup";
            }
            model.addAttribute("studyStateView", VIEW_SETUP);
            return "study-page";
        }
    }

    @PostMapping("/session/answer")
    public String answer(@RequestParam Long cardId,
                         @RequestParam boolean isCorrect,
                         Model model,
                         Principal principal,
                         HttpSession httpSession,
                         HttpServletResponse response,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        StudySessionState state = getState(httpSession);

        if (state == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, List.of(), "Session expired. Start a new study session.");
            if (hxRequest != null) {
                return "fragments/study-setup :: studySetup";
            }
            model.addAttribute("studyStateView", VIEW_SETUP);
            return "study-page";
        }

        try {
            flashcardService.getFlashcardForUser(cardId, user);
            StudySessionState nextState = studySessionService.recordAnswer(state, cardId, isCorrect);
            httpSession.setAttribute(SESSION_KEY, nextState);
            return renderCurrentState(model, user, nextState, hxRequest);
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            if (studySessionService.isComplete(state)) {
                prepareCompletionModel(model, state);
                if (hxRequest != null) {
                    return "fragments/study-complete :: studyComplete";
                }
                model.addAttribute("studyStateView", VIEW_COMPLETE);
                return "study-page";
            }

            prepareCardModel(model, state, ex.getMessage());
            if (hxRequest != null) {
                return "fragments/study-card :: studyCard";
            }
            model.addAttribute("studyStateView", VIEW_CARD);
            return "study-page";
        }
    }

    @PostMapping("/session/redo")
    public String redo(Model model,
                       Principal principal,
                       HttpSession httpSession,
                       HttpServletResponse response,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        StudySessionState state = getState(httpSession);

        if (state == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, List.of(), "Session expired. Start a new study session.");
            if (hxRequest != null) {
                return "fragments/study-setup :: studySetup";
            }
            model.addAttribute("studyStateView", VIEW_SETUP);
            return "study-page";
        }

        try {
            StudySessionState rebuilt = studySessionService.buildSession(state.config(), user);
            httpSession.setAttribute(SESSION_KEY, rebuilt);
            return renderCurrentState(model, user, rebuilt, hxRequest);
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, state.config().selectedDeckIds(), ex.getMessage());
            if (hxRequest != null) {
                return "fragments/study-setup :: studySetup";
            }
            model.addAttribute("studyStateView", VIEW_SETUP);
            return "study-page";
        }
    }

    @PostMapping("/session/redo-incorrect")
    public String redoIncorrect(Model model,
                                Principal principal,
                                HttpSession httpSession,
                                HttpServletResponse response,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";

        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        StudySessionState state = getState(httpSession);

        if (state == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareSetupModel(model, user, List.of(), "Session expired. Start a new study session.");
            if (hxRequest != null) return "fragments/study-setup :: studySetup";
            model.addAttribute("studyStateView", VIEW_SETUP);
            return "study-page";
        }

        try {
            StudySessionState rebuilt = studySessionService.redoIncorrect(state);
            httpSession.setAttribute(SESSION_KEY, rebuilt);
            return renderCurrentState(model, user, rebuilt, hxRequest);
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            prepareCompletionModel(model, state);
            model.addAttribute("studyError", ex.getMessage());
            if (hxRequest != null) return "fragments/study-complete :: studyComplete";
            model.addAttribute("studyStateView", VIEW_COMPLETE);
            return "study-page";
        }
    }

    private String renderCurrentState(Model model, User user, StudySessionState state, String hxRequest) {
        if (studySessionService.isComplete(state)) {
            prepareCompletionModel(model, state);
            if (hxRequest != null) {
                return "fragments/study-complete :: studyComplete";
            }
            model.addAttribute("studyStateView", VIEW_COMPLETE);
            return "study-page";
        }

        StudyCardView currentCard = studySessionService.nextCard(state);
        flashcardService.getFlashcardForUser(currentCard.cardId(), user);
        prepareCardModel(model, state, null);

        if (hxRequest != null) {
            return "fragments/study-card :: studyCard";
        }
        model.addAttribute("studyStateView", VIEW_CARD);
        return "study-page";
    }

    private void prepareSetupModel(Model model,
                                   User user,
                                   List<Long> preselectedDeckIds,
                                   String error) {
        List<Long> normalized = normalizeDeckIds(preselectedDeckIds);
        List<StudyDeckGroup> deckGroups = folderService.getStudyFolderTree(user, normalized);
        model.addAttribute("deckGroups", deckGroups);
        model.addAttribute("preselectedDeckIds", normalized);
        model.addAttribute("studyError", error);
        model.addAttribute("sessionModes", SessionMode.values());
    }

    private void prepareCardModel(Model model, StudySessionState state, String error) {
        StudyCardView currentCard = studySessionService.nextCard(state);
        model.addAttribute("state", state);
        model.addAttribute("currentCard", currentCard);
        model.addAttribute("currentCardNumber", state.currentIndex() + 1);
        model.addAttribute("totalCards", state.queue().size());
        model.addAttribute("isDeckByDeck", state.config().sessionMode() == SessionMode.DECK_BY_DECK);
        model.addAttribute("studyError", error);
    }

    private void prepareCompletionModel(Model model, StudySessionState state) {
        StudySessionStats stats = studySessionService.buildStats(state);
        model.addAttribute("state", state);
        model.addAttribute("stats", stats);
        model.addAttribute("studyError", null);
        model.addAttribute("incorrectCardCount", state.incorrectCardIds().size());
    }

    private StudySessionState getState(HttpSession httpSession) {
        Object raw = httpSession.getAttribute(SESSION_KEY);
        if (raw instanceof StudySessionState state) {
            return state;
        }
        return null;
    }

    private List<Long> normalizeDeckIds(List<Long> deckIds) {
        if (deckIds == null || deckIds.isEmpty()) {
            return List.of();
        }

        List<Long> normalized = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long deckId : deckIds) {
            if (deckId != null && seen.add(deckId)) {
                normalized.add(deckId);
            }
        }

        return List.copyOf(normalized);
    }
}
