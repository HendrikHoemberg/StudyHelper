package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.config.SecurityConfig;
import com.HendrikHoemberg.StudyHelper.dto.CollectResult;
import com.HendrikHoemberg.StudyHelper.dto.CollectStatus;
import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.SavedSessionSummary;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.security.LoginAttemptService;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.DungeonSessionService;
import com.HendrikHoemberg.StudyHelper.service.DungeonShrineService;
import com.HendrikHoemberg.StudyHelper.service.DungeonViewModelBuilder;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.StorageQuotaService;
import com.HendrikHoemberg.StudyHelper.service.StudyLogService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * HTTP-layer smoke tests for the dungeon endpoints, run against the real
 * {@link SecurityConfig} so the security contract is actually exercised. These cover
 * what only the controller/Spring-Security layer can establish — authentication, CSRF
 * on the {@code @ResponseBody} collect beacons, collect status codes, and the
 * redirect-when-no-active-session guard — and that the unit tests on the services
 * cannot reach. View-rendering of game fragments is intentionally not exercised here
 * (that is covered by the service / view-model tests); these tests stay on the wire
 * contract.
 */
@WebMvcTest(controllers = {DungeonController.class, DungeonCombatController.class, DungeonRoomController.class})
@Import(SecurityConfig.class)
class DungeonControllerTests {

    private static final String SESSION_KEY = "dungeonSessionState";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private DungeonSessionService dungeonSessionService;
    @MockitoBean private SavedSessionService savedSessionService;
    @MockitoBean private StudyLogService studyLogService;
    @MockitoBean private UserService userService;
    @MockitoBean private DungeonViewModelBuilder viewModelBuilder;
    @MockitoBean private DungeonShrineService shrineService;

    // Dependencies of GlobalControllerAdvice, which @WebMvcTest also loads.
    @MockitoBean private FolderService folderService;
    @MockitoBean private DeckService deckService;
    @MockitoBean private AiRequestQuotaService aiRequestQuotaService;
    @MockitoBean private StorageQuotaService storageQuotaService;

    // Dependency of the imported SecurityConfig filter chain.
    @MockitoBean private LoginAttemptService loginAttemptService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);
    }

    private static DungeonSessionState minimalState() {
        return DungeonSessionState.builder().build();
    }

    // === Authentication ===

    @Test
    void resume_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get("/dungeon/resume"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void collectCoin_unauthenticated_isRejectedAndSkipsService() throws Exception {
        mockMvc.perform(post("/dungeon/collect/coin").with(csrf()).param("itemId", "r0_coin_0"))
            .andExpect(status().is3xxRedirection());

        verify(dungeonSessionService, never()).collectCoin(any(), any());
    }

    // === CSRF (authenticated but missing token) ===

    @Test
    void move_authenticatedWithoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/dungeon/move").with(user("alice")).param("direction", "UP"))
            .andExpect(status().isForbidden());

        verify(dungeonSessionService, never()).move(any(), any());
    }

    @Test
    void collectCoin_authenticatedWithoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/dungeon/collect/coin").with(user("alice")).param("itemId", "r0_coin_0"))
            .andExpect(status().isForbidden());

        verify(dungeonSessionService, never()).collectCoin(any(), any());
    }

    // === Collect beacon contract (@ResponseBody, render-independent) ===

    @Test
    void collectCoin_ok_returnsSuccessBodyAndPersists() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_KEY, minimalState());
        when(dungeonSessionService.collectCoin(any(), eq("r0_coin_0")))
            .thenReturn(new CollectResult(CollectStatus.OK, minimalState()));

        mockMvc.perform(post("/dungeon/collect/coin").with(user("alice")).with(csrf())
                .session(session).param("itemId", "r0_coin_0"))
            .andExpect(status().isOk())
            .andExpect(content().string("success"));

        verify(savedSessionService).saveDungeon(eq(user), any());
    }

    @Test
    void collectCoin_duplicate_returnsConflictWithEmptyBody() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_KEY, minimalState());
        when(dungeonSessionService.collectCoin(any(), any()))
            .thenReturn(new CollectResult(CollectStatus.DUPLICATE, minimalState()));

        mockMvc.perform(post("/dungeon/collect/coin").with(user("alice")).with(csrf())
                .session(session).param("itemId", "r0_coin_0"))
            .andExpect(status().isConflict())
            .andExpect(content().string(""));

        verify(savedSessionService, never()).saveDungeon(any(), any());
    }

    @Test
    void collectShield_rejected_returnsOkWithEmptyBody() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_KEY, minimalState());
        when(dungeonSessionService.collectShield(any(), any()))
            .thenReturn(new CollectResult(CollectStatus.REJECTED, minimalState()));

        mockMvc.perform(post("/dungeon/collect/shield").with(user("alice")).with(csrf())
                .session(session).param("itemId", "r0_shield_0"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(savedSessionService, never()).saveDungeon(any(), any());
    }

    // === No active session → redirect to start (render-independent) ===

    @Test
    void move_noActiveSession_redirectsToStartAndSkipsService() throws Exception {
        when(savedSessionService.loadDungeon(user)).thenReturn(Optional.empty());

        mockMvc.perform(post("/dungeon/move").with(user("alice")).with(csrf()).param("direction", "UP"))
            .andExpect(redirectedUrl("/study/start?mode=DUNGEON"));

        verify(dungeonSessionService, never()).move(any(), any());
    }

    // === Start guards against clobbering an existing saved run ===

    @Test
    void start_withExistingSavedSession_returnsConflictWithRetargetAndSkipsGeneration() throws Exception {
        when(savedSessionService.findForUser(user)).thenReturn(Optional.of(
            new SavedSessionSummary(SavedSessionType.DUNGEON, "Run", "Floor 1", Instant.now(), "/dungeon/resume")));

        mockMvc.perform(post("/dungeon/start").with(user("alice")).with(csrf())
                .param("dungeonMode", "FLASHCARDS")
                .param("dungeonSize", "SMALL"))
            .andExpect(status().isOk())
            .andExpect(header().string("HX-Retarget", "#modal-placeholder"))
            .andExpect(view().name("fragments/saved-session :: conflict"));

        verify(dungeonSessionService, never()).createFlashcardDungeon(any(), any(), any());
    }
}
