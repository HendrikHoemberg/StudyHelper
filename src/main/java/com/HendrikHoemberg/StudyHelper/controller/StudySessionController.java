package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.Grade;
import com.HendrikHoemberg.StudyHelper.dto.Grade;
import com.HendrikHoemberg.StudyHelper.dto.SessionMode;
import com.HendrikHoemberg.StudyHelper.dto.StudyCardView;
import com.HendrikHoemberg.StudyHelper.dto.StudyMode;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionStats;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.SrsScheduler;
import com.HendrikHoemberg.StudyHelper.service.StudyLogService;
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
import java.util.NoSuchElementException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles flashcard session runtime endpoints (next, answer, redo).
 * Setup routes (GET /study/start, POST /study/session) are in StudyController.
 */
@Controller
public class StudySessionController {

    private static final String SESSION_KEY = "studySessionState";
    private static final String VIEW_CARD = "card";
    private static final String VIEW_COMPLETE = "complete";
    private static final String SETUP_REDIRECT = "redirect:/study/start?mode=FLASHCARDS";

    private final StudySessionService studySessionService;
    private final FlashcardService flashcardService;
    private final UserService userService;
    private final SavedSessionService savedSessionService;
    private final StudyLogService studyLogService;
    private final SrsScheduler srsScheduler;

    public StudySessionController(StudySessionService studySessionService,
                                  FlashcardService flashcardService,
                                  UserService userService,
                                  SavedSessionService savedSessionService,
                                  StudyLogService studyLogService,
                                  SrsScheduler srsScheduler) {
        this.studySessionService = studySessionService;
        this.flashcardService = flashcardService;
        this.userService = userService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
        this.srsScheduler = srsScheduler;
    }

    @GetMapping("/study/resume")
    public String resume(Model model,
                         Principal principal,
                         HttpSession httpSession,
                         RedirectAttributes redirect) {
        User user = userService.getByUsername(principal.getName());
        return savedSessionService.loadFlashcards(user).map(saved -> {
            SavedSessionService.ReconcileResult result = savedSessionService.reconcileFlashcards(saved, user);
            if (result.state().queue().isEmpty()) {
                savedSessionService.discard(user);
                redirect.addFlashAttribute("studyError",
                    "The cards in your saved session are no longer available.");
                return SETUP_REDIRECT;
            }
            httpSession.setAttribute(SESSION_KEY, result.state());
            savedSessionService.saveFlashcards(user, result.state());
            if (result.removedCount() > 0) {
                httpSession.setAttribute("studyResumeNotice",
                    result.removedCount() + " card(s) were removed since you paused.");
            }
            return "redirect:/session/next";
        }).orElse(SETUP_REDIRECT);
    }

    @GetMapping("/session/next")
    public String nextCard(Model model,
                           Principal principal,
                           HttpSession httpSession,
                           HttpServletResponse response,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        Object notice = httpSession.getAttribute("studyResumeNotice");
        if (notice != null) {
            model.addAttribute("studyResumeNotice", notice);
            httpSession.removeAttribute("studyResumeNotice");
        }
        StudySessionState state = getState(httpSession, user);

        if (state == null) {
            return SETUP_REDIRECT;
        }

        try {
            return renderCurrentState(model, user, state, hxRequest);
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            return SETUP_REDIRECT;
        }
    }

