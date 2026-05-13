# AI Generation Instructions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional per-request AI generation instructions for flashcard, quiz, and exam generation through visible secondary buttons that validate before opening an instructions modal.

**Architecture:** Backend support is split into prompt support, preflight validation, and controller pass-through. The preflight routes reuse the same validation path as real generation but stop before quota recording and AI calls. The frontend uses the existing global dialog component, extended with textarea support, and submits the original HTMX forms after successful preflight.

**Tech Stack:** Java 21, Spring MVC, Thymeleaf fragments, HTMX, vanilla JavaScript, JUnit 5, Mockito, AssertJ, Maven Wrapper.

---

## File Structure

- Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiInstructionSupport.java`
  - Package-private helper that trims, caps, and renders the optional prompt section.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java`
  - Add instruction-aware overload and append the user-instruction section to the prompt.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiQuizService.java`
  - Add instruction-aware overload and append the user-instruction section to the prompt.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java`
  - Add instruction-aware generation overload only. Do not change grading.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationController.java`
  - Add `additionalInstructions` to generation, add `/flashcards/generate/preflight`, and extract reusable validation/building helpers.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/controller/StudyController.java`
  - Add `additionalInstructions` to `/study/session`, add `/study/session/preflight`, and extract quiz validation/source-building helpers.
- Modify `src/main/java/com/HendrikHoemberg/StudyHelper/controller/ExamController.java`
  - Accept additional instructions for delegated exam generation and expose a preflight method for study setup.
- Modify `src/main/resources/templates/fragments/dialog.html`
  - Add a textarea element to the shared dialog.
- Modify `src/main/resources/static/js/app.js`
  - Add textarea dialog support, secondary-button preflight handling, and instruction submission helpers for the flashcard generator.
- Modify `src/main/resources/static/js/study-wizard.js`
  - Add secondary-button preflight handling for quiz and exam wizard submissions.
- Modify `src/main/resources/templates/fragments/flashcard-generator.html`
  - Add hidden `additionalInstructions` and a visible secondary button.
- Modify `src/main/resources/templates/fragments/study-setup.html`
  - Add hidden `additionalInstructions` and a visible secondary button.
- Modify `src/main/resources/static/css/styles.css`
  - Add minimal textarea/dialog layout styles if existing input styles are not enough.
- Test files:
  - `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java`
  - `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiQuizServiceTests.java`
  - `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java`
  - `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationControllerTests.java`
  - `src/test/java/com/HendrikHoemberg/StudyHelper/controller/StudyControllerTests.java`

---

### Task 1: AI Prompt Instruction Support

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiInstructionSupport.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiQuizService.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiQuizServiceTests.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java`

- [ ] **Step 1: Add failing flashcard prompt tests**

Add these tests to `AiFlashcardServiceTests`:

```java
@Test
void generate_WithAdditionalInstructions_AppendsUserInstructionsSection() {
    when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
        new GeneratedFlashcard("Front 1", "Back 1"),
        new GeneratedFlashcard("Front 2", "Back 2")
    ));

    service.generate(new TextDocument("notes.txt", "topic"), " focus on the HTML examples ");

    assertThat(capturedPrompt.get()).contains("USER INSTRUCTIONS:");
    assertThat(capturedPrompt.get()).contains("focus on the HTML examples");
    assertThat(capturedPrompt.get()).contains("Follow them when they are compatible with the rules above");
}

@Test
void generate_BlankAdditionalInstructions_OmitsUserInstructionsSection() {
    when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
        new GeneratedFlashcard("Front 1", "Back 1"),
        new GeneratedFlashcard("Front 2", "Back 2")
    ));

    service.generate(new TextDocument("notes.txt", "topic"), "   ");

    assertThat(capturedPrompt.get()).doesNotContain("USER INSTRUCTIONS:");
}

@Test
void generate_LongAdditionalInstructions_CapsUserInstructionsAtOneThousandCharacters() {
    when(callSpec.entity(FlashcardsResponse.class)).thenReturn(wrap(
        new GeneratedFlashcard("Front 1", "Back 1"),
        new GeneratedFlashcard("Front 2", "Back 2")
    ));

    service.generate(new TextDocument("notes.txt", "topic"), "x".repeat(1100));

    assertThat(capturedPrompt.get()).contains("x".repeat(1000));
    assertThat(capturedPrompt.get()).doesNotContain("x".repeat(1001));
}
```

- [ ] **Step 2: Add failing quiz prompt tests**

Add these tests to `AiQuizServiceTests`:

```java
@Test
void generate_WithAdditionalInstructions_AppendsUserInstructionsSection() {
    when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
        mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
    ));

    service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY, "focus on diagrams");

    assertThat(capturedPrompt.get()).contains("USER INSTRUCTIONS:");
    assertThat(capturedPrompt.get()).contains("focus on diagrams");
}

@Test
void generate_BlankAdditionalInstructions_OmitsUserInstructionsSection() {
    when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
        mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
    ));

    service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY, "");

    assertThat(capturedPrompt.get()).doesNotContain("USER INSTRUCTIONS:");
}
```

- [ ] **Step 3: Add failing exam generation prompt tests**

Add these tests to `AiExamServiceTests`:

```java
@Test
void generate_WithAdditionalInstructions_AppendsUserInstructionsSection() {
    when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

    service.generate(cards(2), List.of(), 3, ExamQuestionSize.MEDIUM, "focus on implementation tradeoffs");

    assertThat(capturedPrompt.get()).contains("USER INSTRUCTIONS:");
    assertThat(capturedPrompt.get()).contains("focus on implementation tradeoffs");
}

