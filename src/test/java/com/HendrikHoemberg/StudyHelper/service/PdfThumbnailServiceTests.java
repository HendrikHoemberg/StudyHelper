package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfThumbnailServiceTests {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;
    private PdfThumbnailService service;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        service = new PdfThumbnailService(fileStorageService);
    }

    @Test
    void thumbnailForPortraitPdfFillsLandscapePreviewWithoutWhiteSideGutters() throws IOException {
        Path pdf = tempDir.resolve("portrait.pdf");
        writeFilledPortraitPdf(pdf);
        when(fileStorageService.resolvePath("stored-portrait.pdf")).thenReturn(pdf);

        Resource resource = service.thumbnailFor(fileEntry("stored-portrait.pdf"));
        BufferedImage thumbnail = ImageIO.read(resource.getInputStream());

        assertThat(resource.getFilename()).isEqualTo("portrait.pdf.cover-top.png");
        assertThat(thumbnail.getWidth()).isEqualTo(360);
        assertThat(thumbnail.getHeight()).isEqualTo(240);
        assertThat(new Color(thumbnail.getRGB(0, thumbnail.getHeight() / 2))).isNotEqualTo(Color.WHITE);
        assertThat(new Color(thumbnail.getRGB(thumbnail.getWidth() - 1, thumbnail.getHeight() / 2))).isNotEqualTo(Color.WHITE);
        assertThat(new Color(thumbnail.getRGB(10, 10))).isEqualTo(Color.RED);
    }

    private FileEntry fileEntry(String storedFilename) {
        FileEntry entry = new FileEntry();
        entry.setOriginalFilename("source.pdf");
        entry.setStoredFilename(storedFilename);
        entry.setMimeType("application/pdf");
        return entry;
    }

    private void writeFilledPortraitPdf(Path dest) throws IOException {
        PDRectangle pageSize = new PDRectangle(300, 600);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.setNonStrokingColor(new Color(180, 180, 180));
                content.addRect(0, 0, pageSize.getWidth(), pageSize.getHeight());
                content.fill();
                content.setNonStrokingColor(Color.RED);
                content.addRect(0, pageSize.getHeight() - 120, pageSize.getWidth(), 120);
                content.fill();
            }
            doc.save(dest.toFile());
        }
    }
}
