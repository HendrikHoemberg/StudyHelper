package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Concept;
import com.HendrikHoemberg.StudyHelper.dto.ConceptOutline;
import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 1 of the two-stage flashcard pipeline: read the source PDFs/text and
 * enumerate every exam-worthy concept they teach, with a brief factual essence
 * for each. Stage 2 turns the resulting outline into flashcards without
 * re-reading the source material.
 */
@Service
public class ConceptExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ConceptExtractionService.class);
    private static final int MAX_OUTPUT_TOKENS = 65_536;

    private final ChatClient chatClient;
    private final String responseSchema;

    public ConceptExtractionService(ChatClient.Builder builder, JsonMapper objectMapper) {
        this.chatClient = builder.build();
        this.responseSchema = new BeanOutputConverter<>(ConceptOutline.class, objectMapper).getJsonSchema();
    }

    public ConceptOutline extract(List<DocumentInput> documents, String additionalInstructions) {
        String docContent = AiGenerationSupport.textDocuments(documents);
        String pdfListing = AiGenerationSupport.pdfListing(documents);
        Media[] pdfMedia = AiGenerationSupport.pdfMedia(documents);

        String prompt = buildPrompt(docContent, pdfListing, additionalInstructions);

        ConceptOutline response;
        try {
            response = chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder()
                    .responseMimeType("application/json")
                    .responseSchema(responseSchema)
                    .thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS))
                .user(u -> {
                    u.text(prompt);
                    if (pdfMedia.length > 0) u.media(pdfMedia);
                })
                .call()
                .entity(ConceptOutline.class);
        } catch (Exception e) {
            throw AiGenerationSupport.failure(log, "FLASHCARDS", "EXTRACTION_PROVIDER_REQUEST",
                "AI request failed, please retry with fewer or smaller PDFs.", e);
        }

        List<Concept> raw = response == null || response.concepts() == null ? List.of() : response.concepts();
        List<Concept> cleaned = new ArrayList<>(raw.size());
        for (Concept c : raw) {
            if (c == null) continue;
            String topic = c.topic() == null ? null : c.topic().strip();
            String essence = c.essence() == null ? null : c.essence().strip();
            if (topic == null || topic.isBlank() || essence == null || essence.isBlank()) continue;
            String id = c.id() == null || c.id().isBlank() ? "c" + (cleaned.size() + 1) : c.id().strip();
            String location = c.location() == null ? "" : c.location().strip();
            cleaned.add(new Concept(id, topic, essence, location));
        }
        return new ConceptOutline(cleaned);
    }

    private String buildPrompt(String docContent, String pdfListing, String additionalInstructions) {
        String docSection = docContent.isBlank() ? "(none)" : docContent;
        String pdfSection = pdfListing.isBlank() ? "(none)" : pdfListing;

        return """
            You are a study analyst preparing material for flashcard creation.
            Your sole job in this step is to enumerate every distinct,
            exam-worthy concept the source material teaches. A downstream
            step will turn each concept into exactly one flashcard, so the
            number of concepts you list IS the size of the final deck.

            DENSITY EXPECTATION:
            Educational PDFs typically support roughly 2-5 concepts per
            content page (lecture slides commonly 1-3 per slide). A 20-page
            chapter should normally yield 40-80+ concepts. If your draft is
            far below that range for the material at hand, you are missing
            content — re-read and add more.

            EXTRACTION RULES:
            - Be EXHAUSTIVE. Include every distinct testable idea, including
              borderline ones. Under-extraction is the primary failure
              mode of this step; over-extraction is acceptable.
            - One concept = one atomic, testable idea. Split compound ideas
              into separate concepts. Definitions, properties, formulas,
              causes, effects, examples, comparisons, edge cases, and named
              entities are each their own concept.
            - Include concepts from every part of the document — beginning,
              middle, AND end. Long documents commonly contain important
              material in the middle third that is easy to under-attend to;
              counteract that bias explicitly.
            - Do NOT merge or de-duplicate aggressively. Only collapse
              concepts that would produce literally identical flashcards.
              Two concepts that share a topic but target different facts
              remain separate entries.
            - Do NOT skip a concept because its essence feels short or
              simple. Short essences become short cards; that is fine.
              The downstream writer step will produce one card per concept
              regardless.
            - Ignore metadata, page numbers, headers, footers, tables of
              contents, bibliographies, and incidental asides. Do not list
              authors, dates, or publication details as concepts unless they
              are themselves the subject matter.
            - If multiple sources are supplied, treat each as a separate
              source and ensure every source contributes concepts.

            LANGUAGE:
            Detect the dominant natural language of the supplied source
            material. Write every `topic`, `essence`, and `location` value
            in that same language. If sources mix languages, use the most
            prevalent one. Do not translate technical terms, proper nouns,
            or code.

            FIELD RULES (for each concept):
            - id:       A stable short slug (lowercase, hyphenated, unique
                        within this output). Used only to reference the
                        concept internally; does not appear in the final
                        flashcard.
            - topic:    5-10 word label naming the concept.
            - essence:  1-2 sentence factual core. Can be short for a
                        simple fact; do not pad. Must contain the answer
                        the eventual card will ask about.
            - location: Short hint of where the concept appears, e.g.
                        "Section 2.3" or "page 14". Free text; may be
                        empty if unclear.

            === DOCUMENTS ===
            %s

            === ATTACHED PDFs ===
            %s
            """.formatted(docSection, pdfSection)
            + AiInstructionSupport.section(additionalInstructions);
    }
}