@Test
void grade_DoesNotIncludeGenerationInstructionsSection() {
    var perQ = new ExamGradingResult.PerQuestion(80, "OK.");
    var overall = new ExamGradingResult.Overall(80, List.of(), List.of(), List.of(), List.of());
    when(callSpec.entity(ExamGradingResult.class))
        .thenReturn(new ExamGradingResult(List.of(perQ), overall));

    service.grade(List.of(new ExamQuestion("Q1", "H1")), Map.of(0, "A1"), ExamQuestionSize.SHORT);

    assertThat(capturedPrompt.get()).doesNotContain("USER INSTRUCTIONS:");
}
```

- [ ] **Step 4: Run service tests to verify they fail**

Run:

```bash
./mvnw -Dtest=AiFlashcardServiceTests,AiQuizServiceTests,AiExamServiceTests test
```

Expected: compilation fails because the instruction-aware overloads do not exist.

- [ ] **Step 5: Create the prompt helper**

Create `AiInstructionSupport.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

final class AiInstructionSupport {

    static final int MAX_LENGTH = 1_000;

    private AiInstructionSupport() {
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.strip();
        if (trimmed.isBlank()) {
            return "";
        }
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }

    static String section(String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) {
            return "";
        }
        return "\n\nUSER INSTRUCTIONS:\n"
            + "The user provided these additional preferences for this generation. "
            + "Follow them when they are compatible with the rules above, the source material, "
            + "and the required JSON schema:\n"
            + normalized
            + "\n";
    }
}
```

- [ ] **Step 6: Update flashcard service**

In `AiFlashcardService`, keep the existing method and add an overload:

```java
public List<GeneratedFlashcard> generate(DocumentInput document) {
    return generate(document, null);
}

