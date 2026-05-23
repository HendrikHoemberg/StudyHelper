# Mobile Bottom Bar Navigation Design

Design specification for replacing the traditional mobile top navigation and hamburger drawer with a modern, ergonomic mobile bottom navigation bar and a glassmorphism bottom sheet drawer in StudyHelper.

## Overview & Goal

The current mobile navigation utilizes a traditional top-aligned navigation bar containing a folder drawer toggle, logo/branding, and a hamburger menu. Tapping the hamburger menu slides out a sidebar containing links, user quotas, profile details, theme switcher, and logout.

While functional, this top-heavy layout has poor ergonomics on modern tall mobile devices. The goal is to:
1. **Maximize content space**: Completely hide the top navigation bar on mobile screen sizes ($w \le 768\text{px}$) for an immersive, native-app feel.
2. **Improve thumb reachability**: Place core destination links at the bottom within comfortable reach.
3. **Enhance visual design**: Implement an elegant, glassmorphic bottom sheet that slides up from the bottom to house secondary actions and user metrics (quotas, theme toggling, logout).

---

## Visual Presentation

Below is the design mockup representing the modern mobile bottom bar navigation and the smooth glassmorphism sliding sheet.

![Mobile Navigation Mockup](file:///home/hendrik/.gemini/antigravity-cli/brain/1815bf42-8bf0-4972-bce3-3fed858cee55/mobile_bottom_bar_mockup_1779522347359.png)

---

## Detailed Components

### 1. Top Navbar Responsive Modifications
* On screens $> 768\text{px}$, the top navigation bar (`.app-topnav`) behaves exactly as it does today.
* On screens $\le 768\text{px}$, `.app-topnav` is hidden completely (`display: none;`). This eliminates the burger toggle, brand header, and folders toggle from the top of the viewport.
* Scroll margins and main viewport layout offsets (like `padding-top: 52px` on the body or explorer wrapper) will be modified via mobile media queries to reclaim the top 52px screen height.

### 2. Bottom Navigation Bar (`.app-bottom-bar`)
A new sticky navigation bar placed at the bottom viewport boundary. It is only visible on mobile screen sizes ($w \le 768\text{px}$).

#### HTML Structure (to be added inside `layout.html`)
```html
<nav class="app-bottom-bar">
    <div class="bottom-bar-inner">
        <!-- Dashboard Tab -->
        <a href="/dashboard" class="bottom-tab"
           hx-get="/dashboard" hx-target="#explorer-detail" hx-push-url="true">
            <iconify-icon icon="lucide:layout-dashboard"></iconify-icon>
            <span class="bottom-tab-label">Dashboard</span>
        </a>

        <!-- Study Session Tab -->
        <a href="/study/start" class="bottom-tab"
           hx-get="/study/start" hx-target="#explorer-detail" hx-push-url="true">
            <iconify-icon icon="lucide:book-open"></iconify-icon>
            <span class="bottom-tab-label">Study</span>
        </a>

        <!-- AI Assistant Tab -->
        <a href="/flashcards/generate" class="bottom-tab"
           hx-get="/flashcards/generate" hx-target="#explorer-detail" hx-push-url="true">
            <iconify-icon icon="lucide:sparkles"></iconify-icon>
            <span class="bottom-tab-label">AI</span>
        </a>

        <!-- Folders Drawer Toggle -->
        <button id="bottom-folders-btn" class="bottom-tab-btn" type="button">
            <iconify-icon icon="lucide:folder"></iconify-icon>
            <span class="bottom-tab-label">Folders</span>
        </button>

        <!-- "More" Bottom Sheet Toggle -->
        <button id="bottom-more-btn" class="bottom-tab-btn" type="button">
            <iconify-icon icon="lucide:more-horizontal"></iconify-icon>
            <span class="bottom-tab-label">More</span>
        </button>
    </div>
</nav>
```

### 3. Glassmorphism Bottom Sheet (`#mobile-more-sheet`)
A sliding modal drawer container positioned off-screen below the viewport. It will animate up when the user clicks the "More" tab.

#### HTML Structure (inside `layout.html`, placed next to `scripts` or bottom drawer fragments)
```html
<!-- Bottom Sheet Container -->
<div id="mobile-more-sheet" class="sh-bottom-sheet">
    <!-- Touch drag indicator bar for aesthetics -->
    <div class="sh-bottom-sheet-handle"></div>
    
    <div class="sh-bottom-sheet-content">
        <!-- Quotas Section -->
        <div class="sh-sheet-section" th:if="${userQuota != null}">
            <div class="sh-sheet-section-title">Your Limits</div>
            <div class="sh-sheet-quota">
                <div class="sh-quota-label">
                    <span>Storage</span>
                    <strong th:text="${userQuota.storageLabel()}">512 MB / 1.0 GB</strong>
                </div>
                <div class="sh-quota-progress">
                    <div class="sh-quota-bar"
                         th:classappend="${userQuota.storageStateClass()}"
                         th:style="'width:' + ${userQuota.storagePercent()} + '%'"></div>
                </div>
            </div>
            <div class="sh-sheet-quota">
                <div class="sh-quota-label">
                    <span>AI Requests</span>
                    <strong th:text="${userQuota.aiLabel()}">7 / 10</strong>
                </div>
                <div class="sh-quota-progress">
                    <div class="sh-quota-bar"
                         th:classappend="${userQuota.aiStateClass()}"
                         th:style="'width:' + ${userQuota.aiPercent()} + '%'"></div>
                </div>
            </div>
        </div>

        <div class="sh-sheet-divider" th:if="${userQuota != null}"></div>

        <!-- Controls Section (Profile, Theme, Admin, Logout) -->
        <div class="sh-sheet-section">
            <div class="sh-sheet-user-pill">
                <iconify-icon icon="lucide:user"></iconify-icon>
                <span class="sh-sheet-username" th:text="${username}">User</span>
            </div>

            <a th:if="${isAdmin}" href="/admin" class="sh-sheet-row-link">
                <iconify-icon icon="lucide:shield"></iconify-icon>
                <span>Admin Panel</span>
                <iconify-icon icon="lucide:chevron-right" class="sh-sheet-arrow"></iconify-icon>
            </a>

            <!-- Quick Theme Switcher Row -->
            <div class="sh-sheet-row-toggle" onclick="toggleTheme()">
                <div class="sh-toggle-left">
                    <iconify-icon icon="lucide:sun" class="theme-icon-light"></iconify-icon>
                    <iconify-icon icon="lucide:moon" class="theme-icon-dark"></iconify-icon>
                    <span>App Theme</span>
                </div>
                <div class="sh-toggle-switch">
                    <span class="sh-toggle-switch-handle"></span>
                </div>
            </div>

            <!-- Sign Out Form -->
            <form th:action="@{/logout}" method="post" class="sh-sheet-logout-form">
                <button type="submit" class="sh-btn sh-btn-danger sh-sheet-logout-btn">
                    <iconify-icon icon="lucide:log-out"></iconify-icon>
                    Sign out
                </button>
            </form>
        </div>
    </div>
</div>

<!-- Backdrop Overlay for Bottom Sheet -->
<div id="sh-bottom-sheet-backdrop" class="sh-bottom-sheet-backdrop"></div>
```

---

## CSS Styling Spec (`styles.css`)

We will use rich aesthetics, dynamic gradients, and backdrop-blur styling to make the sheet look high-end:

```css
/* ---------- Mobile Bottom Bar & Bottom Sheet styles ---------- */
@media (max-width: 768px) {
    /* Hide top nav */
    .app-topnav {
        display: none !important;
    }

    /* Shift body/shell layout up since top nav is hidden */
    .sh-explorer-shell {
        margin-top: 0 !important;
        padding-top: 0 !important;
        height: calc(100vh - 56px) !important; /* reserve bottom space */
    }
    
    body {
        padding-top: 0 !important;
        padding-bottom: 56px !important;
    }

    /* App Bottom Bar */
    .app-bottom-bar {
        position: fixed;
        bottom: 0;
        left: 0;
        right: 0;
        height: 56px;
        background: var(--surface);
        border-top: 1px solid var(--border-light);
        z-index: 999;
        display: block;
        box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.06);
    }

    .bottom-bar-inner {
        display: flex;
        justify-content: space-around;
        align-items: center;
        height: 100%;
        padding: 0 0.5rem;
    }

    .bottom-tab, .bottom-tab-btn {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 0.125rem;
        background: none;
        border: none;
        color: var(--text-secondary);
        text-decoration: none;
        font-size: 0.6875rem;
        font-weight: 500;
        cursor: pointer;
        padding: 0.25rem 0.5rem;
        width: 20%;
        height: 100%;
        transition: color 0.2s ease;
        -webkit-tap-highlight-color: transparent;
    }

    .bottom-tab iconify-icon, .bottom-tab-btn iconify-icon {
        font-size: 1.25rem;
        transition: transform 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    }

    .bottom-tab:hover, .bottom-tab-btn:hover {
        color: var(--accent);
    }

    .bottom-tab.active, .bottom-tab-btn.active {
        color: var(--accent);
        font-weight: 600;
    }

    .bottom-tab.active iconify-icon, .bottom-tab-btn.active iconify-icon {
        transform: scale(1.15);
    }

    /* Bottom Sheet Drawer */
    .sh-bottom-sheet {
        position: fixed;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(var(--surface-rgb, 255, 255, 255), 0.8);
        backdrop-filter: blur(20px) saturate(180%);
        -webkit-backdrop-filter: blur(20px) saturate(180%);
        border-top: 1px solid var(--border-light);
        border-top-left-radius: var(--radius-lg, 16px);
        border-top-right-radius: var(--radius-lg, 16px);
        z-index: 1001;
        transform: translateY(100%);
        transition: transform 0.35s cubic-bezier(0.32, 0.94, 0.6, 1);
        box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.12);
        max-height: 85vh;
        overflow-y: auto;
        padding: 1.25rem;
    }

    .sh-bottom-sheet.is-open {
        transform: translateY(0);
    }

    .sh-bottom-sheet-handle {
        width: 36px;
        height: 4px;
        background: var(--border);
        border-radius: 99px;
        margin: -0.5rem auto 1rem auto;
        opacity: 0.6;
    }

    /* Bottom Sheet Backdrop */
    .sh-bottom-sheet-backdrop {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0, 0, 0, 0.4);
        backdrop-filter: blur(2px);
        opacity: 0;
        visibility: hidden;
        z-index: 1000;
        transition: opacity 0.3s ease, visibility 0.3s ease;
    }

    .sh-bottom-sheet-backdrop.is-open {
        opacity: 1;
        visibility: visible;
    }

    /* Controls & Rows inside sheet */
    .sh-sheet-section-title {
        font-size: 0.75rem;
        text-transform: uppercase;
        font-weight: 700;
        color: var(--text-muted, #737373);
        margin-bottom: 0.75rem;
        letter-spacing: 0.05em;
    }

    .sh-sheet-quota {
        margin-bottom: 0.75rem;
    }

    .sh-sheet-divider {
        height: 1px;
        background: var(--border-light);
        margin: 1rem 0;
    }

    .sh-sheet-user-pill {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        background: var(--bg-hover);
        padding: 0.375rem 0.75rem;
        border-radius: var(--radius-full, 99px);
        font-size: 0.8125rem;
        font-weight: 600;
        margin-bottom: 1rem;
    }

    .sh-sheet-row-link, .sh-sheet-row-toggle {
        display: flex;
        align-items: center;
        padding: 0.75rem;
        background: var(--bg-hover);
        border-radius: var(--radius-md, 8px);
        font-size: 0.875rem;
        color: var(--text);
        text-decoration: none;
        margin-bottom: 0.5rem;
        cursor: pointer;
        transition: background 0.2s ease;
    }

    .sh-sheet-row-link iconify-icon, .sh-sheet-row-toggle iconify-icon {
        font-size: 1.15rem;
        margin-right: 0.75rem;
    }

    .sh-sheet-arrow {
        margin-left: auto;
        color: var(--text-muted);
    }

    /* Toggle switch styled matching studyhelper theme */
    .sh-toggle-switch {
        margin-left: auto;
        width: 38px;
        height: 20px;
        background: var(--border);
        border-radius: 99px;
        position: relative;
        transition: background 0.25s ease;
    }

    .sh-toggle-switch-handle {
        position: absolute;
        top: 2px;
        left: 2px;
        width: 16px;
        height: 16px;
        background: #fff;
        border-radius: 50%;
        transition: transform 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    }

    [data-theme="dark"] .sh-toggle-switch {
        background: var(--accent);
    }

    [data-theme="dark"] .sh-toggle-switch-handle {
        transform: translateX(18px);
    }

    .sh-sheet-logout-form {
        margin-top: 1rem;
    }

    .sh-sheet-logout-btn {
        width: 100%;
        justify-content: center;
        padding: 0.75rem !important;
        border-radius: var(--radius-md, 8px) !important;
        font-size: 0.875rem !important;
        font-weight: 600 !important;
    }
}

@media (min-width: 769px) {
    .app-bottom-bar, .sh-bottom-sheet, .sh-bottom-sheet-backdrop {
        display: none !important;
    }
}
```

---

## JavaScript Interactive Interactions (`app.js`)

We will introduce coordinates in `app.js` to initialize, open, and close the mobile drawers smoothly.

### 1. Toggle Controllers
We will add `initBottomSheet()` inside `app.js` and call it on `DOMContentLoaded`.

```javascript
function initBottomSheet() {
    const moreBtn = document.getElementById('bottom-more-btn');
    const folderBtn = document.getElementById('bottom-folders-btn');
    const sheet = document.getElementById('mobile-more-sheet');
    const backdrop = document.getElementById('sh-bottom-sheet-backdrop');
    
    if (!sheet || !backdrop) return;

    const openSheet = () => {
        // Automatically close Folders sidebar drawer to prevent double overlay
        const sidebar = document.getElementById('sidebar-aside');
        const shBackdrop = document.getElementById('sh-sidebar-backdrop');
        if (sidebar) sidebar.classList.remove('is-open');
        if (shBackdrop) shBackdrop.classList.remove('is-open');
        
        sheet.classList.add('is-open');
        backdrop.classList.add('is-open');
        moreBtn?.classList.add('active');
    };

    const closeSheet = () => {
        sheet.classList.remove('is-open');
        backdrop.classList.remove('is-open');
        moreBtn?.classList.remove('active');
    };

    moreBtn?.addEventListener('click', (e) => {
        e.preventDefault();
        if (sheet.classList.contains('is-open')) closeSheet();
        else openSheet();
    });

    // Close on backdrop click
    backdrop.addEventListener('click', closeSheet);

    // Coordinate with Folders button to close "More" sheet
    folderBtn?.addEventListener('click', (e) => {
        closeSheet();
        // Trigger the standard sidebar drawer opening behavior
        const topnavFoldersBtn = document.getElementById('topnav-folders-btn');
        if (topnavFoldersBtn) {
            topnavFoldersBtn.click();
        }
    });

    // Drag handle closure support
    sheet.querySelector('.sh-bottom-sheet-handle')?.addEventListener('click', closeSheet);

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeSheet();
    });
}
```

### 2. Live Tab Routing Sync
Modify `updateActiveNavLink()` in `app.js` to correctly assign the `.active` class to the bottom tabs:

```javascript
// Inside updateActiveNavLink()
const bottomLinks = document.querySelectorAll('.bottom-tab');
updateLinks(bottomLinks);
```

### 3. HTMX State Resets
Add sheet closure handlers to the `htmx:afterSwap` event listener inside `app.js` so that when a user triggers any page change (like navigating to AI flashcards or starting a study session), the bottom sheet closes instantly.

---

## Verification Plan

### Manual Verification Path
1. **Desktop view**: Open application in a desktop viewport ($> 768\text{px}$). Verify that the top navbar remains untouched and works perfectly, with no bottom bar appearing.
2. **Mobile Viewport Simulation**: Shrink viewport to $\le 768\text{px}$ (or load on a mobile device).
   * Verify top navigation is completely hidden (brand header, quotas, settings, toggle folder).
   * Verify the elegant bottom bar appears anchored at the bottom edge.
3. **Ergonomic Drawer Interactions**:
   * Tap "**Folders**" icon $\rightarrow$ slides out the directory tree.
   * Tap "**More**" icon $\rightarrow$ slides up the frosted glass sheet.
   * Verify circular quota bounds look beautiful.
   * Click **Theme Switcher** row $\rightarrow$ instantly toggles app theme (light/dark) while keeping sheet open.
   * Tap **Sign Out** $\rightarrow$ verifies session terminates correctly.
4. **Coordination validation**: Open "**Folders**" drawer, then tap "**More**". Folders drawer should close immediately as bottom sheet opens.
