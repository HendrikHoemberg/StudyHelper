# Plan: Unified Study Wizard + new Exam mode

## 1. Goal & overview

Today the app has two separate study experiences:

- **Flashcards** — `/study/start` → wizard (mode → decks → order) → flip-card review.
- **AI Quiz** (currently called "Test") — `/test/start` → wizard (mode → sources → count) → MCQ/TF instant-graded quiz.

Each has its own entry buttons in the sidebar, explorer, folder detail and deck card. This is visually noisy and forces the user to decide *how* they want to study before they have a unified mental model of "starting a session".

We will:

1. **Rename "Test" → "Quiz"** project-wide to free the word "Test" and remove ambiguity with the new Exam mode.
2. **Introduce a new "Exam" mode** — multi-question, freeform written answers, AI-graded with per-question feedback and a structured improvement report. Sessions are persisted and viewable later in a "Past Exams" view.
3. **Unify the three modes into one creation wizard** (`/study/start`) with a Mode picker as Step 1. All entry points throughout the app feed into this wizard, optionally pre-selecting a mode and/or pre-checking sources.

This document specifies every file to add, change or delete and the order to do it in.

---

## 2. Terminology & rename pass

`Test` → `Quiz` everywhere it refers to the existing AI MCQ/TF mode. The new mode is `Exam`. Keep these meanings strict throughout the codebase to avoid future confusion.

| Old name                  | New name                  |
|---------------------------|---------------------------|
| Test mode                 | Quiz mode                 |
| `/test/...`               | `/quiz/...`               |
| `TestController`          | `QuizController`          |
| `AiTestService`           | `AiQuizService`           |
| `TestConfig` etc. DTOs    | `QuizConfig` etc.         |
| `TestQuestion`            | `QuizQuestion`            |
| `TestQuestionMode`        | `QuizQuestionMode`        |
| `TestSessionState`        | `QuizSessionState`        |
| `TestSourceGroup`         | `QuizSourceGroup`         |
| `TestFileOption`          | `QuizFileOption`          |
| `test-page.html`          | `quiz-page.html`          |
| `fragments/test-*.html`   | `fragments/quiz-*.html`   |
| `test.css`                | `quiz.css`                |
| Java package classes referencing `Test` in name | rename accordingly |

The rename is mechanical. Use IDE refactor / `git grep -l "Test" -- src` to catch all references. Verify no method bodies reference test-mode-specific text the user can see.

---

## 3. Unified wizard architecture

### 3.1 Routes

| Method | Path | Purpose |
|--------|------|---------|
| `GET`  | `/study/start` | Render unified wizard. Optional query params: `mode` (`FLASHCARDS`/`QUIZ`/`EXAM`), `deckId`, `folderId`, `fileId` for preselection. |
| `POST` | `/study/setup/update` | HTMX picker swap. Carries `mode` so the picker knows whether to show files. |
| `POST` | `/study/session` | Submit form. Branches by `mode` to existing flashcard/quiz/exam launchers. |

The legacy `/study/start` (flashcards-only) and `/test/start` are removed; their behavior is absorbed into `/study/start?mode=...`.

### 3.2 Wizard step structure

The wizard is a single `<form>` with conditionally visible panels, like the existing two wizards. Steps differ per mode:

| Step | Flashcards          | Quiz                             | Exam                                      |
|------|---------------------|----------------------------------|-------------------------------------------|
| 1    | Mode picker         | Mode picker                      | Mode picker                               |
| 2    | Sub-mode (deck-by-deck / shuffled) | Question type + difficulty | Size + question count + timer toggle    |
| 3    | Sources (decks only) | Sources (decks + files)        | Sources (decks + files)                   |
| 4    | Order (only if deck-by-deck) | —                       | Layout toggle (one-per-page / single page)|

Step 1 is always the **Mode picker**. Steps 2–4 change based on the chosen mode. The step indicator at the top updates labels and count when mode changes (same pattern as the current flashcards wizard already uses to hide step 3 when "Shuffled" is chosen).