public List<GeneratedFlashcard> generate(DocumentInput document, String additionalInstructions) {
```

Replace the current `public List<GeneratedFlashcard> generate(DocumentInput document)` body with the new overload body. Change prompt creation from:

```java
String prompt = buildPrompt(docContent, pdfListing);
```

to:

```java
String prompt = buildPrompt(docContent, pdfListing, additionalInstructions);
```

Change `buildPrompt` signature and return expression:

```java
private String buildPrompt(String docContent, String pdfListing, String additionalInstructions) {
```

Append the helper result after the existing formatted prompt:

```java
        ).formatted(MAX_FLASHCARDS, docSection, pdfSection)
            + AiInstructionSupport.section(additionalInstructions);
```

- [ ] **Step 7: Update quiz service**

In `AiQuizService`, keep the existing method and add an overload:

```java
public List<QuizQuestion> generate(
        List<Flashcard> flashcards,
        List<DocumentInput> documents,
        int count,
        QuizQuestionMode mode,
        Difficulty difficulty) {
    return generate(flashcards, documents, count, mode, difficulty, null);
}

public List<QuizQuestion> generate(
        List<Flashcard> flashcards,
        List<DocumentInput> documents,
        int count,
        QuizQuestionMode mode,
        Difficulty difficulty,
        String additionalInstructions) {
```

Change prompt creation to:

```java
String prompt = buildPrompt(cardContent, docContent, pdfListing, count, mode, difficulty, mcqCount, tfCount, additionalInstructions);
```

Change `buildPrompt` signature to include `String additionalInstructions`, and append:

```java
        ).formatted(count, modeDescription, difficultyInstruction, count, typeRules, cardSection, docSection, pdfSection)
            + AiInstructionSupport.section(additionalInstructions);
```

- [ ] **Step 8: Update exam generation service**

In `AiExamService`, keep the existing generation method and add an overload:

```java
public List<ExamQuestion> generate(
        List<Flashcard> flashcards,
        List<DocumentInput> documents,
        int questionCount,
        ExamQuestionSize size) {
    return generate(flashcards, documents, questionCount, size, null);
}

public List<ExamQuestion> generate(
        List<Flashcard> flashcards,
        List<DocumentInput> documents,
        int questionCount,
        ExamQuestionSize size,
        String additionalInstructions) {
```

Change prompt creation to:

```java
String prompt = buildGenerationPrompt(cardContent, docContent, pdfListing, questionCount, size, additionalInstructions);
```

Change `buildGenerationPrompt` signature to include `String additionalInstructions`, and append:

```java
        ).formatted(count, sizeInstruction, count, cardSection, docSection, pdfSection)
            + AiInstructionSupport.section(additionalInstructions);
```

Do not change `grade`.

- [ ] **Step 9: Run service tests to verify they pass**

Run:

```bash
./mvnw -Dtest=AiFlashcardServiceTests,AiQuizServiceTests,AiExamServiceTests test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiInstructionSupport.java src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java src/main/java/com/HendrikHoemberg/StudyHelper/service/AiQuizService.java src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java src/test/java/com/HendrikHoemberg/StudyHelper/service/AiQuizServiceTests.java src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java
git commit -m "feat: add AI generation instruction prompt support"
```

---

### Task 2: Flashcard Preflight and Instruction Pass-Through

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationController.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationControllerTests.java`

- [ ] **Step 1: Add failing controller tests**

Add these imports to `FlashcardGenerationControllerTests` if they are missing:

```java
import static org.mockito.ArgumentMatchers.isNull;
```

Add these tests:

```java
@Test
void preflight_NewDeckDuplicateName_ReturnsErrorBeforeAiOrQuota() throws Exception {
    doThrow(new IllegalArgumentException("A deck named \"lecture\" already exists in this folder."))
        .when(persistenceService).validateDestination(FlashcardGenerationDestination.NEW_DECK, null, 10L, "lecture", user);
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletResponse response = new MockHttpServletResponse();

    String view = controller.preflight(
        99L,
        DocumentMode.TEXT,
        FlashcardGenerationDestination.NEW_DECK,
        null,
        10L,
        "lecture",
        model,
        () -> "alice",
        response
    );

    assertThat(view).isEqualTo("fragments/ai-generation-error :: aiGenerationError");
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(model.get("aiErrorMessage")).isEqualTo("A deck named \"lecture\" already exists in this folder.");
    verify(aiRequestQuotaService, never()).checkAndRecord(any());
    verify(aiFlashcardService, never()).generate(any(DocumentInput.class), any());
}

@Test
void preflight_ValidTextMode_ReturnsNoContentWithoutQuotaOrAi() throws Exception {
    when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
    when(documentExtractionService.isSupported(pdf)).thenReturn(true);
    when(documentExtractionService.extractText(pdf)).thenReturn("Lecture text");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String view = controller.preflight(
        99L,
        DocumentMode.TEXT,
        FlashcardGenerationDestination.EXISTING_DECK,
        20L,
        null,
        null,
        new ExtendedModelMap(),
        () -> "alice",
        response
    );

    assertThat(view).isNull();
    assertThat(response.getStatus()).isEqualTo(204);
    verify(aiRequestQuotaService, never()).checkAndRecord(any());
    verify(aiFlashcardService, never()).generate(any(DocumentInput.class), any());
}

@Test
void generate_WithAdditionalInstructions_PassesInstructionsToAi() throws Exception {
    when(fileEntryService.getByIdAndUser(99L, user)).thenReturn(pdf);
    when(documentExtractionService.isSupported(pdf)).thenReturn(true);
    when(documentExtractionService.extractText(pdf)).thenReturn("Lecture text");
    when(aiFlashcardService.generate(any(DocumentInput.class), eq("focus on HTML"))).thenReturn(List.of(new GeneratedFlashcard("Q", "A")));
    when(persistenceService.saveGeneratedCards(eq(FlashcardGenerationDestination.EXISTING_DECK), eq(20L), eq(null), eq(null), eq(user), any())).thenReturn(deck);
    when(deckService.getDeck(20L, user)).thenReturn(deck);

    String view = controller.generate(
        99L,
        DocumentMode.TEXT,
        FlashcardGenerationDestination.EXISTING_DECK,
        20L,
        null,
        null,
        "focus on HTML",
        new ExtendedModelMap(),
        () -> "alice",
        new MockHttpServletResponse(),
        "true"
    );

    assertThat(view).isEqualTo("fragments/deck :: deckDetail");
    verify(aiFlashcardService).generate(any(DocumentInput.class), eq("focus on HTML"));
}
```

- [ ] **Step 2: Run flashcard controller tests to verify they fail**

Run:

```bash
./mvnw -Dtest=FlashcardGenerationControllerTests test
```

Expected: compilation fails because `preflight` and the new `generate` parameter do not exist.

- [ ] **Step 3: Add flashcard preflight route and shared validation helper**

In `FlashcardGenerationController`, add `additionalInstructions` before `Model model` in `generate`:

```java
@RequestParam(required = false) String additionalInstructions,
```

Change the AI call:

```java
List<GeneratedFlashcard> generated = aiFlashcardService.generate(input, additionalInstructions);
```

Add this preflight route:

```java
@PostMapping("/flashcards/generate/preflight")
public String preflight(@RequestParam(required = false) Long fileId,
                        @RequestParam(defaultValue = "TEXT") DocumentMode documentMode,
                        @RequestParam(required = false) FlashcardGenerationDestination destination,
                        @RequestParam(required = false) Long existingDeckId,
                        @RequestParam(required = false) Long newDeckFolderId,
                        @RequestParam(required = false) String newDeckName,
                        Model model,
                        Principal principal,
                        HttpServletResponse response) throws Exception {
    if (principal == null) return "redirect:/login";
    User user = userService.getByUsername(principal.getName());
    try {
        validateAndBuildDocumentInput(fileId, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, user);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        return null;
    } catch (Exception ex) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("aiErrorTitle", "AI generation failed");
        model.addAttribute("aiErrorMessage", ex.getMessage() == null || ex.getMessage().isBlank() ? "Please try again." : ex.getMessage());
        model.addAttribute("aiErrorDetails", generationDetails("FLASHCARDS", ex));
        return "fragments/ai-generation-error :: aiGenerationError";
    }
}
```

Extract the validation/building part used by both `generate` and `preflight`:

```java
private DocumentInput validateAndBuildDocumentInput(Long fileId,
                                                    DocumentMode documentMode,
                                                    FlashcardGenerationDestination destination,
                                                    Long existingDeckId,
                                                    Long newDeckFolderId,
                                                    String newDeckName,
                                                    User user) throws Exception {
    if (fileId == null) throw new IllegalArgumentException("Please select one PDF.");
    if (destination == null) throw new IllegalArgumentException("Please choose where to save the generated flashcards.");
    persistenceService.validateDestination(destination, existingDeckId, newDeckFolderId, newDeckName, user);

    FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
    if (!isPdf(file) || !documentExtractionService.isSupported(file)) {
        throw new IllegalArgumentException("Please select a supported PDF under 10 MB.");
    }

    return buildDocumentInput(file, documentMode);
}
```

In `generate`, replace duplicated validation with:

```java
DocumentInput input = validateAndBuildDocumentInput(fileId, documentMode, destination, existingDeckId, newDeckFolderId, newDeckName, user);
FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
requireQuotaService().checkAndRecord(user);
List<GeneratedFlashcard> generated = aiFlashcardService.generate(input, additionalInstructions);
```

- [ ] **Step 4: Update existing tests for new signature**

In `FlashcardGenerationControllerTests`, every existing `controller.generate(...)` call must include one extra `null` argument between `newDeckName` and `model`.

Update stubs/verifications from:

```java
when(aiFlashcardService.generate(any(DocumentInput.class))).thenReturn(List.of(new GeneratedFlashcard("Q", "A")));
verify(aiFlashcardService).generate(docCaptor.capture());
verify(aiFlashcardService, never()).generate(any(DocumentInput.class));
```

to:

```java
when(aiFlashcardService.generate(any(DocumentInput.class), isNull())).thenReturn(List.of(new GeneratedFlashcard("Q", "A")));
verify(aiFlashcardService).generate(docCaptor.capture(), isNull());
verify(aiFlashcardService, never()).generate(any(DocumentInput.class), any());
```

- [ ] **Step 5: Run flashcard controller tests**

Run:

```bash
./mvnw -Dtest=FlashcardGenerationControllerTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationController.java src/test/java/com/HendrikHoemberg/StudyHelper/controller/FlashcardGenerationControllerTests.java
git commit -m "feat: preflight AI flashcard generation instructions"
```

---

### Task 3: Study Wizard Preflight and Quiz/Exam Instruction Pass-Through

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/StudyController.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/ExamController.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/StudyControllerTests.java`

- [ ] **Step 1: Promote mocks to fields in `StudyControllerTests`**

Change the local variables in `setUp` to fields so new tests can verify them:

```java
private StudySessionService studySessionService;
private AiQuizService aiQuizService;
private AiExamService aiExamService;
private ExamService examService;
private UserService userService;
private DeckService deckService;
private FlashcardService flashcardService;
private FolderService folderService;
private DocumentExtractionService documentExtractionService;
private FileEntryService fileEntryService;
```

In `setUp`, assign each field:

```java
studySessionService = mock(StudySessionService.class);
aiQuizService = mock(AiQuizService.class);
aiExamService = mock(AiExamService.class);
examService = mock(ExamService.class);
userService = mock(UserService.class);
deckService = mock(DeckService.class);
flashcardService = mock(FlashcardService.class);
folderService = mock(FolderService.class);
documentExtractionService = mock(DocumentExtractionService.class);
fileEntryService = mock(FileEntryService.class);
aiRequestQuotaService = mock(AiRequestQuotaService.class);
```

- [ ] **Step 2: Add failing study preflight and pass-through tests**

Add these imports to `StudyControllerTests`:

```java
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuestionType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
```

Add these tests:

```java
@Test
void preflight_QuizMissingSources_ReturnsErrorWithoutQuotaOrAi() {
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletResponse response = new MockHttpServletResponse();

    String view = controller.preflight(
        StudyMode.QUIZ,
        List.of(),
        List.of(),
        new MockHttpServletRequest(),
        SessionMode.DECK_BY_DECK,
        DeckOrderMode.SELECTED_ORDER,
        null,
        QuizQuestionMode.MCQ_ONLY,
        Difficulty.MEDIUM,
        5,
        ExamQuestionSize.MEDIUM,
        5,
        null,
        ExamLayout.PER_PAGE,
        model,
        () -> "alice",
        new MockHttpSession(),
        response
    );

    assertThat(view).isEqualTo("fragments/ai-generation-error :: aiGenerationError");
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(model.get("aiErrorMessage")).isEqualTo("Please select at least one deck or document.");
    verify(aiRequestQuotaService, never()).checkAndRecord(any());
    verify(aiQuizService, never()).generate(anyList(), anyList(), anyInt(), any(), any(), any());
}

@Test
void preflight_QuizValidSource_ReturnsNoContentWithoutQuotaOrAi() {
    ExtendedModelMap model = new ExtendedModelMap();
    MockHttpServletResponse response = new MockHttpServletResponse();

    String view = controller.preflight(
        StudyMode.QUIZ,
        List.of(1L),
        List.of(),
        new MockHttpServletRequest(),
        SessionMode.DECK_BY_DECK,
        DeckOrderMode.SELECTED_ORDER,
        null,
        QuizQuestionMode.MCQ_ONLY,
        Difficulty.MEDIUM,
        5,
        ExamQuestionSize.MEDIUM,
        5,
        null,
        ExamLayout.PER_PAGE,
        model,
        () -> "alice",
        new MockHttpSession(),
        response
    );

    assertThat(view).isNull();
    assertThat(response.getStatus()).isEqualTo(204);
    verify(aiRequestQuotaService, never()).checkAndRecord(any());
    verify(aiQuizService, never()).generate(anyList(), anyList(), anyInt(), any(), any(), any());
}

@Test
void createSession_QuizWithAdditionalInstructions_PassesInstructionsToAi() {
    when(aiQuizService.generate(anyList(), anyList(), eq(3), eq(QuizQuestionMode.MCQ_ONLY), eq(Difficulty.EASY), eq("focus on HTML")))
        .thenReturn(List.of(new QuizQuestion(QuestionType.MULTIPLE_CHOICE, "Q1", List.of("a", "b", "c", "d"), 0)));
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.createSession(
        StudyMode.QUIZ,
        List.of(1L),
        List.of(),
        new MockHttpServletRequest(),
        SessionMode.DECK_BY_DECK,
        DeckOrderMode.SELECTED_ORDER,
        null,
        QuizQuestionMode.MCQ_ONLY,
        Difficulty.EASY,
        3,
        ExamQuestionSize.MEDIUM,
        5,
        null,
        ExamLayout.PER_PAGE,
        "focus on HTML",
        model,
        () -> "alice",
        new MockHttpSession(),
        new MockHttpServletResponse(),
        "true"
    );

    assertThat(view).isEqualTo("fragments/quiz-question :: quizQuestion");
    verify(aiQuizService).generate(anyList(), anyList(), eq(3), eq(QuizQuestionMode.MCQ_ONLY), eq(Difficulty.EASY), eq("focus on HTML"));
}

@Test
void createSession_ExamWithAdditionalInstructions_PassesInstructionsToAiExamService() {
    when(aiExamService.generate(anyList(), anyList(), eq(4), eq(ExamQuestionSize.SHORT), eq("focus on HTML")))
        .thenReturn(List.of(new ExamQuestion("Q1", "Hint")));
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.createSession(
        StudyMode.EXAM,
        List.of(1L),
        List.of(),
        new MockHttpServletRequest(),
        SessionMode.DECK_BY_DECK,
        DeckOrderMode.SELECTED_ORDER,
        null,
        QuizQuestionMode.MCQ_ONLY,
        Difficulty.MEDIUM,
        5,
        ExamQuestionSize.SHORT,
        4,
        null,
        ExamLayout.PER_PAGE,
        "focus on HTML",
        model,
        () -> "alice",
        new MockHttpSession(),
        new MockHttpServletResponse(),
        "true"
    );

    assertThat(view).isEqualTo("fragments/exam-question :: exam-question");
    verify(aiExamService).generate(anyList(), anyList(), eq(4), eq(ExamQuestionSize.SHORT), eq("focus on HTML"));
}
```

- [ ] **Step 3: Run study controller tests to verify they fail**

Run:

```bash
./mvnw -Dtest=StudyControllerTests test
```

Expected: compilation fails because `preflight` and the new `createSession` parameter do not exist.

- [ ] **Step 4: Add `additionalInstructions` to StudyController**

In `StudyController.createSession`, add:

```java
@RequestParam(required = false) String additionalInstructions,
```

Place it after `ExamLayout layout` and before `Model model`.

Change the quiz AI call to:

```java
List<QuizQuestion> questions = aiQuizService.generate(flashcards, documents, qCount, quizQuestionMode, difficulty, additionalInstructions);
```

Change the exam delegation to:

```java
return examController.createSession(selectedDeckIds, selectedFileIds, request, questionSize, count, timerMinutes, layout, additionalInstructions, model, principal, session, response, hxRequest);
```

Update every existing direct test call to `createSession` with an extra `null` argument after `ExamLayout.PER_PAGE`.

- [ ] **Step 5: Add instruction support to ExamController delegation**

In `ExamController.createSession`, add this parameter after `ExamLayout layout`:

```java
@RequestParam(required = false) String additionalInstructions,
```

Change the generation call to:

```java
List<ExamQuestion> questions = aiExamService.generate(flashcards, documents, qCount, questionSize, additionalInstructions);
```

Update any direct calls to `ExamController.createSession` in tests with `null` for this new argument.

- [ ] **Step 6: Add StudyController preflight route**

Add this method to `StudyController`:

```java
@PostMapping("/study/session/preflight")
public String preflight(@RequestParam StudyMode mode,
                        @RequestParam(required = false) List<Long> selectedDeckIds,
                        @RequestParam(required = false) List<Long> selectedFileIds,
                        HttpServletRequest request,
                        @RequestParam(defaultValue = "DECK_BY_DECK") SessionMode sessionMode,
                        @RequestParam(defaultValue = "SELECTED_ORDER") DeckOrderMode deckOrderMode,
                        @RequestParam(required = false) String orderedDeckIds,
                        @RequestParam(defaultValue = "MCQ_ONLY") QuizQuestionMode quizQuestionMode,
                        @RequestParam(defaultValue = "MEDIUM") Difficulty difficulty,
                        @RequestParam(defaultValue = "5") int questionCount,
                        @RequestParam(defaultValue = "MEDIUM") ExamQuestionSize questionSize,
                        @RequestParam(defaultValue = "5") int count,
                        @RequestParam(required = false) Integer timerMinutes,
                        @RequestParam(defaultValue = "PER_PAGE") ExamLayout layout,
                        Model model,
                        Principal principal,
                        HttpSession session,
                        HttpServletResponse response) {
    Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
    if (principal == null) return "redirect:/login";
    User user = userService.getByUsername(principal.getName());
    try {
        if (mode == StudyMode.QUIZ) {
            buildQuizGenerationInput(selectedDeckIds, selectedFileIds, pdfMode, user, questionCount, quizQuestionMode, difficulty);
        } else if (mode == StudyMode.EXAM) {
            examController.preflight(selectedDeckIds, selectedFileIds, request, questionSize, count, timerMinutes, layout, user);
        } else {
            List<Long> orderedSelection = resolveOrderedSelection(selectedDeckIds, orderedDeckIds, sessionMode);
            StudySessionConfig config = new StudySessionConfig(orderedSelection, sessionMode, deckOrderMode);
            studySessionService.buildSession(config, user);
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        return null;
    } catch (Exception ex) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("aiErrorTitle", "AI generation failed");
        model.addAttribute("aiErrorMessage", ex.getMessage() == null || ex.getMessage().isBlank() ? "Please try again." : ex.getMessage());
        model.addAttribute("aiErrorDetails", generationDetails(mode, ex));
        return "fragments/ai-generation-error :: aiGenerationError";
    }
}
```

- [ ] **Step 7: Extract quiz validation/building helper**

Add a record inside `StudyController`:

```java
private record QuizGenerationInput(List<Long> deckIds, List<Long> fileIds, List<Flashcard> flashcards, List<DocumentInput> documents, int questionCount) {
}
```

Add helper:

```java
private QuizGenerationInput buildQuizGenerationInput(List<Long> selectedDeckIds,
                                                     List<Long> selectedFileIds,
                                                     Map<Long, DocumentMode> pdfMode,
                                                     User user,
                                                     int questionCount,
                                                     QuizQuestionMode quizQuestionMode,
                                                     Difficulty difficulty) throws Exception {
    List<Long> deckIds = normalizeIds(selectedDeckIds);
    List<Long> fileIds = normalizeIds(selectedFileIds);
    if (deckIds.isEmpty() && fileIds.isEmpty()) {
        throw new IllegalArgumentException("Please select at least one deck or document.");
    }

    List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(deckIds, user);
    List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
    List<DocumentInput> documents = new ArrayList<>();
    long totalChars = 0;
    for (Long fileId : fileIds) {
        FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
        DocumentMode docMode = DocumentModeResolver.resolve(file, pdfMode);
        switch (docMode) {
            case TEXT -> {
                String text = documentExtractionService.extractText(file);
                documents.add(new TextDocument(file.getOriginalFilename(), text));
                totalChars += text.length();
            }
            case FULL_PDF -> documents.add(new PdfDocument(file.getOriginalFilename(),
                    documentExtractionService.loadResource(file)));
        }
    }

    if (totalChars > 150_000) {
        throw new IllegalArgumentException("Selection too large — please deselect some sources.");
    }

    int qCount = Math.max(1, Math.min(questionCount, 20));
    return new QuizGenerationInput(deckIds, fileIds, flashcards, documents, qCount);
}
```

In the quiz branch of `createSession`, replace duplicated code with:

```java
QuizGenerationInput input = buildQuizGenerationInput(selectedDeckIds, selectedFileIds, pdfMode, user, questionCount, quizQuestionMode, difficulty);
requireQuotaService().checkAndRecord(user);
List<QuizQuestion> questions = aiQuizService.generate(input.flashcards(), input.documents(), input.questionCount(), quizQuestionMode, difficulty, additionalInstructions);

QuizSessionState state = new QuizSessionState(
    new QuizConfig(input.deckIds(), input.fileIds(), input.questionCount(), quizQuestionMode, difficulty),
    questions, 0, new HashMap<>()
);
```

- [ ] **Step 8: Add ExamController preflight helper**

In `ExamController`, add:

```java
public void preflight(List<Long> selectedDeckIds,
                      List<Long> selectedFileIds,
                      HttpServletRequest request,
                      ExamQuestionSize questionSize,
                      int count,
                      Integer timerMinutes,
                      ExamLayout layout,
                      User user) throws Exception {
    Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
    buildExamGenerationInput(selectedDeckIds, selectedFileIds, pdfMode, user, count);
}
```

Add record:

```java
private record ExamGenerationInput(List<Long> deckIds, List<Long> fileIds, List<Flashcard> flashcards, List<DocumentInput> documents, List<String> sourceNames, int questionCount) {
}
```

Extract the validation/source-building logic from `createSession` into `buildExamGenerationInput`, matching existing behavior and keeping the same messages:

```java
private ExamGenerationInput buildExamGenerationInput(List<Long> selectedDeckIds,
                                                     List<Long> selectedFileIds,
                                                     Map<Long, DocumentMode> pdfMode,
                                                     User user,
                                                     int count) throws Exception {
    List<Long> deckIds = normalizeIds(selectedDeckIds);
    List<Long> fileIds = normalizeIds(selectedFileIds);

    if (deckIds.isEmpty() && fileIds.isEmpty()) {
        throw new IllegalArgumentException("Please select at least one source.");
    }

    List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(deckIds, user);
    List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);

    List<DocumentInput> documents = new ArrayList<>();
    List<String> sourceNames = new ArrayList<>();
    decks.forEach(d -> sourceNames.add(d.getName()));

    long totalChars = 0;
    for (Long fileId : fileIds) {
        FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
        DocumentMode docMode = DocumentModeResolver.resolve(file, pdfMode);
        switch (docMode) {
            case TEXT -> {
                String text = documentExtractionService.extractText(file);
                documents.add(new TextDocument(file.getOriginalFilename(), text));
                totalChars += text.length();
            }
            case FULL_PDF -> documents.add(new PdfDocument(file.getOriginalFilename(),
                    documentExtractionService.loadResource(file)));
        }
        sourceNames.add(file.getOriginalFilename());
    }

    if (totalChars > 150_000) {
        throw new IllegalArgumentException("Selection too large — please deselect some sources.");
    }

    int qCount = Math.max(1, Math.min(count, 20));
    return new ExamGenerationInput(deckIds, fileIds, flashcards, documents, sourceNames, qCount);
}
```

In `createSession`, use the helper and pass instructions:

```java
ExamGenerationInput input = buildExamGenerationInput(selectedDeckIds, selectedFileIds, pdfMode, user, count);
requireQuotaService().checkAndRecord(user);
List<ExamQuestion> questions = aiExamService.generate(input.flashcards(), input.documents(), input.questionCount(), questionSize, additionalInstructions);
```

Use `input.sourceNames()`, `input.deckIds()`, `input.fileIds()`, and `input.questionCount()` when building the source summary and `ExamConfig`.

- [ ] **Step 9: Run study controller tests**

Run:

```bash
./mvnw -Dtest=StudyControllerTests test
```

Expected: PASS.

- [ ] **Step 10: Run related controller tests**

Run:

```bash
./mvnw -Dtest=StudyControllerTests,ExamControllerTests test
```

Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/StudyController.java src/main/java/com/HendrikHoemberg/StudyHelper/controller/ExamController.java src/test/java/com/HendrikHoemberg/StudyHelper/controller/StudyControllerTests.java
git commit -m "feat: preflight study AI generation instructions"
```

