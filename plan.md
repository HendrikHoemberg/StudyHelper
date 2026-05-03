# Plan — In-App Image Editor + Custom Color Picker

## 0. Scope summary

Two cooperating features, planned together because they share design language and one consumes the other:

1. **Image Editor** — Fabric.js-based vector/layered canvas in a full-screen modal. Brush, eraser, line/rect/circle shapes, text, undo/redo, clear, zoom/pan, color via the new picker. Reachable from flashcard form (front/back image preview) and folder-detail files table (image MIME types only).
2. **Custom Color Picker** — Reusable popover component with HEX / RGB / HSB tabs, SV plane, hue slider, app palette, recent colors. Replaces the native `<input type="color">` in folder forms and is used internally by the image editor toolbar.

No crop tool in v1 (per decision). Touch input deferred but the editor's input layer is abstracted so it can be added without restructuring.

---

## 1. Architectural overview

### 1.1 Modules

```
static/
├── js/
│   ├── lib/
│   │   └── fabric.min.js              # vendored, fabric@6.x (~300KB gzipped)
│   ├── color-picker.js                # ColorPicker — standalone popover component
│   ├── image-editor.js                # ImageEditor — modal + Fabric canvas controller
│   └── app.js                         # existing
├── css/
│   ├── styles.css                     # existing (3436 lines — leave alone)
│   ├── color-picker.css               # new, ~250 lines
│   └── image-editor.css               # new, ~400 lines
templates/fragments/
├── image-editor.html                  # modal shell + toolbar markup
├── color-picker.html                  # popover shell (rendered server-side once, reused)
├── flashcard-form.html                # +Edit button on preview wraps
├── folder-detail.html                 # +Edit button in actions column for image rows
└── folder-form.html                   # color input → ColorPicker trigger
```

`color-picker.css` and `image-editor.css` are linked from `layout.html` after `styles.css`. Splitting avoids bloating the existing 3.4k-line stylesheet and keeps these features deployable as a unit.

### 1.2 Public JS APIs

```js
// color-picker.js
ColorPicker.attach(triggerEl, {
  initialColor: '#6366f1',     // hex string
  onChange: (hex) => {...},    // fires while dragging, throttled to rAF
  onCommit: (hex) => {...},    // fires on close / Enter / blur
  alpha: false,                // hide alpha slider unless asked
  paletteKey: 'folder',        // namespace for "recent colors" in localStorage
});
ColorPicker.open(triggerEl, opts); // imperative open, used by image editor toolbar
```

```js
// image-editor.js
ImageEditor.open({
  source: File | Blob | string,        // File for unsaved upload, URL for existing
  filename: 'card-front.png',          // used when posting back
  mode: 'flashcard-new'                // returns edited Blob via onSave; never POSTs
        | 'flashcard-existing'         // POSTs to onSave callback's URL
        | 'file-existing',             // shows Save / Save-as-new buttons
  onSave: async (blob, choice) => {},  // choice: 'overwrite' | 'new' (only file-existing)
  onCancel: () => {},
});
```

The editor module has zero coupling to HTMX, controllers, or DOM ids outside its own modal — every integration point goes through `onSave`. This keeps it reusable and testable in isolation.

### 1.3 Why Fabric.js (per decision: layered editor)

A layered editor means a user who drew a rectangle 4 strokes ago can still select, recolor, and move it. Building this from scratch on raw `<canvas>` would require reimplementing object hit-testing, transform handles, multi-select, serialization for undo, and text editing — easily 2k+ lines of subtle code. Fabric provides this as its core competency.

Fabric is vendored (matching the lucide pattern) so there's no CDN dependency. We use the `fabric.min.js` browser build, not the Node build.

### 1.4 Undo/redo strategy

Snapshot-based using Fabric's `canvas.toJSON()` / `loadFromJSON()`. Stack capped at 20 (10 each direction is the floor in the brief; 20 gives headroom). Snapshots are pushed on `path:created`, `object:added`, `object:modified`, `object:removed`. ~50–200KB per snapshot in JSON form, well under typical browser memory limits.

### 1.5 Input abstraction (touch-ready)

All canvas input flows through a thin wrapper:

