package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.QuestionType;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiQuizServiceTests {

    private ChatClient.CallResponseSpec callSpec;
    private AiQuizService service;
    private final AtomicReference<String> capturedPrompt = new AtomicReference<>();
    private final List<org.springframework.ai.content.Media> capturedMedia = new ArrayList<>();

    @BeforeEach
    void setUp() {
        capturedPrompt.set(null);
        capturedMedia.clear();

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);

        when(requestSpec.user(any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<ChatClient.PromptUserSpec> consumer = invocation.getArgument(0);
            ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
            when(userSpec.text(anyString())).thenAnswer(a -> {
                capturedPrompt.set(a.getArgument(0));
                return userSpec;
            });
            when(userSpec.media(any(org.springframework.ai.content.Media.class))).thenAnswer(a -> {
                // varargs: Mockito captures each element; collect all raw args
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

        service = new AiQuizService(builder, new JsonMapper());
    }

    @Test
    void generate_McqOnly_ReturnsFiveMcqQuestions() {
        when(callSpec.content()).thenReturn("""
            {"questions":[
              {"type":"MULTIPLE_CHOICE","questionText":"Q1","options":["a","b","c","d"],"correctOptionIndex":0},
              {"type":"MULTIPLE_CHOICE","questionText":"Q2","options":["a","b","c","d"],"correctOptionIndex":1},
              {"type":"MULTIPLE_CHOICE","questionText":"Q3","options":["a","b","c","d"],"correctOptionIndex":2},
              {"type":"MULTIPLE_CHOICE","questionText":"Q4","options":["a","b","c","d"],"correctOptionIndex":3},
              {"type":"MULTIPLE_CHOICE","questionText":"Q5","options":["a","b","c","d"],"correctOptionIndex":0}
            ]}""");

        List<QuizQuestion> result = service.generate(cards(3), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM);

        assertThat(result).hasSize(5);
        assertThat(result).allMatch(q -> q.type() == QuestionType.MULTIPLE_CHOICE);
        assertThat(result).allMatch(q -> q.correctOptionIndex() >= 0 && q.correctOptionIndex() <= 3);
    }

    @Test
    void generate_TfOnly_ReturnsTfQuestionsWithNormalizedOptions() {
        when(callSpec.content()).thenReturn("""
            {"questions":[
              {"type":"TRUE_FALSE","questionText":"Q1","options":["True","False"],"correctOptionIndex":0},
              {"type":"TRUE_FALSE","questionText":"Q2","options":["True","False"],"correctOptionIndex":1},
              {"type":"TRUE_FALSE","questionText":"Q3","options":["True","False"],"correctOptionIndex":0}
            ]}""");

        List<QuizQuestion> result = service.generate(cards(3), List.of(), 3, QuizQuestionMode.TF_ONLY, Difficulty.EASY);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(q -> q.type() == QuestionType.TRUE_FALSE);
        assertThat(result).allMatch(q -> q.options().equals(List.of("True", "False")));
    }

    @Test
    void generate_Mixed_ReturnsBothTypes() {
        when(callSpec.content()).thenReturn("""
            {"questions":[
              {"type":"MULTIPLE_CHOICE","questionText":"Q1","options":["a","b","c","d"],"correctOptionIndex":0},
              {"type":"TRUE_FALSE","questionText":"Q2","options":["True","False"],"correctOptionIndex":1},
              {"type":"MULTIPLE_CHOICE","questionText":"Q3","options":["a","b","c","d"],"correctOptionIndex":2},
              {"type":"TRUE_FALSE","questionText":"Q4","options":["True","False"],"correctOptionIndex":0}
            ]}""");

        List<QuizQuestion> result = service.generate(cards(3), List.of(), 4, QuizQuestionMode.MIXED, Difficulty.MEDIUM);

        assertThat(result).hasSize(4);
        assertThat(result).anyMatch(q -> q.type() == QuestionType.MULTIPLE_CHOICE);
        assertThat(result).anyMatch(q -> q.type() == QuestionType.TRUE_FALSE);
    }

    @Test
    void generate_LowercaseTfOptions_NormalizedToTitleCase() {
        when(callSpec.content()).thenReturn("""
            {"questions":[
              {"type":"TRUE_FALSE","questionText":"Q1","options":["true","false"],"correctOptionIndex":0},
              {"type":"TRUE_FALSE","questionText":"Q2","options":["true","false"],"correctOptionIndex":1}
            ]}""");

        List<QuizQuestion> result = service.generate(cards(2), List.of(), 2, QuizQuestionMode.TF_ONLY, Difficulty.EASY);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(q -> q.options().equals(List.of("True", "False")));
    }

    @Test
    void generate_MissingTypeField_InferredFromOptionsShape() {
        when(callSpec.content()).thenReturn("""
            {"questions":[
              {"questionText":"Q1","options":["a","b","c","d"],"correctOptionIndex":0},
              {"questionText":"Q2","options":["True","False"],"correctOptionIndex":1}
            ]}""");

        List<QuizQuestion> result = service.generate(cards(2), List.of(), 2, QuizQuestionMode.MIXED, Difficulty.MEDIUM);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(result.get(1).type()).isEqualTo(QuestionType.TRUE_FALSE);
    }

    @Test
    void generate_MalformedEntriesDropped_TooFewThrowsIllegalState() {
        when(callSpec.content()).thenReturn("""
            {"questions":[
              {"type":"MULTIPLE_CHOICE","questionText":"Q1","options":["only","two"],"correctOptionIndex":0},
              {"type":"MULTIPLE_CHOICE","questionText":"Q2","options":["only","two"],"correctOptionIndex":0}
            ]}""");

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("too few valid questions");
    }

    @Test
    void generate_EmptyFlashcardsAndDocs_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> service.generate(List.of(), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no usable");
    }

    @Test
    void generate_ImageOnlyCards_FilteredOut_ThrowsIllegalArgument() {
        Flashcard imageOnly = new Flashcard();
        imageOnly.setFrontText("");
        imageOnly.setBackText("");
        imageOnly.setFrontImageFilename("diagram.png");

        assertThatThrownBy(() -> service.generate(List.of(imageOnly), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no usable");
    }

    @Test
    void generate_TextDocument_includedInPromptDocumentsSection() {
        when(callSpec.content()).thenReturn(threeQuestions());

        var docs = List.<DocumentInput>of(new TextDocument("lecture.pdf", "photosynthesis is awesome"));
        service.generate(cards(2), docs, 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

        assertThat(capturedPrompt.get()).contains("=== DOCUMENTS ===");
        assertThat(capturedPrompt.get()).contains("lecture.pdf");
        assertThat(capturedPrompt.get()).contains("photosynthesis is awesome");
        assertThat(capturedMedia).isEmpty();
    }

    @Test
    void generate_PdfDocument_attachedAsMediaAndListedInPrompt() {
        when(callSpec.content()).thenReturn(threeQuestions());

        var resource = new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46});
        var docs = List.<DocumentInput>of(new PdfDocument("chapter5.pdf", resource));
        service.generate(cards(2), docs, 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

        assertThat(capturedPrompt.get()).contains("=== ATTACHED PDFs ===");
        assertThat(capturedPrompt.get()).contains("chapter5.pdf");
        assertThat(capturedMedia).hasSize(1);
        assertThat(capturedMedia.get(0).getMimeType().toString()).isEqualTo("application/pdf");
    }

    @Test
    void generate_MixedTextAndPdfDocs_bothSectionsPopulated() {
        when(callSpec.content()).thenReturn(threeQuestions());

        var resource = new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46});
        var docs = List.<DocumentInput>of(
            new TextDocument("notes.md", "Mitochondria are the powerhouse"),
            new PdfDocument("biology.pdf", resource)
        );
        service.generate(cards(2), docs, 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

        assertThat(capturedPrompt.get()).contains("Mitochondria are the powerhouse");
        assertThat(capturedPrompt.get()).contains("biology.pdf");
        assertThat(capturedMedia).hasSize(1);
    }

    @Test
    void generate_PdfOnly_NoFlashcardsNoText_DoesNotThrow() {
        when(callSpec.content()).thenReturn(threeQuestions());

        var resource = new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46});
        var docs = List.<DocumentInput>of(new PdfDocument("only.pdf", resource));

        List<QuizQuestion> result = service.generate(List.of(), docs, 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);
        assertThat(result).hasSize(3);
    }

    @Test
    void generate_ProviderFailure_throwsStableRetryMessage() {
        when(callSpec.content()).thenThrow(new RuntimeException("provider offline"));

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI request failed, please retry with fewer or smaller PDFs.")
            .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void generate_ParseFailure_stillUsesParseSpecificMessage() {
        when(callSpec.content()).thenReturn("this is not json");

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Could not parse the AI response. Please try again.");
    }

    private String threeQuestions() {
        return """
            {"questions":[
              {"type":"MULTIPLE_CHOICE","questionText":"Q1","options":["a","b","c","d"],"correctOptionIndex":0},
              {"type":"MULTIPLE_CHOICE","questionText":"Q2","options":["a","b","c","d"],"correctOptionIndex":0},
              {"type":"MULTIPLE_CHOICE","questionText":"Q3","options":["a","b","c","d"],"correctOptionIndex":0}
            ]}""";
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