---

### Task 4: Shared Instructions Dialog Support

**Files:**
- Modify: `src/main/resources/templates/fragments/dialog.html`
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/static/css/styles.css`

- [ ] **Step 1: Add textarea markup**

In `dialog.html`, add this after the existing `#sh-dialog-input`:

```html
<textarea class="sh-input sh-dialog-textarea"
          id="sh-dialog-textarea"
          rows="5"
          maxlength="1000"
          style="display:none;"></textarea>
```

- [ ] **Step 2: Extend dialog JavaScript**

In `app.js`, update `initShDialog` OK and Enter handling to recognize the textarea:

```javascript
const textarea = document.getElementById('sh-dialog-textarea');
const isTextareaPrompt = textarea && textarea.style.display !== 'none';
const isPrompt = input && input.style.display !== 'none';
shDialogResolve(isTextareaPrompt ? textarea.value : (isPrompt ? input.value : true));
```

Use the same expression in the Enter key handler. For textarea prompts, Enter should insert a newline, so only submit on `Ctrl+Enter` or `Meta+Enter`:

```javascript
if (isTextareaPrompt && document.activeElement === textarea && !(e.ctrlKey || e.metaKey)) return;
```

- [ ] **Step 3: Add textarea options to `_shOpenDialog`**

Change the function signature:

