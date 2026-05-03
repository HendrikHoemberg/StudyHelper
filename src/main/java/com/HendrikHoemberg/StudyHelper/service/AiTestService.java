package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.TestQuestion;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiTestService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiTestService(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<TestQuestion> generateQuestions(List<Flashcard> flashcards, int count) {
        String cardContent = buildCardContent(flashcards);
        if (cardContent.isBlank()) {
            throw new IllegalArgumentException("The selected decks have no text content. Add text to your flashcards before generating a test.");
        }

        String response = chatClient.prompt()
            .user(buildPrompt(cardContent, count))
            .call()
            .content();

        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            List<TestQuestion> questions = objectMapper
                .readerForListOf(TestQuestion.class)
                .readValue(root.get("questions"));
            if (questions == null || questions.isEmpty()) {
                throw new IllegalStateException("AI returned no questions.");
            }
            return questions.stream()
                .filter(q -> q.options() != null && q.options().size() == 4
                    && q.correctOptionIndex() >= 0 && q.correctOptionIndex() <= 3)
                .limit(count)
                .toList();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the AI response. Please try again.", e);
        }
    }

    private String buildCardContent(List<Flashcard> flashcards) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Flashcard card : flashcards) {
            String front = card.getFrontText();
            String back = card.getBackText();
            boolean hasFront = front != null && !front.isBlank();
            boolean hasBack = back != null && !back.isBlank();
            if (!hasFront && !hasBack) continue;
            count++;
            sb.append("Card ").append(count).append(":\n");
            if (hasFront) sb.append("  Q: ").append(front.strip()).append("\n");
            if (hasBack) sb.append("  A: ").append(back.strip()).append("\n");
        }
        return sb.toString();
    }

    private String buildPrompt(String cardContent, int count) {
        return """
            You are a study assistant. Based on the flashcard content below, generate exactly %d multiple-choice questions.

            Rules:
            - Each question must have exactly 4 answer options
            - Exactly one option is correct; the others are plausible but wrong
            - correctOptionIndex is 0-based (0 = first option, 3 = last)
            - Vary which index is correct across questions
            - Test understanding, not exact wording

            Flashcard content:
            %s

            Respond ONLY with valid JSON, no extra text:
            {"questions":[{"questionText":"...","options":["...","...","...","..."],"correctOptionIndex":0}]}
            """.formatted(count, cardContent);
    }

    private String extractJson(String response) {
        String s = response.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("```(?:json)?\\s*\n?", "");
            int codeEnd = s.lastIndexOf("```");
            if (codeEnd > 0) s = s.substring(0, codeEnd).trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }
}
