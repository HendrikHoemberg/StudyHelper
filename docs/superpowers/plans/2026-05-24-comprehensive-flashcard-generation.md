# Comprehensive Flashcard Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace user-selected flashcard counts with timeout-safe, chunked, coverage-driven flashcard generation that shows predictable quota cost before generation.

**Architecture:** Add a deterministic generation plan service that chunks selected PDFs into request units, then run chunked AI generation inside persisted background jobs. The controller returns short-lived HTMX responses for estimate, start, and progress polling; quota is charged once when a job is accepted.

**Tech Stack:** Spring Boot 4 MVC, Thymeleaf, HTMX, Spring Data JPA, PDFBox 3, Spring AI Google GenAI, Gemini `gemini-3.1-flash-lite` with `thinkingLevel`.

---

## File Structure

- Create `src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardChunk.java`
  - Immutable chunk input for AI generation.
- Create `src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardGenerationPlan.java`
  - Immutable preflight plan shown to users and used for charging.
- Create `src/main/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJob.java`
  - Persisted job status and destination/source snapshot.
- Create `src/main/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJobStatus.java`
  - Job state enum.
- Create `src/main/java/com/HendrikHoemberg/StudyHelper/repository/FlashcardGenerationJobRepository.java`
  - User-scoped job lookup and stale-running lookup.
- Create `src/main/java/com/HendrikHoemberg/StudyHelper/config/GoogleGenAiClientConfig.java`
  - Supplies a Google GenAI `Client` bean with finite provider-call timeout.
- Create `src/main/java/com/HendrikHoemberg/StudyHelper/config/FlashcardGenerationAsyncConfig.java`
  - Small bounded executor for background generation.
- Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationPlanService.java`
  - Builds chunks, estimates cost/time/cards/risk, extracts PDF page ranges.
- Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationJobService.java`
  - Accepts jobs, checks quota, starts worker, exposes progress, enforces timeout.
- Create `src/test/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationPlanServiceTests.java`
- Create `src/test/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJobTests.java`
- Create `src/test/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationJobServiceTests.java`
- Create `src/test/java/com/HendrikHoemberg/StudyHelper/config/GoogleGenAiClientConfigTests.java`
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiRequestQuotaService.java`
  - Add amount-based charging while keeping existing one-request method.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java`
  - Add chunked generation path, chunk prompt, `MEDIUM` thinking level, dedupe.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/DocumentExtractionService.java`
  - Add PDF page count, text extraction by page, PDF page-range resource creation.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationController.java`
  - Replace synchronous generation path with estimate/start/status job flow.
- Modify `src/main/resources/templates/fragments/flashcard-generator.html`
  - Remove card-count stepper, add estimate and high-risk acknowledgement.
- Modify `src/main/resources/templates/fragments/layout.html`
  - No new global script if existing `app.js` can own HTMX behavior.
- Modify `src/main/resources/static/js/app.js`
  - Trigger estimate refresh, block high-risk submit until acknowledged, handle progress.
- Modify `src/main/resources/messages.properties`
  - Add English estimate/progress/warning labels.
- Modify `src/main/resources/messages_de.properties`
  - Add German estimate/progress/warning labels.
- Modify tests:
  - `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiRequestQuotaServiceTests.java`
  - `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java`
  - `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationControllerTests.java`
  - `src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java`

## Task 1: Amount-Based AI Quota

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiRequestQuotaService.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiRequestQuotaServiceTests.java`

- [ ] **Step 1: Write failing quota tests**

Add these tests to `AiRequestQuotaServiceTests`:

```java
@Test
void checkAndRecordAmount_WhenQuotaAvailable_RecordsRequestedAmount() {
    user.setDailyAiRequestLimit(10);
    AiRequestUsage usage = new AiRequestUsage();
    usage.setUser(user);
    usage.setUsageDate(today);
    usage.setRequestCount(3);
    when(aiRequestUsageRepository.findByUserAndUsageDateForUpdate(user, today)).thenReturn(Optional.of(usage));
    when(aiRequestUsageRepository.save(usage)).thenReturn(usage);

    aiRequestQuotaService.checkAndRecord(user, 4);

    assertThat(usage.getRequestCount()).isEqualTo(7);
    verify(aiRequestUsageRepository).save(usage);
}

@Test
void checkAndRecordAmount_WhenAmountWouldExceedLimit_ThrowsAndDoesNotSave() {
    user.setDailyAiRequestLimit(10);
    AiRequestUsage usage = new AiRequestUsage();
    usage.setUser(user);
    usage.setUsageDate(today);
    usage.setRequestCount(8);
    when(aiRequestUsageRepository.findByUserAndUsageDateForUpdate(user, today)).thenReturn(Optional.of(usage));

    assertThatThrownBy(() -> aiRequestQuotaService.checkAndRecord(user, 3))
        .isInstanceOf(AiQuotaExceededException.class)
        .hasMessage("Daily AI request limit reached.");

    verify(aiRequestUsageRepository, never()).save(any(AiRequestUsage.class));
}

