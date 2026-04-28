package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record FileSummary(
    Long id,
    String originalFilename,
    String folderPath,
    String folderColorHex,
    String mimeType,
    Long fileSizeBytes,
    LocalDateTime uploadedAt
) implements Serializable {
}
