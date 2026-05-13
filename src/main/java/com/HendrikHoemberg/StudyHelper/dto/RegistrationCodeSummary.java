package com.HendrikHoemberg.StudyHelper.dto;

import java.time.Instant;

public record RegistrationCodeSummary(
    Long id,
    Instant createdAt,
    Instant expiresAt,
    Instant usedAt,
    Instant revokedAt,
    String createdBy,
    String usedBy,
    String status
) {
}
