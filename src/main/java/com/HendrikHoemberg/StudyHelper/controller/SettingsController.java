package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class SettingsController {

    private final UserService userService;
    private final MessageSource messageSource;

    public SettingsController(UserService userService, MessageSource messageSource) {
        this.userService = userService;
        this.messageSource = messageSource;
    }

    @GetMapping("/settings")
    public String getSettings(Principal principal, Model model) {
        addUserToModel(model, userService.getByUsername(principal.getName()));
        return "settings";
    }

    @PostMapping("/settings/password")
    public String changePassword(
        @RequestParam("currentPassword") String currentPassword,
        @RequestParam("newPassword") String newPassword,
        @RequestParam("confirmPassword") String confirmPassword,
        Principal principal,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        User user = userService.getByUsername(principal.getName());
        try {
            userService.changePassword(user, currentPassword, newPassword, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage",
                translate("settings.password.updated"));
            return "redirect:/settings";
        } catch (IllegalArgumentException ex) {
            addUserToModel(model, user);
            model.addAttribute("passwordError", translate(ex.getMessage()));
            return "settings";
        }
    }

    @PostMapping("/settings/language")
    public String changeLanguage(
        @RequestParam("language") String language,
        Principal principal,
        RedirectAttributes redirectAttributes
    ) {
        User user = userService.getByUsername(principal.getName());
        userService.setLanguage(user, language);
        redirectAttributes.addFlashAttribute("successMessage",
            translate("settings.language.updated"));
        return "redirect:/settings";
    }

    private String translate(String key) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException ex) {
            return key;
        }
    }

    private void addUserToModel(Model model, User user) {
        model.addAttribute("username", user.getUsername());
        model.addAttribute("currentLanguage", user.getLanguage() == null ? "en" : user.getLanguage());
    }
}
