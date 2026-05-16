package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.InviteRegistrationService;
import com.HendrikHoemberg.StudyHelper.service.RegistrationCodeService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = RegistrationController.class)
class RegistrationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InviteRegistrationService inviteRegistrationService;

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
    void registerPage_IsPublicAndRenders() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"));
    }

    @Test
    void register_WithValidInput_RedirectsToLoginWithSuccessFlash() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("code", "INVITE-123")
                .param("username", "alice")
                .param("password", "password123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andExpect(flash().attribute("successMessage", "Account created. You can now sign in."));

        verify(inviteRegistrationService).register("INVITE-123", "alice", "password123");
    }

    @Test
    void register_WithInvalidCode_RendersRegisterWithErrorAndPreservesInput() throws Exception {
        doThrow(new IllegalArgumentException(RegistrationCodeService.INVALID_CODE_MESSAGE))
            .when(inviteRegistrationService).register("BAD-CODE", "alice", "password123");

        mockMvc.perform(post("/register")
                .with(csrf())
                .param("code", "BAD-CODE")
                .param("username", "alice")
                .param("password", "password123"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attribute("errorMessage", RegistrationCodeService.INVALID_CODE_MESSAGE))
            .andExpect(model().attribute("code", "BAD-CODE"))
            .andExpect(model().attribute("username", "alice"));
    }
}
