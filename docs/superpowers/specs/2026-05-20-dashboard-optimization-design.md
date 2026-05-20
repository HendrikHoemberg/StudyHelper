# Dashboard Optimization Design

**Date:** 2026-05-20
**Status:** Approved (pending user review)
**Follows:** `2026-05-20-dashboard-redesign-design.md` (which introduced the cockpit layout)

## Problem

The newly introduced dashboard cockpit (`.sh-dashboard-cockpit`) wastes horizontal space on wide and medium screens:

- On 1440p and 1080p screens, the cockpit is capped at `max-width: 960px` and centered inside `.sh-explorer-detail`, leaving a large empty band to the right of the content (and a smaller one between the folders sidebar and the cockpit).
- On iPad landscape (1024×768), the 960px cockpit centers awkwardly inside the explorer detail, leaving ~32px gaps on each side that read as broken layout.
- On mobile, cumulative horizontal padding (shell 1.5rem + cockpit 0.75rem) eats viewport width, so cards visibly float in too much chrome instead of going edge-to-edge.

The user's goal is to fill the screen meaningfully at all breakpoints, polish the visual density, and make mobile cards span the full width.

## Goals

1. Cockpit fills the available horizontal space at every breakpoint.
2. Wide-screen layout adds a useful secondary column instead of just stretching existing content.
3. Mobile cards reach the viewport edges (minus a small framing margin).
4. iPad landscape renders as a fully utilized single-column page, not a centered 960px block.
5. Visual polish: tighter tile/card sizing so more useful content lands above the fold.

## Non-goals

- No new dashboard data (no new stats, streak counters, or backend additions).
- No change to the folders sidebar (`.sh-dashboard-sidebar` / `fragments/sidebar`).
- No restructuring of the routes, controllers, or `DashboardViewModel`.
- No theming changes beyond a small tweak to the diagonal-line overlay opacity.

## Approved approach

Adaptive 2-column cockpit with three breakpoint tiers. The right column on wide screens hosts the Review-mistakes card and the Recent activity feed (existing data, no backend changes).

### Breakpoint tiers

| Tier   | Range          | Cockpit layout |
|--------|----------------|----------------|
| Narrow | ≤ 767px        | Single column, edge-to-edge, sidebar as drawer |
| Medium | 768 – 1199px   | Single column, sidebar visible, no max-width cap |
| Wide   | ≥ 1200px       | 2-column grid: `minmax(0, 1fr)` + 320px |

1200px is chosen because at 1024px (iPad landscape) a 320px right column would crowd the left column; 1200px leaves enough room for both columns to breathe.

## Detailed changes

### 1. Cockpit container (`.sh-dashboard-cockpit`)

Current state (styles.css:5764):
```css
.sh-dashboard-cockpit {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
    max-width: 960px;
    margin: 0 auto;
    padding: 1.5rem 1rem;
}
```

