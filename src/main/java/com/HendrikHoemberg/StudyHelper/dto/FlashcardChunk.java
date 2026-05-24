package com.HendrikHoemberg.StudyHelper.dto;

import org.springframework.core.io.Resource;

public record FlashcardChunk(
    String sourceFilename,
    int chunkIndex,
    int startPage,
    int endPage,
    String text,
    Resource pdfResource
) {
    public boolean hasPdfResource() {
        return pdfResource != null;
    }
}
