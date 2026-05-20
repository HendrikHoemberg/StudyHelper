# Dashboard Redesign — Design

## Problem

The dashboard at `/dashboard` was built when StudyHelper was a flashcard-only app. As the app grew (quizzes, exams, AI generation, PDF tooling, file library), the dashboard did not evolve with it:

- The main panel is a flat grid of every deck and every file in the user's library — the same data the sidebar already navigates folder-by-folder. The grid is now mostly duplication.
- The right-side widgets ("Recent Activity", "Daily Goal — 4/5 decks studied today") are hardcoded placeholders. They show fake data regardless of the user's actual state. This has been flagged in `todo.md` ("maybe remove My library section in dashboard").
- The dashboard is the post-login landing page and the user's first impression every session, but it does not surface the things a returning user actually needs: *what should I do next?*

## Goal

Transform `/dashboard` from a redundant library mirror into a **lean cockpit + launcher**:

- **Cockpit**: surface the small set of signals that tell the user what to do next — a resume card if a session is interrupted, a "review mistakes" card if there are cards to review, recent activity, recently studied decks, recent exams.
- **Launcher**: three large, opinionated action tiles for the three primary verbs of the app — Start studying, Generate flashcards, Take an exam.
- **Lean**: no fake widgets. Every card shows real data, and every card hides itself when there's no data to show. The dashboard collapses gracefully for a brand-new user down to the launcher tiles.

This requires a small amount of new tracking (see "Data additions"). Spaced-repetition scheduling is explicitly out of scope; instead, a lightweight "wrong-answer streak" model gives most of the value with one column.

## Scope

In scope:

- Replace the dashboard main content (`fragments/explorer.html :: dashboardContent`) with the new layout.
- Remove the library grid and the fake right-side aside from the dashboard. (The sidebar continues to provide library navigation; nothing else changes.)
- Add `Deck.lastStudiedAt` and wire updates from study/quiz/exam completion.
- Add a `StudySession` history entity, written at the end of every completed flashcard, quiz, and exam session.
- Add `Flashcard.correctStreak` (nullable integer) and update it after every answer in flashcard study and quiz modes.
- Add a "Review mistakes" virtual deck: a cross-deck collection of every flashcard the user owns where `correctStreak == 0`. Studyable from the dashboard.
- Add a `mastered` count per deck (cards where `correctStreak >= 2`), shown on deck tiles in the recent-decks rail.
- Add `Deck.pinned` (boolean) and a pin/unpin control on deck tiles. Pinned decks appear in a dedicated "Pinned" rail above "Recent decks" on the dashboard.
- Update the existing resume card (already implemented in the `2026-05-20-resume-study-sessions-design.md` work) to use the new layout slot. No behavior changes to resume itself.

Out of scope:

- Full spaced repetition (SM-2, due dates, intervals). The wrong-answer model is intentionally simpler.
- Daily streaks, XP, badges, or other gamification. (`todo.md` lists "Gamify the system" as a separate item; this redesign creates the data foundation but does not build the widgets. Note: `Flashcard.correctStreak` introduced below is per-card, not a daily-login streak.)
- Per-card review history beyond the `correctStreak` integer (no review log).
- A dedicated "weak decks" or "weak topics" view. The mastery percent on deck tiles is the only weak-signal surface.
- Backfilling `lastStudiedAt` or `correctStreak` from historical exam/session data. Both default to `null` and start tracking forward.
- Changes to the sidebar, top nav, or any non-dashboard page (except the small hooks for writing the new tracking).
- Search on the dashboard. The current dashboard search filters the library grid; with the grid removed, there is no search UI on the dashboard. Library search remains available on folder pages and via direct sidebar navigation. Adding global search is a separate concern.

## Layout

The dashboard is a single-column stack. Each section is conditionally rendered; when its data source is empty, the section is omitted entirely. There is no fixed right-side aside.

Order, top to bottom:

