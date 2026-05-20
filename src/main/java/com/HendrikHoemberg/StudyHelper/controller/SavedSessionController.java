package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.security.Principal;

@Controller
public class SavedSessionController {

    private final SavedSessionService savedSessionService;
    private final UserService userService;

    public SavedSessionController(SavedSessionService savedSessionService, UserService userService) {
        this.savedSessionService = savedSessionService;
        this.userService = userService;
    }

    @GetMapping("/sessions/saved/resume")
    public String resume(Principal principal) {
        User user = userService.getByUsername(principal.getName());
        return savedSessionService.findForUser(user)
            .map(s -> "redirect:" + s.resumeUrl())
            .orElse("redirect:/dashboard");
    }

    @PostMapping("/sessions/saved/discard")
    public String discard(Principal principal,
                          HttpServletResponse response,
                          @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        savedSessionService.discard(user);
        if (hxRequest != null) {
            response.setStatus(HttpServletResponse.SC_OK);
            return "fragments/saved-session :: empty";
        }
        return "redirect:/dashboard";
    }
}
