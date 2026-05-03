package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
import com.HendrikHoemberg.StudyHelper.service.FileStorageService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import com.HendrikHoemberg.StudyHelper.service.FolderView;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;

@Controller
public class FileController {

    private final FileEntryService fileEntryService;
    private final FileStorageService fileStorageService;
    private final UserService userService;
    private final FolderService folderService;

    public FileController(FileEntryService fileEntryService,
                          FileStorageService fileStorageService,
                          UserService userService,
                          FolderService folderService) {
        this.fileEntryService = fileEntryService;
        this.fileStorageService = fileStorageService;
        this.userService = userService;
        this.folderService = folderService;
    }

    @GetMapping("/folders/{folderId}/files/upload")
    public String uploadModal(@PathVariable Long folderId, Model model) {
        model.addAttribute("folderId", folderId);
        return "fragments/file-form :: uploadModal";
    }

    @PostMapping("/folders/{folderId}/files")
    public String upload(@PathVariable Long folderId,
                         @RequestParam("file") MultipartFile file,
                         Principal principal,
                         RedirectAttributes redirectAttributes,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        try {
            fileEntryService.upload(file, folderId, user);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
        }
        return "redirect:/folders/" + folderId;
    }

    @GetMapping("/files/{id}/view")
    public ResponseEntity<Resource> view(@PathVariable Long id, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        FileEntry entry = fileEntryService.getFile(id, user);
        Resource resource = fileStorageService.load(entry.getStoredFilename());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(entry.getMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + entry.getOriginalFilename() + "\"")
            .body(resource);
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        FileEntry entry = fileEntryService.getFile(id, user);
        Resource resource = fileStorageService.load(entry.getStoredFilename());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + entry.getOriginalFilename() + "\"")
            .body(resource);
    }

    @PostMapping(value = "/files/{id}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String editOverwrite(@PathVariable Long id,
                                @RequestParam("image") MultipartFile image,
                                Principal principal,
                                Model model) throws IOException {
        User user = userService.getByUsername(principal.getName());
        Long folderId = fileEntryService.replaceContents(id, image, user);
        FolderView view = folderService.getFolderView(folderId, user, null, "asc");
        model.addAttribute("view", view);
        model.addAttribute("sortBy", null);
        model.addAttribute("direction", "asc");
        return "fragments/folder-detail :: filesTable";
    }

    @PostMapping("/files/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal,
                         RedirectAttributes redirectAttributes,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        try {
            Long folderId = fileEntryService.deleteAndGetFolderId(id, user);
            return "redirect:/folders/" + folderId;
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Could not delete file from disk.");
            return "redirect:/dashboard";
        }
    }
}
