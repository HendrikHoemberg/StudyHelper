# Sub-plan C1 — Exam backend (entities, DTOs, services, controller)

**Spec reference:** [`plan.md` §4.1, §4.4-4.7](../plan.md), [`plan.md` §5.1, §5.3-5.4](../plan.md), [`plan.md` §7 Phase C steps 1-6](../plan.md).

**Goal:** Build the entire backend for Exam mode — entities, repository, DTOs, AI service (generate + grade), persistence service, and the controller — with **no UI**. The controller's HTML returns can be stub fragments returning `"TODO C3"` or a minimal placeholder; C3 wires real templates to them.

## Config

Add to `application.properties`:

```
server.servlet.session.timeout=1d
```

(Spec: [`plan.md:481, §8`](../plan.md#L481).)

## DTOs to add

All under `com.HendrikHoemberg.StudyHelper.dto`:

| File | Type | Shape |
|------|------|-------|
| `ExamQuestionSize.java` | enum | `SHORT, MEDIUM, LONG, MIXED` |
| `ExamLayout.java` | enum | `PER_PAGE, SINGLE_PAGE` |
| `ExamQuestion.java` | record | `(String questionText, String expectedAnswerHints)` |
| `ExamGradingResult.java` | record | matches the JSON in [`plan.md:209-225`](../plan.md#L209-L225); contains `List<PerQuestion>` and `Overall` (nested records). |
| `ExamReport.java` | record | `(int scorePercent, List<String> strengths, List<String> weaknesses, List<String> topicsToRevisit, List<String> suggestedNextSteps)` — used both at runtime and as the persisted `reportJson`. |
| `ExamConfig.java` | record | `(List<Long> deckIds, List<Long> fileIds, ExamQuestionSize size, int count, Integer timerMinutes, ExamLayout layout)` |
| `ExamSessionState.java` | record | `(ExamConfig config, List<ExamQuestion> questions, Map<Integer,String> answers, Instant startedAt, String sourceSummary)` — held in `HttpSession` until submit. |

## Entities to add

Per [`plan.md §4.7`](../plan.md):

- `entity/Exam.java` — fields per the table at [`plan.md:233-247`](../plan.md#L233-L247). `reportJson` is `@Lob String` storing a Jackson-serialized `ExamReport`.
- `entity/ExamQuestionResult.java` — fields per [`plan.md:251-260`](../plan.md#L251-L260). `position` is 0-based.

`spring.jpa.hibernate.ddl-auto=update` will create the tables. Don't write migrations.

## Repository

`repository/ExamRepository.java`:

```java
public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findAllByUserOrderByCreatedAtDesc(User user);
}
```

## Services

### `service/AiExamService.java`

Mirror the structure of the existing `AiQuizService` (formerly `AiTestService`) — same `ChatClient`, same JSON-parsing helper, same error normalization.

Two public methods:

```java
List<ExamQuestion> generate(List<Flashcard> flashcards,
                            List<DocumentSource> documents,
                            int questionCount,
                            ExamQuestionSize size);

ExamGradingResult grade(List<ExamQuestion> questions,
                        Map<Integer, String> userAnswers,
                        ExamQuestionSize size);
```

**Generation prompt** — model on the existing quiz generation prompt; differences:

- No options / correctOptionIndex.
- Schema: `{"questions":[{"questionText":"...","expectedAnswerHints":"..."}]}`.
- Append the size-specific instruction:
  - `SHORT`: "Each question should require a brief recall or definition answer (1–2 sentences, ~50 words)."
  - `MEDIUM`: "Each question should require an explanatory answer (~150 words). Test understanding, not just recall."
  - `LONG`: "Each question should require an essay-style answer (~400 words). Test synthesis, application, or comparison."
  - `MIXED`: "Mix all three depths across the questions. Choose the most appropriate depth per topic."
- `expectedAnswerHints` is a 2–3 sentence rubric — never shown to the user, used during grading.
- Reuse the same "topic focus" / "skip cards lacking context" instructions from the quiz prompt.

**Grading prompt** — input shape per [`plan.md:188-194`](../plan.md#L188-L194); output shape per [`plan.md:209-225`](../plan.md#L209-L225). Instructions per [`plan.md:198-208`](../plan.md#L198-L208):

- Score each answer 0–100 (integer).
- Per-question feedback: 2–4 sentences. State what was correct, what was missing/incorrect, and what was expected.
- Blank/whitespace-only answers → `scorePercent: 0`, `feedback: "Not answered."`
- Overall report: mean score (rounded), 2–4 strengths, 2–4 weaknesses, 3–6 topics to revisit (short noun phrases tied to source material), 2–3 suggested next steps.

If the AI returns malformed JSON, raise the same exception type `AiQuizService` raises.

### `service/ExamService.java`

```java
Exam saveCompleted(User user, ExamSessionState state, ExamGradingResult grading);
List<Exam> listForUser(User user);
Exam getOwnedById(User user, Long examId);   // throws if not found or wrong owner
void deleteOwned(User user, Long examId);
void renameOwned(User user, Long examId, String newTitle);
```

`saveCompleted`:

- Builds `Exam` (auto-titled from `state.sourceSummary()` and the current date — e.g. `"Exam · Biology Deck + 2 docs · 12 May 2026"`).
- `completedAt = LocalDateTime.now()`. `createdAt = state.startedAt` converted to `LocalDateTime`.
- `overallScorePct = grading.overall().scorePercent()`.
- `reportJson` = Jackson-serialized `ExamReport` built from `grading.overall()`.
- Children: one `ExamQuestionResult` per question, position-indexed, snapshotting `questionText`, `expectedAnswerHints`, `userAnswer`, `scorePercent`, `feedback`.

Ownership checks: `getOwnedById`, `deleteOwned`, `renameOwned` all 404 if the exam isn't owned by the user.

### `FolderService`

If sub-plans A/B haven't already done so, ensure `getQuizSourceTree(...)` exists. Exam reuses it — no Exam-specific copy. (Spec: [`plan.md:370`](../plan.md#L370).)

## Controller

`controller/ExamController.java` — endpoints per [`plan.md:353-361`](../plan.md#L353-L361):

```
POST /exam/session    Generate questions, store ExamSessionState in HttpSession, return run-view fragment (stub OK in C1)
POST /exam/answer     PER_PAGE only. Save answer at index, render same/next question fragment (stub OK)
GET  /exam/next       PER_PAGE only. Advance.
GET  /exam/prev       PER_PAGE only.
POST /exam/submit     Grade, persist via ExamService, return result fragment (stub OK)
GET  /exams           List view (stub OK — D fills in)
GET  /exams/{id}      Detail view (stub OK — D)
POST /exams/{id}/rename  Inline rename (D)
DELETE /exams/{id}    Delete with ownership (D)
```

For C1, "stub OK" means: handler runs the real backend logic (generate / save answer / grade / persist), then returns a tiny inline `<div>` with debug info (e.g. `"Generated N questions"`). The route exists and works against curl/HTTP; C3 swaps the response body for real templates.

`POST /exam/session` accepts an `ExamConfig` form binding. The unified wizard's `POST /study/session` (with `mode=EXAM`) delegates here — wire that delegation in `StudyController` now (replace the 501 placeholder from B1).

`HttpSession` key for the in-progress exam: `"examSession"`. Starting a new one **silently overwrites** any prior one (per [`plan.md §8`](../plan.md)).

All routes follow the existing `@RequestHeader(value = "HX-Request", required = false)` pattern for full-page vs fragment responses. For C1 stubs, returning a fragment is fine even on full-page request.

## Verification before commit

1. `./mvnw -q -DskipTests package` compiles.
2. Boot, log in. From the unified wizard, manually craft a request:
   ```
   POST /exam/session
     deckIds=<id>&size=SHORT&count=2&timerMinutes=10&layout=PER_PAGE
   ```
   Expect: 2xx, response says "Generated 2 questions" or similar. Server log shows the AI generate call succeeded.
3. `POST /exam/answer` with `index=0&answer=foo` updates session state (verify by setting a breakpoint or logging).
4. `POST /exam/submit` triggers grading and persists. Check the H2/Postgres console: a row exists in `exam` and N rows in `exam_question_result`. `report_json` is valid JSON deserializable to `ExamReport`.
5. `GET /exams` returns 200 (stub body is fine).

## Commit message

```
feat: add Exam mode backend — entities, AI generate/grade, persistence

Adds Exam and ExamQuestionResult entities, the ExamConfig/ExamSessionState
DTO family, AiExamService for generation and grading, ExamService for
persistence, and ExamController endpoints. Bumps server session timeout
to 1 day so long exams aren't lost. UI is stubbed — templates land in C3.
```
