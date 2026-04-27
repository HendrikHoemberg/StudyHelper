package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class FlashcardController {

    private final FlashcardService flashcardService;
    private final DeckService deckService;
    private final UserService userService;

    public FlashcardController(FlashcardService flashcardService, DeckService deckService, UserService userService) {
        this.flashcardService = flashcardService;
        this.deckService = deckService;
        this.userService = userService;
    }

    @PostMapping("/decks/{deckId}/flashcards")
    public String createFlashcard(@PathVariable Long deckId,
                                  @RequestParam String frontText,
                                  @RequestParam String backText,
                                  Model model, Principal principal,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        flashcardService.createFlashcard(frontText, backText, deckId, user);

        if (hxRequest != null) {
            Deck deck = deckService.getDeck(deckId, user);
            model.addAttribute("deck", deck);
            model.addAttribute("flashcards", deck.getFlashcards());
            return "fragments/deck :: flashcardList";
        }
        return "redirect:/decks/" + deckId;
    }

    @PostMapping("/flashcards/{id}/edit")
    public String editFlashcard(@PathVariable Long id,
                                @RequestParam String frontText,
                                @RequestParam String backText,
                                @RequestParam Long deckId,
                                Model model, Principal principal,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        flashcardService.updateFlashcard(id, frontText, backText, principal.getName());

        if (hxRequest != null) {
            Deck deck = deckService.getDeck(deckId, user);
            model.addAttribute("deck", deck);
            model.addAttribute("flashcards", deck.getFlashcards());
            return "fragments/deck :: flashcardList";
        }
        return "redirect:/decks/" + deckId;
    }

    @PostMapping("/flashcards/{id}/delete")
    public String deleteFlashcard(@PathVariable Long id,
                                  Model model, Principal principal,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        Long deckId = flashcardService.deleteFlashcard(id, principal.getName());

        if (hxRequest != null) {
            Deck deck = deckService.getDeck(deckId, user);
            model.addAttribute("deck", deck);
            model.addAttribute("flashcards", deck.getFlashcards());
            return "fragments/deck :: flashcardList";
        }
        return "redirect:/decks/" + deckId;
    }
}
