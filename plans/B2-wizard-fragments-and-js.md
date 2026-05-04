# Sub-plan B2 — Extract wizard fragments + orchestrator JS

**Spec reference:** [`plan.md` §3.2](../plan.md), [`plan.md` §6.1-6.3](../plan.md), [`plan.md` §7 Phase B step 4](../plan.md).

**Goal:** Refactor the inline panels added in B1 into reusable fragments, and replace the hard-coded step indicator with a small JS state machine. After this sub-plan, the wizard's behavior is identical to B1 but the markup is composable and Exam can plug in cleanly in C2.

## Extract these fragments

Each is a `th:fragment` in its own file, included from `fragments/study-setup.html` via `th:replace`.

| New file | Fragment name | Source content |
|----------|---------------|----------------|
| `fragments/wizard-flashcards.html` | `panels(...)` | Flashcards Step 2 (sub-mode) and Step 4 (order) — currently inline in `study-setup.html`. Step 3 (deck picker) is shared. |
| `fragments/wizard-quiz.html` | `panels(...)` | Quiz Step 2 (question type, difficulty, count). Step 3 source picker is shared. |
| `fragments/wizard-source-picker.html` | `picker(mode, deckGroups, fileGroups, preselectedDeckIds, preselectedFileIds)` | The `vb-list` markup currently rendered in both wizards. Behavior: when `mode == FLASHCARDS`, render only `deckGroups` and omit the files section. When `mode == QUIZ` or `EXAM`, render decks + files. |

Move the relevant CSS classes' usage along with the markup; don't duplicate styles.

## Orchestrator JS

Add `static/js/study-wizard.js` (loaded from `study-page.html`).

Responsibilities:

- Track `currentStep` and `currentMode` in module-scoped variables.
- On mode-card click: set `currentMode`, animate the chosen card to center (reuse the existing slide-to-center animation from the current flashcards wizard — find its class names in the pre-rewrite `study-setup.html` history if needed), then call `goToStep(2)`.
- `goToStep(n)` shows the correct panel and updates the step indicator. Panels are siblings under a single container, addressed by `data-step="N"` and `data-mode="FLASHCARDS|QUIZ|EXAM"`. Hide all, show the one matching `currentMode` + `n`.
- Step indicator labels come from a JS map:
  ```js
  const STEP_LABELS = {
    FLASHCARDS: ["Mode", "Type", "Decks", "Order"],
    QUIZ:       ["Mode", "Format", "Sources"],
    EXAM:       ["Mode", "Setup", "Sources", "Layout"],
  };
  ```
  Re-render the indicator whenever `currentMode` changes.
- Per-step validation before allowing Next:
  - Step 2 (Flashcards type panel): a sub-mode radio must be selected.
  - Step 2 (Quiz format panel): question type and difficulty must be selected; count must be in range.
  - Step 3 (source picker): at least one deck OR file selected.
  - Step 4 (Flashcards order): drag-to-order JS already lives in the existing flashcards wizard — keep that code, just move it into `study-wizard.js` and gate it on `currentMode === 'FLASHCARDS' && currentStep === 4`.
- Skip-Step-1 behavior: on DOMContentLoaded, if a hidden `<input name="mode">` already has a value (server preselected), set `currentMode` from it and call `goToStep(2)` immediately. Mark Step 1 in the indicator as completed-but-clickable.
- HTMX hook: when `mode` changes, trigger `htmx.trigger` on the source picker container so the server re-renders the picker for the new mode (calls `POST /study/setup/update`).

## Controller-side changes

`StudyController.POST /study/setup/update`: should now return only the picker fragment (`wizard-source-picker.html :: picker`), not the whole wizard. The `mode` form field selects which picker variant to render.

## Verification before commit

1. `./mvnw -q -DskipTests package` compiles.
2. Navigate to `/study/start`. Pick Flashcards → Step 2 shows sub-mode radios → pick "Deck-by-deck" → Step 3 shows deck-only picker → Step 4 shows order panel → Submit launches flashcards session.
3. Same flow for "Shuffled" sub-mode → Step 4 is skipped (indicator shows 3 steps total).
4. Pick Quiz → Step 2 → Step 3 (deck+file picker) → Submit launches quiz.
5. Switch mode mid-flow (click Step 1 in indicator → pick a different mode) → wizard reconfigures correctly.
6. `GET /study/start?mode=FLASHCARDS&deckId=<id>` still skips picker, opens at Step 2, deck preselected at Step 3.
7. View source on `study-setup.html` — it should be short, mostly `th:replace` calls.

## Commit message

```
refactor: extract wizard panels into per-mode fragments and add orchestrator JS

Moves Flashcards, Quiz, and the shared source picker into their own
fragment files. Adds study-wizard.js to drive step transitions, mode
switching, and validation. Behavior unchanged.
```
