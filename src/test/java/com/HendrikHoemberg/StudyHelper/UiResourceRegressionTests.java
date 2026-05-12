package com.HendrikHoemberg.StudyHelper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UiResourceRegressionTests {

    @Test
    void applicationPropertiesCapsSpringAiRetryAttempts() throws IOException {
        String properties = file("src/main/resources/application.properties");

        assertThat(properties)
            .contains("spring.ai.retry.max-attempts=2");
    }

    @Test
    void aiFlashcardFormDropsDuplicateInFlightSubmissions() throws IOException {
        String template = resource("templates/fragments/flashcard-generator.html");

        assertThat(template)
            .contains("hx-post=\"/flashcards/generate\"")
            .contains("hx-sync=\"this:drop\"");
    }

    @Test
    void appJsShowsAiGenerationDialogForNonResponseHtmxFailures() throws IOException {
        String appJs = resource("static/js/app.js");

        assertThat(appJs)
            .contains("htmx:responseError")
            .contains("htmx:sendError")
            .contains("htmx:timeout")
            .contains("htmx:abort")
            .contains("handleAiGenerationFailure")
            .contains("extractAiGenerationDetails")
            .contains("technicalDetails");
    }

    @Test
    void aiErrorFragmentsExposeTechnicalDetailsForOptionalModalExpansion() throws IOException {
        String flashcardTemplate = resource("templates/fragments/flashcard-generator.html");
        String aiErrorTemplate = resource("templates/fragments/ai-generation-error.html");
        String dialogTemplate = resource("templates/fragments/dialog.html");

        assertThat(flashcardTemplate)
            .contains("data-ai-generation-details=\"true\"")
            .contains("generationDetails");
        assertThat(aiErrorTemplate)
            .contains("data-ai-generation-details=\"true\"")
            .contains("aiErrorDetails");
        assertThat(dialogTemplate)
            .contains("sh-dialog-details-toggle")
            .contains("sh-dialog-details");
    }

    private String file(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private String resource(String path) throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(inputStream).as("resource %s", path).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
