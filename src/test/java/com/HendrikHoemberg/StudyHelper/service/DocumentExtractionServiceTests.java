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
import java.util.List;

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

    @Test
    void pdfPageCount_returnsNumberOfPages() throws Exception {
        FileEntry file = pdfFile("pages.pdf", 3);

        assertThat(service.pdfPageCount(file)).isEqualTo(3);
    }

    @Test
    void extractPdfTextPages_returnsOneEntryPerPage() throws Exception {
        FileEntry file = pdfFileWithText("pages.pdf", "Alpha", "Beta");

        List<String> pages = service.extractPdfTextPages(file);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0)).contains("Alpha");
        assertThat(pages.get(1)).contains("Beta");
    }

    @Test
    void loadPdfPageRangeResource_returnsOnlyRequestedPages() throws Exception {
        FileEntry file = pdfFile("pages.pdf", 4);

        org.springframework.core.io.Resource range = service.loadPdfPageRangeResource(file, 2, 3);

        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(range.getContentAsByteArray())) {
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
        }
    }

    @Test
    void pdfPageCount_nonPdf_throwsIllegalArgument() {
        FileEntry entry = fileEntry("notes.txt", "stored.txt", 100L);
        assertThatThrownBy(() -> service.pdfPageCount(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected pdf file");
    }

    @Test
    void extractPdfTextPages_nonPdf_throwsIllegalArgument() {
        FileEntry entry = fileEntry("notes.md", "stored.md", 100L);
        assertThatThrownBy(() -> service.extractPdfTextPages(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected pdf file");
    }

    @Test
    void loadPdfPageRangeResource_invalidRange_throwsIllegalArgument() throws Exception {
        FileEntry file = pdfFile("invalid.pdf", 3);
        assertThatThrownBy(() -> service.loadPdfPageRangeResource(file, 4, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid PDF page range.");
    }

    @Test
    void loadPdfPageRangeResource_nonPdf_throwsIllegalArgument() {
        FileEntry entry = fileEntry("notes.txt", "stored.txt", 100L);
        assertThatThrownBy(() -> service.loadPdfPageRangeResource(entry, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected pdf file");
    }

    private FileEntry fileEntry(String originalFilename, String storedFilename, long sizeBytes) {
        FileEntry entry = new FileEntry();
        entry.setOriginalFilename(originalFilename);
        entry.setStoredFilename(storedFilename);
        entry.setFileSizeBytes(sizeBytes);
        return entry;
    }

    @Test
    void loadResource_pdfFile_returnsReadableFileSystemResource() throws IOException {
        Path pdfPath = tempDir.resolve("doc.pdf");
        writeTinyPdf(pdfPath, "hello");

        FileEntry entry = fileEntry("doc.pdf", "stored.pdf", pdfPath.toFile().length());
        when(fileStorageService.resolvePath("stored.pdf")).thenReturn(pdfPath);

        org.springframework.core.io.Resource resource = service.loadResource(entry);
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(pdfPath.toFile().length());
    }

    @Test
    void loadResource_nonPdf_throwsIllegalArgumentException() {
        FileEntry entry = fileEntry("notes.md", "stored.md", 100L);

        assertThatThrownBy(() -> service.loadResource(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pdf");
    }

    @Test
    void loadResource_oversizePdf_throwsIllegalArgumentException() {
        FileEntry entry = fileEntry("big.pdf", "big.pdf",
                DocumentExtractionService.MAX_FILE_SIZE_BYTES + 1);

        assertThatThrownBy(() -> service.loadResource(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void loadResource_nullSize_throwsIllegalArgumentException() {
        FileEntry entry = fileEntry("doc.pdf", "stored.pdf", 0L);
        entry.setFileSizeBytes(null);

        assertThatThrownBy(() -> service.loadResource(entry))
                .isInstanceOf(IllegalArgumentException.class);
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

    private FileEntry pdfFile(String filename, int pageCount) throws IOException {
        Path path = tempDir.resolve(filename);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText("Page " + (i + 1));
                    cs.endText();
                }
            }
            doc.save(path.toFile());
        }
        FileEntry entry = fileEntry(filename, filename, Files.size(path));
        when(fileStorageService.resolvePath(filename)).thenReturn(path);
        return entry;
    }

    private FileEntry pdfFileWithText(String filename, String... pageTexts) throws IOException {
        Path path = tempDir.resolve(filename);
        try (PDDocument doc = new PDDocument()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(path.toFile());
        }
        FileEntry entry = fileEntry(filename, filename, Files.size(path));
        when(fileStorageService.resolvePath(filename)).thenReturn(path);
        return entry;
    }
}
