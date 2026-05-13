# AI Generation Instructions Design

## Context

StudyHelper has three AI generation entry points:

- AI flashcard deck generation from one PDF.
- AI quiz generation from selected decks and documents.
- AI exam question generation from selected decks and documents.

Flashcard generation uses its own form at `/flashcards/generate`. Quiz and exam generation share the study setup wizard and submit through `/study/session`, with exam generation delegated to `ExamController`. Exam grading is also AI-backed, but it is outside this feature.

Users need an optional way to give the AI a short request-specific note, such as "focus on the HTML part of this document." This should not force extra UI into every workflow, and it must not ask users for instructions before showing validation errors that can already be detected.

## Goals

- Add optional user instructions for flashcard, quiz, and exam generation.
- Keep the current primary generation buttons fast: they submit immediately with no extra prompt.
- Add visible secondary buttons that generate with instructions.
- Validate the current request before opening the instructions modal.
- Reuse existing error behavior when validation fails.
- Ensure preflight validation never records quota and never calls the AI provider.
- Keep server-side validation in the real generation endpoints.

## Non-Goals

- Do not add instructions to exam grading.
- Do not persist or prefill previous user instructions.
- Do not add an always-visible textarea to the generation forms.
- Do not let additional instructions override output schema, ownership checks, quota checks, or safety rules.

## User Flow

Each generation UI gets a visible secondary action beside the existing primary action:

- Flashcards: `Generate flashcards` and `Generate with instructions`.
- Quiz: `Generate Quiz` and `Generate with instructions`.
- Exam: `Start Exam` and `Start with instructions`.

The primary action keeps the existing behavior and submits immediately.

The secondary action follows this sequence:

1. Run the same client-side step validation that already protects the wizard where applicable.
2. Send the current form values to a server-side preflight endpoint.
3. If preflight returns an error, show the existing error UI and do not open the instructions modal.
4. If preflight succeeds, open a small modal with an optional textarea.
5. If the user cancels, do not submit.
6. If the user confirms, place the trimmed text into a hidden `additionalInstructions` field and submit the original generation form.

Empty instructions are allowed. Confirming an empty modal produces the same generation behavior as the primary button.

## Preflight Validation

Preflight endpoints validate only conditions that can be checked before the AI call:

- User is authenticated.
- Selected sources exist and belong to the user.
- Required selections are present.
- Generated flashcard destination is valid.
- New flashcard deck name does not conflict.
- Selected files are supported PDFs where required.
- Text-mode extraction produces usable content.
- Quiz and exam source selections stay under the existing size cap.
- Quiz and exam counts and options are valid.

Preflight endpoints must not call:

- `AiFlashcardService`
- `AiQuizService`
- `AiExamService`
- `AiRequestQuotaService.checkAndRecord`

The actual generation endpoints still repeat validation before recording quota or calling AI. This protects direct POSTs, stale form state, and races after a successful preflight.

## API Shape

Add preflight routes that accept the same relevant request parameters as the real generation endpoints:

- `POST /flashcards/generate/preflight`
- `POST /study/session/preflight`

`/study/session/preflight` branches by `StudyMode`. For `QUIZ` and `EXAM`, it validates the generation request. For `FLASHCARDS`, it can validate the non-AI study session path normally, but no instruction button is needed for that mode because flashcard study sessions are not AI generation.

Preflight success returns `204 No Content`. Preflight failure returns a `400` response with the same error markup conventions currently used by AI generation failures, so existing client parsing can show the error consistently.

The real generation endpoints accept:

- `additionalInstructions` on `/flashcards/generate`
- `additionalInstructions` on `/study/session`
- an internal `additionalInstructions` parameter when `StudyController` delegates exam generation to `ExamController`

## Prompt Integration

AI services get overloads or updated signatures that accept optional instructions:

- `AiFlashcardService.generate(DocumentInput document, String additionalInstructions)`
- `AiQuizService.generate(..., String additionalInstructions)`
- `AiExamService.generate(..., String additionalInstructions)`

Existing call sites can use an overload with no instructions or pass `null`.

The service layer normalizes instructions by trimming whitespace and enforcing a maximum length of 1,000 characters. Blank input is ignored.

When present, instructions are appended as their own prompt section:

```text
USER INSTRUCTIONS:
The user provided these additional preferences for this generation. Follow them when they are compatible with the rules above, the source material, and the required JSON schema:
<instructions>
```

The wording keeps existing generation rules stronger than the user note. The note can focus topic selection, emphasis, difficulty nuance, or source area, but cannot change ownership, quota, JSON shape, or the requirement to stay grounded in supplied material.

## Client Design

Use the existing global dialog system as the base interaction pattern and extend it with textarea prompt support. The modal should include:

- Title: `Add generation instructions`.
- Short message explaining that the field is optional.
- Textarea with a concise placeholder, for example `Focus on the HTML part of this document`.
- `Cancel` and generation confirm buttons.
- Character limit feedback if a limit is visible; otherwise the server still enforces the cap.

The secondary button handler submits preflight with the current form data. On success, it opens the modal. On confirm, it fills a hidden `additionalInstructions` input and submits the original form through the same HTMX path used by the primary button.

The loading modal should only appear for the real generation request, not for preflight.

## Error Handling

Preflight validation errors show before the instructions modal. Examples:

- Duplicate flashcard deck name.
- No PDF selected.
- No quiz or exam sources selected.
- Selection too large.
- Unsupported or inaccessible file.

If preflight succeeds but the real generation request later fails validation, the existing error handling still applies. This can happen if another request changes data between preflight and submit, or if a user submits directly without preflight.

AI provider failures and invalid AI responses continue to use the existing AI generation failure behavior.

## Testing

Add focused coverage for:

- Flashcard preflight catches missing PDF, invalid destination, duplicate deck name, and does not record quota or call AI.
- Study preflight catches missing quiz/exam sources and oversized selections.
- Real generation still validates when called directly without preflight.
- Generation endpoints pass `additionalInstructions` to the relevant AI service.
- AI prompts include the `USER INSTRUCTIONS` section when instructions are present.
- AI prompts omit the section when instructions are blank.
- Existing generation tests continue to pass for calls without instructions.

## Open Decisions

No open decisions remain for the initial implementation.
