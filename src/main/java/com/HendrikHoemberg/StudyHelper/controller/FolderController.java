package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.FileSummary;
import com.HendrikHoemberg.StudyHelper.dto.SidebarFolderNode;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckOption;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
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
    private final DeckService deckService;
    private final FileEntryService fileEntryService;
    private final UserService userService;

    public FolderController(FolderService folderService,
                            DeckService deckService,
                            FileEntryService fileEntryService,
                            UserService userService) {
        this.folderService = folderService;
        this.deckService = deckService;
        this.fileEntryService = fileEntryService;
        this.userService = userService;
    }

    @GetMapping("/folders")
    public String listFolders(Model model, Principal principal,
                              @RequestParam(required = false) String q,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                              @RequestHeader(value = "HX-Target", required = false) String hxTarget) {
        User user = userService.getByUsername(principal.getName());
        List<Folder> folders = folderService.getRootFolders(user);

        List<StudyDeckOption> deckOptions = deckService.getStudyDeckOptions(user);
        List<FileSummary> fileSummaries = fileEntryService.getFileSummaries(user);

        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            deckOptions = deckOptions.stream()
                .filter(d -> d.deckName().toLowerCase().contains(query) || d.folderPath().toLowerCase().contains(query))
                .toList();
            fileSummaries = fileSummaries.stream()
                .filter(f -> f.originalFilename().toLowerCase().contains(query) || f.folderPath().toLowerCase().contains(query))
                .toList();
        }

        model.addAttribute("folders", folders);

        model.addAttribute("deckOptions", deckOptions);
        model.addAttribute("fileSummaries", fileSummaries);
        model.addAttribute("username", principal.getName());
        model.addAttribute("query", q);

        if (hxRequest != null) {
            if ("library-grid-container".equals(hxTarget)) {
                return "fragments/explorer :: libraryGrid";
            }
            return "fragments/explorer :: dashboardContent";
        }
        return "dashboard";
    }

    @PostMapping("/folders")
    public String createRootFolder(@RequestParam String name,
                                   @RequestParam(defaultValue = "#6c757d") String colorHex,
                                   Principal principal,
                                   @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        folderService.createFolder(name, colorHex, null, user);
        return "redirect:/folders";
    }

    @GetMapping("/folders/{id}")
    public String viewFolder(@PathVariable Long id, 
                             @RequestParam(required = false) String sortBy,
                             @RequestParam(required = false, defaultValue = "asc") String direction,
                             Model model, Principal principal,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                             @RequestHeader(value = "HX-Target", required = false) String hxTarget) {
        User user = userService.getByUsername(principal.getName());
        FolderView view = folderService.getFolderView(id, user, sortBy, direction);
        model.addAttribute("view", view);
        model.addAttribute("username", principal.getName());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);

        if (hxRequest != null) {
            if ("files-table-container".equals(hxTarget)) {
                return "fragments/folder-detail :: filesTable";
            }
            return "fragments/folder-detail :: folderDetail";
        }
        return "folder-page";
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
                                  @RequestParam(defaultValue = "#6c757d") String colorHex,
                                  Principal principal,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        folderService.createFolder(name, colorHex, id, user);

        if (hxRequest != null) {
            return "redirect:/folders/" + id;
        }
        return "redirect:/folders/" + id;
    }

    @PostMapping("/folders/{id}/edit")
    public String editFolder(@PathVariable Long id,
                             @RequestParam String name,
                             @RequestParam(defaultValue = "#6c757d") String colorHex,
                             @RequestParam(defaultValue = "folder") String iconName,
                             Principal principal) {
        User user = userService.getByUsername(principal.getName());
        folderService.updateFolder(id, name, colorHex, iconName, user);
        return "redirect:/folders/" + id;
    }

    @PostMapping("/folders/{id}/color")
    public String updateColor(@PathVariable Long id,
                              @RequestParam String colorHex,
                              Principal principal,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        folderService.updateFolderColor(id, colorHex, user);

        if (hxRequest != null) {
            return "redirect:/folders/" + id;
        }
        return "redirect:/folders/" + id;
    }

    @PostMapping("/folders/{id}/delete")
    public String deleteFolder(@PathVariable Long id, Principal principal,
                               RedirectAttributes redirectAttributes,
                               @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        try {
            folderService.deleteFolder(id, user);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Could not delete all files: " + e.getMessage());
        }
        return "redirect:/folders";
    }
}
