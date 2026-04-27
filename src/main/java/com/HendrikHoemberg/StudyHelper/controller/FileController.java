package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
import com.HendrikHoemberg.StudyHelper.service.FileStorageService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
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

    public FileController(FileEntryService fileEntryService,
                          FileStorageService fileStorageService,
                          UserService userService) {
        this.fileEntryService = fileEntryService;
        this.fileStorageService = fileStorageService;
        this.userService = userService;
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
            return "redirect:/folders";
        }
    }
}
