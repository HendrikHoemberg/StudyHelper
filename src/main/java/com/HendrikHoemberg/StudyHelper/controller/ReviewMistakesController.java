package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode;
import com.HendrikHoemberg.StudyHelper.dto.SessionMode;
import com.HendrikHoemberg.StudyHelper.dto.StudyCardView;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionConfig;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.FlashcardReviewService;
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ReviewMistakesController {

    private final UserService userService;
    private final FlashcardReviewService reviewService;
    private final SavedSessionService savedSessionService;

    public ReviewMistakesController(UserService userService,
                                    FlashcardReviewService reviewService,
                                    SavedSessionService savedSessionService) {
        this.userService = userService;
        this.reviewService = reviewService;
        this.savedSessionService = savedSessionService;
    }

    @PostMapping("/study/review-mistakes")
    public String start(Principal principal, HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        List<Flashcard> cards = reviewService.loadMistakeCards(user);
        if (cards.isEmpty()) return "redirect:/dashboard";

        Map<Long, List<StudyCardView>> byDeck = new LinkedHashMap<>();
        List<StudyCardView> queue = new java.util.ArrayList<>();
        for (Flashcard fc : cards) {
            StudyCardView v = new StudyCardView(
                fc.getId(),
                fc.getFrontText(),
                fc.getBackText(),
                fc.getDeck().getId(),
                fc.getDeck().getName(),
                fc.getDeck().getName(),
                fc.getDeck().getColorHex(),
                fc.getDeck().getIconName(),
                null,
                null
            );
            byDeck.computeIfAbsent(v.deckId(), k -> new java.util.ArrayList<>()).add(v);
            queue.add(v);
        }

        StudySessionConfig config = new StudySessionConfig(
            List.copyOf(byDeck.keySet()),
            SessionMode.SHUFFLED,
            DeckOrderMode.SELECTED_ORDER
        );
        StudySessionState state = new StudySessionState(
            config,
            byDeck,
            List.copyOf(queue),
            0, 0, 0, 0, List.of()
        );

        savedSessionService.discard(user);
        session.setAttribute("studySessionState", state);
        savedSessionService.saveFlashcards(user, state);
        return "redirect:/session/next";
    }
}
