package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.entity.UserRole;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void seedUser() {
        userRepository.findByUsername("settings-tester").ifPresent(userRepository::delete);
        user = new User();
        user.setUsername("settings-tester");
        user.setPassword(passwordEncoder.encode("currentpw1"));
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user = userRepository.save(user);
    }

    @Test
    void getSettings_RendersView() throws Exception {
        mockMvc.perform(get("/settings").with(user("settings-tester")))
            .andExpect(status().isOk())
            .andExpect(view().name("settings"));
    }

    @Test
    void postPassword_WrongCurrent_RerendersWithError() throws Exception {
        mockMvc.perform(post("/settings/password")
                .with(user("settings-tester")).with(csrf())
                .param("currentPassword", "wrong")
                .param("newPassword", "newpw5678")
                .param("confirmPassword", "newpw5678"))
            .andExpect(status().isOk())
            .andExpect(view().name("settings"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Current password is incorrect")));
    }

    @Test
    void postPassword_HappyPath_RedirectsAndUpdates() throws Exception {
        mockMvc.perform(post("/settings/password")
                .with(user("settings-tester")).with(csrf())
                .param("currentPassword", "currentpw1")
                .param("newPassword", "newpw5678")
                .param("confirmPassword", "newpw5678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/settings"));

        User reloaded = userRepository.findByUsername("settings-tester").orElseThrow();
        assertThat(passwordEncoder.matches("newpw5678", reloaded.getPassword())).isTrue();
    }

    @Test
    void postLanguage_PersistsAndRedirects() throws Exception {
        mockMvc.perform(post("/settings/language")
                .with(user("settings-tester")).with(csrf())
                .param("language", "de"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/settings"));

        User reloaded = userRepository.findByUsername("settings-tester").orElseThrow();
        assertThat(reloaded.getLanguage()).isEqualTo("de");
    }
}
