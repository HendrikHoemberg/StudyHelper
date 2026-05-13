package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@WebMvcTest(controllers = QuotaController.class)
class QuotaControllerTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AiRequestQuotaService aiRequestQuotaService;

    @MockitoBean
    StorageQuotaService storageQuotaService;

    @MockitoBean
    FolderService folderService;

    @MockitoBean
    DeckService deckService;

    @MockitoBean
    UserRepository userRepository;

    @Test
    @WithMockUser
    void quotaEndpoint_returnsOkForAuthenticatedUser() throws Exception {
        User user = new User();
        user.setDailyAiRequestLimit(10);
        when(userService.getByUsername("user")).thenReturn(user);
        when(storageQuotaService.usedBytes(any())).thenReturn(500L);
        when(aiRequestQuotaService.todayUsed(any())).thenReturn(3);

        mockMvc.perform(get("/api/quota").with(csrf()).principal(() -> "user"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void quotaEndpoint_rendersQuotaBars() throws Exception {
        User user = new User();
        user.setDailyAiRequestLimit(10);
        when(userService.getByUsername("user")).thenReturn(user);
        when(storageQuotaService.usedBytes(any())).thenReturn(500L);
        when(aiRequestQuotaService.todayUsed(any())).thenReturn(3);

        mockMvc.perform(get("/api/quota").with(csrf()).principal(() -> "user"))
            .andExpect(status().isOk())
            .andExpect(xpath("//div[@id='quota-desktop']").exists())
            .andExpect(xpath("//div[@id='quota-mobile']").exists());
    }
}