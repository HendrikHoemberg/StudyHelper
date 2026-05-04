# Sub-plan B3 — Migrate entry points to unified wizard

**Spec reference:** [`plan.md` §3.4-3.5](../plan.md), [`plan.md` §7 Phase B steps 5-6](../plan.md).

**Goal:** Replace every existing study/quiz CTA with a single "Start Studying" entry that routes through the unified wizard, and retire the legacy setup endpoints. After this sub-plan, the only way to start a session is via `/study/start`.

## Link migration table

Apply the table at [`plan.md:90-97`](../plan.md#L90-L97) verbatim. Concretely:

| Template | Change |
|----------|--------|
| `templates/fragments/sidebar.html` | "Study Session" + "Test Mode" buttons → single "Start Studying" → `/study/start`. Add a second sidebar entry "Past Exams" → `/exams` (route doesn't exist yet — D wires it; the link is fine to add now since it 404s harmlessly until then). |
| `templates/fragments/explorer.html` | Two buttons collapse to one "Start Studying" → `/study/start`. |
| `templates/fragments/folder-detail.html` | "Start Study Session" → "Study This Folder" → `/study/start?folderId={id}`. |
| `templates/fragments/deck.html` | "Study" button → `/study/start?mode=FLASHCARDS&deckId={id}` (skips picker). "Test" button → **removed**. Replace with a single "Start Studying" CTA → `/study/start?deckId={id}` (picker shown). Net: deck card has two buttons — primary "Study" (Flashcards quick-start), secondary "Start Studying" (full picker). |
| File-row "Test" button (likely in `fragments/folder-children.html`) | → `/study/start?fileId={id}` (picker shown, file preselected). Label becomes "Study". |

If the deck-card asymmetry feels off when implementing, re-read the rationale at [`plan.md:99`](../plan.md#L99) before changing it.

## Routes to retire

After this sub-plan:

- `/quiz/start` (the standalone Quiz setup GET) — **delete** the handler and template.
- `/quiz/setup/update` — **delete**. Picker swaps now route through `/study/setup/update`.
- The legacy flashcards-only `GET /study/start` handler that pre-existed B1 — confirm B1's `StudyController` already replaced it; remove any leftover route.

**Keep** these (they're the runtime — the unified wizard delegates to them on submit):

- `POST /quiz/session`
- `POST /quiz/answer`, `POST /quiz/next` (or whatever the existing runtime routes are named)
- All flashcards runtime endpoints in `StudySessionController`.

## Sidebar "Past Exams" entry

Add a second sidebar CTA below "Start Studying":

```html
<a href="/exams" ...>📋 Past Exams</a>
```

(Use the project's existing icon convention — Lucide `clipboard-list` or `history`, whichever matches the rest of the sidebar.) Route lands in sub-plan D; until then it 404s.

## Verification before commit

1. `git grep -nE '/test/(start|setup)|/quiz/(start|setup/update)' src/main/` returns **no matches**.
2. Boot the app:
   - Sidebar → "Start Studying" → picker shown.
   - Explorer toolbar → single "Start Studying" → picker shown.
   - Folder detail → "Study This Folder" → picker shown, all decks/files in folder preselected at Step 3.
   - Deck card "Study" → opens at Flashcards Step 2 with deck preselected (no picker).
   - Deck card "Start Studying" → picker shown, deck preselected.
   - File row "Study" → picker shown, file preselected.
3. Submit a flashcards session and a quiz session via the unified wizard — both work end-to-end.
4. `/quiz/start` returns 404.
5. Sidebar "Past Exams" link is present (404 expected, not in scope).

## Commit message

```
refactor: route all study entry points through unified /study/start

Sidebar, explorer, folder detail, deck cards, and file rows now all
launch the unified wizard with appropriate preselection encoded as
query params. Retires the standalone /quiz/start setup route. Adds a
Past Exams sidebar link in anticipation of the upcoming /exams view.
```
