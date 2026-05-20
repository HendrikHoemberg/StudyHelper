# Dashboard Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the dashboard cockpit into an adaptive 2-column layout that fills the screen at every breakpoint, with edge-to-edge cards on mobile and a useful secondary column (review + activity) on wide screens.

**Architecture:** CSS + Thymeleaf-only change. The cockpit (`.sh-dashboard-cockpit`) becomes a CSS grid. Two wrapper divs (`.sh-cockpit-primary`, `.sh-cockpit-secondary`) split the existing children into left/right groups; on narrow/medium tiers they use `display: contents` so children flow as a single column; on wide (≥ 1200px) they become two grid cells. No Java, controller, or view-model changes.

**Tech Stack:** Thymeleaf templates, plain CSS, Spring Boot (untouched for this work). Verification is visual at four viewport sizes plus existing JUnit tests must keep passing.

**Spec:** `docs/superpowers/specs/2026-05-20-dashboard-optimization-design.md`

---

## Pre-flight

Before starting, confirm the working tree is clean and the dev server can run.

- [ ] **Pre-flight 1: Confirm clean tree**

Run: `git status`
Expected: `working tree clean` on `main` (or your feature branch).

- [ ] **Pre-flight 2: Start the dev server in a background terminal you can reach**

Run: `./mvnw spring-boot:run` (in a separate terminal, leave running)
Expected: app boots on `http://localhost:8080`.

You will reload the dashboard page (`http://localhost:8080/`, logged in as `hendrik`) after each visual task. Browser dev-tools responsive mode is enough — you do not need real devices. Use these viewport widths for verification:

| Tier   | Viewport       | What to confirm |
|--------|----------------|-----------------|
| Wide   | 1440 × 900     | Two-column cockpit; right column shows review + activity |
| Wide   | 1200 × 800     | Two-column cockpit just barely engaged |
| Medium | 1024 × 768     | Single stretched column (iPad landscape simulation) |
| Medium | 900 × 800      | Single column, no horizontal scroll |
| Narrow | 480 × 800      | Sidebar drawer, cards near edge-to-edge, 2-col deck rail |
| Narrow | 375 × 812      | Sidebar drawer, 1-col action tiles, 1-col deck rail |

---

## File map

- **Modify:** `src/main/resources/templates/fragments/explorer.html` — wrap dashboard children in primary/secondary divs (Task 1).
- **Modify:** `src/main/resources/static/css/styles.css` — replace the cockpit block (lines ~5762–5946) and tweak the dashboard-shell `::before` overlay (line ~459). All other tasks.

No new files. No deletions.

---

## Task 1: Add primary/secondary wrappers in the dashboard fragment

**Why:** The CSS grid in Task 2 needs two child elements to define the wide-tier two-column layout. On narrow/medium they will collapse via `display: contents` so the current flow is preserved.

**Files:**
- Modify: `src/main/resources/templates/fragments/explorer.html` (the `dashboardContent` fragment, lines ~6–114)

- [ ] **Step 1: Open the fragment and locate the `<main class="sh-dashboard-main sh-dashboard-cockpit">` block**

The block today contains, in order:
1. Greeting header
2. Resume card (conditional)
3. Review mistakes card (conditional)
4. Action tile row
5. Pinned decks section (conditional)
6. Recent decks section (conditional)
7. Recent activity section (conditional)

- [ ] **Step 2: Wrap children into two groups**

Replace the entire `<main class="sh-dashboard-main sh-dashboard-cockpit">` block with this exact markup. Note the children are partitioned: greeting + action tiles + pinned + recent decks go into `.sh-cockpit-primary`; resume + review-mistakes + recent activity go into `.sh-cockpit-secondary`.

