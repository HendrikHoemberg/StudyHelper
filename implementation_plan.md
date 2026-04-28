# StudyHelper — Complete UI Redesign

A comprehensive redesign transforming StudyHelper from a basic Bootstrap-styled app into a polished, modern application with sidebar navigation, dark/light mode, prominent folder colors, and a refined study experience.

## Design Philosophy

**Clean & Minimalist foundation** — the UI itself stays neutral and uncluttered so that the user's custom folder colors become the primary visual accent throughout the app. Professional layout with playful micro-interactions (hover lifts, smooth transitions, subtle glow effects).

---

## Key Design Decisions

### Drop Bootstrap 5
Bootstrap is being replaced with a fully custom CSS design system. Rationale:
- Bootstrap's grid and utility classes add 230KB of CSS we barely use
- A custom system gives full control over the sidebar layout, dark mode tokens, and component aesthetics
- All current Bootstrap usage is limited to the grid (`row`, `col-*`), `d-flex` utilities, modals, and alert dismissal — all replaceable
- **Bootstrap JS** is still needed for modal behavior → we'll replace it with a lightweight custom modal system (or keep the slim Bootstrap JS bundle for modals only)

> [!IMPORTANT]
> **Modal strategy**: We'll keep `bootstrap.bundle.min.js` solely for modal show/hide/backdrop behavior since all CRUD actions use Bootstrap modals. Rebuilding modal logic from scratch adds risk for little gain. All Bootstrap *CSS* classes will be removed from templates.

