package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.SavedSessionSummary;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SavedSessionController.class)
class SavedSessionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SavedSessionService savedSessionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FolderService folderService;

    @MockitoBean
    private DeckService deckService;

    @MockitoBean
    private AiRequestQuotaService aiRequestQuotaService;

    @MockitoBean
    private StorageQuotaService storageQuotaService;

    @Test
    @WithMockUser(username = "alice")
    void resumeDispatcher_redirectsToTypedUrl() throws Exception {
        User user = new User();
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);
        when(savedSessionService.findForUser(user)).thenReturn(Optional.of(
                new SavedSessionSummary(SavedSessionType.QUIZ, "t", "p", Instant.now(), "/quiz/resume")
        ));

        mockMvc.perform(get("/sessions/saved/resume").principal(() -> "alice"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quiz/resume"));
    }

    @Test
    @WithMockUser(username = "alice")
    void resumeDispatcher_noSavedSession_redirectsToDashboard() throws Exception {
        User user = new User();
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);
        when(savedSessionService.findForUser(user)).thenReturn(Optional.empty());

        mockMvc.perform(get("/sessions/saved/resume").principal(() -> "alice"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @WithMockUser(username = "alice")
    void discard_callsService() throws Exception {
        User user = new User();
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);

        mockMvc.perform(post("/sessions/saved/discard").with(csrf()).principal(() -> "alice"))
                .andExpect(status().is3xxRedirection());

        verify(savedSessionService).discard(user);
    }
}
