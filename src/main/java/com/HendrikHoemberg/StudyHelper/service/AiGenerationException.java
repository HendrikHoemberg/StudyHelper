package com.HendrikHoemberg.StudyHelper.service;

public class AiGenerationException extends IllegalStateException {

    private final AiGenerationDiagnostics diagnostics;

    public AiGenerationException(String message, AiGenerationDiagnostics diagnostics) {
        super(message);
        this.diagnostics = diagnostics;
    }

    public AiGenerationException(String message, AiGenerationDiagnostics diagnostics, Throwable cause) {
        super(message, cause);
        this.diagnostics = diagnostics;
    }

    public AiGenerationDiagnostics diagnostics() {
        return diagnostics;
    }
}
