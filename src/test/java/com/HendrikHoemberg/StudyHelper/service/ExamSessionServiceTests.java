package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.ExamLayout;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestion;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionSize;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamSessionServiceTests {

    private AiExamService aiExamService;
    private DeckService deckService;
    private FlashcardService flashcardService;
    private FileEntryService fileEntryService;
    private DocumentExtractionService documentExtractionService;
    private AiRequestQuotaService aiRequestQuotaService;
    private ExamSessionService service;

    @BeforeEach
    void setUp() {
        aiExamService = mock(AiExamService.class);
        deckService = mock(DeckService.class);
        flashcardService = mock(FlashcardService.class);
        fileEntryService = mock(FileEntryService.class);
        documentExtractionService = mock(DocumentExtractionService.class);
        aiRequestQuotaService = mock(AiRequestQuotaService.class);

        service = new ExamSessionService(
            aiExamService, deckService, flashcardService,
            fileEntryService, documentExtractionService, aiRequestQuotaService
        );
    }

    @Test
    void validateForPreflight_rejectsEmptySelection() {
        HttpServletRequest req = new MockHttpServletRequest();
        assertThatThrownBy(() -> service.validateForPreflight(
                List.of(), List.of(), req,
                ExamQuestionSize.MEDIUM, 5, null, ExamLayout.PER_PAGE,
                new User()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one source");
    }

    @Test
    void validateForPreflight_rejectsNullQuestionSize() {
        HttpServletRequest req = new MockHttpServletRequest();
        assertThatThrownBy(() -> service.validateForPreflight(
                List.of(1L), List.of(), req,
                null, 5, null, ExamLayout.PER_PAGE,
                new User()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateForPreflight_rejectsNullLayout() {
        HttpServletRequest req = new MockHttpServletRequest();
        assertThatThrownBy(() -> service.validateForPreflight(
                List.of(1L), List.of(), req,
                ExamQuestionSize.MEDIUM, 5, null, null,
                new User()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_buildsResultWithLayoutFromConfig() throws Exception {
        User user = new User();
        Deck deck = new Deck();
        deck.setName("D1");

        when(deckService.getValidatedDecksInRequestedOrder(any(), any()))
            .thenReturn(List.of(deck));
        when(flashcardService.getFlashcardsFlattened(any())).thenReturn(Collections.emptyList());

        ExamQuestion q = new ExamQuestion("Q?", "rubric");
        when(aiExamService.generate(any(), any(), anyInt(), any(ExamQuestionSize.class), any()))
            .thenReturn(List.of(q));

        HttpServletRequest req = new MockHttpServletRequest();
        ExamSessionService.ExamSessionResult result = service.createSession(
            List.of(10L), List.of(), "be concise", req,
            ExamQuestionSize.SHORT, 1, 30, ExamLayout.SINGLE_PAGE,
            user
        );

        assertThat(result.state().config().layout()).isEqualTo(ExamLayout.SINGLE_PAGE);
        assertThat(result.state().questions()).containsExactly(q);
        verify(aiRequestQuotaService).checkAndRecord(user);
    }

    @Test
    void createSession_requiresQuotaService() throws Exception {
        ExamSessionService svcNoQuota = new ExamSessionService(
            aiExamService, deckService, flashcardService,
            fileEntryService, documentExtractionService, null
        );
        Deck deck = new Deck();
        deck.setName("D1");
        when(deckService.getValidatedDecksInRequestedOrder(any(), any())).thenReturn(List.of(deck));
        when(flashcardService.getFlashcardsFlattened(any())).thenReturn(Collections.emptyList());
        when(aiExamService.generate(any(), any(), anyInt(), any(ExamQuestionSize.class), any()))
            .thenReturn(List.of(new ExamQuestion("Q?", "r")));

        HttpServletRequest req = new MockHttpServletRequest();
        assertThatThrownBy(() -> svcNoQuota.createSession(
                List.of(10L), List.of(), null, req,
                ExamQuestionSize.MEDIUM, 1, null, ExamLayout.PER_PAGE,
                new User()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("quota");
        verify(aiRequestQuotaService, never()).checkAndRecord(any());
    }

    @Test
    void createSession_runsCallbackAfterQuotaBeforeAiGeneration() throws Exception {
        User user = new User();
        Deck deck = new Deck();
        deck.setName("D1");
        AtomicBoolean callbackRan = new AtomicBoolean(false);

        when(deckService.getValidatedDecksInRequestedOrder(any(), any())).thenReturn(List.of(deck));
        when(flashcardService.getFlashcardsFlattened(any())).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
                assertThat(callbackRan).isTrue();
                throw new RuntimeException("provider unavailable");
            })
            .when(aiExamService).generate(any(), any(), anyInt(), any(ExamQuestionSize.class), any());

        HttpServletRequest req = new MockHttpServletRequest();
        assertThatThrownBy(() -> service.createSession(
                List.of(10L), List.of(), null, req,
                ExamQuestionSize.MEDIUM, 1, null, ExamLayout.PER_PAGE,
                user, () -> callbackRan.set(true)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("provider unavailable");

        assertThat(callbackRan).isTrue();
        verify(aiRequestQuotaService).checkAndRecord(user);
    }

    @Test
    void createSession_doesNotRunCallbackWhenQuotaIsExceeded() throws Exception {
        User user = new User();
        Deck deck = new Deck();
        deck.setName("D1");
        AtomicBoolean callbackRan = new AtomicBoolean(false);

        when(deckService.getValidatedDecksInRequestedOrder(any(), any())).thenReturn(List.of(deck));
        when(flashcardService.getFlashcardsFlattened(any())).thenReturn(Collections.emptyList());
        doThrow(new AiQuotaExceededException("Daily AI request limit reached."))
            .when(aiRequestQuotaService).checkAndRecord(user);

        HttpServletRequest req = new MockHttpServletRequest();
        assertThatThrownBy(() -> service.createSession(
                List.of(10L), List.of(), null, req,
                ExamQuestionSize.MEDIUM, 1, null, ExamLayout.PER_PAGE,
                user, () -> callbackRan.set(true)))
            .isInstanceOf(AiQuotaExceededException.class)
            .hasMessageContaining("Daily AI request limit reached.");

        assertThat(callbackRan).isFalse();
        verify(aiExamService, never()).generate(any(), any(), anyInt(), any(ExamQuestionSize.class), any());
    }
}
