# Multi-File Upload and Multi-PDF Flashcard Generation Design

## Goal

Users can upload multiple files in one folder upload action. Users can also select multiple uploaded PDFs in the AI Flashcards page, generate from those PDFs in one AI request, and save all generated cards into one combined deck.

## Existing Context

- Folder uploads currently submit one multipart field named `file` to `FileController.upload`, which delegates to `FileEntryService.upload`.
- The upload modal uses a single native file input and shows one selected filename.
- AI flashcard generation currently accepts one `fileId`, validates one PDF, builds one `DocumentInput`, and calls `AiFlashcardService.generate(DocumentInput, ...)`.
- `AiGenerationSupport` already supports lists of `DocumentInput` for text document prompt sections, attached PDF listings, and PDF media arrays.
- The app already uses `.sh-checkbox` in the study source picker. That class currently relies on native checkbox rendering plus `accent-color`; this feature replaces that native rendering with custom checkbox styling that is consistent across browsers.

## Upload Design

The upload form keeps the existing `file` field name, but the input gains the `multiple` attribute. The selected-file summary shows a single filename for one selection and a count plus compact filename list for multiple selections.

`FileController.upload` accepts `List<MultipartFile>` or `MultipartFile[]` for `file`. It delegates to a new `FileEntryService.uploadAll(files, folderId, user)` method. The existing single-file `upload` method remains for other callers and delegates to shared per-file logic.

`uploadAll` validates the target folder once, rejects empty selections, validates each file through `UploadValidator`, checks quota against the combined added bytes, then stores and saves each file as a separate `FileEntry`.

The initial implementation uses all-or-error request semantics. If validation, quota, or storage fails, the UI shows the existing upload error path and does not report partial success. If a later file fails after earlier files were stored, the service cleans up files it stored during that request before surfacing the error.

## Flashcard Generation Design

The AI Flashcards page changes from "Choose one PDF" to "Choose PDFs". Each PDF row uses a checkbox input named `fileId`, so normal form submission sends repeated `fileId` values.

The document mode is one global choice for the request, not one mode per PDF. This keeps the UI and request contract simple: Text mode extracts text from every selected PDF; Full PDF mode attaches every selected PDF. The existing mode toggle can move above or near the PDF list and submit one `documentMode` field.

`FlashcardGenerationController.generate` and `preflightGenerate` accept `List<Long> fileId`. Validation requires at least one selected PDF and checks every file:

- The file belongs to the current user.
- The original filename ends in `.pdf`.
- `DocumentExtractionService.isSupported(file)` returns true.

The controller builds `List<DocumentInput>` in selection order and calls a new `AiFlashcardService.generate(List<DocumentInput>, cardCount, instructions)` overload. Existing single-document overloads remain and delegate to the list overload.

The AI request is one combined request. Generated cards are persisted exactly as today, either to one new deck or one existing deck. No separate deck is created per source PDF.

The success message changes to distinguish one and many sources:

- `Generated 1 flashcard from lecture.pdf.`
- `Generated 20 flashcards from 3 PDFs.`

When one PDF is selected, new-deck name prefill still uses that PDF filename. When multiple PDFs are selected, prefill does not overwrite a typed deck name; for a blank deck name it uses `Generated Flashcards`.

## Checkbox UI Design

Use the app's existing `.sh-checkbox` class for the flashcard PDF checkboxes so the generator matches the study source picker. Update the shared checkbox CSS to avoid browser-native differences:

- Set `appearance: none` and draw the square, border, checkmark, focus ring, disabled state, and indeterminate state in CSS.
- Preserve the 16px checkbox footprint used by the source picker.
- Use `var(--accent)`, `var(--border)`, `var(--surface)`, and existing focus glow tokens.
- Keep labels clickable by nesting the checkbox inside the PDF row label.
- Ensure selected PDF rows still receive `.is-selected` when their checkbox is checked.

This shared styling intentionally improves all `.sh-checkbox` usages, including the study wizard source picker, instead of creating a one-off browser-specific checkbox in the flashcard generator.

## Client Behavior

Update the flashcard generator JavaScript:

- Search continues filtering `.sh-ai-pdf-row`.
- PDF row selection logic handles checkboxes instead of radios.
- Multiple rows can carry `.is-selected`.
- Size warning considers all selected PDFs and appears if any selected PDF is above the slow-warning threshold.
- New-deck folder preselection only auto-runs for a single selected PDF.
- New-deck name prefill only auto-runs for a single selected PDF, or uses a neutral default when multiple are selected and the field is blank.
- Preflight and generation submission continue using normal `FormData`, so repeated `fileId` fields are included automatically.

## Error Handling

Validation messages:

- No selected PDFs: `Please select at least one PDF.`
- Unsupported selected source: `Please select only supported PDFs under 10 MB.`
- Text mode with no extractable text in one or more PDFs: identify the failing filename where practical, e.g. `lecture.pdf has no extractable text. Try Full PDF mode.`

Provider and quota errors continue through the existing AI generation error display.

## Tests

Add or update tests for:

- `FileEntryService.uploadAll` validates the folder once, checks quota against combined bytes, stores each file, and saves separate entries.
- `FileEntryService.uploadAll` rejects empty selections.
- `FileController.upload` accepts multiple files and delegates to `uploadAll`.
- `FlashcardGenerationController.generate` accepts multiple `fileId` values in Text mode and passes a list of `TextDocument` inputs to the AI service.
- `FlashcardGenerationController.generate` accepts multiple `fileId` values in Full PDF mode and passes a list of `PdfDocument` inputs to the AI service.
- `FlashcardGenerationController.generate` rejects missing selection and unsupported PDFs before quota or AI calls.
- `AiFlashcardService.generate(List<DocumentInput>, ...)` lists multiple PDFs and attaches multiple PDF media items in one request.
- Existing single-PDF generation tests continue passing through compatibility overloads.

## Out of Scope

- One deck per selected PDF.
- Per-PDF Text vs Full PDF mode in the flashcard generator.
- Partial-success upload reporting.
- Multi-source flashcard generation from non-PDF files.
