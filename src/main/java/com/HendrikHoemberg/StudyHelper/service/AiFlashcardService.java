package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiFlashcardService {

    private static final int MAX_FLASHCARDS = 50;

    private final ChatClient chatClient;
    private final JsonMapper objectMapper;

    public AiFlashcardService(ChatClient.Builder builder, JsonMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<GeneratedFlashcard> generate(DocumentInput document) {
        if (document == null) {
            throw new IllegalArgumentException("Flashcard generation requires one PDF input.");
        }

        String docContent = buildTextDocContent(document);
        String pdfListing = buildPdfListing(document);
        Media[] pdfMedia = buildPdfMedia(document);

        if (docContent.isBlank() && pdfMedia.length == 0) {
            throw new IllegalArgumentException("Selected sources contain no usable text or PDFs. Pick a document with extractable content, or a PDF in full-document mode.");
        }

        String prompt = buildPrompt(docContent, pdfListing);

        String response;
        try {
            response = chatClient.prompt()
                .user(u -> {
                    u.text(prompt);
                    if (pdfMedia.length > 0) u.media(pdfMedia);
                })
                .call()
                .content();
        } catch (Exception e) {
            throw new IllegalStateException("AI request failed, please retry with fewer or smaller PDFs.", e);
        }

        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            JsonNode flashcardsNode = root.get("flashcards");
            List<GeneratedFlashcard> rawList = flashcardsNode != null
                ? objectMapper.<List<GeneratedFlashcard>>readerForListOf(GeneratedFlashcard.class).readValue(flashcardsNode)
                : List.of();

            List<GeneratedFlashcard> valid = new ArrayList<>();
            for (GeneratedFlashcard card : rawList) {
                if (card == null) continue;
                String front = card.frontText() == null ? null : card.frontText().trim();
                String back = card.backText() == null ? null : card.backText().trim();
                if (front == null || front.isBlank() || back == null || back.isBlank()) continue;
                valid.add(new GeneratedFlashcard(front, back));
            }

            if (valid.isEmpty()) {
                throw new IllegalStateException("AI returned no valid flashcards; please retry.");
            }

            return valid.stream().limit(MAX_FLASHCARDS).toList();

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the AI response. Please try again.", e);
        }
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
        if (!(document instanceof PdfDocument pd)) return new Media[0];
        return new Media[] { new Media(new MimeType("application", "pdf"), pd.source()) };
    }

    private String buildPrompt(String docContent, String pdfListing) {
        String docSection = docContent.isBlank() ? "(none)" : docContent;
        String pdfSection = pdfListing.isBlank() ? "(none)" : pdfListing;

        return ("You are a study assistant. Generate flashcards based on the source material below.\n\n"
            + "Maximum flashcards: %d\n\n"
            + "GENERAL RULES:\n"
            + "- First identify the dominant educational content of the source material.\n"
            + "- Ignore metadata, headers, footers, page numbers, and incidental details.\n"
            + "- Create concise front and back text for each flashcard.\n"
            + "- Keep each card self-contained and avoid duplicate cards.\n"
            + "- Use the attached PDF documents as primary source material when present.\n\n"
            + "=== DOCUMENTS ===\n"
            + "%s\n\n"
            + "=== ATTACHED PDFs ===\n"
            + "%s\n\n"
            + "Respond ONLY with valid JSON, no extra text. Schema:\n"
            + "{\"flashcards\":[{\"frontText\":\"...\",\"backText\":\"...\"}]}"
        ).formatted(MAX_FLASHCARDS, docSection, pdfSection);
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
