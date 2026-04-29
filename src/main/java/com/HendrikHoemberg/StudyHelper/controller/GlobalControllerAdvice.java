package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.SidebarFolderNode;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final FolderService folderService;
    private final UserService userService;
    private final HttpServletRequest request;

    public GlobalControllerAdvice(FolderService folderService, UserService userService, HttpServletRequest request) {
        this.folderService = folderService;
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
        Pattern pattern = Pattern.compile("/folders/(\\d+)");
        Matcher matcher = pattern.matcher(uri);
        if (matcher.find()) {
            activeFolderId = Long.parseLong(matcher.group(1));
        }

        return folderService.getSidebarTree(user, activeFolderId);
    }
}