```js
canvas.on('mouse:down', ...)   // Fabric already normalizes pointer events
```

For pan/zoom we add a `gestureLayer` object with `onPanStart`, `onPanMove`, `onPanEnd`, `onPinch` hooks. v1 wires only mouse + wheel. v2 wires touch — no other code changes needed because the gesture layer is the only thing that speaks input events.

---

## 2. UI / UX description

### 2.1 Image Editor modal

Full-screen modal (`position: fixed; inset: 0`), reusing the existing glassmorphism aesthetic (`--sh-bg-elevated`, backdrop blur, soft shadows). Layout:

```
┌─────────────────────────────────────────────────────────────────┐
│  [Editor: card-front.png]                          [×] Close    │  ← header bar (48px)
├──────┬──────────────────────────────────────────────────────────┤
│      │                                                          │
│ 🖌   │                                                          │
│ 🧹   │                                                          │
│ ▭    │              [    canvas area     ]                      │
│ ◯    │           (checkerboard background                       │
│ ╱    │            for transparency, image                       │
│ T    │            centered, scaled to fit)                      │
│ ─    │                                                          │
│      │                                                          │
│ ●●●  │                                                          │
│ ↶ ↷  │                                                          │
│ ⊘    │                                                          │
├──────┴──────────────────────────────────────────────────────────┤
│ [size: ●─────] [opacity: ●────]  [zoom: 100%] [reset]  [Save] [Save as new] │
└─────────────────────────────────────────────────────────────────┘
```

- **Left toolbar** (`64px` wide on desktop, collapsible bottom-bar on `<768px`): Brush, Eraser, Rectangle, Ellipse, Line, Text, plus a **color swatch button** (opens ColorPicker), **undo/redo**, **clear**.
- **Bottom bar**: contextual tool options (brush size 1–60px, opacity 0–100%, font size for text), zoom indicator, action buttons.
- **Action buttons** vary by mode:
  - `flashcard-new`, `flashcard-existing`: **Cancel** + **Save**.
  - `file-existing`: **Cancel** + **Save** (overwrite) + **Save as new** (creates new FileEntry).
- Each tool button is a Lucide icon (`brush`, `eraser`, `square`, `circle`, `minus`, `type`, `palette`, `undo-2`, `redo-2`, `trash-2`).
- Active tool gets `--sh-primary` accent border.
- Save button shows a spinner (Lucide `loader-2` with CSS spin) and disables during the POST.

### 2.2 Color Picker popover

Anchored popover (~280px wide), opens below or above the trigger depending on viewport. Reference: VSCode color picker.

```
┌──────────────────────────────────┐
│ ┌────────────────────────┐ ┌─┐  │
│ │                        │ │ │  │  ← SV plane (saturation × value)
│ │           ●            │ │ │  │     + vertical hue slider
│ │                        │ │ │  │
│ └────────────────────────┘ └─┘  │
│                                  │
│ [HEX] [RGB] [HSB]                │  ← mode tabs
│ ┌──────────────┐                 │
│ │ #6366F1      │  [eyedropper]   │  ← input row varies by mode
│ └──────────────┘                 │
│                                  │
│ Recent: ■ ■ ■ ■ ■ ■              │  ← last 6 from localStorage
│ Palette: ■ ■ ■ ■ ■ ■ ■ ■         │  ← curated app palette
└──────────────────────────────────┘
```

- **SV plane**: 240×160 canvas, draws saturation gradient ×  value gradient masked by current hue. A circular cursor (white ring + black inner stroke) marks the current point. Pointer drag updates S+V; the picker emits `onChange` per `requestAnimationFrame`.
- **Hue slider**: 12×160 vertical strip, full-spectrum gradient. Drag updates hue.
- **Mode tabs**: HEX / RGB / HSB. Switching swaps the inputs but not the value.
  - HEX: single text input with validation regex (`#?[0-9a-f]{3,8}`).
  - RGB: three numeric inputs (0–255) plus a small "alpha" input only if `alpha: true`.
  - HSB: three numeric inputs (H 0–360, S/B 0–100).