New state:
- Remove `max-width: 960px` and `margin: 0 auto`.
- Default `display: grid` with a single column.
- At ≥ 1200px, switch to `grid-template-columns: minmax(0, 1fr) 320px` with `gap: 0` (the secondary column's left padding + border-left provides the visual separation; see §8).
- Padding becomes `1.5rem` at default, `1.5rem 1.5rem` on medium, `1rem 0` on narrow.

### 2. Markup change in `fragments/explorer.html`

The current `<main class="sh-dashboard-main sh-dashboard-cockpit">` contains a flat list of sections. To support the 2-column wide layout, wrap the children into two named grid areas:

```html
<main class="sh-dashboard-main sh-dashboard-cockpit">
    <div class="sh-cockpit-primary">
        <!-- greeting, action tiles, pinned, recently studied -->
    </div>
    <div class="sh-cockpit-secondary">
        <!-- resume card (if present), review-mistakes card, recent activity -->
    </div>
</main>
```

On narrow/medium tiers the two wrapper divs collapse via `display: contents` so the children flow as one column. On wide, the wrappers become the two grid cells.

Ordering inside `sh-cockpit-primary`: greeting → action tiles → pinned decks → recently studied decks.

Ordering inside `sh-cockpit-secondary`: resume card (if present) → review-mistakes card → recent activity.

(The resume card lives in the secondary column because it's a CTA-shaped prompt, same archetype as review-mistakes. Both share the "what should I do next" role.)

### 3. Review-mistakes card (`.sh-review-card`)

On wide tier (in the secondary column), the card becomes vertical:
- Icon on top.
- Title and meta below.
- Full-width "Study now" button at the bottom (replaces the inline right-side CTA).

On medium and narrow tiers, the card keeps its current horizontal banner form (with the existing `@media (max-width: 480px)` stacking rule unchanged for very narrow phones).

Implementation: add a `.sh-review-card.is-vertical` modifier class that the wide-tier media query (or a parent selector like `.sh-cockpit-secondary .sh-review-card`) applies. Prefer the parent selector — no markup changes needed.

### 4. Action tile row (`.sh-action-tile`)

- Padding: `1.5rem 1rem` → `1.25rem 1rem`.
- Icon font-size: `2rem` → `1.5rem`.
- Grid stays 3 columns at wide and medium; stacks to 1 column at ≤ 640px (current behavior, preserved).

### 5. Deck rails (`.sh-deck-rail`, `.sh-deck-tile`, `.sh-deck-cover`)

- `.sh-deck-rail` grid: `minmax(180px, 1fr)` → `minmax(170px, 1fr)` at wide and medium.
- On narrow at 480–767px: explicit 2-column grid.
- On narrow at < 480px: 1-column grid.
- `.sh-deck-cover` height: 120px → 100px at wide and medium; 90px at narrow.
- Pin button visibility: keep the existing always-visible behavior on touch devices (already implemented at `@media (hover: none) and (pointer: coarse)`). No change.

### 6. Recent activity (`.sh-activity-list`, `.sh-activity-row`)

When inside `.sh-cockpit-secondary` on wide tier:
- The list gets `max-height: calc(100vh - 12rem)` and `overflow-y: auto` so the right column doesn't stretch the page.
- Row gap: 0.5rem (down from 0.75rem) for density.

On medium and narrow tiers the list returns to its current full-width form below the deck rails — no max-height, no scrolling.

### 7. Shell padding for mobile

- `.sh-dashboard-shell` padding at ≤ 767px: `1.5rem` → `0.75rem`.
- `.sh-dashboard-cockpit` padding at ≤ 767px: `1rem 0.75rem` → `1rem 0`.

Net effect: cards span (viewport width − 1.5rem) instead of (viewport width − 4.5rem).

### 8. Secondary column separator

On wide tier, `.sh-cockpit-secondary` gets:
- `padding-left: 2rem`
- `border-left: 1px solid var(--border-light)`

This is the only thing that visually anchors the secondary column as "a different region" rather than "more content below." Without it the right column reads as orphaned cards. The cockpit's grid `gap` is `0` at wide tier so the border-left sits exactly at the column boundary with `2rem` content offset to its right.

### 9. Background overlay tweak

`.sh-dashboard-shell::before` diagonal lines opacity: `0.35` → `0.25` (light mode only). With denser content the existing 0.35 overlay competes with the cards. Dark mode stays at 0.25.

## Files touched

- `src/main/resources/templates/fragments/explorer.html` — add `.sh-cockpit-primary` / `.sh-cockpit-secondary` wrappers around existing children.
- `src/main/resources/static/css/styles.css` — replace the `.sh-dashboard-cockpit` block (~lines 5762–5946) and adjust `.sh-dashboard-shell` padding within the narrow media query.

No Java, controller, or view-model changes. No new templates or fragments.

## Testing plan

Manual verification at four viewports after implementation:

1. **1440×900 (wide):** Two-column cockpit. Left column has greeting + actions + decks. Right column has review-mistakes (vertical form) + scrollable activity feed. No empty band on the right.
2. **1080×~1920 portrait or 1280×800 laptop:** If width ≥ 1200px, behaves as wide. If 768–1199px, single stretched column.
3. **1024×768 (iPad landscape):** Single column, content fills the explorer detail area, no awkward centering.
4. **375×812 (iPhone-ish mobile):** Sidebar in drawer, cards span near full width with ~0.75rem margin, action tiles stacked 1-column at ≤ 640px, deck rail 1-column.

For each: confirm no horizontal scrollbar, gradient backdrop still visible, no overlap between sidebar and content, no broken `:has()` selectors during study session transitions.

Type-check / test commands: `./mvnw test -Dtest='DashboardControllerTests,DashboardServiceTests'` — these should remain passing untouched since no Java changes.

## Open questions

None. All decisions confirmed in brainstorming.