1. **Greeting header.** `"Welcome back, <displayName>"` + subtitle. No actions in the header.
2. **Resume card** *(only if `SavedSession` exists for the user).* Existing component from the resume-study-sessions feature, rendered into this slot.
3. **Review mistakes card** *(only if at least one of the user's flashcards has `correctStreak == 0`).* Shows the count and a `[Study now]` button. Cross-deck.
4. **Action tiles row.** Three equally-sized tiles, always shown:
   - **Start studying** → `/study/start`
   - **Generate flashcards** → `/flashcards/generate`
   - **Take an exam** → `/study/start?mode=EXAM` (the existing study wizard accepts a `mode` query param; the same convention is already used for the `mode=QUIZ` tile on file rows)
5. **Pinned decks rail** *(only if the user has at least one pinned deck).* Horizontal scroll of deck tiles.
6. **Recent decks rail** *(only if the user has studied at least one deck — i.e., at least one deck has a non-null `lastStudiedAt`).* Up to 6 most-recently-studied decks. Deck tiles show the deck name, folder path, total card count, and a mastery line: `"<mastered> / <total> mastered"`.
7. **Recent activity list** *(only if at least one `StudySession` row exists).* Up to 5 most recent rows. Each row: icon for type (flashcards / quiz / exam), title, "<correct>/<total> · <relative time>".

For a brand-new user, only the greeting and action tiles render. The dashboard never shows empty-state copy for individual sections; the sections themselves are hidden.

The existing `aside.sh-dashboard-aside` block is deleted. The grid container (`#library-grid-container` and its `libraryGrid` fragment) is deleted. The search bar is deleted. Existing CSS classes that are no longer used (`sh-deck-grid`, `sh-dashboard-aside`, `sh-side-card`, `sh-activity-*`, `sh-goal-*`) are removed from the stylesheet in the same change.

## Data additions

### 1. `Deck.lastStudiedAt`

```
ALTER TABLE decks ADD COLUMN last_studied_at DATETIME NULL;
```

- Type: `LocalDateTime`, nullable.
- Updated whenever a flashcard study session, a quiz session, or an exam involves at least one card from this deck and the session is **completed** (reached the summary screen). Interrupted-then-resumed sessions update `lastStudiedAt` on completion, not on resume.
- For quizzes and exams that draw from multiple decks, every involved deck's `lastStudiedAt` is updated.
- For sessions that draw from files (no deck), nothing is updated. `StudySession` (below) still records the session.
- Recent-decks rail orders by `lastStudiedAt DESC NULLS LAST` and filters out nulls.

### 2. `StudySession` history table

One row per **completed** session, written at session end. Not used to persist in-flight state — that is `SavedSession`'s job.

```
study_session
  id              BIGINT PK
  user_id         BIGINT FK -> user(id), ON DELETE CASCADE, INDEXED
  type            ENUM('FLASHCARDS','QUIZ','EXAM')
  title           VARCHAR(255)       -- denormalized, e.g. "Biochem deck 2" or "Quiz · 2 decks"
  card_count      INT                -- total questions / cards in the session
  correct_count   INT                -- correctly answered (for EXAM, derived from score)
  duration_sec    INT NULL           -- wall-clock duration; null when not measurable
  completed_at    DATETIME, INDEXED  -- ordering key
```

- No `deck_id` column. Multi-deck sessions are common; the title carries the human label.
- For EXAM, `correct_count = round(card_count * overall_score_pct / 100)`. Mirrors the existing exam summary.
- For FLASHCARDS, `correct_count` is the count of cards the user marked correct in that pass.
- `duration_sec` is null for flashcards (untimed) and populated for quizzes and exams.
- Written once, never updated. No edit UI.
- Recent activity list reads the top 5 by `completed_at DESC`.
- No retention policy in this iteration; the table is small (one row per completed session per user).

### 3. `Flashcard.correctStreak`

```
ALTER TABLE flashcards ADD COLUMN correct_streak INT NULL;
```

- `NULL` = never answered. `0` = wrong at last sighting. `N >= 1` = right N times in a row.
- Updated after every answer in **flashcard study mode** and in **quiz mode** (for quiz questions that map back to a flashcard — see below). Exams do not update `correctStreak`; they are tested separately and the mapping back to source cards is not 1:1.
- Update rule:
  - Wrong → `0`
  - Right → `(correctStreak ?? 0) + 1`
- "Review mistakes" virtual deck = `SELECT f FROM Flashcard f WHERE f.deck.user = :user AND f.correctStreak = 0`.
- "Mastered" per deck = `count(f) where f.correctStreak >= 2`.
- Quiz-mode mapping: the existing quiz already generates questions from selected decks, but the generated question DTOs do not currently carry a back-reference to the source flashcard. Add a nullable `sourceFlashcardId` to the quiz question DTO (and persist it in the in-flight quiz session state) as part of this work. When the quiz is graded, questions with a non-null `sourceFlashcardId` update that flashcard's `correctStreak`; questions sourced from files do not update any streak.

### 4. `Deck.pinned`

```
ALTER TABLE decks ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
```

- One pin toggle button per deck tile (a star icon, on the deck tile actions overlay alongside the existing edit/delete buttons).
- The "Pinned" rail on the dashboard shows `WHERE pinned = TRUE` ordered by `name ASC`.
- No cap; the user pins as many decks as they want.

## Service layer

### `DashboardService` *(new)*

The dashboard controller currently composes data from multiple repositories inline. Pull the dashboard's data-loading into a `DashboardService` that returns a single `DashboardViewModel`:

```java
record DashboardViewModel(
    String greetingName,
    Optional<SavedSessionSummary> resume,
    int reviewMistakesCount,
    List<DeckSummary> pinnedDecks,
    List<DeckSummary> recentDecks,
    List<StudySessionSummary> recentActivity
) {}
```

- `greetingName`: the user's display name (falls back to username).
- `resume`: delegated to the existing `SavedSessionService.findForUser`.
- `reviewMistakesCount`: `SELECT count(f) FROM Flashcard f WHERE f.deck.user = :user AND f.correctStreak = 0`.
- `pinnedDecks`: projection over decks where `pinned = TRUE`, including `masteredCount` and total card count.
- `recentDecks`: projection over decks where `lastStudiedAt IS NOT NULL`, ordered desc, limit 6, including `masteredCount`.
- `recentActivity`: projection over the 5 most recent `StudySession` rows.

`DeckSummary` and `StudySessionSummary` are lightweight records used only by the dashboard view; they exist alongside (not replace) the existing `DeckOptionView` used by the wizards.

### `StudySessionRecorder` *(new)*

Single service called from the three completion paths (`StudyController` summary, `QuizController` summary, `ExamController` save). One method per session type:

```java
void recordFlashcards(User user, StudySessionState state);
void recordQuiz(User user, QuizSessionState state);
void recordExam(User user, Exam exam);
```

Each method:

1. Inserts one `StudySession` row.
2. For flashcards and quiz: updates `correctStreak` on every flashcard touched. (Exam does not update streaks.)
3. Updates `lastStudiedAt = now()` on every deck that contributed cards to the session.

All three steps run in a single `@Transactional` block. If any step fails, none commit, and the user sees the normal completion screen with no recorded history. This is preferable to half-written stats.

### `FlashcardReviewService` *(new)*

Owns the "Review mistakes" virtual deck. Public surface:

```java
int countMistakes(User user);
List<Flashcard> loadMistakeCards(User user);   // for the study session
```

`loadMistakeCards` returns the cards in deterministic order (id asc) and is consumed by a new endpoint `POST /study/start?mode=REVIEW_MISTAKES` that builds a flashcard `StudySessionState` from the result and reuses the existing study flow. The session, when completed, runs through the same `StudySessionRecorder.recordFlashcards`; cards answered correctly leave the mistakes deck naturally via `correctStreak` going from 0 → 1.

### `DeckPinService` *(new, tiny)*

```java
void togglePin(User user, Long deckId);
```

Backed by `DeckRepository`. Ownership is checked. Wired to `POST /decks/{id}/pin`.

## Controller changes

- `DashboardController.dashboard()` becomes a one-liner: `model.addAttribute("vm", dashboardService.buildFor(currentUser)); return "dashboard";`. The existing path-prefix and HTMX-target conventions are preserved.
- The current dashboard search endpoint (`GET /dashboard?q=…` returning a `libraryGrid` fragment) is removed. The `libraryGrid` Thymeleaf fragment is deleted.
- `StudyController`, `QuizController`, `ExamController` gain one line each at the end of their respective completion handlers: a call into `StudySessionRecorder`. No other behavior changes.
- New endpoints:
  - `POST /study/start?mode=REVIEW_MISTAKES` — starts a flashcard study session from the mistakes virtual deck.
  - `POST /decks/{id}/pin` — toggles the pin flag. Returns the updated dashboard fragment via HTMX, or a 200 with no body for non-dashboard callers.

## Templates

- `fragments/explorer.html :: dashboardContent` is rewritten end-to-end against `vm` (`DashboardViewModel`). The fragment is renamed in place to keep the URL routing intact; the file structure does not change.
- The rewritten fragment has top-level `th:if` guards around every conditional section so empty states drop out cleanly.
- The new sections use existing design-system classes (`sh-page-header`, `sh-btn`, `sh-deck-tile`, etc.) wherever they fit. New CSS is added only where existing classes don't cover the need:
  - `.sh-action-tile` — large action button on the dashboard launcher row.
  - `.sh-review-card`, `.sh-resume-card` — the two prominent stacked cards above the action tiles.
  - `.sh-activity-row` — replaces the old hardcoded `.sh-activity-item`, now backed by real data.
- The deleted CSS (`.sh-deck-grid`, `.sh-dashboard-aside`, `.sh-side-card`, `.sh-goal-*`, fake `.sh-activity-*`) is removed in the same change to keep the stylesheet honest.

## Migration

Three additive schema changes; all columns are nullable or have safe defaults. `spring.jpa.hibernate.ddl-auto=update` (current setting) handles them on app startup; no manual migration step.

- `decks.last_studied_at` — nullable, no backfill, no index in this iteration. Recent-decks rail tolerates a sequential scan (per-user filtering, then limit 6).
- `decks.pinned` — `BOOLEAN NOT NULL DEFAULT FALSE`. Safe to apply to existing rows.
- `flashcards.correct_streak` — nullable, no backfill.
- `study_session` table — new, no migration concerns.

Existing data is unaffected. Existing dashboards render correctly on the first request after deployment because every new section is conditional on data that simply does not exist yet.

## Testing

- **Service tests:**
  - `DashboardServiceTest` — verifies the view model assembles correctly for: brand-new user (only greeting + actions), user with mistakes only, user with recent decks only, user with all signals present.
  - `StudySessionRecorderTest` — verifies that completing a flashcard / quiz / exam session writes a `StudySession` row, updates the involved decks' `lastStudiedAt`, and updates `correctStreak` (or doesn't, for exams). Transactional rollback verified by injecting a failure in one step.
  - `FlashcardReviewServiceTest` — `countMistakes` and `loadMistakeCards` return only `correctStreak == 0` cards owned by the user. Verifies isolation across users.
- **Controller / integration tests:**
  - `DashboardControllerTest` — GET `/dashboard` renders all conditional sections correctly given seeded data.
  - `POST /study/start?mode=REVIEW_MISTAKES` — starts a session containing the right cards; on completion, the cards are correctly removed from / kept in the mistakes pool.
  - `POST /decks/{id}/pin` — toggles correctly; ownership is enforced (403 for someone else's deck).
- **No UI tests required.** The dashboard is server-rendered Thymeleaf; the existing test profile covers the integration layer.

## Open questions resolved

- **Cross-deck "Review mistakes" vs per-deck?** Cross-deck only in this iteration. A per-deck filter would be a tiny addition later but is not part of this work.
- **Mastery threshold?** `correctStreak >= 2`. Two consecutive correct answers graduate a card. Single guesses do not.
- **What about decks with no cards?** Recent-decks rail still shows them if `lastStudiedAt` is set, with `0 / 0 mastered`. (This is an edge case — sessions cannot be completed against empty decks — but the projection handles it safely.)
- **What about files in the library?** Files no longer appear on the dashboard. They remain accessible through the sidebar's folder browser and through the quiz/exam wizards. This is intentional: files are a source for sessions, not a primary navigation target.
