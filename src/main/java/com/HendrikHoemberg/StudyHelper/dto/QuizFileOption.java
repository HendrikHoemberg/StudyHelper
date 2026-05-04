package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record QuizFileOption(
    Long fileId,
    String filename,
    long sizeBytes,
    String extension,        // "pdf" | "txt" | "md"
    boolean isSupported       // false if size > 5MB or extraction would fail
) implements Serializable {}