```html
    <main class="sh-dashboard-main sh-dashboard-cockpit">

        <div class="sh-cockpit-primary">

            <!-- Greeting header -->
            <header class="sh-dashboard-greeting">
                <h1 class="sh-page-title" th:text="'Welcome back, ' + ${vm.greetingName()}">Welcome back</h1>
                <p class="sh-page-subtitle">What do you want to study today?</p>
            </header>

            <!-- Action tiles row (always shown) -->
            <div class="sh-action-tile-row">
                <a class="sh-action-tile" href="/study/start"
                   hx-get="/study/start" hx-target="#explorer-detail" hx-push-url="true">
                    <iconify-icon icon="lucide:book-open"></iconify-icon>
                    <span class="sh-action-tile-label">Start studying</span>
                </a>
                <a class="sh-action-tile" href="/flashcards/generate"
                   hx-get="/flashcards/generate" hx-target="#explorer-detail" hx-push-url="true">
                    <iconify-icon icon="lucide:sparkles"></iconify-icon>
                    <span class="sh-action-tile-label">Generate flashcards</span>
                </a>
                <a class="sh-action-tile" th:href="@{/study/start(mode='EXAM')}"
                   th:hx-get="@{/study/start(mode='EXAM')}" hx-target="#explorer-detail" hx-push-url="true">
                    <iconify-icon icon="lucide:file-edit"></iconify-icon>
                    <span class="sh-action-tile-label">Take an exam</span>
                </a>
            </div>

            <!-- Pinned decks rail -->
            <section th:if="${!vm.pinnedDecks().isEmpty()}" class="sh-dashboard-section">
                <h2 class="sh-section-title">Pinned</h2>
                <div class="sh-deck-rail">
                    <th:block th:each="deck : ${vm.pinnedDecks()}">
                        <div th:replace="~{fragments/explorer :: dashboardDeckTile(${deck})}"></div>
                    </th:block>
                </div>
            </section>

            <!-- Recent decks rail -->
            <section th:if="${!vm.recentDecks().isEmpty()}" class="sh-dashboard-section">
                <h2 class="sh-section-title">Recently studied</h2>
                <div class="sh-deck-rail">
                    <th:block th:each="deck : ${vm.recentDecks()}">
                        <div th:replace="~{fragments/explorer :: dashboardDeckTile(${deck})}"></div>
                    </th:block>
                </div>
            </section>

        </div>

        <div class="sh-cockpit-secondary">

            <!-- Resume card (only if saved session exists) -->
            <th:block th:if="${vm.resume().isPresent()}">
                <th:block th:replace="~{fragments/saved-session :: card}"></th:block>
            </th:block>

            <!-- Review mistakes card -->
            <a th:if="${vm.reviewMistakesCount() > 0}"
               class="sh-review-card"
               href="#"
               hx-post="/study/review-mistakes"
               hx-target="#explorer-detail"
               hx-push-url="true">
                <span class="sh-review-card-icon">
                    <iconify-icon icon="lucide:rotate-ccw"></iconify-icon>
                </span>
                <span class="sh-review-card-body">
                    <span class="sh-review-card-title">Review mistakes</span>
                    <span class="sh-review-card-meta">
                        <span th:text="${vm.reviewMistakesCount()}">0</span>
                        <span th:text="${vm.reviewMistakesCount() == 1 ? 'card to review' : 'cards to review'}">cards to review</span>
                    </span>
                </span>
                <span class="sh-review-card-cta">Study now</span>
            </a>

            <!-- Recent activity -->
            <section th:if="${!vm.recentActivity().isEmpty()}" class="sh-dashboard-section">
                <h2 class="sh-section-title">Recent activity</h2>
                <ul class="sh-activity-list">
                    <li th:each="row : ${vm.recentActivity()}" class="sh-activity-row">
                        <span class="sh-activity-icon">
                            <iconify-icon th:attr="icon=${row.type().name() == 'EXAM' ? 'lucide:file-edit' : (row.type().name() == 'QUIZ' ? 'lucide:list-checks' : 'lucide:book-open')}"></iconify-icon>
                        </span>
                        <div class="sh-activity-body">
                            <div class="sh-activity-title" th:text="${row.title()}">Title</div>
                            <div class="sh-activity-meta">
                                <span th:text="${row.correctCount()} + ' / ' + ${row.cardCount()}">0 / 0</span>
                                <span> · </span>
                                <span th:text="${#temporals.format(row.completedAt(), 'MMM d, HH:mm')}">date</span>
                            </div>
                        </div>
                    </li>
                </ul>
            </section>

        </div>

    </main>
```

- [ ] **Step 3: Smoke test the page still renders**

Reload `http://localhost:8080/` in the browser at any viewport.
Expected: all the same sections appear in the same order as before this task (greeting → review → actions → pinned → recent decks → activity). Visually it should look nearly identical to before — the wrappers don't change layout yet because CSS hasn't been touched.

