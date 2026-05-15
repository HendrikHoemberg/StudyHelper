package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Selection-handling helpers shared by the quiz and exam session services (and
 * the study wizard controller), which previously carried byte-identical private
 * copies of each of these.
 */
public final class StudySourceSupport {

    /** Upper bound on the combined extracted-text length of a generation request. */
    public static final long MAX_SELECTION_CHARS = 150_000;

    /** Largest number of questions a single generation request may ask for. */
    public static final int MAX_QUESTION_COUNT = 20;

    private StudySourceSupport() {
    }

    /** Drops nulls and duplicates from a raw request-parameter id list. */
    public static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /** Clamps a requested question count into the supported 1..MAX range. */
    public static int normalizeQuestionCount(int count) {
        return Math.max(1, Math.min(count, MAX_QUESTION_COUNT));
    }

    public static boolean isPdf(FileEntry file) {
        String filename = file == null ? null : file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    /**
     * Extracts the file's text, rejecting documents that yield nothing usable
     * (e.g. an image-only PDF) so callers get a clear, actionable message.
     */
    public static String requireExtractedText(DocumentExtractionService extractor, FileEntry file) throws IOException {
        String text = extractor.extractText(file);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("This PDF has no extractable text. Try Full PDF mode.");
        }
        return text;
    }
}
