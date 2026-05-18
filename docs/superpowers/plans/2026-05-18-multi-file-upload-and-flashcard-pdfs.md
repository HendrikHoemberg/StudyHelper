# Multi-File Upload and Multi-PDF Flashcard Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users upload multiple files at once and generate one combined flashcard deck from multiple selected PDFs.

**Architecture:** Keep existing single-file APIs as compatibility wrappers, then add list-based service/controller flows. The flashcard generator submits repeated `fileId` values and sends one list of `DocumentInput` objects to the AI service. Shared `.sh-checkbox` styling becomes custom-rendered so flashcard PDF checkboxes and existing source-picker checkboxes are coherent across browsers.

**Tech Stack:** Java 21, Spring Boot MVC, Thymeleaf, HTMX, vanilla JavaScript, JUnit 5, Mockito, AssertJ, Maven wrapper.

---

## File Map

- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/FileEntryService.java`: add `uploadAll`, shared upload helper, combined quota check, stored-file cleanup on failure.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FileController.java`: accept repeated multipart `file` fields and delegate to `uploadAll`.
- Modify `src/test/java/com/HendrikHoemberg/StudyHelper/service/FileEntryServiceTests.java`: cover multi-upload success and empty selection.
- Modify `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FileControllerTests.java`: cover multipart request with multiple `file` fields.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java`: add list-based `generate` overload and keep existing overloads delegating to it.
- Modify `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java`: cover multiple PDF attachments in one request.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationController.java`: accept `List<Long> fileId`, validate/build multiple documents, preserve selected IDs on errors, produce multi-source success message.
- Modify `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationControllerTests.java`: cover multiple selected PDFs in Text and Full PDF modes plus missing selection.
- Modify `src/main/resources/templates/fragments/file-form.html`: allow multiple file selection and display count/list.
- Modify `src/main/resources/templates/fragments/flashcard-generator.html`: change PDF radios to `.sh-checkbox` checkboxes and move document mode to one global field.
- Modify `src/main/resources/static/js/app.js`: update flashcard generator selection, prefill, folder selection, and size warning behavior for multiple selected PDFs.
- Modify `src/main/resources/static/css/styles.css`: make `.sh-checkbox` custom-rendered and add any generator-specific spacing needed.

---

### Task 1: Multi-File Upload Service