The picker (Step 3 for Quiz/Exam, Step 3 for Flashcards) reuses the existing `vb-list` markup. For Flashcards mode, the picker hides files entirely — the controller passes only `deckGroups` and the template branches on the `mode` hidden input.

### 3.3 Mode picker (Step 1)

Three large clickable cards, identical visual treatment to the existing `selectStudyMode` cards:

- **Flashcards** — `book-open` icon, "Flip through cards. Track right/wrong."
- **AI Quiz** — `list-checks` icon, "Auto-generated multiple choice & true/false questions."
- **Exam** — `pencil-line` icon, "Long-form written answers, AI-graded with feedback."

Selecting a card triggers the same slide-to-center animation already implemented for flashcards mode, then auto-advances to Step 2.

If the wizard was opened with `?mode=...`, the picker is **skipped entirely** (the corresponding mode is preselected and the wizard opens at Step 2). The step indicator still shows step 1 as "done" so the user can click back to change mode.

### 3.4 Entry-point consolidation

Replace every existing study/quiz CTA with a single unified one. In each case the `mode` and source preselection are encoded as query params.

| Location                           | Before                                              | After                                                         |
|------------------------------------|-----------------------------------------------------|---------------------------------------------------------------|
| Sidebar CTA                        | "Study Session" (`/study/start`)                    | "Start Studying" (`/study/start`) — mode picker shown         |
| Explorer toolbar                   | Two buttons: "Study Session" + "Test Mode"          | Single "Start Studying" (`/study/start`)                      |
| Folder detail header               | "Start Study Session"                               | "Study This Folder" (`/study/start?folderId={id}`) — picker shown, all decks/files in folder preselected |
| File row "Test" button             | `/test/start?fileId={id}`                           | `/study/start?fileId={id}` — picker shown, file preselected   |
| Deck card "Study"                  | `/decks/{id}/study/start`                           | `/study/start?mode=FLASHCARDS&deckId={id}` — **skips picker**, opens Flashcards wizard with deck preselected |
| Deck card "Test"                   | `/test/start?deckId={id}`                           | Removed. Replaced by single "Start Studying" button per deck → `/study/start?deckId={id}` (picker shown). |

> Rationale for the deck card asymmetry: the deck-level Study button is the primary way users start a flashcards session today; auto-selecting Flashcards mode preserves muscle memory. Other launches (folder, file, sidebar) show the picker because the source mix doesn't strongly imply a mode.

### 3.5 Sidebar additions

The sidebar gets a second CTA below "Start Studying":

```
📋 Past Exams      → /exams
```

Folders panel stays unchanged.

---

## 4. Exam mode — full specification

### 4.1 Configuration (Step 2)

| Field                | Values                                                   | Default   |
|----------------------|----------------------------------------------------------|-----------|
| `questionSize`       | `SHORT`, `MEDIUM`, `LONG`, `MIXED`                       | `MEDIUM`  |
| `questionCount`      | integer 1–20                                             | 5         |
| `timerEnabled`       | boolean                                                  | `true`    |
| `timerMinutes`       | integer 1–240; UI prefills from size × count             | computed  |

**Size → AI prompt mapping** (drives expected answer length, *not* the timer):

| Size   | Target answer length per question | Prompt hint                                                           |
|--------|-----------------------------------|------------------------------------------------------------------------|
| SHORT  | ~50 words                         | "Brief recall or definition. 1–2 sentences."                          |
| MEDIUM | ~150 words                        | "Explanation. ~150 words. Requires understanding, not just recall."    |
| LONG   | ~400 words                        | "Essay-style. ~400 words. Synthesis, application, or comparison."      |
| MIXED  | varies                            | "Mix all three depths. AI chooses the best depth per topic."           |

**Timer prefill formula** (UI only, user can override):

```
short  → 2 min × count
medium → 5 min × count
long   → 10 min × count
mixed  → 5 min × count
```

The setup UI shows a live "Estimated time: ~X minutes" label that updates as size/count change.

### 4.2 Source picker (Step 3)

