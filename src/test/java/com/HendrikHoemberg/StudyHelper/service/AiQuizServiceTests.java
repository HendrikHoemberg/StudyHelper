package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.QuestionType;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiQuizServiceTests {

    private ChatClient.CallResponseSpec callSpec;
    private AiQuizService service;

    @BeforeEach
    void setUp() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
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
            .hasMessageContaining("no usable text");
    }

    @Test
    void generate_ImageOnlyCards_FilteredOut_ThrowsIllegalArgument() {
        Flashcard imageOnly = new Flashcard();
        imageOnly.setFrontText("");
        imageOnly.setBackText("");
        imageOnly.setFrontImageFilename("diagram.png");

        assertThatThrownBy(() -> service.generate(List.of(imageOnly), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no usable text");
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
