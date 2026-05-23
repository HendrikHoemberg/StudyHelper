# Flashcard Generator: 2-Step Wizard

**Date:** 2026-05-23
**Status:** Approved (design)

## Goal

Convert the single-page AI Flashcard generator (`/flashcards/generate`) into a 2-step wizard that mirrors the existing study-setup wizard pattern. The split makes the form less overwhelming on first open and provides a natural gate against submitting with no PDFs selected.

## Step split

- **Step 1 — Sources.** Pick PDFs and Text / Full-PDF mode.
- **Step 2 — Destination.** Pick destination (new deck or existing deck), set card count, submit.

The Next button on step 1 is disabled until at least one PDF is selected. Server-side validation in `FlashcardGenerationController` remains the source of truth for everything else (destination chosen, deck name, etc.).

## Approach

Reuse the existing wizard *visuals* (CSS classes `sh-wizard-panel`, `sh-wizard-steps`, `sh-wizard-footer`) and a small new JS controller. The much larger `study-wizard.js` is **not** extended — it is tightly coupled to study modes (FLASHCARDS/QUIZ/EXAM), the source picker, the saved-session conflict modal, and the additional-instructions modal. A focused controller for the generator is easier to read and avoids regressing study setup.

The server-side flow (`/flashcards/generate`, `/flashcards/generate/preflight`, `FlashcardGenerationController`, validation, persistence) is unchanged. Only the view template and a new JS file change.

## File changes

- **Modify** `src/main/resources/templates/fragments/flashcard-generator.html`
  - Add a `sh-wizard-steps` indicator at the top (two pills: "1 Sources", "2 Destination").
  - Wrap the existing two `<section>` blocks in `<div class="sh-wizard-panel" data-step="1">` and `<div class="sh-wizard-panel" data-step="2">` respectively. Drop the `sh-ai-flashcard-grid` 2-column wrapper.
  - Step 1 contains: section title "Choose PDFs", search box, mode toggle (Text / Full PDF), large-PDF warning banner, PDF folder tree.
  - Step 2 contains: section title "Where to save", destination cards (new deck + existing deck), card-count stepper.
  - Footer: add Back and Next buttons. Keep the existing "Generate with instructions" and "Generate flashcards" buttons but show them only on step 2. Footer keeps the `sh-wizard-footer` class.
  - Give the form an explicit id (`id="ai-flashcard-form"`) for the JS controller to grab.

- **New** `src/main/resources/static/js/flashcard-gen-wizard.js`
  - Module-scoped state: `currentStep`, references to form, panels, step pills, Back/Next/Submit/Generate-with-instructions buttons, PDF checkboxes.
  - `init()` runs on `htmx:load` and initial `DOMContentLoaded`. Bails out if `#ai-flashcard-form` is absent.
  - Builds the step-pill markup into `#sh-wizard-steps` (same DOM shape as the study wizard's pills, but only two entries).
  - `showStep(n)`:
    - Toggles `hidden`/`display` on each panel based on `data-step`.
    - Updates pill active class.
    - Footer button visibility: step 1 → Next visible; step 2 → Back + Generate-with-instructions + Generate visible.
    - Scrolls form into view.
  - `updateNextEnabled()` counts checked `input[name=fileId]` and toggles `nextBtn.disabled`.
  - Click handlers for Next (1→2 if enabled), Back (2→1).
  - On init, if a `[data-ai-generation-error="true"]` alert is present in the DOM, call `showStep(2)` instead of step 1 — the user already finished step 1 and the error came from generate/preflight.

- **Modify** `src/main/resources/templates/fragments/layout.html`
  - Add a `<script src="/js/flashcard-gen-wizard.js" defer></script>` tag alongside the other JS includes.

- **Modify** `src/main/resources/messages.properties` and `messages_de.properties`
  - Add keys: `flashcard-gen.step.sources` ("Sources" / "Quellen"), `flashcard-gen.step.destination` ("Destination" / "Speicherort"), `flashcard-gen.where-to-save` ("Where to save" / "Speicherort"), `flashcard-gen.back` ("Back" / "Zurück"), `flashcard-gen.next` ("Next" / "Weiter"). New keys (not reused from `study.setup.*`) to keep the two wizards independently translatable.

- **Modify** `src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java`
  - Existing generator-page assertions should be extended to require:
    - `data-step="1"` and `data-step="2"` in the generator fragment.
    - `id="sh-wizard-steps"` present.
    - Back/Next button IDs (e.g., `ai-flashcard-back`, `ai-flashcard-next`) present.
    - The new JS file is referenced by `layout.html`.

## Data flow

Unchanged. The wizard is purely a client-side presentational split:

1. User opens `/flashcards/generate` → `FlashcardGenerationController#showGenerator` renders the form with both panels.
2. User picks PDFs in panel 1 → clicks Next → panel 2 shown.
3. User picks destination + card count → clicks Generate (or Generate-with-instructions, which opens the existing instructions modal first, then submits).
4. Form posts to `/flashcards/generate` with the same fields as today (`fileId[]`, `documentMode`, `destination`, `existingDeckId` or `newDeckName`+`newDeckFolderId`, `cardCount`, `additionalInstructions`).
5. On server error, controller re-renders the same fragment with selections preserved and a `generationError` banner; the JS controller detects the banner and opens on step 2.

No new endpoints; no controller-side changes.

## Error handling

- **No PDFs selected, attempt Next** — Next is disabled; clicking it is a no-op.
- **Destination invalid / generation fails** — server returns the form fragment with selections preserved; wizard reopens on step 2 with the error banner.
- **PDF mode toggle, search filter, large-PDF warning, number stepper, instructions modal** — keep working unchanged, since they target elements that now simply live inside a panel.

## Testing

- **Unit / regression** — `UiResourceRegressionTests` extended to assert the wizard DOM hooks listed above. Existing tests for the generator endpoint continue to pass without changes.
- **Manual smoke (via `/run` skill)**:
  - Happy path: open `/flashcards/generate` → step 1 visible, Next disabled → check a PDF → Next enabled → click Next → step 2 visible, Back visible, Generate buttons visible → choose new-deck destination + folder → click Generate → flashcards generated.
  - Gating: refresh page, confirm Next is disabled with no PDFs selected.
  - Back navigation: from step 2, click Back → step 1 shown, selections retained.
  - Error round-trip: force a destination error (e.g., New deck with no folder selected) → submit → page re-renders on step 2 with banner and selections.
  - i18n: switch UI to German and confirm step pills, Back/Next labels render correctly.

## Out of scope

- Changing what the server does on submit.
- Introducing per-step HTMX round-trips.
- Refactoring `study-wizard.js` or merging the two wizards.
- Mobile-specific layout changes beyond what already falls out of the existing `sh-wizard-*` CSS.
