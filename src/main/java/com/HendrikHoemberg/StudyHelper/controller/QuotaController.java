package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.UserQuotaSummary;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

@Controller
@RequestMapping("/api")
public class QuotaController {

    private final UserService userService;
    private final AiRequestQuotaService aiRequestQuotaService;
    private final StorageQuotaService storageQuotaService;

    public QuotaController(UserService userService,
                           AiRequestQuotaService aiRequestQuotaService,
                           StorageQuotaService storageQuotaService) {
        this.userService = userService;
        this.aiRequestQuotaService = aiRequestQuotaService;
        this.storageQuotaService = storageQuotaService;
    }

    @GetMapping("/quota")
    public String quota(Principal principal, org.springframework.ui.Model model) {
        User user = userService.getByUsername(principal.getName());
        UserQuotaSummary summary = new UserQuotaSummary(
            storageQuotaService.usedBytes(user),
            user.getStorageQuotaBytes(),
            aiRequestQuotaService.todayUsed(user),
            user.getDailyAiRequestLimit()
        );
        model.addAttribute("userQuota", summary);
        return "fragments/quota";
    }
}