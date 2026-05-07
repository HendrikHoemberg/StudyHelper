# Tabbed Folder Page Layout — Design Spec

## Summary

Replace the current stacked folder page layout (subfolders → decks → files, all visible at once) with a tabbed interface: **Folders | Decks | Files**. Each tab shows its content in a dedicated panel. Tab switching is server-driven via HTMX.

## Current State

`folder-page.html` renders `fragments/folder-detail.html`, which contains:
- Breadcrumb navigation
- Folder header card (name, card count, action buttons)
- Subfolders section (conditional, card grid)
- Decks section (conditional, card grid)
- Files section (always present, sortable table)

All sections are stacked vertically. Users scroll through everything.

## Proposed Architecture

### Layout Structure

```
┌─────────────────────────────────────────────┐
│ Breadcrumb                                  │
─────────────────────────────────────────────┤
│ Folder Header Card                          │
│  [icon] Folder Name  [badge]                │
│  [Study] [Edit] [Delete] ← primary actions  │
├─────────────────────────────────────────────┤
│ Tabs: [Folders] [Decks] [Files]             │
├─────────────────────────────────────────────┤
│ Tab Content Panel (HTMX-swapped)            │
│                                             │
│  Folders: subfolder cards + add button      │
│  Decks:   deck cards + new deck button      │
│  Files:   file table + upload button        │
─────────────────────────────────────────────┘
```

### Tab Switching Flow

1. User clicks a tab link
2. HTMX fires `GET /folders/{id}?tab=decks` with `hx-target="#tab-content"`
3. Server renders the folder header + the requested tab fragment
4. Only the `#tab-content` region is replaced (OOB swap)
5. URL is pushed via `hx-push-url`

### Backend Changes

**Controller**: `FolderController` (or equivalent)
- Existing `GET /folders/{id}` endpoint accepts optional `?tab=` query param
- Default tab is `folders` (no param = folders tab)
- Returns `folder-page.html` with the appropriate tab fragment active
- Tab state is tracked via query param, not session

**Model/View**: `FolderView` (or equivalent DTO)
- Add `activeTab` field (enum: `FOLDERS`, `DECKS`, `FILES`)
- Existing fields (`subFolders`, `decks`, `files`) remain unchanged

### Template Changes

**`fragments/folder-detail.html`**:
- Wrap subfolders/decks/files sections in a tab container
- Add tab bar markup above the content area
- Each tab link uses `hx-get` with `?tab=` param
- Content panels are wrapped in `#tab-content` target div
- Move "Upload" button from files card header to Files tab header
- Move "Deck" button from folder header to Decks tab header
- Move "Subfolder" button from folder header to Folders tab header

**New fragment: `fragments/tab-bar.html`**:
- Renders the three tab links
- Active tab gets `aria-selected="true"` and underline styling
- Each tab shows a count badge: `Folders (2)`, `Decks (3)`, `Files (1)`

**New fragment: `fragments/tab-folders.html`**:
- Subfolder card grid (reuse existing `sh-d-pill` or `sh-card-grid` styles)
- "Add Subfolder" dashed card at end of grid
- Empty state: "No subfolders yet" + CTA button

**New fragment: `fragments/tab-decks.html`**:
- Deck card grid (reuse existing `sh-deck-card` styles)
- "New Deck" button in tab header area
- Empty state: "No decks yet" + CTA button

**New fragment: `fragments/tab-files.html`**:
- File table (reuse existing `sh-file-table` with sort headers)
- "Upload File" button in tab header area
- Empty state: "No files yet" + CTA button

### CSS Changes

**`styles.css`**:
- Add `.sh-tab-bar` styles: flex row, border-bottom, gap
- Add `.sh-tab-link` styles: padding, muted color, transition
- Add `.sh-tab-link[aria-selected="true"]` styles: accent color, bottom border indicator
- Add `.sh-tab-panel` styles: padding, min-height
- Add `.sh-tab-header` styles: flex row with title + action button
- Reuse existing card, grid, and table styles for tab content

### HTMX Attributes

Tab links:
```html
<a hx-get="@{/folders/{id}(id=${view.folder.id}, tab='decks')}"
   hx-target="#tab-content"
   hx-push-url="true"
   hx-swap="innerHTML"
   aria-selected="false">
  Decks <span class="sh-tab-badge" th:text="${view.decks.size()}">0</span>
</a>
```

Tab content container:
```html
<div id="tab-content" class="sh-tab-panel">
  <div th:replace="~{fragments/tab-${view.activeTab} :: tabContent}"></div>
</div>
```

### Error Handling

- If `?tab=` value is invalid (not folders/decks/files), default to `folders`
- If HTMX request fails, show existing error alert in the folder detail area
- Tab state is lost on error — user returns to default tab

### Accessibility

- Tab bar uses `role="tablist"`, tabs use `role="tab"`, panels use `role="tabpanel"`
- `aria-selected` toggles on active tab
- `aria-controls` links each tab to its panel
- Keyboard navigation: arrow keys move between tabs, Enter/Space activates

## Data Flow

```
User clicks "Decks" tab
  → HTMX GET /folders/42?tab=decks
  → Controller parses tab param, sets activeTab=DECKS
  → Controller loads folder + subfolders + decks + files
  → Thymeleaf renders folder-detail with tab-decks fragment
  → HTMX swaps #tab-content innerHTML
  → URL updates to /folders/42?tab=decks
```

## Migration Notes

- Existing bookmarked URLs (`/folders/42`) continue to work — default tab is folders
- Sidebar folder tree is unaffected
- Deck and file detail pages are unaffected
- No database changes required
- No API changes required (HTMX endpoints are internal)

## Files to Modify

| File | Change |
|------|--------|
| `templates/fragments/folder-detail.html` | Add tab bar, restructure content into tab panels |
| `templates/fragments/tab-bar.html` | New: tab navigation markup |
| `templates/fragments/tab-folders.html` | New: folders tab content |
| `templates/fragments/tab-decks.html` | New: decks tab content |
| `templates/fragments/tab-files.html` | New: files tab content |
| `static/css/styles.css` | Add tab bar, tab link, tab panel styles |
| `java/.../FolderController.java` | Accept `?tab=` param, set activeTab on view |
| `java/.../FolderView.java` | Add `activeTab` field |
