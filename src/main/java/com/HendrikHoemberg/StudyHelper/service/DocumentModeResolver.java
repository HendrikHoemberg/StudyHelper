package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

public final class DocumentModeResolver {

    private DocumentModeResolver() {}

    public static DocumentMode resolve(FileEntry file, Map<Long, DocumentMode> pdfMode) {
        if (!isPdf(file)) return DocumentMode.TEXT;
        if (pdfMode == null) return DocumentMode.TEXT;
        return pdfMode.getOrDefault(file.getId(), DocumentMode.TEXT);
    }

    public static Map<Long, DocumentMode> parseFromRequest(HttpServletRequest request) {
        Map<Long, DocumentMode> result = new HashMap<>();
        if (request == null) return result;
        request.getParameterMap().forEach((key, values) -> {
            if (key.startsWith("pdfMode[") && key.endsWith("]") && values.length > 0) {
                try {
                    Long id = Long.parseLong(key.substring(8, key.length() - 1));
                    DocumentMode mode = DocumentMode.valueOf(values[0]);
                    result.put(id, mode);
                } catch (Exception ignored) {}
            }
        });
        return result;
    }

    private static boolean isPdf(FileEntry file) {
        String name = file.getOriginalFilename();
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return "pdf".equalsIgnoreCase(name.substring(dot + 1));
    }
}
