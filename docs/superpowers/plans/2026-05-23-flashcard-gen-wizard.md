# Flashcard Generator 2-Step Wizard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the AI Flashcard generator page into a 2-step wizard (Step 1: Sources, Step 2: Destination), reusing existing wizard styles and gating Next until at least one PDF is selected.

**Architecture:** Purely view + JS change. The Thymeleaf fragment `flashcard-generator.html` is restructured so its two existing sections become `.sh-wizard-panel` blocks inside the same `<form>`. A new focused JS controller `flashcard-gen-wizard.js` toggles panel visibility, manages the Back/Next/Submit footer, and gates Next on PDF selection. Server endpoints (`/flashcards/generate`, `/flashcards/generate/preflight`) and the controller class are unchanged.

**Tech Stack:** Thymeleaf, vanilla JS, HTMX, JUnit + AssertJ for regression tests. Reuses existing CSS classes `sh-wizard-panel`, `sh-wizard-steps`, `sh-wizard-footer`.

---

## File Structure

- **Modify** `src/main/resources/templates/fragments/flashcard-generator.html` — split the form body into two `.sh-wizard-panel` blocks, add `#sh-wizard-steps` indicator with two server-rendered pills, restructure the footer with Back/Next buttons in addition to the existing Generate / Generate-with-instructions buttons.
- **Create** `src/main/resources/static/js/flashcard-gen-wizard.js` — small controller (~120 lines) that initialises on `DOMContentLoaded` + `htmx:load`, toggles `.is-active` on panels, updates step-pill state, shows/hides footer buttons, and watches PDF checkboxes to enable/disable the Next button. If a server-rendered error banner is present, opens on step 2.
- **Modify** `src/main/resources/templates/fragments/layout.html` — add `<script src="/js/flashcard-gen-wizard.js" defer></script>` next to `study-wizard.js`.
- **Modify** `src/main/resources/messages.properties` and `src/main/resources/messages_de.properties` — add `flashcard-gen.step.sources`, `flashcard-gen.step.destination`, `flashcard-gen.where-to-save`, `flashcard-gen.back`, `flashcard-gen.next`.
- **Modify** `src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java` — add a test asserting the new wizard DOM, JS, and script registration.

Each task below produces a working, committable change.

---

## Task 1: Add i18n keys for the wizard

**Files:**
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_de.properties`

- [ ] **Step 1: Append English keys**

Open `src/main/resources/messages.properties`. Find the existing `flashcard-gen.card-count-hint=...` line (around line 544) and append the following block immediately after it:

```properties
flashcard-gen.step.sources=Sources
flashcard-gen.step.destination=Destination
flashcard-gen.where-to-save=Where to save
flashcard-gen.back=Back
flashcard-gen.next=Next
```

- [ ] **Step 2: Append German keys**

Open `src/main/resources/messages_de.properties`. Find the corresponding `flashcard-gen.card-count-hint=...` line and append:

```properties
flashcard-gen.step.sources=Quellen
flashcard-gen.step.destination=Speicherort
flashcard-gen.where-to-save=Speicherort
flashcard-gen.back=Zurück
flashcard-gen.next=Weiter
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/messages.properties src/main/resources/messages_de.properties
git commit -m "i18n: add flashcard generator wizard step and nav keys"
```

---

## Task 2: Add failing regression test for wizard DOM

**Files:**
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java`

- [ ] **Step 1: Add a new test method**

Open `src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java`. After the existing `aiFlashcardFormDropsDuplicateInFlightSubmissions` test (ends around line 52), insert this new test method:

