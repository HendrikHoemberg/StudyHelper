package com.HendrikHoemberg.StudyHelper.dto;

import org.springframework.core.io.Resource;

public record FlashcardChunk(
    Long sourceFileId,
    String sourceFilename,
    int chunkIndex,
    int startPage,
    int endPage,
    String text,
    Resource pdfResource
) {
    public FlashcardChunk(String sourceFilename, int chunkIndex, int startPage, int endPage, String text, Resource pdfResource) {
        this(null, sourceFilename, chunkIndex, startPage, endPage, text, pdfResource);
    }

    public boolean hasPdfResource() {
        return pdfResource != null;
    }
}