### Lucide Icons (CDN)
Replace all inline SVGs with [Lucide Icons](https://lucide.dev/) via their unpkg CDN script. This gives us a consistent icon library with `<i data-lucide="folder">` syntax.

### Dark/Light Mode
CSS custom properties with a `[data-theme="dark"]` attribute on `<html>`. Toggle persisted to `localStorage`. Both themes use the same layout — only color tokens change.

---

## Proposed Changes

### Design Token System & Global Styles

#### [MODIFY] [styles.css](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/static/css/styles.css)

Complete rewrite. The new CSS will contain:

**1. Theme tokens (`:root` and `[data-theme="dark"]`)**
- Light mode: `--bg: #f9fafb`, `--surface: #ffffff`, `--surface-raised: #ffffff`, `--border: #e5e7eb`, `--text: #111827`, `--text-secondary: #6b7280`, etc.
- Dark mode: `--bg: #0f1117`, `--surface: #1a1d27`, `--surface-raised: #232734`, `--border: #2d3140`, `--text: #e5e7eb`, `--text-secondary: #9ca3af`, etc.
- Accent tokens: `--accent: #6366f1`, `--accent-hover: #4f46e5` (kept from current palette — clean indigo)
- Status: `--success: #10b981`, `--danger: #ef4444`, `--warning: #f59e0b`

**2. Base styles** — font (Inter), body, scrollbar, `*` box-sizing reset

**3. Layout system**
- `.app-layout` — CSS Grid: `grid-template-columns: 260px 1fr` for the sidebar + main content split
- `.app-sidebar` — fixed-height sidebar (`100vh`, `position: sticky; top: 0`), scrollable content
- `.app-main` — the main content area with padding
- Responsive: sidebar collapses to an off-canvas drawer below 768px

**4. Component styles** (all new):
- Sidebar: nav items, section headers, brand, theme toggle, user info
- Cards: `.card`, `.card-header`, `.card-body`
- Buttons: `.btn`, `.btn-primary`, `.btn-secondary`, `.btn-danger`, `.btn-ghost`, `.btn-icon`
- Badges: `.badge`, `.badge-primary`, `.badge-muted`
- Breadcrumbs
- Form controls: `.input`, `.label`, `.color-input`
- File table
- Folder cards & deck cards (with prominent color stripe/background tint)
- Flashcard grid items
- 3D flip card (refined)
- Study session: progress bar, integrated card buttons
- Modals (custom styling, `.modal-overlay`, `.modal-content`, `.modal-header`, etc.)
- Explorer tree items (with folder/deck icons)
- Empty states
- Alerts/toasts
- Animations: fade-in, slide-in, hover lifts
- Utility classes: flex, gap, margin, padding, text alignment (replacing Bootstrap's `d-flex`, `gap-2`, `mb-3`, etc.)

---

### Layout & Navigation

#### [MODIFY] [layout.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/layout.html)

Major restructuring:

- **Remove**: Bootstrap CSS CDN link
- **Add**: Lucide Icons CDN (`<script src="https://unpkg.com/lucide@latest/dist/umd/lucide.min.js"></script>`)
- **Replace** the `navbar` fragment with a new `sidebar` fragment and a `topbar` fragment

**New `sidebar` fragment:**
```
┌──────────────────────┐
│  📚 StudyHelper      │  ← Brand + logo
│                      │
│  ─── NAVIGATION ───  │
│  📁 My Folders       │  ← Link to /folders (active state)
│  📖 Study Session    │  ← Link to /study/start
│                      │
│  ─── ACCOUNT ─────   │
│  👤 alice             │  ← Username display
│  🚪 Sign out         │  ← Logout button
│                      │
│  ☀️/🌙 Theme toggle   │  ← Bottom of sidebar
└──────────────────────┘
```

**New `topbar` fragment** (replaces the per-page title/action bar):
- Sits at the top of the main content area
- Contains: page title (dynamically set), breadcrumb, and page-specific action buttons
- Mobile: includes hamburger menu to toggle sidebar

**New `appShell` fragment** — wraps sidebar + topbar + content slot into the grid layout so individual pages don't repeat the structure.

#### [MODIFY] [dashboard.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/dashboard.html)

- Use the new layout shell instead of navbar + container
- Body structure becomes: `sidebar | main > topbar + #main-content`

#### [MODIFY] [folder-page.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/folder-page.html)

- Same layout shell conversion

#### [MODIFY] [deck-page.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/deck-page.html)

- Same layout shell conversion

#### [MODIFY] [study-page.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/study-page.html)

- Same layout shell conversion

---

### Login Page

#### [MODIFY] [login.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/login.html)

- Remove Bootstrap CSS link, use only `styles.css`
- Add Lucide Icons CDN
- Redesign: centered card on a subtle gradient background (works in both light/dark mode)
- Add a small animated book/graduation-cap icon above the brand
- Refined input styling, button with hover glow
- Respect dark mode (the login page should detect `localStorage` theme preference and apply it)

---

### Dashboard / Explorer

#### [MODIFY] [explorer.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/explorer.html)

- **Page header**: "My Folders" title + action buttons move to the topbar area
- **Explorer tree items**: Redesigned with:
  - Lucide `folder` icon (colored with the folder's `colorHex`) instead of the small color dot
  - Subtle background tint using the folder color at low opacity (`background: color-mix(in srgb, var(--folder-color) 8%, transparent)`)
  - Left color stripe (3-4px, rounded) using the folder color
  - Card count badge on the right
  - Smooth expand/collapse with CSS `max-height` transition (not abrupt `display: none/block`)
- **Search bar**: Refined with icon inside, rounded corners, subtle shadow on focus
- **New Folder modal**: Updated with custom modal classes (no Bootstrap CSS)
- Replace all `d-flex`, `gap-*`, `mb-*` Bootstrap utilities with custom classes

#### [MODIFY] [folder-children.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/folder-children.html)

- Same tree-item redesign as explorer
- **Folders**: Lucide `folder` icon colored with `colorHex`
- **Decks**: Lucide `layers` icon (representing a stack of cards) in the accent color
- Clear visual distinction: folders have their custom color, decks use a neutral/accent icon

---

### Folder Detail Page

#### [MODIFY] [folder-detail.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/folder-detail.html)

- **Folder header**: Larger folder icon colored with the folder's color, prominent color accent
- **Breadcrumb**: Styled with Lucide `chevron-right` separators
- **Subfolders grid**: Cards with prominent color — each subfolder card gets:
  - A color-tinted header/stripe
  - Lucide `folder` icon in the folder color
  - Hover: slight lift + color glow
- **Decks grid**: Cards with Lucide `layers` icon, card count badge, hover lift
- **Files table**: Cleaner styling, file type icons using Lucide (`file-text`, `image`, `file`, etc.)
- **Action buttons**: Placed in topbar (Subfolder, Deck, Upload, Delete)
- **Modals**: Converted from Bootstrap classes to custom modal classes

---

### Deck Detail Page

#### [MODIFY] [deck.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/deck.html)

- **Deck header**: Lucide `layers` icon, card count badge
- **Flashcard grid**: Each card gets:
  - Cleaner front/back separation (subtle color divider)
  - Edit/delete icons using Lucide (`pencil`, `trash-2`)
  - Hover lift effect with shadow
- **Action buttons** (Add Card, Rename, Delete, Start Study) in the topbar
- **Modals**: Converted to custom modal classes

---

### Study Session

#### [MODIFY] [study-setup.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/study-setup.html)

- Clean card-based layout for mode selection
- Radio buttons styled as selectable cards (entire card is clickable, selected state has accent border + background tint)
- Deck picker with Lucide `layers` icons and folder path labels
- All Bootstrap classes replaced

#### [MODIFY] [study-card.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/study-card.html)

Major redesign of the study card experience:

**Progress bar** (top of the study area):
- Thin horizontal bar showing `currentCardNumber / totalCards` progress
- Animated fill with accent color
- Text label: "Card 3 of 10" displayed alongside or below the bar
- The progress data (`currentCardNumber`, `totalCards`) is already available in the template model

**Flip card redesign**:
- The card itself becomes the entire interaction surface
- **Front face**: Shows question text centered, with a small "Tap to reveal" hint at the bottom + Lucide `eye` icon
- The "Reveal Answer" button is **inside the front face** of the card (a subtle button/text at the bottom of the card)
- **Back face**: Shows answer text centered
- Below the answer text (still inside the back face): two round icon buttons side by side:
  - ✓ **Correct** button: round, green background, Lucide `check` icon, no text
  - ✕ **Incorrect** button: round, red background, Lucide `x` icon, no text
- This means the answer buttons are part of the card's back face, keeping the entire flow contained within the card

**Layout change**:
- Remove the external "Reveal Answer" button and external answer controls
- Everything is inside the flip card faces
- The card should be larger/more prominent (centered, max-width ~640px, min-height ~400px)

> [!IMPORTANT]
> The Correct/Incorrect buttons on the back face still need to submit HTMX POST requests to `/session/answer`. They will be `<form>` elements with `hx-post` attributes, styled as round icon buttons inside the card's back face.

#### [MODIFY] [study-complete.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/study-complete.html)

- Minor styling refresh only (no layout changes per user request)
- Use custom classes instead of Bootstrap
- Add Lucide icons to stat cards (`hash`, `check-circle`, `x-circle`, `percent`)

---

### JavaScript

#### [MODIFY] [app.js](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/static/js/app.js)

- **Add**: Theme toggle logic
  - Read `localStorage.getItem('theme')` on load, default to `'light'`
  - Set `document.documentElement.dataset.theme` accordingly
  - Toggle function switches the attribute and saves to `localStorage`
- **Add**: `lucide.createIcons()` call after DOMContentLoaded and after every `htmx:afterSwap` (Lucide needs to be re-initialized after HTMX swaps new content)
- **Add**: Sidebar mobile toggle logic (hamburger button shows/hides sidebar overlay)
- **Modify**: `initStudyCard()` — the reveal button is now inside the card's front face; the answer controls are inside the back face. The flip logic stays the same but selectors change.
- **Modify**: `initExplorer()` — expand/collapse may use CSS transitions now (the JS toggles a class, CSS handles the animation)
- **Add**: Custom modal open/close logic (if we decide to drop Bootstrap JS entirely) — **OR** keep calling `bootstrap.Modal` if we retain the slim JS bundle
- **Remove**: References to Bootstrap-specific classes like `d-none`, `d-flex`, etc.
- Smooth animated expand/collapse for explorer tree using `max-height` transitions

---

## Resolved Decisions

- **Bootstrap JS**: Fully dropped. Custom lightweight modal system (~50 lines JS).
- **Study session layout**: Focused/immersive mode — sidebar hidden during active study card flow.
- **Mobile breakpoint**: 768px confirmed.

---

## File Change Summary

| File | Action | Scope |
|------|--------|-------|
| [styles.css](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/static/css/styles.css) | REWRITE | Complete new design system (~1200 lines) |
| [app.js](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/static/js/app.js) | MODIFY | Theme toggle, Lucide init, sidebar toggle, updated selectors |
| [layout.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/layout.html) | REWRITE | Sidebar + topbar layout, Lucide CDN, remove Bootstrap CSS |
| [dashboard.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/dashboard.html) | MODIFY | Use new layout shell |
| [folder-page.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/folder-page.html) | MODIFY | Use new layout shell |
| [deck-page.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/deck-page.html) | MODIFY | Use new layout shell |
| [study-page.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/study-page.html) | MODIFY | Use new layout shell |
| [login.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/login.html) | MODIFY | Restyle without Bootstrap, dark mode support |
| [explorer.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/explorer.html) | REWRITE | New tree design with Lucide icons, color-prominent folders |
| [folder-children.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/folder-children.html) | REWRITE | Folder/deck icon distinction, color styling |
| [folder-detail.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/folder-detail.html) | REWRITE | New layout, color-prominent cards, custom modals |
| [deck.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/deck.html) | REWRITE | New styling, Lucide icons, custom modals |
| [study-setup.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/study-setup.html) | REWRITE | New card-based mode selector, custom classes |
| [study-card.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/study-card.html) | REWRITE | Progress bar, buttons inside card, new layout |
| [study-complete.html](file:///home/hendrik/Documents/Coding/StudyHelper/src/main/resources/templates/fragments/study-complete.html) | MODIFY | Replace Bootstrap classes, add Lucide icons |

**No Java/backend changes required.** All model attributes and endpoints remain identical.

---

## Verification Plan

### Automated Tests
- No existing frontend tests to run
- Build the Spring Boot app (`./mvnw compile`) to verify Thymeleaf templates parse correctly

### Manual Verification (Browser)
After implementation, use the browser tool to verify each page:
1. **Login page** — renders correctly in both light and dark mode
2. **Dashboard** — sidebar visible, explorer tree expands/collapses, folder colors are prominent, search works
3. **Folder detail** — breadcrumb navigation, subfolder/deck cards display correctly, file table sorts, modals open/close
4. **Deck detail** — flashcard grid renders, edit/delete actions work, modals function
5. **Study session setup** — mode selection works, deck picker functions, form submits
6. **Study card** — flip animation works, progress bar shows correct values, correct/incorrect buttons inside the card submit properly
7. **Study complete** — stats display correctly, redo/reconfigure/back links work
8. **Theme toggle** — switching themes updates all pages, persists across navigation
9. **Mobile responsive** — sidebar collapses, content is usable on narrow viewports

### Visual Checks
- Folder colors are the dominant visual accent (not lost in the UI)
- Lucide icons render correctly after HTMX swaps
- Dark mode has proper contrast ratios
- All transitions and animations feel smooth
