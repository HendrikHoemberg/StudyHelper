package com.HendrikHoemberg.StudyHelper.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Validates uploaded MultipartFiles against an extension whitelist
 * and the file's actual magic-byte content. The MIME type returned by
 * this validator is the one that should be persisted with the FileEntry —
 * the client-supplied Content-Type is never trusted.
 */
@Service
public class UploadValidator {

    private static final Set<String> DOC_EXTENSIONS = Set.of(".pdf", ".txt", ".md");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");
    private static final int SNIFF_BYTES = 16;

    public String validateDocument(MultipartFile file) {
        byte[] head = preflight(file);
        String ext = requireWhitelistedExtension(file, DOC_EXTENSIONS);
        return switch (ext) {
            case ".pdf" -> matchOrReject(head, "application/pdf", isPdf(head), ext);
            case ".txt", ".md" -> validateUtf8(file);
            default -> throw new IllegalArgumentException("File type not allowed: " + ext);
        };
    }

    public String validateImage(MultipartFile file) {
        byte[] head = preflight(file);
        String ext = requireWhitelistedExtension(file, IMAGE_EXTENSIONS);
        return switch (ext) {
            case ".png" -> matchOrReject(head, "image/png", isPng(head), ext);
            case ".jpg", ".jpeg" -> matchOrReject(head, "image/jpeg", isJpeg(head), ext);
            case ".gif" -> matchOrReject(head, "image/gif", isGif(head), ext);
            case ".webp" -> matchOrReject(head, "image/webp", isWebp(head), ext);
            default -> throw new IllegalArgumentException("Image type not allowed: " + ext);
        };
    }

    public String safeExtension(String filename) {
        if (filename == null) return "";
        // Strip any path components (..\ ../ etc.) — keep only the basename.
        String base = filename.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot < 0) return "";
        return base.substring(dot).toLowerCase();
    }

    private byte[] preflight(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("Uploaded file has no filename.");
        }
        try {
            byte[] all = file.getBytes();
            int len = Math.min(SNIFF_BYTES, all.length);
            byte[] head = new byte[len];
            System.arraycopy(all, 0, head, 0, len);
            return head;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
        }
    }

    private String requireWhitelistedExtension(MultipartFile file, Set<String> allowed) {
        String ext = safeExtension(file.getOriginalFilename());
        if (!allowed.contains(ext)) {
            throw new IllegalArgumentException("File type not allowed: " + (ext.isEmpty() ? "<no extension>" : ext));
        }
        return ext;
    }

    private String matchOrReject(byte[] head, String mime, boolean matches, String ext) {
        if (!matches) {
            throw new IllegalArgumentException("File content does not match extension " + ext + ".");
        }
        return mime;
    }

    private String validateUtf8(MultipartFile file) {
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(java.nio.ByteBuffer.wrap(file.getBytes()));
            return "text/plain";
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Uploaded text file is not valid UTF-8 text.", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
        }
    }

    private boolean isPdf(byte[] h) {
        return h.length >= 4 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
    }

    private boolean isPng(byte[] h) {
        return h.length >= 8
            && (h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47
            && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A;
    }

    private boolean isJpeg(byte[] h) {
        return h.length >= 3
            && (h[0] & 0xFF) == 0xFF
            && (h[1] & 0xFF) == 0xD8
            && (h[2] & 0xFF) == 0xFF;
    }

    private boolean isGif(byte[] h) {
        return h.length >= 6
            && h[0] == 'G' && h[1] == 'I' && h[2] == 'F'
            && h[3] == '8'
            && (h[4] == '7' || h[4] == '9')
            && h[5] == 'a';
    }

    private boolean isWebp(byte[] h) {
        return h.length >= 12
            && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
            && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }
}
