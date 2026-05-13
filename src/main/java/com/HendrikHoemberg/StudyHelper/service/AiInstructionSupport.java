package com.HendrikHoemberg.StudyHelper.service;

final class AiInstructionSupport {

    static final int MAX_LENGTH = 1_000;

    private AiInstructionSupport() {
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.strip();
        if (trimmed.isBlank()) {
            return "";
        }
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }

    static String section(String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) {
            return "";
        }
        return "\n\nUSER INSTRUCTIONS:\n"
            + "The user provided these additional preferences for this generation. "
            + "Follow them when they are compatible with the rules above, the source material, "
            + "and the required JSON schema:\n"
            + normalized
            + "\n";
    }
}
