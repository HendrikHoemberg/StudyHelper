package com.HendrikHoemberg.StudyHelper.dto;

public record UserQuotaSummary(
    long storageUsedBytes,
    long storageQuotaBytes,
    int aiUsedToday,
    int aiDailyLimit
) {
    private static final long MiB = 1024L * 1024L;
    private static final long GiB = 1024L * MiB;

    public int storagePercent() {
        if (storageQuotaBytes <= 0) return 100;
        return (int) Math.min(100, storageUsedBytes * 100L / storageQuotaBytes);
    }

    public int aiPercent() {
        if (aiDailyLimit <= 0) return 100;
        return Math.min(100, aiUsedToday * 100 / aiDailyLimit);
    }

    public String formattedStorageUsed() {
        return formatBytes(storageUsedBytes);
    }

    public String formattedStorageQuota() {
        return formatBytes(storageQuotaBytes);
    }

    public String storageLabel() {
        return formattedStorageUsed() + " / " + formattedStorageQuota();
    }

    public String aiLabel() {
        return aiUsedToday + " / " + aiDailyLimit;
    }

    public String storageStateClass() {
        return stateClass(storagePercent());
    }

    public String aiStateClass() {
        return stateClass(aiPercent());
    }

    private static String stateClass(int percent) {
        if (percent >= 90) return "is-danger";
        if (percent >= 75) return "is-warn";
        return "";
    }

    private static String formatBytes(long bytes) {
        if (bytes >= GiB) return String.format("%.1f GB", (double) bytes / GiB);
        if (bytes >= MiB) return String.format("%.0f MB", (double) bytes / MiB);
        return bytes + " B";
    }
}