```javascript
function _shOpenDialog({ title, message, icon, iconKind, confirmText, cancelText, danger, prompt, textarea, defaultValue, placeholder, hideCancel, technicalDetails }) {
```

Inside it, fetch the textarea:

```javascript
const textareaEl = document.getElementById('sh-dialog-textarea');
```

After the input handling block, add:

```javascript
if (textareaEl) {
    if (textarea) {
        textareaEl.style.display = '';
        textareaEl.value = defaultValue || '';
        textareaEl.placeholder = placeholder || '';
        textareaEl.maxLength = 1000;
    } else {
        textareaEl.style.display = 'none';
        textareaEl.value = '';
    }
}
```

Update focus management:

```javascript
if (textarea && textareaEl) textareaEl.focus();
else if (prompt) input.focus();
else okBtn.focus();
```

- [ ] **Step 4: Add a public textarea prompt helper**

Add after `shPrompt`:

```javascript
function shTextareaPrompt(opts) {
    if (typeof opts === 'string') opts = { message: opts };
    return _shOpenDialog({ iconKind: 'edit', ...opts, prompt: false, textarea: true });
}
```

- [ ] **Step 5: Add minimal textarea CSS**

In `styles.css`, near the existing dialog styles, add:

```css
.sh-dialog-textarea {
    width: 100%;
    min-height: 7rem;
    resize: vertical;
    line-height: 1.45;
}
```

