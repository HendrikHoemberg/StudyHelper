# Implementation Prompts — AI Test Mode Expansion

Companion to [plan.md](plan.md). Hand each prompt to a coding agent in the order given. Each prompt is self-contained.

---

## Sub-plan: Step 5 — `AiTestService.generate(...)`

Extends `plan.md` §4.

### 5a. Inputs

```java
public record DocumentInput(String filename, String extractedText) {}

public List<TestQuestion> generate(
    List<Flashcard> flashcards,
    List<DocumentInput> documents,
    int count,
    TestQuestionMode mode,
    Difficulty difficulty
);
```

### 5b. Pre-flight

1. Build `cardContent` via the existing `buildCardContent(flashcards)` (filters image-only cards already).
2. Build `docContent`: for each `DocumentInput`, append `Document N — <filename>:\n<extractedText>\n\n`.
3. If both are blank → `IllegalArgumentException("Selected sources contain no usable text. Pick a deck with text or a document with extractable content.")`.
4. Compute split for `MIXED`: `mcqCount = count / 2; tfCount = count - mcqCount`.

### 5c. Prompt template (concrete wording)

```
You are a study assistant. Generate {COUNT} {MODE_DESCRIPTION} based on the source material below.

DIFFICULTY: {DIFFICULTY_INSTRUCTION}

TOPIC FOCUS:
First identify the dominant subject matter of the supplied content. Generate
questions only about concepts that belong to that subject. Ignore incidental
metadata such as author names, page numbers, publication dates, headers, footers,
or off-topic asides.

SOME CARDS MAY HAVE MISSING CONTEXT:
Some flashcards may have had images removed. If a card's text alone is
insufficient to form a meaningful question (e.g. it references "this diagram"
or "the image above"), skip that card.

QUESTION TYPE RULES:
{TYPE_RULES}

GENERAL RULES:
- Each question stands alone — do not reference "the text" or "the document".
- correctOptionIndex is 0-based.
- Vary which index is correct across questions.
- Test understanding, not exact wording.

=== FLASHCARDS ===
{CARD_CONTENT_OR_NONE}

=== DOCUMENTS ===
{DOC_CONTENT_OR_NONE}

Respond ONLY with valid JSON, no extra text. Schema:
{"questions":[
  {"type":"MULTIPLE_CHOICE","questionText":"...","options":["a","b","c","d"],"correctOptionIndex":0},
  {"type":"TRUE_FALSE","questionText":"...","options":["True","False"],"correctOptionIndex":0}
]}
```

**`{MODE_DESCRIPTION}`:**
- `MCQ_ONLY` → `"multiple-choice questions"`
- `TF_ONLY` → `"true/false questions"`
- `MIXED` → `"questions: exactly {mcqCount} multiple-choice and {tfCount} true/false"`

**`{DIFFICULTY_INSTRUCTION}`:**
- `EASY` → `"Direct recall. Questions test obvious facts. Distractors are clearly wrong."`
- `MEDIUM` → `"Minor inference required. Distractors are plausible but distinguishable on careful reading."`
- `HARD` → `"Synthesis across sources. Distractors share surface features with the answer; require precise understanding."`

**`{TYPE_RULES}`:**
- `MCQ_ONLY` → `- All questions are MULTIPLE_CHOICE with exactly 4 options. Exactly one correct.`
- `TF_ONLY` → `- All questions are TRUE_FALSE. options must be exactly ["True","False"]. correctOptionIndex is 0 (True) or 1 (False).`
- `MIXED` → both rules above plus `- Approximately half each type as specified above.`

**`{CARD_CONTENT_OR_NONE}`:** the built card content, or literal `(none)` if empty.
**`{DOC_CONTENT_OR_NONE}`:** the built doc content, or literal `(none)` if empty.

### 5d. Validation (post-parse)

For each question in JSON:
- Must have non-blank `questionText`.
- Must have a `type` field (default to `MULTIPLE_CHOICE` if absent for backwards-compat).
- If `MULTIPLE_CHOICE`: `options.size() == 4`, `correctOptionIndex` ∈ [0,3]. Drop otherwise.
- If `TRUE_FALSE`: normalize `options` to `["True","False"]` if any case variant matches (`true/True/TRUE`, `false/False/FALSE`); reject if options can't be normalized to exactly two boolean-ish strings. `correctOptionIndex` ∈ [0,1].

