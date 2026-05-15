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
 * Shared building blocks for the AI generation services (flashcards, quizzes,
 * exams), which otherwise carried byte-identical copies of this logic:
 * source-material builders, the reusable prompt scaffolding (LANGUAGE / TOPIC
 * FOCUS / COVERAGE blocks), and a uniform generation-failure factory.
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

    /**
     * The LANGUAGE instruction block. {@code outputStrings} names the JSON fields
     * the model must localise — the only part that differs between question types.
     */
    static String languageSection(String outputStrings) {
        return "LANGUAGE:\n"
            + "Detect the dominant natural language of the supplied source material (flashcards,\n"
            + "documents, and attached PDFs together). Write every output string — " + outputStrings + " —\n"
            + "in that same language. If sources mix languages, use the most-prevalent one.\n"
            + "Do not translate technical terms, proper nouns, or code.\n";
    }

    static String topicFocusSection() {
        return "TOPIC FOCUS:\n"
            + "First identify the dominant subject matter of the supplied content. Generate\n"
            + "questions only about concepts that belong to that subject. Ignore incidental\n"
            + "metadata such as author names, page numbers, publication dates, headers, footers,\n"
            + "or off-topic asides.\n";
    }

    static String coverageSection(int questionCount) {
        return ("COVERAGE:\n"
            + "Distribute the %d requested questions evenly across the entire source material.\n"
            + "- Treat each attached PDF and each text document as a separate source.\n"
            + "- Within each source, sample roughly equally from the early third, middle third,\n"
            + "  and final third (by page count for PDFs, by length for text).\n"
            + "- If multiple sources are supplied, allocate questions proportionally to source\n"
            + "  length, but ensure every source contributes at least one question when N is\n"
            + "  large enough.\n"
            + "- Do not cluster on introductions, abstracts, tables of contents, or the first\n"
            + "  few pages. Skip these unless they contain core subject matter.\n"
            + "- Before writing questions, sketch a brief internal coverage plan (which\n"
            + "  pages/sections each question will draw from). Do not include the plan in\n"
            + "  the JSON output.\n").formatted(questionCount);
    }

    static String missingContextSection() {
        return "SOME CARDS MAY HAVE MISSING CONTEXT:\n"
            + "Some flashcards may have had images removed. If a card's text alone is\n"
            + "insufficient to form a meaningful question (e.g. it references \"this diagram\"\n"
            + "or \"the image above\"), skip that card.\n";
    }
}
