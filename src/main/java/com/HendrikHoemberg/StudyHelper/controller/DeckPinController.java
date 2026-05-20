package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DashboardService;
import com.HendrikHoemberg.StudyHelper.service.DeckPinService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.security.Principal;
import java.util.NoSuchElementException;

@Controller
public class DeckPinController {

    private final UserService userService;
    private final DeckPinService deckPinService;
    private final DashboardService dashboardService;

    public DeckPinController(UserService userService,
                             DeckPinService deckPinService,
                             DashboardService dashboardService) {
        this.userService = userService;
        this.deckPinService = deckPinService;
        this.dashboardService = dashboardService;
    }

    @PostMapping("/decks/{id}/pin")
    public Object togglePin(@PathVariable Long id,
                            Principal principal,
                            Model model,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        try {
            deckPinService.togglePin(user, id);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (hxRequest != null) {
            model.addAttribute("vm", dashboardService.buildFor(user));
            model.addAttribute("username", user.getUsername());
            return "fragments/explorer :: dashboardContent";
        }
        return ResponseEntity.ok().build();
    }
}
