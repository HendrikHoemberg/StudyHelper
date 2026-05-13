package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.RegistrationCodeSummary;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.entity.UserRole;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import com.HendrikHoemberg.StudyHelper.service.AdminService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.RegistrationCodeService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = AdminController.class)
class AdminControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private RegistrationCodeService registrationCodeService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FolderService folderService;

    @MockitoBean
    private DeckService deckService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        when(userService.getByUsername("admin")).thenReturn(admin);
        when(userRepository.findByUsername("admin")).thenReturn(java.util.Optional.of(admin));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminDashboard_AsAdmin_RendersPage() throws Exception {
        mockMvc.perform(get("/admin").with(csrf()).principal(() -> "admin"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminDashboard_WithRegistrationCodes_RendersCodeUsers() throws Exception {
        when(registrationCodeService.listSummaries()).thenReturn(List.of(new RegistrationCodeSummary(
            1L,
            Instant.parse("2026-05-13T10:00:00Z"),
            Instant.parse("2026-05-16T10:00:00Z"),
            null,
            null,
            "admin",
            "alice",
            "Used"
        )));

        mockMvc.perform(get("/admin").with(csrf()).principal(() -> "admin"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin"))
            .andExpect(content().string(containsString("admin")))
            .andExpect(content().string(containsString("alice")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void disableUser_WhenSelfProtectionTriggered_ShowsFlashError() throws Exception {
        doThrow(new IllegalArgumentException("You cannot disable your own account."))
            .when(adminService).disableUser(any(), any());

        mockMvc.perform(post("/admin/users/1/disable").with(csrf()).principal(() -> "admin"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin"))
            .andExpect(flash().attribute("error", "You cannot disable your own account."));
    }
}
