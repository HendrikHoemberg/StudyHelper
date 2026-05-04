# Sub-plan D — Past Exams list + detail + rename + delete

**Spec reference:** [`plan.md` §4.8](../plan.md), [`plan.md` §6.4](../plan.md), [`plan.md` §7 Phase D](../plan.md).

**Goal:** Replace the stubbed `/exams` and `/exams/{id}` responses with real UI. Wire inline rename and delete.

## Templates to add

| File | Used by |
|------|---------|
| `templates/exams-page.html` | `GET /exams` host page. |
| `templates/fragments/exams-list.html` | Card grid — list of all exams for the current user. |
| `templates/fragments/exam-detail.html` | `GET /exams/{id}` content. |

## Markup details

### `exams-list.html`

Card grid (model on the existing `sh-card` style used by deck cards in [`fragments/deck.html`](../src/main/resources/templates/fragments/deck.html)). Each card shows:

- Title.
- Taken date (formatted, e.g. `12 May 2026`).
- Source summary (truncated to 1–2 lines).
- Score circle (color-coded: green ≥80, amber 60–79, red <60). Reuse `.sh-exam-result-score` from `exam.css`.
- "View" button → `/exams/{id}`.
- Delete icon button — match the existing deck-delete UX (hover-confirm or modal — copy whichever pattern `deck.html` uses today). On confirm: `hx-delete="/exams/{id}"`, target the card's container, swap `outerHTML` so the card disappears in place.

Empty state (no exams): a centered message + a CTA "Take your first exam" → `/study/start?mode=EXAM`.

Newest first. Server provides the list pre-sorted via `ExamRepository.findAllByUserOrderByCreatedAtDesc`.

### `exam-detail.html`

Header strip:

- Title — inline-editable. Match the existing inline-edit pattern in the codebase if there is one; otherwise: click pencil icon → title becomes an input → blur or Enter posts `/exams/{id}/rename` (form field `title`) → server returns updated header fragment.
- Score circle.
- Taken date + config summary (size, count, timer if used).
- Delete button (top right) — same hover-confirm/modal pattern as the list view. On confirm: `hx-delete`, redirect to `/exams` on success (use `HX-Redirect` response header).

Body:

- Overall report — 4 panels (Strengths / Weaknesses / Topics to revisit / Next steps). Reuse `.sh-exam-result-section` styles from `exam.css`. Read from `ExamReport` (deserialized from `Exam.reportJson`).
- Per-question accordion — `<details>` per question, sorted by `position`. Each contains question text, user answer (or italic "Not answered"), score, feedback. Reuse `.sh-exam-result-question` styles.

## Controller — replace D stubs

`ExamController`:

- `GET /exams` — fetch `examService.listForUser(currentUser)`, render `exams-page.html` (full page) or `exams-list.html` (HTMX). Pass into the template.
- `GET /exams/{id}` — fetch `examService.getOwnedById(currentUser, id)`. Deserialize `reportJson` into `ExamReport` server-side and pass alongside the `Exam`. Render `exams-page.html` host with `exam-detail.html` fragment, or just the fragment for HTMX.
- `POST /exams/{id}/rename` — read `title` form field, `examService.renameOwned(currentUser, id, title)`, return the updated header fragment (or just the title element).
- `DELETE /exams/{id}` — `examService.deleteOwned(currentUser, id)`. For HTMX requests from the list, return empty body (the card swap handles removal). For HTMX requests from the detail page, return `HX-Redirect: /exams`. For non-HTMX (defensive), redirect to `/exams`.

Ownership errors → 404 (don't leak existence). `ExamService` already throws on bad ownership per C1; map to 404 in the controller.

## Sidebar link

The sidebar already has the "Past Exams" entry from B3. No change needed — it now leads somewhere real.

## Verification before commit

1. `./mvnw -q -DskipTests package` compiles.
2. With at least one persisted exam from C3 testing:
   - Sidebar "Past Exams" → list renders, card shows title/date/source/score, color-coded.
   - Click "View" → detail page renders with header, 4 report panels, per-question accordion.
   - Click pencil on title → edit → save → header updates with new title.
   - Reload → renamed title persists.
   - Delete from detail → redirects to `/exams`, exam is gone.
   - Delete from list → card disappears in place via HTMX swap.
3. As a different user (or with no exams): `/exams` shows empty state with CTA.
4. Try `GET /exams/<other-user-id>` → 404.
5. Take a fresh exam end-to-end → it appears at the top of `/exams`.

## Commit message

```
feat: add Past Exams list and detail views with rename and delete

Wires GET /exams (card grid) and GET /exams/{id} (header + report +
per-question accordion). Adds inline title rename and ownership-checked
delete from both views. The previously stubbed sidebar Past Exams link
now lands on a real page.
```
