package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AdminService;
import com.HendrikHoemberg.StudyHelper.service.RegistrationCodeService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class AdminController {

    private final AdminService adminService;
    private final RegistrationCodeService registrationCodeService;
    private final UserService userService;

    public AdminController(AdminService adminService,
                           RegistrationCodeService registrationCodeService,
                           UserService userService) {
        this.adminService = adminService;
        this.registrationCodeService = registrationCodeService;
        this.userService = userService;
    }

    @GetMapping("/admin")
    public String adminDashboard(Model model, Principal principal,
                                  @RequestParam(value = "tab", defaultValue = "users") String tab) {
        User admin = userService.getByUsername(principal.getName());
        model.addAttribute("username", admin.getUsername());
        model.addAttribute("users", adminService.listUsers());
        model.addAttribute("registrationCodes", registrationCodeService.listSummaries());
        model.addAttribute("activeTab", tab);
        return "admin";
    }

    @PostMapping("/admin/codes")
    public String generateCode(Principal principal, RedirectAttributes redirectAttributes) {
        try {
            User admin = userService.getByUsername(principal.getName());
            String code = registrationCodeService.generateCode(admin);
            redirectAttributes.addFlashAttribute("generatedCode", code);
            redirectAttributes.addFlashAttribute("success", "Invite code generated successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin?tab=codes";
    }

    @PostMapping("/admin/codes/{id}/revoke")
    public String revokeCode(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            registrationCodeService.revoke(id);
            redirectAttributes.addFlashAttribute("success", "Invite code revoked.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin?tab=codes";
    }

    @PostMapping("/admin/users/{id}/quotas")
    public String updateQuotas(@PathVariable Long id,
                               @RequestParam("storageQuotaGib") long storageQuotaGib,
                               @RequestParam("dailyAiLimit") int dailyAiLimit,
                               RedirectAttributes redirectAttributes) {
        try {
            long storageQuotaBytes = storageQuotaGib * 1024L * 1024L * 1024L;
            adminService.updateQuotas(id, storageQuotaBytes, dailyAiLimit);
            redirectAttributes.addFlashAttribute("success", "Quotas updated.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/users/{id}/disable")
    public String disableUser(@PathVariable Long id,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        try {
            User actingAdmin = userService.getByUsername(principal.getName());
            adminService.disableUser(id, actingAdmin);
            redirectAttributes.addFlashAttribute("success", "User disabled.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/users/{id}/enable")
    public String enableUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            adminService.enableUser(id);
            redirectAttributes.addFlashAttribute("success", "User enabled.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/users/{id}/delete")
    public String deleteDisabledUser(@PathVariable Long id,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            User actingAdmin = userService.getByUsername(principal.getName());
            adminService.hardDeleteDisabledUser(id, actingAdmin);
            redirectAttributes.addFlashAttribute("success", "Disabled user deleted.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin";
    }
}
