# AI Test Mode — Expansion Plan

## Goals

Extend AI Test Mode beyond text-only flashcards. Add document sources (PDF/TXT/MD), image-aware flashcard filtering, question-type & difficulty selection, shortcut entry points, and a more honest progress UI.

Saving / history of completed tests is **out of scope** for this iteration — may revisit later.

---

## 1. New entities & DTOs

### 1.1 `DocumentSource` enum (new, `dto/`)
Lightweight marker (`PDF`, `TXT`, `MD`) used while extracting text. No persistence change to `FileEntry` — file type is derived from extension at read time.

### 1.2 `QuestionType` enum (new, `dto/`)
```
MULTIPLE_CHOICE, TRUE_FALSE
```

### 1.3 `TestQuestionMode` enum (new, `dto/`)
```
MCQ_ONLY, TF_ONLY, MIXED
```

### 1.4 `Difficulty` enum (new, `dto/`)
```
EASY, MEDIUM, HARD
```

### 1.5 `TestQuestion` (modify [src/main/java/com/HendrikHoemberg/StudyHelper/dto/TestQuestion.java](src/main/java/com/HendrikHoemberg/StudyHelper/dto/TestQuestion.java))
Add `QuestionType type` field. For `TRUE_FALSE`, `options` will be exactly `["True", "False"]` and `correctOptionIndex` is 0 or 1. Existing MCQ shape is unchanged.

### 1.6 `TestConfig` (modify [src/main/java/com/HendrikHoemberg/StudyHelper/dto/TestConfig.java](src/main/java/com/HendrikHoemberg/StudyHelper/dto/TestConfig.java))
```java
public record TestConfig(
    List<Long> selectedDeckIds,
    List<Long> selectedFileIds,
    int questionCount,
    TestQuestionMode questionMode,
    Difficulty difficulty
) {}
```

### 1.7 `StudyDeckGroup` / `StudyDeckOption` — extend for files
Either:
- **(a)** Add `List<TestFileOption> files` to [StudyDeckGroup](src/main/java/com/HendrikHoemberg/StudyHelper/dto/StudyDeckGroup.java) and to its sub-groups, **or**
- **(b)** Build a parallel `TestSourceGroup` DTO used only by the test picker, leaving study-session DTOs untouched.

**Decision: (b)** — keeps study-session code free of test-specific fields and avoids accidentally surfacing files in the study picker. New DTOs:

```
TestSourceGroup(folderId, folderName, folderColor, folderIcon,
                List<StudyDeckOption> decks, List<TestFileOption> files,
                List<TestSourceGroup> subGroups,
                isSelected, isIndeterminate, selectableSourceCount, totalSourceCount, ...)

TestFileOption(fileId, filename, sizeBytes, extension /* "pdf"|"txt"|"md" */,
               approxCharCount /* nullable, computed on demand */,
               isSupported)
```

`FolderService` gains a `getTestSourceTree(User user, List<Long> selectedDeckIds, List<Long> selectedFileIds)` method that filters `FileEntry` rows to extension ∈ {pdf, txt, md} per folder.

---

## 2. Document text extraction

### 2.1 New service: `DocumentExtractionService`
```
String extractText(FileEntry file) throws IOException
```
- Dispatches by extension:
  - `.txt` / `.md` — `Files.readString(path, UTF_8)`.
  - `.pdf` — Apache PDFBox 3.x.
- Throws `IllegalArgumentException` for unsupported extensions.
- Returns trimmed text; empty string is allowed (caller validates).

### 2.2 Dependency
Add to [pom.xml](pom.xml):
```xml
<dependency>
  <groupId>org.apache.pdfbox</groupId>
  <artifactId>pdfbox</artifactId>
  <version>3.0.3</version>
</dependency>
```

### 2.3 Limits (server-enforced, surfaced in UI)
Constants in `DocumentExtractionService`:
- `MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024` (5 MB) — files larger than this are marked `isSupported=false` in `TestFileOption` and shown disabled in the picker.
- `WARN_TOTAL_CHARS = 50_000` — when the **sum** of extracted chars across the current selection crosses this, picker shows a yellow inline banner: *"Large selection — generation may be slow."*
- `MAX_TOTAL_CHARS = 150_000` — submitting beyond this returns 400 with message *"Selection too large — please deselect some sources."*

Char counts per file are computed lazily once and cached in-memory per session (simple `HashMap<Long, Integer>` on the controller; cleared on logout via session listener — or just left to expire with the session).

---

## 3. Image-flashcard filtering

### 3.1 In `FlashcardService`
New helper:
```java
boolean hasUsableTextForAi(Flashcard card)
// true if card has front text OR back text (i.e. not image-only on every side)
```

`FlashcardService.getFlashcardsFlattened(decks)` is **not** changed; instead the `AiTestService` filters before building the prompt. This keeps the existing flashcard pipeline intact.

### 3.2 Deck card-count display
Currently `StudyDeckOption.cardCount` is total. Add `usableCardCount` (text-bearing cards) and surface in:
- Picker badge: `12 cards · 8 usable` (or just `8 usable` if all 12 are usable, omit redundancy).
- A deck with `usableCardCount == 0` is disabled in the **test** picker (study picker still uses `cardCount`).
- Folder-level meta updated to `selectableDeckCount` based on `usableCardCount > 0`.

