package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.SplitPart;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.exception.ResourceNotFoundException;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfSplitService {

    private final FileEntryRepository fileEntryRepository;
    private final FileStorageService fileStorageService;
    private final StorageQuotaService storageQuotaService;

    public PdfSplitService(FileEntryRepository fileEntryRepository,
                           FileStorageService fileStorageService,
                           StorageQuotaService storageQuotaService) {
        this.fileEntryRepository = fileEntryRepository;
        this.fileStorageService = fileStorageService;
        this.storageQuotaService = storageQuotaService;
    }

    @Transactional
    public Long splitPdf(Long fileId, List<SplitPart> parts, User user) throws IOException {
        FileEntry source = fileEntryRepository.findByIdAndUser(fileId, user)
            .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (!"application/pdf".equals(source.getMimeType())) {
            throw new IllegalArgumentException("Only PDF files can be split.");
        }

        Path sourcePath = fileStorageService.resolvePath(source.getStoredFilename());
        List<byte[]> partBytes = new ArrayList<>();
        long totalBytes = 0L;

        try (PDDocument document = Loader.loadPDF(sourcePath.toFile())) {
            validate(parts, document.getNumberOfPages());
            for (SplitPart part : parts) {
                byte[] bytes = extractRange(document, part.startPage(), part.endPage());
                partBytes.add(bytes);
                totalBytes += bytes.length;
            }
        }

        storageQuotaService.assertWithinQuota(user, 0L, totalBytes);

        for (int i = 0; i < parts.size(); i++) {
            byte[] bytes = partBytes.get(i);
            String storedFilename = fileStorageService.storeBytes(bytes, ".pdf");

            FileEntry entry = new FileEntry();
            entry.setOriginalFilename(normalizeName(parts.get(i).name()));
            entry.setStoredFilename(storedFilename);
            entry.setMimeType("application/pdf");
            entry.setFileSizeBytes((long) bytes.length);
            entry.setFolder(source.getFolder());
            entry.setUser(user);
            fileEntryRepository.save(entry);
        }

        return source.getFolder().getId();
    }

    private byte[] extractRange(PDDocument source, int startPage, int endPage) throws IOException {
        try (PDDocument part = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int p = startPage; p <= endPage; p++) {
                part.importPage(source.getPage(p - 1));
            }
            part.save(out);
            return out.toByteArray();
        }
    }

    private void validate(List<SplitPart> parts, int pageCount) {
        if (parts == null || parts.size() < 2) {
            throw new IllegalArgumentException("A split needs at least two parts.");
        }
        int expectedStart = 1;
        for (SplitPart part : parts) {
            if (part.name() == null || part.name().isBlank()) {
                throw new IllegalArgumentException("Every part needs a name.");
            }
            if (part.startPage() != expectedStart) {
                throw new IllegalArgumentException("Parts must cover the document without gaps.");
            }
            if (part.endPage() < part.startPage() || part.endPage() > pageCount) {
                throw new IllegalArgumentException("A part's page range is out of bounds.");
            }
            expectedStart = part.endPage() + 1;
        }
        if (expectedStart != pageCount + 1) {
            throw new IllegalArgumentException("Parts must cover every page of the document.");
        }
    }

    private String normalizeName(String name) {
        String trimmed = name.trim();
        return trimmed.toLowerCase().endsWith(".pdf") ? trimmed : trimmed + ".pdf";
    }
}
