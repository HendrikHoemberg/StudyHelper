package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import com.HendrikHoemberg.StudyHelper.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

@WebMvcTest(controllers = FlashcardController.class)
class FlashcardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeckService deckService;

    @MockitoBean
    private FlashcardService flashcardService;

    @MockitoBean
    private FolderService folderService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AiRequestQuotaService aiRequestQuotaService;

    @MockitoBean
    private StorageQuotaService storageQuotaService;

    @MockitoBean
    private SavedSessionService savedSessionService;

    @MockitoBean
    private FlashcardRepository flashcardRepository;

    @MockitoBean
    private FileStorageService fileStorageService;

    private User user;
    private Deck deck;
    private Flashcard flashcard;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        deck = new Deck();
        deck.setId(10L);
        deck.setUser(user);

        flashcard = new Flashcard();
        flashcard.setId(1404L);
        flashcard.setDeck(deck);
        flashcard.setFrontText("Front");
        flashcard.setBackText("Back");

        when(userService.getByUsername("alice")).thenReturn(user);
        when(flashcardService.getFlashcardForUser(1404L, user)).thenReturn(flashcard);
    }

    @Test
    @WithMockUser(username = "alice")
    void testGetEditFlashcard() throws Exception {
        mockMvc.perform(get("/flashcards/1404/edit")
                .principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/flashcard-form :: flashcardModal"))
                .andExpect(model().attributeExists("flashcard"))
                .andExpect(model().attribute("deckId", 10L));
    }

    @Test
    @WithMockUser(username = "alice")
    void testPostEditFlashcard() throws Exception {
        mockMvc.perform(post("/flashcards/1404/edit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .principal(() -> "alice")
                .param("deckId", "10")
                .param("frontText", "Updated Front")
                .param("backText", "Updated Back"))
                .andExpect(status().is3xxRedirection());
    }
}
