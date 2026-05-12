# AI PDF Flashcard Generation Design

## Goal

Allow users to generate flashcards from one already-uploaded PDF and save the generated cards directly into either an existing deck or a newly created deck.

## Scope

This feature adds a standalone flashcard generator flow. It does not change the existing study wizard, quiz generation, exam generation, or manual flashcard creation flows.

In scope:

- Select exactly one already-uploaded PDF owned by the current user.
- Choose whether the AI receives extracted text or the full PDF.
- Save generated flashcards into an existing deck.
- Create a new deck in a user-selected folder and save generated flashcards there.
- Let the AI decide the appropriate number of flashcards, bounded by a backend cap.
- Save generated cards immediately after generation.

Out of scope:

- Uploading a new PDF inside the generator flow.
- Reviewing or editing generated cards before saving.
- Generating from multiple PDFs in one request.
- Generating image-based flashcards.

## User Flow

1. User opens the standalone AI flashcard generator.
2. User selects one uploaded PDF from their existing files.
3. User chooses the PDF input mode using the same right-aligned segmented control style used in the study wizard: `Text` or `Full PDF`.
4. User chooses a destination:
   - Existing deck, selected from their decks.
   - New deck, with a deck name and destination folder selected by the user.
5. User submits generation.
6. The app shows the existing AI generation modal/progress treatment while the request runs.
7. Generated flashcards are saved immediately.
8. The user lands on the target deck with a success message and refreshed sidebar if needed.

## Architecture

Add a dedicated generator endpoint and backend path rather than extending the study session wizard.

New or changed units:

- `AiFlashcardService`: generates `frontText` / `backText` card pairs from selected PDF input.
- `FlashcardGenerationController`: renders the generator UI and handles generation submissions.
- `FlashcardService`: gains a bulk-create method for generated text-only cards.
- Existing `DocumentExtractionService`: reused for `Text` mode extraction and `Full PDF` resource loading.
- Existing `DocumentMode`: reused for `TEXT` and `FULL_PDF` mode selection.
- Existing `DeckService`: reused for target deck validation and new deck creation.
- Existing file, folder, and deck services: reused for ownership validation and UI data.

The generator will be transactional around destination creation and card persistence. The preferred sequence is to validate all inputs, prepare the AI document input, call AI generation, then create or resolve the deck and save cards in one persistence transaction. This avoids creating an empty new deck when generation fails.

## PDF Input Modes

The selected PDF carries a `DocumentMode` value:

- `TEXT`: extract text using `DocumentExtractionService.extractText(file)` and send only extracted text to AI.
- `FULL_PDF`: load the PDF resource using `DocumentExtractionService.loadResource(file)` and send it as `application/pdf` media to Gemini.

The UI should use the same visual language as the study wizard PDF toggle: a compact segmented `Text` / `Full PDF` control aligned on the right side of the selected PDF row.

## AI Generation Contract

`AiFlashcardService` should request JSON only, with a schema like:

```json
{
  "flashcards": [
    { "frontText": "...", "backText": "..." }
  ]
}
```

Prompt rules:

- Generate high-quality study flashcards from the dominant educational content of the PDF.
- Ignore metadata, headers, footers, page numbers, and incidental document details.
- Each card should be self-contained.
- Fronts should be clear questions, prompts, or terms.
- Backs should be concise answers or explanations.
- Avoid duplicate cards.
- Let the model choose the appropriate number of cards for the material, but never exceed the backend cap requested in the prompt.

Backend normalization:

- Discard cards with blank fronts or backs.
- Trim whitespace.
- Cap saved cards to the configured maximum, initially 50.
- Fail if no valid cards remain.

## Destination Rules

Existing deck destination:

- The submitted deck ID must belong to the current user.
- Generated cards are appended to that deck.

New deck destination:

- The submitted folder ID must belong to the current user.
- The submitted deck name must be non-blank after trimming.
- The new deck is created in the selected folder only after successful AI generation.
- Generated cards are added to the new deck.

## Error Handling

Validation errors are shown on the generator page without creating cards:

- Missing PDF selection.
- Selected file does not belong to the user.
- Selected file is not a supported PDF.
- Missing destination type.
- Missing or invalid existing deck.
- Missing or invalid new-deck folder.
- Blank new-deck name.
- Empty extracted text in `TEXT` mode, with guidance to try `Full PDF` mode.

AI/runtime failures:

- AI request failure shows a retryable error consistent with quiz/exam generation.
- Invalid JSON or too few valid cards shows a retryable parse/quality error.
- No deck is created if AI generation fails.
- No generated cards are saved if persistence fails.

## UI Notes

The standalone page should follow the existing design system:

- A page header for AI flashcard generation.
- A PDF picker limited to supported uploaded PDFs.
- Search/filter affordance if the existing file lists are large enough to warrant it.
- A selected PDF row that exposes the right-aligned `Text` / `Full PDF` segmented toggle.
- A destination section with two mutually exclusive choices: existing deck or new deck.
- Existing deck selector.
- New deck name input and folder selector.
- Existing AI generation modal/progress treatment while generating.
- Success banner on the target deck after redirect/swap.

## Testing

Add tests in the existing project style:

- `AiFlashcardServiceTests` for text-mode prompt generation, full-PDF media input, JSON parsing, blank-card filtering, output capping, and AI failure handling.
- Controller tests for generating into an existing deck.
- Controller tests for generating into a new deck in a selected folder.
- Validation tests for missing PDF, non-PDF or unsupported file, invalid destination, invalid deck/folder, blank new-deck name, and empty text-mode extraction.
- Failure tests showing no new deck or cards are persisted when AI generation fails.

## Acceptance Criteria

- A user can open a standalone generator and select exactly one uploaded PDF.
- The selected PDF exposes a right-aligned `Text` / `Full PDF` toggle matching the study wizard style.
- A user can append generated cards to an existing deck.
- A user can create a new deck in a selected folder and save generated cards there.
- The AI chooses the number of generated flashcards up to the backend cap.
- Generated cards are saved immediately and the user is shown the target deck.
- Invalid inputs and AI failures show clear errors and do not create partial data.
- Tests cover generation, validation, and failure behavior.
