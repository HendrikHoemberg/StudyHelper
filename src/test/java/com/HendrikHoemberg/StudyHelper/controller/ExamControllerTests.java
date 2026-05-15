package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.ExamLayout;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionSize;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestion;
import com.HendrikHoemberg.StudyHelper.dto.ExamSessionState;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationDiagnostics;
import com.HendrikHoemberg.StudyHelper.service.AiGenerationException;
import com.HendrikHoemberg.StudyHelper.service.AiQuotaExceededException;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.AiExamService;
import com.HendrikHoemberg.StudyHelper.service.ExamService;
import com.HendrikHoemberg.StudyHelper.service.ExamSessionService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamControllerTests {

    private ExamController controller;
    private UserService userService;
    private AiExamService aiExamService;
    private ExamSessionService examSessionService;
    private AiRequestQuotaService aiRequestQuotaService;
    private User user;

    @BeforeEach
    void setUp() {
        aiExamService = mock(AiExamService.class);
        ExamService examService = mock(ExamService.class);
        userService = mock(UserService.class);
        examSessionService = mock(ExamSessionService.class);
        aiRequestQuotaService = mock(AiRequestQuotaService.class);
        controller = new ExamController(
            examSessionService,
            examService,
            userService,
            aiExamService,
            aiRequestQuotaService
        );

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);
    }

    @Test
    void createSession_HtmxWithoutSources_ReturnsAiGenerationErrorFragment() throws Exception {
        doThrow(new IllegalArgumentException("Please select at least one source."))
            .when(examSessionService).createSession(
                anyList(), anyList(), any(), any(),
                any(), anyInt(), any(), any(), any()
            );

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
            List.of(),
            List.of(),
            null,
            new MockHttpServletRequest(),
            ExamQuestionSize.MEDIUM,
            5,
            null,
            ExamLayout.PER_PAGE,
            model,
            () -> "alice",
            new MockHttpSession(),
            response,
            "true"
        );

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("HX-Trigger")).isNull();
        assertThat(view).isEqualTo("fragments/ai-generation-error :: aiGenerationError");
        assertThat(model.get("aiErrorTitle")).isEqualTo("AI generation failed");
        assertThat(model.get("aiErrorMessage")).isEqualTo("Please select at least one source.");
        verify(examSessionService).createSession(
            anyList(), anyList(), any(), any(),
            any(), anyInt(), any(), any(), any()
        );
    }

    @Test
    void createSession_QuotaExceeded_RendersAiGenerationErrorFragment() throws Exception {
        doThrow(new AiQuotaExceededException("Daily AI request limit reached."))
            .when(examSessionService).createSession(
                anyList(), anyList(), any(), any(),
                any(), anyInt(), any(), any(), any()
            );

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
            List.of(10L),
            List.of(),
            null,
            new MockHttpServletRequest(),
            ExamQuestionSize.MEDIUM,
            5,
            null,
            ExamLayout.PER_PAGE,
            model,
            () -> "alice",
            new MockHttpSession(),
            response,
            "true"
        );

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("HX-Trigger")).isNull();
        assertThat(view).isEqualTo("fragments/ai-generation-error :: aiGenerationError");
        assertThat(model.get("aiErrorMessage")).isEqualTo("Daily AI request limit reached.");
        verify(examSessionService).createSession(
            anyList(), anyList(), any(), any(),
            any(), anyInt(), any(), any(), any()
        );
    }

    @Test
    void createSession_ProviderFailureAfterQuotaRefreshesQuota() throws Exception {
        doThrow(new AiGenerationException("provider unavailable",
                AiGenerationDiagnostics.fromException("EXAM", "PROVIDER_REQUEST",
                    new RuntimeException("provider unavailable"))))
            .when(examSessionService).createSession(
                anyList(), anyList(), any(), any(),
                any(), anyInt(), any(), any(), any()
            );

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
            List.of(10L),
            List.of(),
            null,
            new MockHttpServletRequest(),
            ExamQuestionSize.MEDIUM,
            5,
            null,
            ExamLayout.PER_PAGE,
            model,
            () -> "alice",
            new MockHttpSession(),
            response,
            "true"
        );

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("HX-Trigger")).isEqualTo("refresh-quota");
        assertThat(view).isEqualTo("fragments/ai-generation-error :: aiGenerationError");
        assertThat(model.get("aiErrorMessage")).isEqualTo("provider unavailable");
    }

    @Test
    void preflightCreateSession_ValidRequest_DoesNotCallQuotaOrAi() throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.preflightCreateSession(
            List.of(10L),
            List.of(),
            new MockHttpServletRequest(),
            ExamQuestionSize.MEDIUM,
            5,
            null,
            ExamLayout.PER_PAGE,
            model,
            () -> "alice",
            new MockHttpSession(),
            response,
            "true"
        );

        assertThat(view).isNull();
        assertThat(response.getStatus()).isEqualTo(204);
        verify(examSessionService).validateForPreflight(
            anyList(), anyList(), any(),
            any(), anyInt(), any(), any(), any()
        );
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
        verify(aiExamService, never()).generate(anyList(), anyList(), anyInt(), any(), any());
    }

    @Test
    void submit_QuotaExceededOnGrading_ReturnsQuotaErrorMessage() {
        doThrow(new AiQuotaExceededException("Daily AI request limit reached."))
            .when(aiRequestQuotaService).checkAndRecord(user);

        MockHttpSession session = new MockHttpSession();
        ExamSessionState state = new ExamSessionState(
            null,
            List.of(new ExamQuestion("Q", "rubric")),
            new HashMap<>(),
            Instant.now(),
            "source"
        );
        session.setAttribute("examSession", state);

        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = controller.submit(
            null,
            null,
            null,
            new ExtendedModelMap(),
            () -> "alice",
            session,
            response
        );

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body).contains("Daily AI request limit reached.");
        verify(aiRequestQuotaService).checkAndRecord(user);
    }
}
