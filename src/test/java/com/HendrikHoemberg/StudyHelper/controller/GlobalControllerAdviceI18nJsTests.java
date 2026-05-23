package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.entity.UserRole;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalControllerAdviceI18nJsTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUser() {
        userRepository.findByUsername("i18n-en-user").ifPresent(userRepository::delete);
        userRepository.findByUsername("i18n-de-user").ifPresent(userRepository::delete);
        User en = new User();
        en.setUsername("i18n-en-user");
        en.setPassword(passwordEncoder.encode("password1"));
        en.setRole(UserRole.USER);
        en.setEnabled(true);
        en.setLanguage("en");
        userRepository.save(en);

        User de = new User();
        de.setUsername("i18n-de-user");
        de.setPassword(passwordEncoder.encode("password1"));
        de.setRole(UserRole.USER);
        de.setEnabled(true);
        de.setLanguage("de");
        userRepository.save(de);
    }

    @Test
    void englishUser_PageContainsEnglishI18nDict() throws Exception {
        mockMvc.perform(get("/settings").with(user("i18n-en-user")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"test.hello\":\"Hello\"")));
    }

    @Test
    void germanUser_PageContainsGermanI18nDict() throws Exception {
        mockMvc.perform(get("/settings").with(user("i18n-de-user")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"test.hello\":\"Hallo\"")));
    }
}