Note: order will look identical to before *because* without CSS grid setup, `.sh-cockpit-primary` and `.sh-cockpit-secondary` are still plain block divs and their children flow top-to-bottom. The new ordering (primary then secondary) means review-mistakes now appears AFTER pinned decks instead of before action tiles — that's the expected state until Task 2.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/explorer.html
git commit -m "refactor: split dashboard cockpit into primary/secondary wrappers"
```

---

## Task 2: Cockpit grid container — remove max-width, add tier media queries

**Why:** Removes the 960px cap that left empty bands on wide screens. Sets up the grid that splits primary/secondary on ≥ 1200px and collapses cleanly on narrower screens.

**Files:**
- Modify: `src/main/resources/static/css/styles.css` (the `.sh-dashboard-cockpit` rule at ~line 5764, and the existing narrow media-query block at ~5917-5945)

- [ ] **Step 1: Replace the `.sh-dashboard-cockpit` block**

Find the existing block (around line 5762-5771):

```css
/* ─── Dashboard cockpit ───────────────────────────────────── */

.sh-dashboard-cockpit {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
    max-width: 960px;
    margin: 0 auto;
    padding: 1.5rem 1rem;
}
```

Replace with:

```css
/* ─── Dashboard cockpit ───────────────────────────────────── */

.sh-dashboard-cockpit {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 1.5rem;
    padding: 1.5rem;
}

.sh-cockpit-primary,
.sh-cockpit-secondary {
    display: contents;
}

.sh-cockpit-primary > * + *,
.sh-cockpit-secondary > * + * {
    margin-top: 1.5rem;
}

@media (min-width: 1200px) {
    .sh-dashboard-cockpit {
        grid-template-columns: minmax(0, 1fr) 320px;
        gap: 0;
    }

    .sh-cockpit-primary,
    .sh-cockpit-secondary {
        display: flex;
        flex-direction: column;
        gap: 1.5rem;
        min-width: 0;
    }

    .sh-cockpit-primary > * + *,
    .sh-cockpit-secondary > * + * {
        margin-top: 0;
    }
}
```

The `display: contents` trick + sibling-margin rule simulates the old flex-column gap behavior on narrow/medium tiers so children flow as one column with consistent 1.5rem vertical spacing. At ≥ 1200px the wrappers become real flex containers (two grid cells).

- [ ] **Step 2: Verify wide tier (1440 × 900)**

Reload at 1440 × 900.
Expected:
- Cockpit spans the full width of `.sh-explorer-detail` (no 960px cap).
- Right column ~320px wide containing resume (if any) + review-mistakes + recent activity.
- Left column contains greeting, action tiles, pinned decks, recent decks.
- No visual separator yet between columns (that's Task 3).

- [ ] **Step 3: Verify medium tier (1024 × 768 and 900 × 800)**

Resize browser to 1024 × 768, then 900 × 800.
Expected:
- Single column. All sections flow top-to-bottom in this order: greeting, action tiles, pinned, recent decks, resume (if any), review-mistakes, recent activity.
- Cockpit fills the explorer-detail width with 1.5rem padding.
- No horizontal scrollbar.

- [ ] **Step 4: Verify narrow tier (375 × 812)**

Resize to 375 × 812 (use browser dev-tools mobile emulation).
Expected:
- Single column, same flow as medium.
- Folders sidebar hidden behind the drawer toggle (existing behavior, unchanged).
- Cards still have some inner padding — full edge-to-edge fix is Task 6.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/css/styles.css
git commit -m "refactor: convert dashboard cockpit to adaptive grid"
```

---

## Task 3: Secondary column separator and activity scrolling

**Why:** Without a visual divider, the right column on wide screens reads as orphaned cards. Adding a left border + padding anchors it as a distinct region. The activity list also needs a max-height so it doesn't push the page when there are many entries.

**Files:**
- Modify: `src/main/resources/static/css/styles.css` — add new rules at the end of the cockpit section.

- [ ] **Step 1: Add wide-tier secondary-column styling**

In the `@media (min-width: 1200px)` block from Task 2, extend it. Find the block and add these rules inside it (right after the existing rules):