- [ ] **Step 6: Manually verify dialog behavior**

Run the app later in Task 6 and verify:

- `shTextareaPrompt({ title: 'Add generation instructions', message: 'Optional', confirmText: 'Generate' })` opens a textarea.
- Escape and Cancel resolve `null`.
- The OK button resolves the textarea value.
- Enter inserts a newline inside the textarea.
- Ctrl+Enter or Cmd+Enter submits the dialog.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/fragments/dialog.html src/main/resources/static/js/app.js src/main/resources/static/css/styles.css
git commit -m "feat: support textarea dialogs"
```

---

### Task 5: Secondary Buttons and Preflight Client Flow

**Files:**
- Modify: `src/main/resources/templates/fragments/flashcard-generator.html`
- Modify: `src/main/resources/templates/fragments/study-setup.html`
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/static/js/study-wizard.js`

- [ ] **Step 1: Add flashcard hidden input and secondary button**

In `flashcard-generator.html`, inside `form.sh-ai-flashcard-form`, add:

```html
<input type="hidden" name="additionalInstructions" value="">
```

In the footer, add a secondary button before the primary submit:

```html
<button type="button" id="ai-flashcard-instructions-submit" class="sh-btn sh-btn-secondary">
    <iconify-icon icon="lucide:message-square-plus"></iconify-icon>
    Generate with instructions
</button>
```

