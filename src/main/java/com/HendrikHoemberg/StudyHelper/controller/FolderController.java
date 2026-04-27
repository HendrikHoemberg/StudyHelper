package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.FolderView;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;

@Controller
public class FolderController {

    private final FolderService folderService;
    private final UserService userService;

    public FolderController(FolderService folderService, UserService userService) {
        this.folderService = folderService;
        this.userService = userService;
    }

    @GetMapping("/folders")
    public String listFolders(Model model, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("folders", folderService.getRootFolders(user));
        model.addAttribute("username", principal.getName());
        return "folders";
    }

    @PostMapping("/folders")
    public String createRootFolder(@RequestParam String name,
                                   @RequestParam(defaultValue = "#6c757d") String colorHex,
                                   Principal principal) {
        User user = userService.getByUsername(principal.getName());
        folderService.createFolder(name, colorHex, null, user);
        return "redirect:/folders";
    }

    @GetMapping("/folders/{id}")
    public String viewFolder(@PathVariable Long id, Model model, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        FolderView view = folderService.getFolderView(id, user);
        model.addAttribute("view", view);
        model.addAttribute("username", principal.getName());
        return "folder";
    }

    @PostMapping("/folders/{id}/subfolders")
    public String createSubfolder(@PathVariable Long id,
                                  @RequestParam String name,
                                  @RequestParam(defaultValue = "#6c757d") String colorHex,
                                  Principal principal) {
        User user = userService.getByUsername(principal.getName());
        folderService.createFolder(name, colorHex, id, user);
        return "redirect:/folders/" + id;
    }

    @PostMapping("/folders/{id}/delete")
    public String deleteFolder(@PathVariable Long id, Principal principal,
                               RedirectAttributes redirectAttributes) {
        User user = userService.getByUsername(principal.getName());
        try {
            folderService.deleteFolder(id, user);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Could not delete all files: " + e.getMessage());
        }
        return "redirect:/folders";
    }
}