```java
    @Test
    void aiFlashcardGeneratorIsA2StepWizard() throws IOException {
        String template = resource("templates/fragments/flashcard-generator.html");
        String layout = resource("templates/fragments/layout.html");
        String wizardJs = resource("static/js/flashcard-gen-wizard.js");

        // Step indicator + two panels live in the template.
        assertThat(template)
            .contains("id=\"sh-wizard-steps\"")
            .contains("sh-wizard-panel")
            .contains("data-step=\"1\"")
            .contains("data-step=\"2\"")
            .contains("id=\"ai-flashcard-form\"")
            .contains("id=\"ai-flashcard-back\"")
            .contains("id=\"ai-flashcard-next\"")
            .contains("#{flashcard-gen.step.sources}")
            .contains("#{flashcard-gen.step.destination}")
            .contains("#{flashcard-gen.back}")
            .contains("#{flashcard-gen.next}");

        // Layout loads the new controller.
        assertThat(layout).contains("/js/flashcard-gen-wizard.js");

        // Controller toggles panels and gates Next on PDF selection.
        assertThat(wizardJs)
            .contains("ai-flashcard-form")
            .contains("data-step")
            .contains("is-active")
            .contains("input[name=\"fileId\"]")
            .contains("ai-flashcard-back")
            .contains("ai-flashcard-next")
            .contains("data-ai-generation-error");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./mvnw -q -Dtest=UiResourceRegressionTests#aiFlashcardGeneratorIsA2StepWizard test
```

Expected: FAIL. The template doesn't yet have `sh-wizard-panel` or `data-step` attributes, the layout doesn't reference the new JS file, and the JS file doesn't exist at all (file-not-found will appear in the failure trace).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java
git commit -m "test: add failing regression for flashcard generator wizard structure"
```

---

## Task 3: Restructure `flashcard-generator.html` into wizard panels

**Files:**
- Modify: `src/main/resources/templates/fragments/flashcard-generator.html`

The current fragment has a single `<form class="sh-card sh-ai-flashcard-form">` with `<div class="sh-card-body">` containing `<div class="sh-ai-flashcard-grid">` and two `<section>` children. We're going to:

1. Give the form `id="ai-flashcard-form"`.
2. Insert a `sh-wizard-steps` indicator with two pre-rendered pills.
3. Replace the grid container with a `sh-wizard-body` that holds two `.sh-wizard-panel` blocks (one per existing section).
4. Add Back/Next buttons in the footer, keep the existing two submit buttons.

- [ ] **Step 1: Add `id` to the form and the wizard step indicator**

In `src/main/resources/templates/fragments/flashcard-generator.html`, locate the `<form class="sh-card sh-ai-flashcard-form" ...>` element (around line 28). Add `id="ai-flashcard-form"` to its attributes so it begins:

```html
    <form class="sh-card sh-ai-flashcard-form"
          id="ai-flashcard-form"
          method="post"
          hx-post="/flashcards/generate"
          ...
```

Immediately *above* this `<form>` (i.e., still inside `div th:fragment="generator"`, after the closing `</div>` of the `generationError` alert block), insert the step indicator:

```html
    <div class="sh-wizard-steps" id="sh-wizard-steps">
        <div class="sh-wizard-step is-active" data-step-pill="1">
            <div class="sh-wizard-step-dot">1</div>
            <div class="sh-wizard-step-label" th:text="#{flashcard-gen.step.sources}">Sources</div>
        </div>
        <div class="sh-wizard-step-connector"></div>
        <div class="sh-wizard-step" data-step-pill="2">
            <div class="sh-wizard-step-dot">2</div>
            <div class="sh-wizard-step-label" th:text="#{flashcard-gen.step.destination}">Destination</div>
        </div>
    </div>
```

- [ ] **Step 2: Wrap section 1 (PDFs) in a wizard panel**

The body currently is:

```html
        <div class="sh-card-body">
            <div class="sh-ai-flashcard-grid">
                <section>
                    <div class="sh-section-title" th:text="#{flashcard-gen.choose-pdfs}">Choose PDFs</div>
                    ...
                </section>
```

Change the body wrapper class to add `sh-wizard-body` and remove the grid wrapper. Replace the opening of the body and the first `<section>` so it reads:

```html
        <div class="sh-card-body sh-wizard-body">
            <div class="sh-wizard-panel is-active" data-step="1" id="ai-flashcard-panel-1">
                <div class="sh-section-title" th:text="#{flashcard-gen.choose-pdfs}">Choose PDFs</div>
