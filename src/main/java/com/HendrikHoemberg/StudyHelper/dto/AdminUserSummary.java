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
    private static final long MiB = 1024L * 1024L;
    private static final long GiB = 1024L * MiB;

    /** Storage quota expressed in whole GiB — used as default value for the quota input. */
    public long storageQuotaGib() {
        return storageQuotaBytes / GiB;
    }

    /** 0–100 percentage of storage used (capped at 100). */
    public int storagePercent() {
        if (storageQuotaBytes <= 0) return 100;
        return (int) Math.min(100, storageUsedBytes * 100L / storageQuotaBytes);
    }

    /** Human-readable storage used (e.g. "512.0 MB" or "1.2 GB"). */
    public String formattedStorageUsed() {
        return formatBytes(storageUsedBytes);
    }

    /** Human-readable storage quota (e.g. "1.0 GB"). */
    public String formattedStorageQuota() {
        return formatBytes(storageQuotaBytes);
    }

    private static String formatBytes(long bytes) {
        if (bytes >= GiB) return String.format("%.1f GB", (double) bytes / GiB);
        if (bytes >= MiB) return String.format("%.0f MB", (double) bytes / MiB);
        return bytes + " B";
    }
}