Identical to the current Quiz picker (`testSetupPicker` fragment), reused under the unified wizard. Decks + files. Same source cap of **150,000 characters total** as Quiz. Same warning/error banners.

### 4.3 Layout toggle (Step 4)

Two options:

- **One question per page** — Next/Previous buttons. Progress shown as "Question 3 of 10". Allows the user to focus.
- **All on one page** — All N questions stacked, single scroll, single Submit button. Useful for review-style studying or when the user wants to see everything at once.

Stored as `examLayout: PER_PAGE | SINGLE_PAGE`. Default `PER_PAGE`.

### 4.4 Question generation

`AiExamService.generate(flashcards, documents, questionCount, size)` returns `List<ExamQuestion>` (records). Single AI call. Prompt structure mirrors `AiQuizService` but:

- No options / correctOptionIndex.
- Schema: `{"questions":[{"questionText":"...", "expectedAnswerHints":"..."}]}` where `expectedAnswerHints` is a short rubric the AI generates alongside the question (used later during grading to ensure consistency).
- Includes the size-specific length hint from §4.1.
- Uses the same "topic focus" / "skip cards lacking context" instructions as the quiz prompt.

### 4.5 Answer flow

**Per-page layout:**
- Single textarea per question, large (~6 rows), no character limit.
- Auto-saves to client-side state on change so navigating Previous/Next preserves answers.
- HTMX posts `/exam/answer` on Next; server updates session state and renders next question.
- "Submit Exam" button on the last question.

**Single-page layout:**
- All questions rendered in a `<form>` with N textareas.
- Single "Submit Exam" button at the bottom.

**Timer (if enabled):**
- Sticky countdown header.
- At 5 min remaining: amber visual.
- At 1 min remaining: red visual.
- At 0: auto-submits the form (whatever is filled).

### 4.6 Grading

A single AI call. Input:

```
{
  "questions": [
    { "questionText": "...", "expectedAnswerHints": "...", "userAnswer": "..." },
    ...
  ]
}
```

Prompt instructs the model to:

1. Score each answer **0–100%**.
2. Provide per-question feedback (2–4 sentences): what was correct, what was missing or incorrect, and what the model expected.
3. Mark blank answers as 0% with feedback "Not answered."
4. Produce an overall structured report:
   - **Overall score** (mean of per-question scores, percentage).
   - **Strengths** (2–4 bullet points).
   - **Weaknesses** (2–4 bullet points).
   - **Topics to revisit** (3–6 short topic names tied back to the source material).
   - **Suggested next steps** (2–3 actionable recommendations).

Response schema:

```json
{
  "perQuestion": [
    { "scorePercent": 85, "feedback": "..." },
    ...
  ],
  "overall": {
    "scorePercent": 76,
    "strengths": ["...", "..."],
    "weaknesses": ["...", "..."],
    "topicsToRevisit": ["...", "..."],
    "suggestedNextSteps": ["...", "..."]
  }
}
```

While grading, show a `htmx-indicator` spinner with rotating progress messages (same pattern as quiz generation today): "Reading your answers…", "Comparing with source material…", "Drafting feedback…", "Compiling report…".

### 4.7 Persistence — new entities

#### `Exam`

| Field             | Type                | Notes                                                  |
|-------------------|---------------------|--------------------------------------------------------|
| `id`              | `Long`              | PK                                                     |
| `user`            | `@ManyToOne User`   | Owner.                                                 |
| `title`           | `String`            | Auto-generated from sources (e.g. "Exam · Biology Deck + 2 docs · 12 May 2026"). User can rename later. |
| `createdAt`       | `LocalDateTime`     |                                                         |
| `completedAt`     | `LocalDateTime`     |                                                         |
| `questionSize`    | `ExamQuestionSize`  | enum                                                    |
| `questionCount`   | `int`               |                                                         |
| `timerMinutes`    | `Integer` (nullable)| null if disabled                                        |
| `layout`          | `ExamLayout`        | enum                                                    |
| `overallScorePct` | `int`               | 0–100                                                   |
| `sourceSummary`   | `String`            | Short human-readable list of source names at the time of taking, snapshotted (sources may be deleted later). |
| `reportJson`      | `@Lob String`       | Serialized overall report (`strengths`, `weaknesses`, `topicsToRevisit`, `suggestedNextSteps`). |
| `questions`       | `@OneToMany`        | `ExamQuestionResult`, `cascade = ALL`, `orphanRemoval = true` |