@Test
void checkAndRecordAmount_WhenAmountIsLessThanOne_ThrowsIllegalArgument() {
    assertThatThrownBy(() -> aiRequestQuotaService.checkAndRecord(user, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI request amount must be positive.");
}
```

- [ ] **Step 2: Run failing quota tests**

Run:

```bash
./mvnw -Dtest=AiRequestQuotaServiceTests test
```

Expected: compile failure because `checkAndRecord(User,int)` does not exist.

- [ ] **Step 3: Implement amount-based quota**

Change `AiRequestQuotaService`:

```java
@Transactional
public void checkAndRecord(User user) {
    checkAndRecord(user, 1);
}

@Transactional
public void checkAndRecord(User user, int requestCount) {
    if (requestCount < 1) {
        throw new IllegalArgumentException("AI request amount must be positive.");
    }
    LocalDate today = LocalDate.now(clock);
    AiRequestUsage usage = lockOrCreateUsageRow(user, today);
    if (usage.getRequestCount() + requestCount > user.getDailyAiRequestLimit()) {
        throw new AiQuotaExceededException("Daily AI request limit reached.");
    }
    usage.setRequestCount(usage.getRequestCount() + requestCount);
    aiRequestUsageRepository.save(usage);
}
```

- [ ] **Step 4: Verify quota tests pass**

Run:

```bash
./mvnw -Dtest=AiRequestQuotaServiceTests test
```

Expected: all tests pass.

- [ ] **Step 5: Commit quota support**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiRequestQuotaService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiRequestQuotaServiceTests.java
git commit -m "feat: support amount-based AI quota charging"
```

## Task 2: Document Page Utilities

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DocumentExtractionService.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DocumentExtractionServiceTests.java`

- [ ] **Step 1: Add failing PDF page utility tests**

Add tests using a temporary PDF created with PDFBox:

```java
@Test
void pdfPageCount_ReturnsNumberOfPages() throws Exception {
    FileEntry file = pdfFile("pages.pdf", 3);

    assertThat(service.pdfPageCount(file)).isEqualTo(3);
}

@Test
void extractTextByPage_ReturnsOneEntryPerPage() throws Exception {
    FileEntry file = pdfFileWithText("pages.pdf", "Alpha", "Beta");

    List<String> pages = service.extractPdfTextPages(file);

    assertThat(pages).hasSize(2);
    assertThat(pages.get(0)).contains("Alpha");
    assertThat(pages.get(1)).contains("Beta");
}

@Test
void loadPdfPageRangeResource_ReturnsOnlyRequestedPages() throws Exception {
    FileEntry file = pdfFile("pages.pdf", 4);

    Resource range = service.loadPdfPageRangeResource(file, 2, 3);

    try (PDDocument doc = Loader.loadPDF(range.getContentAsByteArray())) {
        assertThat(doc.getNumberOfPages()).isEqualTo(2);
    }
}
```

Use helper methods that save PDFs through the existing test storage setup in `DocumentExtractionServiceTests`.

- [ ] **Step 2: Run failing document tests**

Run:

```bash
./mvnw -Dtest=DocumentExtractionServiceTests test
```

Expected: compile failure for missing methods.

- [ ] **Step 3: Implement PDF page utilities**

Add imports:

```java
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.core.io.ByteArrayResource;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
```

Add methods:

```java
public int pdfPageCount(FileEntry file) throws IOException {
    assertPdf(file);
    Path path = fileStorageService.resolvePath(file.getStoredFilename());
    try (PDDocument doc = Loader.loadPDF(path.toFile())) {
        return doc.getNumberOfPages();
    }
}

public List<String> extractPdfTextPages(FileEntry file) throws IOException {
    assertPdf(file);
    Path path = fileStorageService.resolvePath(file.getStoredFilename());
    try (PDDocument doc = Loader.loadPDF(path.toFile())) {
        PDFTextStripper stripper = new PDFTextStripper();
        List<String> pages = new ArrayList<>(doc.getNumberOfPages());
        for (int page = 1; page <= doc.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            pages.add(stripper.getText(doc).strip());
        }
        return pages;
    }
}

public Resource loadPdfPageRangeResource(FileEntry file, int startPageInclusive, int endPageInclusive) throws IOException {
    assertPdf(file);
    if (startPageInclusive < 1 || endPageInclusive < startPageInclusive) {
        throw new IllegalArgumentException("Invalid PDF page range.");
    }
    Path path = fileStorageService.resolvePath(file.getStoredFilename());
    try (PDDocument source = Loader.loadPDF(path.toFile());
         PDDocument target = new PDDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        int end = Math.min(endPageInclusive, source.getNumberOfPages());
        for (int page = startPageInclusive; page <= end; page++) {
            PDPage imported = target.importPage(source.getPage(page - 1));
            imported.setResources(source.getPage(page - 1).getResources());
        }
        target.save(out);
        return new ByteArrayResource(out.toByteArray()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
    }
}

private void assertPdf(FileEntry file) {
    String ext = extension(file.getOriginalFilename());
    if (!"pdf".equals(ext)) {
        throw new IllegalArgumentException("Expected pdf file, got: " + ext);
    }
}
```

- [ ] **Step 4: Verify document tests pass**

Run:

```bash
./mvnw -Dtest=DocumentExtractionServiceTests test
```

Expected: pass.

- [ ] **Step 5: Commit document utilities**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DocumentExtractionService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DocumentExtractionServiceTests.java
git commit -m "feat: add PDF page extraction utilities"
```

## Task 3: Generation Plan and Chunking

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardChunk.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardGenerationPlan.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardGenerationRisk.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationPlanService.java`
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationPlanServiceTests.java`

- [ ] **Step 1: Write failing plan service tests**

Create `FlashcardGenerationPlanServiceTests`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationRisk;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlashcardGenerationPlanServiceTests {

    private DocumentExtractionService documentExtractionService;
    private FlashcardGenerationPlanService service;
    private FileEntry pdf;

    @BeforeEach
    void setUp() {
        documentExtractionService = mock(DocumentExtractionService.class);
        service = new FlashcardGenerationPlanService(documentExtractionService);
        pdf = new FileEntry();
        pdf.setId(7L);
        pdf.setOriginalFilename("lecture.pdf");
    }

    @Test
    void planTextMode_SplitsEveryTwoPagesWhenWordsAreSmall() throws Exception {
        when(documentExtractionService.extractPdfTextPages(pdf)).thenReturn(List.of("one", "two", "three", "four", "five"));

        var plan = service.plan(List.of(pdf), DocumentMode.TEXT);

        assertThat(plan.requestCost()).isEqualTo(3);
        assertThat(plan.chunks()).extracting("startPage").containsExactly(1, 3, 5);
        assertThat(plan.chunks()).extracting("endPage").containsExactly(2, 4, 5);
    }

    @Test
    void planTextMode_SplitsOnFifteenHundredWords() throws Exception {
        String words = "word ".repeat(1600);
        when(documentExtractionService.extractPdfTextPages(pdf)).thenReturn(List.of(words));

        var plan = service.plan(List.of(pdf), DocumentMode.TEXT);

        assertThat(plan.requestCost()).isEqualTo(2);
        assertThat(plan.chunks()).hasSize(2);
    }

    @Test
    void plan_AssignsRiskFromRequestCost() throws Exception {
        when(documentExtractionService.extractPdfTextPages(pdf)).thenReturn(List.of("x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x", "x"));

        var plan = service.plan(List.of(pdf), DocumentMode.TEXT);

        assertThat(plan.requestCost()).isEqualTo(16);
        assertThat(plan.risk()).isEqualTo(FlashcardGenerationRisk.WARNING);
    }
}
```

- [ ] **Step 2: Run failing plan tests**

Run:

```bash
./mvnw -Dtest=FlashcardGenerationPlanServiceTests test
```

Expected: compile failure because DTOs and service do not exist.

- [ ] **Step 3: Create chunk/plan DTOs**

Create `FlashcardGenerationRisk.java`:

```java
package com.HendrikHoemberg.StudyHelper.dto;

public enum FlashcardGenerationRisk {
    NORMAL,
    WARNING,
    HIGH
}
```

Create `FlashcardChunk.java`:

```java
package com.HendrikHoemberg.StudyHelper.dto;

import org.springframework.core.io.Resource;

public record FlashcardChunk(
    String sourceFilename,
    int chunkIndex,
    int startPage,
    int endPage,
    String text,
    Resource pdfResource
) {
    public boolean hasPdfResource() {
        return pdfResource != null;
    }
}
```

Create `FlashcardGenerationPlan.java`:

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record FlashcardGenerationPlan(
    List<FlashcardChunk> chunks,
    int requestCost,
    int estimatedSecondsLow,
    int estimatedSecondsHigh,
    int estimatedCardsLow,
    int estimatedCardsHigh,
    FlashcardGenerationRisk risk
) {
    public boolean highRisk() {
        return risk == FlashcardGenerationRisk.HIGH;
    }
}
```

- [ ] **Step 4: Implement plan service**

Create `FlashcardGenerationPlanService.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardChunk;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationPlan;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationRisk;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class FlashcardGenerationPlanService {

    static final int MAX_PAGES_PER_CHUNK = 2;
    static final int MAX_WORDS_PER_CHUNK = 1500;
    static final int SECONDS_LOW_PER_CHUNK = 8;
    static final int SECONDS_HIGH_PER_CHUNK = 12;
    static final int CARDS_LOW_PER_CHUNK = 5;
    static final int CARDS_HIGH_PER_CHUNK = 15;

    private final DocumentExtractionService documentExtractionService;

    public FlashcardGenerationPlanService(DocumentExtractionService documentExtractionService) {
        this.documentExtractionService = documentExtractionService;
    }

    public FlashcardGenerationPlan plan(List<FileEntry> pdfs, DocumentMode mode) throws IOException {
        if (pdfs == null || pdfs.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one PDF.");
        }
        List<FlashcardChunk> chunks = new ArrayList<>();
        for (FileEntry pdf : pdfs) {
            chunks.addAll(mode == DocumentMode.FULL_PDF ? fullPdfChunks(pdf) : textChunks(pdf));
        }
        int cost = chunks.size();
        return new FlashcardGenerationPlan(
            List.copyOf(chunks),
            cost,
            cost * SECONDS_LOW_PER_CHUNK,
            cost * SECONDS_HIGH_PER_CHUNK,
            cost * CARDS_LOW_PER_CHUNK,
            cost * CARDS_HIGH_PER_CHUNK,
            risk(cost)
        );
    }

    private List<FlashcardChunk> textChunks(FileEntry pdf) throws IOException {
        List<String> pages = documentExtractionService.extractPdfTextPages(pdf);
        List<FlashcardChunk> chunks = new ArrayList<>();
        int chunkIndex = 1;
        int startPage = 1;
        StringBuilder text = new StringBuilder();
        int words = 0;
        for (int i = 0; i < pages.size(); i++) {
            String pageText = pages.get(i) == null ? "" : pages.get(i).strip();
            int pageNumber = i + 1;
            for (String part : splitByWordLimit(pageText)) {
                int partWords = wordCount(part);
                boolean pageLimitReached = pageNumber - startPage >= MAX_PAGES_PER_CHUNK;
                boolean wordLimitReached = words > 0 && words + partWords > MAX_WORDS_PER_CHUNK;
                if (text.length() > 0 && (pageLimitReached || wordLimitReached)) {
                    chunks.add(new FlashcardChunk(pdf.getOriginalFilename(), chunkIndex++, startPage, pageNumber - 1, text.toString().strip(), null));
                    text.setLength(0);
                    words = 0;
                    startPage = pageNumber;
                }
                text.append("Page ").append(pageNumber).append(":\n").append(part).append("\n\n");
                words += partWords;
            }
        }
        if (text.length() > 0) {
            chunks.add(new FlashcardChunk(pdf.getOriginalFilename(), chunkIndex, startPage, pages.size(), text.toString().strip(), null));
        }
        return chunks;
    }

    private List<FlashcardChunk> fullPdfChunks(FileEntry pdf) throws IOException {
        int pages = documentExtractionService.pdfPageCount(pdf);
        List<FlashcardChunk> chunks = new ArrayList<>();
        int chunkIndex = 1;
        for (int start = 1; start <= pages; start += MAX_PAGES_PER_CHUNK) {
            int end = Math.min(start + MAX_PAGES_PER_CHUNK - 1, pages);
            Resource resource = documentExtractionService.loadPdfPageRangeResource(pdf, start, end);
            chunks.add(new FlashcardChunk(pdf.getOriginalFilename(), chunkIndex++, start, end, "", resource));
        }
        return chunks;
    }

    private List<String> splitByWordLimit(String text) {
        if (wordCount(text) <= MAX_WORDS_PER_CHUNK) return List.of(text);
        String[] words = text.split("\\s+");
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;
        for (String word : words) {
            if (count == MAX_WORDS_PER_CHUNK) {
                parts.add(current.toString());
                current.setLength(0);
                count = 0;
            }
            current.append(word).append(' ');
            count++;
        }
        if (current.length() > 0) parts.add(current.toString());
        return parts;
    }

    private int wordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.strip().split("\\s+").length;
    }

    private FlashcardGenerationRisk risk(int cost) {
        if (cost >= 31) return FlashcardGenerationRisk.HIGH;
        if (cost >= 11) return FlashcardGenerationRisk.WARNING;
        return FlashcardGenerationRisk.NORMAL;
    }
}
```

- [ ] **Step 5: Verify plan tests pass**

Run:

```bash
./mvnw -Dtest=FlashcardGenerationPlanServiceTests test
```

Expected: pass.

- [ ] **Step 6: Commit plan service**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardChunk.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardGenerationPlan.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardGenerationRisk.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationPlanService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationPlanServiceTests.java
git commit -m "feat: plan flashcard generation chunks"
```

## Task 4: Chunked AI Generation

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java`

- [ ] **Step 1: Write failing chunk-generation tests**

Add tests:

```java
@Test
void generateChunks_UsesMediumThinkingAndNoFixedCount() {
    when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
        new GeneratedFlashcard("Q", "A")
    ));

    List<GeneratedFlashcard> result = service.generateChunks(List.of(
        new FlashcardChunk("lecture.pdf", 1, 1, 2, "Mitosis has prophase.", null)
    ), null);

    assertThat(result).containsExactly(new GeneratedFlashcard("Q", "A"));
    assertThat(capturedPrompt.get()).contains("every testable detail");
    assertThat(capturedPrompt.get()).doesNotContain("Generate exactly");
    var built = (GoogleGenAiChatOptions) capturedOptionsBuilder.get().build();
    assertThat(built.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.MEDIUM);
    assertThat(built.getThinkingBudget()).isNull();
}

@Test
void generateChunks_RemovesExactDuplicateCardsAcrossChunks() {
    when(callSpec.entity(FlashcardsResponse.class))
        .thenReturn(wrap(new GeneratedFlashcard("Q", "A")))
        .thenReturn(wrap(new GeneratedFlashcard(" Q ", " A "), new GeneratedFlashcard("Q2", "A2")));

    List<GeneratedFlashcard> result = service.generateChunks(List.of(
        new FlashcardChunk("lecture.pdf", 1, 1, 2, "one", null),
        new FlashcardChunk("lecture.pdf", 2, 3, 4, "two", null)
    ), null);

    assertThat(result).containsExactly(
        new GeneratedFlashcard("Q", "A"),
        new GeneratedFlashcard("Q2", "A2")
    );
}
```

Add import:

```java
import com.HendrikHoemberg.StudyHelper.dto.FlashcardChunk;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
```

- [ ] **Step 2: Run failing AI tests**

Run:

```bash
./mvnw -Dtest=AiFlashcardServiceTests test
```

Expected: compile failure for missing `generateChunks`.

- [ ] **Step 3: Implement chunked generation**

Add public method:

```java
public List<GeneratedFlashcard> generateChunks(List<FlashcardChunk> chunks, String additionalInstructions) {
    if (chunks == null || chunks.isEmpty()) {
        throw new IllegalArgumentException("Flashcard generation requires at least one source chunk.");
    }
    List<GeneratedFlashcard> all = new ArrayList<>();
    for (FlashcardChunk chunk : chunks) {
        all.addAll(generateChunk(chunk, additionalInstructions));
    }
    return dedupe(all);
}
```

Add private chunk call:

```java
private List<GeneratedFlashcard> generateChunk(FlashcardChunk chunk, String additionalInstructions) {
    String prompt = buildChunkPrompt(chunk, additionalInstructions);
    Media[] media = chunk.hasPdfResource()
        ? new Media[] { new Media(new MimeType("application", "pdf"), chunk.pdfResource()) }
        : new Media[0];
    FlashcardsResponse response;
    try {
        response = chatClient.prompt()
            .options(GoogleGenAiChatOptions.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .thinkingLevel(GoogleGenAiThinkingLevel.MEDIUM))
            .user(u -> {
                u.text(prompt);
                if (media.length > 0) u.media(media);
            })
            .call()
            .entity(FlashcardsResponse.class);
    } catch (Exception e) {
        throw AiGenerationSupport.failure(log, "FLASHCARDS", "PROVIDER_REQUEST",
            "AI request failed while generating flashcards for " + chunk.sourceFilename() + " pages "
                + chunk.startPage() + "-" + chunk.endPage() + ".", e);
    }
    return validCards(response);
}
```

Extract existing response validation into:

```java
private List<GeneratedFlashcard> validCards(FlashcardsResponse response) {
    List<GeneratedFlashcard> rawList = response == null || response.flashcards() == null
        ? List.of()
        : response.flashcards();
    List<GeneratedFlashcard> valid = new ArrayList<>();
    for (GeneratedFlashcard card : rawList) {
        if (card == null) continue;
        String front = card.frontText() == null ? null : card.frontText().trim();
        String back = card.backText() == null ? null : card.backText().trim();
        if (front == null || front.isBlank() || back == null || back.isBlank()) continue;
        valid.add(new GeneratedFlashcard(front, back));
    }
    if (valid.isEmpty()) {
        throw AiGenerationSupport.failure(log, "FLASHCARDS", "RESPONSE_VALIDATION",
            "AI returned no valid flashcards; please retry.",
            new IllegalStateException("AI response contained no flashcards with both frontText and backText."));
    }
    return valid;
}
```

Add prompt:

```java
private String buildChunkPrompt(FlashcardChunk chunk, String additionalInstructions) {
    String source = chunk.hasPdfResource()
        ? "Attached PDF page range is the source. Filename: " + chunk.sourceFilename()
        : chunk.text();
    return """
        You are a study assistant. Generate flashcards from exactly this source chunk.

        SOURCE:
        %s pages %d-%d, chunk %d.

        TASK:
        Create one concise, self-contained flashcard for every testable detail in this chunk.
        A testable detail is a definition, fact, formula, rule, process step, comparison,
        cause/effect relationship, named concept, exception, caveat, or example that teaches a concept.

        RULES:
        - Do not target a fixed number of flashcards.
        - Do not omit testable details just to keep the deck short.
        - Skip metadata, headers, footers, page numbers, bibliographies, acknowledgements, and layout-only details.
        - Skip vague image references unless the needed information is present in the chunk.
        - Preserve the dominant source language.
        - Avoid duplicates within this chunk.

        CHUNK CONTENT:
        %s
        """.formatted(chunk.sourceFilename(), chunk.startPage(), chunk.endPage(), chunk.chunkIndex(), source)
        + AiInstructionSupport.section(additionalInstructions);
}
```

Add dedupe:

```java
private List<GeneratedFlashcard> dedupe(List<GeneratedFlashcard> cards) {
    List<GeneratedFlashcard> deduped = new ArrayList<>();
    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
    for (GeneratedFlashcard card : cards) {
        String key = (card.frontText().strip() + "\n" + card.backText().strip()).toLowerCase();
        if (seen.add(key)) {
            deduped.add(card);
        }
    }
    return deduped;
}
```

- [ ] **Step 4: Keep old generate tests passing**

Do not delete the old `generate(...)` overloads yet. Existing quiz/study tests may still use them. Task 8 moves the controller to `generateChunks(...)`.

- [ ] **Step 5: Verify AI tests pass**

Run:

```bash
./mvnw -Dtest=AiFlashcardServiceTests test
```

Expected: pass.

- [ ] **Step 6: Commit chunked AI generation**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java
git commit -m "feat: generate flashcards from source chunks"
```

## Task 5: Provider Client Timeout

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/config/GoogleGenAiClientConfig.java`
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/config/GoogleGenAiClientConfigTests.java`

- [ ] **Step 1: Add config class**

Create:

```java
package com.HendrikHoemberg.StudyHelper.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class GoogleGenAiClientConfig {

    static final int PROVIDER_TIMEOUT_MILLIS = 180_000;

    @Bean
    @ConditionalOnMissingBean(Client.class)
    Client googleGenAiClient(GoogleGenAiConnectionProperties properties) {
        Client.Builder builder = Client.builder()
            .httpOptions(HttpOptions.builder().timeout(PROVIDER_TIMEOUT_MILLIS).build());
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.apiKey(properties.getApiKey());
        }
        if (properties.isVertexAi()) {
            builder.vertexAI(true)
                .project(properties.getProjectId())
                .location(properties.getLocation());
        }
        return builder.build();
    }
}
```

- [ ] **Step 2: Add a lightweight config test**

Create:

```java
package com.HendrikHoemberg.StudyHelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleGenAiClientConfigTests {

    @Test
    void providerTimeoutConstant_IsThreeMinutes() {
        assertThat(GoogleGenAiClientConfig.PROVIDER_TIMEOUT_MILLIS).isEqualTo(180_000);
    }
}
```

- [ ] **Step 3: Run config test**

Run:

```bash
./mvnw -Dtest=GoogleGenAiClientConfigTests test
```

Expected: pass.

- [ ] **Step 4: Commit timeout config**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/config/GoogleGenAiClientConfig.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/config/GoogleGenAiClientConfigTests.java
git commit -m "feat: configure Gemini provider timeout"
```

## Task 6: Persisted Generation Jobs

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJobStatus.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJob.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/repository/FlashcardGenerationJobRepository.java`
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJobTests.java`

- [ ] **Step 1: Add job status enum**

```java
package com.HendrikHoemberg.StudyHelper.entity;

public enum FlashcardGenerationJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
```

- [ ] **Step 2: Add job entity**

```java
package com.HendrikHoemberg.StudyHelper.entity;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "flashcard_generation_jobs")
@Getter
@Setter
@NoArgsConstructor
public class FlashcardGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlashcardGenerationJobStatus status = FlashcardGenerationJobStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentMode documentMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FlashcardGenerationDestination destination;

    @Column(nullable = false, length = 1000)
    private String sourceFileIdsCsv;

    private Long existingDeckId;
    private Long newDeckFolderId;

    @Column(length = 255)
    private String newDeckName;

    @Column(length = 1000)
    private String additionalInstructions;

    @Column(nullable = false)
    private int estimatedRequestCost;

    @Column(nullable = false)
    private int chargedRequestCost;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false)
    private int completedChunkCount;

    @Column(nullable = false)
    private int generatedCardCount;

    private Long savedDeckId;

    @Column(length = 1000)
    private String failureMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;
    private Instant finishedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = FlashcardGenerationJobStatus.QUEUED;
    }
}
```

- [ ] **Step 3: Add repository**

```java
package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJob;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJobStatus;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FlashcardGenerationJobRepository extends JpaRepository<FlashcardGenerationJob, Long> {
    Optional<FlashcardGenerationJob> findByIdAndUser(Long id, User user);
    List<FlashcardGenerationJob> findByStatusAndStartedAtBefore(FlashcardGenerationJobStatus status, Instant startedBefore);
}
```

- [ ] **Step 4: Add entity smoke test**

Create `src/test/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJobTests.java`:

```java
package com.HendrikHoemberg.StudyHelper.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlashcardGenerationJobTests {

@Test
void jobDefaultsToQueuedAndSetsCreatedAt() {
    FlashcardGenerationJob job = new FlashcardGenerationJob();
    job.prePersist();

    assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.QUEUED);
    assertThat(job.getCreatedAt()).isNotNull();
}
}
```

- [ ] **Step 5: Run job tests**

Run:

```bash
./mvnw -Dtest=FlashcardGenerationJobTests test
```

Expected: pass.

- [ ] **Step 6: Commit job persistence**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJobStatus.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJob.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/repository/FlashcardGenerationJobRepository.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/entity/FlashcardGenerationJobTests.java
git commit -m "feat: persist flashcard generation jobs"
```

## Task 7: Background Job Service

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/config/FlashcardGenerationAsyncConfig.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationJobService.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationJobServiceTests.java`

- [ ] **Step 1: Add async executor config**

```java
package com.HendrikHoemberg.StudyHelper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class FlashcardGenerationAsyncConfig {

    @Bean
    TaskExecutor flashcardGenerationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("flashcard-gen-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: Write failing job accept test**

Add to `FlashcardGenerationJobServiceTests`:

```java
@Test
void acceptJob_ChargesQuotaAndPersistsQueuedJob() {
    when(jobRepository.save(any(FlashcardGenerationJob.class))).thenAnswer(inv -> {
        FlashcardGenerationJob job = inv.getArgument(0);
        job.setId(44L);
        return job;
    });

    FlashcardGenerationJob job = service.acceptJob(
        user,
        List.of(99L),
        DocumentMode.TEXT,
        FlashcardGenerationDestination.NEW_DECK,
        null,
        10L,
        "Generated",
        "focus",
        plan
    );

    assertThat(job.getId()).isEqualTo(44L);
    assertThat(job.getStatus()).isEqualTo(FlashcardGenerationJobStatus.QUEUED);
    assertThat(job.getEstimatedRequestCost()).isEqualTo(plan.requestCost());
    assertThat(job.getChargedRequestCost()).isEqualTo(plan.requestCost());
    verify(aiRequestQuotaService).checkAndRecord(user, plan.requestCost());
}
```

- [ ] **Step 3: Implement `FlashcardGenerationJobService` constructor and accept method**

```java
@Service
public class FlashcardGenerationJobService {

    private final FlashcardGenerationJobRepository jobRepository;
    private final AiRequestQuotaService aiRequestQuotaService;
    private final TaskExecutor flashcardGenerationTaskExecutor;

    public FlashcardGenerationJobService(FlashcardGenerationJobRepository jobRepository,
                                         AiRequestQuotaService aiRequestQuotaService,
                                         TaskExecutor flashcardGenerationTaskExecutor) {
        this.jobRepository = jobRepository;
        this.aiRequestQuotaService = aiRequestQuotaService;
        this.flashcardGenerationTaskExecutor = flashcardGenerationTaskExecutor;
    }

    @Transactional
    public FlashcardGenerationJob acceptJob(User user,
                                            List<Long> sourceFileIds,
                                            DocumentMode documentMode,
                                            FlashcardGenerationDestination destination,
                                            Long existingDeckId,
                                            Long newDeckFolderId,
                                            String newDeckName,
                                            String additionalInstructions,
                                            FlashcardGenerationPlan plan) {
        aiRequestQuotaService.checkAndRecord(user, plan.requestCost());
        FlashcardGenerationJob job = new FlashcardGenerationJob();
        job.setUser(user);
        job.setSourceFileIdsCsv(sourceFileIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        job.setDocumentMode(documentMode);
        job.setDestination(destination);
        job.setExistingDeckId(existingDeckId);
        job.setNewDeckFolderId(newDeckFolderId);
        job.setNewDeckName(newDeckName);
        job.setAdditionalInstructions(additionalInstructions == null || additionalInstructions.isBlank() ? null : additionalInstructions.strip());
        job.setEstimatedRequestCost(plan.requestCost());
        job.setChargedRequestCost(plan.requestCost());
        job.setChunkCount(plan.chunks().size());
        FlashcardGenerationJob saved = jobRepository.save(job);
        flashcardGenerationTaskExecutor.execute(() -> runJob(saved.getId()));
        return saved;
    }

    void runJob(Long jobId) {
        // Fill in after worker dependencies are added in the next step.
    }
}
```

- [ ] **Step 4: Add worker dependencies and run logic**

Extend constructor with:

```java
private final FileEntryService fileEntryService;
private final FlashcardGenerationPlanService planService;
private final AiFlashcardService aiFlashcardService;
private final FlashcardGenerationPersistenceService persistenceService;
```

Implement:

```java
@Transactional
public FlashcardGenerationJob getJob(Long jobId, User user) {
    return jobRepository.findByIdAndUser(jobId, user)
        .orElseThrow(() -> new ResourceNotFoundException("Generation job not found"));
}

void runJob(Long jobId) {
    FlashcardGenerationJob job = markRunning(jobId);
    try {
        User user = job.getUser();
        List<FileEntry> files = parseFileIds(job.getSourceFileIdsCsv()).stream()
            .map(id -> fileEntryService.getByIdAndUser(id, user))
            .toList();
        FlashcardGenerationPlan plan = planService.plan(files, job.getDocumentMode());
        List<GeneratedFlashcard> generated = aiFlashcardService.generateChunks(plan.chunks(), job.getAdditionalInstructions());
        Deck deck = persistenceService.saveGeneratedCards(job.getDestination(), job.getExistingDeckId(), job.getNewDeckFolderId(), job.getNewDeckName(), user, generated);
        markSucceeded(jobId, deck.getId(), generated.size());
    } catch (Exception ex) {
        markFailed(jobId, ex.getMessage() == null ? "Flashcard generation failed." : ex.getMessage());
    }
}
```

Add transactional helpers:

```java
@Transactional
FlashcardGenerationJob markRunning(Long jobId) {
    FlashcardGenerationJob job = jobRepository.findById(jobId).orElseThrow();
    job.setStatus(FlashcardGenerationJobStatus.RUNNING);
    job.setStartedAt(Instant.now());
    return job;
}

@Transactional
void markSucceeded(Long jobId, Long deckId, int generatedCardCount) {
    FlashcardGenerationJob job = jobRepository.findById(jobId).orElseThrow();
    job.setStatus(FlashcardGenerationJobStatus.SUCCEEDED);
    job.setSavedDeckId(deckId);
    job.setGeneratedCardCount(generatedCardCount);
    job.setCompletedChunkCount(job.getChunkCount());
    job.setFinishedAt(Instant.now());
}

@Transactional
void markFailed(Long jobId, String message) {
    FlashcardGenerationJob job = jobRepository.findById(jobId).orElseThrow();
    job.setStatus(FlashcardGenerationJobStatus.FAILED);
    job.setFailureMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
    job.setFinishedAt(Instant.now());
}

private List<Long> parseFileIds(String csv) {
    if (csv == null || csv.isBlank()) return List.of();
    return java.util.Arrays.stream(csv.split(",")).map(Long::valueOf).toList();
}
```

- [ ] **Step 5: Add timeout method**

```java
@Transactional
public int failTimedOutJobs(Instant olderThan) {
    List<FlashcardGenerationJob> stale = jobRepository.findByStatusAndStartedAtBefore(FlashcardGenerationJobStatus.RUNNING, olderThan);
    for (FlashcardGenerationJob job : stale) {
        job.setStatus(FlashcardGenerationJobStatus.FAILED);
        job.setFailureMessage("Flashcard generation timed out.");
        job.setFinishedAt(Instant.now());
    }
    return stale.size();
}
```

- [ ] **Step 6: Run job service tests**

Run:

```bash
./mvnw -Dtest=FlashcardGenerationJobServiceTests test
```

Expected: pass.

- [ ] **Step 7: Commit job service**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/config/FlashcardGenerationAsyncConfig.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationJobService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/FlashcardGenerationJobServiceTests.java
git commit -m "feat: run flashcard generation as background jobs"
```

## Task 8: Controller Estimate, Start, and Status Flow

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationController.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationControllerTests.java`

- [ ] **Step 1: Update controller constructor dependencies**

Add:

```java
private final FlashcardGenerationPlanService planService;
private final FlashcardGenerationJobService jobService;
```

Constructor parameters:

```java
FlashcardGenerationPlanService planService,
FlashcardGenerationJobService jobService
```

- [ ] **Step 2: Write failing controller tests**

Add:

```java
@Test
void estimateGenerate_ValidSelection_ReturnsEstimateFragment() throws Exception {
    when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
    when(documentExtractionService.isSupported(pdf)).thenReturn(true);
    FlashcardGenerationPlan plan = new FlashcardGenerationPlan(List.of(
        new FlashcardChunk("lecture.pdf", 1, 1, 2, "text", null)
    ), 1, 8, 12, 5, 15, FlashcardGenerationRisk.NORMAL);
    when(planService.plan(List.of(pdf), DocumentMode.TEXT)).thenReturn(plan);

    String view = controller.estimateGenerate(List.of(99L), DocumentMode.TEXT, new ExtendedModelMap(), () -> "alice", new MockHttpServletResponse());

    assertThat(view).isEqualTo("fragments/flashcard-generator :: estimate");
}

@Test
void generate_StartsJobAndReturnsProgressFragment() throws Exception {
    when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
    when(documentExtractionService.isSupported(pdf)).thenReturn(true);
    FlashcardGenerationPlan plan = new FlashcardGenerationPlan(List.of(
        new FlashcardChunk("lecture.pdf", 1, 1, 2, "text", null)
    ), 1, 8, 12, 5, 15, FlashcardGenerationRisk.NORMAL);
    when(planService.plan(List.of(pdf), DocumentMode.TEXT)).thenReturn(plan);
    FlashcardGenerationJob job = new FlashcardGenerationJob();
    job.setId(77L);
    job.setStatus(FlashcardGenerationJobStatus.QUEUED);
    when(jobService.acceptJob(eq(user), eq(List.of(99L)), eq(DocumentMode.TEXT), eq(FlashcardGenerationDestination.NEW_DECK), eq(null), eq(10L), eq("Deck"), eq(null), eq(plan))).thenReturn(job);

    String view = controller.generate(List.of(99L), DocumentMode.TEXT, null, FlashcardGenerationDestination.NEW_DECK, null, 10L, "Deck", null, new ExtendedModelMap(), () -> "alice", new MockHttpServletResponse(), "true");

    assertThat(view).isEqualTo("fragments/flashcard-generator :: progress");
}
```

- [ ] **Step 3: Replace synchronous `generate` signature**

Remove `cardCount` parameter from `generate(...)`. Add `highRiskAcknowledged`:

```java
@RequestParam(required = false, defaultValue = "false") boolean highRiskAcknowledged
```

Inside generate:

```java
List<FileEntry> files = validateAndResolveFiles(fileIds, destination, existingDeckId, newDeckFolderId, newDeckName, user);
List<Long> selectedIds = safeFileIds(fileIds);
FlashcardGenerationPlan plan = planService.plan(files, documentMode);
if (plan.highRisk() && !highRiskAcknowledged) {
    throw new IllegalArgumentException("Please confirm the high-risk generation warning before continuing.");
}
FlashcardGenerationJob job = jobService.acceptJob(user, selectedIds, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, additionalInstructions, plan);
model.addAttribute("generationJob", job);
model.addAttribute("generationPlan", plan);
response.addHeader("HX-Trigger", "refresh-quota");
return "fragments/flashcard-generator :: progress";
```

- [ ] **Step 4: Add estimate endpoint**

```java
@PostMapping("/flashcards/generate/estimate")
public String estimateGenerate(@RequestParam(name = "fileId", required = false) List<Long> fileIds,
                               @RequestParam(defaultValue = "TEXT") DocumentMode documentMode,
                               Model model,
                               Principal principal,
                               HttpServletResponse response) throws Exception {
    User user = userService.getByUsername(principal.getName());
    List<FileEntry> files = validateAndResolveFiles(fileIds, null, null, null, null, user, false);
    FlashcardGenerationPlan plan = planService.plan(files, documentMode);
    model.addAttribute("generationPlan", plan);
    return "fragments/flashcard-generator :: estimate";
}
```

Use a helper overload so estimate validates selected PDFs but does not require destination.

- [ ] **Step 5: Add status endpoint**

```java
@GetMapping("/flashcards/generate/jobs/{jobId}")
public String generationStatus(@PathVariable Long jobId,
                               Model model,
                               Principal principal,
                               HttpServletResponse response) {
    User user = userService.getByUsername(principal.getName());
    FlashcardGenerationJob job = jobService.getJob(jobId, user);
    model.addAttribute("generationJob", job);
    if (job.getStatus() == FlashcardGenerationJobStatus.SUCCEEDED && job.getSavedDeckId() != null) {
        response.setHeader("HX-Redirect", "/decks/" + job.getSavedDeckId());
    }
    if (job.getStatus() == FlashcardGenerationJobStatus.FAILED) {
        response.addHeader("HX-Trigger", "refresh-quota");
    }
    return "fragments/flashcard-generator :: progress";
}
```

- [ ] **Step 6: Run controller tests**

Run:

```bash
./mvnw -Dtest=FlashcardGenerationControllerTests test
```

Expected: pass after updating old synchronous assertions to expect job flow.

- [ ] **Step 7: Commit controller job flow**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationController.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationControllerTests.java
git commit -m "feat: start flashcard generation jobs from controller"
```

## Task 9: Generator Template and JavaScript

**Files:**
- Modify: `src/main/resources/templates/fragments/flashcard-generator.html`
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_de.properties`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java`

- [ ] **Step 1: Add failing UI regression assertions**

Update or replace the card-count stepper test:

```java
@Test
void flashcardGeneratorShowsEstimateAndProgressHooksInsteadOfCardCountStepper() throws IOException {
    String template = resource("templates/fragments/flashcard-generator.html");
    String appJs = resource("static/js/app.js");

    assertThat(template)
        .doesNotContain("name=\"cardCount\"")
        .contains("id=\"ai-generation-estimate\"")
        .contains("th:fragment=\"estimate\"")
        .contains("th:fragment=\"progress\"")
        .contains("name=\"highRiskAcknowledged\"");
    assertThat(appJs).contains("/flashcards/generate/estimate");
}
```

- [ ] **Step 2: Add estimate fragment**

In `flashcard-generator.html`, remove the `sh-setting-row` card count block. Add inside step 2:

```html
<div id="ai-generation-estimate"
     th:replace="~{fragments/flashcard-generator :: estimate}">
</div>

<label class="sh-quiz-warn-banner sh-ai-high-risk-ack" th:if="${generationPlan != null and generationPlan.highRisk()}">
    <input type="checkbox" name="highRiskAcknowledged" value="true">
    <span th:text="#{flashcard-gen.estimate.high-risk-ack}">I understand this generation may use significant quota and take several minutes.</span>
</label>
```

Add fragment:

```html
<div th:fragment="estimate" id="ai-generation-estimate" class="sh-ai-estimate">
    <div th:if="${generationPlan == null}" class="sh-empty-hint" th:text="#{flashcard-gen.estimate.select-sources}">
        Select sources to estimate AI usage.
    </div>
    <div th:if="${generationPlan != null}">
        <div class="sh-setting-label" th:text="#{flashcard-gen.estimate.title}">Generation estimate</div>
        <div class="sh-setting-hint"
             th:text="#{flashcard-gen.estimate.summary(${generationPlan.requestCost()}, ${generationPlan.estimatedSecondsLow()}, ${generationPlan.estimatedSecondsHigh()}, ${generationPlan.estimatedCardsLow()}, ${generationPlan.estimatedCardsHigh()})}">
            Uses 1 AI request.
        </div>
        <div th:if="${#strings.toString(generationPlan.risk()) == 'WARNING' or #strings.toString(generationPlan.risk()) == 'HIGH'}"
             class="sh-quiz-warn-banner">
            <iconify-icon icon="lucide:alert-triangle"></iconify-icon>
            <span th:text="#{flashcard-gen.estimate.warning}">Large PDFs can take longer and may produce uneven cards. Split by chapter for best results.</span>
        </div>
    </div>
</div>
```

Add progress fragment:

```html
<div th:fragment="progress" class="sh-card sh-ai-generation-progress"
     th:attr="hx-get=@{/flashcards/generate/jobs/{id}(id=${generationJob.id})}"
     hx-trigger="every 2s"
     hx-target="this"
     hx-swap="outerHTML">
    <div class="sh-card-body">
        <div class="sh-section-title" th:text="#{flashcard-gen.progress.title}">Generating flashcards</div>
        <p class="sh-setting-hint"
           th:text="#{flashcard-gen.progress.detail(${generationJob.completedChunkCount}, ${generationJob.chunkCount})}">
            Processing chunks...
        </p>
        <div th:if="${#strings.toString(generationJob.status) == 'FAILED'}" class="sh-alert sh-alert-danger">
            <div class="sh-alert-body" th:text="${generationJob.failureMessage}">Generation failed.</div>
        </div>
    </div>
</div>
```

- [ ] **Step 3: Add JS estimate refresh**

In `app.js`, add:

```javascript
function refreshFlashcardGenerationEstimate(form) {
    if (!form) return;
    const target = form.querySelector('#ai-generation-estimate');
    if (!target) return;
    fetch('/flashcards/generate/estimate', {
        method: 'POST',
        headers: {
            ...getCsrfHeaders(),
            'HX-Request': 'true'
        },
        body: new URLSearchParams(new FormData(form))
    })
        .then((response) => response.text())
        .then((html) => {
            target.outerHTML = html;
            const freshTarget = document.getElementById('ai-generation-estimate');
            if (window.htmx && freshTarget) {
                htmx.process(freshTarget);
            }
        })
        .catch(() => {});
}

document.body.addEventListener('change', (event) => {
    const form = event.target.closest?.('form.sh-ai-flashcard-form');
    if (!form) return;
    if (event.target.matches('input[name="fileId"], input[name="documentMode"], .sh-ai-global-pdf-mode input[name="documentMode"]')) {
        refreshFlashcardGenerationEstimate(form);
    }
});
```

- [ ] **Step 4: Add messages**

English:

```properties
flashcard-gen.estimate.select-sources=Select sources to estimate AI usage.
flashcard-gen.estimate.title=Generation estimate
flashcard-gen.estimate.summary=Uses {0} AI requests · about {1}-{2}s · roughly {3}-{4} cards
flashcard-gen.estimate.warning=Large PDFs can take several minutes and may produce duplicate or uneven cards. For best results, split long PDFs by chapter or lecture.
flashcard-gen.estimate.high-risk-ack=I understand this generation may use significant quota and take several minutes.
flashcard-gen.progress.title=Generating flashcards
flashcard-gen.progress.detail=Processed {0} of {1} chunks.
```

German:

```properties
flashcard-gen.estimate.select-sources=Quellen auswählen, um die KI-Nutzung zu schätzen.
flashcard-gen.estimate.title=Generierungsschätzung
flashcard-gen.estimate.summary=Verwendet {0} KI-Anfragen · ca. {1}-{2}s · ungefähr {3}-{4} Karten
flashcard-gen.estimate.warning=Große PDFs können mehrere Minuten dauern und doppelte oder uneinheitliche Karten erzeugen. Teile lange PDFs für beste Ergebnisse nach Kapitel oder Vorlesung.
flashcard-gen.estimate.high-risk-ack=Ich verstehe, dass diese Generierung viel Kontingent nutzen und mehrere Minuten dauern kann.
flashcard-gen.progress.title=Karteikarten werden generiert
flashcard-gen.progress.detail={0} von {1} Abschnitten verarbeitet.
```

- [ ] **Step 5: Run UI regression tests**

Run:

```bash
./mvnw -Dtest=UiResourceRegressionTests test
```

Expected: pass.

- [ ] **Step 6: Commit UI changes**

```bash
git add src/main/resources/templates/fragments/flashcard-generator.html \
        src/main/resources/static/js/app.js \
        src/main/resources/messages.properties \
        src/main/resources/messages_de.properties \
        src/test/java/com/HendrikHoemberg/StudyHelper/UiResourceRegressionTests.java
git commit -m "feat: show flashcard generation estimates and progress"
```

## Task 10: Final Integration and Cleanup

**Files:**
- Verify files changed in Tasks 1-9.

- [ ] **Step 1: Run flashcard-related tests**

Run:

```bash
./mvnw -Dtest=AiFlashcardServiceTests,FlashcardGenerationPlanServiceTests,FlashcardGenerationJobServiceTests,FlashcardGenerationControllerTests,UiResourceRegressionTests test
```

Expected: pass.

- [ ] **Step 2: Run full test suite**

Run:

```bash
./mvnw test
```

Expected: pass.

- [ ] **Step 3: Run application smoke**

Run:

```bash
./mvnw spring-boot:run
```

Expected: app starts on `http://localhost:8080`.

Manual smoke:

```text
1. Open /flashcards/generate.
2. Select one PDF.
3. Confirm estimate appears and no card count input is visible.
4. Choose destination.
5. Start generation.
6. Confirm progress view appears immediately.
7. Confirm quota widget refreshes.
8. Confirm successful job redirects/swaps to the generated deck.
```

- [ ] **Step 4: Check no legacy count UI remains**

Run:

```bash
rg -n "cardCount|card-count|Generate exactly|DEFAULT_FLASHCARD_COUNT" src/main/java src/main/resources src/test/java
```

Expected: no generator UI/controller references to `cardCount`. Existing explicit-count tests may remain only if they cover old service overloads that are still intentionally retained.

- [ ] **Step 5: Commit final cleanup**

```bash
git add .
git commit -m "test: verify comprehensive flashcard generation flow"
```

## Self-Review

Spec coverage:

- User no longer chooses card count: Task 8 removes controller `cardCount`, Task 9 removes UI stepper.
- Every testable detail via chunking: Task 3 chunks source material, Task 4 prompt generates every testable detail.
- Predictable quota: Task 1 amount quota, Task 3 request cost, Task 7 charges accepted job cost.
- Large-PDF warning: Task 3 risk levels, Task 9 warning and acknowledgement.
- Timeout safety: Task 5 provider timeout, Task 7 background jobs, Task 8 progress polling.
- Gemini 3.1 Flash-Lite thinking levels: Task 4 sets `MEDIUM`, Task 4/plan reserves `HIGH` for audit.
- Testing: Tasks include unit, controller, UI regression, and full suite commands.

No placeholder scan:

- The plan contains no placeholder markers or unbounded deferred implementation steps.
- Verified mode is documented as outside this implementation and is not required by any implementation step.

Type consistency:

- DTO names used by tests and services match: `FlashcardChunk`, `FlashcardGenerationPlan`, `FlashcardGenerationRisk`.
- Job names match: `FlashcardGenerationJob`, `FlashcardGenerationJobStatus`, `FlashcardGenerationJobRepository`, `FlashcardGenerationJobService`.
- Thinking option uses `GoogleGenAiThinkingLevel.MEDIUM`, not `thinkingBudget`.
