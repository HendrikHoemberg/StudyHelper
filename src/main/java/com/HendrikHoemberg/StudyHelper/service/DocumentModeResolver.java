package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;

import java.util.Map;

public final class DocumentModeResolver {

    private DocumentModeResolver() {}

    public static DocumentMode resolve(FileEntry file, Map<Long, DocumentMode> pdfMode) {
        if (!isPdf(file)) return DocumentMode.TEXT;
        if (pdfMode == null) return DocumentMode.TEXT;
        return pdfMode.getOrDefault(file.getId(), DocumentMode.TEXT);
    }

    private static boolean isPdf(FileEntry file) {
        String name = file.getOriginalFilename();
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return "pdf".equalsIgnoreCase(name.substring(dot + 1));
    }
}
