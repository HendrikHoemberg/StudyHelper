package com.HendrikHoemberg.StudyHelper.dto;

import java.time.Instant;

public record AdminUserSummary(
    Long id,
    String username,
    String role,
    boolean enabled,
    long storageUsedBytes,
    long storageQuotaBytes,
    int aiUsedToday,
    int aiDailyLimit,
    Instant createdAt
) {
}
