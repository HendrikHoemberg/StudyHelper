package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
import com.HendrikHoemberg.StudyHelper.service.FileStorageService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.PdfThumbnailService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FileController.class)
class FileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileEntryService fileEntryService;

    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FolderService folderService;

    @MockitoBean
    private PdfThumbnailService pdfThumbnailService;

    @MockitoBean
    private DeckService deckService;

    @MockitoBean
    private AiRequestQuotaService aiRequestQuotaService;

    @MockitoBean
    private StorageQuotaService storageQuotaService;

    private User user;
    private FileEntry pdf;

    @BeforeEach
    void setUp() throws IOException {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        Folder folder = new Folder();
        folder.setId(42L);
        folder.setUser(user);

        pdf = new FileEntry();
        pdf.setId(7L);
        pdf.setOriginalFilename("notes.pdf");
        pdf.setStoredFilename("stored-notes.pdf");
        pdf.setMimeType("application/pdf");
        pdf.setFolder(folder);
        pdf.setUser(user);

        when(userService.getByUsername("alice")).thenReturn(user);
        when(fileEntryService.getFile(7L, user)).thenReturn(pdf);
        when(pdfThumbnailService.thumbnailFor(pdf)).thenReturn(new ByteArrayResource(new byte[] {1, 2, 3}));
    }

    @Test
    @WithMockUser(username = "alice")
    void thumbnail_ReturnsGeneratedPdfPreviewImage() throws Exception {
        mockMvc.perform(get("/files/7/thumbnail")
                .with(csrf())
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(header().string("Cache-Control", "private, max-age=86400"))
            .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }
}
