package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentExtractionServiceTests {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;
    private DocumentExtractionService service;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        service = new DocumentExtractionService(fileStorageService);
    }

    @Test
    void extractText_txtFile_returnsContentTrimmed() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "  hello world  \n", StandardCharsets.UTF_8);

        FileEntry entry = fileEntry("test.txt", "stored.txt", 16L);
        when(fileStorageService.resolvePath("stored.txt")).thenReturn(file);

        assertThat(service.extractText(entry)).isEqualTo("hello world");
    }

    @Test
    void extractText_mdFile_returnsContentTrimmed() throws IOException {
        Path file = tempDir.resolve("notes.md");
        Files.writeString(file, "\n# Title\nSome text\n", StandardCharsets.UTF_8);

        FileEntry entry = fileEntry("notes.md", "stored.md", 20L);
        when(fileStorageService.resolvePath("stored.md")).thenReturn(file);

        assertThat(service.extractText(entry)).isEqualTo("# Title\nSome text");
    }

    @Test
    void extractText_unsupportedExtension_throwsIllegalArgumentException() {
        FileEntry entry = fileEntry("image.png", "stored.png", 100L);

        assertThatThrownBy(() -> service.extractText(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("png");
    }

    @Test
    void extractText_pdfFile_returnsTextContent() throws IOException {
        Path pdfPath = tempDir.resolve("test.pdf");
        writeTinyPdf(pdfPath, "hello world");

        FileEntry entry = fileEntry("test.pdf", "stored.pdf", pdfPath.toFile().length());
        when(fileStorageService.resolvePath("stored.pdf")).thenReturn(pdfPath);

        String text = service.extractText(entry);
        assertThat(text).containsIgnoringCase("hello world");
    }

    @Test
    void isSupported_txtUnderLimit_returnsTrue() {
        FileEntry entry = fileEntry("doc.txt", "s.txt", 1024L);
        assertThat(service.isSupported(entry)).isTrue();
    }

    @Test
    void isSupported_pngExtension_returnsFalse() {
        FileEntry entry = fileEntry("photo.png", "s.png", 100L);
        assertThat(service.isSupported(entry)).isFalse();
    }

    @Test
    void isSupported_fileOverSizeLimit_returnsFalse() {
        FileEntry entry = fileEntry("big.pdf", "big.pdf", DocumentExtractionService.MAX_FILE_SIZE_BYTES + 1);
        assertThat(service.isSupported(entry)).isFalse();
    }

    @Test
    void isSupported_fileAtExactLimit_returnsTrue() {
        FileEntry entry = fileEntry("exact.pdf", "exact.pdf", DocumentExtractionService.MAX_FILE_SIZE_BYTES);
        assertThat(service.isSupported(entry)).isTrue();
    }

    private FileEntry fileEntry(String originalFilename, String storedFilename, long sizeBytes) {
        FileEntry entry = new FileEntry();
        entry.setOriginalFilename(originalFilename);
        entry.setStoredFilename(storedFilename);
        entry.setFileSizeBytes(sizeBytes);
        return entry;
    }

    private void writeTinyPdf(Path dest, String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(dest.toFile());
        }
    }
}
