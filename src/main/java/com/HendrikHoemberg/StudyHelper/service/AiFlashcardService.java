package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardsResponse;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiFlashcardService {

    private static final Logger log = LoggerFactory.getLogger(AiFlashcardService.class);
    private static final int MAX_FLASHCARDS = 50;

    private final ChatClient chatClient;
    private final String responseSchema;

    public AiFlashcardService(ChatClient.Builder builder, JsonMapper objectMapper) {
        this.chatClient = builder.build();
        this.responseSchema = new BeanOutputConverter<>(FlashcardsResponse.class, objectMapper).getJsonSchema();
    }

    public List<GeneratedFlashcard> generate(DocumentInput document) {
        if (document == null) {
            throw new IllegalArgumentException("Flashcard generation requires one PDF input.");
        }

        String docContent = buildTextDocContent(document);
        String pdfListing = buildPdfListing(document);
        Media[] pdfMedia = buildPdfMedia(document);

        if (docContent.isBlank() && pdfMedia.length == 0) {
            throw new IllegalArgumentException(
                    "Selected sources contain no usable text or PDFs. Pick a document with extractable content, or a PDF in full-document mode.");
        }

        String prompt = buildPrompt(docContent, pdfListing);

        FlashcardsResponse response;
        try {
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
            throw aiFailure("PROVIDER_REQUEST", "AI request failed, please retry with fewer or smaller PDFs.", e);
        }

        try {
            List<GeneratedFlashcard> rawList = response == null || response.flashcards() == null
                    ? List.of()
                    : response.flashcards();

            List<GeneratedFlashcard> valid = new ArrayList<>();
            for (GeneratedFlashcard card : rawList) {
                if (card == null)
                    continue;
                String front = card.frontText() == null ? null : card.frontText().trim();
                String back = card.backText() == null ? null : card.backText().trim();
                if (front == null || front.isBlank() || back == null || back.isBlank())
                    continue;
                valid.add(new GeneratedFlashcard(front, back));
            }

            if (valid.isEmpty()) {
                throw aiFailure("RESPONSE_VALIDATION", "AI returned no valid flashcards; please retry.",
                    new IllegalStateException("AI response contained no flashcards with both frontText and backText."));
            }

            return valid.stream().limit(MAX_FLASHCARDS).toList();

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw aiFailure("RESPONSE_PARSE", "Could not parse the AI response. Please try again.", e);
        }
    }

    private AiGenerationException aiFailure(String stage, String message, Throwable cause) {
        AiGenerationDiagnostics diagnostics = AiGenerationDiagnostics.fromException("FLASHCARDS", stage, cause);
        log.error("AI generation failed generationId={} type={} stage={}",
            diagnostics.generationId(), diagnostics.type(), diagnostics.stage(), cause);
        return new AiGenerationException(message, diagnostics, cause);
    }

    private String buildTextDocContent(DocumentInput document) {
        StringBuilder sb = new StringBuilder();
        if (document instanceof TextDocument td && td.extractedText() != null && !td.extractedText().isBlank()) {
            sb.append("Document: ").append(td.filename()).append("\n")
                    .append(td.extractedText().trim()).append("\n\n");
        }
        return sb.toString();
    }

    private String buildPdfListing(DocumentInput document) {
        StringBuilder sb = new StringBuilder();
        if (document instanceof PdfDocument pd) {
            sb.append("- ").append(pd.filename()).append("\n");
        }
        return sb.toString();
    }

    private Media[] buildPdfMedia(DocumentInput document) {
        if (!(document instanceof PdfDocument pd))
            return new Media[0];
        return new Media[] { new Media(new MimeType("application", "pdf"), pd.source()) };
    }

    private String buildPrompt(String docContent, String pdfListing) {
        String docSection = docContent.isBlank() ? "(none)" : docContent;
        String pdfSection = pdfListing.isBlank() ? "(none)" : pdfListing;

        return ("You are a study assistant. Generate flashcards based on the source material below.\n\n"
                + "Maximum flashcards: %d\n\n"
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
                + "%s\n").formatted(MAX_FLASHCARDS, docSection, pdfSection);
    }
}