    @PostMapping("/session/answer")
    public String answer(@RequestParam Long cardId,
                         @RequestParam Grade grade,
                         Model model,
                         Principal principal,
                         HttpSession httpSession,
                         HttpServletResponse response,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        StudySessionState state = getState(httpSession, user);

        if (state == null) {
            return SETUP_REDIRECT;
        }

        try {
            flashcardService.getFlashcardForUser(cardId, user);
            StudySessionState nextState = studySessionService.recordAnswer(state, cardId, grade);
            stashAndPersist(httpSession, user, nextState);
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
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        StudySessionState state = getState(httpSession, user);

        if (state == null) {
            return SETUP_REDIRECT;
        }

        try {
            StudySessionState rebuilt = studySessionService.buildSession(state.config(), user);
            stashAndPersist(httpSession, user, rebuilt);
            return renderCurrentState(model, user, rebuilt, hxRequest);
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            return SETUP_REDIRECT;
        }
    }

    @PostMapping("/session/redo-incorrect")
    public String redoIncorrect(Model model,
                                Principal principal,
                                HttpSession httpSession,
                                HttpServletResponse response,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());
        StudySessionState state = getState(httpSession, user);

        if (state == null) {
            return SETUP_REDIRECT;
        }

        try {
            StudySessionState rebuilt = studySessionService.redoIncorrect(state);
            stashAndPersist(httpSession, user, rebuilt);
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
        model.addAttribute("mode", StudyMode.FLASHCARDS);
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

    private void prepareCardModel(Model model, StudySessionState state, String error) {
        StudyCardView currentCard = studySessionService.nextCard(state);
        model.addAttribute("state", state);
        model.addAttribute("currentCard", currentCard);
        model.addAttribute("currentCardNumber", state.currentIndex() + 1);
        model.addAttribute("totalCards", state.queue().size());
        model.addAttribute("studyError", error);

        java.time.LocalDate today = java.time.LocalDate.now();
        flashcardService.getFlashcardOptional(currentCard.cardId()).ifPresentOrElse(fc -> {
            int interval = fc.getIntervalDays() == null ? 0 : fc.getIntervalDays();
            double ef = fc.getEaseFactor() == null ? SrsScheduler.INITIAL_EF : fc.getEaseFactor();
            int reps = fc.getRepetitions() == null ? 0 : fc.getRepetitions();
            model.addAttribute("previewAgain", "today");
            model.addAttribute("previewHard", srsScheduler.next(interval, ef, reps, Grade.HARD, today).intervalDays() + "d");
            model.addAttribute("previewGood", srsScheduler.next(interval, ef, reps, Grade.GOOD, today).intervalDays() + "d");
            model.addAttribute("previewEasy", srsScheduler.next(interval, ef, reps, Grade.EASY, today).intervalDays() + "d");
        }, () -> {
            model.addAttribute("previewAgain", "today");
            model.addAttribute("previewHard", "");
            model.addAttribute("previewGood", "");
            model.addAttribute("previewEasy", "");
        });
        model.addAttribute("isPractice", state.config().practice());
    }

    private void prepareCompletionModel(Model model, StudySessionState state) {
        StudySessionStats stats = studySessionService.buildStats(state);
        model.addAttribute("state", state);
        model.addAttribute("stats", stats);
        model.addAttribute("studyError", null);
        model.addAttribute("incorrectCardCount", state.incorrectCardIds().size());
    }

    private StudySessionState getState(HttpSession httpSession, User user) {
        Object raw = httpSession.getAttribute(SESSION_KEY);
        if (raw instanceof StudySessionState state) {
            return state;
        }
        return savedSessionService.loadFlashcards(user).map(loaded -> {
            httpSession.setAttribute(SESSION_KEY, loaded);
            return loaded;
        }).orElse(null);
    }

    private void stashAndPersist(HttpSession httpSession, User user, StudySessionState state) {
        Object prior = httpSession.getAttribute(SESSION_KEY);
        boolean wasComplete = prior instanceof StudySessionState s && studySessionService.isComplete(s);
        httpSession.setAttribute(SESSION_KEY, state);
        if (studySessionService.isComplete(state)) {
            savedSessionService.discard(user);
            if (!wasComplete) {
                try {
                    studyLogService.recordFlashcards(user, state);
                } catch (Exception ignored) {
                    // Never block the user's completion view because of bookkeeping.
                }
            }
        } else {
            savedSessionService.saveFlashcards(user, state);
        }
    }
}
