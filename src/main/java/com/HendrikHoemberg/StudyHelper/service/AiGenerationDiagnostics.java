package com.HendrikHoemberg.StudyHelper.service;

import java.time.Instant;
import java.util.UUID;

public record AiGenerationDiagnostics(
    String generationId,
    Instant timestamp,
    String type,
    String stage,
    String exceptionClass,
    String exceptionMessage,
    String rootCauseClass,
    String rootCauseMessage
) {

    public static AiGenerationDiagnostics fromException(String type, String stage, Throwable throwable) {
        Throwable root = rootCause(throwable);
        return new AiGenerationDiagnostics(
            UUID.randomUUID().toString().substring(0, 8),
            Instant.now(),
            valueOrUnknown(type),
            valueOrUnknown(stage),
            throwable == null ? "(none)" : throwable.getClass().getName(),
            throwable == null ? "(none)" : valueOrUnknown(throwable.getMessage()),
            root == null ? "(none)" : root.getClass().getName(),
            root == null ? "(none)" : valueOrUnknown(root.getMessage())
        );
    }

    /**
     * Client-facing summary. Deliberately omits any stack trace — the full
     * trace is logged server-side (with the throwable) and must never be
     * rendered into a response sent to the browser.
     */
    public String toDisplayString() {
        return "Generation ID: " + generationId + "\n"
            + "Timestamp: " + timestamp + "\n"
            + "Type: " + type + "\n"
            + "Stage: " + stage + "\n"
            + "Exception: " + exceptionClass + "\n"
            + "Exception message: " + exceptionMessage + "\n"
            + "Root cause: " + rootCauseClass + "\n"
            + "Root cause message: " + rootCauseMessage;
    }

    private static Throwable rootCause(Throwable throwable) {
        if (throwable == null) return null;
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "(unknown)" : value;
    }
}