#### `ExamQuestionResult`

| Field               | Type              | Notes                                            |
|---------------------|-------------------|--------------------------------------------------|
| `id`                | `Long`            | PK                                               |
| `exam`              | `@ManyToOne Exam` |                                                  |
| `position`          | `int`             | 0-based order                                    |
| `questionText`      | `@Lob String`     | snapshotted                                      |
| `expectedAnswerHints` | `@Lob String`   | snapshotted                                      |
| `userAnswer`        | `@Lob String`     | may be empty                                     |
| `scorePercent`      | `int`             | 0–100                                            |
| `feedback`          | `@Lob String`     |                                                  |

`spring.jpa.hibernate.ddl-auto=update` will create both tables automatically.

> Note: we deliberately snapshot question/answer/source text into the exam record. Decks and files may be deleted after the exam is taken; the report must remain fully readable.

### 4.8 Past Exams view (`/exams`)

`GET /exams` — list all of the current user's exams, newest first. Card grid:

- Title, taken date, source summary
- Big overall score percent with color (green ≥80, amber 60–79, red <60)
- "View" button → `/exams/{id}`
- Delete button (icon, requires hover-confirm or modal — match existing deck delete pattern)

`GET /exams/{id}` — full detail view:

- Header: title (inline-editable), score, taken date, config (size, count, timer if used)
- Overall report (Strengths / Weaknesses / Topics to revisit / Next steps)
- Per-question accordion: question text, user answer, score, feedback
- "Delete" button in header

`DELETE /exams/{id}` — delete, redirect to `/exams`.

No re-take flow in v1 (user can start a new exam from the same sources via the wizard).

---

## 5. Backend changes — file by file

### 5.1 Files to add

| File | Purpose |
|------|---------|
| `entity/Exam.java` | JPA entity, §4.7 |
| `entity/ExamQuestionResult.java` | JPA entity, §4.7 |
| `repository/ExamRepository.java` | `JpaRepository<Exam, Long>` + `findAllByUserOrderByCreatedAtDesc(User)` |
| `dto/ExamQuestionSize.java` | enum `SHORT, MEDIUM, LONG, MIXED` |
| `dto/ExamLayout.java` | enum `PER_PAGE, SINGLE_PAGE` |
| `dto/ExamQuestion.java` | record `(String questionText, String expectedAnswerHints)` — AI generation output |
| `dto/ExamGradingResult.java` | record holding per-question + overall report (matches §4.6 schema) |
| `dto/ExamConfig.java` | record `(List<Long> deckIds, List<Long> fileIds, ExamQuestionSize size, int count, Integer timerMinutes, ExamLayout layout)` |
| `dto/ExamSessionState.java` | record `(ExamConfig config, List<ExamQuestion> questions, Map<Integer,String> answers, Instant startedAt)` — held in `HttpSession` until submit |
| `dto/ExamReport.java` | record for the overall report (used both during display and for `reportJson` serialization) |
| `dto/StudyMode.java` | enum `FLASHCARDS, QUIZ, EXAM` — shared by unified wizard |
| `service/AiExamService.java` | Two methods: `generate(...)` returns `List<ExamQuestion>`; `grade(...)` returns `ExamGradingResult`. Mirrors `AiQuizService` patterns (single ChatClient call, JSON parsing, error normalization). |
| `service/ExamService.java` | Persistence: save completed exam (`Exam` + child results), list by user, fetch by id with ownership check, delete. |
| `controller/ExamController.java` | Routes for the exam runtime + Past Exams view. See §5.3. |
| `controller/StudyController.java` | New unified wizard controller. See §5.3. |

### 5.2 Files to rename (Test → Quiz)

