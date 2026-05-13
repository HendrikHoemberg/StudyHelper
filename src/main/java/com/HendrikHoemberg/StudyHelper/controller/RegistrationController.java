package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.service.InviteRegistrationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationController {

    private final InviteRegistrationService inviteRegistrationService;

    public RegistrationController(InviteRegistrationService inviteRegistrationService) {
        this.inviteRegistrationService = inviteRegistrationService;
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
        @RequestParam("code") String code,
        @RequestParam("username") String username,
        @RequestParam("password") String password,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        try {
            inviteRegistrationService.register(code, username, password);
            redirectAttributes.addFlashAttribute("successMessage", "Account created. You can now sign in.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("code", code);
            model.addAttribute("username", username);
            return "register";
        }
    }
}