- **Eyedropper**: button calls `new EyeDropper().open()` if available; gracefully hidden if not (Firefox). No fallback shim — the SV/inputs cover the use case.
- **Curated palette**: 12 colors aligned with `--sh-primary` and friends — defined in `color-picker.js` as a constant.
- **Recent colors**: 6 most-recently-committed colors per `paletteKey`, persisted in `localStorage` as `sh.colorpicker.recent.{key}`.
- Closes on outside click, `Escape`, or trigger re-click. Pressing `Enter` in a numeric input commits.

### 2.3 Folder color picker integration

The native `<input type="color" class="sh-color-input">` in `folder-form.html` is replaced with:

```html
<button type="button" class="sh-color-trigger" data-color-picker
        data-target="modalFolderColor" data-palette-key="folder">
  <span class="sh-color-swatch" style="background:#6366f1"></span>
  <span class="sh-color-hex">#6366F1</span>
</button>
<input type="hidden" name="colorHex" id="modalFolderColor" value="#6366f1">
```

JS auto-attaches `ColorPicker` to anything with `[data-color-picker]` after each HTMX swap. `onChange` updates the swatch + hex label + the hidden input + calls `updateFolderPreview()` to keep the existing live preview working. No controller or service changes needed — the form still posts `colorHex` exactly like today.

---

## 3. File-by-file changes

### 3.1 New files

#### `static/js/lib/fabric.min.js`
Vendored Fabric.js v6.x browser build. Source: `https://cdnjs.cloudflare.com/ajax/libs/fabric.js/6.4.3/fabric.min.js` (or the version current at implementation). Single `<script>` tag in `layout.html` after lucide.

#### `static/js/color-picker.js` (~450 lines)
Self-contained module exposing `window.ColorPicker = { attach, open, close, parse, format }`.

Key internals:
- `parseColor(input)` — accepts hex/rgb/hsb strings, returns `{r,g,b,h,s,v,hex}`.
- `renderSV(canvas, hue)` — paints SV plane for a given hue (cached per hue).
- `bindPointer(el, onDrag)` — pointer-events handler used by SV plane and hue slider.
- `attach(trigger, opts)` — listens for click on trigger, lazy-creates one shared popover root in `<body>` (singleton — only one picker open at a time), positions it.
- Auto-init: on `DOMContentLoaded` and `htmx:afterSwap`, scan for `[data-color-picker]` and attach. Idempotent (skips already-attached triggers).

#### `static/js/image-editor.js` (~700 lines)
Module exposing `window.ImageEditor = { open, close }`.

Internals:
- `createCanvas(container, source)` — loads source into a Fabric `Canvas`, applies max-dim downscale (2048px on long edge), draws checkerboard background pattern.
- `Tools` — registry with `{ id, icon, cursor, onActivate, onDeactivate, onPointerDown, onPointerMove, onPointerUp }`.
  - Brush/Eraser use Fabric's `PencilBrush` / `EraserBrush`-style path via `freeDrawingMode`.
  - Shapes use temporary preview object created on pointer-down, finalized on pointer-up.
  - Text uses `IText` so users can dbl-click to re-edit.
- `History` — push/pop wrapping `canvas.toJSON()`.
- `exportPNG()` — `canvas.toDataURL({ format: 'png', multiplier: 1 })` → fetch → `Blob`.
  - **Output format decision**: always PNG. Predictable, lossless, supports transparency for annotations on transparent backgrounds. ~3× JPEG size is acceptable given downscale to 2048px (typical output 0.5–2MB).
- `Save` flow per mode (see §4 Data flow).

#### `static/css/color-picker.css` (~250 lines)
Scoped to `.sh-cp-*` classes. Uses existing tokens (`--sh-bg-elevated`, `--sh-border`, `--sh-primary`, `--sh-text`, `--sh-text-muted`). Glassmorphism via `backdrop-filter: blur(12px)` matching modal styles. Tab-active state mirrors the existing `.sh-icon-btn.is-selected` accent.

#### `static/css/image-editor.css` (~400 lines)
Scoped to `.sh-ie-*`. Modal shell, toolbar, canvas wrapper with checkerboard `background-image`, tool button states, bottom bar with sliders styled to match `.sh-input` aesthetic.

