package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.ExamLayout;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionSize;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestion;
import com.HendrikHoemberg.StudyHelper.dto.ExamSessionState;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.AiQuotaExceededException;
import com.HendrikHoemberg.StudyHelper.service.AiRequestQuotaService;
import com.HendrikHoemberg.StudyHelper.service.AiExamService;
import com.HendrikHoemberg.StudyHelper.service.DeckService;
import com.HendrikHoemberg.StudyHelper.service.DocumentExtractionService;
import com.HendrikHoemberg.StudyHelper.service.ExamService;
import com.HendrikHoemberg.StudyHelper.service.FileEntryService;
import com.HendrikHoemberg.StudyHelper.service.FlashcardService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamControllerTests {

    private ExamController controller;
    private UserService userService;
    private AiExamService aiExamService;
    private AiRequestQuotaService aiRequestQuotaService;
    private User user;

    @BeforeEach
    void setUp() {
        aiExamService = mock(AiExamService.class);
        ExamService examService = mock(ExamService.class);
        userService = mock(UserService.class);
        DeckService deckService = mock(DeckService.class);
        FlashcardService flashcardService = mock(FlashcardService.class);
        FileEntryService fileEntryService = mock(FileEntryService.class);
        DocumentExtractionService documentExtractionService = mock(DocumentExtractionService.class);
        aiRequestQuotaService = mock(AiRequestQuotaService.class);
        controller = new ExamController(
            aiExamService,
            examService,
            userService,
            deckService,
            flashcardService,
            fileEntryService,
            documentExtractionService,
            aiRequestQuotaService
        );

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userService.getByUsername("alice")).thenReturn(user);
    }

    @Test
    void createSession_HtmxWithoutSources_ReturnsAiGenerationErrorFragment() {
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
            List.of(),
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

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(view).isEqualTo("fragments/ai-generation-error :: aiGenerationError");
        assertThat(model.get("aiErrorTitle")).isEqualTo("AI generation failed");
        assertThat(model.get("aiErrorMessage")).isEqualTo("Please select at least one source.");
    }

    @Test
    void createSession_QuotaExceeded_RendersAiGenerationErrorFragment() {
        doThrow(new AiQuotaExceededException("Daily AI request limit reached."))
            .when(aiRequestQuotaService).checkAndRecord(user);

        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.createSession(
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

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(view).isEqualTo("fragments/ai-generation-error :: aiGenerationError");
        assertThat(model.get("aiErrorMessage")).isEqualTo("Daily AI request limit reached.");
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
