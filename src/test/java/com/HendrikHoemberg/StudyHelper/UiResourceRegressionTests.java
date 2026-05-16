package com.HendrikHoemberg.StudyHelper;

import com.HendrikHoemberg.StudyHelper.config.AppDefaults;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UiResourceRegressionTests {

    @Test
    void applicationPropertiesDisablesSpringAiRetries() throws IOException {
        String properties = file("src/main/resources/application.properties");

        assertThat(properties)
            .contains("spring.ai.retry.max-attempts=0");
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
    void deckViewShowsAddCardTileBeforeExistingFlashcards() throws IOException {
        String template = resource("templates/fragments/deck.html");

        assertThat(template.indexOf("sh-flashcard sh-flashcard-add"))
            .isLessThan(template.indexOf("th:each=\"card : ${flashcards}\""));
    }

    @Test
    void pdfFileThumbnailsFillDeckCoverLikeImageThumbnails() throws IOException {
        String styles = resource("static/css/styles.css");

        assertThat(styles)
            .contains(".sh-deck-cover-img")
            .contains("object-fit: cover")
            .contains(".sh-pdf-cover-img {\n    object-fit: cover;");
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

    @Test
    void studyWizardDoesNotRenderStandalonePageHeader() throws IOException {
        String template = resource("templates/fragments/study-setup.html");

        assertThat(template)
            .doesNotContain("class=\"sh-page-header\"")
            .doesNotContain("Start Studying")
            .doesNotContain("Back to Dashboard");
    }

    @Test
    void studyWizardSourceTreeIsScrollableAndCollapsible() throws IOException {
        String template = resource("templates/fragments/wizard-source-picker.html");
        String styles = resource("static/css/styles.css");
        String wizardJs = resource("static/js/study-wizard.js");

        assertThat(template)
            .contains("vb-source-scroll")
            .contains("vb-folder-toggle")
            .contains("aria-expanded=\"false\"")
            .contains("vb-folder-content")
            .contains("hasSelectionInGroup");
        assertThat(styles)
            .contains(".vb-source-scroll")
            .contains("overflow-y: auto")
            .contains(".vb-group.is-collapsed:not(.is-search-expanded) > .vb-folder-content")
            .contains(".vb-folder-toggle iconify-icon");
        assertThat(wizardJs)
            .contains("initSourceFolderTree")
            .contains("vb-folder-toggle")
            .contains("aria-expanded")
            .contains("openFoldersWithSelection");
    }

    @Test
    void studyWizardSeparatesQuizAndExamSettingsIntoDedicatedStepperSteps() throws IOException {
        String quizTemplate = resource("templates/fragments/wizard-quiz.html");
        String examTemplate = resource("templates/fragments/wizard-exam.html");
        String sourceTemplate = resource("templates/fragments/wizard-source-picker.html");
        String styles = resource("static/css/styles.css");
        String wizardJs = resource("static/js/study-wizard.js");

        assertThat(wizardJs)
            .contains("QUIZ:       [\"Mode\", \"Format\", \"Settings\", \"Sources\"]")
            .contains("EXAM:       [\"Mode\", \"Depth\", \"Settings\", \"Sources\", \"Layout\"]")
            .contains("sourceStep()")
            .contains("initCustomSteppers")
            .contains("data-stepper-action");

        assertThat(quizTemplate)
            .contains("data-step=\"3\" data-mode=\"QUIZ\"")
            .contains("Quiz Settings")
            .contains("class=\"sh-number-stepper\"")
            .contains("name=\"questionCount\"");

        assertThat(examTemplate)
            .contains("data-step=\"3\" data-mode=\"EXAM\"")
            .contains("Exam Settings")
            .contains("class=\"sh-number-stepper\"")
            .contains("name=\"count\"")
            .contains("name=\"timerMinutes\"");

        assertThat(sourceTemplate)
            .contains("data-source-step=\"true\"");

        assertThat(styles)
            .contains(".sh-number-stepper")
            .contains(".sh-stepper-btn")
            .contains("appearance: textfield");
    }

    @Test
    void studyWizardAdvancesImmediatelyFromEveryStepTwoModeChoice() throws IOException {
        String flashcardsTemplate = resource("templates/fragments/wizard-flashcards.html");
        String quizTemplate = resource("templates/fragments/wizard-quiz.html");
        String examTemplate = resource("templates/fragments/wizard-exam.html");
        String wizardJs = resource("static/js/study-wizard.js");

        assertThat(flashcardsTemplate)
            .contains("name=\"sessionMode\"");
        assertThat(quizTemplate)
            .contains("name=\"quizQuestionMode\"");
        assertThat(examTemplate)
            .contains("name=\"questionSize\"");
        assertThat(wizardJs)
            .contains("initInstantStepTwoChoices")
            .contains("input[name=\"sessionMode\"], input[name=\"quizQuestionMode\"], input[name=\"questionSize\"]")
            .contains("input.addEventListener('change'")
            .contains("if (input.disabled || !input.checked || currentStep !== 2) return;")
            .doesNotContain("setTimeout(() => wizardNext(), 0)");
    }

    @Test
    void studyWizardStepTwoStartsUnselectedAndDoesNotShowNextButton() throws IOException {
        String flashcardsTemplate = resource("templates/fragments/wizard-flashcards.html");
        String quizTemplate = resource("templates/fragments/wizard-quiz.html");
        String examTemplate = resource("templates/fragments/wizard-exam.html");
        String wizardJs = resource("static/js/study-wizard.js");

        assertThat(flashcardsTemplate)
            .doesNotContain("name=\"sessionMode\" value=\"DECK_BY_DECK\" checked")
            .doesNotContain("name=\"sessionMode\" value=\"SHUFFLED\" checked");
        assertThat(quizTemplate)
            .doesNotContain("name=\"quizQuestionMode\" value=\"MCQ_ONLY\" checked")
            .doesNotContain("name=\"quizQuestionMode\" value=\"TF_ONLY\" checked")
            .doesNotContain("name=\"quizQuestionMode\" value=\"MIXED\" checked");
        assertThat(examTemplate)
            .doesNotContain("name=\"questionSize\" value=\"SHORT\" checked")
            .doesNotContain("name=\"questionSize\" value=\"MEDIUM\" checked")
            .doesNotContain("name=\"questionSize\" value=\"LONG\" checked")
            .doesNotContain("name=\"questionSize\" value=\"MIXED\" checked");
        assertThat(wizardJs)
            .contains("currentStep === 2")
            .contains("if (nextBtn) nextBtn.style.display = (isLast || currentStep === 1 || currentStep === 2) ? 'none' : '';");
    }

    @Test
    void studyWizardMobileFooterWrapsActionsAndConstrainsSourceTree() throws IOException {
        String styles = resource("static/css/styles.css");

        assertThat(styles)
            .contains("position: static")
            .contains(".sh-wizard-footer > div")
            .contains("margin-left: 0 !important")
            .contains(".sh-wizard-footer #wizard-btn-submit-with-instructions")
            .contains("flex: 1 1 12rem")
            .contains("max-height: min(44vh, 28rem)");
    }

    @Test
    void studyWizardDesktopHidesSidebarAndCentersConstrainedSetupPanels() throws IOException {
        String styles = resource("static/css/styles.css");

        assertThat(styles)
            .containsPattern("(?s)@media \\(min-width: 769px\\).*\\.sh-explorer-shell:has\\(\\.sh-study-setup-card\\) \\{\\s*grid-template-columns: 1fr;")
            .contains(".sh-explorer-shell:has(.sh-study-setup-card) .sh-dashboard-sidebar")
            .contains("max-width: min(70%, 80rem)")
            .contains(".sh-wizard-panel[data-mode=\"QUIZ\"] .sh-settings-grid")
            .contains(".sh-wizard-panel[data-mode=\"EXAM\"] .sh-settings-grid")
            .contains("margin-inline: auto");
    }

    @Test
    void liveQuizAndExamSessionsAreConstrainedOnDesktop() throws IOException {
        String styles = resource("static/css/styles.css");

        assertThat(styles)
            .containsPattern("(?s)@media \\(min-width: 769px\\).*\\.sh-explorer-detail:has\\(#quiz-session-content\\)")
            .contains(".sh-explorer-detail:has(#exam-runtime-wrapper)")
            .contains("max-width: 80rem")
            .contains("margin-inline: auto");
    }

    @Test
    void mobileDashboardRemovesOuterMainContentPadding() throws IOException {
        String styles = resource("static/css/styles.css");

        assertThat(styles)
            .containsPattern("(?s)@media \\(max-width: 768px\\).*\\.app-main-content \\{[^}]*padding: 0;[^}]*\\}")
            .containsPattern("(?s)@media \\(max-width: 768px\\).*\\.sh-dashboard-shell \\{[^}]*padding: 0.75rem;[^}]*\\}");
    }

    @Test
    void appExposesInstallablePwaMetadataWithoutOfflineCaching() throws IOException {
        String layout = resource("templates/fragments/layout.html");
        String appJs = resource("static/js/app.js");
        String manifest = resource("static/manifest.webmanifest");
        String serviceWorker = resource("static/service-worker.js");
        String favicon = resource("static/icons/favicon.svg");
        String securityConfig = file("src/main/java/com/HendrikHoemberg/StudyHelper/config/SecurityConfig.java");

        assertThat(layout)
            .contains("<link rel=\"manifest\" href=\"/manifest.webmanifest\">")
            .contains("<link rel=\"icon\" href=\"/favicon.ico\" sizes=\"any\">")
            .contains("<link rel=\"icon\" type=\"image/png\" sizes=\"192x192\" href=\"/icons/icon-192.png\">")
            .contains("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/icons/favicon.svg\">")
            .contains("<meta name=\"theme-color\" content=\"#0f766e\">")
            .contains("<meta name=\"mobile-web-app-capable\" content=\"yes\">")
            .contains("<meta name=\"apple-mobile-web-app-capable\" content=\"yes\">")
            .contains("<link rel=\"apple-touch-icon\" href=\"/icons/icon-192.png\">");
        assertThat(layout.indexOf("href=\"/icons/favicon.svg\""))
            .isGreaterThan(layout.indexOf("href=\"/icons/icon-192.png\""));
        assertThat(appJs)
            .contains("serviceWorker.register('/service-worker.js')");
        assertThat(manifest)
            .contains("\"name\": \"StudyHelper\"")
            .contains("\"short_name\": \"StudyHelper\"")
            .contains("\"start_url\": \"/dashboard\"")
            .contains("\"display\": \"standalone\"")
            .contains("\"icons\"")
            .contains("\"/icons/icon-192.png\"")
            .contains("\"/icons/icon-512.png\"");
        assertThat(serviceWorker)
            .contains("self.addEventListener('install'")
            .contains("self.skipWaiting()")
            .doesNotContain("caches.open")
            .doesNotContain("fetch");
        assertThat(favicon)
            .contains("<rect width=\"64\" height=\"64\" fill=\"#0f766e\"")
            .doesNotContain("rx=");
        assertThat(securityConfig)
            .contains(".requestMatchers(\"/login\", \"/register\", \"/css/**\", \"/js/**\", \"/manifest.webmanifest\", \"/service-worker.js\", \"/favicon.ico\", \"/icons/**\").permitAll()");
    }

    @Test
    void authPagesUseStudyHelperLogoInsteadOfInlineBookIcon() throws IOException {
        String login = resource("templates/login.html");
        String register = resource("templates/register.html");
        String styles = resource("static/css/styles.css");

        assertThat(login)
            .contains("<img src=\"/icons/icon-192.png\" alt=\"\" class=\"sh-login-logo\">")
            .doesNotContain("<path d=\"M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z\"/>");
        assertThat(register)
            .contains("<img src=\"/icons/icon-192.png\" alt=\"\" class=\"sh-login-logo\">")
            .doesNotContain("<path d=\"M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z\"/>");
        assertThat(styles)
            .contains(".sh-login-logo")
            .contains("object-fit: cover");
    }

    @Test
    void appPrimaryPaletteMatchesTealPwaIdentity() throws IOException {
        String styles = resource("static/css/styles.css");
        String manifest = resource("static/manifest.webmanifest");

        assertThat(styles)
            .contains("--accent: #0f766e;")
            .contains("--accent-hover: #134e4a;")
            .contains("--accent-light: #ccfbf1;")
            .contains("--accent-glow: rgba(15, 118, 110, 0.25);")
            .contains("--accent-light: rgba(15, 118, 110, 0.15);");
        assertThat(manifest)
            .contains("\"theme_color\": \"#0f766e\"");
    }

    @Test
    void newFolderAndDeckFormsDefaultToPrimaryTeal() throws IOException {
        String folderForm = resource("templates/fragments/folder-form.html");
        String deckForm = resource("templates/fragments/deck-form.html");
        String appJs = resource("static/js/app.js");
        String colorPickerJs = resource("static/js/color-picker.js");
        String colorPickerTemplate = resource("templates/fragments/color-picker.html");

        assertThat(AppDefaults.DEFAULT_COLOR_HEX).isEqualTo("#0f766e");
        assertThat(folderForm)
            .contains("${colorHex ?: defaultColorHex}");
        assertThat(deckForm)
            .contains("${colorHex ?: defaultColorHex}");
        assertThat(appJs)
            .contains("|| '#0f766e'");
        assertThat(colorPickerJs)
            .contains("'#0f766e'")
            .doesNotContain("'#6366f1'");
        assertThat(colorPickerTemplate)
            .contains("placeholder=\"#0f766e\"");
    }

    @Test
    void topnavBrandUsesStudyHelperLogoAsset() throws IOException {
        String layout = resource("templates/fragments/layout.html");
        String styles = resource("static/css/styles.css");

        assertThat(layout)
            .contains("<img src=\"/icons/icon-192.png\" alt=\"\" class=\"topnav-brand-logo\">")
            .doesNotContain("<span class=\"topnav-brand-icon\">\n                <iconify-icon icon=\"lucide:book-open\"></iconify-icon>\n            </span>");
        assertThat(styles)
            .contains(".topnav-brand-logo")
            .contains("object-fit: cover");
    }

    @Test
    void flashcardImageInputsAcceptPastedClipboardImages() throws IOException {
        String template = resource("templates/fragments/flashcard-form.html");

        assertThat(template)
            .contains("document.addEventListener('paste', handleImagePaste)")
            .contains("clipboardData.items")
            .contains("item.type.startsWith('image/')")
            .contains("selectPasteTargetSide")
            .contains("var file = new File([blob], filename, { type: blob.type || 'image/png' })")
            .contains("input.dispatchEvent(new Event('change', { bubbles: true }))")
            .contains("showPreview(file)");
    }

    @Test
    void flashcardAddImageOpensBrowseOrPasteChooser() throws IOException {
        String template = resource("templates/fragments/flashcard-form.html");

        assertThat(template)
            .contains("data-image-source-trigger=\"front\"")
            .contains("data-image-source-trigger=\"back\"")
            .contains("id=\"flashcard-image-source-modal\"")
            .contains("data-image-source-browse")
            .contains("data-image-source-paste")
            .contains("openImageSourceModal(side)")
            .contains("pasteImageFromClipboard(selectedImageSourceSide)")
            .contains("navigator.clipboard.read()")
            .contains("Paste image")
            .contains("Browse files");
    }

    @Test
    void fileUploadModalUsesModernChooserDesign() throws IOException {
        String template = resource("templates/fragments/file-form.html");
        String styles = resource("static/css/styles.css");

        assertThat(template)
            .contains("class=\"sh-upload-file-input\"")
            .contains("class=\"sh-upload-chooser\"")
            .contains("lucide:upload-cloud")
            .contains("Choose file")
            .contains("id=\"upload-file-name\"")
            .contains("No file selected")
            .contains("class=\"sh-upload-selected\"")
            .contains("lucide:check-circle")
            .contains("id=\"upload-submit\"")
            .contains("<button type=\"submit\" class=\"sh-btn sh-btn-primary\" id=\"upload-submit\" disabled>Upload</button>")
            .contains("chooser.classList.toggle('is-selected', hasFile);")
            .contains("chooserText.textContent = hasFile ? 'Change file' : 'Choose file';")
            .contains("submitButton.disabled = !hasFile;")
            .contains("Maximum file size: 100 MB")
            .contains("selectedFileName.textContent = fileInput.files[0] ? fileInput.files[0].name : 'No file selected';")
            .doesNotContain("Ready to upload")
            .doesNotContain("style=\"color:var(--text-muted); font-size:0.8125rem; margin-top:0.5rem; margin-bottom:0;\"");

        assertThat(styles)
            .contains(".sh-upload-chooser")
            .contains(".sh-upload-chooser.is-selected")
            .contains(".sh-upload-file-input")
            .contains(".sh-upload-selected")
            .contains(".sh-upload-file-name")
            .contains(".sh-upload-limit");
    }

    @Test
    void pdfJsLibraryIsVendoredLocally() throws IOException {
        String lib = resource("static/js/lib/pdfjs/pdf.min.mjs");
        String worker = resource("static/js/lib/pdfjs/pdf.worker.min.mjs");

        assertThat(lib.length()).isGreaterThan(50000);
        assertThat(worker.length()).isGreaterThan(50000);
        assertThat(lib).contains("pdfjsVersion = 5.7.284");
        assertThat(worker).contains("pdfjsVersion = 5.7.284");
    }

    @Test
    void pdfViewerComponentRendersOverlayAndModule() throws IOException {
        String html = resource("templates/fragments/pdf-viewer.html");
        String css = resource("static/css/pdf-viewer.css");
        String js = resource("static/js/pdf-viewer.js");

        assertThat(html)
            .contains("th:fragment=\"modal\"")
            .contains("id=\"sh-pv-modal\"")
            .contains("id=\"sh-pv-pages\"")
            .contains("id=\"sh-pv-split-btn\"");
        assertThat(css).contains(".sh-pv-modal");
        assertThat(js)
            .contains("import * as pdfjsLib from '/js/lib/pdfjs/pdf.min.mjs'")
            .contains("GlobalWorkerOptions.workerSrc")
            .contains("sh-pdf-viewer-trigger")
            .contains("window.PdfViewer");
    }

    @Test
    void pdfSplitterComponentRendersModalAndModule() throws IOException {
        String html = resource("templates/fragments/pdf-splitter.html");
        String css = resource("static/css/pdf-splitter.css");
        String js = resource("static/js/pdf-splitter.js");

        assertThat(html)
            .contains("th:fragment=\"modal\"")
            .contains("id=\"sh-ps-modal\"")
            .contains("id=\"sh-ps-pages\"")
            .contains("id=\"sh-ps-parts\"")
            .contains("id=\"sh-ps-save-btn\"");
        assertThat(css).contains(".sh-ps-modal");
        assertThat(js)
            .contains("import * as pdfjsLib from '/js/lib/pdfjs/pdf.min.mjs'")
            .contains("data-split-file-id")
            .contains("window.PdfSplitter");
    }

    @Test
    void layoutWiresPdfViewerAndSplitterAssets() throws IOException {
        String layout = resource("templates/fragments/layout.html");

        assertThat(layout)
            .contains("<link href=\"/css/pdf-viewer.css\" rel=\"stylesheet\">")
            .contains("<link href=\"/css/pdf-splitter.css\" rel=\"stylesheet\">")
            .contains("<script type=\"module\" src=\"/js/pdf-viewer.js\"></script>")
            .contains("<script type=\"module\" src=\"/js/pdf-splitter.js\"></script>")
            .contains("~{fragments/pdf-viewer :: modal}")
            .contains("~{fragments/pdf-splitter :: modal}");
    }

    @Test
    void pdfFileTilesOpenTheInAppViewer() throws IOException {
        String tabFiles = resource("templates/fragments/tab-files.html");
        String explorer = resource("templates/fragments/explorer.html");

        assertThat(tabFiles)
            .contains("'sh-pdf-viewer-trigger'")
            .contains("th:data-file-id=\"${file.id}\"")
            .contains("th:data-file-url=\"@{/files/{id}/view(id=${file.id})}\"")
            .contains("th:data-file-name=\"${file.originalFilename}\"");
        assertThat(explorer)
            .contains("'sh-pdf-viewer-trigger'")
            .contains("th:data-file-id=\"${file.id}\"")
            .contains("th:data-file-url=\"@{/files/{id}/view(id=${file.id})}\"")
            .contains("th:data-file-name=\"${file.originalFilename}\"");
    }

    @Test
    void fileEditModalOffersPdfSplitterForPdfFiles() throws IOException {
        String modal = resource("templates/fragments/file-edit-modal.html");

        assertThat(modal)
            .contains("data-split-file-id=${file.id}")
            .contains("data-split-url=@{/files/{id}/view(id=${file.id})}")
            .contains("data-split-filename=${file.originalFilename}")
            .contains("Open PDF Splitter");
    }

    @Test
    void imageEditorUsesViewportArtboardModelWithRetinaScaling() throws IOException {
        String js = resource("static/js/image-editor.js");

        assertThat(js)
            .contains("enableRetinaScaling")
            .contains("_artboard")
            .contains("function _resizeViewport()")
            .contains("function _fitArtboard()");
    }

    @Test
    void imageEditorPaintsArtboardPageAndClipsToIt() throws IOException {
        String js = resource("static/js/image-editor.js");

        assertThat(js)
            .contains("before:render")
            .contains("function _drawArtboardPage()")
            .contains("function _syncClipPath()")
            .contains("absolutePositioned");
    }

    @Test
    void imageEditorExportsTheArtboardRectAtNativeResolution() throws IOException {
        String js = resource("static/js/image-editor.js");

        assertThat(js)
            .contains("_exporting = true")
            .contains("toDataURL")
            .contains("_artboard.w");
    }

    @Test
    void imageEditorRefitsOnResizeAndScopesPanListeners() throws IOException {
        String js = resource("static/js/image-editor.js");

        assertThat(js)
            .contains("function _onWindowResize()")
            .contains("window.addEventListener('resize', _onWindowResize)")
            .contains("function _panMove(")
            .contains("function _panUp(");
    }

    @Test
    void imageEditorHistorySnapshotsIncludeTheArtboard() throws IOException {
        String js = resource("static/js/image-editor.js");

        assertThat(js)
            .contains("artboard: { x: _artboard.x")
            .contains("snap.artboard");
    }

    @Test
    void imageEditorHasASelectTool() throws IOException {
        String js   = resource("static/js/image-editor.js");
        String html = resource("templates/fragments/image-editor.html");

        assertThat(html).contains("data-tool=\"select\"");
        assertThat(js)
            .contains("name === 'select'")
            .contains("_deleteActiveObjects");
    }

    @Test
    void imageEditorHasDraggableArtboardEdgeHandles() throws IOException {
        String js   = resource("static/js/image-editor.js");
        String html = resource("templates/fragments/image-editor.html");
        String css  = resource("static/css/image-editor.css");

        assertThat(html).contains("id=\"sh-ie-handles\"");
        assertThat(css).contains(".sh-ie-handle");
        assertThat(js)
            .contains("function _updateHandles()")
            .contains("function _resizeArtboard(")
            .contains("MIN_ARTBOARD");
    }

    @Test
    void imageEditorHasAContextualOptionsBar() throws IOException {
        String html = resource("templates/fragments/image-editor.html");
        String css  = resource("static/css/image-editor.css");
        String js   = resource("static/js/image-editor.js");

        assertThat(html).contains("id=\"sh-ie-options\"");
        assertThat(css).contains(".sh-ie-options");
        assertThat(js).contains("function _refreshOptionsBar(");
    }

    @Test
    void imageEditorShapesSupportAFillColor() throws IOException {
        String html = resource("templates/fragments/image-editor.html");
        String js   = resource("static/js/image-editor.js");

        assertThat(html).contains("id=\"sh-ie-fill-btn\"");
        assertThat(js)
            .contains("_fillColor")
            .contains("function _setFill(");
    }

    @Test
    void imageEditorEraserErasesToTransparency() throws IOException {
        String js = resource("static/js/image-editor.js");

        assertThat(js).contains("globalCompositeOperation = 'destination-out'");
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
