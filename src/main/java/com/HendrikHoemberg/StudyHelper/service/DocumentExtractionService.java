package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static String extension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
