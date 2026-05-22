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
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.StudyLogService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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

    @MockitoBean
    private SavedSessionService savedSessionService;

    @MockitoBean
    private StudyLogService studyLogService;

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

    @Test
    @WithMockUser(username = "alice")
    void answer_persistsNewStateViaSavedSessionService() throws Exception {
        User user = new User(); user.setId(1L); user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);

        StudyCardView card = new StudyCardView(101L, "f", "b", 10L, "Deck", "Root", "#fff", null, null, null);
        StudySessionConfig config = new StudySessionConfig(List.of(10L), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER, false, 20);
        StudySessionState before = new StudySessionState(config, Map.of(10L, List.of(card)), List.of(card), 0, 0, 0, 0, List.of());
        StudySessionState after = new StudySessionState(config, Map.of(10L, List.of(card)), List.of(card), 1, 1, 1, 0, List.of());

        when(studySessionService.recordAnswer(any(), eq(101L), eq(true))).thenReturn(after);
        when(studySessionService.isComplete(after)).thenReturn(true);
        when(studySessionService.buildStats(after)).thenReturn(new StudySessionStats(1, 1, 1, 0, 100));
        when(flashcardService.getFlashcardForUser(eq(101L), eq(user))).thenReturn(new Flashcard());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studySessionState", before);

        mockMvc.perform(post("/session/answer").session(session).with(csrf())
                .principal(() -> "alice")
                .header("HX-Request", "true")
                .param("cardId", "101").param("isCorrect", "true"))
            .andExpect(status().isOk());

        verify(savedSessionService).discard(user);
        verify(savedSessionService, never()).saveFlashcards(any(), any());
    }

    @Test
    @WithMockUser(username = "alice")
    void answer_inProgress_callsSaveFlashcards() throws Exception {
        User user = new User(); user.setId(1L); user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);

        StudyCardView c1 = new StudyCardView(101L, "f1", "b1", 10L, "Deck", "Root", "#fff", null, null, null);
        StudyCardView c2 = new StudyCardView(102L, "f2", "b2", 10L, "Deck", "Root", "#fff", null, null, null);
        StudySessionConfig config = new StudySessionConfig(List.of(10L), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER, false, 20);
        StudySessionState before = new StudySessionState(config, Map.of(10L, List.of(c1, c2)), List.of(c1, c2), 0, 0, 0, 0, List.of());
        StudySessionState after = new StudySessionState(config, Map.of(10L, List.of(c1, c2)), List.of(c1, c2), 1, 1, 1, 0, List.of());

        when(studySessionService.recordAnswer(any(), eq(101L), eq(true))).thenReturn(after);
        when(studySessionService.isComplete(after)).thenReturn(false);
        when(studySessionService.nextCard(any())).thenReturn(c1);
        when(flashcardService.getFlashcardForUser(eq(101L), eq(user))).thenReturn(new Flashcard());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studySessionState", before);

        mockMvc.perform(post("/session/answer").session(session).with(csrf())
                .principal(() -> "alice")
                .header("HX-Request", "true")
                .param("cardId", "101").param("isCorrect", "true"))
            .andExpect(status().isOk());

        verify(savedSessionService).saveFlashcards(user, after);
        verify(savedSessionService, never()).discard(any());
    }

    @Test
    @WithMockUser(username = "alice")
    void resume_noSavedSession_redirectsToStart() throws Exception {
        User user = new User(); user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);
        when(savedSessionService.loadFlashcards(user)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/study/resume").principal(() -> "alice"))
            .andExpect(redirectedUrl("/study/start?mode=FLASHCARDS"));
    }

    @Test
    @WithMockUser(username = "alice")
    void resume_allCardsRemoved_discardsAndFlashes() throws Exception {
        User user = new User(); user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);

        StudyCardView c1 = new StudyCardView(101L, "f", "b", 10L, "Deck", "Root", "#fff", null, null, null);
        StudySessionConfig config = new StudySessionConfig(List.of(10L), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER, false, 20);
        StudySessionState saved = new StudySessionState(config, Map.of(10L, List.of(c1)), List.of(c1), 0, 0, 0, 0, List.of());
        StudySessionState empty = new StudySessionState(config, Map.of(), List.of(), 0, 0, 0, 0, List.of());

        when(savedSessionService.loadFlashcards(user)).thenReturn(java.util.Optional.of(saved));
        when(savedSessionService.reconcileFlashcards(saved, user))
            .thenReturn(new SavedSessionService.ReconcileResult(empty, 1));

        mockMvc.perform(get("/study/resume").principal(() -> "alice"))
            .andExpect(redirectedUrl("/study/start?mode=FLASHCARDS"))
            .andExpect(flash().attributeExists("studyError"));

        verify(savedSessionService).discard(user);
    }

    @Test
    @WithMockUser(username = "alice")
    void resume_partialRemoval_redirectsToNextWithNotice() throws Exception {
        User user = new User(); user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);

        StudyCardView c1 = new StudyCardView(101L, "f", "b", 10L, "Deck", "Root", "#fff", null, null, null);
        StudyCardView c2 = new StudyCardView(102L, "f", "b", 10L, "Deck", "Root", "#fff", null, null, null);
        StudySessionConfig config = new StudySessionConfig(List.of(10L), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER, false, 20);
        StudySessionState saved = new StudySessionState(config, Map.of(10L, List.of(c1, c2)), List.of(c1, c2), 0, 0, 0, 0, List.of());
        StudySessionState reconciled = new StudySessionState(config, Map.of(10L, List.of(c2)), List.of(c2), 0, 0, 0, 0, List.of());

        when(savedSessionService.loadFlashcards(user)).thenReturn(java.util.Optional.of(saved));
        when(savedSessionService.reconcileFlashcards(saved, user))
            .thenReturn(new SavedSessionService.ReconcileResult(reconciled, 1));

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/study/resume").session(session).principal(() -> "alice"))
            .andExpect(redirectedUrl("/session/next"));

        verify(savedSessionService).saveFlashcards(user, reconciled);
        org.assertj.core.api.Assertions.assertThat(session.getAttribute("studyResumeNotice"))
            .isEqualTo("1 card(s) were removed since you paused.");
    }

    private StudySessionState activeState() {
        StudyCardView card = new StudyCardView(10L, "Q", "A", 1L, "Deck", "Root", null, null, null, null);
        return new StudySessionState(
            new StudySessionConfig(List.of(1L), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER, false, 20),
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
