package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DocumentExtractionService {

    public static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;

    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "txt", "md");

    private final FileStorageService fileStorageService;

    public DocumentExtractionService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public String extractText(FileEntry file) throws IOException {
        String ext = extension(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file type: " + ext);
        }
        Path path = fileStorageService.resolvePath(file.getStoredFilename());
        String text;
        if ("pdf".equals(ext)) {
            try (PDDocument doc = Loader.loadPDF(path.toFile())) {
                text = new PDFTextStripper().getText(doc);
            }
        } else {
            text = Files.readString(path, StandardCharsets.UTF_8);
        }
        return text == null ? "" : text.strip();
    }

    public Resource loadResource(FileEntry file) {
        String ext = extension(file.getOriginalFilename());
        if (!"pdf".equals(ext)) {
            throw new IllegalArgumentException("loadResource only supports pdf files, got: " + ext);
        }
        if (file.getFileSizeBytes() == null || file.getFileSizeBytes() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds size limit or has unknown size");
        }
        Path path = fileStorageService.resolvePath(file.getStoredFilename());
        return new FileSystemResource(path);
    }

    public boolean isSupported(FileEntry file) {
        String ext = extension(file.getOriginalFilename());
        return SUPPORTED_EXTENSIONS.contains(ext)
                && file.getFileSizeBytes() != null
                && file.getFileSizeBytes() <= MAX_FILE_SIZE_BYTES;
    }

    public int pdfPageCount(FileEntry file) throws IOException {
        assertPdf(file);
        Path path = fileStorageService.resolvePath(file.getStoredFilename());
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            return doc.getNumberOfPages();
        }
    }

    public List<String> extractPdfTextPages(FileEntry file) throws IOException {
        assertPdf(file);
        Path path = fileStorageService.resolvePath(file.getStoredFilename());
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>(doc.getNumberOfPages());
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(doc).strip());
            }
            return pages;
        }
    }

    public Resource loadPdfPageRangeResource(FileEntry file, int startPageInclusive, int endPageInclusive) throws IOException {
        assertPdf(file);
        if (startPageInclusive < 1 || endPageInclusive < startPageInclusive) {
            throw new IllegalArgumentException("Invalid PDF page range.");
        }
        Path path = fileStorageService.resolvePath(file.getStoredFilename());
        try (PDDocument source = Loader.loadPDF(path.toFile());
             PDDocument target = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int end = Math.min(endPageInclusive, source.getNumberOfPages());
            for (int page = startPageInclusive; page <= end; page++) {
                PDPage sourcePage = source.getPage(page - 1);
                PDPage imported = target.importPage(sourcePage);
                imported.setResources(sourcePage.getResources());
            }
            target.save(out);
            return new ByteArrayResource(out.toByteArray()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
        }
    }

    private void assertPdf(FileEntry file) {
        String ext = extension(file.getOriginalFilename());
        if (!"pdf".equals(ext)) {
            throw new IllegalArgumentException("Expected pdf file, got: " + ext);
        }
    }

    public static String extension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
