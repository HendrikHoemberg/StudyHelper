package com.HendrikHoemberg.StudyHelper.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void storeBytesWritesContentUnderUniqueFilenameWithExtension() throws IOException {
        FileStorageService service = new FileStorageService(tempDir.toString());
        byte[] content = {10, 20, 30, 40, 50};

        String storedA = service.storeBytes(content, ".pdf");
        String storedB = service.storeBytes(content, ".pdf");

        assertThat(storedA).endsWith(".pdf");
        assertThat(storedB).endsWith(".pdf");
        assertThat(storedA).isNotEqualTo(storedB);
        assertThat(Files.readAllBytes(tempDir.resolve(storedA))).isEqualTo(content);
    }
}
