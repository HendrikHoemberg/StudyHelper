# Sub-plan C2 — Wizard Exam panel

**Spec reference:** [`plan.md` §4.1-4.3](../plan.md), [`plan.md` §6.3](../plan.md), [`plan.md` §7 Phase C step 7](../plan.md).

**Goal:** Wire Exam mode into the unified wizard. After this sub-plan, the user can pick Exam from the mode picker, configure size/count/timer/sources/layout, and submit — landing on the (stubbed-by-C1) `/exam/session` runtime endpoint.

C3 replaces those stubbed runtime responses with real UI.

## Add

`templates/fragments/wizard-exam.html` — `th:fragment="panels(...)"`.

Sibling to `wizard-flashcards.html` and `wizard-quiz.html` from B2. Contributes:

- **Step 2 panel** — `data-step="2" data-mode="EXAM"`. Fields:
  - `questionSize` — radio group: SHORT, MEDIUM, LONG, MIXED. Default MEDIUM.
  - `count` — number input, range 1–20, default 5.
  - `timerEnabled` — checkbox, default checked.
  - `timerMinutes` — number input, range 1–240, default computed from size×count (see formula at [`plan.md:135-140`](../plan.md#L135-L140)). Disabled when `timerEnabled` is unchecked.
  - Live label: `Estimated time: ~X minutes` — recomputes on size/count change. The "Estimated time" reflects the prefilled timer; once the user manually overrides `timerMinutes`, stop auto-updating it (mark a `data-user-overridden` flag) but keep updating the standalone label.
- **Step 3** — reuses `wizard-source-picker.html` with `mode=EXAM` (same as Quiz: decks + files).
- **Step 4 panel** — `data-step="4" data-mode="EXAM"`. Layout toggle: two large radio cards `PER_PAGE` ("One question per page") and `SINGLE_PAGE` ("All on one page"). Default `PER_PAGE`.

## Wire into orchestrator

`fragments/study-setup.html`:

- Include `wizard-exam.html` alongside the other wizard fragments.
- Re-enable the Exam mode card (remove the `disabled` attribute and "Coming soon" hint added in B1).

`static/js/study-wizard.js`:

- The `STEP_LABELS.EXAM` entry already exists from B2: `["Mode", "Setup", "Sources", "Layout"]`. No change needed.
- Add Step 2 validation for EXAM: `count` in range, and if `timerEnabled` then `timerMinutes` in range.
- Add Step 4 validation for EXAM: a layout radio must be selected (default-checked, so this is mostly defensive).
- Add the time-estimate calculation:
  ```js
  const TIMER_PER_Q = { SHORT: 2, MEDIUM: 5, LONG: 10, MIXED: 5 };
  function estimatedMinutes(size, count) { return TIMER_PER_Q[size] * count; }
  ```
  On size or count change in the EXAM Step 2 panel: update the estimate label, and update `timerMinutes` field unless `data-user-overridden="true"`.

## Wire into controllers

This is mostly already done in C1, but verify:

- `StudyController.POST /study/session` with `mode=EXAM` delegates to `ExamController` (the 501 placeholder from B1 should already be gone after C1).
- `StudyController.GET /study/start?mode=EXAM&...` resolves preselection the same way it does for Quiz.
- `StudyController.POST /study/setup/update` returns the deck+file picker variant when `mode=EXAM`.

## Verification before commit

1. `./mvnw -q -DskipTests package` compiles.
2. `GET /study/start` → mode picker shows three enabled cards.
3. Click Exam → Step 2 (size/count/timer fields) appears, time estimate updates as you change inputs.
4. Override `timerMinutes` manually, then change size — `timerMinutes` does NOT auto-update, but the estimate label does.
5. Step 3 source picker shows decks + files.
6. Step 4 layout toggle shows two cards.
7. Submit → POST hits `/exam/session` (C1's stub responds with "Generated N questions").
8. `GET /study/start?mode=EXAM&deckId=<id>` skips picker, opens at Step 2, deck preselected at Step 3.

## Commit message

```
feat: wire Exam mode into unified wizard

Adds wizard-exam.html with size/count/timer/layout configuration and
the live time-estimate label. Re-enables the Exam mode picker card
and extends the orchestrator with EXAM-specific validation. Submitting
hands off to the C1 ExamController stubs.
```
