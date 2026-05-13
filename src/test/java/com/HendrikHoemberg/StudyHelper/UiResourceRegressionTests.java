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
            .contains("spring.ai.retry.max-attempts=1");
    }

    @Test
    void aiFlashcardFormDropsDuplicateInFlightSubmissions() throws IOException {
        String template = resource("templates/fragments/flashcard-generator.html");

        assertThat(template)
            .contains("hx-post=\"/flashcards/generate\"")
            .contains("hx-sync=\"this:drop\"")
            .contains("name=\"additionalInstructions\"")
            .contains("ai-flashcard-submit-with-instructions");
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
            .contains("technicalDetails")
            .contains("runAiPreflight")
            .contains("openInstructionDialog")
            .contains("isTextareaPrompt && document.activeElement === textarea")
            .contains("typeof instructions === 'string' ? instructions.trim() : ''");
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
            .contains("sh-dialog-details")
            .contains("sh-dialog-textarea")
            .contains("maxlength=\"1000\"");
    }

    @Test
    void textareaDialogSupportsKeyboardSubmitWithoutHijackingPlainEnter() throws IOException {
        String appJs = resource("static/js/app.js");

        assertThat(appJs)
            .contains("isTextareaPrompt && document.activeElement === textarea && !(e.ctrlKey || e.metaKey)")
            .contains("shDialogResolve(isTextareaPrompt ? textarea.value : (isPrompt ? input.value : true))");
    }

    @Test
    void studySetupIncludesInstructionSubmitHookForQuizAndExam() throws IOException {
        String template = resource("templates/fragments/study-setup.html");
        String wizardJs = resource("static/js/study-wizard.js");

        assertThat(template)
            .contains("name=\"additionalInstructions\"")
            .contains("wizard-btn-submit-with-instructions");
        assertThat(wizardJs)
            .contains("typeof instructions === 'string' ? instructions.trim() : ''")
            .contains("htmx:afterRequest")
            .contains("form.sh-study-setup-card");
    }

    @Test
    void studyWizardPrimarySubmitUsesStableTextSpanForDynamicLabels() throws IOException {
        String template = resource("templates/fragments/study-setup.html");
        String wizardJs = resource("static/js/study-wizard.js");

        assertThat(template)
            .contains("id=\"wizard-btn-submit\"")
            .contains("class=\"sh-btn-text\">Start</span>");
        assertThat(wizardJs)
            .contains("submitBtn.querySelector('.sh-btn-text')")
            .doesNotContain("submitBtn.childNodes[1]");
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