```

Keep every line inside the existing first `<section>` (search box, mode toggle, large-PDF warning, PDF folder tree) exactly as it is. Then change the closing `</section>` of that first section into `</div>` so the panel closes properly. *Do not* delete or modify the content lines themselves — only the opening/closing wrappers.

- [ ] **Step 3: Wrap section 2 (destination + card count) in a wizard panel**

The second `<section>` opens with:

```html
                <section>
                    <div class="sh-section-title" th:text="#{flashcard-gen.save-to}">Save generated cards to</div>
```

Replace that opening with:

```html
            <div class="sh-wizard-panel" data-step="2" id="ai-flashcard-panel-2">
                <div class="sh-section-title" th:text="#{flashcard-gen.save-to}">Save generated cards to</div>
```

Keep all the inner content (destination cards, folder/deck pickers, card count stepper) untouched. Change the closing `</section>` to `</div>` to close the panel.

Then delete the now-empty `<div class="sh-ai-flashcard-grid">` opening and its matching `</div>` (the wrapper that previously held both sections).

- [ ] **Step 4: Update the footer with Back + Next buttons**

The current footer (around line 233) is:

```html
        <div class="sh-wizard-footer" style="display:flex;">
            <button type="button" id="ai-flashcard-submit-with-instructions" class="sh-btn sh-btn-secondary" style="margin-left:auto;">
                <iconify-icon icon="lucide:message-square-text"></iconify-icon>
                <span th:text="#{flashcard-gen.generate-with-instructions}">Generate with instructions</span>
            </button>
            <button type="submit" id="ai-flashcard-submit" class="sh-btn sh-btn-primary">
                <iconify-icon icon="lucide:sparkles"></iconify-icon>
                <span th:text="#{flashcard-gen.generate}">Generate flashcards</span>
            </button>
        </div>
```

Replace the entire `<div class="sh-wizard-footer" ...>...</div>` block with:

```html
        <div class="sh-wizard-footer">
            <button type="button" id="ai-flashcard-back" class="sh-btn sh-btn-secondary" style="display:none;">
                <iconify-icon icon="lucide:arrow-left"></iconify-icon>
                <span th:text="#{flashcard-gen.back}">Back</span>
            </button>
            <div style="display:flex;gap:0.75rem;margin-left:auto;">
                <button type="button" id="ai-flashcard-next" class="sh-btn sh-btn-primary" disabled>
                    <span th:text="#{flashcard-gen.next}">Next</span>
                    <iconify-icon icon="lucide:arrow-right"></iconify-icon>
                </button>
                <button type="button" id="ai-flashcard-submit-with-instructions" class="sh-btn sh-btn-secondary" style="display:none;">
                    <iconify-icon icon="lucide:message-square-text"></iconify-icon>
                    <span th:text="#{flashcard-gen.generate-with-instructions}">Generate with instructions</span>
                </button>
                <button type="submit" id="ai-flashcard-submit" class="sh-btn sh-btn-primary" style="display:none;">
                    <iconify-icon icon="lucide:sparkles"></iconify-icon>
                    <span th:text="#{flashcard-gen.generate}">Generate flashcards</span>
                </button>
            </div>
        </div>