This requires a new `FlashcardRepository` projection or a small service-layer aggregation that counts text-bearing cards per deck. Computed once per request, not per row.

### 3.3 Prompt-side defense
In the AI prompt, include:
> *"Some cards may have missing or partial context (their original images have been removed). If a card's text alone is insufficient to form a meaningful question, skip it."*

This catches the text+image cards where the text references the image (e.g. "What is shown in this diagram?").

---

## 4. `AiTestService` overhaul

Rename / restructure into a single `generate(...)` entry point:

```java
public List<TestQuestion> generate(
    List<Flashcard> flashcards,
    List<DocumentInput> documents,   // record(filename, extractedText)
    int count,
    TestQuestionMode mode,
    Difficulty difficulty
);
```

### 4.1 Prompt construction
- Build a single prompt that includes flashcard content **and** document content under labeled sections.
- For documents, include the full extracted text (trimmed). The hard char cap protects against blowing up the context.
- Mode handling:
  - `MCQ_ONLY` — all `MULTIPLE_CHOICE`.
  - `TF_ONLY` — all `TRUE_FALSE` (with "True"/"False" options enforced).
  - `MIXED` — instruct the model to produce a roughly 50/50 split (`floor(n/2)` MCQ + `ceil(n/2)` TF, or vice versa). Validate post-response and reject if grossly off (e.g. all one type).
- Difficulty handling — appended to prompt:
  - `EASY` — direct recall, obvious distractors.
  - `MEDIUM` — minor inference, plausible distractors.
  - `HARD` — synthesis across sources, distractors that share surface features with the answer.
- **Topic-relevance instruction** (per user feedback):
  > *"First identify the dominant subject matter of the supplied content. Generate questions only about concepts that belong to that subject. Ignore incidental metadata such as author names, page numbers, publication dates, headers, footers, or off-topic asides."*

### 4.2 JSON contract update
```json
{ "questions": [
  { "type": "MULTIPLE_CHOICE", "questionText": "...", "options": ["a","b","c","d"], "correctOptionIndex": 2 },
  { "type": "TRUE_FALSE",      "questionText": "...", "options": ["True","False"], "correctOptionIndex": 0 }
]}
```

Validation in `AiTestService`:
- `MULTIPLE_CHOICE` → exactly 4 options, index ∈ [0,3].
- `TRUE_FALSE` → options must be `["True","False"]` (normalize case), index ∈ [0,1].
- Drop malformed questions; if fewer than `max(1, count/2)` valid remain, throw `IllegalStateException("AI returned too few valid questions; please retry.")`.

### 4.3 Empty-source guards
- If both `flashcards` (after filtering) and `documents` are empty → `IllegalArgumentException` with clear message.

---

## 5. `TestController` changes

### 5.1 `/test/setup/update` — extend
Add params:
```
selectedFileIds: List<Long>
toggledFileId: Long
removeFileId: Long
```
Mirror existing deck-toggle logic for files. Folder toggle now selects/deselects **all usable decks AND all supported files** in that folder.

### 5.2 `/test/session` — extend
New params:
```
selectedFileIds: List<Long>
questionMode: TestQuestionMode  (default MCQ_ONLY)
difficulty: Difficulty          (default MEDIUM)
```

Flow:
1. Validate at least one source selected (deck OR file).
2. Load decks + flashcards (existing path).
3. For each selected file: load `FileEntry`, call `DocumentExtractionService.extractText(...)`, build `DocumentInput`. Sum char counts; reject if `> MAX_TOTAL_CHARS`.
4. Call `aiTestService.generate(...)`.
5. Persist new `TestSessionState` (extended to include mode/difficulty) in HTTP session.

### 5.3 New endpoint: `GET /test/start?deckId=` and `GET /test/start?fileId=`
For the "Test me on this" shortcut. Pre-selects the given source in the wizard.
- If `deckId` provided: validate ownership, pre-fill `preselectedDeckIds`.
- If `fileId` provided: validate ownership and supported extension, pre-fill `preselectedFileIds`.
- Falls through to the existing setup view.

### 5.4 New endpoint: `POST /test/sources/size`
Returns a JSON payload `{ totalChars, warn, exceedsCap }` for the current selection. Called via HTMX whenever a deck/file checkbox toggles, used to show/hide the warn/error banners without a full picker re-render.

(Alternative: fold this into `/test/setup/update` by including the metric in the rendered fragment. Cleaner. **Use this** — separate endpoint adds round-trip complexity.)

### 5.5 `TestSessionState` — extend
Add `TestQuestionMode mode` and `Difficulty difficulty` fields (carried for completeness; not currently shown anywhere post-setup, but needed if we later add "regenerate same settings").

---

## 6. UI changes

### 6.1 Wizard restructured to **3 panels** (was 2)
[fragments/test-setup.html](src/main/resources/templates/fragments/test-setup.html):

