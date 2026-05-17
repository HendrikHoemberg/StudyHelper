package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedQuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.QuestionType;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionsResponse;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.Arrays;
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

        service = new AiQuizService(builder, new JsonMapper());
    }

    @Test
    void generate_McqOnly_ReturnsFiveMcqQuestions() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 1), mcq("Q3", 2), mcq("Q4", 3), mcq("Q5", 0)
        ));

        List<QuizQuestion> result = service.generate(cards(3), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM);

        assertThat(result).hasSize(5);
        assertThat(result).allMatch(q -> q.type() == QuestionType.MULTIPLE_CHOICE);
        assertThat(result).allMatch(q -> q.correctOptionIndices().size() == 1
            && q.correctOptionIndices().get(0) >= 0 && q.correctOptionIndices().get(0) <= 3);
    }

    @Test
    void generate_TfOnly_ReturnsTfQuestionsWithNormalizedOptions() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            tf("Q1", 0), tf("Q2", 1), tf("Q3", 0)
        ));

        List<QuizQuestion> result = service.generate(cards(3), List.of(), 3, QuizQuestionMode.TF_ONLY, Difficulty.EASY);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(q -> q.type() == QuestionType.TRUE_FALSE);
        assertThat(result).allMatch(q -> q.options().equals(List.of("True", "False")));
    }

    @Test
    void generate_Mixed_ReturnsBothTypes() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), tf("Q2", 1), mcq("Q3", 2), tf("Q4", 0)
        ));

        List<QuizQuestion> result = service.generate(cards(3), List.of(), 4, QuizQuestionMode.MIXED, Difficulty.MEDIUM);

        assertThat(result).hasSize(4);
        assertThat(result).anyMatch(q -> q.type() == QuestionType.MULTIPLE_CHOICE);
        assertThat(result).anyMatch(q -> q.type() == QuestionType.TRUE_FALSE);
    }

    @Test
    void generate_Mixed_InterleavesGroupedChoiceAndTrueFalseQuestions() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 1), mcq("Q3", 2),
            tf("Q4", 0), tf("Q5", 1), tf("Q6", 0)
        ));

        List<QuizQuestion> result = service.generate(cards(6), List.of(), 6, QuizQuestionMode.MIXED, Difficulty.MEDIUM);

        assertThat(result).extracting(QuizQuestion::type).containsExactly(
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.TRUE_FALSE,
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.TRUE_FALSE,
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.TRUE_FALSE
        );
    }

    @Test
    void generate_LowercaseTfOptions_NormalizedToTitleCase() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            tfLower("Q1", 0), tfLower("Q2", 1)
        ));

        List<QuizQuestion> result = service.generate(cards(2), List.of(), 2, QuizQuestionMode.TF_ONLY, Difficulty.EASY);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(q -> q.options().equals(List.of("True", "False")));
    }

    @Test
    void generate_MissingTypeField_InferredFromOptionsShape() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcqTypeless("Q1", 0), tfTypeless("Q2", 1)
        ));

        List<QuizQuestion> result = service.generate(cards(2), List.of(), 2, QuizQuestionMode.MIXED, Difficulty.MEDIUM);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(result.get(1).type()).isEqualTo(QuestionType.TRUE_FALSE);
    }

    @Test
    void generate_MalformedEntriesDropped_TooFewThrowsIllegalState() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            gen(QuestionType.MULTIPLE_CHOICE, "Q1", List.of("only", "two"), List.of(0)),
            gen(QuestionType.MULTIPLE_CHOICE, "Q2", List.of("only", "two"), List.of(0))
        ));

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
    void generate_NullFlashcardEntry_IsIgnored() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 1), mcq("Q3", 2)
        ));

        List<Flashcard> withNull = new ArrayList<>();
        withNull.add(cards(1).get(0));
        withNull.add(null);

        List<QuizQuestion> result = service.generate(withNull, List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

        assertThat(result).hasSize(3);
    }

    @Test
    void generate_NullMcqOptionEntry_IsDroppedAndCanTriggerTooFewValid() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            gen(QuestionType.MULTIPLE_CHOICE, "Q1", Arrays.asList("a", null, "c", "d"), List.of(0)),
            gen(QuestionType.MULTIPLE_CHOICE, "Q2", Arrays.asList("a", null, "c", "d"), List.of(0))
        ));

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("too few valid questions");
    }

    @Test
    void generate_NullTfOptionEntry_IsDroppedAndCanTriggerTooFewValid() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            gen(QuestionType.TRUE_FALSE, "Q1", Arrays.asList("True", null), List.of(0)),
            gen(QuestionType.TRUE_FALSE, "Q2", Arrays.asList(null, "False"), List.of(1))
        ));

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 5, QuizQuestionMode.TF_ONLY, Difficulty.EASY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("too few valid questions");
    }

    @Test
    void generate_TextDocument_includedInPromptDocumentsSection() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

        var docs = List.<DocumentInput>of(new TextDocument("lecture.pdf", "photosynthesis is awesome"));
        service.generate(cards(2), docs, 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

        assertThat(capturedPrompt.get()).contains("=== DOCUMENTS ===");
        assertThat(capturedPrompt.get()).contains("lecture.pdf");
        assertThat(capturedPrompt.get()).contains("photosynthesis is awesome");
        assertThat(capturedMedia).isEmpty();
    }

    @Test
    void generate_PdfDocument_attachedAsMediaAndListedInPrompt() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

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
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

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
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

        var resource = new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46});
        var docs = List.<DocumentInput>of(new PdfDocument("only.pdf", resource));

        List<QuizQuestion> result = service.generate(List.of(), docs, 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);
        assertThat(result).hasSize(3);
    }

    @Test
    void generate_ProviderFailure_throwsStableRetryMessage() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenThrow(new RuntimeException("provider offline"));

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI request failed, please retry with fewer or smaller PDFs.")
            .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void generate_ConfiguresStructuredOutputOptions() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

        service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

        assertThat(capturedOptionsBuilder.get()).isNotNull();
        var built = (GoogleGenAiChatOptions) capturedOptionsBuilder.get().build();
        assertThat(built.getResponseMimeType()).isEqualTo("application/json");
        assertThat(built.getResponseSchema()).isNotBlank();
    }

    @Test
    void generate_ResponseSchema_DeclaresPropertyOrderingForChainOfThought() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

        service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

        String schema = ((GoogleGenAiChatOptions) capturedOptionsBuilder.get().build()).getResponseSchema();
        int ordering = schema.indexOf("propertyOrdering");
        assertThat(ordering).isGreaterThan(-1);

        String orderingTail = schema.substring(ordering);
        assertThat(orderingTail.indexOf("optionAnalysis"))
            .isLessThan(orderingTail.indexOf("correctOptionIndices"));
        assertThat(orderingTail.indexOf("options"))
            .isLessThan(orderingTail.indexOf("optionAnalysis"));
    }

    @Test
    void generate_PromptContainsLanguageAndCoverageBlocks() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

        service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

        assertThat(capturedPrompt.get()).contains("LANGUAGE:");
        assertThat(capturedPrompt.get()).contains("Detect the dominant natural language");
        assertThat(capturedPrompt.get()).contains("COVERAGE:");
        assertThat(capturedPrompt.get()).contains("early third, middle third");
    }

    @Test
    void generate_WithAdditionalInstructions_AppendsUserInstructionsSection() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

        service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY, "focus on diagrams");

        assertThat(capturedPrompt.get()).contains("USER INSTRUCTIONS:");
        assertThat(capturedPrompt.get()).contains("focus on diagrams");
    }

    @Test
    void generate_BlankAdditionalInstructions_OmitsUserInstructionsSection() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

        service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY, "");

        assertThat(capturedPrompt.get()).doesNotContain("USER INSTRUCTIONS:");
    }

    @Test
    void generate_MultiSelect_TwoOrThreeCorrect_Accepted() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            multiSelect("Q1", 0, 2), multiSelect("Q2", 1, 2, 3), mcq("Q3", 0)
        ));

        List<QuizQuestion> result = service.generate(cards(3), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).type()).isEqualTo(QuestionType.MULTIPLE_SELECT);
        assertThat(result.get(0).correctOptionIndices()).containsExactly(0, 2);
        assertThat(result.get(1).correctOptionIndices()).containsExactly(1, 2, 3);
    }

    @Test
    void generate_MultiSelect_OneCorrect_IsRejected() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            gen(QuestionType.MULTIPLE_SELECT, "Q1", List.of("a", "b", "c", "d"), List.of(0)),
            gen(QuestionType.MULTIPLE_SELECT, "Q2", List.of("a", "b", "c", "d"), List.of(1))
        ));

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("too few valid questions");
    }

    @Test
    void generate_MultiSelect_AllFourCorrect_IsRejected() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            gen(QuestionType.MULTIPLE_SELECT, "Q1", List.of("a", "b", "c", "d"), List.of(0, 1, 2, 3)),
            gen(QuestionType.MULTIPLE_SELECT, "Q2", List.of("a", "b", "c", "d"), List.of(0, 1, 2, 3))
        ));

        assertThatThrownBy(() -> service.generate(cards(2), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("too few valid questions");
    }

    @Test
    void generate_McqMode_PromptContainsTypeSelectionGuidance() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

        service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM);

        assertThat(capturedPrompt.get()).contains("QUESTION-TYPE SELECTION");
        assertThat(capturedPrompt.get()).contains("NEVER collapse a multi-answer question");
        assertThat(capturedPrompt.get()).contains("MULTIPLE_SELECT");
    }

    @Test
    void generate_TfOnlyMode_OmitsTypeSelectionGuidance() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            tf("Q1", 0), tf("Q2", 1), tf("Q3", 0)
        ));

        service.generate(cards(2), List.of(), 3, QuizQuestionMode.TF_ONLY, Difficulty.EASY);

        assertThat(capturedPrompt.get()).doesNotContain("QUESTION-TYPE SELECTION");
    }

    @Test
    void generate_PromptContainsPerOptionAnalysisGuidance() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
        ));

        service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM);

        assertThat(capturedPrompt.get()).contains("PER-OPTION ANALYSIS");
        assertThat(capturedPrompt.get()).contains("fill optionAnalysis before choosing type");
    }

    @Test
    void generate_TfOnlyMode_StillContainsPerOptionAnalysisGuidance() {
        when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
            tf("Q1", 0), tf("Q2", 1), tf("Q3", 0)
        ));

        service.generate(cards(2), List.of(), 3, QuizQuestionMode.TF_ONLY, Difficulty.EASY);

        assertThat(capturedPrompt.get()).contains("PER-OPTION ANALYSIS");
    }

    // --- helpers ---

    private QuizQuestionsResponse wrap(GeneratedQuizQuestion... questions) {
        return new QuizQuestionsResponse(List.of(questions));
    }

    private GeneratedQuizQuestion gen(QuestionType type, String text,
                                      List<String> options, List<Integer> correctIndices) {
        return new GeneratedQuizQuestion(text, options, List.of(), type, correctIndices);
    }

    private GeneratedQuizQuestion mcq(String text, int correctIdx) {
        return new GeneratedQuizQuestion(text, List.of("a", "b", "c", "d"),
            List.of(), QuestionType.MULTIPLE_CHOICE, List.of(correctIdx));
    }

    private GeneratedQuizQuestion multiSelect(String text, Integer... correctIdx) {
        return new GeneratedQuizQuestion(text, List.of("a", "b", "c", "d"),
            List.of(), QuestionType.MULTIPLE_SELECT, List.of(correctIdx));
    }

    private GeneratedQuizQuestion tf(String text, int correctIdx) {
        return new GeneratedQuizQuestion(text, List.of("True", "False"),
            List.of(), QuestionType.TRUE_FALSE, List.of(correctIdx));
    }

    private GeneratedQuizQuestion tfLower(String text, int correctIdx) {
        return new GeneratedQuizQuestion(text, List.of("true", "false"),
            List.of(), QuestionType.TRUE_FALSE, List.of(correctIdx));
    }

    private GeneratedQuizQuestion mcqTypeless(String text, int correctIdx) {
        return new GeneratedQuizQuestion(text, List.of("a", "b", "c", "d"),
            List.of(), null, List.of(correctIdx));
    }

    private GeneratedQuizQuestion tfTypeless(String text, int correctIdx) {
        return new GeneratedQuizQuestion(text, List.of("True", "False"),
            List.of(), null, List.of(correctIdx));
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