#### `templates/fragments/image-editor.html`
Modal shell — toolbar buttons, canvas container `<div id="sh-ie-canvas-wrap">`, bottom action bar. Rendered server-side only as a fragment when needed (see §4); easier to localize and CSP-friendly than building all DOM in JS.

#### `templates/fragments/color-picker.html`
Popover shell. Rendered once on app load and kept hidden in `<body>` for reuse (singleton pattern). Could also be built in JS — server-side keeps it consistent with the rest of the codebase's Thymeleaf-first style.

### 3.2 Modified files

#### `templates/fragments/layout.html`
Add after existing CSS link:
```html
<link href="/css/color-picker.css" rel="stylesheet">
<link href="/css/image-editor.css" rel="stylesheet">
```
Add before `app.js`:
```html
<script src="/js/lib/fabric.min.js"></script>
<script src="/js/color-picker.js"></script>
<script src="/js/image-editor.js"></script>
```
Include the color-picker singleton fragment once:
```html
<div th:replace="~{fragments/color-picker :: popover}"></div>
```

#### `templates/fragments/flashcard-form.html`
Add an Edit button inside each `.sh-img-preview-wrap` next to the existing remove button:

```html
<button type="button" class="sh-img-edit-btn" data-side="front">
  <i data-lucide="pencil"></i>
</button>
```

The same button is added in JS's `showPreview()` (the dynamic preview path) so newly-selected images also get it.

In the inline `<script>`, wire the button:

```js
function openEditor(side) {
  var input = document.getElementById(side + 'ImageInput');
  var existing = side === 'front' ? frontExistingUrl : backExistingUrl; // from data attrs
  if (input.files && input.files[0]) {
    // Mode A: unsaved local file
    ImageEditor.open({
      source: input.files[0],
      filename: input.files[0].name,
      mode: 'flashcard-new',
      onSave: (blob) => {
        var file = new File([blob], input.files[0].name.replace(/\.[^.]+$/, '') + '.png',
                            { type: 'image/png' });
        var dt = new DataTransfer();
        dt.items.add(file);
        input.files = dt.files;
        showPreview(file);  // existing function — refreshes preview
      },
    });
  } else if (existing) {
    // Mode B: existing server image
    ImageEditor.open({
      source: existing,
      filename: side + '.png',
      mode: 'flashcard-existing',
      onSave: async (blob) => {
        var fd = new FormData();
        fd.append('image', blob, side + '.png');
        var resp = await fetch('/flashcards/' + flashcardId + '/images/' + side,
                               { method: 'POST', body: fd, headers: csrfHeaders() });
        if (!resp.ok) throw new Error('Save failed');
        // bust cache
        var img = document.querySelector('#' + side + '-preview-wrap img');
        img.src = existing + '?v=' + Date.now();
      },
    });
  }
}
```

The existing-image URL is stamped onto the preview wrap as `data-existing-url` from the Thymeleaf template so the JS doesn't have to know URL conventions.

#### `templates/fragments/folder-detail.html`
In the files table actions cell, before the View button, conditionally add:

```html
<button th:if="${#strings.startsWith(file.mimeType, 'image/')}"
        class="sh-btn sh-btn-ghost sh-btn-sm"
        th:attr="data-edit-image=${file.id},
                 data-edit-url=@{/files/{id}/view(id=${file.id})},
                 data-edit-filename=${file.originalFilename}">
  Edit
</button>
```

A delegated click handler in `image-editor.js` listens on `body` for `[data-edit-image]` clicks and opens with `mode: 'file-existing'`. On save, it POSTs to either the overwrite endpoint (`/files/{id}/edit`) or the create endpoint (existing `/folders/{folderId}/files`) depending on the user's choice, then triggers an HTMX swap of `#files-table-container` to refresh the row.

#### `templates/fragments/folder-form.html`
Replace lines 19–24 (the color input block) with the trigger button described in §2.3. No other changes — the form still submits `colorHex`.

#### `static/js/app.js`
No structural changes. The existing `htmx:afterSwap` hook already re-runs `initLucide()`; `ColorPicker` and `ImageEditor` register their own delegated listeners on `body`, so they don't need re-init per swap.