```css
    .sh-cockpit-secondary {
        padding-left: 2rem;
        border-left: 1px solid var(--border-light);
    }

    .sh-cockpit-secondary .sh-activity-list {
        max-height: calc(100vh - 16rem);
        overflow-y: auto;
        gap: 0.5rem;
    }
```

Final wide-tier block should look like:

```css
@media (min-width: 1200px) {
    .sh-dashboard-cockpit {
        grid-template-columns: minmax(0, 1fr) 320px;
        gap: 0;
    }

    .sh-cockpit-primary,
    .sh-cockpit-secondary {
        display: flex;
        flex-direction: column;
        gap: 1.5rem;
        min-width: 0;
    }

    .sh-cockpit-primary > * + *,
    .sh-cockpit-secondary > * + * {
        margin-top: 0;
    }

    .sh-cockpit-secondary {
        padding-left: 2rem;
        border-left: 1px solid var(--border-light);
    }

    .sh-cockpit-secondary .sh-activity-list {
        max-height: calc(100vh - 16rem);
        overflow-y: auto;
        gap: 0.5rem;
    }
}
```

- [ ] **Step 2: Verify at 1440 × 900**

Reload.
Expected:
- A thin vertical border separates the left (primary) column from the right (secondary) column.
- Right column content sits 2rem to the right of the border.
- Recent activity list, if it has many rows, becomes scrollable inside the secondary column instead of stretching the page.

- [ ] **Step 3: Verify at 1024 × 768**

Reload at iPad-landscape width.
Expected:
- No border visible (we are below the 1200px wide tier).
- Recent activity flows below the decks as a normal list (no max-height).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/styles.css
git commit -m "feat: add visual divider and scroll behavior to dashboard secondary column"
```

---

## Task 4: Review-mistakes card vertical variant in the secondary column

**Why:** In the right column the card needs to read top-down (icon → title/meta → CTA) instead of left-right, so it fits a narrow column without looking cramped.

**Files:**
- Modify: `src/main/resources/static/css/styles.css` — add a parent-selector rule for the review card inside the secondary column.

- [ ] **Step 1: Add the vertical variant rules**

Add this block immediately after the wide-tier media query you finished in Task 3:

```css
@media (min-width: 1200px) {
    .sh-cockpit-secondary .sh-review-card {
        flex-direction: column;
        align-items: stretch;
        gap: 0.5rem;
        padding: 1rem;
    }

    .sh-cockpit-secondary .sh-review-card-icon {
        align-self: flex-start;
    }

    .sh-cockpit-secondary .sh-review-card-body {
        flex: none;
    }

    .sh-cockpit-secondary .sh-review-card-cta {
        align-self: stretch;
        text-align: center;
        padding: 0.5rem 0.75rem;
        border-radius: 0.5rem;
        background: rgba(255, 120, 80, 0.12);
        margin-top: 0.25rem;
    }
}
```

- [ ] **Step 2: Verify at 1440 × 900**

Make sure the dashboard has at least one card needing review (the `Review mistakes` card will only render when `vm.reviewMistakesCount() > 0`). Use an account that has reviewable cards, or visit any deck and intentionally mark a card wrong.

Reload.
Expected:
- The Review-mistakes card in the right column is now vertical: icon at top-left, title/meta in the middle, "Study now" button as a full-width pill at the bottom of the card.

- [ ] **Step 3: Verify at 1024 × 768 and 480 × 800**

Reload at 1024 × 768, then 480 × 800.
Expected:
- The Review-mistakes card keeps its original horizontal banner form at both these widths.
- At 480 the existing `@media (max-width: 480px)` rule (styles.css:5917) keeps stacking it — unchanged.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/styles.css
git commit -m "feat: vertical review-mistakes card variant for wide secondary column"
```

---

## Task 5: Polish — action tiles, deck rails, deck covers

**Why:** Slim down chunky tiles so more content fits above the fold and the page feels less sparse.

**Files:**
- Modify: `src/main/resources/static/css/styles.css` — edit existing action-tile, deck-rail, and deck-cover rules.

- [ ] **Step 1: Reduce action-tile padding and icon size**

Find the existing rule (around line 5783):

