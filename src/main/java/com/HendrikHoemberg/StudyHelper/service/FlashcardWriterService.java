package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Concept;
import com.HendrikHoemberg.StudyHelper.dto.ConceptOutline;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardsResponse;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 2 of the two-stage flashcard pipeline: turn the concept outline from
 * {@link ConceptExtractionService} into a flashcard per concept. This stage
 * does not see the original PDFs — the outline already contains every fact it
 * needs — so it runs on minimal thinking and small input.
 */
@Service
public class FlashcardWriterService {

    private static final Logger log = LoggerFactory.getLogger(FlashcardWriterService.class);
    private static final int MAX_OUTPUT_TOKENS = 65_536;

    private final ChatClient chatClient;
    private final String responseSchema;
    private final JsonMapper objectMapper;

    public FlashcardWriterService(ChatClient.Builder builder, JsonMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.responseSchema = new BeanOutputConverter<>(FlashcardsResponse.class, objectMapper).getJsonSchema();
    }

    public List<GeneratedFlashcard> write(ConceptOutline outline, String additionalInstructions) {
        if (outline == null || outline.concepts() == null || outline.concepts().isEmpty()) {
            return List.of();
        }

        String prompt = buildPrompt(outline, additionalInstructions);

        FlashcardsResponse response;
        try {
            response = chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder()
                    .responseMimeType("application/json")
                    .responseSchema(responseSchema)
                    .thinkingLevel(GoogleGenAiThinkingLevel.MINIMAL)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS))
                .user(u -> u.text(prompt))
                .call()
                .entity(FlashcardsResponse.class);
        } catch (Exception e) {
            throw AiGenerationSupport.failure(log, "FLASHCARDS", "WRITER_PROVIDER_REQUEST",
                "AI request failed, please retry with fewer or smaller PDFs.", e);
        }

        List<GeneratedFlashcard> raw = response == null || response.flashcards() == null
            ? List.of()
            : response.flashcards();
        List<GeneratedFlashcard> valid = new ArrayList<>(raw.size());
        for (GeneratedFlashcard card : raw) {
            if (card == null) continue;
            String front = card.frontText() == null ? null : card.frontText().trim();
            String back = card.backText() == null ? null : card.backText().trim();
            if (front == null || front.isBlank() || back == null || back.isBlank()) continue;
            valid.add(new GeneratedFlashcard(front, back));
        }
        return valid;
    }

    private String buildPrompt(ConceptOutline outline, String additionalInstructions) {
        String outlineJson;
        try {
            outlineJson = objectMapper.writeValueAsString(outline);
        } catch (RuntimeException e) {
            outlineJson = fallbackOutlineRendering(outline);
        }

        return """
            You are a flashcard writer. The CONCEPTS array below was produced
            by an upstream extraction step that already read the source
            material. You do NOT have the source material yourself — every
            fact you need is in the `essence` field of each concept.

            WRITING RULES:
            - Produce EXACTLY one flashcard per concept, in the same order
              the concepts appear.
            - Front: a focused, self-contained question that elicits the
              concept's `essence`. Do not reference "the text", "the
              document", or "the source".
            - Back: a concise answer drawn from `essence`. Be complete but
              not verbose.
            - Use the same natural language as the `essence` field of each
              concept (do not translate).
            - If a concept's essence is genuinely too thin to support a
              high-value card, you may omit that card. Do not invent facts
              to pad a thin essence.
            - Do not duplicate question/answer pairs across cards.

            CONCEPTS (JSON):
            %s
            """.formatted(outlineJson)
            + AiInstructionSupport.section(additionalInstructions);
    }

    private String fallbackOutlineRendering(ConceptOutline outline) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        int i = 0;
        for (Concept c : outline.concepts()) {
            if (i++ > 0) sb.append(",\n");
            sb.append("  { \"id\": \"").append(c.id()).append("\",")
              .append(" \"topic\": ").append(jsonString(c.topic())).append(",")
              .append(" \"essence\": ").append(jsonString(c.essence())).append(" }");
        }
        sb.append("\n]");
        return sb.toString();
    }

    private static String jsonString(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
    }
}
