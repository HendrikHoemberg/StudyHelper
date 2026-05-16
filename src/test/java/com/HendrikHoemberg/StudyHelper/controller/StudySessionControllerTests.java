package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode;
import com.HendrikHoemberg.StudyHelper.dto.SessionMode;
import com.HendrikHoemberg.StudyHelper.dto.StudyCardView;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionConfig;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionStats;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.StudySessionService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = StudySessionController.class)
class StudySessionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudySessionService studySessionService;

    @MockitoBean
    private FlashcardService flashcardService;

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

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        when(userService.getByUsername("alice")).thenReturn(user);
    }

    @Test
    @WithMockUser(username = "alice")
    void nextCard_ReturnsCardFragmentContract() throws Exception {
        StudySessionState state = activeState();
        StudyCardView card = state.queue().get(0);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studySessionState", state);

        when(studySessionService.isComplete(state)).thenReturn(false);
        when(studySessionService.nextCard(state)).thenReturn(card);
        when(flashcardService.getFlashcardForUser(card.cardId(), user)).thenReturn(new Flashcard());

        mockMvc.perform(get("/session/next")
            .principal(() -> "alice")
                .header("HX-Request", "true")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("fragments/study-card :: studyCard"));
    }

    @Test
    @WithMockUser(username = "alice")
    void answer_WhenComplete_RendersCompletionFragment() throws Exception {
        StudySessionState state = activeState();
        StudyCardView card = state.queue().get(0);
        StudySessionState completed = new StudySessionState(
            state.config(),
            state.cardsByDeck(),
            state.queue(),
            1,
            1,
            1,
            0,
            List.of()
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studySessionState", state);

        when(flashcardService.getFlashcardForUser(card.cardId(), user)).thenReturn(new Flashcard());
        when(studySessionService.recordAnswer(state, card.cardId(), true)).thenReturn(completed);
        when(studySessionService.isComplete(completed)).thenReturn(true);
        when(studySessionService.buildStats(completed)).thenReturn(new StudySessionStats(1, 1, 1, 0, 100));

        mockMvc.perform(post("/session/answer")
                .with(csrf())
            .principal(() -> "alice")
                .header("HX-Request", "true")
                .session(session)
                .param("cardId", String.valueOf(card.cardId()))
                .param("isCorrect", "true"))
            .andExpect(status().isOk())
            .andExpect(view().name("fragments/study-complete :: studyComplete"));
    }

    private StudySessionState activeState() {
        StudyCardView card = new StudyCardView(10L, "Q", "A", 1L, "Deck", "Root", null, null, null, null);
        return new StudySessionState(
            new StudySessionConfig(List.of(1L), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER),
            Map.of(1L, List.of(card)),
            List.of(card),
            0,
            0,
            0,
            0,
            List.of()
        );
    }
}
