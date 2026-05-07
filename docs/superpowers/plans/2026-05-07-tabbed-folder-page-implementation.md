# Tabbed Folder Page Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stacked folder page layout with a tabbed interface (Folders | Decks | Files) where tab switching is server-driven via HTMX.

**Architecture:** The existing `FolderController.viewFolder` endpoint accepts a new `?tab=` query param. The `FolderView` record gets an `activeTab` field. Thymeleaf templates are restructured so the folder header sits above a tab bar, and tab content panels are swapped via HTMX into a `#tab-content` target. Primary actions (Study, Edit, Delete) stay in the folder header; content-specific actions (Add Subfolder, New Deck, Upload) move into their respective tab headers.

**Tech Stack:** Spring Boot 4.0.6, Java 21, Thymeleaf, HTMX, JUnit 5, MockMvc, Lombok

---

### Task 1: Add ActiveTab enum and FolderView field

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/service/ActiveTab.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/FolderView.java`

- [ ] **Step 1: Create the ActiveTab enum**

Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/ActiveTab.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

public enum ActiveTab {
    FOLDERS,
    DECKS,
    FILES
}
```

- [ ] **Step 2: Add activeTab field to FolderView**

Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/FolderView.java`. Change the record from:

```java
public record FolderView(
    Folder folder,
    List<Folder> subFolders,
    List<Deck> decks,
    List<FileEntry> files,
    List<Folder> breadcrumb,
    int totalCardCount
) {}
```

To:

```java
public record FolderView(
    Folder folder,
    List<Folder> subFolders,
    List<Deck> decks,
    List<FileEntry> files,
    List<Folder> breadcrumb,
    int totalCardCount,
    ActiveTab activeTab
) {
    public FolderView(Folder folder, List<Folder> subFolders, List<Deck> decks,
                      List<FileEntry> files, List<Folder> breadcrumb, int totalCardCount) {
        this(folder, subFolders, decks, files, breadcrumb, totalCardCount, ActiveTab.FOLDERS);
    }
}
```

This adds a compact constructor so existing callers that don't pass `activeTab` get `FOLDERS` as default.

- [ ] **Step 3: Update FolderService to pass activeTab**

Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/FolderService.java`. Find the `getFolderView` method (around line 412) and add an overloaded version:

```java
public FolderView getFolderView(Long id, User user, String sortBy, String direction, ActiveTab activeTab) {
    FolderView base = getFolderView(id, user, sortBy, direction);
    return new FolderView(
        base.folder(), base.subFolders(), base.decks(), base.files(),
        base.breadcrumb(), base.totalCardCount(), activeTab
    );
}
```

Keep the existing `getFolderView(Long id, User user, String sortBy, String direction)` method unchanged — it will use the compact constructor defaulting to `FOLDERS`.

- [ ] **Step 4: Run tests to verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/ActiveTab.java src/main/java/com/HendrikHoemberg/StudyHelper/service/FolderView.java src/main/java/com/HendrikHoemberg/StudyHelper/service/FolderService.java
git commit -m "refactor: add ActiveTab enum and activeTab field to FolderView"
```

---

### Task 2: Update FolderController to accept tab param

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FolderController.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FolderControllerTests.java`

- [ ] **Step 1: Write failing tests for tab param handling**

