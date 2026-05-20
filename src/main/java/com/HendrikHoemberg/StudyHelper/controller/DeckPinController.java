package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DeckPinService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.util.NoSuchElementException;

@Controller
public class DeckPinController {

    private final UserService userService;
    private final DeckPinService deckPinService;

    public DeckPinController(UserService userService, DeckPinService deckPinService) {
        this.userService = userService;
        this.deckPinService = deckPinService;
    }

    @PostMapping("/decks/{id}/pin")
    public ResponseEntity<Void> togglePin(@PathVariable Long id, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        try {
            deckPinService.togglePin(user, id);
            return ResponseEntity.ok().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
