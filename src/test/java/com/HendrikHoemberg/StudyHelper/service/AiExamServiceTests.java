package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.ExamGradingResult;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestion;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionSize;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionsResponse;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiExamServiceTests {

    private ChatClient.CallResponseSpec callSpec;
    private AiExamService service;
    private final AtomicReference<String> capturedPrompt = new AtomicReference<>();
    private final List<org.springframework.ai.content.Media> capturedMedia = new ArrayList<>();
    private final AtomicReference<org.springframework.ai.chat.prompt.ChatOptions.Builder> capturedOptionsBuilder = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        capturedPrompt.set(null);
        capturedMedia.clear();
        capturedOptionsBuilder.set(null);

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);

        when(requestSpec.options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class)))
            .thenAnswer(invocation -> {
                capturedOptionsBuilder.set(invocation.getArgument(0));
                return requestSpec;
            });

        when(requestSpec.user(any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<ChatClient.PromptUserSpec> consumer = invocation.getArgument(0);
            ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
            when(userSpec.text(anyString())).thenAnswer(a -> {
                capturedPrompt.set(a.getArgument(0));
                return userSpec;
            });
            when(userSpec.media(any(org.springframework.ai.content.Media.class))).thenAnswer(a -> {
                for (Object arg : a.getRawArguments()) {
                    if (arg instanceof org.springframework.ai.content.Media m) {
                        capturedMedia.add(m);
                    } else if (arg instanceof org.springframework.ai.content.Media[] arr) {
                        Collections.addAll(capturedMedia, arr);
                    }
                }
                return userSpec;
            });
            consumer.accept(userSpec);
            return requestSpec;
        });
        when(requestSpec.call()).thenReturn(callSpec);

        service = new AiExamService(builder, new JsonMapper());
    }

    @Test
    void generate_TextDocument_includedInPromptDocumentsSection() {
        when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

        var docs = List.<DocumentInput>of(new TextDocument("lecture.pdf", "photosynthesis is awesome"));
        List<ExamQuestion> result = service.generate(cards(2), docs, 3, ExamQuestionSize.MEDIUM);

        assertThat(result).hasSize(3);
        assertThat(capturedPrompt.get()).contains("=== DOCUMENTS ===");
        assertThat(capturedPrompt.get()).contains("lecture.pdf");
        assertThat(capturedPrompt.get()).contains("photosynthesis is awesome");
        assertThat(capturedMedia).isEmpty();
    }

    @Test
    void generate_PdfDocument_attachedAsMediaAndListedInPrompt() {
        when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

        var resource = new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46});
        var docs = List.<DocumentInput>of(new PdfDocument("chapter5.pdf", resource));
        List<ExamQuestion> result = service.generate(cards(2), docs, 3, ExamQuestionSize.MEDIUM);

        assertThat(result).hasSize(3);
        assertThat(capturedPrompt.get()).contains("=== ATTACHED PDFs ===");
        assertThat(capturedPrompt.get()).contains("chapter5.pdf");
        assertThat(capturedMedia).hasSize(1);
        assertThat(capturedMedia.get(0).getMimeType().toString()).isEqualTo("application/pdf");
    }

    @Test
    void generate_MixedTextAndPdfDocs_bothSectionsPopulated() {
        when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

        var resource = new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46});
        var docs = List.<DocumentInput>of(
            new TextDocument("notes.md", "Mitochondria are the powerhouse"),
            new PdfDocument("biology.pdf", resource)
        );

        List<ExamQuestion> result = service.generate(cards(2), docs, 3, ExamQuestionSize.MEDIUM);

        assertThat(result).hasSize(3);
        assertThat(capturedPrompt.get()).contains("Mitochondria are the powerhouse");
        assertThat(capturedPrompt.get()).contains("biology.pdf");
        assertThat(capturedMedia).hasSize(1);
    }

    @Test
    void generate_PdfOnly_NoFlashcardsNoText_DoesNotThrow() {
        when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

        var resource = new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46});
        var docs = List.<DocumentInput>of(new PdfDocument("only.pdf", resource));

        List<ExamQuestion> result = service.generate(List.of(), docs, 3, ExamQuestionSize.MEDIUM);
        assertThat(result).hasSize(3);
    }

    @Test
    void generate_NullFlashcardEntry_IsIgnored() {
        when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

        List<Flashcard> withNull = new ArrayList<>();
        withNull.add(cards(1).get(0));
        withNull.add(null);

        List<ExamQuestion> result = service.generate(withNull, List.of(), 3, ExamQuestionSize.MEDIUM);

        assertThat(result).hasSize(3);
    }

    @Test
    void generate_NullFlashcardsAndNoDocs_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> service.generate(null, List.of(), 3, ExamQuestionSize.MEDIUM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no usable");
    }

    @Test
    void generate_ProviderFailure_throwsStableRetryMessage() {
        when(callSpec.entity(ExamQuestionsResponse.class)).thenThrow(new RuntimeException("provider offline"));

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 3, ExamQuestionSize.MEDIUM))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI request failed, please retry with fewer or smaller PDFs.")
            .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void generate_EntityFailure_usesStableRetryMessage() {
        when(callSpec.entity(ExamQuestionsResponse.class)).thenThrow(new RuntimeException("parse failed"));

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 3, ExamQuestionSize.MEDIUM))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI request failed, please retry with fewer or smaller PDFs.");
    }

    @Test
    void generate_ConfiguresStructuredOutputOptions() {
        when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

        service.generate(cards(2), List.of(), 3, ExamQuestionSize.MEDIUM);

        assertThat(capturedOptionsBuilder.get()).isNotNull();
        var built = (GoogleGenAiChatOptions) capturedOptionsBuilder.get().build();
        assertThat(built.getResponseMimeType()).isEqualTo("application/json");
        assertThat(built.getResponseSchema()).isNotBlank();
    }

    @Test
    void generate_PromptContainsLanguageAndCoverageBlocks() {
        when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

        service.generate(cards(2), List.of(), 3, ExamQuestionSize.MEDIUM);

        assertThat(capturedPrompt.get()).contains("LANGUAGE:");
        assertThat(capturedPrompt.get()).contains("Detect the dominant natural language");
        assertThat(capturedPrompt.get()).contains("COVERAGE:");
        assertThat(capturedPrompt.get()).contains("early third, middle third");
    }

    @Test
    void grade_ReturnsTypedResult() {
        var perQ = new ExamGradingResult.PerQuestion(90, "Good.");
        var overall = new ExamGradingResult.Overall(
            90,
            List.of("Strong recall"),
            List.of("Minor details"),
            List.of("Cell cycle"),
            List.of("Review notes")
        );
        when(callSpec.entity(ExamGradingResult.class))
            .thenReturn(new ExamGradingResult(List.of(perQ), overall));

        var questions = List.of(new ExamQuestion("Explain mitosis", "Mention phases and checkpoints"));
        var result = service.grade(questions, Map.of(0, "Mitosis has prophase, metaphase, anaphase, telophase."), ExamQuestionSize.MEDIUM);

        assertThat(result.perQuestion()).hasSize(1);
        assertThat(result.perQuestion().get(0).scorePercent()).isEqualTo(90);
        assertThat(result.overall().scorePercent()).isEqualTo(90);
    }

    @Test
    void grade_ProviderFailure_throwsStableRetryMessage() {
        when(callSpec.entity(ExamGradingResult.class)).thenThrow(new RuntimeException("provider offline"));

        var questions = List.of(new ExamQuestion("Q1", "H1"));

        assertThatThrownBy(() -> service.grade(questions, Map.of(0, "A1"), ExamQuestionSize.SHORT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI grading request failed. Please try again.")
            .hasCauseInstanceOf(RuntimeException.class);
    }

    // parse-failure path collapsed into provider-failure under structured output
    @Test
    void grade_ConfiguresStructuredOutputOptions() {
        var perQ = new ExamGradingResult.PerQuestion(80, "OK.");
        var overall = new ExamGradingResult.Overall(80, List.of(), List.of(), List.of(), List.of());
        when(callSpec.entity(ExamGradingResult.class))
            .thenReturn(new ExamGradingResult(List.of(perQ), overall));

        service.grade(List.of(new ExamQuestion("Q1", "H1")), Map.of(0, "A1"), ExamQuestionSize.SHORT);

        assertThat(capturedOptionsBuilder.get()).isNotNull();
        var built = (GoogleGenAiChatOptions) capturedOptionsBuilder.get().build();
        assertThat(built.getResponseMimeType()).isEqualTo("application/json");
        assertThat(built.getResponseSchema()).isNotBlank();
    }

    @Test
    void grade_PromptContainsLanguageBlock() {
        var perQ = new ExamGradingResult.PerQuestion(80, "OK.");
        var overall = new ExamGradingResult.Overall(80, List.of(), List.of(), List.of(), List.of());
        when(callSpec.entity(ExamGradingResult.class))
            .thenReturn(new ExamGradingResult(List.of(perQ), overall));

        service.grade(List.of(new ExamQuestion("Q1", "H1")), Map.of(0, "A1"), ExamQuestionSize.SHORT);

        assertThat(capturedPrompt.get()).contains("LANGUAGE:");
        assertThat(capturedPrompt.get()).contains("language of the questions and user answers");
        assertThat(capturedPrompt.get()).doesNotContain("COVERAGE:");
    }

    @Test
    void grade_NullQuestionsAndAnswers_DoesNotThrow() {
        var perQ = new ExamGradingResult.PerQuestion(80, "OK.");
        var overall = new ExamGradingResult.Overall(80, List.of(), List.of(), List.of(), List.of());
        when(callSpec.entity(ExamGradingResult.class))
            .thenReturn(new ExamGradingResult(List.of(perQ), overall));

        var result = service.grade(null, null, ExamQuestionSize.SHORT);

        assertThat(result.perQuestion()).hasSize(1);
        assertThat(result.overall().scorePercent()).isEqualTo(80);
    }

    private ExamQuestionsResponse wrapQuestions(ExamQuestion... questions) {
        return new ExamQuestionsResponse(List.of(questions));
    }

    private ExamQuestionsResponse threeExamQuestions() {
        return wrapQuestions(
            new ExamQuestion("Q1", "Hint 1"),
            new ExamQuestion("Q2", "Hint 2"),
            new ExamQuestion("Q3", "Hint 3")
        );
    }

    private List<Flashcard> cards(int n) {
        return IntStream.rangeClosed(1, n)
            .mapToObj(i -> {
                Flashcard c = new Flashcard();
                c.setFrontText("Front " + i);
                c.setBackText("Back " + i);
                return c;
            })
            .toList();
    }
}
