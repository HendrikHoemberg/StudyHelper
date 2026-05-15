package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import org.slf4j.Logger;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

import java.util.List;

/**
 * Shared source-material builders and a uniform generation-failure factory used
 * by the AI generation services (flashcards, quizzes, exams), which otherwise
 * carried byte-identical copies of this logic.
 */
final class AiGenerationSupport {

    private AiGenerationSupport() {
    }

    static String cards(List<Flashcard> flashcards) {
        if (flashcards == null || flashcards.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Flashcard card : flashcards) {
            if (card == null) continue;
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

    static String textDocuments(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (DocumentInput doc : documents) {
            if (!(doc instanceof TextDocument td)) continue;
            if (td.extractedText() == null || td.extractedText().isBlank()) continue;
            n++;
            sb.append("Document ").append(n).append(" — ").append(td.filename()).append(":\n")
              .append(td.extractedText()).append("\n\n");
        }
        return sb.toString();
    }

    static String pdfListing(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DocumentInput doc : documents) {
            if (doc instanceof PdfDocument pd) {
                sb.append("- ").append(pd.filename()).append("\n");
            }
        }
        return sb.toString();
    }

    static Media[] pdfMedia(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return new Media[0];
        return documents.stream()
            .filter(PdfDocument.class::isInstance)
            .map(PdfDocument.class::cast)
            .map(pd -> new Media(new MimeType("application", "pdf"), pd.source()))
            .toArray(Media[]::new);
    }

    static AiGenerationException failure(Logger log, String type, String stage, String message, Throwable cause) {
        AiGenerationDiagnostics diagnostics = AiGenerationDiagnostics.fromException(type, stage, cause);
        log.error("AI generation failed generationId={} type={} stage={}",
            diagnostics.generationId(), diagnostics.type(), diagnostics.stage(), cause);
        return new AiGenerationException(message, diagnostics, cause);
    }
}