1. **Mode** — styled like study-session step 1 ([fragments/study-setup.html:55-80](src/main/resources/templates/fragments/study-setup.html#L55-L80)). Two/three radio cards:
   - "Multiple Choice" (icon: `list`)
   - "True / False" (icon: `check-circle`)
   - "Mixed (50/50)" (icon: `shuffle`)
   
   Plus a difficulty segmented-control row below: `Easy | Medium | Hard` (Medium pre-selected). Reuse existing `.sh-study-choice` / segmented-control styles where they exist; add small new CSS only if necessary.

2. **Sources** — current deck picker, extended:
   - Folder groups now list **decks + supported files** inline in the same `vb-group-body`. Files render with a `file-text` / `file` lucide icon and a size badge (e.g. `42 KB`).
   - Files exceeding the 5 MB hard cap render disabled with tooltip *"File too large for AI test (max 5 MB)."*
   - "Select all" and folder-level checkboxes select decks + files together.
   - Inline warning banner above the list: yellow when warn threshold crossed, red when hard cap exceeded. Driven by extra fields on the picker fragment.

3. **Question count** — unchanged (1–20).

Wizard JS (`updateNavButtons`, `goToStep`, etc.) bumps `totalSteps` to 3 and adds a validation step for panel 1 (mode must be selected — MCQ default).

### 6.2 Image-aware deck rendering
Disabled-deck label changes from "empty" to `"image-only"` when `usableCardCount == 0` but `cardCount > 0`. Tooltip: *"All cards in this deck are image-only and can't be used for AI tests."*

Decks with mixed cards show: `8 usable / 12 cards`.

### 6.3 Progress UI (cosmetic stages)
Replace the static "Generating your test with AI…" with a JS-driven cycler. While `htmx-indicator` is active, cycle messages every ~2.5s:
```
Reading flashcards…
Reading documents…
Asking Gemini…
Building questions…
Almost there…
```
Stop on `htmx:afterRequest`. Pure client-side, no server signal.

### 6.4 "Test me on this" shortcuts
- **Deck page** ([fragments/deck.html](src/main/resources/templates/fragments/deck.html)): add a button in the deck header actions row → `GET /test/start?deckId={id}` with `hx-target="#explorer-detail"`, `hx-push-url="true"`.
- **Folder page file rows** ([fragments/folder-detail.html](src/main/resources/templates/fragments/folder-detail.html)): for files with extension pdf/txt/md, add a small "Test" action (sparkles icon) in the row's action cluster → `GET /test/start?fileId={id}`.
- **Dashboard document tiles** ([fragments/explorer.html:88-110](src/main/resources/templates/fragments/explorer.html#L88-L110)): for tiles whose mime/extension is pdf/txt/md, add a hover action button (sparkles icon) → `GET /test/start?fileId={id}`. Position alongside the existing edit-options button so tile layout is undisturbed.

---

## 7. Order of implementation

Each step compiles & runs independently.

1. **Add enums + extend `TestQuestion` / `TestConfig` / `TestSessionState`.** Update existing tests to compile (default new enum fields).
2. **Add PDFBox dependency + `DocumentExtractionService`.** Unit-test `.txt`, `.md`, `.pdf` extraction in isolation.
3. **Add `usableCardCount` to `StudyDeckOption` + service aggregation.** Verify study picker is unaffected.
4. **Build `TestSourceGroup` + `FolderService.getTestSourceTree(...)`.** Returns decks (existing) + supported files filtered by extension.
5. **Rewrite `AiTestService.generate(...)`** — accept docs, mode, difficulty; new prompt; new JSON contract; new validators. Update `AiTestServiceTests` if any (none currently).
6. **Extend `TestController`** — `/test/setup/update`, `/test/session`, `TestSessionState` shape, `/test/start` query-param shortcuts.
7. **Wizard UI rework** — 3-panel wizard, files inline in picker, warning banners, progress cycler.
8. **"Test me on this" buttons** in deck page, folder file rows, dashboard document tiles.
9. **Manual QA pass** — run dev server, walk through each flow including: empty-source error, 5 MB cap, warn banner, hard cap, image-only deck disabled, mixed-card deck reduced count, deck+file mixed test, shortcut from each entry point, mode + difficulty variations.

---

## 8. Open risks / things to watch

- **PDFBox extraction quality** varies on scanned/image PDFs (returns blank text). Surface this: if a selected PDF extracts to <50 chars, treat it like an empty source and show a tile-level warning.
- **Token blow-up.** A 150 K-char prompt is ~37 K tokens — within Gemini Flash limits but slow. The 50 K warning threshold is the realistic comfort zone.
- **HTMX picker re-render preserving file checkboxes** — the existing pattern uses `hx-include="[name='selectedDeckIds']"`; we'll need a parallel `[name='selectedFileIds']` include on every toggle control. Easy to miss one.
- **Mixed mode fairness** — Gemini sometimes ignores 50/50 split instructions. Validation in §4.2 won't catch a 30/70 split; if this becomes a problem, escalate to two separate AI calls (n/2 MCQ + n/2 TF) and merge.
- **JSON parsing fragility** — TF questions returning `["true","false"]` (lowercase) or `["Yes","No"]`. Normalize defensively.
