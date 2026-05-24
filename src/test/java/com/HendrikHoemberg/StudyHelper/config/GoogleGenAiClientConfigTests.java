package com.HendrikHoemberg.StudyHelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleGenAiClientConfigTests {

    @Test
    void providerTimeoutConstant_isThreeMinutes() {
        assertThat(GoogleGenAiClientConfig.PROVIDER_TIMEOUT_MILLIS).isEqualTo(180_000);
    }
}
