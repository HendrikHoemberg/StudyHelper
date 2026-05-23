package com.HendrikHoemberg.StudyHelper.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class I18nConfigTests {

    @Autowired
    private MessageSource messageSource;

    @Test
    void resolvesEnglishKey() {
        assertThat(messageSource.getMessage("app.brand", null, Locale.ENGLISH))
                .isEqualTo("StudyHelper");
    }

    @Test
    void resolvesGermanKey() {
        assertThat(messageSource.getMessage("app.brand", null, Locale.GERMAN))
                .isEqualTo("StudyHelper");
    }

    @Test
    void missingKeyReturnsKeyAsFallback() {
        assertThat(messageSource.getMessage("nonexistent.key", null, Locale.ENGLISH))
                .isEqualTo("nonexistent.key");
    }
}