```css
.sh-action-tile {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.75rem;
    padding: 1.5rem 1rem;
    border-radius: 1rem;
    background: var(--surface-raised);
    color: var(--text);
    text-decoration: none;
    font-weight: 600;
    border: 1px solid var(--border);
    transition: transform 120ms ease, background 120ms ease;
}

.sh-action-tile iconify-icon {
    font-size: 2rem;
}
```

Change `padding: 1.5rem 1rem;` → `padding: 1.25rem 1rem;` and `font-size: 2rem;` → `font-size: 1.5rem;`. The rest stays the same.

- [ ] **Step 2: Tighten deck rail minmax**

Find the existing rule (around line 5849):

```css
.sh-deck-rail {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 1rem;
}
```

Change `minmax(180px, 1fr)` → `minmax(170px, 1fr)`.

- [ ] **Step 3: Reduce deck-cover height**

Find the existing rule (around line 690):

```css
.sh-deck-cover {
    height: 120px;
    ...
}
```

Change `height: 120px;` → `height: 100px;`. Leave the rest of the rule untouched.

- [ ] **Step 4: Verify at 1440 × 900 and 1024 × 768**

Reload at both widths.
Expected:
- Action tiles are visibly shorter and the icons are smaller — less dominating.
- Deck covers are shorter (~20px less tall).
- Deck rail may fit one extra tile per row on the same width because of the 170px minmax.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/css/styles.css
git commit -m "style: tighten action tiles and deck tiles for denser dashboard"
```

---

## Task 6: Narrow-tier polish — edge-to-edge cards, denser deck rail

**Why:** Mobile cards still float in too much inner padding because the cockpit applies `1rem 0.75rem`. Also, the auto-fill rail with 170px min can produce awkward layouts at narrow widths.

**Files:**
- Modify: `src/main/resources/static/css/styles.css` — replace the existing narrow-tier block (around lines 5917–5945) and add new deck-rail rules.

- [ ] **Step 1: Verify the existing shell padding (NO change needed)**

Look at the existing block (line ~4453):

```css
.sh-dashboard-shell {
    padding: 0.75rem;
}
```

This is already inside the `@media (max-width: 768px)` block — it's correct as-is. Do NOT modify.

- [ ] **Step 2: Replace the narrow-tier cockpit block**

Find the existing block (around line 5917-5945):

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

Replace the entire block with:

```css
@media (max-width: 767px) {
    .sh-dashboard-cockpit {
        padding: 1rem 0;
        gap: 1.25rem;
    }

    .sh-cockpit-primary > * + *,
    .sh-cockpit-secondary > * + * {
        margin-top: 1.25rem;
    }

    .sh-deck-rail {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 0.75rem;
    }

    .sh-deck-cover {
        height: 90px;
    }
}

