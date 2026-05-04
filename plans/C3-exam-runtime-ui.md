# Sub-plan C3 — Exam runtime UI (templates, JS, CSS)

**Spec reference:** [`plan.md` §4.5-4.6](../plan.md), [`plan.md` §6.1, §6.5-6.6](../plan.md), [`plan.md` §7 Phase C steps 8-10](../plan.md).

**Goal:** Replace the C1 stub responses with real UI. After this sub-plan, the full exam flow runs end-to-end: setup → questions → submit → grading loader → result.

The persistence and listing UI (`/exams`, `/exams/{id}`) stay stubbed — sub-plan D handles those.

## Templates to add

| File | Used by |
|------|---------|
| `templates/exam-page.html` | Host page returned by `POST /exam/session` on full-page request. |
| `templates/fragments/exam-question.html` | PER_PAGE: single question + textarea + nav (Previous/Next/Submit). HTMX target for `/exam/answer`, `/exam/next`, `/exam/prev`. |
| `templates/fragments/exam-single-page.html` | SINGLE_PAGE: form with N textareas + Submit. |
| `templates/fragments/exam-grading.html` | Loader shown while `POST /exam/submit` runs. Rotating progress messages. |
| `templates/fragments/exam-result.html` | Per-question accordion + overall report (Strengths / Weaknesses / Topics to revisit / Next steps). |

## Markup details

### `exam-question.html` (PER_PAGE)

- Sticky timer header at top (only if `timerEnabled`). Shows `MM:SS` remaining.
- Question text (large).
- Single `<textarea name="answer" rows="6">` — class `sh-exam-answer`. Pre-filled with `state.answers[currentIndex]` if present.
- Footer:
  - "Previous" button (hx-get `/exam/prev`, target `#exam-runtime`) — hidden when at index 0.
  - "Next" button (hx-post `/exam/answer`, includes `index` and the textarea, target `#exam-runtime`) — visible when not at last index.
  - "Submit Exam" button (hx-post `/exam/submit`, target `#exam-runtime`, hx-indicator `#grading-loader`) — visible only at last index.
- Progress text: "Question X of N".

Persist the answer client-side (sessionStorage) on every input event so navigating Previous → Next within the same browser tab preserves what the user typed even if HTMX doesn't round-trip the value yet.

### `exam-single-page.html` (SINGLE_PAGE)

- Sticky timer header at top (if enabled).
- A single `<form hx-post="/exam/submit" hx-target="#exam-runtime" hx-indicator="#grading-loader">` containing N stacked question blocks, each with a `<textarea name="answers[i]" rows="6">`.
- Single Submit button at the bottom.

### `exam-grading.html`

`htmx-indicator` div with rotating messages (driven by JS in `exam.js`):

```
"Reading your answers…"
"Comparing with source material…"
"Drafting feedback…"
"Compiling report…"
```

Cycle every ~3 seconds. Match the visual pattern of the existing quiz generation loader (look at `fragments/quiz-setup.html` or `quiz-question.html` for the existing loader).

### `exam-result.html`

- Header: title (auto-generated, plain text — inline editing arrives in D), score circle (color-coded: green ≥80, amber 60–79, red <60), config summary (size, count, timer if used), "View in Past Exams" link → `/exams/{id}`.
- Overall report: 4 panels — Strengths / Weaknesses / Topics to revisit / Suggested next steps. Each is a bulleted list from `ExamReport`.
- Per-question section: `<details>` accordion per question. Each contains: question text, the user's answer (or "Not answered" italicized), score, feedback.

## JS

`static/js/exam.js`:

- **Timer**: read `data-started-at` (ISO instant) and `data-timer-minutes` from the timer element. Compute `endsAt = startedAt + timerMinutes*60s`. Tick every 500ms. Update display. At `≤5 min` add class `is-warn` (amber). At `≤1 min` add `is-danger` (red). At `0` programmatically submit the form (PER_PAGE: trigger the Submit button; SINGLE_PAGE: submit the form). Server-driven `startedAt` means refresh doesn't reset the timer.
- **Autosize textarea**: on input, set `el.style.height = 'auto'; el.style.height = el.scrollHeight + 'px'` for `.sh-exam-answer`.
- **Loader rotator**: in `exam-grading.html`, cycle through the four message strings every 3000ms.
- **Submit-with-confirm**: on Submit click (both layouts), if any textarea is empty/whitespace, `confirm("Submit with N unanswered questions?")` — abort if user cancels. Skipped on auto-submit (timer expiry).
- **PER_PAGE answer cache**: write current textarea value to `sessionStorage` keyed by `examId + index` on input. On render of a new question, read back if present.

## CSS

`static/css/exam.css`:

- `.sh-exam-answer` — full-width textarea, autosizable.
- `.sh-exam-timer` — sticky top, monospace, color states `.is-warn` (amber bg/text), `.is-danger` (red bg/text).
- `.sh-exam-loader` — large centered loader (model on existing quiz loader; alias styles if reusable).
- `.sh-exam-result-score` — score circle, color-coded.
- `.sh-exam-result-section` — the 4 report panels in a 2×2 grid (collapse to 1 col on narrow screens).
- `.sh-exam-result-question` — per-question card.

Wire `<link>` references in `exam-page.html`.

## Replace C1 stubs

In `ExamController`, swap the stub responses for:

- `POST /exam/session` → render `exam-page.html` (full page) or `fragments/exam-question.html` / `fragments/exam-single-page.html` (HTMX swap, depending on layout).
- `POST /exam/answer` → render `fragments/exam-question.html` (next index).
- `GET /exam/next`, `GET /exam/prev` → render `fragments/exam-question.html`.
- `POST /exam/submit` → grade, save via `ExamService.saveCompleted`, render `fragments/exam-result.html`. The just-saved `Exam` is in scope for the template — pass `exam.id` so the "View in Past Exams" link works.

The `exam-runtime` HTMX swap target is the outer wrapper around the question fragments inside `exam-page.html`. Use that ID consistently in all hx-target attributes.

## Verification before commit

1. `./mvnw -q -DskipTests package` compiles.
2. End-to-end PER_PAGE flow:
   - `/study/start` → Exam → size=SHORT, count=3, timer=5min, PER_PAGE → pick a deck → submit.
   - Question 1 renders with timer. Type an answer. Click Next → Question 2. Click Previous → Q1 with answer preserved. Forward to Q3 → Submit.
   - "Submit Exam" → grading loader shows with rotating messages → result page renders with score, report, and accordion.
3. End-to-end SINGLE_PAGE flow:
   - Same setup, layout=SINGLE_PAGE → all 3 questions on one page → fill some, leave one blank → Submit → confirm dialog ("Submit with 1 unanswered questions?") → grade → result.
4. Timer color states: set `count=1, timerMinutes=2` and watch the timer turn amber under 5min (immediate), red under 1min, auto-submit at 0.
5. Refresh mid-exam: timer continues from real elapsed time (not reset).
6. DB check: a row exists in `exam` and N rows in `exam_question_result`.

## Commit message

```
feat: add Exam runtime UI — questions, grading loader, result page

Wires real templates to ExamController endpoints for both PER_PAGE and
SINGLE_PAGE layouts. Adds exam.js (timer, autosize, autosave, submit
confirm, auto-submit on expiry) and exam.css. Past Exams views remain
stubbed — handled in sub-plan D.
```
