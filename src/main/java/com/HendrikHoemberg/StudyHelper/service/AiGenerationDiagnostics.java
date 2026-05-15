package com.HendrikHoemberg.StudyHelper.service;

import java.io.PrintWriter;
import java.io.StringWriter;
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
    String rootCauseMessage,
    String stackTrace
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
            root == null ? "(none)" : valueOrUnknown(root.getMessage()),
            stackTrace(throwable)
        );
    }

    /**
     * Client-facing summary. Deliberately excludes the stack trace: the full
     * trace is kept in {@link #stackTrace()} for server-side logging only and
     * must never be rendered into a response sent to the browser.
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

    private static String stackTrace(Throwable throwable) {
        if (throwable == null) return "(none)";
        StringWriter out = new StringWriter();
        throwable.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "(unknown)" : value;
    }
}
