package com.HendrikHoemberg.StudyHelper.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTests {

    private final UploadValidator validator = new UploadValidator();

    private static final byte[] PDF_MAGIC = "%PDF-1.7\n...".getBytes();
    private static final byte[] PNG_MAGIC = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 'x', 'x'
    };
    private static final byte[] JPEG_MAGIC = new byte[] {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 'x', 'x'
    };
    private static final byte[] GIF_MAGIC = "GIF89a-data".getBytes();
    private static final byte[] WEBP_MAGIC = new byte[] {
        'R','I','F','F', 0, 0, 0, 0, 'W','E','B','P', 'x', 'x'
    };

    @Test
    void allowsPdfWithMatchingExtensionAndMagic() {
        var file = new MockMultipartFile("file", "lecture.pdf", "application/pdf", PDF_MAGIC);
        assertThat(validator.validateDocument(file)).isEqualTo("application/pdf");
    }

    @Test
    void rejectsExecutableMasqueradingAsPdf() {
        byte[] mzExe = new byte[] { 'M', 'Z', 0, 0, 0, 0 };
        var file = new MockMultipartFile("file", "evil.pdf", "application/pdf", mzExe);
        assertThatThrownBy(() -> validator.validateDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("content does not match");
    }

    @Test
    void rejectsDisallowedExtensions() {
        var file = new MockMultipartFile("file", "shell.jsp", "text/plain", "<%%>".getBytes());
        assertThatThrownBy(() -> validator.validateDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> validator.validateDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void rejectsMissingFilename() {
        var file = new MockMultipartFile("file", null, "application/pdf", PDF_MAGIC);
        assertThatThrownBy(() -> validator.validateDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("filename");
    }

    @Test
    void acceptsTxtAsUtf8() {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello world".getBytes());
        assertThat(validator.validateDocument(file)).isEqualTo("text/plain");
    }

    @Test
    void acceptsPngForGeneralFileUpload() {
        var file = new MockMultipartFile("file", "diagram.png", "image/png", PNG_MAGIC);
        assertThat(validator.validateUpload(file)).isEqualTo("image/png");
    }

    @Test
    void rejectsTxtWithBinaryGarbage() {
        byte[] garbage = new byte[] { 0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE };
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", garbage);
        assertThatThrownBy(() -> validator.validateDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not valid UTF-8 text");
    }

    @Test
    void acceptsPngImageForImageValidator() {
        var file = new MockMultipartFile("file", "a.png", "image/png", PNG_MAGIC);
        assertThat(validator.validateImage(file)).isEqualTo("image/png");
    }

    @Test
    void acceptsJpegImage() {
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG_MAGIC);
        assertThat(validator.validateImage(file)).isEqualTo("image/jpeg");
    }

    @Test
    void acceptsGifImage() {
        var file = new MockMultipartFile("file", "a.gif", "image/gif", GIF_MAGIC);
        assertThat(validator.validateImage(file)).isEqualTo("image/gif");
    }

    @Test
    void acceptsWebpImage() {
        var file = new MockMultipartFile("file", "a.webp", "image/webp", WEBP_MAGIC);
        assertThat(validator.validateImage(file)).isEqualTo("image/webp");
    }

    @Test
    void imageRejectsPdf() {
        var file = new MockMultipartFile("file", "fake.png", "image/png", PDF_MAGIC);
        assertThatThrownBy(() -> validator.validateImage(file))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizedFilenameStripsPathSegments() {
        assertThat(validator.safeExtension("../../etc/passwd.pdf")).isEqualTo(".pdf");
        assertThat(validator.safeExtension("legit.PDF")).isEqualTo(".pdf");
        assertThat(validator.safeExtension("no-ext")).isEqualTo("");
    }
}
