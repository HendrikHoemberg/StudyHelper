# PDF Viewer & PDF Splitter — Design

**Date:** 2026-05-16
**Status:** Approved (pending spec review)

## Overview

Two PDF features for StudyHelper:

1. **In-app PDF viewer** — clicking a PDF thumbnail opens the document inside the
   app in an overlay, instead of navigating to a new browser tab. This matches
   the existing in-app image experience (`#sh-lightbox`).
2. **PDF splitter** — an edit mode that lets the user place breakpoints between
   pages of a PDF and export the resulting consecutive segments as new files
   with custom names. The original file is kept untouched.

Both features are limited to `application/pdf` files.

## Shared infrastructure

### PDF.js (v5.7.284)

PDF.js is vendored as a local static asset alongside the existing
`src/main/resources/static/js/lib/fabric.min.js`:

- `js/lib/pdfjs/pdf.min.mjs` — the PDF.js library
- `js/lib/pdfjs/pdf.worker.min.mjs` — the rendering worker

PDF.js v5 is **ESM-only** — there is no UMD/global build. Therefore:

- `pdf-viewer.js` and `pdf-splitter.js` are loaded as **ES modules**
  (`<script type="module">`), and `import` PDF.js directly.
- Each module sets `pdfjsLib.GlobalWorkerOptions.workerSrc` to the vendored
  worker path.
- Each module assigns its public entry point to `window` (`window.PdfViewer`,
  `window.PdfSplitter`) so the existing classic scripts (`app.js`) and inline
  handlers can trigger them.
- The existing classic scripts (`app.js`, `image-editor.js`, etc.) are
  unchanged.

### Reading PDF bytes

Both features fetch the raw PDF from the **existing** `GET /files/{id}/view`
endpoint, which already serves the file inline with the correct content type.
No new read endpoint is required. The browser caches this fetch, so when the
user moves from the viewer to the splitter the bytes are not re-downloaded.

## Feature 1 — In-app PDF viewer

### Components

| File | Purpose |
|------|---------|
| `templates/fragments/pdf-viewer.html` | Overlay markup, included once in `layout.html` |
| `static/js/pdf-viewer.js` | ES module — renders pages with PDF.js, exposes `window.PdfViewer` |
| `static/css/pdf-viewer.css` | Styles, prefix `sh-pv-` |

`pdf-viewer.html` is added to the `layout :: scripts` fragment next to
`fragments/image-editor :: modal` and `fragments/lightbox :: lightbox`.
`pdf-viewer.css` is linked from `layout :: head` next to `image-editor.css`.

### Trigger wiring

In `tab-files.html` and `explorer.html`, the PDF tile anchor currently is:

```html
<a th:href="@{/files/{id}/view(id=${file.id})}" target="_blank" class="sh-deck-cover">
```

This changes — for PDF files — to a trigger element carrying data attributes,
mirroring the `sh-lightbox-trigger` pattern used for images:

```html
<a th:href="@{/files/{id}/view(id=${file.id})}"
   class="sh-deck-cover sh-pdf-viewer-trigger"
   th:data-file-id="${file.id}"
   th:data-file-url="@{/files/{id}/view(id=${file.id})}"
   th:data-file-name="${file.originalFilename}">
```

A delegated `click` handler (registered in `pdf-viewer.js`) intercepts
`.sh-pdf-viewer-trigger`, calls `preventDefault()`, and invokes
`PdfViewer.open({ fileId, url, name })`. The `href` is kept as a no-JS
fallback. Images keep their existing `sh-lightbox-trigger` behavior.

### Viewer UI

- **Header:** document filename + close button (`✕`).
- **Body:** a vertically scrollable area; PDF.js renders each page to a
  `<canvas>` stacked top-to-bottom. Pages render lazily as they scroll into
  view (IntersectionObserver) so large PDFs stay responsive.
- **Footer toolbar:** zoom out / zoom in, a live page indicator (`3 / 12`,
  driven by scroll position), a **Download** button (links to
  `/files/{id}/download`), and a **Split this PDF** button.
- **Split handoff:** the Split button closes the viewer and calls
  `PdfSplitter.open(...)` with the same `{ fileId, url, name }`.
- **Dismissal:** clicking the backdrop or pressing `Escape` closes the viewer.
- **States:** a loading spinner while the document loads; an error panel with
  a message if the PDF fails to load (corrupt / password-protected).

## Feature 2 — PDF splitter edit mode

### Components

| File | Purpose |
|------|---------|
| `templates/fragments/pdf-splitter.html` | Full-screen modal markup, included once in `layout.html` |
| `static/js/pdf-splitter.js` | ES module — renders page thumbnails, manages breakpoints, exposes `window.PdfSplitter` |
| `static/css/pdf-splitter.css` | Styles, prefix `sh-ps-` |

Structure mirrors the existing image editor modal (`sh-ie-modal`):
header / body / footer, included via `layout :: scripts`.

### Entry points

1. **Edit-options modal** (`file-edit-modal.html`): for `application/pdf`
   files, an "Open PDF Splitter" button is shown in the same place images get
   the "Open Image Editor" button. It carries `data-*` attributes
   (`data-split-file-id`, `data-split-url`, `data-split-filename`), closes the
   modal on click, and calls `PdfSplitter.open(...)`. For single-page PDFs the
   button is disabled with a hint ("Only one page — nothing to split").
