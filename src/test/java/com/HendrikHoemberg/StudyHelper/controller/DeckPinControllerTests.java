package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeckPinControllerTests {

    @Autowired MockMvc mvc;
    @Autowired DeckRepository deckRepository;
    @Autowired ObjectFactory<EntityManager> emFactory;
    @Autowired PasswordEncoder passwordEncoder;

    User owner;
    User other;
    Deck deck;

    @BeforeEach
    void setUp() {
        var em = emFactory.getObject();
        owner = persistUser(em, "owner");
        other = persistUser(em, "other");
        Folder folder = new Folder();
        folder.setName("Root");
        folder.setColorHex("#000");
        folder.setIconName("folder");
        folder.setUser(owner);
        em.persist(folder);
        deck = new Deck();
        deck.setName("D");
        deck.setUser(owner);
        deck.setFolder(folder);
        em.persist(deck);
        em.flush();
    }

    @Test
    void togglePin_flipsValueForOwner() throws Exception {
        mvc.perform(post("/decks/" + deck.getId() + "/pin")
                .with(csrf())
                .with(user(owner.getUsername())))
            .andExpect(status().isOk());

        assertThat(deckRepository.findById(deck.getId()).orElseThrow().isPinned()).isTrue();

        mvc.perform(post("/decks/" + deck.getId() + "/pin")
                .with(csrf())
                .with(user(owner.getUsername())))
            .andExpect(status().isOk());

        assertThat(deckRepository.findById(deck.getId()).orElseThrow().isPinned()).isFalse();
    }

    @Test
    void togglePin_rejectsNonOwner() throws Exception {
        mvc.perform(post("/decks/" + deck.getId() + "/pin")
                .with(csrf())
                .with(user(other.getUsername())))
            .andExpect(status().isNotFound());

        assertThat(deckRepository.findById(deck.getId()).orElseThrow().isPinned()).isFalse();
    }

    private User persistUser(EntityManager em, String prefix) {
        User u = new User();
        u.setUsername(prefix + "-" + System.nanoTime());
        u.setPassword(passwordEncoder.encode("password123"));
        em.persist(u);
        return u;
    }
}
