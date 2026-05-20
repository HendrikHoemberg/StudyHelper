# Dashboard Redesign — Mobile Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the new cockpit dashboard genuinely usable on phones. The primary plan (`2026-05-20-dashboard-redesign.md`) ships a working dashboard but only minimally accounts for touch devices and narrow viewports; this follow-up closes that gap.

**Architecture:** CSS-only and template-only changes. No new entities, services, or routes. Touch detection is handled via the `(hover: none) and (pointer: coarse)` media query (the modern, reliable signal) rather than user-agent sniffing. Width breakpoints align with the existing `640px` breakpoint already used by the main plan.

**Tech Stack:** Thymeleaf, CSS (the project's main stylesheet), htmx — no new tooling.

**Prerequisite:** The main dashboard redesign plan (`2026-05-20-dashboard-redesign.md`) must be merged first. This plan modifies templates and CSS that plan introduces.

---

## File map

**Modify:**
- The project's main stylesheet (the file containing `.sh-action-tile`, `.sh-deck-rail`, etc. — locate with `grep -rln "sh-action-tile" src/main/resources/static`)
- `src/main/resources/templates/fragments/explorer.html` — small markup tweaks for tap-target sizing and review-card stacking

**No new files.**

---

## Scope

In scope:
- Make the pin button always visible (not hover-revealed) on touch devices.
- Make the pin button (and other deck-tile action buttons it sits next to) at least 44×44px on touch — Apple's HIG and WCAG 2.5.5 minimum.
- Stack the review card vertically below ~480px so the "Study now" CTA never wraps awkwardly.
- Reduce the greeting title size below ~480px so it doesn't dominate the viewport.
- Tighten dashboard horizontal padding below ~480px.
- Verify the existing sidebar/folder drawer still works correctly when the new dashboard is open.
- Add a mobile smoke-test step to the verification flow.

Out of scope:
- Long-press / context menu for pin (the "always visible" treatment is sufficient).
- Native-app-shell tweaks (PWA manifest changes).
- Reworking the existing sidebar drawer itself.
- Tablet-specific breakpoints (the existing `640px` breakpoint covers phones; tablets fall into the desktop layout, which already works).

---

## Task 1: Make the pin button visible by default on touch devices

The pin button currently lives in `.sh-file-tile-actions`, which (based on the existing codebase pattern for `.sh-deck-edit-btn`) is hover-revealed. On phones with no hover, the user can't discover it.

**Files:**
- Modify: the main stylesheet (see "File map")

- [ ] **Step 1: Locate the stylesheet**

```bash
grep -rln "sh-action-tile" src/main/resources/static
```

Note the path. The following steps refer to it as `STYLES.css`.

- [ ] **Step 2: Inspect the existing `.sh-file-tile-actions` rule**

```bash
grep -n "sh-file-tile-actions\|sh-deck-edit-btn" STYLES.css
```

Confirm the current behavior. It is almost certainly opacity- or display-toggled on hover of the parent tile. Note the parent selector used (likely `.sh-deck-tile:hover .sh-file-tile-actions { ... }`).

- [ ] **Step 3: Append the touch-device override**

At the bottom of `STYLES.css`, append:

```css
/* ─── Dashboard mobile polish ─────────────────────────────── */

/* On touch devices, deck-tile action buttons are always visible.
   Hover-reveal patterns hide critical affordances (pin/edit/delete)
   on phones where there is no hover state. */
@media (hover: none) and (pointer: coarse) {
    .sh-deck-tile .sh-file-tile-actions {
        opacity: 1 !important;
        visibility: visible !important;
        pointer-events: auto !important;
    }
}
```

The `!important` is justified here: this rule must beat the more-specific hover rule it is countering, and `(hover: none)` already restricts it to touch devices so it cannot leak into desktop styles.

- [ ] **Step 4: Verify with browser devtools**

Start the app:
```bash
./mvnw spring-boot:run
```

Open the dashboard. In Chrome devtools, toggle device emulation to "iPhone 14 Pro" (or any touch profile). Confirm the pin button on deck tiles is visible without hovering. Stop the app.

- [ ] **Step 5: Commit**

```bash
git add <STYLES.css>
git commit -m "$(cat <<'EOF'
fix: keep deck-tile action buttons visible on touch devices

Hover-reveal hides pin/edit/delete affordances on phones. Use
(hover: none) and (pointer: coarse) to force them visible there.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Audit and fix tap-target sizes

WCAG 2.5.5 (AAA) recommends a 44×44px minimum tap target. The pin button currently inherits `.sh-deck-edit-btn` sizing, which is icon-sized.

**Files:**
- Modify: `STYLES.css`

- [ ] **Step 1: Check current button size**

```bash
grep -n "sh-deck-edit-btn" STYLES.css
```

Read the rule. If `width`/`height`/`padding` give a tap area smaller than 44px on either axis, it fails the audit.

- [ ] **Step 2: Append a touch-device sizing override**

Below the rule added in Task 1, append:

```css
@media (hover: none) and (pointer: coarse) {
    .sh-deck-tile .sh-deck-edit-btn {
        min-width: 44px;
        min-height: 44px;
        padding: 0.5rem;
    }

    .sh-deck-tile .sh-deck-edit-btn iconify-icon {
        font-size: 1.25rem;
    }
}
```

Using `min-width`/`min-height` rather than fixed `width`/`height` ensures the button still grows if its content demands more, and `min-*` does not stretch the button container if the icon is small.

- [ ] **Step 3: Verify the action tiles also pass**

The `.sh-action-tile` row has `padding: 1.5rem 1rem` and contains a 2rem icon plus text. That is well over 44px in both dimensions. No change needed.

The review card has `padding: 1rem 1.25rem`. With icon + body + CTA, it is also well over 44px. No change needed.

- [ ] **Step 4: Visually verify in devtools mobile emulation**

Boot the app, emulate a phone, hold an inspector over the pin button — it should now report at least 44×44px. Confirm the action tiles and review card also tap easily.

Stop the app.

- [ ] **Step 5: Commit**

```bash
git add <STYLES.css>
git commit -m "$(cat <<'EOF'
fix: enlarge deck-tile action buttons on touch devices to 44px min

Meets WCAG 2.5.5 tap-target guidance.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Stack the review card vertically on narrow viewports

Below ~480px, the review card's "Study now" CTA can wrap onto a new line awkwardly inside a horizontal flex. Stack the whole card vertically below that breakpoint and left-align everything.

**Files:**
- Modify: `STYLES.css`

- [ ] **Step 1: Append the breakpoint rule**

```css
@media (max-width: 480px) {
    .sh-review-card {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.5rem;
    }

    .sh-review-card-cta {
        align-self: flex-end;
    }
}
```

The CTA aligns to the right at the bottom so the "Study now" affordance is still the last thing the eye lands on, matching the desktop reading order.

- [ ] **Step 2: Visually verify**

Boot the app, narrow the viewport to ~375px (iPhone SE width). The review card should read:
- Icon and "Review mistakes" + count stacked at the top
- "Study now" pinned to the right at the bottom

Stop the app.

- [ ] **Step 3: Commit**

```bash
git add <STYLES.css>
git commit -m "$(cat <<'EOF'
fix: stack review-mistakes card vertically under 480px

Prevents the "Study now" CTA from wrapping awkwardly on narrow screens.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Tighten dashboard typography and padding on narrow viewports

The greeting `<h1>` inherits the existing `.sh-page-title` size, which is likely tuned for desktop. On a 375px viewport it can dominate. Also tighten horizontal padding below 480px so content uses available width.

**Files:**
- Modify: `STYLES.css`

- [ ] **Step 1: Append the rule**

```css
@media (max-width: 480px) {
    .sh-dashboard-cockpit {
        padding: 1rem 0.75rem;
        gap: 1.25rem;
    }

    .sh-dashboard-greeting .sh-page-title {
        font-size: 1.5rem;
        line-height: 1.2;
    }

    .sh-dashboard-greeting .sh-page-subtitle {
        font-size: 0.95rem;
    }

    .sh-dashboard-section .sh-section-title {
        font-size: 1rem;
    }
}
```

- [ ] **Step 2: Visually verify**

Boot the app, narrow viewport. Greeting should fit on 1–2 lines without overflow. Stop the app.

- [ ] **Step 3: Commit**

```bash
git add <STYLES.css>
git commit -m "$(cat <<'EOF'
fix: tighten dashboard typography and padding under 480px

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Verify sidebar/drawer interaction with the new dashboard

The app already uses a folder drawer on mobile (per README). Confirm the new dashboard layout doesn't fight it: the drawer should overlay or push the dashboard correctly, the dashboard should reflow correctly when the drawer closes, and the action tiles shouldn't be obscured.

This is a verification task, not a code change. If issues are found, address them inline.

- [ ] **Step 1: Boot the app and open the dashboard on a mobile viewport**

```bash
./mvnw spring-boot:run
```

Open Chrome devtools, emulate iPhone 14 Pro (390×844). Log in.

- [ ] **Step 2: Toggle the folder drawer**

Open and close the folder drawer (whatever the existing mobile trigger is — usually a hamburger in the top nav). Watch for:
- Drawer opens without overlapping the dashboard content awkwardly.
- Dashboard remains scrollable while the drawer is open *or* the drawer scrolls correctly (whichever the current pattern is).
- Closing the drawer returns the dashboard to its full layout with no visible glitch.
- The action tiles row remains tappable after the drawer closes.

- [ ] **Step 3: If issues are found**

If the drawer pattern interferes with `.sh-dashboard-cockpit`'s `max-width: 960px` centering, the most common fix is removing the centering on mobile:

```css
@media (max-width: 640px) {
    .sh-dashboard-cockpit {
        max-width: none;
        margin: 0;
    }
}
```

If no issues, skip this fix and move to Step 4.

- [ ] **Step 4: Commit any fixes**

If you made changes in Step 3:

```bash
git add <STYLES.css>
git commit -m "$(cat <<'EOF'
fix: drop dashboard max-width on mobile so it works with the folder drawer

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

If nothing changed, no commit is needed.

---

## Task 6: Mobile smoke test

Walk the user journey on a real mobile viewport. This catches things desktop testing misses: thumb reach, scroll behavior, animation feel.

- [ ] **Step 1: Boot and emulate iPhone 14 Pro (390×844)**

```bash
./mvnw spring-boot:run
```

Chrome devtools → device emulation → iPhone 14 Pro.

- [ ] **Step 2: Run through the dashboard journey**

1. Open `/dashboard`. Confirm:
   - Greeting fits on 1–2 lines.
   - Action tiles stack vertically (single column).
   - Vertical scroll feels right — no horizontal scrollbar appears at any time.
2. Tap "Start studying" → wizard opens. Confirm tap landed reliably.
3. Complete a flashcard session with at least one wrong answer.
4. Tap "Back to Dashboard". Confirm:
   - Review-mistakes card appears at the top.
   - "Study now" CTA on the card is bottom-right and doesn't wrap.
   - Recently-studied rail shows the deck. Tile is tappable.
   - Pin button is visible without hovering, and tapping it flips the icon.
5. Pin a deck. Confirm it appears in the "Pinned" rail above "Recently studied".
6. Open the folder drawer. Confirm the dashboard remains usable (per Task 5 verification).
7. Close the drawer. Tap a deck tile. Confirm navigation works.

- [ ] **Step 3: Also test on a real device if available**

Emulators miss touch quirks (iOS rubber-band scroll, Safari's bottom-bar overlap). If a real phone is reachable on the network, open the app there and repeat steps 1–7.

- [ ] **Step 4: Stop the app**

Stop the dev server. No commit needed unless something broke and was fixed in earlier tasks.

- [ ] **Step 5: Run the test suite once more**

```bash
./mvnw test
```

Expected: all pass. Mobile polish is CSS-only; nothing should have regressed.

---

## Notes for the implementer

- **The `(hover: none) and (pointer: coarse)` media query is the modern test for touch.** It is not perfect (a hybrid laptop with a touch screen reports `coarse` while having a mouse plugged in), but it is the recommended signal and dramatically better than UA sniffing. Living with a touch laptop edge-case is acceptable.
- **`!important` is used deliberately in Task 1.** Generally avoid it, but here it counters a more-specific hover rule and the media query strictly limits its scope. If you can reorder rules or beat the specificity without `!important`, prefer that — but don't twist into knots over it.
- **Tap-target sizes only matter on touch.** The desktop hover-revealed treatment of `.sh-deck-edit-btn` is fine for mouse precision. The Task 2 rule only applies inside `(hover: none) and (pointer: coarse)`.
- **Don't redesign the sidebar drawer.** If the drawer interaction is broken on the new dashboard, the fix is in the dashboard layout (Task 5 Step 3 shows the most likely one). The drawer itself is shared infrastructure outside the scope of this plan.
- **Real-device testing matters more than emulator testing.** If you have a phone, use it. Chrome devtools emulation gets viewport size and touch right but misses iOS Safari's bottom URL bar overlap and other vendor quirks.
