# AI Prompt Optimization — Design

**Date:** 2026-05-12
**Status:** Approved
**Scope:** `AiQuizService`, `AiExamService`, `AiFlashcardService`

## Goals

1. **Robustness.** Eliminate brittle regex-based JSON extraction by using Gemini's server-side schema enforcement via Spring AI structured output.
2. **Output language matching.** Generated content is written in the dominant language of the source material instead of defaulting to English.
3. **Coverage balance.** For large documents, questions are distributed across the entire source rather than clustering on the first pages.

The Gemini model stays fixed at `gemini-3.1-flash-lite-preview` (per `application.properties`).

## Non-goals

- Automatic retries on AI failure (defer until we observe whether structured output makes them unnecessary).
- Refactoring duplicated prompt-builder helpers into a shared utility.
- Per-task model swaps.
- Two-pass coverage pipelines (outline → questions).

## Section 1 — Structured output

### Wrapper response DTOs

Add to `dto/`:

- `QuizQuestionsResponse(List<QuizQuestion> questions)`
- `ExamQuestionsResponse(List<ExamQuestion> questions)`
- `FlashcardsResponse(List<GeneratedFlashcard> flashcards)`

`ExamGradingResult` already fits and needs no wrapper.

### Per-service changes

In each of `AiQuizService.generate`, `AiExamService.generate`, `AiExamService.grade`, `AiFlashcardService.generate`:

1. Configure `GoogleGenAiChatOptions` for the call:
   - `responseMimeType = "application/json"`
   - `responseSchema = <JSON schema string for the wrapper response>`
2. Generate the schema string once (at construction or first use) from the wrapper class — either via Spring AI's `BeanOutputConverter` or `org.springframework.ai.google.genai.schema.JsonSchemaConverter`. Cache the string in a final field on the service.
3. Replace the existing call chain
   ```
   chatClient.prompt().user(...).call().content() + extractJson(...) + objectMapper.readTree(...)
   ```
   with
   ```
   chatClient.prompt().options(<options>).user(...).call().entity(<WrapperResponse>.class)
   ```
4. Delete the `extractJson` private helper from all three services.
5. Delete the inline `"Respond ONLY with valid JSON, no extra text. Schema: …"` block from the end of each prompt. Schema is enforced server-side; the converter handles parsing.

### Validation kept post-parse

- `AiQuizService.normalizeQuestion` continues to enforce T/F option strings, MCQ option count, and index range.
- The "too few valid items" floor (`count / 2`) stays in all three services.
- The catch-all `IllegalStateException("Could not parse the AI response. Please try again.")` stays, since `BeanOutputConverter` can still throw on type-coercion edge cases.

## Section 2 — Language-matching rule

Insert the following block near the top of each **generation** prompt (Quiz, Exam, Flashcard generation), immediately under the role line:

```
LANGUAGE:
Detect the dominant natural language of the supplied source material (flashcards,
documents, and attached PDFs together). Write every output string — question text,
answer options, flashcard front/back, hints — in that same language. If sources
mix languages, use the most-prevalent one. Do not translate technical terms,
proper nouns, or code.
```

### Carve-outs

- **T/F option labels.** `AiQuizService.normalizeQuestion` continues to rewrite T/F options as the fixed English strings `["True","False"]`. `QuestionType` enum values (`MULTIPLE_CHOICE`, `TRUE_FALSE`) remain English protocol tokens. Localized rendering of T/F option labels is a UI concern, out of scope here.
- **Schema field names.** `questionText`, `options`, `frontText`, etc. are structural identifiers, not content — unaffected by the language rule.

### Exam grading

`AiExamService.grade` gets the same LANGUAGE block, but pinned to the language of the **questions and user answers** (the grader does not see the original sources). All of `feedback`, `strengths`, `weaknesses`, `topicsToRevisit`, and `suggestedNextSteps` are emitted in that language.

## Section 3 — Coverage-balance rule

Insert into `AiQuizService` and `AiExamService` generation prompts immediately after the `TOPIC FOCUS` block:

```
COVERAGE:
Distribute the N requested questions evenly across the entire source material.
- Treat each attached PDF and each text document as a separate source.
- Within each source, sample roughly equally from the early third, middle third,
  and final third (by page count for PDFs, by length for text).
- If multiple sources are supplied, allocate questions proportionally to source
  length, but ensure every source contributes at least one question when N is
  large enough.
- Do not cluster on introductions, abstracts, tables of contents, or the first
  few pages. Skip these unless they contain core subject matter.
- Before writing questions, sketch a brief internal coverage plan (which
  pages/sections each question will draw from). Do not include the plan in
  the JSON output.
```

The "internal coverage plan" step is intentional: Gemini Flash-Lite responds well to a brief planning step framed as internal reasoning, even when output is schema-constrained. The extra output tokens are small relative to the coverage gain.

### Flashcard variant

`AiFlashcardService` gets a lighter version of the rule, since flashcards extract everything worthwhile rather than sampling. Insert in place of the full block:

```
COVERAGE:
Draw flashcards from across the entire document. Do not concentrate on the
opening pages; ensure later sections are represented proportionally to their
content density.
```

### Not applied

`AiExamService.grade` — no source material, rule is N/A.

## Section 4 — Validation & retry behavior

**Stays as-is:**

- `AiQuizService.normalizeQuestion` runs after structured parsing as a defense-in-depth check.
- `count / 2` validity floor in all three services.
- User-facing error wording on AI failure and parse failure stays identical.

**Changes:**

- Parse-failure path is exercised much less often (schema-enforced JSON), but the `try`/`catch` around the converter call is retained.

**Out of scope:**

- No automatic retry on AI failure. Re-evaluate after rollout if failures persist.

## Section 5 — Testing

Update existing test files (no new files):

- `AiQuizServiceTests.java`
- `AiExamServiceTests.java`
- `AiFlashcardServiceTests.java`

### Changes per test file

- Update the mocked `ChatClient` chain so `.entity(<WrapperResponse>.class)` returns a typed wrapper, replacing existing setups that stubbed `.content()` with a raw JSON `String`.
- Preserve existing assertions for validation, normalization, and the `count / 2` floor.
- Add one test: assert the rendered prompt text contains the literal headings `LANGUAGE:` and `COVERAGE:` (or, for `AiExamService.grade`, only `LANGUAGE:`).
- Add one test: assert the chat options passed to the call carry `responseMimeType = "application/json"` and a non-blank `responseSchema`.

No live model calls. No integration tests. No retry tests (no retry exists).

## Files touched

**New:**

- `dto/QuizQuestionsResponse.java`
- `dto/ExamQuestionsResponse.java`
- `dto/FlashcardsResponse.java`

**Modified:**

- `service/AiQuizService.java`
- `service/AiExamService.java`
- `service/AiFlashcardService.java`
- `test/.../service/AiQuizServiceTests.java`
- `test/.../service/AiExamServiceTests.java`
- `test/.../service/AiFlashcardServiceTests.java`

**Unchanged:** application.properties, DTOs other than the new wrappers, controllers, persistence, UI templates.
