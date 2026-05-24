package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardChunk;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationPlan;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationRisk;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class FlashcardGenerationPlanService {

    static final int WARNING_THRESHOLD = 5;
    static final int HIGH_THRESHOLD = 13;
    static final int MAX_PAGES_PER_CHUNK = 5;
    static final int MAX_WORDS_PER_CHUNK = 4000;
    static final int SECONDS_LOW_PER_CHUNK = 12;
    static final int SECONDS_HIGH_PER_CHUNK = 20;
    static final int CARDS_LOW_PER_CHUNK = 12;
    static final int CARDS_HIGH_PER_CHUNK = 35;

    private final DocumentExtractionService documentExtractionService;

    public FlashcardGenerationPlanService(DocumentExtractionService documentExtractionService) {
        this.documentExtractionService = documentExtractionService;
    }

    public FlashcardGenerationPlan plan(List<FileEntry> pdfs, DocumentMode mode) throws IOException {
        if (pdfs == null || pdfs.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one PDF.");
        }
        List<FlashcardChunk> chunks = new ArrayList<>();
        for (FileEntry pdf : pdfs) {
            chunks.addAll(mode == DocumentMode.FULL_PDF ? fullPdfChunks(pdf) : textChunks(pdf));
        }
        int cost = chunks.size();
        return new FlashcardGenerationPlan(
            List.copyOf(chunks),
            cost,
            cost * SECONDS_LOW_PER_CHUNK,
            cost * SECONDS_HIGH_PER_CHUNK,
            cost * CARDS_LOW_PER_CHUNK,
            cost * CARDS_HIGH_PER_CHUNK,
            risk(cost)
        );
    }

    private List<FlashcardChunk> textChunks(FileEntry pdf) throws IOException {
        List<String> pages = documentExtractionService.extractPdfTextPages(pdf);
        List<FlashcardChunk> chunks = new ArrayList<>();
        int chunkIndex = 1;
        int startPage = 1;
        int endPage = 1;
        StringBuilder text = new StringBuilder();
        int words = 0;
        for (int i = 0; i < pages.size(); i++) {
            String pageText = pages.get(i) == null ? "" : pages.get(i).strip();
            int pageNumber = i + 1;
            for (String part : splitByWordLimit(pageText)) {
                int partWords = wordCount(part);
                boolean pageLimitReached = pageNumber > endPage && pageNumber - startPage >= MAX_PAGES_PER_CHUNK;
                boolean wordLimitReached = words > 0 && words + partWords > MAX_WORDS_PER_CHUNK;
                if (text.length() > 0 && (pageLimitReached || wordLimitReached)) {
                    chunks.add(new FlashcardChunk(pdf.getId(), pdf.getOriginalFilename(), chunkIndex++, startPage, endPage, text.toString().strip(), null));
                    text.setLength(0);
                    words = 0;
                    startPage = pageNumber;
                    endPage = pageNumber;
                }
                text.append("Page ").append(pageNumber).append(":\n").append(part).append("\n\n");
                endPage = pageNumber;
                words += partWords;
            }
        }
        if (text.length() > 0) {
            chunks.add(new FlashcardChunk(pdf.getId(), pdf.getOriginalFilename(), chunkIndex, startPage, endPage, text.toString().strip(), null));
        }
        return chunks;
    }

    private List<FlashcardChunk> fullPdfChunks(FileEntry pdf) throws IOException {
        int pages = documentExtractionService.pdfPageCount(pdf);
        List<FlashcardChunk> chunks = new ArrayList<>();
        int chunkIndex = 1;
        for (int start = 1; start <= pages; start += MAX_PAGES_PER_CHUNK) {
            int end = Math.min(start + MAX_PAGES_PER_CHUNK - 1, pages);
            Resource resource = documentExtractionService.loadPdfPageRangeResource(pdf, start, end);
            chunks.add(new FlashcardChunk(pdf.getId(), pdf.getOriginalFilename(), chunkIndex++, start, end, "", resource));
        }
        return chunks;
    }

    private List<String> splitByWordLimit(String text) {
        if (wordCount(text) <= MAX_WORDS_PER_CHUNK) return List.of(text);
        String[] words = text.split("\\s+");
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;
        for (String word : words) {
            if (count == MAX_WORDS_PER_CHUNK) {
                parts.add(current.toString().strip());
                current.setLength(0);
                count = 0;
            }
            if (count > 0) current.append(' ');
            current.append(word);
            count++;
        }
        if (current.length() > 0) parts.add(current.toString().strip());
        return parts;
    }

    private int wordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.strip().split("\\s+").length;
    }

    private FlashcardGenerationRisk risk(int cost) {
        if (cost >= HIGH_THRESHOLD) return FlashcardGenerationRisk.HIGH;
        if (cost >= WARNING_THRESHOLD) return FlashcardGenerationRisk.WARNING;
        return FlashcardGenerationRisk.NORMAL;
    }
}