@media (max-width: 480px) {
    .sh-review-card {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.5rem;
    }

    .sh-review-card-cta {
        align-self: flex-end;
    }

    .sh-deck-rail {
        grid-template-columns: minmax(0, 1fr);
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

Key differences:
- New `@media (max-width: 767px)` block sets cockpit padding to `1rem 0` (no horizontal padding — shell already provides 0.75rem) and forces a 2-column deck rail. Sibling-margin rule adjusts the inter-section spacing to match the new gap.
- Existing `@media (max-width: 480px)` block adds a 1-column deck rail override.

- [ ] **Step 3: Verify at 480 × 800**

Reload at 480 × 800.
Expected:
- Cards (review card, deck tiles, activity rows) span from ~12px left to ~12px right — essentially edge-to-edge with just the shell's `0.75rem` padding.
- Deck rail is 2 columns.
- Action tile row is 3 columns at this width (1 column kicks in below 640px via the existing rule at styles.css:5888 — verify in Step 4).

- [ ] **Step 4: Verify at 375 × 812**

Reload at 375 × 812.
Expected:
- Same edge-to-edge cards.
- Action tile row is 1 column (existing rule at line 5888).
- Deck rail is 1 column.
- Deck covers shorter (90px) than wider screens.

- [ ] **Step 5: Verify medium and wide tiers are unaffected**

Reload at 1024 × 768 and 1440 × 900.
Expected:
- No change from Tasks 2–5. The new narrow rules don't fire at these widths.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/css/styles.css
git commit -m "feat: edge-to-edge dashboard cards and dense deck rail on narrow screens"
```

---

## Task 7: Soften the diagonal-line overlay in light mode

**Why:** With denser content the existing overlay competes with the cards. The spec asks for a small opacity tweak in light mode only.

**Files:**
- Modify: `src/main/resources/static/css/styles.css` — adjust the `.sh-dashboard-shell::before` rule at line ~459.

- [ ] **Step 1: Reduce opacity**

Find the existing rule (around line 453):

```css
.sh-dashboard-shell::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image:
        repeating-linear-gradient(135deg, rgba(15, 23, 42, 0.06) 0 1px, transparent 1px 24px);
    opacity: 0.35;
    pointer-events: none;
}
```

Change `opacity: 0.35;` → `opacity: 0.25;`.

The dark-mode override at line 469 already uses 0.25 and stays unchanged.

- [ ] **Step 2: Verify in light mode**

Reload at any viewport in light mode.
Expected: the diagonal-line pattern is still visible but slightly more recessed than before.

- [ ] **Step 3: Verify dark mode untouched**

Toggle dark mode (the theme switcher in the topnav).
Expected: dark mode looks identical to before (still at its own 0.25 opacity).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/styles.css
git commit -m "style: soften dashboard background overlay in light mode"
```

---

## Task 8: Regression checks

**Why:** No Java was touched, but we want to confirm the existing dashboard tests still pass and that no edge case in the templates broke.

**Files:**
- No edits. Verification only.

- [ ] **Step 1: Run dashboard-related JUnit tests**

Run: `./mvnw test -Dtest='DashboardControllerTests,DashboardServiceTests'`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Full visual sweep at every viewport**

Walk through every viewport from the pre-flight table:

| Viewport | Confirm |
|----------|---------|
| 1440 × 900 | Two-column cockpit, left border on secondary, vertical review card, scrollable activity if long, no horizontal scrollbar. |
| 1200 × 800 | Same as 1440 but tighter — both columns still readable. |
| 1024 × 768 | Single stretched column, no centered 960px block. |
| 900 × 800 | Single column, no horizontal scrollbar. |
| 480 × 800 | Cards near edge-to-edge, 2-col deck rail, 3-col action tiles, sidebar in drawer. |
| 375 × 812 | Cards near edge-to-edge, 1-col deck rail, 1-col action tiles, sidebar in drawer. |

- [ ] **Step 3: Edge cases**

Test these states explicitly:

- **No review mistakes:** the secondary column should still show recent activity (and resume card if applicable). On wide screens the secondary column should not be empty as long as there is activity.
- **No recent activity, no pinned decks, no recent decks (fresh account):** the secondary column should be empty on wide; the primary column should show greeting + action tiles only. Confirm there's no visual brokenness (e.g., border-left of secondary column hanging in space — this is acceptable; if it looks bad we can add a `:empty` selector later).
- **Active study session:** start a study session from the dashboard. The `.sh-explorer-shell:has(.sh-study-session-active)` rule (line 485) should still kick in, hiding the sidebar and centering content. Confirm nothing in the new cockpit rules breaks this.
- **HTMX swap of `#explorer-detail`:** click "Start studying" — the explorer-detail should swap and the new dashboard fragment should NOT be re-rendered until you navigate back. When you do navigate back, the new layout should appear correctly.

- [ ] **Step 4: Commit any fixes**

If you found and fixed regression issues during Step 2 or Step 3, commit them with a clear message. If nothing needed fixing, skip this step.

- [ ] **Step 5: Final status check**

Run: `git status`
Expected: clean working tree, all dashboard optimization commits present in `git log`.

Run: `git log --oneline -8`
Expected: visible commits from Tasks 1, 2, 3, 4, 5, 6, 7 (and optionally 8 if you fixed regressions).

---

## Done

The dashboard is now adaptive at every breakpoint. Spec acceptance criteria revisited:

1. ✅ Cockpit fills available horizontal space at every breakpoint (Task 2, Task 6).
2. ✅ Wide-screen layout adds a useful secondary column (Task 1, Task 2, Task 3, Task 4).
3. ✅ Mobile cards reach the viewport edges (Task 6).
4. ✅ iPad landscape (1024px) renders as a fully utilized single-column page (Task 2 medium tier).
5. ✅ Visual polish — denser tile/card sizing (Task 5, Task 7).