Create `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FolderControllerTests.java`:

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.ActiveTab;
import com.HendrikHoemberg.StudyHelper.service.FolderService;
import com.HendrikHoemberg.StudyHelper.service.FolderView;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = FolderController.class)
class FolderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FolderService folderService;

    @MockitoBean
    private UserService userService;

    private User user;
    private Folder folder;
    private FolderView folderView;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        folder = new Folder();
        folder.setId(42L);
        folder.setName("Test Folder");
        folder.setUser(user);

        folderView = new FolderView(
            folder, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.singletonList(folder), 0, ActiveTab.FOLDERS
        );

        when(userService.getByUsername("alice")).thenReturn(user);
        when(folderService.getFolderView(eq(42L), eq(user), any(), any(), any(ActiveTab.class)))
            .thenReturn(folderView);
        when(folderService.getFolderView(eq(42L), eq(user), any(), any()))
            .thenReturn(new FolderView(
                folder, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.singletonList(folder), 0
            ));
    }

    @Test
    void viewFolder_DefaultTab_IsFolders() throws Exception {
        mockMvc.perform(get("/folders/42")
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("folder-page"))
            .andExpect(model().attribute("view", folderView));
    }

    @Test
    void viewFolder_WithTabParam_PassesActiveTab() throws Exception {
        FolderView decksView = new FolderView(
            folder, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.singletonList(folder), 0, ActiveTab.DECKS
        );
        when(folderService.getFolderView(eq(42L), eq(user), any(), any(), eq(ActiveTab.DECKS)))
            .thenReturn(decksView);

        mockMvc.perform(get("/folders/42")
                .param("tab", "decks")
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("folder-page"))
            .andExpect(model().attribute("view", decksView));
    }

    @Test
    void viewFolder_InvalidTabParam_DefaultsToFolders() throws Exception {
        mockMvc.perform(get("/folders/42")
                .param("tab", "invalid")
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("folder-page"))
            .andExpect(model().attribute("view", folderView));
    }

    @Test
    void viewFolder_HtmxRequest_ReturnsFolderDetailFragment() throws Exception {
        mockMvc.perform(get("/folders/42")
                .header("HX-Request", "true")
                .principal(() -> "alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("fragments/folder-detail :: folderDetail"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=FolderControllerTests -q`
Expected: FAIL — `viewFolder_WithTabParam_PassesActiveTab` fails because the controller doesn't accept `tab` param yet.

- [ ] **Step 3: Update FolderController.viewFolder to accept tab param**

Modify `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FolderController.java`. Change the `viewFolder` method (lines 108-130) from:

```java
@GetMapping("/folders/{id}")
public String viewFolder(@PathVariable Long id, 
                         @RequestParam(required = false) String sortBy,
                         @RequestParam(required = false, defaultValue = "asc") String direction,
                         Model model, Principal principal,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                         @RequestHeader(value = "HX-Target", required = false) String hxTarget) {
    User user = userService.getByUsername(principal.getName());
    FolderView view = folderService.getFolderView(id, user, sortBy, direction);
    model.addAttribute("view", view);
    model.addAttribute("username", principal.getName());
    model.addAttribute("sortBy", sortBy);
    model.addAttribute("direction", direction);

    if (hxRequest != null) {
        if ("files-table-container".equals(hxTarget)) {
            return "fragments/folder-detail :: filesTable";
        }
        model.addAttribute("refreshSidebar", true);
        return "fragments/folder-detail :: folderDetail";
    }
    return "folder-page";
}
```

To:

```java
@GetMapping("/folders/{id}")
public String viewFolder(@PathVariable Long id, 
                         @RequestParam(required = false) String sortBy,
                         @RequestParam(required = false, defaultValue = "asc") String direction,
                         @RequestParam(required = false) String tab,
                         Model model, Principal principal,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                         @RequestHeader(value = "HX-Target", required = false) String hxTarget) {
    User user = userService.getByUsername(principal.getName());
    ActiveTab activeTab = parseTab(tab);
    FolderView view = folderService.getFolderView(id, user, sortBy, direction, activeTab);
    model.addAttribute("view", view);
    model.addAttribute("username", principal.getName());
    model.addAttribute("sortBy", sortBy);
    model.addAttribute("direction", direction);

    if (hxRequest != null) {
        if ("files-table-container".equals(hxTarget)) {
            return "fragments/folder-detail :: filesTable";
        }
        model.addAttribute("refreshSidebar", true);
        return "fragments/folder-detail :: folderDetail";
    }
    return "folder-page";
}

private ActiveTab parseTab(String tab) {
    if (tab == null || tab.isBlank()) {
        return ActiveTab.FOLDERS;
    }
    try {
        return ActiveTab.valueOf(tab.toUpperCase());
    } catch (IllegalArgumentException e) {
        return ActiveTab.FOLDERS;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=FolderControllerTests -q`
Expected: BUILD SUCCESS, all 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/FolderController.java src/test/java/com/HendrikHoemberg/StudyHelper/controller/FolderControllerTests.java
git commit -m "feat: accept ?tab= param in FolderController, default to FOLDERS"
```

---

### Task 3: Create tab bar fragment

**Files:**
- Create: `src/main/resources/templates/fragments/tab-bar.html`

- [ ] **Step 1: Create the tab bar fragment**

Create `src/main/resources/templates/fragments/tab-bar.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>

<div th:fragment="tabBar" class="sh-tab-bar" role="tablist">
    <a th:classappend="${view.activeTab == T(com.HendrikHoemberg.StudyHelper.service.ActiveTab).FOLDERS} ? 'sh-tab-link is-active' : 'sh-tab-link'"
       th:href="@{/folders/{id}(id=${view.folder.id}, tab='folders')}"
       hx-get th:hx-get="@{/folders/{id}(id=${view.folder.id}, tab='folders')}"
       hx-target="#tab-content"
       hx-push-url="true"
       hx-swap="innerHTML"
       role="tab"
       th:aria-selected="${view.activeTab == T(com.HendrikHoemberg.StudyHelper.service.ActiveTab).FOLDERS}"
       aria-controls="tab-content">
        Folders <span class="sh-tab-badge" th:text="${view.subFolders.size()}">0</span>
    </a>
    <a th:classappend="${view.activeTab == T(com.HendrikHoemberg.StudyHelper.service.ActiveTab).DECKS} ? 'sh-tab-link is-active' : 'sh-tab-link'"
       th:href="@{/folders/{id}(id=${view.folder.id}, tab='decks')}"
       hx-get th:hx-get="@{/folders/{id}(id=${view.folder.id}, tab='decks')}"
       hx-target="#tab-content"
       hx-push-url="true"
       hx-swap="innerHTML"
       role="tab"
       th:aria-selected="${view.activeTab == T(com.HendrikHoemberg.StudyHelper.service.ActiveTab).DECKS}"
       aria-controls="tab-content">
        Decks <span class="sh-tab-badge" th:text="${view.decks.size()}">0</span>
    </a>
    <a th:classappend="${view.activeTab == T(com.HendrikHoemberg.StudyHelper.service.ActiveTab).FILES} ? 'sh-tab-link is-active' : 'sh-tab-link'"
       th:href="@{/folders/{id}(id=${view.folder.id}, tab='files')}"
       hx-get th:hx-get="@{/folders/{id}(id=${view.folder.id}, tab='files')}"
       hx-target="#tab-content"
       hx-push-url="true"
       hx-swap="innerHTML"
       role="tab"
       th:aria-selected="${view.activeTab == T(com.HendrikHoemberg.StudyHelper.service.ActiveTab).FILES}"
       aria-controls="tab-content">
        Files <span class="sh-tab-badge" th:text="${view.files.size()}">0</span>
    </a>
</div>

</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/fragments/tab-bar.html
git commit -m "feat: create tab-bar fragment with Folders/Decks/Files tabs"
```

---

### Task 4: Create tab content fragments

**Files:**
- Create: `src/main/resources/templates/fragments/tab-folders.html`
- Create: `src/main/resources/templates/fragments/tab-decks.html`
- Create: `src/main/resources/templates/fragments/tab-files.html`

- [ ] **Step 1: Create tab-folders.html**

Create `src/main/resources/templates/fragments/tab-folders.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>

<div th:fragment="tabContent" class="sh-tab-panel sh-fade-in">
    <div class="sh-tab-header">
        <h6 class="sh-tab-header-title">Subfolders</h6>
        <button class="sh-btn sh-btn-primary sh-btn-sm"
                th:hx-get="@{/folders/new(parentId=${view.folder.id})}"
                hx-target="#modal-placeholder">
            <iconify-icon icon="lucide:folder-plus"></iconify-icon>
            Add Subfolder
        </button>
    </div>

    <div th:if="${#lists.isEmpty(view.subFolders)}" class="sh-empty-state">
        <iconify-icon icon="lucide:folder-open" style="font-size:2rem;color:var(--text-muted);"></iconify-icon>
        <p class="sh-empty-text">No subfolders yet.</p>
        <button class="sh-btn sh-btn-secondary sh-btn-sm"
                th:hx-get="@{/folders/new(parentId=${view.folder.id})}"
                hx-target="#modal-placeholder">
            Create your first subfolder
        </button>
    </div>

    <div th:if="${!#lists.isEmpty(view.subFolders)}" class="sh-card-grid">
        <a th:each="sub : ${view.subFolders}"
           th:href="@{/folders/{id}(id=${sub.id})}"
           class="sh-d-pill"
           th:style="'--folder-color:' + ${sub.colorHex}"
           hx-get th:hx-get="@{/folders/{id}(id=${sub.id})}"
           hx-target="#explorer-detail" hx-push-url="true">
            <span class="sh-d-pill-cap sh-d-pill-cap-static">
                <iconify-icon th:attr="icon=${sub.iconName != null && sub.iconName.contains(':') ? sub.iconName : 'lucide:' + (sub.iconName ?: 'folder')}"></iconify-icon>
            </span>
            <span class="sh-d-pill-name">
                <span class="sh-d-pill-text" th:text="${sub.name}">Subfolder</span>
                <span class="sh-d-pill-count" th:text="${sub.decks.size()}">0</span>
            </span>
        </a>
    </div>
</div>

</body>
</html>
```

- [ ] **Step 2: Create tab-decks.html**

Create `src/main/resources/templates/fragments/tab-decks.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>

<div th:fragment="tabContent" class="sh-tab-panel sh-fade-in">
    <div class="sh-tab-header">
        <h6 class="sh-tab-header-title">Decks</h6>
        <button class="sh-btn sh-btn-primary sh-btn-sm"
                th:hx-get="@{/folders/{fid}/decks/new(fid=${view.folder.id})}"
                hx-target="#modal-placeholder">
            <iconify-icon icon="lucide:layers"></iconify-icon>
            New Deck
        </button>
    </div>

    <div th:if="${#lists.isEmpty(view.decks)}" class="sh-empty-state">
        <iconify-icon icon="lucide:layers" style="font-size:2rem;color:var(--text-muted);"></iconify-icon>
        <p class="sh-empty-text">No decks yet.</p>
        <button class="sh-btn sh-btn-secondary sh-btn-sm"
                th:hx-get="@{/folders/{fid}/decks/new(fid=${view.folder.id})}"
                hx-target="#modal-placeholder">
            Create your first deck
        </button>
    </div>

    <div th:if="${!#lists.isEmpty(view.decks)}" class="sh-card-grid">
        <a th:each="deck : ${view.decks}"
           th:href="@{/decks/{id}(id=${deck.id})}"
           class="sh-deck-card"
           hx-get th:hx-get="@{/decks/{id}(id=${deck.id})}"
           hx-target="#explorer-detail" hx-push-url="true">
            <div class="sh-deck-info">
                <div class="sh-deck-icon">
                    <iconify-icon icon="lucide:layers"></iconify-icon>
                </div>
                <div>
                    <div class="sh-deck-name" th:text="${deck.name}">Deck Name</div>
                    <div class="sh-folder-meta" th:text="${deck.flashcards.size()} + ' cards'">0 cards</div>
                </div>
            </div>
        </a>
    </div>
</div>

</body>
</html>
```

- [ ] **Step 3: Create tab-files.html**

Create `src/main/resources/templates/fragments/tab-files.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>

<div th:fragment="tabContent" class="sh-tab-panel sh-fade-in">
    <div class="sh-tab-header">
        <h6 class="sh-tab-header-title">Files</h6>
        <button class="sh-btn sh-btn-primary sh-btn-sm"
                th:hx-get="@{/folders/{fid}/files/upload(fid=${view.folder.id})}"
                hx-target="#modal-placeholder">
            <iconify-icon icon="lucide:upload"></iconify-icon>
            Upload File
        </button>
    </div>

    <div th:if="${#lists.isEmpty(view.files)}" class="sh-empty-state">
        <iconify-icon icon="lucide:file-up" style="font-size:2rem;color:var(--text-muted);"></iconify-icon>
        <p class="sh-empty-text">No files yet.</p>
        <button class="sh-btn sh-btn-secondary sh-btn-sm"
                th:hx-get="@{/folders/{fid}/files/upload(fid=${view.folder.id})}"
                hx-target="#modal-placeholder">
            Upload your first file
        </button>
    </div>

    <div th:if="${!#lists.isEmpty(view.files)}">
        <div id="files-table-container">
            <table th:fragment="filesTable" class="sh-file-table">
                <thead>
                    <tr>
                        <th hx-get th:hx-get="@{/folders/{id}(id=${view.folder.id}, sortBy='name', direction=${sortBy == 'name' && direction == 'asc' ? 'desc' : 'asc'}, tab='files')}"
                            hx-target="#files-table-container" hx-headers='{"HX-Request": "folder-files-table"}' class="cursor-pointer">
                            Name <span class="sort-indicator" th:text="${sortBy == 'name' ? (direction == 'asc' ? '▲' : '▼') : '▲'}">▲</span>
                        </th>
                        <th hx-get th:hx-get="@{/folders/{id}(id=${view.folder.id}, sortBy='type', direction=${sortBy == 'type' && direction == 'asc' ? 'desc' : 'asc'}, tab='files')}"
                            hx-target="#files-table-container" hx-headers='{"HX-Request": "folder-files-table"}' class="cursor-pointer">
                            Type <span class="sort-indicator" th:text="${sortBy == 'type' ? (direction == 'asc' ? '▲' : '▼') : '▲'}">▲</span>
                        </th>
                        <th hx-get th:hx-get="@{/folders/{id}(id=${view.folder.id}, sortBy='size', direction=${sortBy == 'size' && direction == 'asc' ? 'desc' : 'asc'}, tab='files')}"
                            hx-target="#files-table-container" hx-headers='{"HX-Request": "folder-files-table"}' class="cursor-pointer">
                            Size <span class="sort-indicator" th:text="${sortBy == 'size' ? (direction == 'asc' ? '▲' : '▼') : '▲'}">▲</span>
                        </th>
                        <th hx-get th:hx-get="@{/folders/{id}(id=${view.folder.id}, sortBy='date', direction=${sortBy == 'date' && direction == 'asc' ? 'desc' : 'asc'}, tab='files')}"
                            hx-target="#files-table-container" hx-headers='{"HX-Request": "folder-files-table"}' class="cursor-pointer">
                            Uploaded <span class="sort-indicator" th:text="${sortBy == 'date' ? (direction == 'asc' ? '▲' : '▼') : '▲'}">▲</span>
                        </th>
                        <th style="text-align:right;">Actions</th>
                    </tr>
                </thead>
                <tbody>
                    <tr th:each="file : ${view.files}">
                        <td class="sh-file-name" th:text="${file.originalFilename}">filename.pdf</td>
                        <td class="sh-file-meta" th:text="${file.mimeType}">application/pdf</td>
                        <td class="sh-file-meta" th:data-size-bytes="${file.fileSizeBytes}"
                            th:text="${#numbers.formatDecimal(file.fileSizeBytes / 1024.0, 1, 1) + ' KB'}">0 KB</td>
                        <td class="sh-file-meta" th:data-timestamp="${file.uploadedAt}"
                            th:text="${#temporals.format(file.uploadedAt, 'dd MMM yyyy HH:mm')}">01 Jan 2025</td>
                        <td style="text-align:right; white-space:nowrap;">
                            <a th:if="${#strings.endsWith(#strings.toLowerCase(file.originalFilename), '.pdf') || #strings.endsWith(#strings.toLowerCase(file.originalFilename), '.txt') || #strings.endsWith(#strings.toLowerCase(file.originalFilename), '.md')}"
                               class="sh-btn sh-btn-ghost sh-btn-sm"
                               style="color:var(--accent);"
                               th:href="@{'/study/start'(mode='QUIZ', fileId=${file.id})}"
                               hx-get="/study/start" th:hx-vals="${'{&quot;fileId&quot;: ' + file.id + '}'}"
                               hx-target="#explorer-detail" hx-push-url="true">
                                <iconify-icon icon="lucide:sparkles"></iconify-icon>
                                Quiz
                            </a>
                            <button class="sh-btn sh-btn-ghost sh-btn-sm"
                                    th:hx-get="@{/files/{id}/edit-options(id=${file.id})}"
                                    hx-target="#modal-placeholder">
                                <iconify-icon icon="lucide:pencil"></iconify-icon>
                                Edit
                            </button>
                            <a th:href="@{/files/{id}/view(id=${file.id})}" target="_blank"
                               th:classappend="${#strings.startsWith(file.mimeType, 'image/')} ? 'sh-lightbox-trigger' : ''"
                               class="sh-btn sh-btn-ghost sh-btn-sm">View</a>
                            <a th:href="@{/files/{id}/download(id=${file.id})}"
                               class="sh-btn sh-btn-ghost sh-btn-sm">Download</a>
                            <button class="sh-btn sh-btn-ghost sh-btn-sm"
                                    style="color:var(--danger);"
                                    th:hx-post="@{/files/{id}/delete(id=${file.id})}"
                                    hx-confirm="Delete this file?"
                                    hx-target="#explorer-detail">Delete</button>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

</body>
</html>
```

Note: The sort header links in tab-files.html include `tab='files'` to preserve the active tab when sorting.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/tab-folders.html src/main/resources/templates/fragments/tab-decks.html src/main/resources/templates/fragments/tab-files.html
git commit -m "feat: create tab content fragments for folders, decks, and files"
```

---

### Task 5: Restructure folder-detail.html with tab bar and tab content

**Files:**
- Modify: `src/main/resources/templates/fragments/folder-detail.html`

- [ ] **Step 1: Rewrite folder-detail.html with tab structure**

Replace the entire content of `src/main/resources/templates/fragments/folder-detail.html` with:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>

<!-- Fragment: folder detail view (swapped into #explorer-detail) -->
<div th:fragment="folderDetail" class="sh-fade-in">

    <!-- Sidebar OOB refresh (rendered when refreshSidebar flag is set on mutation responses) -->
    <th:block th:if="${refreshSidebar}">
        <aside th:replace="~{fragments/sidebar :: sidebar}"></aside>
    </th:block>

    <!-- Alert for errors -->
    <div th:if="${error}" class="sh-alert sh-alert-danger">
        <div class="sh-alert-body" th:text="${error}"></div>
        <button type="button" class="sh-alert-close" hx-on:click="this.closest('.sh-alert').remove()">
            <iconify-icon icon="lucide:x"></iconify-icon>
        </button>
    </div>

    <!-- Breadcrumb -->
    <ul class="sh-breadcrumb">
        <li>
            <a href="/dashboard" hx-get="/dashboard" hx-target="#explorer-detail"
               hx-push-url="true">Dashboard</a>
        </li>
        <th:block th:each="crumb, stat : ${view.breadcrumb}">
            <li class="sh-breadcrumb-sep">/</li>
            <li th:if="!${stat.last}">
                <a th:href="@{/folders/{id}(id=${crumb.id})}"
                   hx-get th:hx-get="@{/folders/{id}(id=${crumb.id})}"
                   hx-target="#explorer-detail" hx-push-url="true"
                   th:text="${crumb.name}">Crumb</a>
            </li>
            <li th:if="${stat.last}" class="sh-breadcrumb-current" th:text="${crumb.name}">Current</li>
        </th:block>
    </ul>

    <!-- Folder header (primary actions only) -->
    <div class="sh-page-header">
        <div class="flex items-center gap-3">
            <div class="sh-folder-card-icon" th:style="'--folder-color:' + ${view.folder.colorHex}">
                <iconify-icon th:attr="icon=${view.folder.iconName != null && view.folder.iconName.contains(':') ? view.folder.iconName : 'lucide:' + (view.folder.iconName ?: 'folder')}"></iconify-icon>
            </div>
            <div>
                <h1 class="sh-page-title" th:text="${view.folder.name}">Folder Name</h1>
                <span class="sh-badge sh-badge-muted mt-1"
                      th:text="${view.totalCardCount} + ' cards total'"></span>
            </div>
        </div>
        <div class="sh-page-actions">
            <a th:if="${view.totalCardCount > 0}"
               class="sh-btn sh-btn-primary sh-btn-sm"
               th:href="@{'/study/start'(folderId=${view.folder.id})}"
               hx-get="/study/start" th:hx-vals="${'{&quot;folderId&quot;: ' + view.folder.id + '}'}"
               hx-target="#explorer-detail" hx-push-url="true">
                <iconify-icon icon="lucide:play"></iconify-icon>
                Study
            </a>
            <button class="sh-btn sh-btn-secondary sh-btn-sm"
                    th:hx-get="@{/folders/{id}/edit(id=${view.folder.id})}" hx-target="#modal-placeholder">
                <iconify-icon icon="lucide:pencil"></iconify-icon>
                Edit
            </button>
            <button class="sh-btn sh-btn-danger sh-btn-sm"
                    th:hx-post="@{/folders/{id}/delete(id=${view.folder.id})}"
                    hx-confirm="Delete this folder and all its contents? This action cannot be undone."
                    hx-target="#explorer-detail"
                    hx-push-url="true">
                <iconify-icon icon="lucide:trash-2"></iconify-icon>
                Delete
            </button>
        </div>
    </div>

    <!-- Tab bar -->
    <div th:replace="~{fragments/tab-bar :: tabBar}"></div>

    <!-- Tab content (HTMX target) -->
    <div id="tab-content" class="sh-tab-content-area">
        <div th:if="${view.activeTab == T(com.HendrikHoemberg.StudyHelper.service.ActiveTab).FOLDERS}"
             th:replace="~{fragments/tab-folders :: tabContent}"></div>
        <div th:if="${view.activeTab == T(com.HendrikHoemberg.StudyHelper.service.ActiveTab).DECKS}"
             th:replace="~{fragments/tab-decks :: tabContent}"></div>
        <div th:if="${view.activeTab == T(com.HendrikHoemberg.StudyHelper.service.ActiveTab).FILES}"
             th:replace="~{fragments/tab-files :: tabContent}"></div>
    </div>

</div>

</body>
</html>
```

Key changes from the original:
- Removed the standalone subfolders section (now in tab-folders)
- Removed the standalone decks section (now in tab-decks)
- Removed the standalone files card (now in tab-files)
- Removed Subfolder, Deck, and Upload buttons from the folder header (moved to tab headers)
- Added tab bar include
- Added `#tab-content` div that conditionally renders the active tab's fragment

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/fragments/folder-detail.html
git commit -m "refactor: restructure folder-detail with tab bar and tab content area"
```

---

### Task 6: Add CSS styles for tabs

**Files:**
- Modify: `src/main/resources/static/css/styles.css`

- [ ] **Step 1: Add tab CSS to styles.css**

Append to `src/main/resources/static/css/styles.css` (after the last existing rule):

```css
/* ============================================================
   TAB BAR
   ============================================================ */

.sh-tab-bar {
    display: flex;
    align-items: center;
    gap: 0;
    border-bottom: 2px solid var(--border);
    margin-bottom: 0;
}

.sh-tab-link {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    padding: 0.75rem 1.25rem;
    font-size: 0.875rem;
    font-weight: 500;
    color: var(--text-muted);
    text-decoration: none;
    border-bottom: 2px solid transparent;
    margin-bottom: -2px;
    transition: all var(--transition-fast);
    cursor: pointer;
}

.sh-tab-link:hover {
    color: var(--text);
    background: var(--surface-hover);
}

.sh-tab-link.is-active {
    color: var(--accent);
    border-bottom-color: var(--accent);
    font-weight: 600;
}

.sh-tab-badge {
    font-size: 0.6875rem;
    font-weight: 600;
    color: var(--text-muted);
    background: var(--border-light);
    padding: 0.1rem 0.4rem;
    border-radius: 999px;
    line-height: 1.3;
}

.sh-tab-link.is-active .sh-tab-badge {
    background: var(--accent-light);
    color: var(--accent);
}

/* ============================================================
   TAB PANEL
   ============================================================ */

.sh-tab-content-area {
    background: var(--surface);
    border: 1px solid var(--border);
    border-top: none;
    border-radius: 0 0 var(--radius-lg) var(--radius-lg);
    min-height: 200px;
}

.sh-tab-panel {
    padding: 1.25rem;
}

.sh-tab-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 1rem;
}

.sh-tab-header-title {
    font-family: var(--font-heading);
    font-size: 0.625rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--text-muted);
    margin: 0;
}

/* ============================================================
   EMPTY STATE
   ============================================================ */

.sh-empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.75rem;
    padding: 2.5rem 1rem;
    text-align: center;
}

.sh-empty-state iconify-icon {
    opacity: 0.5;
}

.sh-empty-text {
    font-size: 0.875rem;
    color: var(--text-muted);
    margin: 0;
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/css/styles.css
git commit -m "feat: add tab bar, tab panel, and empty state CSS styles"
```

---

### Task 7: Run full test suite and verify

**Files:** (no changes — verification only)

- [ ] **Step 1: Run all tests**

Run: `./mvnw test -q`
Expected: BUILD SUCCESS, all tests pass including new `FolderControllerTests`.

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit final state**

```bash
git status
# Verify no unintended changes
```

---

## Self-Review

**Spec coverage check:**
- ✅ Tab bar with Folders/Decks/Files tabs → Task 3 (tab-bar.html) + Task 6 (CSS)
- ✅ HTMX-driven tab switching → Task 2 (controller tab param) + Task 3 (hx-get on tab links) + Task 5 (folder-detail restructuring)
- ✅ Folder header with primary actions only (Study, Edit, Delete) → Task 5 (folder-detail.html)
- ✅ Content-specific actions in tab headers → Task 4 (tab-folders, tab-decks, tab-files)
- ✅ Folders tab shows subfolder cards + add button → Task 4 (tab-folders.html)
- ✅ Decks tab shows deck cards + new deck button → Task 4 (tab-decks.html)
- ✅ Files tab shows file table + upload button → Task 4 (tab-files.html)
- ✅ Default tab is Folders → Task 1 (compact constructor) + Task 2 (parseTab default)
- ✅ Invalid tab defaults to Folders → Task 2 (parseTab catch block)
- ✅ URL updates via hx-push-url → Task 3 (tab links have hx-push-url)
- ✅ Sort headers in files tab preserve tab state → Task 4 (tab-files.html sort links include tab='files')
- ✅ Accessibility (role="tablist", role="tab", aria-selected, aria-controls) → Task 3 (tab-bar.html)
- ✅ Badge counts on tab labels → Task 3 (sh-tab-badge spans)

**Placeholder scan:** No TBD, TODO, or vague instructions found. All code blocks are complete.

**Type consistency:** `ActiveTab` enum values (FOLDERS, DECKS, FILES) are used consistently across FolderView, FolderController, and Thymeleaf templates. The `T(...)` Thymeleaf syntax for enum comparison matches the existing pattern in the codebase.
