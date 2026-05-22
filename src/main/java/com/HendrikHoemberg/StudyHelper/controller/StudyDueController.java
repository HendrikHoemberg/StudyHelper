package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode;
import com.HendrikHoemberg.StudyHelper.dto.SessionMode;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckOption;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionConfig;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.StudySessionService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.util.List;

@Controller
public class StudyDueController {

    private static final String DASHBOARD_URL = "/dashboard";

    private final UserService userService;
    private final DeckService deckService;
    private final StudySessionService studySessionService;
    private final SavedSessionService savedSessionService;

    public StudyDueController(UserService userService,
                              DeckService deckService,
                              StudySessionService studySessionService,
                              SavedSessionService savedSessionService) {
        this.userService = userService;
        this.deckService = deckService;
        this.studySessionService = studySessionService;
        this.savedSessionService = savedSessionService;
    }

    @PostMapping("/study/start-due")
    public String start(Principal principal,
                        HttpSession session,
                        Model model,
                        HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());

        var existing = savedSessionService.findForUser(user);
        if (existing.isPresent()) {
            model.addAttribute("savedSession", existing.get());
            model.addAttribute("studyWizardCancelUrl", DASHBOARD_URL);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader("HX-Retarget", "#modal-placeholder");
            response.setHeader("HX-Reswap", "innerHTML");
            return "fragments/saved-session :: conflict";
        }

        List<Long> allDeckIds = deckService.getStudyDeckOptions(user).stream()
            .map(StudyDeckOption::deckId)
            .toList();

        StudySessionConfig config = new StudySessionConfig(
            allDeckIds,
            SessionMode.DECK_BY_DECK,
            DeckOrderMode.SELECTED_ORDER,
            false,
            20
        );

        try {
            StudySessionState state = studySessionService.buildSession(config, user);
            session.setAttribute("studySessionState", state);
            savedSessionService.saveFlashcards(user, state);
            return "redirect:/session/next";
        } catch (IllegalArgumentException ex) {
            return "redirect:/dashboard";
        }
    }
}
