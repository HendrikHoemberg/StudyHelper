package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.SidebarFolderNode;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final FolderService folderService;
    private final UserService userService;

    public GlobalControllerAdvice(FolderService folderService, UserService userService) {
        this.folderService = folderService;
        this.userService = userService;
    }

    @ModelAttribute("sidebarTree")
    public List<SidebarFolderNode> addSidebarTree(Principal principal) {
        if (principal == null) {
            return null;
        }
        User user = userService.getByUsername(principal.getName());
        return folderService.getSidebarTree(user);
    }
}
