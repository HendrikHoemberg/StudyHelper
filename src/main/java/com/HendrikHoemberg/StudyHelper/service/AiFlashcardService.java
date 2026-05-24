package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardChunk;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardsResponse;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.util.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

@Service
public class AiFlashcardService {

    private static final Logger log = LoggerFactory.getLogger(AiFlashcardService.class);
    private static final int MAX_FLASHCARDS = 100;
    private static final int DEFAULT_FLASHCARD_COUNT = 20;

    private final ChatClient chatClient;
    private final String responseSchema;
    private final AiThrottlingService throttlingService;

    public AiFlashcardService(ChatClient.Builder builder, JsonMapper objectMapper, AiThrottlingService throttlingService) {
        this.chatClient = builder.build();
        this.responseSchema = new BeanOutputConverter<>(FlashcardsResponse.class, objectMapper).getJsonSchema();
        this.throttlingService = throttlingService;
    }

    public List<GeneratedFlashcard> generate(DocumentInput document) {
        return generate(document, DEFAULT_FLASHCARD_COUNT, null);
    }

    public List<GeneratedFlashcard> generate(DocumentInput document, String additionalInstructions) {
        return generate(document, DEFAULT_FLASHCARD_COUNT, additionalInstructions);
    }

    public List<GeneratedFlashcard> generate(DocumentInput document, int cardCount, String additionalInstructions) {
        if (document == null) {
            throw new IllegalArgumentException("Flashcard generation requires at least one PDF input.");
        }
        return generate(List.of(document), cardCount, additionalInstructions);
    }

