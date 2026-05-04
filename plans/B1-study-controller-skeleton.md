# Sub-plan B1 — Unified `StudyController` skeleton + mode picker

**Spec reference:** [`plan.md` §3.1-3.3](../plan.md), [`plan.md` §7 Phase B steps 1-3](../plan.md).

**Goal:** Stand up the unified wizard host. After this sub-plan, `/study/start` shows a three-card mode picker; selecting Flashcards or Quiz transitions to the existing per-mode wizard panels rendered inline. Exam card is present but disabled (`disabled` attribute, "Coming soon" hint) — its wiring lands in C2.

Sub-plan B2 extracts the mode-specific markup into reusable fragments and writes the orchestrator JS. Sub-plan B3 migrates the entry points. Don't do those here.

## Add

| File | Notes |
|------|-------|
| `dto/StudyMode.java` | Enum `FLASHCARDS, QUIZ, EXAM`. |
| `controller/StudyController.java` | See routes below. |

## Routes (StudyController)

```
GET  /study/start?mode=&deckId=&folderId=&fileId=
POST /study/setup/update
POST /study/session
```

- `GET /study/start` resolves preselection from query params (any combination of `mode`, `deckId`, `folderId`, `fileId`) and adds:
  - `mode` (StudyMode or null) — if null, picker is shown; otherwise picker is skipped and the wizard opens at Step 2.
  - `preselectedDeckIds`, `preselectedFileIds` — derived: `deckId` → singleton; `folderId` → all decks/files inside via `FolderService.getAllSourcesInFolder`; `fileId` → singleton.
  - `deckGroups` and `quizSourceTree` (the Quiz/Exam picker also wants files; Flashcards uses deck-only).
- `POST /study/setup/update` — HTMX swap target for the source picker. Receives `mode` so the controller knows whether to render the deck-only picker or the deck+files picker.
- `POST /study/session` — branches on `mode`:
  - `FLASHCARDS` → call into the existing flashcards launcher (today in `StudySessionController`). Keep that controller's launch method but extract a `public` service method on `StudySessionService` if not already public, then call it from here. Either delegate via `forward:` or call the same service path directly — match the cleaner pattern already used elsewhere.
  - `QUIZ` → delegate to `QuizController`'s existing `createSession` handler. Same approach.
  - `EXAM` → return 501 placeholder for now; C2 fills this in.

## Rewrite `templates/study-page.html` host

Keep its layout shell (header/nav/sidebar) intact. Replace the body with a slot for the unified wizard fragment.

## Rewrite `templates/fragments/study-setup.html`

This file currently hosts the flashcards-only wizard. Restructure it as the orchestrator with mode picker as Step 1. For now, paste the existing flashcards Step 2/3/4 and the quiz Step 2/3 panels inline (don't extract to separate fragments yet — B2 does that). Branching is fine via `th:if="${currentMode == 'FLASHCARDS'}"` etc.

### Mode picker markup (Step 1)

Three large cards mirroring the existing `selectStudyMode` cards already in the flashcards wizard (deck-by-deck vs shuffled). Match their classes and visual treatment. Use these icon names (Lucide):

- Flashcards — `book-open`
- AI Quiz — `list-checks`
- Exam — `pencil-line` (card has `disabled` attribute + "Coming soon" subtitle until C2)

### Skipping the picker

If `${mode}` is non-null on render, hide the picker panel (`th:if`/`th:unless`) and start the wizard at Step 2. The step indicator should still show Step 1 as completed and clickable so the user can change mode.

## Step indicator

Top of wizard. Labels are mode-specific. Hard-code three label maps in the template for now (a JS-driven indicator comes in B2):

- `FLASHCARDS`: `["Mode", "Type", "Decks", "Order"]` (Step 4 hidden when sub-mode = shuffled, same as today).
- `QUIZ`: `["Mode", "Format", "Sources"]`.
- `EXAM`: `["Mode", "Setup", "Sources", "Layout"]` (irrelevant in B1 since the card is disabled).

## Don't do here

- Don't extract `wizard-flashcards.html` / `wizard-quiz.html` / `wizard-source-picker.html`. That's B2.
- Don't change the entry-point links (sidebar, explorer, etc.). That's B3 — until B3 lands, they continue to point at `/quiz/start` and the legacy flashcards `/study/start`. To avoid a broken state, keep the legacy `/quiz/start` and the legacy flashcards setup endpoints alive in this sub-plan; they get retired in B3.
- Don't touch the runtime endpoints (`/quiz/session`, `/quiz/answer`, etc.) — they stay untouched.

## Verification before commit

1. `./mvnw -q -DskipTests package` compiles.
2. `GET /study/start` (no params) renders the picker with three cards.
3. Click Flashcards card → flashcards Step 2 visible. Click Quiz card → quiz Step 2 visible. Exam card is disabled.
4. `GET /study/start?mode=FLASHCARDS&deckId=<existing-deck-id>` skips the picker and lands on flashcards Step 2 with that deck preselected when the user reaches Step 3.
5. `POST /study/session` with `mode=FLASHCARDS` launches a flashcards session end-to-end. Same with `mode=QUIZ`.
6. The legacy `/quiz/start` URL still works (entry points haven't been migrated yet).

## Commit message

```
feat: add unified StudyController and mode picker on /study/start

Introduces StudyMode enum and a single wizard host that renders a
Flashcards / Quiz / Exam picker. Selecting a mode reveals the
existing per-mode panels. Entry-point links and fragment extraction
follow in subsequent commits.
```
