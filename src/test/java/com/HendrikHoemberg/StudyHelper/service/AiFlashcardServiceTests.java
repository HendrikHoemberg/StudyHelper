package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.FlashcardsResponse;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiFlashcardServiceTests {

    private ChatClient.CallResponseSpec callSpec;
    private AiFlashcardService service;
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

        service = new AiFlashcardService(builder, new JsonMapper());
    }

    @Test
    void generate_TextDocument_buildsPromptWithoutMedia() {
        when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
            new GeneratedFlashcard("Front 1", "Back 1"),
            new GeneratedFlashcard("Front 2", "Back 2")
        ));

        var doc = new TextDocument("notes.txt", "Photosynthesis converts light energy.");

        List<GeneratedFlashcard> result = service.generate(doc);

        assertThat(result).hasSize(2);
        assertThat(capturedPrompt.get()).contains("=== DOCUMENTS ===");
        assertThat(capturedPrompt.get()).contains("notes.txt");
        assertThat(capturedPrompt.get()).contains("Photosynthesis converts light energy.");
        assertThat(capturedPrompt.get()).contains("Maximum flashcards: 50");
        assertThat(capturedPrompt.get()).containsIgnoringCase("dominant educational content");
        assertThat(capturedPrompt.get()).containsIgnoringCase("ignore metadata");
        assertThat(capturedPrompt.get()).containsIgnoringCase("avoid duplicate cards");
        assertThat(capturedPrompt.get()).containsIgnoringCase("self-contained");
        assertThat(capturedPrompt.get()).contains("LANGUAGE:");
        assertThat(capturedPrompt.get()).contains("COVERAGE:");
        assertThat(capturedMedia).isEmpty();
    }

    @Test
    void generate_PdfDocument_buildsPromptAndAttachesPdfMedia() {
        when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
            new GeneratedFlashcard("Front 1", "Back 1"),
            new GeneratedFlashcard("Front 2", "Back 2")
        ));

        var doc = new PdfDocument("chapter5.pdf", new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46}));

        List<GeneratedFlashcard> result = service.generate(doc);

        assertThat(result).hasSize(2);
        assertThat(capturedPrompt.get()).contains("=== ATTACHED PDFs ===");
        assertThat(capturedPrompt.get()).contains("chapter5.pdf");
        assertThat(capturedPrompt.get()).contains("Maximum flashcards: 50");
        assertThat(capturedMedia).hasSize(1);
        assertThat(capturedMedia.get(0).getMimeType().toString()).isEqualTo("application/pdf");
    }

    @Test
    void generate_BlankCardsAreDroppedAndValuesAreTrimmed() {
        when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
            new GeneratedFlashcard("  Front 1  ", "  Back 1  "),
            new GeneratedFlashcard("   ", "ignored"),
            new GeneratedFlashcard("Front 2", "   ")
        ));

        List<GeneratedFlashcard> result = service.generate(new TextDocument("notes.txt", "topic"));

        assertThat(result).containsExactly(new GeneratedFlashcard("Front 1", "Back 1"));
    }

    @Test
    void generate_AllCardsFilteredOut_ThrowsIllegalState() {
        when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
            new GeneratedFlashcard("   ", "   "),
            new GeneratedFlashcard("", "ignored")
        ));

        assertThatThrownBy(() -> service.generate(new TextDocument("notes.txt", "topic")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI returned no valid flashcards; please retry.");
    }

    @Test
    void generate_MoreThanFiftyCards_CapsAtFifty() {
        when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(manyFlashcards(55)));

        List<GeneratedFlashcard> result = service.generate(new TextDocument("notes.txt", "topic"));

        assertThat(result).hasSize(50);
        assertThat(result.get(0)).isEqualTo(new GeneratedFlashcard("Front 1", "Back 1"));
        assertThat(result.get(49)).isEqualTo(new GeneratedFlashcard("Front 50", "Back 50"));
    }

    @Test
    void generate_ProviderFailure_throwsStableRetryMessage() {
        when(callSpec.entity(FlashcardsResponse.class)).thenThrow(new RuntimeException("provider offline"));

        assertThatThrownBy(() -> service.generate(new TextDocument("notes.txt", "topic")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI request failed, please retry with fewer or smaller PDFs.")
            .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void generate_ParseFailure_throwsStableRetryMessage() {
        when(callSpec.entity(FlashcardsResponse.class)).thenThrow(new RuntimeException("parse failed"));

        assertThatThrownBy(() -> service.generate(new TextDocument("notes.txt", "topic")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI request failed, please retry with fewer or smaller PDFs.");
    }

    @Test
    void generate_NoUsableSources_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.generate(new TextDocument("notes.txt", "   ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no usable");
    }

    @Test
    void generate_ConfiguresStructuredOutputOptions() {
        when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
            new GeneratedFlashcard("Front 1", "Back 1"),
            new GeneratedFlashcard("Front 2", "Back 2")
        ));

        service.generate(new TextDocument("notes.txt", "topic"));

        assertThat(capturedOptionsBuilder.get()).isNotNull();
        var built = (GoogleGenAiChatOptions) capturedOptionsBuilder.get().build();
        assertThat(built.getResponseMimeType()).isEqualTo("application/json");
        assertThat(built.getResponseSchema()).isNotBlank();
    }

    @Test
    void generate_PromptContainsLanguageAndCoverageBlocks() {
        when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
            new GeneratedFlashcard("Front 1", "Back 1"),
            new GeneratedFlashcard("Front 2", "Back 2")
        ));

        service.generate(new TextDocument("notes.txt", "topic"));

        assertThat(capturedPrompt.get()).contains("LANGUAGE:");
        assertThat(capturedPrompt.get()).contains("Detect the dominant natural language");
        assertThat(capturedPrompt.get()).contains("COVERAGE:");
        assertThat(capturedPrompt.get()).contains("across the full source material");
    }

    private FlashcardsResponse wrap(GeneratedFlashcard... cards) {
        return new FlashcardsResponse(List.of(cards));
    }

    private GeneratedFlashcard[] manyFlashcards(int count) {
        GeneratedFlashcard[] cards = new GeneratedFlashcard[count];
        for (int i = 1; i <= count; i++) {
            cards[i - 1] = new GeneratedFlashcard("Front " + i, "Back " + i);
        }
        return cards;
    }
}