```

Note: the two generate buttons are now hidden by default; the controller will reveal them on step 2.

- [ ] **Step 5: Commit (test will still fail until JS file is created in Task 4)**

```bash
git add src/main/resources/templates/fragments/flashcard-generator.html
git commit -m "refactor(flashcard-gen): split form into 2-step wizard panels"
```

---

## Task 4: Create `flashcard-gen-wizard.js`

**Files:**
- Create: `src/main/resources/static/js/flashcard-gen-wizard.js`

- [ ] **Step 1: Write the controller**

Create `src/main/resources/static/js/flashcard-gen-wizard.js` with this exact content:

```javascript
(function () {
    'use strict';

    const FORM_ID = 'ai-flashcard-form';
    const PANEL_SELECTOR = '.sh-wizard-panel';
    const STEP_PILL_SELECTOR = '#sh-wizard-steps [data-step-pill]';
    const BACK_BTN_ID = 'ai-flashcard-back';
    const NEXT_BTN_ID = 'ai-flashcard-next';
    const SUBMIT_BTN_ID = 'ai-flashcard-submit';
    const SUBMIT_INSTR_BTN_ID = 'ai-flashcard-submit-with-instructions';
    const ERROR_BANNER_SELECTOR = '[data-ai-generation-error="true"]';

    let currentStep = 1;

    function $(id) { return document.getElementById(id); }

    function panels(form) {
        return form.querySelectorAll(PANEL_SELECTOR);
    }

    function setPanelActive(panel, active, direction) {
        panel.classList.remove('slide-from-right', 'slide-from-left', 'fade-in-up');
        if (active) {
            if (direction === 'forward') panel.classList.add('slide-from-right');
            else if (direction === 'backward') panel.classList.add('slide-from-left');
            else panel.classList.add('fade-in-up');
            panel.classList.add('is-active');
        } else {
            panel.classList.remove('is-active');
        }
    }

    function updateStepPills(step) {
        document.querySelectorAll(STEP_PILL_SELECTOR).forEach((pill) => {
            const pillStep = parseInt(pill.dataset.stepPill, 10);
            pill.classList.toggle('is-active', pillStep === step);
            pill.classList.toggle('is-done', pillStep < step);
            if (pillStep < step) {
                pill.style.cursor = 'pointer';
                pill.onclick = () => showStep(pillStep);
            } else {
                pill.style.cursor = '';
                pill.onclick = null;
            }
        });
    }

    function updateFooter(step) {
        const back = $(BACK_BTN_ID);
        const next = $(NEXT_BTN_ID);
        const submit = $(SUBMIT_BTN_ID);
        const submitInstr = $(SUBMIT_INSTR_BTN_ID);
        if (back) back.style.display = step === 2 ? '' : 'none';
        if (next) next.style.display = step === 1 ? '' : 'none';
        if (submit) submit.style.display = step === 2 ? '' : 'none';
        if (submitInstr) submitInstr.style.display = step === 2 ? '' : 'none';
    }

    function showStep(step) {
        const form = $(FORM_ID);
        if (!form) return;
        const direction = step > currentStep ? 'forward' : step < currentStep ? 'backward' : 'none';
        currentStep = step;
        panels(form).forEach((panel) => {
            const panelStep = parseInt(panel.dataset.step, 10);
            setPanelActive(panel, panelStep === step, direction);
        });
        updateStepPills(step);
        updateFooter(step);
        const stepsIndicator = document.getElementById('sh-wizard-steps');
        if (stepsIndicator) stepsIndicator.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function countSelectedPdfs(form) {
        return form.querySelectorAll('input[name="fileId"]:checked').length;
    }

    function updateNextEnabled() {
        const form = $(FORM_ID);
        const next = $(NEXT_BTN_ID);
        if (!form || !next) return;
        next.disabled = countSelectedPdfs(form) === 0;
    }

    function wire(form) {
        if (form.dataset.wizardWired === 'true') return;
        form.dataset.wizardWired = 'true';

        const back = $(BACK_BTN_ID);
        const next = $(NEXT_BTN_ID);
        if (back) back.addEventListener('click', () => showStep(1));
        if (next) next.addEventListener('click', () => {
            if (next.disabled) return;
            showStep(2);
        });

        form.addEventListener('change', (event) => {
            if (event.target.matches('input[name="fileId"]')) updateNextEnabled();
        });
    }

    function init() {
        const form = $(FORM_ID);
        if (!form) return;
        wire(form);
        updateNextEnabled();
        const hasError = document.querySelector(ERROR_BANNER_SELECTOR);
        showStep(hasError ? 2 : 1);
    }

    document.addEventListener('DOMContentLoaded', init);
    document.body.addEventListener('htmx:load', init);
})();
```

- [ ] **Step 2: Quick syntax sanity check**

Run:

```bash
node --check src/main/resources/static/js/flashcard-gen-wizard.js
```

Expected: no output (success). If `node` is unavailable, skip this and rely on the Maven build in step 4.

- [ ] **Step 3: Commit (regression test still fails — layout link missing)**

```bash
git add src/main/resources/static/js/flashcard-gen-wizard.js
git commit -m "feat(flashcard-gen): add wizard controller for 2-step generator"
```

---

## Task 5: Load the controller from `layout.html`

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html`

- [ ] **Step 1: Add the script tag**

Open `src/main/resources/templates/fragments/layout.html`. Find the line:

```html
    <script src="/js/study-wizard.js"></script>
```

(around line 205). Insert a new line immediately after it:

```html
    <script src="/js/flashcard-gen-wizard.js" defer></script>
```

- [ ] **Step 2: Run the regression test — now passing**

Run:

```bash
./mvnw -q -Dtest=UiResourceRegressionTests#aiFlashcardGeneratorIsA2StepWizard test
```

Expected: PASS.

- [ ] **Step 3: Run the full regression test class to confirm no other tests broke**

Run:

```bash
./mvnw -q -Dtest=UiResourceRegressionTests test
```

Expected: PASS for all tests in the class.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/layout.html
git commit -m "feat(layout): load flashcard generator wizard controller"
```

---

## Task 6: Manual smoke test in the running app

**Files:** none (verification only)

- [ ] **Step 1: Start the app**

Run:

```bash
./mvnw -q spring-boot:run
```

Wait for `Started StudyHelperApplication`.

- [ ] **Step 2: Verify happy path**

In a browser, log in, then go to `/flashcards/generate`. Confirm:

1. The step pill "1 Sources" is highlighted; the "2 Destination" pill is dim.
2. Only the PDF selection panel is visible. The destination panel is not shown.
3. The footer shows a single **Next** button (and no Back / Generate buttons). Next is disabled (greyed out).
4. Tick at least one PDF checkbox. Next becomes enabled.
5. Click Next. The panel transitions to step 2; step pill "2 Destination" is now active, "1 Sources" shows as done (clickable).
6. The footer now shows Back, Generate with instructions, and Generate flashcards. Next is hidden.
7. Pick "New deck", enter a name, pick a folder, click **Generate flashcards**. Flashcards generate as before.

- [ ] **Step 3: Verify Back navigation preserves state**

Reload `/flashcards/generate`. Tick two PDFs, click Next, then click Back. Confirm:

1. Step 1 is shown.
2. Both PDFs are still checked.
3. Mode toggle (Text/Full PDF) state is preserved.

- [ ] **Step 4: Verify error round-trip opens on step 2**

From step 1, tick one PDF → Next. On step 2, click Generate flashcards *without* picking a destination folder (assuming "New deck" is the default and no folder is chosen). The server should re-render with an error banner.

Confirm:

1. The error banner appears at the top of the page.
2. The wizard is on step 2 (destination panel visible, Back/Generate buttons visible).
3. The previously selected PDFs are still checked (visible if you click Back).

- [ ] **Step 5: Verify German UI**

Switch UI to German (via settings) and revisit `/flashcards/generate`. Confirm step pills read "Quellen" and "Speicherort" and the nav buttons read "Zurück" / "Weiter".

- [ ] **Step 6: Stop the app and commit nothing**

Stop the dev server with Ctrl+C. No code commit for this task.

---

## Self-Review Notes

Run through the spec sections vs. the tasks:

- "Step split: Sources → Destination" → Task 3 (panel 1 has section title `flashcard-gen.choose-pdfs`, panel 2 has `flashcard-gen.save-to`).
- "Step indicator: study-wizard pills" → Task 3 step 1 adds the indicator with `sh-wizard-step` / `sh-wizard-step-connector` markup.
- "Gating: disable Next until ≥1 PDF" → Task 4's `updateNextEnabled()` + Task 3 step 4 (Next rendered with `disabled` initially).
- "Dedicated small controller (Approach B)" → Task 4.
- "No controller-side changes" → confirmed: no Task touches Java controller/service code.
- "On error, open on step 2" → Task 4 `init()` checks for `[data-ai-generation-error="true"]`.
- "i18n keys (flashcard-gen.step.sources/destination, where-to-save, back, next)" → Task 1.
- "Regression tests for new wizard DOM" → Task 2 + Task 5 confirms passing.
- "Manual smoke via /run skill" → Task 6.

No placeholders; every step has the exact code, file, or command to run.
