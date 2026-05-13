package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserQuotaSummaryTests {

    @Test
    void formatsStorageAndAiQuotaForNavbar() {
        long gib = 1024L * 1024L * 1024L;
        UserQuotaSummary quota = new UserQuotaSummary(gib / 2, gib, 7, 10);

        assertThat(quota.formattedStorageUsed()).isEqualTo("512 MB");
        assertThat(quota.formattedStorageQuota()).isEqualTo("1.0 GB");
        assertThat(quota.storagePercent()).isEqualTo(50);
        assertThat(quota.aiPercent()).isEqualTo(70);
        assertThat(quota.aiLabel()).isEqualTo("7 / 10");
    }

    @Test
    void capsPercentagesAtOneHundred() {
        UserQuotaSummary quota = new UserQuotaSummary(200, 100, 12, 10);

        assertThat(quota.storagePercent()).isEqualTo(100);
        assertThat(quota.aiPercent()).isEqualTo(100);
    }

    @Test
    void formatsKilobytes() {
        UserQuotaSummary quota = new UserQuotaSummary(512 * 1024, 1024 * 1024, 0, 10);
        assertThat(quota.formattedStorageUsed()).isEqualTo("512 KB");
        assertThat(quota.formattedStorageQuota()).isEqualTo("1 MB");
    }
}