Remove the now-dead CSS class `.sh-color-input` (lines 893–910 of `styles.css`) once the native picker is replaced everywhere — verified by grep first.

### 3.3 Backend changes

#### `controller/FlashcardController.java`
Add one endpoint:

```java
@PostMapping(value = "/flashcards/{id}/images/{side}",
             consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<Void> replaceImage(@PathVariable Long id,
                                         @PathVariable String side,
                                         @RequestParam("image") MultipartFile image,
                                         Principal principal) throws IOException {
    if (!"front".equals(side) && !"back".equals(side)) {
        return ResponseEntity.badRequest().build();
    }
    User user = userService.getByUsername(principal.getName());
    flashcardService.replaceImage(id, side, image, user);
    return ResponseEntity.noContent().build();
}
```

`FlashcardService.replaceImage(id, side, file, user)` — finds the existing card, checks ownership, if it has a stored filename calls `fileStorageService.delete(old)` then `store(new)`, otherwise just stores. Updates the entity field and saves.

(Note: existing `updateFlashcard()` already accepts new images. We add a dedicated endpoint rather than reusing `/flashcards/{id}/edit` because the latter expects the full form including text fields and would clobber unsaved text edits. Single-purpose endpoint is cleaner.)

#### `controller/FileController.java`
Add two endpoints:

```java
// Overwrite in place — keeps the same FileEntry id and storedFilename.
@PostMapping(value = "/files/{id}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public String editOverwrite(@PathVariable Long id,
                            @RequestParam("image") MultipartFile image,
                            Principal principal,
                            Model model,
                            @RequestHeader(value = "HX-Request", required = false) String hx)
                            throws IOException {
    User user = userService.getByUsername(principal.getName());
    Long folderId = fileEntryService.replaceContents(id, image, user);
    // Return the files table fragment for HTMX swap.
    return renderFilesTable(folderId, user, model);
}
```

Save-as-new is *not* a new endpoint — it reuses the existing `POST /folders/{folderId}/files` with the edited Blob. The JS just constructs FormData with the right multipart field name. This keeps the surface small.

`FileEntryService.replaceContents(id, image, user)` — checks ownership, calls `FileStorageService.replaceContents(storedFilename, image)`, updates `mimeType`, `fileSizeBytes`, possibly `originalFilename`, returns the parent `folderId`.

`FileStorageService.replaceContents(String filename, MultipartFile file)` — `Files.copy(file.getInputStream(), uploadDir.resolve(filename), REPLACE_EXISTING)`. The existing `store()` method uses the same `REPLACE_EXISTING` flag, so behavior is consistent.

A small helper `renderFilesTable(folderId, user, model)` in `FileController` (or duplicating the existing folder-detail logic) returns `fragments/folder-detail :: filesTable`. Verify the existing `FolderController` flow to see whether such a helper already exists; if it does, call it instead.

#### `service/FileStorageService.java`
Add `replaceContents(filename, file)` (~5 lines). Existing `store()` uses `REPLACE_EXISTING` for new UUIDs, so the file system already supports overwrite semantics.

---

## 4. Data flow

### 4.1 New flashcard image (unsaved)
```
[user picks file] → existing setupZone → input.files set → preview rendered
       │
       ▼
[user clicks Edit] → ImageEditor.open({ source: file, mode: 'flashcard-new' })
       │
       ▼
[user edits] → Fabric canvas mutations → undo stack
       │
       ▼
[user clicks Save] → exportPNG() → Blob
       │
       ▼
[onSave callback] → new File from Blob → DataTransfer → input.files replaced
                  → showPreview() refreshes UI
       │
       ▼
[user submits form] → existing /decks/{id}/flashcards POST → backend stores via FileStorageService
```

### 4.2 Existing flashcard image
```
[modal opens] → preview <img src="/flashcards/{id}/images/{side}">
       │
       ▼
[user clicks Edit] → ImageEditor.open({ source: url, mode: 'flashcard-existing' })
       │             (canvas loads via Image() — same-origin so no CORS issue)
       ▼
[user clicks Save] → exportPNG() → Blob → FormData
       │
       ▼
POST /flashcards/{id}/images/{side}  →  FlashcardService.replaceImage()
       │                                 → delete old stored file
       │                                 → store new (new UUID)
       │                                 → update entity field
       ▼
[response 204] → preview img.src += '?v=' + Date.now()  (cache-bust)
                 [editor closes]
```