| Old | New |
|-----|-----|
| `controller/TestController.java` | `controller/QuizController.java` |
| `service/AiTestService.java` | `service/AiQuizService.java` |
| `dto/TestConfig.java` | `dto/QuizConfig.java` |
| `dto/TestQuestion.java` | `dto/QuizQuestion.java` |
| `dto/TestQuestionMode.java` | `dto/QuizQuestionMode.java` |
| `dto/TestSessionState.java` | `dto/QuizSessionState.java` |
| `dto/TestSourceGroup.java` | `dto/QuizSourceGroup.java` |
| `dto/TestFileOption.java` | `dto/QuizFileOption.java` |
| `templates/test-page.html` | `templates/quiz-page.html` |
| `templates/fragments/test-setup.html` | `templates/fragments/quiz-setup.html` |
| `templates/fragments/test-question.html` | `templates/fragments/quiz-question.html` |
| `templates/fragments/test-summary.html` | `templates/fragments/quiz-summary.html` |
| `static/css/test.css` | `static/css/quiz.css` |
| Routes `/test/...` | Routes `/quiz/...` (`/quiz/setup/update`, `/quiz/session`, `/quiz/answer`, `/quiz/next`) |
| Method calls e.g. `folderService.getTestSourceTree(...)` | `folderService.getQuizSourceTree(...)` |

After renaming, `QuizController` continues to own `/quiz/...` *runtime* endpoints (answer, next, summary). The setup endpoints (`/quiz/start`, `/quiz/setup/update`, `/quiz/session`) move conceptually under the unified wizard but the underlying handlers can stay in `QuizController`, just called from the unified setup.

### 5.3 New controllers — endpoint contracts

#### `StudyController` (unified wizard host)

```
GET  /study/start?mode=&deckId=&folderId=&fileId=
POST /study/setup/update      (carries `mode` so picker knows what to show)
POST /study/session           (branches on mode)
```

`POST /study/session` reads `mode` and:

- `FLASHCARDS` → delegates to existing flashcards launcher (today in `StudySessionController`).
- `QUIZ` → delegates to `QuizController.createSession`.
- `EXAM` → delegates to `ExamController.createSession`.

This keeps mode-specific runtime logic in mode-specific controllers, while the wizard skeleton lives in one place.

#### `ExamController`

```
POST /exam/session           Generate questions, store ExamSessionState in HttpSession, render run view.
POST /exam/answer            (PER_PAGE only) Save answer for current index, render same/next question.
GET  /exam/next              (PER_PAGE only) Advance to next question.
GET  /exam/prev              (PER_PAGE only) Step back.
POST /exam/submit            Grade all answers, persist Exam + ExamQuestionResult, render result page.
GET  /exams                  Past Exams list view.
GET  /exams/{id}             Detail view.
POST /exams/{id}/rename      Inline title edit.
DELETE /exams/{id}           Delete with ownership check.
```

All routes follow the existing pattern: `@RequestHeader("HX-Request")` decides full-page vs fragment response.

### 5.4 Service additions

**`FolderService`**

- Rename `getTestSourceTree(...)` → `getQuizSourceTree(...)` (also reused by Exam — same shape).
- Existing `getAllSourcesInFolder` is reused as-is.

**`StudySessionService`**

- No structural change. The existing flashcard launcher stays; only its caller changes (`StudyController` instead of direct route).

---

## 6. Frontend changes

### 6.1 Templates to add

