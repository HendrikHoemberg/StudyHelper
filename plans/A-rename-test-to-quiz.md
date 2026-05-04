# Sub-plan A — Rename Test → Quiz

**Spec reference:** [`plan.md` §2](../plan.md), [`plan.md` §5.2](../plan.md), [`plan.md` §7 Phase A](../plan.md).

**Goal:** Rename the existing AI MCQ/TF mode from "Test" to "Quiz" project-wide. **No behavior changes.** This frees the word "Test" so the new Exam mode doesn't collide.

## Scope

Mechanical rename only. Do not refactor, do not change routes' shape beyond the `/test` → `/quiz` swap, do not touch the wizard structure.

## Files to rename

Use the table at [`plan.md:312-328`](../plan.md#L312-L328) as the source of truth. In short:

- Java: `TestController`, `AiTestService`, all DTOs starting with `Test*`.
- Templates: `test-page.html`, `fragments/test-setup.html`, `fragments/test-question.html`, `fragments/test-summary.html`.
- CSS: `static/css/test.css`.
- Routes: every `/test/...` → `/quiz/...`.
- Method names: e.g. `FolderService.getTestSourceTree(...)` → `getQuizSourceTree(...)`.

Verify completeness with `git grep -i "test"` in `src/main/` after the change. **Filter manually** — JUnit imports, the word "test" in unrelated comments, and the literal `pom.xml` test dependencies must NOT be renamed. The rename only applies to references to the AI MCQ/TF feature.

## Entry-point links to update

These templates contain hard-coded `/test/...` URLs that must be flipped to `/quiz/...`. They will all be replaced again in sub-plan B3, but for now the app must keep working as-is.

- `templates/fragments/sidebar.html` — "Test Mode" CTA.
- `templates/fragments/explorer.html` — explorer toolbar "Test Mode" button.
- `templates/fragments/folder-detail.html` — any test-mode entry buttons.
- `templates/fragments/deck.html` — "Test" button on deck cards.
- File-row "Test" buttons (likely in `folder-children.html` or wherever file rows render).

User-visible button labels stay as "Test Mode" / "Test" for now — those copy changes happen in B3 alongside the entry-point consolidation. Only the URLs and code identifiers change in this sub-plan.

## Verification before commit

1. `./mvnw -q -DskipTests package` compiles cleanly.
2. Boot the app, log in, and click through:
   - Sidebar "Test Mode" → setup wizard renders → pick deck → generate questions → answer → summary.
   - Same flow from a deck card "Test" button.
   - Same flow from a file row "Test" button.
3. `git grep -nE '/test/(start|setup|session|answer|next)' src/main/` returns **no matches**.
4. `git grep -nE '\bTest(Controller|Config|Question|SessionState|FileOption|SourceGroup|QuestionMode|Service|Setup)\b' src/main/java/` returns **no matches** (the regex word-boundaries avoid JUnit hits).

## Commit message

```
refactor: rename Test mode to Quiz across controllers, services, DTOs, templates

Renames every reference to the AI MCQ/TF feature from "Test" to "Quiz" so
the word "Test" is free for the upcoming long-form Exam mode. Pure rename;
no behavior change.
```
