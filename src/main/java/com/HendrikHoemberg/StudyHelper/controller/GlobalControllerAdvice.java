package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.config.AppDefaults;
import com.HendrikHoemberg.StudyHelper.dto.SidebarFolderNode;
import com.HendrikHoemberg.StudyHelper.dto.UserQuotaSummary;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.entity.UserRole;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final FolderService folderService;
    private final DeckService deckService;
    private final UserService userService;
    private final AiRequestQuotaService aiRequestQuotaService;
    private final StorageQuotaService storageQuotaService;
    private final HttpServletRequest request;

    public GlobalControllerAdvice(FolderService folderService,
                                  DeckService deckService,
                                  UserService userService,
                                  AiRequestQuotaService aiRequestQuotaService,
                                  StorageQuotaService storageQuotaService,
                                  HttpServletRequest request) {
        this.folderService = folderService;
        this.deckService = deckService;
        this.userService = userService;
        this.aiRequestQuotaService = aiRequestQuotaService;
        this.storageQuotaService = storageQuotaService;
        this.request = request;
    }

    @ModelAttribute("sidebarTree")
    public List<SidebarFolderNode> addSidebarTree(Principal principal) {
        if (principal == null) {
            return null;
        }
        User user = userService.getByUsername(principal.getName());
        
        Long activeFolderId = null;
        String uri = request.getRequestURI();
        
        // Folders: /folders/{id}
        Pattern folderPattern = Pattern.compile("/folders/(\\d+)");
        Matcher folderMatcher = folderPattern.matcher(uri);
        if (folderMatcher.find()) {
            activeFolderId = Long.parseLong(folderMatcher.group(1));
        } else {
            // Decks: /decks/{id}
            Pattern deckPattern = Pattern.compile("/decks/(\\d+)");
            Matcher deckMatcher = deckPattern.matcher(uri);
            if (deckMatcher.find()) {
                Long deckId = Long.parseLong(deckMatcher.group(1));
                activeFolderId = deckService.findFolderIdByDeckId(deckId, user);
            }
        }

        return folderService.getSidebarTree(user, activeFolderId);
    }

    @ModelAttribute("isAdmin")
    public boolean addIsAdmin(Principal principal) {
        if (principal == null) {
            return false;
        }
        User user = userService.getByUsername(principal.getName());
        return user.getRole() == UserRole.ADMIN;
    }

    @ModelAttribute("defaultColorHex")
    public String addDefaultColorHex() {
        return AppDefaults.DEFAULT_COLOR_HEX;
    }

    @ModelAttribute("userQuota")
    public UserQuotaSummary addUserQuota(Principal principal) {
        if (principal == null) {
            return null;
        }
        User user = userService.getByUsername(principal.getName());
        return new UserQuotaSummary(
            storageQuotaService.usedBytes(user),
            user.getStorageQuotaBytes(),
            aiRequestQuotaService.todayUsed(user),
            user.getDailyAiRequestLimit()
        );
    }
}
