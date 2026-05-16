package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfThumbnailService {

    private static final int THUMBNAIL_WIDTH = 360;
    private static final int THUMBNAIL_HEIGHT = 240;
    private static final float RENDER_DPI = 96f;

    private final FileStorageService fileStorageService;

    public PdfThumbnailService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public Resource thumbnailFor(FileEntry entry) throws IOException {
        if (!"application/pdf".equals(entry.getMimeType())) {
            throw new IllegalArgumentException("Thumbnail generation only supports PDF files.");
        }

        Path source = fileStorageService.resolvePath(entry.getStoredFilename());
        Path thumbnail = thumbnailPath(source);
        if (Files.notExists(thumbnail)) {
            Files.createDirectories(thumbnail.getParent());
            renderFirstPage(source, thumbnail);
        }
        return resourceFor(thumbnail);
    }

    private Path thumbnailPath(Path source) {
        String filename = source.getFileName().toString() + ".png";
        return source.getParent().resolve("thumbnails").resolve(filename);
    }

    private void renderFirstPage(Path source, Path thumbnail) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage rendered = renderer.renderImageWithDPI(0, RENDER_DPI, ImageType.RGB);
            BufferedImage scaled = scaleToFit(rendered);
            ImageIO.write(scaled, "png", thumbnail.toFile());
        }
    }

    private BufferedImage scaleToFit(BufferedImage source) {
        double scale = Math.min(
            THUMBNAIL_WIDTH / (double) source.getWidth(),
            THUMBNAIL_HEIGHT / (double) source.getHeight()
        );
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage thumbnail = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int x = (THUMBNAIL_WIDTH - width) / 2;
            int y = (THUMBNAIL_HEIGHT - height) / 2;
            graphics.drawImage(source, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }
        return thumbnail;
    }

    private Resource resourceFor(Path thumbnail) {
        try {
            Resource resource = new UrlResource(thumbnail.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("PDF thumbnail not readable: " + thumbnail.getFileName());
        } catch (MalformedURLException e) {
            throw new RuntimeException("PDF thumbnail not found: " + thumbnail.getFileName(), e);
        }
    }
}
