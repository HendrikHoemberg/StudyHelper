package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.SavedSession;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.SavedSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyDueControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectFactory<EntityManager> emFactory;
    @Autowired SavedSessionRepository savedSessionRepository;

    User user;

    @BeforeEach
    void setUp() {
        var em = emFactory.getObject();
        user = new User();
        user.setUsername("due-" + System.nanoTime());
        user.setPassword("x".repeat(60));
        em.persist(user);

        Folder folder = new Folder();
        folder.setName("Root");
        folder.setColorHex("#0f766e");
        folder.setIconName("folder");
        folder.setUser(user);
        em.persist(folder);

        Deck deck = new Deck();
        deck.setName("Due deck");
        deck.setUser(user);
        deck.setFolder(folder);
        deck.setColorHex("#0f766e");
        deck.setIconName("layers");
        em.persist(deck);

        Flashcard card = new Flashcard();
        card.setFrontText("Front");
        card.setBackText("Back");
        card.setDeck(deck);
        card.setCorrectStreak(1);
        card.setDueDate(LocalDate.now());
        em.persist(card);
        em.flush();
    }

    @Test
    void startDue_withDueCardAndNoSavedSession_redirectsToSessionNext() throws Exception {
        mvc.perform(post("/study/start-due").with(csrf()).with(user(user.getUsername())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/session/next"));

        assertThat(savedSessionRepository.findByUser(user)).isPresent();
    }

    @Test
    void startDue_whenNoDueCards_redirectsToDashboard() throws Exception {
        var em = emFactory.getObject();
        User empty = new User();
        empty.setUsername("empty-due-" + System.nanoTime());
        empty.setPassword("x".repeat(60));
        em.persist(empty);
        em.flush();

        mvc.perform(post("/study/start-due").with(csrf()).with(user(empty.getUsername())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void startDue_withSavedSession_returnsConflictModalAndLeavesSavedSessionIntact() throws Exception {
        var em = emFactory.getObject();
        SavedSession saved = new SavedSession();
        saved.setUser(user);
        saved.setType(SavedSessionType.FLASHCARDS);
        saved.setPayload("{}");
        saved.setTitle("Flashcards - Due deck");
        saved.setProgressLabel("1 / 3 answered");
        em.persist(saved);
        em.flush();

        mvc.perform(post("/study/start-due").with(csrf()).with(user(user.getUsername())))
            .andExpect(status().isOk())
            .andExpect(header().string("HX-Retarget", "#modal-placeholder"))
            .andExpect(header().string("HX-Reswap", "innerHTML"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("saved-session-conflict-modal")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Saved session in progress")));

        assertThat(savedSessionRepository.findByUser(user)).isPresent();
    }
}
