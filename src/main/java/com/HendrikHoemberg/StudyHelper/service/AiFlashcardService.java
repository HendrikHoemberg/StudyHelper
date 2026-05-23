package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.ConceptOutline;
import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiFlashcardService {

    private static final Logger log = LoggerFactory.getLogger(AiFlashcardService.class);
    private static final int MAX_FLASHCARDS = 200;

    private final ConceptExtractionService extractor;
    private final FlashcardWriterService writer;

    public AiFlashcardService(ConceptExtractionService extractor, FlashcardWriterService writer) {
        this.extractor = extractor;
        this.writer = writer;
    }

    public List<GeneratedFlashcard> generate(DocumentInput document) {
        return generate(document, null);
    }

    public List<GeneratedFlashcard> generate(DocumentInput document, String additionalInstructions) {
        if (document == null) {
            throw new IllegalArgumentException("Flashcard generation requires at least one PDF input.");
        }
        return generate(List.of(document), additionalInstructions);
    }

    public List<GeneratedFlashcard> generate(List<DocumentInput> documents, String additionalInstructions) {
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
        Media[] pdfMedia = AiGenerationSupport.pdfMedia(usableDocuments);
        if (docContent.isBlank() && pdfMedia.length == 0) {
            throw new IllegalArgumentException(
                "Selected sources contain no usable text or PDFs. Pick a document with extractable content, or a PDF in full-document mode.");
        }

        ConceptOutline outline = extractor.extract(usableDocuments, additionalInstructions);
        if (outline.concepts().isEmpty()) {
            throw AiGenerationSupport.failure(log, "FLASHCARDS", "EXTRACTION_EMPTY",
                "AI could not identify exam-worthy content in the selected sources; please try a different document.",
                new IllegalStateException("Concept extraction returned no concepts."));
        }

        List<GeneratedFlashcard> cards = writer.write(outline, additionalInstructions);
        if (cards.isEmpty()) {
            throw AiGenerationSupport.failure(log, "FLASHCARDS", "RESPONSE_VALIDATION",
                "AI returned no valid flashcards; please retry.",
                new IllegalStateException("Writer stage produced no flashcards from " + outline.concepts().size() + " concepts."));
        }

        return cards.stream().limit(MAX_FLASHCARDS).toList();
    }
}
