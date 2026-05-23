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
                    .thinkingLevel(GoogleGenAiThinkingLevel.MEDIUM)
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
            Integer importance = c.importance();
            if (importance != null && (importance < 1 || importance > 3)) importance = null;
            cleaned.add(new Concept(id, topic, essence, importance, location));
        }
        return new ConceptOutline(cleaned);
    }

    private String buildPrompt(String docContent, String pdfListing, String additionalInstructions) {
        String docSection = docContent.isBlank() ? "(none)" : docContent;
        String pdfSection = pdfListing.isBlank() ? "(none)" : pdfListing;

        return """
            You are a study analyst preparing material for flashcard creation.
            Your sole job in this step is to enumerate every distinct,
            exam-worthy concept the source material teaches. A downstream step
            will turn each concept into a single flashcard, so the number and
            quality of concepts you list directly determines the final deck.

            EXTRACTION RULES:
            - Be EXHAUSTIVE. Err on the side of including a borderline concept
              rather than omitting one. Under-extraction is a worse failure
              than over-extraction.
            - One concept = one atomic, testable idea. Split compound ideas
              into separate concepts. Do NOT bundle multiple facts into one
              entry.
            - Avoid near-duplicates: if two candidate concepts would produce
              flashcards with the same answer, merge them.
            - Ignore metadata, page numbers, headers, footers, tables of
              contents, bibliographies, and incidental asides. Do not list
              authors, dates, or publication details as concepts unless they
              are themselves the subject matter.
            - Cover the ENTIRE document. Sample evenly from the early third,
              middle third, and final third (by page count for PDFs, by
              length for text). Long documents commonly contain important
              material in the middle that is easy to under-attend to —
              counteract that bias explicitly.
            - If multiple sources are supplied, treat each as a separate
              source and ensure every source contributes concepts.

            LANGUAGE:
            Detect the dominant natural language of the supplied source
            material. Write every `topic`, `essence`, and `location` value in
            that same language. If sources mix languages, use the most
            prevalent one. Do not translate technical terms, proper nouns, or
            code.

            FIELD RULES (for each concept):
            - id:         A stable short slug (lowercase, hyphenated, unique
                          within this output). Used only to reference the
                          concept internally; does not appear in the final
                          flashcard.
            - topic:      5-10 word label naming the concept.
            - essence:    1-2 sentence factual core. Must be rich enough on
                          its own to support one focused question and a
                          concise correct answer. If you cannot write a rich
                          essence for a candidate, the concept is too thin —
                          skip it.
            - importance: 1 = core (central to the subject matter),
                          2 = supporting (useful but not essential),
                          3 = peripheral (worth knowing if room allows).
            - location:   Short hint of where the concept appears, e.g.
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