**Files:**
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/FileEntryServiceTests.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/FileEntryService.java`

- [ ] **Step 1: Write failing service tests**

Add these imports to `FileEntryServiceTests.java`:

```java
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
```

Add these tests to `FileEntryServiceTests`:

```java
@Test
void uploadAll_StoresEachFileAndChecksQuotaWithCombinedBytes() throws IOException {
    User user = new User();
    user.setUsername("alice");
    Folder folder = new Folder();
    folder.setId(1L);

    MultipartFile first = new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[250]);
    MultipartFile second = new MockMultipartFile("file", "diagram.png", "image/png", new byte[150]);

    when(folderRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(folder));
    when(uploadValidator.validateUpload(first)).thenReturn("application/pdf");
    when(uploadValidator.validateUpload(second)).thenReturn("image/png");
    when(fileStorageService.store(first)).thenReturn("stored-notes.pdf");
    when(fileStorageService.store(second)).thenReturn("stored-diagram.png");
    when(fileEntryRepository.save(any(FileEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

    List<FileEntry> entries = fileEntryService.uploadAll(List.of(first, second), 1L, user);

    assertThat(entries).hasSize(2);
    assertThat(entries).extracting(FileEntry::getOriginalFilename).containsExactly("notes.pdf", "diagram.png");
    assertThat(entries).extracting(FileEntry::getStoredFilename).containsExactly("stored-notes.pdf", "stored-diagram.png");
    verify(folderRepository, times(1)).findByIdAndUser(1L, user);
    verify(uploadValidator, times(1)).validateUpload(first);
    verify(uploadValidator, times(1)).validateUpload(second);
    verify(storageQuotaService, times(1)).assertWithinQuota(user, 0L, 400L);
    verify(fileEntryRepository, times(2)).save(any(FileEntry.class));
}

@Test
void uploadAll_RejectsEmptySelectionBeforeQuotaOrStorage() {
    User user = new User();
    user.setUsername("alice");

    assertThatThrownBy(() -> fileEntryService.uploadAll(List.of(), 1L, user))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Please choose at least one file to upload.");

    verify(storageQuotaService, never()).assertWithinQuota(any(), anyLong(), anyLong());
    verify(fileStorageService, never()).store(any(MultipartFile.class));
    verify(fileEntryRepository, never()).save(any(FileEntry.class));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./mvnw -Dtest=FileEntryServiceTests test
```

Expected: compilation fails because `FileEntryService.uploadAll(...)` does not exist.

- [ ] **Step 3: Implement `uploadAll`**

In `FileEntryService.java`, add these imports:

```java
import java.util.Collections;
```

Replace `upload(...)` with this delegating version and add the helper methods below it:

```java
@Transactional
public FileEntry upload(MultipartFile file, Long folderId, User user) throws IOException {
    return uploadAll(List.of(file), folderId, user).get(0);
}

@Transactional
public List<FileEntry> uploadAll(List<MultipartFile> files, Long folderId, User user) throws IOException {
    List<MultipartFile> uploadFiles = files == null ? List.of() : files.stream()
        .filter(file -> file != null && !file.isEmpty())
        .toList();
    if (uploadFiles.isEmpty()) {
        throw new IllegalArgumentException("Please choose at least one file to upload.");
    }

    Folder folder = folderRepository.findByIdAndUser(folderId, user)
        .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

    List<String> validatedMimeTypes = new ArrayList<>(uploadFiles.size());
    long addedBytes = 0L;
    for (MultipartFile file : uploadFiles) {
        validatedMimeTypes.add(uploadValidator.validateUpload(file));
        addedBytes += file.getSize();
    }
    storageQuotaService.assertWithinQuota(user, 0L, addedBytes);

    List<String> storedFilenames = new ArrayList<>(uploadFiles.size());
    List<FileEntry> savedEntries = new ArrayList<>(uploadFiles.size());
    try {
        for (int i = 0; i < uploadFiles.size(); i++) {
            MultipartFile file = uploadFiles.get(i);
            String storedFilename = fileStorageService.store(file);
            storedFilenames.add(storedFilename);
            savedEntries.add(fileEntryRepository.save(newFileEntry(file, validatedMimeTypes.get(i), storedFilename, folder, user)));
        }
    } catch (IOException | RuntimeException e) {
        cleanupStoredFiles(storedFilenames);
        throw e;
    }

    return Collections.unmodifiableList(savedEntries);
}

private FileEntry newFileEntry(MultipartFile file, String validatedMime, String storedFilename, Folder folder, User user) {
    FileEntry entry = new FileEntry();
    entry.setOriginalFilename(file.getOriginalFilename());
    entry.setStoredFilename(storedFilename);
    entry.setMimeType(validatedMime);
    entry.setFileSizeBytes(file.getSize());
    entry.setFolder(folder);
    entry.setUser(user);
    return entry;
}

private void cleanupStoredFiles(List<String> storedFilenames) throws IOException {
    IOException cleanupFailure = null;
    for (String storedFilename : storedFilenames) {
        try {
            fileStorageService.delete(storedFilename);
        } catch (IOException e) {
            if (cleanupFailure == null) cleanupFailure = e;
            else cleanupFailure.addSuppressed(e);
        }
    }
    if (cleanupFailure != null) throw cleanupFailure;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./mvnw -Dtest=FileEntryServiceTests test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/FileEntryService.java src/test/java/com/HendrikHoemberg/StudyHelper/service/FileEntryServiceTests.java
git commit -m "feat: support storing multiple uploaded files"
```

---

### Task 2: Multi-File Upload Controller and Modal

**Files:**
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FileControllerTests.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FileController.java`
- Modify: `src/main/resources/templates/fragments/file-form.html`

- [ ] **Step 1: Write failing controller test**

Add these imports to `FileControllerTests.java`:

```java
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
```

Add this test:

```java
@Test
@WithMockUser(username = "alice")
void upload_AcceptsMultipleFilesAndDelegatesToService() throws Exception {
    MockMultipartFile first = new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[] {1, 2});
    MockMultipartFile second = new MockMultipartFile("file", "diagram.png", "image/png", new byte[] {3});
    when(fileEntryService.uploadAll(anyList(), eq(42L), eq(user))).thenReturn(List.of());

    mockMvc.perform(multipart("/folders/42/files")
            .file(first)
            .file(second)
            .with(csrf())
            .principal(() -> "alice"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", "/folders/42?tab=files"));

    verify(fileEntryService).uploadAll(anyList(), eq(42L), eq(user));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./mvnw -Dtest=FileControllerTests#upload_AcceptsMultipleFilesAndDelegatesToService test
```

Expected: compilation fails because `FileEntryService.uploadAll(...)` is not used by the controller, or verification fails because `upload(...)` is called instead.

- [ ] **Step 3: Update controller upload signature**

In `FileController.java`, add:

```java
import java.util.List;
```

Change the upload parameter and service call:

```java
public String upload(@PathVariable Long folderId,
                     @RequestParam("file") List<MultipartFile> files,
                     Principal principal,
                     Model model,
                     RedirectAttributes redirectAttributes,
                     HttpServletResponse response,
                     @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
    User user = userService.getByUsername(principal.getName());
    try {
        fileEntryService.uploadAll(files, folderId, user);
        if (hxRequest != null) {
            response.addHeader("HX-Trigger", "refresh-quota");
            response.addHeader("HX-Redirect", "/folders/" + folderId + "?tab=files");
            return "fragments/quota";
        }
        return "redirect:/folders/" + folderId + "?tab=files";
    } catch (StorageQuotaExceededException e) {
        if (hxRequest != null) {
            response.addHeader("HX-Trigger", "refresh-quota");
            FolderView view = folderService.getFolderView(folderId, user, null, "asc", ActiveTab.FILES);
            model.addAttribute("view", view);
            model.addAttribute("sortBy", null);
            model.addAttribute("direction", "asc");
            model.addAttribute("error", e.getMessage());
            return "fragments/folder-detail :: tabsSection";
        }
        redirectAttributes.addFlashAttribute("error", e.getMessage());
    } catch (IllegalArgumentException e) {
        if (hxRequest != null) {
            FolderView view = folderService.getFolderView(folderId, user, null, "asc", ActiveTab.FILES);
            model.addAttribute("view", view);
            model.addAttribute("sortBy", null);
            model.addAttribute("direction", "asc");
            model.addAttribute("error", e.getMessage());
            return "fragments/folder-detail :: tabsSection";
        }
        redirectAttributes.addFlashAttribute("error", e.getMessage());
    } catch (IOException e) {
        if (hxRequest != null) {
            FolderView view = folderService.getFolderView(folderId, user, null, "asc", ActiveTab.FILES);
            model.addAttribute("view", view);
            model.addAttribute("sortBy", null);
            model.addAttribute("direction", "asc");
            model.addAttribute("error", "Upload failed: " + e.getMessage());
            return "fragments/folder-detail :: tabsSection";
        }
        redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
    }
    return "redirect:/folders/" + folderId + "?tab=files";
}
```

- [ ] **Step 4: Update upload modal**

In `file-form.html`, change the file input:

```html
<input type="file" name="file" id="upload-file-input" class="sh-upload-file-input" multiple required>
```

Change chooser text:

```html
<span>Choose files</span>
```

Change the JavaScript `updateUploadState` function:

```javascript
function updateUploadState() {
    var files = Array.prototype.slice.call(fileInput.files || []);
    var hasFile = files.length > 0;
    if (files.length === 1) {
        selectedFileName.textContent = files[0].name;
    } else if (files.length > 1) {
        selectedFileName.textContent = files.length + ' files selected: ' + files.map(function(file) {
            return file.name;
        }).join(', ');
    } else {
        selectedFileName.textContent = 'No files selected';
    }
    selectedFile.classList.toggle('is-visible', hasFile);
    chooser.classList.toggle('is-selected', hasFile);
    chooserText.textContent = hasFile ? 'Change files' : 'Choose files';
    submitButton.disabled = !hasFile;
}
```

Change submit button text:

```html
<button type="submit" class="sh-btn sh-btn-primary" id="upload-submit" disabled>Upload</button>
```

- [ ] **Step 5: Run controller tests**

Run:

```bash
./mvnw -Dtest=FileControllerTests test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/FileController.java src/test/java/com/HendrikHoemberg/StudyHelper/controller/FileControllerTests.java src/main/resources/templates/fragments/file-form.html
git commit -m "feat: accept multiple files in folder uploads"
```

---

### Task 3: AI Flashcard Service List Input

**Files:**
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java`

- [ ] **Step 1: Write failing AI service test**

Add this test to `AiFlashcardServiceTests`:

```java
@Test
void generate_MultiplePdfDocuments_AttachesAllPdfMediaInOneRequest() {
    when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
        new GeneratedFlashcard("Front 1", "Back 1"),
        new GeneratedFlashcard("Front 2", "Back 2")
    ));

    List<GeneratedFlashcard> result = service.generate(List.of(
        new PdfDocument("chapter1.pdf", new ByteArrayResource(new byte[] {0x25, 0x50, 0x44, 0x46})),
        new PdfDocument("chapter2.pdf", new ByteArrayResource(new byte[] {0x25, 0x50, 0x44, 0x46}))
    ), 20, "focus on formulas");

    assertThat(result).hasSize(2);
    assertThat(capturedPrompt.get()).contains("chapter1.pdf");
    assertThat(capturedPrompt.get()).contains("chapter2.pdf");
    assertThat(capturedPrompt.get()).contains("focus on formulas");
    assertThat(capturedMedia).hasSize(2);
    assertThat(capturedMedia).allSatisfy(media ->
        assertThat(media.getMimeType().toString()).isEqualTo("application/pdf"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./mvnw -Dtest=AiFlashcardServiceTests#generate_MultiplePdfDocuments_AttachesAllPdfMediaInOneRequest test
```

Expected: compilation fails because `generate(List<DocumentInput>, int, String)` does not exist.

- [ ] **Step 3: Add list-based generation overload**

In `AiFlashcardService.java`, change the existing overloads to delegate:

```java
public List<GeneratedFlashcard> generate(DocumentInput document) {
    return generate(document, DEFAULT_FLASHCARD_COUNT, null);
}

public List<GeneratedFlashcard> generate(DocumentInput document, String additionalInstructions) {
    return generate(document, DEFAULT_FLASHCARD_COUNT, additionalInstructions);
}

public List<GeneratedFlashcard> generate(DocumentInput document, int cardCount, String additionalInstructions) {
    if (document == null) {
        throw new IllegalArgumentException("Flashcard generation requires at least one PDF input.");
    }
    return generate(List.of(document), cardCount, additionalInstructions);
}

public List<GeneratedFlashcard> generate(List<DocumentInput> documents, int cardCount, String additionalInstructions) {
    if (documents == null || documents.isEmpty()) {
        throw new IllegalArgumentException("Flashcard generation requires at least one PDF input.");
    }

    List<DocumentInput> usableDocuments = documents.stream()
        .filter(document -> document != null)
        .toList();
    if (usableDocuments.isEmpty()) {
        throw new IllegalArgumentException("Flashcard generation requires at least one PDF input.");
    }

    String docContent = AiGenerationSupport.textDocuments(usableDocuments);
    String pdfListing = AiGenerationSupport.pdfListing(usableDocuments);
    Media[] pdfMedia = AiGenerationSupport.pdfMedia(usableDocuments);

    if (docContent.isBlank() && pdfMedia.length == 0) {
        throw new IllegalArgumentException(
                "Selected sources contain no usable text or PDFs. Pick a document with extractable content, or a PDF in full-document mode.");
    }

    int normalizedCount = normalizeCardCount(cardCount);
    String prompt = buildPrompt(docContent, pdfListing, normalizedCount, additionalInstructions);

    FlashcardsResponse response;
    try {
        response = chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder()
                        .responseMimeType("application/json")
                        .responseSchema(responseSchema))
                .user(u -> {
                    u.text(prompt);
                    if (pdfMedia.length > 0)
                        u.media(pdfMedia);
                })
                .call()
                .entity(FlashcardsResponse.class);
    } catch (Exception e) {
        throw AiGenerationSupport.failure(log, "FLASHCARDS", "PROVIDER_REQUEST",
            "AI request failed, please retry with fewer or smaller PDFs.", e);
    }

    try {
        List<GeneratedFlashcard> rawList = response == null || response.flashcards() == null
                ? List.of()
                : response.flashcards();

        List<GeneratedFlashcard> valid = new ArrayList<>();
        for (GeneratedFlashcard card : rawList) {
            if (card == null)
                continue;
            String front = card.frontText() == null ? null : card.frontText().trim();
            String back = card.backText() == null ? null : card.backText().trim();
            if (front == null || front.isBlank() || back == null || back.isBlank())
                continue;
            valid.add(new GeneratedFlashcard(front, back));
        }

        if (valid.isEmpty()) {
            throw AiGenerationSupport.failure(log, "FLASHCARDS", "RESPONSE_VALIDATION",
                "AI returned no valid flashcards; please retry.",
                new IllegalStateException("AI response contained no flashcards with both frontText and backText."));
        }

        return valid.stream().limit(normalizedCount).toList();

    } catch (IllegalStateException | IllegalArgumentException e) {
        throw e;
    } catch (Exception e) {
        throw AiGenerationSupport.failure(log, "FLASHCARDS", "RESPONSE_PARSE",
            "Could not parse the AI response. Please try again.", e);
    }
}
```

Remove the duplicated single-document body that previously created `List.of(document)`.

- [ ] **Step 4: Run AI service tests**

Run:

```bash
./mvnw -Dtest=AiFlashcardServiceTests test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java
git commit -m "feat: generate flashcards from multiple documents"
```

---

### Task 4: Flashcard Controller Multi-PDF Backend

**Files:**
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationControllerTests.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationController.java`

- [ ] **Step 1: Write failing controller tests**

Add `ArgumentCaptor<List<DocumentInput>>` tests to `FlashcardGenerationControllerTests.java`:

```java
@Test
void generate_TextModeMultiplePdfs_SendsAllTextDocumentsAndSavesOneDeck() throws Exception {
    FileEntry secondPdf = new FileEntry();
    secondPdf.setId(100L);
    secondPdf.setOriginalFilename("lecture-2.pdf");
    secondPdf.setFileSizeBytes(100L);
    secondPdf.setFolder(folder);
    secondPdf.setUser(user);

    when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
    when(fileEntryService.getByIdAndUser(100L, user)).thenReturn(secondPdf);
    when(documentExtractionService.isSupported(pdf)).thenReturn(true);
    when(documentExtractionService.isSupported(secondPdf)).thenReturn(true);
    when(documentExtractionService.extractText(pdf)).thenReturn("Lecture one text");
    when(documentExtractionService.extractText(secondPdf)).thenReturn("Lecture two text");
    when(aiFlashcardService.generate(any(List.class), anyInt(), any())).thenReturn(List.of(new GeneratedFlashcard("Q", "A")));
    when(persistenceService.saveGeneratedCards(eq(FlashcardGenerationDestination.NEW_DECK), eq(null), eq(10L), eq("Combined"), eq(user), any())).thenReturn(deck);
    when(deckService.getDeck(20L, user)).thenReturn(deck);

    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletResponse response = new MockHttpServletResponse();

    String view = controller.generate(
        List.of(99L, 100L),
        DocumentMode.TEXT,
        null,
        20,
        FlashcardGenerationDestination.NEW_DECK,
        null,
        10L,
        "Combined",
        model,
        () -> "alice",
        response,
        "true"
    );

    ArgumentCaptor<List<DocumentInput>> docsCaptor = ArgumentCaptor.forClass(List.class);
    verify(aiFlashcardService).generate(docsCaptor.capture(), eq(20), eq(null));
    assertThat(docsCaptor.getValue()).hasSize(2);
    assertThat(docsCaptor.getValue()).allSatisfy(doc -> assertThat(doc).isInstanceOf(TextDocument.class));
    assertThat(view).isEqualTo("fragments/deck :: deckDetail");
    assertThat(model.get("successMessage")).isEqualTo("Generated 1 flashcard from 2 PDFs.");
}

@Test
void generate_FullPdfModeMultiplePdfs_SendsAllPdfDocuments() throws Exception {
    FileEntry secondPdf = new FileEntry();
    secondPdf.setId(100L);
    secondPdf.setOriginalFilename("lecture-2.pdf");
    secondPdf.setFileSizeBytes(100L);
    secondPdf.setFolder(folder);
    secondPdf.setUser(user);

    when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
    when(fileEntryService.getByIdAndUser(100L, user)).thenReturn(secondPdf);
    when(documentExtractionService.isSupported(pdf)).thenReturn(true);
    when(documentExtractionService.isSupported(secondPdf)).thenReturn(true);
    when(documentExtractionService.loadResource(pdf)).thenReturn(new ByteArrayResource(new byte[] {1}));
    when(documentExtractionService.loadResource(secondPdf)).thenReturn(new ByteArrayResource(new byte[] {2}));
    when(aiFlashcardService.generate(any(List.class), anyInt(), any())).thenReturn(List.of(new GeneratedFlashcard("Q", "A")));
    when(persistenceService.saveGeneratedCards(eq(FlashcardGenerationDestination.EXISTING_DECK), eq(20L), eq(null), eq(null), eq(user), any())).thenReturn(deck);
    when(deckService.getDeck(20L, user)).thenReturn(deck);

    controller.generate(List.of(99L, 100L), DocumentMode.FULL_PDF, null, 20, FlashcardGenerationDestination.EXISTING_DECK, 20L, null, null, new ExtendedModelMap(), () -> "alice", new MockHttpServletResponse(), "true");

    ArgumentCaptor<List<DocumentInput>> docsCaptor = ArgumentCaptor.forClass(List.class);
    verify(aiFlashcardService).generate(docsCaptor.capture(), eq(20), eq(null));
    assertThat(docsCaptor.getValue()).hasSize(2);
    assertThat(docsCaptor.getValue()).allSatisfy(doc -> assertThat(doc).isInstanceOf(PdfDocument.class));
    verify(documentExtractionService, never()).extractText(any(FileEntry.class));
}
```

Update the missing PDF test call to pass `List.of()` instead of `null` once the controller signature changes, and expect:

```java
assertThat(model.get("generationError")).isEqualTo("Please select at least one PDF.");
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./mvnw -Dtest=FlashcardGenerationControllerTests test
```

Expected: compilation fails because `generate` still accepts `Long fileId` and the AI service call still uses single `DocumentInput`.

- [ ] **Step 3: Update controller method signatures**

In `FlashcardGenerationController.java`, change:

```java
public String generate(@RequestParam(required = false) Long fileId,
```

to:

```java
public String generate(@RequestParam(name = "fileId", required = false) List<Long> fileIds,
```

Change `preflightGenerate` the same way:

```java
public String preflightGenerate(@RequestParam(name = "fileId", required = false) List<Long> fileIds,
```

Change calls from:

```java
DocumentInput input = validateAndBuildInput(fileId, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, user);
...
List<GeneratedFlashcard> generated = aiFlashcardService.generate(input, cardCount, additionalInstructions);
...
model.addAttribute("successMessage", successMessage(generated.size(), input.filename()));
```

to:

```java
List<DocumentInput> inputs = validateAndBuildInputs(fileIds, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, user);
...
List<GeneratedFlashcard> generated = aiFlashcardService.generate(inputs, cardCount, additionalInstructions);
...
model.addAttribute("successMessage", successMessage(generated.size(), inputs));
```

In catch blocks, replace:

```java
model.addAttribute("selectedFileId", fileId);
```

with:

```java
model.addAttribute("selectedFileIds", safeFileIds(fileIds));
model.addAttribute("selectedFileId", firstFileId(fileIds));
```

- [ ] **Step 4: Add multi-input validation helpers**

Replace `validateAndBuildInput(...)` with:

```java
private List<DocumentInput> validateAndBuildInputs(List<Long> fileIds,
                                                   DocumentMode documentMode,
                                                   FlashcardGenerationDestination destination,
                                                   Long existingDeckId,
                                                   Long newDeckFolderId,
                                                   String newDeckName,
                                                   User user) throws Exception {
    List<Long> selectedIds = safeFileIds(fileIds);
    if (selectedIds.isEmpty()) throw new IllegalArgumentException("Please select at least one PDF.");
    if (destination == null) throw new IllegalArgumentException("Please choose where to save the generated flashcards.");
    persistenceService.validateDestination(destination, existingDeckId, newDeckFolderId, newDeckName, user);

    List<DocumentInput> inputs = new ArrayList<>(selectedIds.size());
    for (Long selectedId : selectedIds) {
        FileEntry file = fileEntryService.getByIdAndUser(selectedId, user);
        if (!isPdf(file) || !documentExtractionService.isSupported(file)) {
            throw new IllegalArgumentException("Please select only supported PDFs under 10 MB.");
        }
        inputs.add(buildDocumentInput(file, documentMode));
    }
    return inputs;
}

private List<Long> safeFileIds(List<Long> fileIds) {
    if (fileIds == null) return List.of();
    return fileIds.stream()
        .filter(id -> id != null)
        .distinct()
        .toList();
}

private Long firstFileId(List<Long> fileIds) {
    return safeFileIds(fileIds).stream().findFirst().orElse(null);
}
```

Add import:

```java
import java.util.ArrayList;
```

Update `buildDocumentInput` text-mode error:

```java
throw new IllegalArgumentException(file.getOriginalFilename() + " has no extractable text. Try Full PDF mode.");
```

Add a new success-message helper:

```java
private String successMessage(int count, List<DocumentInput> inputs) {
    String cardLabel = " flashcard" + (count == 1 ? "" : "s");
    if (inputs.size() == 1) {
        return "Generated " + count + cardLabel + " from " + inputs.get(0).filename() + ".";
    }
    return "Generated " + count + cardLabel + " from " + inputs.size() + " PDFs.";
}
```

Remove or stop using the old `successMessage(int count, String filename)` helper.

- [ ] **Step 5: Update showGenerator selected IDs**

In `showGenerator`, add:

```java
model.addAttribute("selectedFileIds", fileId == null ? List.of() : List.of(fileId));
```

Keep the existing `selectedFileId` attribute for compatibility during the UI task.

- [ ] **Step 6: Run controller tests**

Run:

```bash
./mvnw -Dtest=FlashcardGenerationControllerTests test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

Run:

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationController.java src/test/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationControllerTests.java
git commit -m "feat: accept multiple PDFs for flashcard generation"
```

---

### Task 5: Flashcard Generator Multi-Select UI and Custom Checkboxes

**Files:**
- Modify: `src/main/resources/templates/fragments/flashcard-generator.html`
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/static/css/styles.css`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java`

- [ ] **Step 1: Write failing resource regression test**

Add this test to `UiResourceRegressionTests.java`:

```java
@Test
void flashcardGenerator_UsesCustomCheckboxesForPdfSelection() throws IOException {
    String template = Files.readString(Path.of("src/main/resources/templates/fragments/flashcard-generator.html"));
    String css = Files.readString(Path.of("src/main/resources/static/css/styles.css"));

    assertThat(template).contains("type=\"checkbox\" name=\"fileId\"");
    assertThat(template).contains("class=\"sh-checkbox");
    assertThat(template).doesNotContain("type=\"radio\" name=\"fileId\"");
    assertThat(css).contains("appearance: none");
    assertThat(css).contains(".sh-checkbox:checked::after");
}
```

If `UiResourceRegressionTests` does not already import these, add:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./mvnw -Dtest=UiResourceRegressionTests#flashcardGenerator_UsesCustomCheckboxesForPdfSelection test
```

Expected: assertion fails because the template still contains radio buttons and `.sh-checkbox` CSS does not define custom rendering.

- [ ] **Step 3: Update template PDF selection**

In `flashcard-generator.html`, change the header text:

```html
<p class="sh-page-subtitle">Generate flashcards from one or more uploaded PDFs.</p>
```

Change section title:

```html
<div class="sh-section-title">Choose PDFs</div>
```

Add one global mode control above the PDF list, after the slow-warning banner:

```html
<div class="vb-pdf-mode sh-ai-global-pdf-mode">
    <input type="hidden" name="documentMode" th:value="${selectedDocumentMode ?: T(com.HendrikHoemberg.StudyHelper.dto.DocumentMode).TEXT}">
    <button type="button" class="vb-pdf-mode-btn" th:classappend="${selectedDocumentMode == null || #strings.toString(selectedDocumentMode) == 'TEXT'} ? ' is-active' : ''" data-mode="TEXT" th:attr="aria-pressed=${selectedDocumentMode == null || #strings.toString(selectedDocumentMode) == 'TEXT'}">Text</button>
    <button type="button" class="vb-pdf-mode-btn" th:classappend="${#strings.toString(selectedDocumentMode) == 'FULL_PDF'} ? ' is-active' : ''" data-mode="FULL_PDF" th:attr="aria-pressed=${#strings.toString(selectedDocumentMode) == 'FULL_PDF'}" title="Send the full PDFs with figures, diagrams, and layout.">Full PDF</button>
</div>
```

Change each PDF row input:

```html
<input class="sh-checkbox" type="checkbox" name="fileId" th:value="${pdf.id}" th:checked="${selectedFileIds != null ? #lists.contains(selectedFileIds, pdf.id) : selectedFileId == pdf.id}">
```

Remove the per-row block:

```html
<span class="vb-pdf-mode sh-ai-pdf-mode" ...>
    ...
</span>
```

- [ ] **Step 4: Update JavaScript selection logic**

In `app.js`, replace the `input[name="fileId"]` radio change branch with:

```javascript
if (event.target.matches('.sh-ai-pdf-row input[name="fileId"]')) {
    const row = event.target.closest('.sh-ai-pdf-row');
    row?.classList.toggle('is-selected', event.target.checked);
    syncAiFolderSelectionWithSelectedPdf();
}
```

Replace the PDF mode click handler selector:

```javascript
const btn = event.target.closest('.sh-ai-global-pdf-mode .vb-pdf-mode-btn');
```

Replace the handler body hidden lookup with:

```javascript
const group = btn.closest('.sh-ai-global-pdf-mode');
const hidden = group?.querySelector('input[name="documentMode"]');
```

Replace `updateAiPdfSizeWarning` with:

```javascript
function updateAiPdfSizeWarning() {
    const warning = document.querySelector('.sh-ai-pdf-size-warning');
    if (!warning) return;

    const selectedRows = Array.from(document.querySelectorAll('.sh-ai-pdf-row input[name="fileId"]:checked'))
        .map(input => input.closest('.sh-ai-pdf-row'))
        .filter(Boolean);
    const showWarning = selectedRows.some(row => Number(row?.dataset?.fileSize || 0) >= AI_PDF_SLOW_WARNING_BYTES);
    warning.hidden = !showWarning;
}
```

Replace `syncAiFolderSelectionWithSelectedPdf` with:

```javascript
function syncAiFolderSelectionWithSelectedPdf() {
    const selectedRows = Array.from(document.querySelectorAll('.sh-ai-pdf-row input[name="fileId"]:checked'))
        .map(input => input.closest('.sh-ai-pdf-row'))
        .filter(Boolean);
    document.querySelectorAll('.sh-ai-pdf-row').forEach(row => {
        const checkbox = row.querySelector('input[name="fileId"]');
        row.classList.toggle('is-selected', !!checkbox?.checked);
    });

    if (selectedRows.length === 1) {
        prefillAiNewDeckNameForPdf(selectedRows[0]);
        preselectAiNewDeckFolderForPdf(selectedRows[0]);
    } else if (selectedRows.length > 1) {
        const deckNameInput = document.querySelector('.sh-ai-flashcard-form input[name="newDeckName"]');
        if (deckNameInput && !deckNameInput.value.trim()) deckNameInput.value = 'Generated Flashcards';
    }
    updateAiPdfSizeWarning();
}
```

Update any remaining calls from `updateAiPdfSizeWarning(row)` to `updateAiPdfSizeWarning()`.

- [ ] **Step 5: Add custom `.sh-checkbox` CSS**

Replace the current `.sh-checkbox` rule in `styles.css` with:

```css
.sh-checkbox {
    appearance: none;
    -webkit-appearance: none;
    width: 16px;
    height: 16px;
    margin: 0;
    border: 1px solid var(--border);
    border-radius: 4px;
    background: var(--surface);
    cursor: pointer;
    flex-shrink: 0;
    display: inline-grid;
    place-content: center;
    transition: background var(--transition-fast), border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.sh-checkbox::after {
    content: "";
    width: 8px;
    height: 5px;
    border-left: 2px solid #fff;
    border-bottom: 2px solid #fff;
    transform: rotate(-45deg) scale(0);
    transform-origin: center;
    transition: transform var(--transition-fast);
}

.sh-checkbox:checked {
    background: var(--accent);
    border-color: var(--accent);
}

.sh-checkbox:checked::after {
    transform: rotate(-45deg) scale(1);
}

.sh-checkbox:indeterminate {
    background: var(--accent);
    border-color: var(--accent);
}

.sh-checkbox:indeterminate::after {
    width: 8px;
    height: 2px;
    border-left: 0;
    border-bottom: 2px solid #fff;
    transform: rotate(0deg) scale(1);
}

.sh-checkbox:focus-visible {
    outline: none;
    box-shadow: 0 0 0 3px var(--accent-glow);
}

.sh-checkbox:disabled {
    cursor: not-allowed;
    opacity: 0.45;
}
```

Add generator mode spacing near the existing flashcard generator styles:

```css
.sh-ai-global-pdf-mode {
    margin-bottom: 0.75rem;
    width: fit-content;
}
```

- [ ] **Step 6: Run resource regression test**

Run:

```bash
./mvnw -Dtest=UiResourceRegressionTests#flashcardGenerator_UsesCustomCheckboxesForPdfSelection test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

Run:

```bash
git add src/main/resources/templates/fragments/flashcard-generator.html src/main/resources/static/js/app.js src/main/resources/static/css/styles.css src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java
git commit -m "feat: add multi-select PDF flashcard UI"
```

---

### Task 6: Final Verification

**Files:**
- No source edits unless verification exposes a failure.

- [ ] **Step 1: Run focused test suite**

Run:

```bash
./mvnw -Dtest=FileEntryServiceTests,FileControllerTests,AiFlashcardServiceTests,FlashcardGenerationControllerTests,UiResourceRegressionTests test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run full test suite**

Run:

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Inspect final diff**

Run:

```bash
git status --short
git log --oneline -6
```

Expected: working tree is clean, and recent commits include the task commits from this plan.

---

## Self-Review

- Spec coverage: upload modal, upload controller/service, multi-PDF flashcard backend, one combined deck, custom checkbox styling, client-side multi-select behavior, error messages, and tests are covered by Tasks 1-6.
- Plan text scan: no red-flag marker text or open-ended test instructions remain.
- Type consistency: plan consistently uses `List<MultipartFile>`, `List<Long> fileIds`, and `AiFlashcardService.generate(List<DocumentInput>, int, String)`.