### 4.3 Folder-detail image file
```
[user clicks Edit] → ImageEditor.open({ source: '/files/{id}/view',
                                         mode: 'file-existing',
                                         onSave: handleFileSave })
       │
       ▼
[user clicks Save (overwrite) OR Save as new]
       │
       ├── 'overwrite' → POST /files/{id}/edit (multipart 'image')
       │                 → FileEntryService.replaceContents()
       │                 → FileStorageService.replaceContents() (Files.copy REPLACE_EXISTING)
       │                 → returns fragments/folder-detail :: filesTable
       │
       └── 'new'       → POST /folders/{folderId}/files (existing endpoint)
                          with edited Blob as 'file' field
                          → FileEntryService.upload() (existing)
                          → returns redirect / files table swap
       │
       ▼
[response] → htmx:swap on #files-table-container → editor closes
```

### 4.4 Color picker
```
[trigger click] → ColorPicker.open(trigger, opts)
       │
       ▼
[user drags SV / hue / types in input]
       │
       ▼
[onChange(hex)] (rAF-throttled) → updates target hidden input + preview swatch
                                → calls user-supplied callback (e.g. updateFolderPreview)
       │
       ▼
[close: outside click / Escape / trigger reclick]
       │
       ▼
[onCommit(hex)] → push to localStorage 'recent' palette
```

---

## 5. Visual feedback & error handling

- **Save buttons** disable on click and swap their label icon to a spinning `loader-2`. On error: button re-enables, an inline `.sh-alert.sh-alert-danger` slides into the editor footer with the server message. On success: editor closes; for folder-detail, an HTMX swap also surfaces any server-side flash error.
- **Editor open** while the source image is loading shows a centered `loader-2` over the canvas area. Failure to load (404, decode error) shows an alert and a Retry button; Cancel closes the editor without modification.
- **Unsaved-changes guard**: closing the editor with a non-empty undo stack triggers `confirm('Discard changes?')`. Skipped when the stack is empty.

---

## 6. Constraints satisfied / notes

- **Heavy libs**: Fabric.js (~300KB gzipped) is the one external dependency. Justified by §1.3 — the layered editing model the user picked is intractable without it. No other libs.
- **HTMX workflow preserved**: existing modal patterns untouched. Editor save endpoints return either 204 or HTMX fragments; no JSON APIs.
- **Aesthetic**: all new components use existing CSS variables (`--sh-bg-elevated`, `--sh-primary`, `--sh-border`, `--sh-text`, `--sh-text-muted`), Lucide icons, and the same glassmorphism / blur recipe as existing modals.
- **Touch deferred but modular**: input goes through `ImageEditor`'s gesture layer; v2 just registers `pointerdown/pointermove/pointerup` on touch in the same hook.
- **Crop deferred**: trivially addable in v1.5 — Fabric supports `clipPath` and recompositing on a smaller canvas.

---

## 7. Implementation order (suggested)

1. `color-picker.js` + `color-picker.css` + fragment + folder-form integration. Self-contained, ships value alone, validates the design language.
2. Backend endpoints (`FlashcardController.replaceImage`, `FileController.editOverwrite`, service methods). Test with `curl` + a hand-crafted PNG.
3. `ImageEditor` module skeleton: modal shell, Fabric setup, brush + eraser + color, undo/redo, save → calls callback. No shapes/text yet.
4. Wire flashcard-form integration (both new and existing image flows). Manual QA both paths.
5. Wire folder-detail integration with overwrite + save-as-new. Manual QA.
6. Add shapes (rect, ellipse, line) and text tool.
7. Polish: zoom/pan with mouse wheel, keyboard shortcuts (`B`/`E`/`R`/`O`/`L`/`T`, Ctrl+Z/Ctrl+Shift+Z), accessibility (ARIA labels on tool buttons).

Each step leaves the app in a working state.
