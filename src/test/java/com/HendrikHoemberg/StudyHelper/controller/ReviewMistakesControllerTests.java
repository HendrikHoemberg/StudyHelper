package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewMistakesControllerTests {

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
        Folder folder = new Folder();
        folder.setName("Root");
        folder.setColorHex("#000");
        folder.setIconName("folder");
        folder.setUser(user);
        em.persist(folder);
        Deck deck = new Deck();
        deck.setName("D");
        deck.setUser(user);
        deck.setFolder(folder);
        em.persist(deck);
        Flashcard c = new Flashcard();
        c.setFrontText("F"); c.setBackText("B"); c.setDeck(deck); c.setCorrectStreak(0);
        em.persist(c);
        em.flush();
    }

    @Test
    void startReview_redirectsToSessionNext() throws Exception {
        mvc.perform(post("/study/review-mistakes").with(csrf()).with(user(user.getUsername())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/session/next"));
    }

    @Test
    void startReview_whenNoMistakes_redirectsToDashboard() throws Exception {
        var em = emFactory.getObject();
        User empty = new User();
        empty.setUsername("empty-" + System.nanoTime());
        empty.setPassword("x".repeat(60));
        em.persist(empty);
        em.flush();

        mvc.perform(post("/study/review-mistakes").with(csrf()).with(user(empty.getUsername())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }
}
