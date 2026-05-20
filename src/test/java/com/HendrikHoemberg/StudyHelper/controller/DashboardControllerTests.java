package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectFactory<EntityManager> emFactory;

    User user;

    @BeforeEach
    void setUp() {
        var em = emFactory.getObject();
        user = new User();
        user.setUsername("u-" + System.nanoTime());
        user.setPassword("x".repeat(60));
        em.persist(user);
        em.flush();
    }

    @Test
    void getDashboard_returnsDashboardViewWithViewModel() throws Exception {
        mvc.perform(get("/dashboard").with(user(user.getUsername())))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"))
            .andExpect(model().attributeExists("vm"));
    }

    @Test
    void getDashboard_htmxReturnsFragment() throws Exception {
        mvc.perform(get("/dashboard")
                .with(user(user.getUsername()))
                .header("HX-Request", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("sh-dashboard-content")));
    }
}