After filter:
- If `validQuestions.size() < max(1, count / 2)` → `IllegalStateException("AI returned too few valid questions; please retry.")`.
- Truncate to `count` and return.

### 5e. JSON parsing fallback

Existing `extractJson(...)` stays. If `type` field is missing on a question, infer from `options`: 2 options of "True"/"False" → `TRUE_FALSE`; otherwise `MULTIPLE_CHOICE`.

### 5f. Tests to add (`AiTestServiceTests`)

Use a mocked `ChatClient` (`@MockBean` or constructor-inject a stub). Cover:
- MCQ_ONLY happy path → 5 questions, all MCQ, indices in range.
- TF_ONLY happy path → all TRUE_FALSE, options normalized.
- MIXED — assert both types present.
- Lowercase `["true","false"]` → normalized to `["True","False"]`.
- Missing `type` field on response → inferred correctly.
- Malformed entries dropped; if too few left → `IllegalStateException`.
- Empty flashcards + empty docs → `IllegalArgumentException`.
- Image-only cards filtered out (use `Flashcard` with both texts blank but image set).

---

## Sub-plan: Step 7 — Wizard UI rework

Extends `plan.md` §6.

### 7a. File-by-file change list

| File | Change |
| --- | --- |
| [src/main/resources/templates/fragments/test-setup.html](src/main/resources/templates/fragments/test-setup.html) | Add Panel 0 (Mode + Difficulty); modify Panel 1 (rename to Sources, render files); add warning banner; bump JS `totalSteps` to 3; add `selectedFileIds` to every `hx-include` |
| [src/main/resources/templates/fragments/test-setup.html](src/main/resources/templates/fragments/test-setup.html) (step indicator) | Add 3rd step pill |
| [src/main/resources/static/css/...](src/main/resources/static/css/) (whichever file holds `sh-study-choice` / wizard styles) | Add `.sh-test-warn-banner` (yellow) and `.sh-test-error-banner` (red); add `.vb-file` row styles mirroring `.vb-deck`; add `.sh-segmented-control` if not present |

### 7b. Form-control names (must match controller)

```
selectedDeckIds       (existing)
selectedFileIds       (new — checkbox name on each file row)
questionMode          (new — radio name on Panel 0; values MCQ_ONLY|TF_ONLY|MIXED)
difficulty            (new — radio name on Panel 0; values EASY|MEDIUM|HARD)
questionCount         (existing)
```

### 7c. `hx-include` checklist

Every checkbox/folder-toggle and the select-all button must include **both** name selectors:

```
hx-include="[name='selectedDeckIds'], [name='selectedFileIds']"
```

Locations to update (current file uses `[name='selectedDeckIds']` only):
- `.sh-test-folder-checkbox` (top-level folder, line ~103)
- `.sh-test-deck-checkbox` (top-level deck, line ~128)
- subgroup folder checkbox (line ~152)
- subgroup deck checkbox (line ~176)
- new file checkboxes (top-level + subgroup)
- new file rows in folder bodies

### 7d. Panel 0 — Mode & Difficulty