2. **Viewer toolbar:** the "Split this PDF" button described above.

### Splitter UI

- **Body:** PDF.js renders every page as a thumbnail laid out in a grid, each
  labeled with its page number.
- **Breakpoints:** between every pair of adjacent pages sits a clickable
  scissors divider. Clicking it toggles a breakpoint on/off. With `B`
  breakpoints the document is divided into `B + 1` **parts**.
- **Parts:** each part group is visually tinted (a distinct accent per part)
  and shows a **name input**. Default names are `{baseName}-1.pdf`,
  `{baseName}-2.pdf`, … where `baseName` is the original filename without its
  extension. Editing a name does not change other parts.
- **Footer:** **Cancel** and **Save N parts** (the count `N` updates live as
  breakpoints change). Save is disabled until there is at least one breakpoint
  (`N >= 2`).
- **States:** loading spinner while thumbnails render; an inline error alert
  in the footer for save failures (e.g. quota exceeded); spinner on the Save
  button while the request is in flight.

### Save flow

On **Save N parts**, the module collects the part list and `POST`s it to
`/files/{id}/split` (see API contract). On success the splitter closes and the
file grid is refreshed so the new parts appear immediately. On failure the
error is shown in the footer alert and the splitter stays open.

## Backend

### API contract — `POST /files/{id}/split`

Added to `FileController`. Request body (JSON):

```json
{
  "parts": [
    { "name": "intro.pdf",   "startPage": 1, "endPage": 3 },
    { "name": "chapter1.pdf", "startPage": 4, "endPage": 10 }
  ]
}
```

- `startPage` / `endPage` are 1-based and inclusive.
- The ranges must be contiguous and cover every page exactly once
  (`parts[0].startPage == 1`, each `startPage == previous.endPage + 1`, last
  `endPage == pageCount`).
- At least 2 parts are required.
- Each `name` must be non-blank; a `.pdf` extension is appended if missing.

**Response:** the refreshed file grid — `folder-detail :: tabsSection` with the
folder view model — so the new parts render via the normal HTMX swap into
`#explorer-detail`. The splitter modal closes client-side on a successful
response.

**Validation errors** (bad ranges, blank names, fewer than 2 parts, file not
PDF) return HTTP 400 with a message rendered into the splitter footer alert.

### `PdfSplitService` (new)

- `splitPdf(Long fileId, List<SplitPart> parts, User user)`:
  1. Loads the `FileEntry` via `fileEntryService.getFile(id, user)` (enforces
     ownership).
  2. Validates the part ranges against the document's page count.
  3. Loads the source PDF with PDFBox (`Loader.loadPDF`).
  4. For each part: builds a new `PDDocument`, copies the page range with
     `importPage`, and serializes it to a `byte[]`.
  5. Computes the total size of all parts and calls
     `storageQuotaService.assertWithinQuota(user, 0L, totalBytes)` **once**
     before writing anything, so a quota failure leaves no partial result.
  6. Stores each part via a new `FileStorageService.storeBytes(byte[], ".pdf")`
     method and creates a `FileEntry` (mime `application/pdf`, same folder as
     the original, same user).
  7. Returns the folder id for the view refresh.
- The original `FileEntry` and its stored file are never modified or deleted.

### `FileStorageService.storeBytes` (new)

`FileStorageService.store` currently only accepts a `MultipartFile`. A new
`String storeBytes(byte[] content, String extension)` method writes the bytes
under a fresh UUID filename and returns the stored filename. The existing
`store` method can be left as-is or refactored to delegate — implementation
detail for the plan.

## Edge cases & error handling

- **Single-page PDF:** the splitter entry button is disabled; nothing to split.
- **Corrupt / password-protected PDF:** the viewer shows its error panel; the
  splitter shows an error and cannot open. `PdfSplitService` surfaces a 400.
- **Empty part (two adjacent breakpoints with no page between):** not possible
  — breakpoints sit *between* pages, so every part has ≥1 page.
- **Blank or duplicate part names:** blank names are rejected (400); duplicate
  names are allowed (stored filenames are UUIDs; display names may repeat,
  consistent with current upload behavior).
- **Storage quota exceeded:** checked before any file is written;
  `StorageQuotaExceededException` → 400 with the quota message in the footer
  alert. No partial parts are created.
- **Cross-user access:** `getFile(id, user)` already throws
  `ResourceNotFoundException` for files the user does not own.
- **CSRF:** the POST includes the CSRF token the same way the image editor's
  save request does (`initCsrf` in `app.js`).

## Testing

- **`PdfSplitServiceTests`** — split a known multi-page PDF fixture; assert
  each part's page count and that part page content matches the source range;
  assert the original file is untouched; assert quota failure produces no
  parts.
- **`FileControllerTests`** — `POST /files/{id}/split`: happy path, invalid
  ranges (gap / overlap / not covering all pages), blank name, fewer than 2
  parts, quota exceeded, non-PDF file, cross-user access denied.
- **`UiResourceRegressionTests`** — the vendored PDF.js assets are served; the
  `pdf-viewer` and `pdf-splitter` fragments render; PDF tiles use
  `sh-pdf-viewer-trigger` and no longer carry `target="_blank"`.

## Out of scope

- Page deletion and page reordering (only consecutive-range splitting).
- Merging PDFs.
- Editing PDF content (annotations, text).
- Deleting or replacing the original PDF as part of a split.
