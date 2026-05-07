# Design Doc: Integrated Tab Bar Styling and Fix

## Problem
1. **Visual:** The `sh-tab-bar` is currently transparent and feels disconnected from the `sh-tab-content-area`.
2. **Functional:** Switching tabs via HTMX only updates the `#tab-content` area, leaving the `.is-active` class on the tab bar stale unless the entire page is reloaded.

## Proposed Solution
Integrate the tab bar into the same visual unit as the content area and use a single HTMX target to keep them in sync.

### 1. Visual Integration (Styling)
- Update `.sh-tab-bar` to have a solid background (`var(--surface)`), border, and rounded top corners.
- Inactive tabs will have a subtle background (`var(--surface-hover)`) to differentiate from the active tab.
- The active tab will share the white background of the content area and have a colored indicator.

### 2. Functional Fix (HTMX)
- Wrap the tab bar and content area in a new container `#folder-tabs-section` in `folder-detail.html`.
- Update `tab-bar.html` to target `#folder-tabs-section`.
- Update `FolderController` to return a combined fragment (tab bar + content) when `#folder-tabs-section` is targeted.

## Technical Details

### CSS Changes (`styles.css`)
- `.sh-tab-bar`: Solid background, border, rounded top.
- `.sh-tab-link`: Update padding/borders to fit the integrated look.
- `.sh-tab-content-area`: Remove `border-top` (as it's provided by the tab bar interaction).

### HTML Changes
- **`folder-detail.html`**:
  ```html
  <div id="folder-tabs-section" class="mt-6">
      <div th:replace="~{fragments/tab-bar :: tabBar}"></div>
      <div id="tab-content" class="sh-tab-content-area" role="tabpanel">
          <th:block th:replace="~{fragments/tab-__${view.activeTab.name().toLowerCase()}__ :: tabContent}"></th:block>
      </div>
  </div>
  ```

### Controller Changes (`FolderController.java`)
- Update `viewFolder` to check for `hxTarget.equals("folder-tabs-section")`.
- If matched, return a new fragment or handle it within `folder-detail.html`.
- *Recommendation:* Create a small fragment in `folder-detail.html` called `tabsSection`.

## Verification Plan
1. **Visual:** Check that the tab bar has rounded corners and integrated background.
2. **Functional:** Click tabs and verify that the `.is-active` class updates immediately on the tab bar.
3. **Regression:** Ensure breadcrumbs and folder headers still work correctly.