Mirror [study-setup.html:55-80](src/main/resources/templates/fragments/study-setup.html#L55-L80) `sh-study-choice` cards. Three radio cards under name `questionMode`:

```html
<label class="sh-study-choice">
    <input type="radio" name="questionMode" value="MCQ_ONLY" checked>
    <i data-lucide="list"></i>
    <div class="sh-study-choice-title">Multiple Choice</div>
    <div class="sh-study-choice-desc">4 options, one correct.</div>
</label>
<label class="sh-study-choice">
    <input type="radio" name="questionMode" value="TF_ONLY">
    <i data-lucide="check-circle"></i>
    <div class="sh-study-choice-title">True / False</div>
    <div class="sh-study-choice-desc">Quick verification.</div>
</label>
<label class="sh-study-choice">
    <input type="radio" name="questionMode" value="MIXED">
    <i data-lucide="shuffle"></i>
    <div class="sh-study-choice-title">Mixed (50/50)</div>
    <div class="sh-study-choice-desc">Half multiple choice, half true/false.</div>
</label>
```

Below, segmented control for `difficulty` (Easy / Medium / Hard, Medium pre-checked).

### 7e. Panel 1 — Sources (deck + file picker)

Inside each `.vb-group-body` and `.vb-subgroup-body`, render files **after** decks:

```html
<label th:each="file : ${group.files}"
       class="vb-file"
       th:classappend="${!file.isSupported ? ' is-disabled' : (#lists.contains(preselectedFileIds, file.fileId) ? ' is-selected' : '')}">
    <input class="sh-checkbox sh-test-file-checkbox"
           type="checkbox"
           name="selectedFileIds"
           th:value="${file.fileId}"
           th:checked="${#lists.contains(preselectedFileIds, file.fileId)}"
           th:disabled="${!file.isSupported}"
           hx-post="/test/setup/update"
           hx-target="#test-setup-picker" hx-swap="outerHTML"
           hx-include="[name='selectedDeckIds'], [name='selectedFileIds']">
    <span class="vb-file-icon"><i data-lucide="file-text"></i></span>
    <span class="vb-file-name" th:text="${file.filename}">file.pdf</span>
    <span class="sh-badge" th:text="${file.extension.toUpperCase()}">PDF</span>
    <span class="sh-mini-tag" th:text="${#numbers.formatDecimal(file.sizeBytes / 1024.0, 0, 0) + ' KB'}">42 KB</span>
    <span th:if="${!file.isSupported}" class="sh-mini-tag sh-mini-tag-warning"
          title="File too large for AI test (max 5 MB).">too large</span>
</label>
```

Update folder toggle checkbox `disabled` condition to consider both decks and files: `selectableSourceCount == 0` (replaces `selectableDeckCount`). Update meta line to show source counts.

### 7f. Warning banners

Above the picker (inside Panel 1, above `#test-setup-picker`):

```html
<div th:if="${selectionWarn}" class="sh-test-warn-banner">
    <i data-lucide="alert-triangle"></i>
    <span>Large selection — generation may be slow.</span>
</div>
<div th:if="${selectionExceedsCap}" class="sh-test-error-banner">
    <i data-lucide="x-circle"></i>
    <span>Selection too large. Deselect some sources to continue.</span>
</div>
```

`selectionWarn` and `selectionExceedsCap` are model attributes set by `prepareSetupModel` based on the running char total of currently selected files (use the in-memory cache mentioned in §2.3).

### 7g. JS changes

In `test-setup.html` `<script>`:
- `const totalSteps = 3;` (was 2).
- Add panel-0 validation in `wizardNext()`: when `currentStep === 1`, check a `questionMode` radio is checked (always true since one is `checked` by default — keep guard anyway).
- Update step-2 (sources) validation to allow either deck or file: `const checked = document.querySelectorAll('.sh-test-deck-checkbox:checked, .sh-test-file-checkbox:checked');`.
- Update select-all button to also toggle `.sh-test-file-checkbox:not([disabled])`.
- Submit button stays on step 3.

### 7h. Progress cycler (replaces static text)

Inside `#test-generating-indicator`, replace static text with a span `id="test-progress-msg"`. Add JS:

```js
(function () {
    const messages = [
        'Reading flashcards…',
        'Reading documents…',
        'Asking Gemini…',
        'Building questions…',
        'Almost there…'
    ];
    let timer = null;
    document.body.addEventListener('htmx:beforeRequest', function (e) {
        if (e.detail.elt.matches('form[hx-post="/test/session"]')) {
            let i = 0;
            const el = document.getElementById('test-progress-msg');
            if (!el) return;
            el.textContent = messages[0];
            timer = setInterval(() => {
                i = Math.min(i + 1, messages.length - 1);
                el.textContent = messages[i];
            }, 2500);
        }
    });
    document.body.addEventListener('htmx:afterRequest', function () {
        if (timer) { clearInterval(timer); timer = null; }
    });
})();
```

### 7i. Step indicator

Add third pill between existing two:

```html
<div class="sh-wizard-step is-active" data-step="1">
    <div class="sh-wizard-step-dot">1</div>
    <div class="sh-wizard-step-label">Mode</div>
</div>
<div class="sh-wizard-step-connector"></div>
<div class="sh-wizard-step" data-step="2">
    <div class="sh-wizard-step-dot">2</div>
    <div class="sh-wizard-step-label">Sources</div>
</div>
<div class="sh-wizard-step-connector"></div>
<div class="sh-wizard-step" data-step="3">
    <div class="sh-wizard-step-dot">3</div>
    <div class="sh-wizard-step-label">Questions</div>
</div>
```

### 7j. Manual smoke test

After implementing, verify in browser:
1. Three panels render; Next/Back navigation works.
2. Default selections: MCQ_ONLY + Medium.
3. Folder checkbox toggles all decks **and** files in the folder.
4. Disabled file (>5 MB) shows tooltip; can't be checked.
5. Image-only deck shows `image-only` mini-tag (depends on Step 3 — verify after 3 lands).
6. Warning banner appears as more sources selected.
7. Submitting the form shows the cycling progress messages.
8. Each `hx-include` round-trip preserves both deck and file selections.

---

## Prompts for each implementation step

Hand each to a separate coding agent invocation. Run sequentially — each step assumes the previous compiles.

---

### Prompt — Step 1: Enums + DTO extensions

> **Task:** Add new enums and extend existing DTOs for the AI Test Mode expansion. Compile only — no behavior change yet.
>
> **Files to create** (in `src/main/java/com/HendrikHoemberg/StudyHelper/dto/`):
> - `DocumentSource.java` — enum `PDF, TXT, MD`
> - `QuestionType.java` — enum `MULTIPLE_CHOICE, TRUE_FALSE`
> - `TestQuestionMode.java` — enum `MCQ_ONLY, TF_ONLY, MIXED`
> - `Difficulty.java` — enum `EASY, MEDIUM, HARD`
>
> **Files to modify:**
> - `dto/TestQuestion.java` — add a `QuestionType type` field. Place it first. Default existing-call sites to `MULTIPLE_CHOICE`.
> - `dto/TestConfig.java` — replace with: `record TestConfig(List<Long> selectedDeckIds, List<Long> selectedFileIds, int questionCount, TestQuestionMode questionMode, Difficulty difficulty)`.
> - `dto/TestSessionState.java` — no field changes needed; `config()` already exposes the new shape via the modified `TestConfig`. Verify it still compiles.
> - `controller/TestController.java` — update the single existing `new TestConfig(normalizedIds, count)` call to `new TestConfig(normalizedIds, List.of(), count, TestQuestionMode.MCQ_ONLY, Difficulty.MEDIUM)` so the project still builds. Don't add behavior.
> - `service/AiTestService.java` — when constructing `TestQuestion` or filtering, pass `MULTIPLE_CHOICE` so it still compiles. (Existing JSON parser will fail if `type` is missing — that's fine for now; Step 5 fixes it.)
>
> **Verify:** `./mvnw compile` succeeds. Existing tests still pass: `./mvnw test`.
>
> Don't touch any HTML. Don't add any new logic beyond what's needed to compile.

---

### Prompt — Step 2: PDFBox + DocumentExtractionService

> **Task:** Add document text extraction for `.txt`, `.md`, and `.pdf` files.
>
> **Add to [pom.xml](pom.xml)** (in the `<dependencies>` section):
> ```xml
> <dependency>
>     <groupId>org.apache.pdfbox</groupId>
>     <artifactId>pdfbox</artifactId>
>     <version>3.0.3</version>
> </dependency>
> ```
>
> **Create `service/DocumentExtractionService.java`:**
> - Constructor-inject `FileStorageService`.
> - Constants: `MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024`, supported extensions set `{"pdf","txt","md"}`.
> - Public `String extractText(FileEntry file) throws IOException`:
>   - Read extension from `file.getOriginalFilename()` (lowercase, after last dot).
>   - Throw `IllegalArgumentException` if extension not supported.
>   - Resolve the path via `fileStorageService.load(file.getStoredFilename())` — get the underlying `Path` (use `resource.getFile().toPath()` or expose a new `Path resolvePath(String storedFilename)` method on `FileStorageService` if cleaner).
>   - For `.txt` / `.md`: `Files.readString(path, StandardCharsets.UTF_8)`.
>   - For `.pdf`: use `org.apache.pdfbox.Loader.loadPDF(path.toFile())` and `new PDFTextStripper().getText(document)`. Always close the document (try-with-resources).
>   - Return `text == null ? "" : text.strip()`.
> - Public helper `boolean isSupported(FileEntry file)` — extension check + `file.getFileSizeBytes() <= MAX_FILE_SIZE_BYTES`.
>
> **Tests:** create `src/test/java/.../service/DocumentExtractionServiceTests.java`. Cover:
> - `.txt` extraction returns file content trimmed.
> - `.md` extraction same.
> - Unsupported extension throws `IllegalArgumentException`.
> - `.pdf` extraction works on a tiny test PDF (generate one in-test via `PDDocument` + `PDPage` + `PDPageContentStream` writing "hello world", or check in a fixture under `src/test/resources/`).
>
> Use `@TempDir` for the upload dir if you mock `FileStorageService`.
>
> **Verify:** `./mvnw test` passes.

---

### Prompt — Step 3: `usableCardCount` on StudyDeckOption

> **Task:** Track how many flashcards in each deck have usable text for AI (front OR back has non-blank text). Surface this without breaking the study picker, which still uses `cardCount`.
>
> **Modify `dto/StudyDeckOption.java`:**
> Add `int usableCardCount` field at the end of the record. Update all `new StudyDeckOption(...)` call sites to pass it.
>
> **Modify `service/FlashcardService.java`:**
> Add public helper:
> ```java
> public boolean hasUsableTextForAi(Flashcard card) {
>     boolean hasFront = card.getFrontText() != null && !card.getFrontText().isBlank();
>     boolean hasBack = card.getBackText() != null && !card.getBackText().isBlank();
>     return hasFront || hasBack;
> }
> ```
>
> **Modify `service/FolderService.java`:**
> In `collectDecksRecursively(...)` and `toStudyDeckGroup(...)`, when building each `StudyDeckOption`, compute `usableCardCount` by streaming the deck's flashcards and counting those satisfying the helper above. The deck's flashcards are already loaded (existing `deck.getFlashcards().size()` triggers loading); reuse the same collection.
>
> **Verify:**
> - `./mvnw compile` succeeds.
> - Study picker still renders correctly (manually load `/study/start` in the dev server) — the new field is unused there.
> - All existing tests pass.
>
> Don't touch any HTML in this step. Don't change the test-setup picker yet.

---

### Prompt — Step 4: TestSourceGroup + getTestSourceTree

> **Task:** Create a parallel tree structure for the test picker that includes both decks (with `usableCardCount`) and supported files. Study-session DTOs must remain untouched.
>
> **Create `dto/TestFileOption.java`:**
> ```java
> public record TestFileOption(
>     Long fileId,
>     String filename,
>     long sizeBytes,
>     String extension,        // "pdf" | "txt" | "md"
>     boolean isSupported       // false if size > 5MB or extraction would fail
> ) implements Serializable {}
> ```
>
> **Create `dto/TestSourceGroup.java`:**
> Mirror [StudyDeckGroup.java](src/main/java/com/HendrikHoemberg/StudyHelper/dto/StudyDeckGroup.java) but add `List<TestFileOption> files`, replace `selectableDeckCount`/`totalDeckCount` with `selectableSourceCount`/`totalSourceCount` (counts decks-with-usable-text PLUS supported files), and `subGroups` is `List<TestSourceGroup>`.
>
> **Modify `service/FolderService.java`:**
> Add:
> ```java
> public List<TestSourceGroup> getTestSourceTree(User user, List<Long> selectedDeckIds, List<Long> selectedFileIds);
> ```
> Implement by walking root folders and recursively building `TestSourceGroup`. For each folder:
> - Decks: same as `toStudyDeckGroup`, but use `usableCardCount > 0` as the "usable" predicate (not `cardCount > 0`).
> - Files: filter `folder.getFiles()` to extension ∈ `{pdf, txt, md}`. For each, build `TestFileOption` with `isSupported = sizeBytes <= 5MB`.
> - `selectableSourceCount` = (decks with `usableCardCount > 0`) + (files with `isSupported`).
> - `totalSourceCount` = decks.size() + files.size() (only the filtered files).
> - `isSelected` / `isIndeterminate` consider both deck and file selections.
> - Filter out folders with no decks AND no files AND no surviving sub-groups.
>
> **Verify:** `./mvnw compile` succeeds. Add a small unit test in `FolderServiceTests` (create one if it doesn't exist) seeding a folder with one deck + one .pdf + one .png — assert the .png is filtered out and `totalSourceCount == 2`.

---

### Prompt — Step 5: AiTestService.generate(...)

> **Task:** Rewrite [AiTestService.generateQuestions](src/main/java/com/HendrikHoemberg/StudyHelper/service/AiTestService.java#L23) into a new `generate(...)` method that accepts documents, mode, and difficulty. Keep the existing private helpers where useful.
>
> **Reference detailed sub-plan:** [implementation_prompts.md §5a-5f](implementation_prompts.md). Read it first — it includes the exact prompt template, mode/difficulty wording tables, and validation rules.
>
> **Add inner record (top of `AiTestService`):**
> ```java
> public record DocumentInput(String filename, String extractedText) {}
> ```
>
> **Replace `generateQuestions` with:**
> ```java
> public List<TestQuestion> generate(
>     List<Flashcard> flashcards,
>     List<DocumentInput> documents,
>     int count,
>     TestQuestionMode mode,
>     Difficulty difficulty
> );
> ```
>
> Follow the prompt template in §5c verbatim. Implement validation per §5d:
> - Per-question: drop malformed; normalize TF options (case-insensitive `true`/`false` → `"True"`/`"False"`).
> - If type is missing on a question, infer from options shape.
> - If `validQuestions.size() < max(1, count / 2)` → throw `IllegalStateException("AI returned too few valid questions; please retry.")`.
>
> **Pre-flight:**
> - If both flashcard text AND documents produce blank content → `IllegalArgumentException("Selected sources contain no usable text. Pick a deck with text or a document with extractable content.")`.
>
> **Update `TestController.createSession` call site:** pass through the new params (default `MCQ_ONLY` / `MEDIUM` until Step 6 adds the form fields).
>
> **Add `AiTestServiceTests`** under `src/test/java/.../service/`. Use a stubbed `ChatClient` (return canned JSON from `.call().content()`). Cover the cases listed in §5f. Use Spring Boot's `@MockitoBean` or a hand-rolled stub builder — whichever the project pattern is (check existing `StudySessionServiceTests` style).
>
> **Verify:** `./mvnw test` passes. Manually exercise the test flow once via dev server with a deck-only selection — should still work end-to-end.

---

### Prompt — Step 6: TestController extensions

> **Task:** Wire the controller for the new params. Don't touch HTML in this step (the wizard already submits old params; new UI lands in Step 7).
>
> **Modify [TestController](src/main/java/com/HendrikHoemberg/StudyHelper/controller/TestController.java):**
>
> 1. **`/test/setup/update`** — add params:
>    - `selectedFileIds: List<Long>`, `toggledFileId: Long`, `removeFileId: Long`.
>    - Mirror existing deck-toggle logic for files (same `normalizeIds` helper, generalized).
>    - Folder toggle: select/deselect **all usable decks AND all supported files** in that folder. Add a `folderService.getAllSourcesInFolder(folderId, user)` helper or extend `getAllDecksInFolder` to return both.
>    - Replace the `prepareSetupModel(...)` call to also pass `selectedFileIds` and use `getTestSourceTree(...)`.
>
> 2. **`/test/session`** — add params:
>    - `selectedFileIds: List<Long>` (default empty)
>    - `questionMode: TestQuestionMode` (default `MCQ_ONLY`)
>    - `difficulty: Difficulty` (default `MEDIUM`)
>    - Validate at least one source selected (deck OR file) — error message: `"Please select at least one deck or document."`
>    - Inject `DocumentExtractionService`. For each selected file: load `FileEntry` via a new `FileEntryService.getByIdAndUser(id, user)` (add it if missing — confirm ownership), call `extractText`, build `DocumentInput`. Sum char counts; if total > `150_000` → BAD_REQUEST with `"Selection too large — please deselect some sources."`.
>    - Replace `aiTestService.generateQuestions(...)` with `aiTestService.generate(flashcards, documents, count, questionMode, difficulty)`.
>    - Persist mode + difficulty in `TestSessionState` via the new `TestConfig` shape.
>
> 3. **New `GET /test/start` query-param shortcut** — already routed; extend signature:
>    - `@RequestParam(required = false) Long deckId, @RequestParam(required = false) Long fileId`
>    - If `deckId`: validate ownership via `deckService`; pre-fill `preselectedDeckIds = List.of(deckId)`.
>    - If `fileId`: validate ownership + supported extension; pre-fill `preselectedFileIds = List.of(fileId)`.
>    - On invalid input, fall through to the empty wizard with an error.
>
> 4. **`prepareSetupModel`** — extend signature to take `selectedFileIds` and add model attributes: `fileGroups` (or rename `deckGroups` → `sourceGroups` if you prefer; pick one and update the template-side names in Step 7), `preselectedFileIds`, `selectionTotalChars`, `selectionWarn` (true if total ≥ 50K), `selectionExceedsCap` (true if total > 150K).
>    - Char totals: compute by iterating selected files, calling `DocumentExtractionService.extractText` once per file. Cache in a `HttpSession` attribute `Map<Long,Integer>` keyed by file ID to avoid repeated extraction on every checkbox toggle.
>
> **Verify:** `./mvnw compile` succeeds. Existing controller tests still pass (update them to pass the new defaults). The page won't visually change yet — the form still submits old field names — that's expected.

---

### Prompt — Step 7: Wizard UI rework

> **Task:** Restructure the test setup wizard from 2 panels to 3, render files inline in the picker, add warning banners, and replace the static loading text with a cycling progress message.
>
> **Reference detailed sub-plan:** [implementation_prompts.md §7a-7j](implementation_prompts.md). Read it first — it includes the file-by-file checklist, exact form-control names, the full `hx-include` checklist, the Panel 0 markup, the file-row markup, and the JS for the progress cycler.
>
> **Files to modify:**
> - [src/main/resources/templates/fragments/test-setup.html](src/main/resources/templates/fragments/test-setup.html) — primary work.
> - CSS: locate the file containing `.sh-study-choice` and `.sh-wizard-panel` styles (grep under `src/main/resources/static/css/`). Add `.vb-file`, `.sh-test-warn-banner`, `.sh-test-error-banner`, `.sh-segmented-control` if missing.
>
> **Critical correctness rules:**
> 1. Every checkbox/toggle that posts to `/test/setup/update` MUST `hx-include="[name='selectedDeckIds'], [name='selectedFileIds']"` (both selectors). Locations enumerated in §7c.
> 2. Form-control names must match controller params exactly: `selectedDeckIds`, `selectedFileIds`, `questionMode`, `difficulty`, `questionCount`. See §7b.
> 3. Default-checked: `MCQ_ONLY`, `MEDIUM` (Medium difficulty).
>
> **Step indicator:** 3 pills — Mode / Sources / Questions (§7i).
>
> **JS changes:**
> - `totalSteps = 3`.
> - Validation on step 2 (Sources) accepts deck-or-file: `'.sh-test-deck-checkbox:checked, .sh-test-file-checkbox:checked'`.
> - Select-all also toggles `.sh-test-file-checkbox`.
> - Add the htmx-progress-cycler IIFE per §7h.
>
> **Image-aware deck rendering:** disabled deck label is `image-only` when `usableCardCount == 0` && `cardCount > 0`; tooltip per §6.2 of plan.md. Mixed-card deck shows `8 usable / 12 cards`.
>
> **Verify manually in dev server:**
> - All 7 steps in §7j.
> - Especially: toggle a folder checkbox, verify both decks AND files in that folder flip together.
> - Toggle a file checkbox, then a deck checkbox in another folder — verify both stay checked after the HTMX swap (this is the `hx-include` correctness test).

---

### Prompt — Step 8: "Test me on this" shortcuts

> **Task:** Add three entry points that pre-select a source in the test wizard.
>
> **Files to modify:**
>
> 1. **[src/main/resources/templates/fragments/deck.html](src/main/resources/templates/fragments/deck.html)** — locate the deck header actions row (where edit / study buttons live). Add:
>    ```html
>    <a class="sh-btn sh-btn-secondary"
>       th:href="@{'/test/start'(deckId=${deck.id})}"
>       hx-get="/test/start" th:hx-vals="${'{&quot;deckId&quot;: ' + deck.id + '}'}"
>       hx-target="#explorer-detail" hx-push-url="true">
>        <i data-lucide="sparkles"></i> Test me
>    </a>
>    ```
>
> 2. **[src/main/resources/templates/fragments/folder-detail.html](src/main/resources/templates/fragments/folder-detail.html)** — find the file row action cluster. For each file row, add a "Test" button **only if** the file extension is `pdf`/`txt`/`md` (use Thymeleaf `th:if` checking the filename suffix). Same `hx-get="/test/start"` with `fileId` param.
>
> 3. **[src/main/resources/templates/fragments/explorer.html](src/main/resources/templates/fragments/explorer.html)** lines 88-110 region (document tiles) — for tiles whose extension is pdf/txt/md, add a hover action button (sparkles icon) → `GET /test/start?fileId={id}`. Position alongside the existing edit-options button so tile layout is undisturbed.
>
> **Verify:**
> - From a deck page, click "Test me" → wizard opens with that deck pre-checked, others unchecked.
> - From a folder file row, click test → wizard opens with that file pre-checked.
> - From dashboard tile, hover → button appears → click → wizard opens with file pre-checked.
> - The pre-selection respects ownership (try with a foreign ID via URL bar — should fall through cleanly without error).
>
> Don't add tests for these; they're pure templating.

---

### Prompt — Step 9: Manual QA pass

> **Task:** Run the dev server and walk through every flow end-to-end. This is exploratory — produce a punch list of anything broken or off, don't fix in this step (file separate tickets / fixes).
>
> **Test matrix:**
>
> | # | Scenario | Expected |
> | --- | --- | --- |
> | 1 | Empty source selection → submit | 400, error banner: "select at least one deck or document" |
> | 2 | Upload a 6 MB PDF, view picker | File row disabled with "too large" badge + tooltip |
> | 3 | Select sources totaling ~60K chars | Yellow warning banner appears |
> | 4 | Select sources totaling ~160K chars | Red error banner; submit is rejected with the cap message |
> | 5 | Deck with all image-only cards | Disabled, label `image-only`, tooltip explains |
> | 6 | Deck with 12 cards, 4 image-only | Enabled, badge shows `8 usable / 12 cards` |
> | 7 | Mixed deck + PDF test, MCQ_ONLY, MEDIUM | Returns N MCQ questions, mostly on-topic |
> | 8 | Same, TF_ONLY | Returns N true/false questions; options always `["True","False"]` |
> | 9 | Same, MIXED, count=6 | ~3 MCQ + ~3 TF |
> | 10 | EASY vs HARD on same source | Subjective: HARD distractors more plausible |
> | 11 | "Test me" on deck page | Wizard pre-selects deck only |
> | 12 | "Test me" on folder file row (pdf) | Wizard pre-selects file only |
> | 13 | "Test me" on dashboard document tile | Wizard pre-selects file only |
> | 14 | Toggle folder checkbox with mixed decks + files inside | All decks AND all supported files flip together |
> | 15 | After HTMX swap, toggle file in folder A, then deck in folder B | Both selections persist |
> | 16 | Submit form → progress messages cycle | Reads "Reading flashcards…" → "Almost there…" |
> | 17 | Image-only PDF (scanned) | <50 chars detected; surface tile-level warning |
> | 18 | Refresh mid-test | Session preserved (not in scope to save tests, but in-flight session should survive) |
>
> Report each as PASS / FAIL with the actual behavior. Capture screenshots of any visual regressions.
>
> **Don't fix issues in this step** — produce the punch list only.