    public List<GeneratedFlashcard> generate(List<DocumentInput> documents, int cardCount, String additionalInstructions) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Flashcard generation requires at least one PDF input.");
        }

        List<DocumentInput> usableDocuments = documents.stream()
            .filter(document -> document != null)
            .toList();
        if (usableDocuments.isEmpty()) {
            throw new IllegalArgumentException("Flashcard generation requires at least one PDF input.");
        }

        String docContent = AiGenerationSupport.textDocuments(usableDocuments);
        String pdfListing = AiGenerationSupport.pdfListing(usableDocuments);
        Media[] pdfMedia = AiGenerationSupport.pdfMedia(usableDocuments);

        if (docContent.isBlank() && pdfMedia.length == 0) {
            throw new IllegalArgumentException(
                    "Selected sources contain no usable text or PDFs. Pick a document with extractable content, or a PDF in full-document mode.");
        }

        int normalizedCount = normalizeCardCount(cardCount);
        String prompt = buildPrompt(docContent, pdfListing, normalizedCount, additionalInstructions);

        FlashcardsResponse response;
        try {
            throttlingService.throttle();
            response = chatClient.prompt()
                    .options(GoogleGenAiChatOptions.builder()
                            .responseMimeType("application/json")
                            .responseSchema(responseSchema))
                    .user(u -> {
                        u.text(prompt);
                        if (pdfMedia.length > 0)
                            u.media(pdfMedia);
                    })
                    .call()
                    .entity(FlashcardsResponse.class);
        } catch (Exception e) {
            throw AiGenerationSupport.failure(log, "FLASHCARDS", "PROVIDER_REQUEST",
                "AI request failed, please retry with fewer or smaller PDFs.", e);
        }

        try {
            List<GeneratedFlashcard> valid = validCards(response);
            return valid.stream().limit(normalizedCount).toList();

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw AiGenerationSupport.failure(log, "FLASHCARDS", "RESPONSE_PARSE",
                "Could not parse the AI response. Please try again.", e);
        }
    }

    private static int normalizeCardCount(int count) {
        return Math.max(1, Math.min(count, MAX_FLASHCARDS));
    }

    private String buildPrompt(String docContent, String pdfListing, int cardCount, String additionalInstructions) {
        String docSection = docContent.isBlank() ? "(none)" : docContent;
        String pdfSection = pdfListing.isBlank() ? "(none)" : pdfListing;

        return ("You are a study assistant. Generate flashcards based on the source material below.\n\n"
                + "TARGET COUNT:\n"
                + "Generate exactly %d flashcards. If the source material genuinely cannot\n"
                + "support %d distinct, non-duplicate, high-value cards, generate as many\n"
                + "high-quality cards as the material supports rather than padding with\n"
                + "low-value or duplicate cards.\n\n"
                + "LANGUAGE:\n"
                + "Detect the dominant natural language of the supplied source material.\n"
                + "Write every flashcard front and back in that same language. If sources mix\n"
                + "languages, use the most-prevalent one. Do not translate proper nouns, code,\n"
                + "or fixed technical terms.\n\n"
                + "COVERAGE:\n"
                + "Draw cards from across the full source material, not just the opening pages\n"
                + "or first sections. Spread coverage across early, middle, and late portions\n"
                + "of each source whenever content allows.\n\n"
                + "GENERAL RULES:\n"
                + "- First identify the dominant educational content of the source material.\n"
                + "- Ignore metadata, headers, footers, page numbers, and incidental details.\n"
                + "- Create concise front and back text for each flashcard.\n"
                + "- Keep each card self-contained and avoid duplicate cards.\n"
                + "- Use the attached PDF documents as primary source material when present.\n\n"
                + "=== DOCUMENTS ===\n"
                + "%s\n\n"
                + "=== ATTACHED PDFs ===\n"
                + "%s\n").formatted(cardCount, cardCount, docSection, pdfSection)
                + AiInstructionSupport.section(additionalInstructions);
    }

    public List<GeneratedFlashcard> generateChunks(List<FlashcardChunk> chunks, String additionalInstructions) {
        return generateChunks(chunks, additionalInstructions, completedChunks -> {});
    }

    public List<GeneratedFlashcard> generateChunks(List<FlashcardChunk> chunks, String additionalInstructions, IntConsumer progressCallback) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("Flashcard generation requires at least one source chunk.");
        }
        IntConsumer progress = progressCallback == null ? completedChunks -> {} : progressCallback;
        List<GeneratedFlashcard> all = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            FlashcardChunk chunk = chunks.get(i);
            all.addAll(generateChunk(chunk, additionalInstructions));
            progress.accept(i + 1);
        }
        return dedupe(all);
    }

    private List<GeneratedFlashcard> generateChunk(FlashcardChunk chunk, String additionalInstructions) {
        String prompt = buildChunkPrompt(chunk, additionalInstructions);
        Media[] media = chunk.hasPdfResource()
            ? new Media[] { new Media(new MimeType("application", "pdf"), chunk.pdfResource()) }
            : new Media[0];
        FlashcardsResponse response;
        try {
            throttlingService.throttle();
            response = chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder()
                    .responseMimeType("application/json")
                    .responseSchema(responseSchema)
                    .thinkingLevel(GoogleGenAiThinkingLevel.MEDIUM))
                .user(u -> {
                    u.text(prompt);
                    if (media.length > 0) u.media(media);
                })
                .call()
                .entity(FlashcardsResponse.class);
        } catch (Exception e) {
            throw AiGenerationSupport.failure(log, "FLASHCARDS", "PROVIDER_REQUEST",
                "AI request failed while generating flashcards for " + chunk.sourceFilename() + " pages "
                    + chunk.startPage() + "-" + chunk.endPage() + ".", e);
        }
        return validCards(response);
    }

    private List<GeneratedFlashcard> validCards(FlashcardsResponse response) {
        List<GeneratedFlashcard> rawList = response == null || response.flashcards() == null
            ? List.of()
            : response.flashcards();
        List<GeneratedFlashcard> valid = new ArrayList<>();
        for (GeneratedFlashcard card : rawList) {
            if (card == null) continue;
            String front = card.frontText() == null ? null : card.frontText().trim();
            String back = card.backText() == null ? null : card.backText().trim();
            if (front == null || front.isBlank() || back == null || back.isBlank()) continue;
            valid.add(new GeneratedFlashcard(front, back));
        }
        if (valid.isEmpty()) {
            throw AiGenerationSupport.failure(log, "FLASHCARDS", "RESPONSE_VALIDATION",
                "AI returned no valid flashcards; please retry.",
                new IllegalStateException("AI response contained no flashcards with both frontText and backText."));
        }
        return valid;
    }

    private String buildChunkPrompt(FlashcardChunk chunk, String additionalInstructions) {
        String source = chunk.hasPdfResource()
            ? "Attached PDF page range is the source. Filename: " + chunk.sourceFilename()
            : chunk.text();
        return """
            You are a study assistant. Generate flashcards from exactly this source chunk.

            SOURCE:
            %s pages %d-%d, chunk %d.

            TASK:
            Create one concise, self-contained flashcard for every testable detail in this chunk.
            A testable detail is a definition, fact, formula, rule, process step, comparison,
            cause/effect relationship, named concept, exception, caveat, or example that teaches a concept.

            RULES:
            - Do not target a fixed number of flashcards.
            - Do not omit testable details just to keep the deck short.
            - Skip metadata, headers, footers, page numbers, bibliographies, acknowledgements, and layout-only details.
            - Skip vague image references unless the needed information is present in the chunk.
            - Preserve the dominant source language.
            - Avoid duplicates within this chunk.

            CHUNK CONTENT:
            %s
            """.formatted(chunk.sourceFilename(), chunk.startPage(), chunk.endPage(), chunk.chunkIndex(), source)
            + AiInstructionSupport.section(additionalInstructions);
    }

    private List<GeneratedFlashcard> dedupe(List<GeneratedFlashcard> cards) {
        List<GeneratedFlashcard> deduped = new ArrayList<>();
        for (GeneratedFlashcard card : cards) {
            boolean duplicate = deduped.stream().anyMatch(existing -> duplicateCard(existing, card));
            if (!duplicate) {
                deduped.add(card);
            }
        }
        return deduped;
    }

    private boolean duplicateCard(GeneratedFlashcard left, GeneratedFlashcard right) {
        return similarText(left.frontText(), right.frontText())
            && similarText(left.backText(), right.backText());
    }

    private boolean similarText(String left, String right) {
        String a = normalizeForDedupe(left);
        String b = normalizeForDedupe(right);
        if (a.equals(b)) return true;
        int maxLength = Math.max(a.length(), b.length());
        if (maxLength < 40) return false;
        return levenshteinSimilarity(a, b) >= 0.94;
    }

    private String normalizeForDedupe(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .strip()
            .replaceAll("\\s+", " ");
    }

    private double levenshteinSimilarity(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) return 1.0;
        return 1.0 - ((double) levenshteinDistance(left, right) / maxLength);
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + substitutionCost
                );
            }
            int[] tmp = previous;
            previous = current;
            current = tmp;
        }
        return previous[right.length()];
    }
}