| File | Purpose |
|------|---------|
| `templates/study-page.html` | Host page for the unified wizard (replaces today's `study-page.html` content; the file already exists, gets rewritten). |
| `templates/fragments/study-setup.html` | **Rewritten** to be the unified wizard with mode picker + branching panels. (See §6.3.) |
| `templates/exam-page.html` | Host page for the exam runtime. |
| `templates/fragments/exam-question.html` | PER_PAGE: single question + textarea + nav. |
| `templates/fragments/exam-single-page.html` | SINGLE_PAGE: all N questions stacked. |
| `templates/fragments/exam-grading.html` | "Grading…" loader (HTMX swap target during grading). |
| `templates/fragments/exam-result.html` | Per-question feedback + overall report. |
| `templates/exams-page.html` | Past Exams list view. |
| `templates/fragments/exams-list.html` | Reusable card grid. |
| `templates/fragments/exam-detail.html` | Detail view fragment. |

### 6.2 Templates to remove

- The existing `study-setup.html` is rewritten into the unified wizard, not kept separate.
- `test-setup.html` is renamed to `quiz-setup.html` and stripped down: it no longer needs to be a standalone wizard — its contents (sources picker, count input) become panels reused inside the unified wizard via `th:fragment`. See §6.3.

### 6.3 Unified wizard composition

`fragments/study-setup.html` becomes the orchestrator. To keep it manageable, mode-specific panels are separate fragments included via `th:replace`:

```
fragments/study-setup.html          ← orchestrator (mode picker, step indicator, footer, JS)
  ├── fragments/wizard-flashcards.html  (sub-mode panel + order panel)
  ├── fragments/wizard-quiz.html        (question type + difficulty + count panels)
  ├── fragments/wizard-exam.html        (size+count+timer panel + layout panel)
  └── fragments/wizard-source-picker.html (shared source picker — accepts `mode` parameter to show/hide files)
```

The orchestrator JS:

- Tracks `currentStep`, `currentMode`.
- On mode select (Step 1), records mode and computes `totalSteps` per mode.
- Step indicator labels are mode-specific: derived from `currentMode` via a small JS map.
- Validation per step (e.g. Step 3 source picker requires ≥1 selection) lives in the orchestrator.
- The drag-to-order interaction (today only used by Flashcards Step 4) stays in `wizard-flashcards.html`.
- The picker HTMX swap target is shared; the controller responds with the correct picker fragment based on `mode`.

### 6.4 Past Exams & detail UI

- List view: card grid (`sh-card`-style) reusing existing badge/score visual conventions.
- Detail view: header strip with score circle (color-coded), then the structured report in 4 panels (Strengths / Weaknesses / Topics to revisit / Next steps), then a per-question accordion using `<details>` elements for simplicity (no extra JS).

### 6.5 CSS additions

Add `static/css/exam.css` for:

- Exam textareas (`sh-exam-answer`, autosize on input).
- Sticky timer header (`sh-exam-timer`, `is-warn`, `is-danger`).
- Grading loader (reuse `sh-test-loading` from quiz, alias if needed).
- Result page layout: score circle, report sections, per-question cards.
- Past Exams card grid.

Rename existing `test.css` → `quiz.css` and update `<link>` references.

### 6.6 JS additions

Add `static/js/exam.js`:

- Timer countdown (driven by server-provided `startedAt` + `timerMinutes` so refresh doesn't reset it).
- Auto-resize for answer textareas.
- Submit-with-confirm if any answer is blank ("Submit with N unanswered questions?").
- Auto-submit on timer expiry.
- For PER_PAGE: client-side answer cache so back/forward preserves text without server round-trips.

---

## 7. Migration & cleanup tasks

Concrete order of work — designed so the app stays runnable between steps.

### Phase A — rename Test → Quiz (no behavior change)

1. Rename Java classes (use IDE refactor).
2. Rename DTO files.
3. Rename templates and update all `th:replace` / `th:fragment` references.
4. Rename routes `/test/...` → `/quiz/...`.
5. Update entry-point links (sidebar, explorer, deck card, folder detail, file row) to new `/quiz/...` URLs.
6. Rename `test.css` → `quiz.css`, update `<link>` tags.
7. Verify: existing quiz mode end-to-end works exactly as before.

**Commit point.**

### Phase B — unified wizard skeleton (still no Exam)

1. Add `dto/StudyMode.java` enum.
2. Add `controller/StudyController.java` with `GET /study/start` and `POST /study/session`.
3. Rewrite `study-setup.html` into the orchestrator with the mode picker and existing Flashcards/Quiz panels wired in (Exam panel is a stub).
4. Extract reusable wizard panel fragments (`wizard-flashcards.html`, `wizard-quiz.html`, `wizard-source-picker.html`).
5. Update entry points per §3.4 to point at the unified wizard with appropriate query params.
6. Remove the standalone `/quiz/start` route once the unified wizard handles its setup; keep `/quiz/session`, `/quiz/answer`, `/quiz/next` as the runtime endpoints.
7. Verify: Flashcards and Quiz both work end-to-end via the unified wizard, all entry points lead to the right preselected state.

**Commit point.**

### Phase C — Exam mode

1. Add `server.servlet.session.timeout=1d` to `application.properties` so long exams can't be lost to default 30-minute session expiry.
2. Add Exam DTOs (config, session state, question, grading result, report, enums).
3. Add `entity/Exam.java` and `entity/ExamQuestionResult.java` + repository.
4. Add `service/AiExamService.java` (generate + grade).
5. Add `service/ExamService.java` (persistence).
6. Add `controller/ExamController.java`.
7. Build `wizard-exam.html` panel with size/count/timer/layout fields and the live time estimate.
8. Build exam runtime templates (`exam-page.html`, `fragments/exam-question.html`, `fragments/exam-single-page.html`, `fragments/exam-grading.html`, `fragments/exam-result.html`).
9. Add `exam.js` (timer, autosize, autosave).
10. Add `exam.css`.
11. Verify: full exam flow — setup → questions → submit → grading loader → result.

**Commit point.**

### Phase D — Past Exams

1. Add `templates/exams-page.html`, `fragments/exams-list.html`, `fragments/exam-detail.html`.
2. Wire `GET /exams`, `GET /exams/{id}`, `POST /exams/{id}/rename`, `DELETE /exams/{id}`.
3. Add Past Exams CTA to sidebar.
4. Wire "View" / "Delete" actions in list and detail views.
5. Verify: take an exam → appears in Past Exams → opens detail → renames → deletes.

**Commit point.**

### Phase E — polish & cleanup

1. Walk all entry points one more time; remove dead code from old per-mode wizards.
2. Update `todo.md`: cross off "redesign step 3 of session creation" if the new design satisfies it.
3. Visual QA: mode picker animation, step indicator transitions, timer color states, grading loader messages.

---

## 8. Risks & open questions

- **AI grading consistency.** Freeform grading is harder than MCQ scoring. Mitigation: AI generates `expectedAnswerHints` at question-creation time, then grades against those hints. This anchors scoring to a per-question rubric the user cannot see but the model can.
- **Grading latency.** Using Gemini 3.1 Flash Lite, a single grading call for up to 20 answers is acceptable. We use one prompt per exam in v1. The grading loader UX stays. If latency or quality becomes a problem later, splitting into batched parallel calls is a small refactor on `AiExamService.grade(...)` only — no schema or controller changes needed.
- **HttpSession size.** `ExamSessionState` holding 20 long answers is fine (KB-scale). We persist only on submit.
- **DDL changes.** `ddl-auto=update` will add the new tables non-destructively. No data loss.
- **Session expiry mid-exam.** Today no explicit session timeout is configured, so Spring Boot's default (30 minutes) applies. We will raise it to **1 day** by adding `server.servlet.session.timeout=1d` to `application.properties`. This eliminates mid-exam loss for any realistic exam length and also benefits in-progress flashcards/quiz sessions. Done as part of Phase C (alongside other Exam-mode work).
- **Concurrent exams.** A user starts an exam, navigates away, starts another. The HttpSession key holds only one. Decision: **starting a new exam overwrites any in-progress one** silently — no confirm dialog. Rationale: simplest behavior, matches how the existing quiz/flashcards sessions already work.

---

## 9. Out of scope (v1)

- Re-taking an exam with the same questions.
- Re-grading an existing exam.
- Exporting an exam result (PDF / share link).
- Comparing two past exams side-by-side.
- Per-question timing.
- Storing in-progress exams across sessions (resume after browser close).
- Stats / trends across past exams (would be a nice follow-up: average score over time, weakest topics).
