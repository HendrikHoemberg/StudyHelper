package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeckController.class)
class DeckControllerTests {

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

    private User user;
    private Folder folder;
    private Deck deck;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        folder = new Folder();
        folder.setId(42L);
        folder.setName("Math");
        folder.setColorHex("#abcdef");
        folder.setUser(user);

        deck = new Deck();
        deck.setId(7L);
        deck.setName("Vocabulary");
        deck.setFolder(folder);
        deck.setUser(user);

        when(userService.getByUsername("alice")).thenReturn(user);
        when(folderService.getFolder(eq(42L), eq(user))).thenReturn(folder);
        when(deckService.createDeck(any(), any(), any(), eq(42L), eq(user))).thenReturn(deck);
        when(deckService.updateDeck(eq(7L), any(), any(), any(), eq(user))).thenReturn(deck);
        when(deckService.deleteDeck(eq(7L), eq(user))).thenReturn(42L);
    }

    @Test
    @WithMockUser(username = "alice")
    void createDeckPersistsColorAndIcon() throws Exception {
        mockMvc.perform(post("/folders/42/decks")
                .with(csrf())
                .param("name", "Vocabulary")
                .param("colorHex", "#ff8800")
                .param("iconName", "book"))
            .andExpect(status().is3xxRedirection());

        verify(deckService).createDeck("Vocabulary", "#ff8800", "book", 42L, user);
    }

    @Test
    @WithMockUser(username = "alice")
    void createDeckWithoutColorAndIconUsesNulls() throws Exception {
        mockMvc.perform(post("/folders/42/decks")
                .with(csrf())
                .param("name", "Vocabulary"))
            .andExpect(status().is3xxRedirection());

        verify(deckService).createDeck("Vocabulary", null, null, 42L, user);
    }

    @Test
    @WithMockUser(username = "alice")
    void editDeckPersistsColorAndIcon() throws Exception {
        mockMvc.perform(post("/decks/7/rename")
                .with(csrf())
                .param("name", "Vocab")
                .param("colorHex", "#112233")
                .param("iconName", "languages"))
            .andExpect(status().is3xxRedirection());

        verify(deckService).updateDeck(7L, "Vocab", "#112233", "languages", user);
    }

    @Test
    @WithMockUser(username = "alice")
    void deleteDeckRedirectsToFolder() throws Exception {
        mockMvc.perform(post("/decks/7/delete").with(csrf()))
            .andExpect(status().is3xxRedirection());

        verify(deckService).deleteDeck(7L, user);
    }
}
