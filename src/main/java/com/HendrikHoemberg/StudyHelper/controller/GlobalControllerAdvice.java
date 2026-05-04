package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.SidebarFolderNode;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
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
    private final HttpServletRequest request;

    public GlobalControllerAdvice(FolderService folderService, DeckService deckService, UserService userService, HttpServletRequest request) {
        this.folderService = folderService;
        this.deckService = deckService;
        this.userService = userService;
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
}