Keep the existing primary submit button unchanged.

- [ ] **Step 2: Add study wizard hidden input and secondary button**

In `study-setup.html`, inside `form.sh-study-setup-card`, add:

```html
<input type="hidden" name="additionalInstructions" value="">
```

In the wizard footer beside `#wizard-btn-submit`, add:

```html
<button type="button" class="sh-btn sh-btn-secondary" id="wizard-btn-submit-instructions" style="display:none;">
    <iconify-icon icon="lucide:message-square-plus"></iconify-icon>
    <span class="sh-btn-text">Generate with instructions</span>
</button>
```

- [ ] **Step 3: Update study wizard button visibility and labels**

In `study-wizard.js`, get the new button in `updateNavButtons`:

```javascript
const instructionsBtn = document.getElementById('wizard-btn-submit-instructions');
```

Set visibility:

```javascript
if (instructionsBtn) instructionsBtn.style.display = isLast && (currentMode === 'QUIZ' || currentMode === 'EXAM') ? '' : 'none';
```

Set labels:

```javascript
if (instructionsBtn) {
    const text = instructionsBtn.querySelector('.sh-btn-text');
    if (text) text.textContent = currentMode === 'EXAM' ? 'Start with instructions' : 'Generate with instructions';
}
```

In `initStudyWizard`, clone and bind the instructions button:

```javascript
const instructionsBtn = document.getElementById('wizard-btn-submit-instructions');
if (instructionsBtn) {
    const fresh = instructionsBtn.cloneNode(true);
    instructionsBtn.parentNode.replaceChild(fresh, instructionsBtn);
    fresh.addEventListener('click', wizardSubmitWithInstructions);
}
```

