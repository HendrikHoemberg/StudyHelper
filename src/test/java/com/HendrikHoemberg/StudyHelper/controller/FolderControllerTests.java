package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.ActiveTab;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.FolderView;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = FolderController.class)
class FolderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FolderService folderService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private DeckService deckService;

    @MockitoBean
    private FileEntryService fileEntryService;

    @MockitoBean
    private AiRequestQuotaService aiRequestQuotaService;

    @MockitoBean
    private StorageQuotaService storageQuotaService;

    private User user;
    private Folder folder;
    private FolderView folderView;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        folder = new Folder();
        folder.setId(42L);
        folder.setName("Test Folder");
        folder.setUser(user);

        folderView = new FolderView(
            folder, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.singletonList(folder), 0, ActiveTab.FOLDERS, false
        );

        when(userService.getByUsername("alice")).thenReturn(user);
        when(folderService.getFolderView(eq(42L), eq(user), any(), any(), any(ActiveTab.class)))
            .thenReturn(folderView);
    }

    @Test
    @WithMockUser(username = "alice")
    void viewFolder_DefaultTab_IsFolders() throws Exception {
        mockMvc.perform(get("/folders/42")
                .with(csrf())
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("folder-page"))
            .andExpect(model().attribute("view", folderView));
    }

    @Test
    @WithMockUser(username = "alice")
    void viewFolder_WithTabParam_PassesActiveTab() throws Exception {
        FolderView decksView = new FolderView(
            folder, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.singletonList(folder), 0, ActiveTab.DECKS, false
        );
        when(folderService.getFolderView(eq(42L), eq(user), any(), any(), eq(ActiveTab.DECKS)))
            .thenReturn(decksView);

        mockMvc.perform(get("/folders/42")
                .with(csrf())
                .param("tab", "decks")
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("folder-page"))
            .andExpect(model().attribute("view", decksView));
    }

    @Test
    @WithMockUser(username = "alice")
    void viewFolder_InvalidTabParam_DefaultsToFolders() throws Exception {
        mockMvc.perform(get("/folders/42")
                .with(csrf())
                .param("tab", "invalid")
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("folder-page"))
            .andExpect(model().attribute("view", folderView));
    }

    @Test
    @WithMockUser(username = "alice")
    void viewFolder_HtmxRequest_ReturnsFolderDetailFragment() throws Exception {
        mockMvc.perform(get("/folders/42")
                .with(csrf())
                .header("HX-Request", "true")
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("fragments/folder-detail :: folderDetail"));
    }

    @Test
    @WithMockUser(username = "alice")
    void viewFolder_FilesTab_RendersCompactIconOnlyFileCardsWithInfoModal() throws Exception {
        FileEntry file = new FileEntry();
        file.setId(7L);
        file.setOriginalFilename("Docker für Anwendungsentwickler.pdf");
        file.setStoredFilename("stored.pdf");
        file.setMimeType("application/pdf");
        file.setFileSizeBytes(1_265_971L);
        file.setUploadedAt(LocalDateTime.of(2026, 5, 7, 15, 10));
        file.setFolder(folder);
        file.setUser(user);

        FolderView filesView = new FolderView(
            folder, Collections.emptyList(), Collections.emptyList(),
            List.of(file), Collections.singletonList(folder), 0, ActiveTab.FILES, false
        );
        when(folderService.getFolderView(eq(42L), eq(user), any(), any(), eq(ActiveTab.FILES)))
            .thenReturn(filesView);

        mockMvc.perform(get("/folders/42")
                .with(csrf())
                .param("tab", "files")
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("aria-label=\"File information\"")))
            .andExpect(content().string(containsString("class=\"sh-file-info-modals\"")))
            .andExpect(content().string(containsString("Details for Docker für Anwendungsentwickler.pdf")))
            .andExpect(content().string(containsString("title=\"Generate flashcards\"")))
            .andExpect(content().string(containsString("/flashcards/generate?fileId=7")))
            .andExpect(content().string(not(containsString("<div class=\"sh-deck-meta\" th:text=\"${file.mimeType}\""))))
            .andExpect(content().string(not(containsString(">Quiz</a>"))))
            .andExpect(content().string(not(containsString(">Edit</button>"))))
            .andExpect(content().string(not(containsString(">Download</a>"))))
            .andExpect(content().string(not(containsString(">Delete</button>"))));
    }
}
