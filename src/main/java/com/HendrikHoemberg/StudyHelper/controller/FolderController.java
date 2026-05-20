package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.config.AppDefaults;
import com.HendrikHoemberg.StudyHelper.dto.SidebarFolderNode;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.ActiveTab;
import com.HendrikHoemberg.StudyHelper.service.DashboardService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.FolderView;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@Controller
public class FolderController {

    private final FolderService folderService;
    private final DashboardService dashboardService;
    private final UserService userService;

    public FolderController(FolderService folderService,
                            DashboardService dashboardService,
                            UserService userService) {
        this.folderService = folderService;
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    @PostMapping("/folders")
    public String createRootFolder(@RequestParam String name,
                                   @RequestParam(defaultValue = AppDefaults.DEFAULT_COLOR_HEX) String colorHex,
                                   @RequestParam(defaultValue = "folder") String iconName,
                                   Principal principal,
                                   @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        folderService.createFolder(name, colorHex, iconName, null, user);
        return "redirect:/dashboard";
    }

    @GetMapping("/folders/new")
    public String newFolderModal(@RequestParam(required = false) Long parentId, Model model) {
        model.addAttribute("parentId", parentId);
        model.addAttribute("folder", new Folder()); // Empty folder for defaults
        model.addAttribute("action", parentId != null ? "/folders/" + parentId + "/subfolders" : "/folders");
        model.addAttribute("title", parentId != null ? "New Subfolder" : "New Folder");
        return "fragments/folder-form :: folderModal";
    }

    @GetMapping("/folders/{id}/edit")
    public String editFolderModal(@PathVariable Long id, Model model, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        Folder folder = folderService.getFolder(id, user);
        model.addAttribute("folder", folder);
        model.addAttribute("action", "/folders/" + id + "/edit");
        model.addAttribute("title", "Edit Folder");
        return "fragments/folder-form :: folderModal";
    }

    @GetMapping("/folders/{id}")
    public String viewFolder(@PathVariable Long id, 
                             @RequestParam(required = false) String sortBy,
                             @RequestParam(required = false, defaultValue = "asc") String direction,
                             @RequestParam(required = false) String tab,
                             Model model, Principal principal,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                             @RequestHeader(value = "HX-Target", required = false) String hxTarget) {
        User user = userService.getByUsername(principal.getName());
        ActiveTab activeTab = parseTab(tab);
        FolderView view = folderService.getFolderView(id, user, sortBy, direction, activeTab);
        model.addAttribute("view", view);
        model.addAttribute("username", principal.getName());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);

        if (hxRequest != null) {
            if ("folder-tabs-section".equals(hxTarget)) {
                return "fragments/folder-detail :: tabsSection";
            }
            model.addAttribute("refreshSidebar", true);
            return "fragments/folder-detail :: folderDetail";
        }
        return "folder-page";
    }

    private ActiveTab parseTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return ActiveTab.FOLDERS;
        }
        try {
            return ActiveTab.valueOf(tab.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ActiveTab.FOLDERS;
        }
    }

    @GetMapping("/folders/{id}/children")
    public String folderChildren(@PathVariable Long id, Model model, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        FolderView view = folderService.getFolderView(id, user);
        model.addAttribute("view", view);
        return "fragments/folder-children :: folderChildren";
    }

    @PostMapping("/folders/{id}/subfolders")
    public String createSubfolder(@PathVariable Long id,
                                   @RequestParam String name,
                                   @RequestParam(defaultValue = AppDefaults.DEFAULT_COLOR_HEX) String colorHex,
                                   @RequestParam(defaultValue = "folder") String iconName,
                                   Principal principal) {
        User user = userService.getByUsername(principal.getName());
        folderService.createFolder(name, colorHex, iconName, id, user);

        return "redirect:/folders/" + id + "?tab=folders";
    }

    @PostMapping("/folders/{id}/edit")
    public String editFolder(@PathVariable Long id,
                             @RequestParam String name,
                             @RequestParam(defaultValue = AppDefaults.DEFAULT_COLOR_HEX) String colorHex,
                             @RequestParam(defaultValue = "folder") String iconName,
                             Principal principal) {
        User user = userService.getByUsername(principal.getName());
        folderService.updateFolder(id, name, colorHex, iconName, user);
        return "redirect:/folders/" + id + "?tab=folders";
    }

    @PostMapping("/folders/{id}/color")
    public String updateColor(@PathVariable Long id,
                              @RequestParam String colorHex,
                              Principal principal) {
        User user = userService.getByUsername(principal.getName());
        folderService.updateFolderColor(id, colorHex, user);

        return "redirect:/folders/" + id + "?tab=folders";
    }

    @PostMapping("/folders/{id}/delete")
    public String deleteFolder(@PathVariable Long id, Principal principal,
                               Model model,
                               RedirectAttributes redirectAttributes,
                               @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                               jakarta.servlet.http.HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        String error = null;
        try {
            folderService.deleteFolder(id, user);
        } catch (IOException e) {
            error = "Could not delete all files: " + e.getMessage();
            redirectAttributes.addFlashAttribute("error", error);
        }

        if (hxRequest != null) {
            response.setHeader("HX-Push-Url", "/dashboard");
            model.addAttribute("vm", dashboardService.buildFor(user));
            model.addAttribute("username", principal.getName());
            model.addAttribute("refreshSidebar", true);
            if (error != null) {
                model.addAttribute("error", error);
            }
            return "fragments/explorer :: dashboardContent";
        }
        return "redirect:/dashboard";
    }
}