- [ ] **Step 4: Add shared modal prompt helper in `app.js`**

Add this helper:

```javascript
function promptForAiGenerationInstructions(confirmText) {
    return shTextareaPrompt({
        title: 'Add generation instructions',
        message: 'Optional. Add a short note about what the AI should focus on for this generation.',
        placeholder: 'Focus on the HTML part of this document',
        confirmText: confirmText || 'Generate',
        cancelText: 'Cancel'
    });
}
```

- [ ] **Step 5: Add flashcard preflight and submit helper in `app.js`**

Add:

```javascript
function submitAiFlashcardsWithInstructions(form, instructions) {
    const input = form.querySelector('input[name="additionalInstructions"]');
    if (input) input.value = (instructions || '').trim();
    form.requestSubmit();
}

function runAiPreflight(form, url) {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = { 'HX-Request': 'true' };
    if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

    return fetch(url, {
        method: 'POST',
        body: new FormData(form),
        headers
    }).then(async response => {
        if (response.ok) return;
        const text = await response.text();
        const message = extractAiGenerationError(text);
        const details = extractAiGenerationDetails(text);
        showAiGenerationFailure(message, details);
        throw new Error('AI preflight failed');
    });
}
```

Bind the flashcard secondary button:

```javascript
document.addEventListener('click', (event) => {
    const btn = event.target.closest('#ai-flashcard-instructions-submit');
    if (!btn) return;
    event.preventDefault();
    const form = btn.closest('form.sh-ai-flashcard-form');
    if (!form) return;

    const hidden = form.querySelector('input[name="additionalInstructions"]');
    if (hidden) hidden.value = '';

    runAiPreflight(form, '/flashcards/generate/preflight')
        .then(() => promptForAiGenerationInstructions('Generate'))
        .then(value => {
            if (value === null) return;
            submitAiFlashcardsWithInstructions(form, value);
        })
        .catch(() => {});
});
```

- [ ] **Step 6: Add wizard preflight flow in `study-wizard.js`**

Add inside the IIFE:

```javascript
function wizardSubmitWithInstructions(e) {
    e.preventDefault();
    if (!validateStep(currentStep)) return;
    const form = document.querySelector('.sh-study-setup-card');
    if (!form) return;

    const hidden = form.querySelector('input[name="additionalInstructions"]');
    if (hidden) hidden.value = '';

    runAiPreflight(form, '/study/session/preflight')
        .then(() => promptForAiGenerationInstructions(currentMode === 'EXAM' ? 'Start exam' : 'Generate quiz'))
        .then(value => {
            if (value === null) return;
            if (hidden) hidden.value = (value || '').trim();
            form.requestSubmit();
        })
        .catch(() => {});
}
```

Because `runAiPreflight` and `promptForAiGenerationInstructions` are global function declarations in `app.js`, they are available to `study-wizard.js` after both scripts load.

- [ ] **Step 7: Ensure primary submit clears old instructions**

In `app.js`, before showing the flashcard loading modal in the existing `htmx:beforeRequest` handler for `form.sh-ai-flashcard-form`, do not clear instructions. The hidden value must survive the secondary flow.

In `study-wizard.js`, in the primary submit button click handler, add:

```javascript
const hidden = document.querySelector('.sh-study-setup-card input[name="additionalInstructions"]');
if (hidden) hidden.value = '';
```

For flashcards, add a click handler for the primary submit:

```javascript
document.addEventListener('click', (event) => {
    const btn = event.target.closest('#ai-flashcard-submit');
    if (!btn) return;
    const hidden = btn.closest('form')?.querySelector('input[name="additionalInstructions"]');
    if (hidden) hidden.value = '';
});
```

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/fragments/flashcard-generator.html src/main/resources/templates/fragments/study-setup.html src/main/resources/static/js/app.js src/main/resources/static/js/study-wizard.js
git commit -m "feat: add AI generation instruction preflight flow"
```

---

### Task 6: Full Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
./mvnw -Dtest=AiFlashcardServiceTests,AiQuizServiceTests,AiExamServiceTests,FlashcardGenerationControllerTests,StudyControllerTests,ExamControllerTests test
```

Expected: PASS.

- [ ] **Step 2: Run full test suite**

Run:

```bash
./mvnw test
```

Expected: PASS.

- [ ] **Step 3: Start the app**

Run:

```bash
./mvnw spring-boot:run
```

Expected: app starts on the configured local port, usually `http://localhost:8080`.

- [ ] **Step 4: Manual browser verification**

Verify these flows:

- Flashcard primary `Generate flashcards` submits without opening the instructions modal.
- Flashcard `Generate with instructions` preflights first, opens the modal on valid input, and submits with instructions.
- Flashcard duplicate new-deck name shows an error before the instructions modal opens.
- Quiz primary `Generate Quiz` submits without opening the instructions modal.
- Quiz `Generate with instructions` preflights first, opens the modal on valid input, and submits with instructions.
- Quiz missing source shows an error before the instructions modal opens.
- Exam primary `Start Exam` submits without opening the instructions modal.
- Exam `Start with instructions` preflights first, opens the modal on valid input, and submits with instructions.
- Exam grading has no instruction prompt.
- The AI loading modal appears only during the actual generation request, not during preflight.

- [ ] **Step 5: Stop the app**

Stop the `spring-boot:run` process with Ctrl+C. Confirm no needed long-running process remains.

- [ ] **Step 6: Inspect final diff**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected: no uncommitted changes if each task was committed. If changes remain from manual verification or formatting, inspect them and either commit intended changes or revert only files changed during this implementation.
