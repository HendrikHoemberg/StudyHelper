# AI Prompt Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `AiQuizService`, `AiExamService`, and `AiFlashcardService` robust (Gemini server-side schema enforcement), language-matching (output written in the source language), and coverage-balanced across large documents.

**Architecture:** Each service swaps its `chatClient.prompt()...call().content() + manual JSON regex` pipeline for `chatClient.prompt().options(...).call().entity(WrapperResponse.class)`. The `options` carry `responseMimeType=application/json` plus a `responseSchema` derived once via `BeanOutputConverter.getJsonSchema()`. Prompts gain two new instruction blocks — `LANGUAGE:` and `COVERAGE:` — placed near the top.

**Tech Stack:** Spring Boot 3, Spring AI 2.0.0-M5 (`spring-ai-google-genai`), Gemini `gemini-3.1-flash-lite-preview`, JUnit 5, AssertJ, Mockito, `tools.jackson.databind.json.JsonMapper`.

**Spec:** `docs/superpowers/specs/2026-05-12-ai-prompt-optimization-design.md`

---

## File Structure

**New files (under `src/main/java/com/HendrikHoemberg/StudyHelper/dto/`):**

- `QuizQuestionsResponse.java` — wrapper record `(List<QuizQuestion> questions)` for Gemini's structured output of quiz questions.
- `ExamQuestionsResponse.java` — wrapper record `(List<ExamQuestion> questions)` for exam generation.
- `FlashcardsResponse.java` — wrapper record `(List<GeneratedFlashcard> flashcards)` for flashcard generation.

**Modified production files:**

- `service/AiQuizService.java` — adopt structured output, add LANGUAGE + COVERAGE blocks, delete `extractJson` and the trailing schema block in the prompt.
- `service/AiExamService.java` — same treatment for both `generate(...)` and `grade(...)`; grading prompt gets only LANGUAGE.
- `service/AiFlashcardService.java` — structured output, LANGUAGE block, light COVERAGE block.

**Modified test files:**

- `test/.../service/AiQuizServiceTests.java`
- `test/.../service/AiExamServiceTests.java`
- `test/.../service/AiFlashcardServiceTests.java`

Each test file's `@BeforeEach` mock chain is extended to stub `requestSpec.options(...)` (capturing the builder) and `callSpec.entity(<WrapperResponse>.class)`. Existing tests that stub `callSpec.content()` are rewritten to stub `callSpec.entity(...)` returning a typed wrapper.

**Unchanged:** controllers, entities, repositories, `application.properties`, UI templates, all other DTOs.

---

## Conventions

- Java records for DTOs, single source file, `dto` package, no Javadoc.
- The existing services already import `import tools.jackson.databind.json.JsonMapper;` — keep using that Jackson 3 mapper (not the legacy `com.fasterxml.jackson` one).
- After every code change, run `./mvnw -q -DskipTests=false test -Dtest=<ClassName>` for the affected test class. Run the full `./mvnw -q test` only at the end (Task 10).
- Commit after each task. Use Conventional Commits (`feat:`, `refactor:`, `test:`).
- Never use `git commit --no-verify` or `--amend`.

---

## Task 1: Add wrapper response DTOs

**Files:**

- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/QuizQuestionsResponse.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/ExamQuestionsResponse.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardsResponse.java`

- [ ] **Step 1: Create `QuizQuestionsResponse.java`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record QuizQuestionsResponse(List<QuizQuestion> questions) {}
```

- [ ] **Step 2: Create `ExamQuestionsResponse.java`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record ExamQuestionsResponse(List<ExamQuestion> questions) {}
```

- [ ] **Step 3: Create `FlashcardsResponse.java`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;

public record FlashcardsResponse(List<GeneratedFlashcard> flashcards) {}
```

- [ ] **Step 4: Confirm compile**

Run: `./mvnw -q -DskipTests compile`
Expected: `BUILD SUCCESS`. No tests affected yet.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/dto/QuizQuestionsResponse.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/ExamQuestionsResponse.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/FlashcardsResponse.java
git commit -m "feat(ai): add wrapper response DTOs for structured AI output"
```

---

## Task 2: Migrate `AiQuizService` to structured output

Replace the manual JSON path with `.options(...).entity(QuizQuestionsResponse.class)`. Prompt content does **not** change yet — only the call mechanism and the trailing schema block.

**Files:**

- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiQuizService.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiQuizServiceTests.java`

- [ ] **Step 1: Update test `@BeforeEach` to capture options and stub `.entity(...)`**

Replace the entire `setUp()` method in `AiQuizServiceTests.java` with:

```java
private ChatClient.CallResponseSpec callSpec;
private AiQuizService service;
private final AtomicReference<String> capturedPrompt = new AtomicReference<>();
private final List<org.springframework.ai.content.Media> capturedMedia = new ArrayList<>();
private final AtomicReference<org.springframework.ai.chat.prompt.ChatOptions.Builder> capturedOptionsBuilder = new AtomicReference<>();

@BeforeEach
void setUp() {
    capturedPrompt.set(null);
    capturedMedia.clear();
    capturedOptionsBuilder.set(null);

    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    callSpec = mock(ChatClient.CallResponseSpec.class);
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.Builder builder = mock(ChatClient.Builder.class);

    when(builder.build()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);

    when(requestSpec.options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class)))
        .thenAnswer(invocation -> {
            capturedOptionsBuilder.set(invocation.getArgument(0));
            return requestSpec;
        });

    when(requestSpec.user(any(Consumer.class))).thenAnswer(invocation -> {
        Consumer<ChatClient.PromptUserSpec> consumer = invocation.getArgument(0);
        ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
        when(userSpec.text(anyString())).thenAnswer(a -> {
            capturedPrompt.set(a.getArgument(0));
            return userSpec;
        });
        when(userSpec.media(any(org.springframework.ai.content.Media.class))).thenAnswer(a -> {
            for (Object arg : a.getRawArguments()) {
                if (arg instanceof org.springframework.ai.content.Media m) {
                    capturedMedia.add(m);
                } else if (arg instanceof org.springframework.ai.content.Media[] arr) {
                    Collections.addAll(capturedMedia, arr);
                }
            }
            return userSpec;
        });
        consumer.accept(userSpec);
        return requestSpec;
    });
    when(requestSpec.call()).thenReturn(callSpec);

    service = new AiQuizService(builder, new JsonMapper());
}
```

Add imports as needed:
```java
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionsResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
```

- [ ] **Step 2: Rewrite all existing tests that stub `callSpec.content()` to stub `callSpec.entity(...)` instead**

For every test in the file that calls `when(callSpec.content()).thenReturn(<json>)`, replace with `when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(<wrapper>)`. Replace the `threeQuestions()` helper and inline JSON returns with builder methods that return `QuizQuestionsResponse` instances.

Add this helper at the bottom of the test class:

```java
private QuizQuestionsResponse wrap(QuizQuestion... questions) {
    return new QuizQuestionsResponse(List.of(questions));
}

private QuizQuestion mcq(String text, int correctIdx) {
    return new QuizQuestion(QuestionType.MULTIPLE_CHOICE, text,
        List.of("a", "b", "c", "d"), correctIdx);
}

private QuizQuestion tf(String text, int correctIdx) {
    return new QuizQuestion(QuestionType.TRUE_FALSE, text,
        List.of("True", "False"), correctIdx);
}

private QuizQuestion tfLower(String text, int correctIdx) {
    return new QuizQuestion(QuestionType.TRUE_FALSE, text,
        List.of("true", "false"), correctIdx);
}

private QuizQuestion mcqTypeless(String text, int correctIdx) {
    return new QuizQuestion(null, text, List.of("a", "b", "c", "d"), correctIdx);
}

private QuizQuestion tfTypeless(String text, int correctIdx) {
    return new QuizQuestion(null, text, List.of("True", "False"), correctIdx);
}
```

Then update each test. For example, `generate_McqOnly_ReturnsFiveMcqQuestions` becomes:

```java
@Test
void generate_McqOnly_ReturnsFiveMcqQuestions() {
    when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
        mcq("Q1", 0), mcq("Q2", 1), mcq("Q3", 2), mcq("Q4", 3), mcq("Q5", 0)
    ));

    List<QuizQuestion> result = service.generate(cards(3), List.of(), 5, QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM);

    assertThat(result).hasSize(5);
    assertThat(result).allMatch(q -> q.type() == QuestionType.MULTIPLE_CHOICE);
    assertThat(result).allMatch(q -> q.correctOptionIndex() >= 0 && q.correctOptionIndex() <= 3);
}
```

Apply the same pattern to every other test that currently stubs `.content()`. Specifically:

| Existing test | New stub |
| --- | --- |
| `generate_TfOnly_ReturnsTfQuestionsWithNormalizedOptions` | `wrap(tf("Q1",0), tf("Q2",1), tf("Q3",0))` |
| `generate_Mixed_ReturnsBothTypes` | `wrap(mcq("Q1",0), tf("Q2",1), mcq("Q3",2), tf("Q4",0))` |
| `generate_LowercaseTfOptions_NormalizedToTitleCase` | `wrap(tfLower("Q1",0), tfLower("Q2",1))` |
| `generate_MissingTypeField_InferredFromOptionsShape` | `wrap(mcqTypeless("Q1",0), tfTypeless("Q2",1))` |
| `generate_MalformedEntriesDropped_TooFewThrowsIllegalState` | `wrap(new QuizQuestion(QuestionType.MULTIPLE_CHOICE,"Q1",List.of("only","two"),0), new QuizQuestion(QuestionType.MULTIPLE_CHOICE,"Q2",List.of("only","two"),0))` |
| `generate_TextDocument_includedInPromptDocumentsSection` | `wrap(mcq("Q1",0), mcq("Q2",0), mcq("Q3",0))` |
| `generate_PdfDocument_attachedAsMediaAndListedInPrompt` | same as above |
| `generate_MixedTextAndPdfDocs_bothSectionsPopulated` | same as above |
| `generate_PdfOnly_NoFlashcardsNoText_DoesNotThrow` | same as above |

For `generate_ProviderFailure_throwsStableRetryMessage`, change to:

```java
when(callSpec.entity(QuizQuestionsResponse.class)).thenThrow(new RuntimeException("provider offline"));
```

For `generate_ParseFailure_stillUsesParseSpecificMessage`, change to:

```java
when(callSpec.entity(QuizQuestionsResponse.class))
    .thenThrow(new org.springframework.ai.converter.BeanOutputConverter.JsonSchemaValidationException("bad"));
```

> If that exception class doesn't exist in M5 (it's a thin wrapper anyway), use a plain `RuntimeException("parse failed")` and verify the test expectation still maps to `"Could not parse the AI response. Please try again."` via the catch arm in the service (see Task 2 Step 4). If the parse-failure path can no longer be distinguished from provider-failure, **delete this test** rather than weakening it — schema enforcement makes the distinction moot.

Delete the `threeQuestions()` helper (no longer used).

- [ ] **Step 3: Add new test asserting structured-output options are configured**

Append to `AiQuizServiceTests.java`:

```java
@Test
void generate_ConfiguresStructuredOutputOptions() {
    when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
        mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
    ));

    service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

    assertThat(capturedOptionsBuilder.get()).isNotNull();
    var built = (GoogleGenAiChatOptions) capturedOptionsBuilder.get().build();
    assertThat(built.getResponseMimeType()).isEqualTo("application/json");
    assertThat(built.getResponseSchema()).isNotBlank();
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiQuizServiceTests`

Expected: tests fail — `AiQuizService` still calls `.content()`, the mocked `callSpec.content()` returns `null`, parsing fails. `generate_ConfiguresStructuredOutputOptions` also fails because `requestSpec.options(...)` is never called.

- [ ] **Step 5: Rewrite `AiQuizService.generate(...)` to use structured output**

Replace the entire `AiQuizService.java` file with this version. Note: the LANGUAGE and COVERAGE blocks are NOT added here — they're added in Task 3. The trailing inline JSON schema block IS removed here.

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.Difficulty;
import com.HendrikHoemberg.StudyHelper.dto.DocumentInput;
import com.HendrikHoemberg.StudyHelper.dto.PdfDocument;
import com.HendrikHoemberg.StudyHelper.dto.QuestionType;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestionsResponse;
import com.HendrikHoemberg.StudyHelper.dto.TextDocument;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiQuizService {

    private final ChatClient chatClient;
    private final String responseSchema;

    public AiQuizService(ChatClient.Builder builder, JsonMapper objectMapper) {
        this.chatClient = builder.build();
        this.responseSchema = new BeanOutputConverter<>(QuizQuestionsResponse.class, objectMapper).getJsonSchema();
    }

    public List<QuizQuestion> generate(
            List<Flashcard> flashcards,
            List<DocumentInput> documents,
            int count,
            QuizQuestionMode mode,
            Difficulty difficulty) {

        String cardContent = buildCardContent(flashcards);
        String docContent  = buildTextDocContent(documents);
        String pdfListing  = buildPdfListing(documents);
        Media[] pdfMedia   = buildPdfMedia(documents);

        if (cardContent.isBlank() && docContent.isBlank() && pdfMedia.length == 0) {
            throw new IllegalArgumentException("Selected sources contain no usable text or PDFs. Pick a deck, a document with extractable content, or a PDF in full-document mode.");
        }

        int mcqCount = count / 2;
        int tfCount = count - mcqCount;

        String prompt = buildPrompt(cardContent, docContent, pdfListing, count, mode, difficulty, mcqCount, tfCount);

        QuizQuestionsResponse response;
        try {
            response = chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder()
                    .responseMimeType("application/json")
                    .responseSchema(responseSchema))
                .user(u -> {
                    u.text(prompt);
                    if (pdfMedia.length > 0) u.media(pdfMedia);
                })
                .call()
                .entity(QuizQuestionsResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("AI request failed, please retry with fewer or smaller PDFs.", e);
        }

        try {
            List<QuizQuestion> rawList = response == null || response.questions() == null
                ? List.of()
                : response.questions();

            List<QuizQuestion> valid = new ArrayList<>();
            for (QuizQuestion q : rawList) {
                if (q == null) continue;
                QuizQuestion normalized = normalizeQuestion(q);
                if (normalized != null) valid.add(normalized);
            }

            if (valid.size() < Math.max(1, count / 2)) {
                throw new IllegalStateException("AI returned too few valid questions; please retry.");
            }

            return valid.stream().limit(count).toList();

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the AI response. Please try again.", e);
        }
    }

    private QuizQuestion normalizeQuestion(QuizQuestion q) {
        if (q.questionText() == null || q.questionText().isBlank()) return null;
        if (q.options() == null) return null;

        QuestionType type = q.type() != null ? q.type() : inferType(q.options());

        if (type == QuestionType.MULTIPLE_CHOICE) {
            if (q.options().size() != 4) return null;
            if (q.correctOptionIndex() < 0 || q.correctOptionIndex() > 3) return null;
            return new QuizQuestion(QuestionType.MULTIPLE_CHOICE, q.questionText(), q.options(), q.correctOptionIndex());
        } else {
            if (q.options().size() != 2) return null;
            String opt0 = q.options().get(0).trim();
            String opt1 = q.options().get(1).trim();
            if (!opt0.equalsIgnoreCase("true") || !opt1.equalsIgnoreCase("false")) return null;
            if (q.correctOptionIndex() < 0 || q.correctOptionIndex() > 1) return null;
            return new QuizQuestion(QuestionType.TRUE_FALSE, q.questionText(), List.of("True", "False"), q.correctOptionIndex());
        }
    }

    private QuestionType inferType(List<String> options) {
        if (options.size() == 2) {
            String o0 = options.get(0).trim().toLowerCase();
            String o1 = options.get(1).trim().toLowerCase();
            if ((o0.equals("true") || o0.equals("false")) && (o1.equals("true") || o1.equals("false"))) {
                return QuestionType.TRUE_FALSE;
            }
        }
        return QuestionType.MULTIPLE_CHOICE;
    }

    private String buildCardContent(List<Flashcard> flashcards) {
        if (flashcards == null || flashcards.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Flashcard card : flashcards) {
            String front = card.getFrontText();
            String back = card.getBackText();
            boolean hasFront = front != null && !front.isBlank();
            boolean hasBack = back != null && !back.isBlank();
            if (!hasFront && !hasBack) continue;
            count++;
            sb.append("Card ").append(count).append(":\n");
            if (hasFront) sb.append("  Q: ").append(front.strip()).append("\n");
            if (hasBack) sb.append("  A: ").append(back.strip()).append("\n");
        }
        return sb.toString();
    }

    private String buildTextDocContent(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (DocumentInput doc : documents) {
            if (!(doc instanceof TextDocument td)) continue;
            if (td.extractedText() == null || td.extractedText().isBlank()) continue;
            n++;
            sb.append("Document ").append(n).append(" — ").append(td.filename()).append(":\n")
              .append(td.extractedText()).append("\n\n");
        }
        return sb.toString();
    }

    private String buildPdfListing(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DocumentInput doc : documents) {
            if (doc instanceof PdfDocument pd) {
                sb.append("- ").append(pd.filename()).append("\n");
            }
        }
        return sb.toString();
    }

    private Media[] buildPdfMedia(List<DocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return new Media[0];
        return documents.stream()
                .filter(d -> d instanceof PdfDocument)
                .map(d -> (PdfDocument) d)
                .map(pd -> new Media(new MimeType("application", "pdf"), pd.source()))
                .toArray(Media[]::new);
    }

    private String buildPrompt(String cardContent, String docContent, String pdfListing,
                                int count, QuizQuestionMode mode, Difficulty difficulty,
                                int mcqCount, int tfCount) {
        String modeDescription = switch (mode) {
            case MCQ_ONLY -> "multiple-choice questions";
            case TF_ONLY -> "true/false questions";
            case MIXED -> "questions: exactly " + mcqCount + " multiple-choice and " + tfCount + " true/false";
        };

        String difficultyInstruction = switch (difficulty) {
            case EASY -> "Direct recall. Questions test obvious facts. Distractors are clearly wrong.";
            case MEDIUM -> "Minor inference required. Distractors are plausible but distinguishable on careful reading.";
            case HARD -> "Synthesis across sources. Distractors share surface features with the answer; require precise understanding.";
        };

        String typeRules = switch (mode) {
            case MCQ_ONLY -> "- All questions are MULTIPLE_CHOICE with exactly 4 options. Exactly one correct.";
            case TF_ONLY -> "- All questions are TRUE_FALSE. options must be exactly [\"True\",\"False\"]. correctOptionIndex is 0 (True) or 1 (False).";
            case MIXED -> """
                - All MULTIPLE_CHOICE questions have exactly 4 options. Exactly one correct.
                - All TRUE_FALSE questions: options must be exactly ["True","False"]. correctOptionIndex is 0 (True) or 1 (False).
                - Approximately half each type as specified above.""";
        };

        String cardSection = cardContent.isBlank() ? "(none)" : cardContent;
        String docSection  = docContent.isBlank()  ? "(none)" : docContent;
        String pdfSection  = pdfListing.isBlank()  ? "(none)" : pdfListing;

        return ("You are a study assistant. Generate %d %s based on the source material below.\n\n"
            + "DIFFICULTY: %s\n\n"
            + "TOPIC FOCUS:\n"
            + "First identify the dominant subject matter of the supplied content. Generate\n"
            + "questions only about concepts that belong to that subject. Ignore incidental\n"
            + "metadata such as author names, page numbers, publication dates, headers, footers,\n"
            + "or off-topic asides.\n\n"
            + "SOME CARDS MAY HAVE MISSING CONTEXT:\n"
            + "Some flashcards may have had images removed. If a card's text alone is\n"
            + "insufficient to form a meaningful question (e.g. it references \"this diagram\"\n"
            + "or \"the image above\"), skip that card.\n\n"
            + "QUESTION TYPE RULES:\n"
            + "%s\n\n"
            + "GENERAL RULES:\n"
            + "- Each question stands alone — do not reference \"the text\" or \"the document\".\n"
            + "- correctOptionIndex is 0-based.\n"
            + "- Vary which index is correct across questions.\n"
            + "- Test understanding, not exact wording.\n"
            + "- Use the attached PDF documents (including their figures, diagrams, and tables) as primary source material for question generation.\n\n"
            + "=== FLASHCARDS ===\n"
            + "%s\n\n"
            + "=== DOCUMENTS ===\n"
            + "%s\n\n"
            + "=== ATTACHED PDFs ===\n"
            + "%s\n"
        ).formatted(count, modeDescription, difficultyInstruction, typeRules, cardSection, docSection, pdfSection);
    }
}
```

Key changes from the prior file:
- New imports: `BeanOutputConverter`, `GoogleGenAiChatOptions`, `QuizQuestionsResponse`. Removed: `JsonNode`, JSON node parsing.
- Constructor builds and caches `responseSchema` via `BeanOutputConverter.getJsonSchema()`. The `JsonMapper` is no longer stored as a field (it's only needed at construction).
- The chat call uses `.options(...)` and `.entity(QuizQuestionsResponse.class)`. The mapper read step and `extractJson` helper are deleted.
- The prompt's trailing `"Respond ONLY with valid JSON, no extra text. Schema: …"` paragraph is removed (schema is enforced server-side).
- `extractJson(...)` private method is deleted entirely.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiQuizServiceTests`
Expected: all tests in `AiQuizServiceTests` pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiQuizService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiQuizServiceTests.java
git commit -m "refactor(ai): migrate AiQuizService to Gemini structured output"
```

---

## Task 3: Add LANGUAGE + COVERAGE blocks to `AiQuizService` prompt

**Files:**

- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiQuizService.java` (the `buildPrompt` method only)
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiQuizServiceTests.java`

- [ ] **Step 1: Add failing test asserting the prompt contains both new blocks**

Append to `AiQuizServiceTests.java`:

```java
@Test
void generate_PromptContainsLanguageAndCoverageBlocks() {
    when(callSpec.entity(QuizQuestionsResponse.class)).thenReturn(wrap(
        mcq("Q1", 0), mcq("Q2", 0), mcq("Q3", 0)
    ));

    service.generate(cards(2), List.of(), 3, QuizQuestionMode.MCQ_ONLY, Difficulty.EASY);

    assertThat(capturedPrompt.get()).contains("LANGUAGE:");
    assertThat(capturedPrompt.get()).contains("Detect the dominant natural language");
    assertThat(capturedPrompt.get()).contains("COVERAGE:");
    assertThat(capturedPrompt.get()).contains("early third, middle third");
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiQuizServiceTests#generate_PromptContainsLanguageAndCoverageBlocks`
Expected: FAIL — the prompt doesn't contain `"LANGUAGE:"` yet.

- [ ] **Step 3: Edit `buildPrompt(...)` to insert the new blocks**

In `AiQuizService.buildPrompt(...)`, change the returned template so that:
- A `LANGUAGE:` block is inserted right after the `"You are a study assistant..."` line.
- A `COVERAGE:` block is inserted right after the `TOPIC FOCUS:` block.

Replace the `return (...)` expression with:

```java
return ("You are a study assistant. Generate %d %s based on the source material below.\n\n"
    + "LANGUAGE:\n"
    + "Detect the dominant natural language of the supplied source material (flashcards,\n"
    + "documents, and attached PDFs together). Write every output string — question text,\n"
    + "answer options, hints — in that same language. If sources mix languages, use the\n"
    + "most-prevalent one. Do not translate technical terms, proper nouns, or code.\n\n"
    + "DIFFICULTY: %s\n\n"
    + "TOPIC FOCUS:\n"
    + "First identify the dominant subject matter of the supplied content. Generate\n"
    + "questions only about concepts that belong to that subject. Ignore incidental\n"
    + "metadata such as author names, page numbers, publication dates, headers, footers,\n"
    + "or off-topic asides.\n\n"
    + "COVERAGE:\n"
    + "Distribute the %d requested questions evenly across the entire source material.\n"
    + "- Treat each attached PDF and each text document as a separate source.\n"
    + "- Within each source, sample roughly equally from the early third, middle third,\n"
    + "  and final third (by page count for PDFs, by length for text).\n"
    + "- If multiple sources are supplied, allocate questions proportionally to source\n"
    + "  length, but ensure every source contributes at least one question when N is\n"
    + "  large enough.\n"
    + "- Do not cluster on introductions, abstracts, tables of contents, or the first\n"
    + "  few pages. Skip these unless they contain core subject matter.\n"
    + "- Before writing questions, sketch a brief internal coverage plan (which\n"
    + "  pages/sections each question will draw from). Do not include the plan in\n"
    + "  the JSON output.\n\n"
    + "SOME CARDS MAY HAVE MISSING CONTEXT:\n"
    + "Some flashcards may have had images removed. If a card's text alone is\n"
    + "insufficient to form a meaningful question (e.g. it references \"this diagram\"\n"
    + "or \"the image above\"), skip that card.\n\n"
    + "QUESTION TYPE RULES:\n"
    + "%s\n\n"
    + "GENERAL RULES:\n"
    + "- Each question stands alone — do not reference \"the text\" or \"the document\".\n"
    + "- correctOptionIndex is 0-based.\n"
    + "- Vary which index is correct across questions.\n"
    + "- Test understanding, not exact wording.\n"
    + "- Use the attached PDF documents (including their figures, diagrams, and tables) as primary source material for question generation.\n\n"
    + "=== FLASHCARDS ===\n"
    + "%s\n\n"
    + "=== DOCUMENTS ===\n"
    + "%s\n\n"
    + "=== ATTACHED PDFs ===\n"
    + "%s\n"
).formatted(count, modeDescription, difficultyInstruction, count, typeRules, cardSection, docSection, pdfSection);
```

Note the format-arg count grows from 7 to 8 (the new `%d` in the COVERAGE block consumes one).

- [ ] **Step 4: Run all `AiQuizServiceTests` to verify pass**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiQuizServiceTests`
Expected: all tests pass, including the new one.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiQuizService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiQuizServiceTests.java
git commit -m "feat(ai): add LANGUAGE and COVERAGE rules to quiz prompt"
```

---

## Task 4: Migrate `AiExamService.generate(...)` to structured output

**Files:**

- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java`

- [ ] **Step 1: Update `AiExamServiceTests.java` `@BeforeEach` to capture options and stub `.entity(...)`**

Replace the existing `setUp()` (keep the duplicate-`.user(anyString())` stub used by grading — that one still calls `.content()` until Task 6) with:

```java
private ChatClient.CallResponseSpec callSpec;
private AiExamService service;
private final AtomicReference<String> capturedPrompt = new AtomicReference<>();
private final List<org.springframework.ai.content.Media> capturedMedia = new ArrayList<>();
private final AtomicReference<org.springframework.ai.chat.prompt.ChatOptions.Builder> capturedOptionsBuilder = new AtomicReference<>();

@BeforeEach
void setUp() {
    capturedPrompt.set(null);
    capturedMedia.clear();
    capturedOptionsBuilder.set(null);

    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    callSpec = mock(ChatClient.CallResponseSpec.class);
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.Builder builder = mock(ChatClient.Builder.class);

    when(builder.build()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);

    when(requestSpec.options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class)))
        .thenAnswer(invocation -> {
            capturedOptionsBuilder.set(invocation.getArgument(0));
            return requestSpec;
        });

    when(requestSpec.user(any(Consumer.class))).thenAnswer(invocation -> {
        Consumer<ChatClient.PromptUserSpec> consumer = invocation.getArgument(0);
        ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
        when(userSpec.text(anyString())).thenAnswer(a -> {
            capturedPrompt.set(a.getArgument(0));
            return userSpec;
        });
        when(userSpec.media(any(org.springframework.ai.content.Media.class))).thenAnswer(a -> {
            for (Object arg : a.getRawArguments()) {
                if (arg instanceof org.springframework.ai.content.Media m) {
                    capturedMedia.add(m);
                } else if (arg instanceof org.springframework.ai.content.Media[] arr) {
                    Collections.addAll(capturedMedia, arr);
                }
            }
            return userSpec;
        });
        consumer.accept(userSpec);
        return requestSpec;
    });
    // Grading path uses .user(String) — kept until Task 6 rewires it
    when(requestSpec.user(anyString())).thenAnswer(invocation -> {
        capturedPrompt.set(invocation.getArgument(0));
        return requestSpec;
    });
    when(requestSpec.call()).thenReturn(callSpec);

    service = new AiExamService(builder, new JsonMapper());
}
```

Add imports:
```java
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionsResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
```

- [ ] **Step 2: Rewrite generation tests to stub `.entity(ExamQuestionsResponse.class)`**

Add helpers at the bottom of the file:

```java
private ExamQuestionsResponse wrapQuestions(ExamQuestion... questions) {
    return new ExamQuestionsResponse(List.of(questions));
}

private ExamQuestionsResponse threeExamQuestions() {
    return wrapQuestions(
        new ExamQuestion("Q1", "Hint 1"),
        new ExamQuestion("Q2", "Hint 2"),
        new ExamQuestion("Q3", "Hint 3")
    );
}
```

Then update each `generate_*` test to stub `.entity(...)` instead of `.content()`:

| Existing test | New stub |
| --- | --- |
| `generate_TextDocument_includedInPromptDocumentsSection` | `when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());` |
| `generate_PdfDocument_attachedAsMediaAndListedInPrompt` | same |
| `generate_MixedTextAndPdfDocs_bothSectionsPopulated` | same |
| `generate_PdfOnly_NoFlashcardsNoText_DoesNotThrow` | same |
| `generate_ProviderFailure_throwsStableRetryMessage` | `when(callSpec.entity(ExamQuestionsResponse.class)).thenThrow(new RuntimeException("provider offline"));` |
| `generate_ParseFailure_stillUsesParseSpecificMessage` | `when(callSpec.entity(ExamQuestionsResponse.class)).thenThrow(new RuntimeException("parse failed"));` — if parse can no longer be distinguished, **delete the test** (see Task 2 Step 2 note). |

Delete the old `threeQuestions()` raw-JSON helper (no longer used by generation tests).

> **Leave the grading tests untouched.** Tasks 6/7 handle `grade(...)`. Until then, `grade_ParsesValidJsonResponse`, `grade_ProviderFailure_throwsStableRetryMessage`, and `grade_ParseFailure_stillUsesParseSpecificMessage` keep using `when(callSpec.content()).thenReturn(...)` — they still pass because Task 4 does not change `AiExamService.grade(...)`.

- [ ] **Step 3: Add new test asserting structured-output options are configured**

Append to `AiExamServiceTests.java`:

```java
@Test
void generate_ConfiguresStructuredOutputOptions() {
    when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

    service.generate(cards(2), List.of(), 3, ExamQuestionSize.MEDIUM);

    assertThat(capturedOptionsBuilder.get()).isNotNull();
    var built = (GoogleGenAiChatOptions) capturedOptionsBuilder.get().build();
    assertThat(built.getResponseMimeType()).isEqualTo("application/json");
    assertThat(built.getResponseSchema()).isNotBlank();
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiExamServiceTests`
Expected: generation tests fail (`callSpec.entity(...)` is stubbed but the service still calls `.content()`).

- [ ] **Step 5: Rewrite `AiExamService.generate(...)` to use structured output**

Modify `AiExamService.java`:

1. **Imports** — add:
   ```java
   import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionsResponse;
   import org.springframework.ai.converter.BeanOutputConverter;
   import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
   ```
   Remove the unused `JsonNode` import after this step.

2. **Constructor and fields** — replace the `JsonMapper objectMapper` field with two new caches; keep `objectMapper` only inside the constructor scope:

   ```java
   private final ChatClient chatClient;
   private final String questionsResponseSchema;
   private final JsonMapper objectMapper; // still used by grade(...) until Task 6

   public AiExamService(ChatClient.Builder builder, JsonMapper objectMapper) {
       this.chatClient = builder.build();
       this.objectMapper = objectMapper;
       this.questionsResponseSchema = new BeanOutputConverter<>(ExamQuestionsResponse.class, objectMapper).getJsonSchema();
   }
   ```

3. **`generate(...)` body** — replace the chat-call + parse blocks with:

   ```java
   ExamQuestionsResponse response;
   try {
       response = chatClient.prompt()
               .options(GoogleGenAiChatOptions.builder()
                   .responseMimeType("application/json")
                   .responseSchema(questionsResponseSchema))
               .user(u -> {
                   u.text(prompt);
                   if (pdfMedia.length > 0) u.media(pdfMedia);
               })
               .call()
               .entity(ExamQuestionsResponse.class);
   } catch (Exception e) {
       throw new IllegalStateException("AI request failed, please retry with fewer or smaller PDFs.", e);
   }

   try {
       List<ExamQuestion> rawList = response == null || response.questions() == null
           ? List.of()
           : response.questions();

       List<ExamQuestion> valid = new ArrayList<>();
       for (ExamQuestion q : rawList) {
           if (q == null || q.questionText() == null || q.questionText().isBlank()) continue;
           valid.add(q);
       }

       if (valid.size() < Math.max(1, questionCount / 2)) {
           throw new IllegalStateException("AI returned too few valid questions; please retry.");
       }

       return valid.stream().limit(questionCount).toList();

   } catch (IllegalStateException | IllegalArgumentException e) {
       throw e;
   } catch (Exception e) {
       throw new IllegalStateException("Could not parse the AI response. Please try again.", e);
   }
   ```

4. **Prompt** — in `buildGenerationPrompt(...)`, remove the trailing
   ```
   + "Respond ONLY with valid JSON, no extra text. Schema:\n"
   + "{\"questions\":[\n"
   + "  {\"questionText\":\"...\",\"expectedAnswerHints\":\"...\"}\n"
   + "]}"
   ```
   so the prompt ends after the `=== ATTACHED PDFs ===\n%s\n` line. The LANGUAGE/COVERAGE blocks come in Task 5.

5. **Helpers** — leave `extractJson(...)` for now; it's still used by `grade(...)`. It will be deleted in Task 6.

- [ ] **Step 6: Run all `AiExamServiceTests` to verify pass**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiExamServiceTests`
Expected: all generation tests pass; the three grading tests still pass (untouched).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java
git commit -m "refactor(ai): migrate AiExamService.generate to Gemini structured output"
```

---

## Task 5: Add LANGUAGE + COVERAGE blocks to `AiExamService.generate(...)` prompt

**Files:**

- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java` (`buildGenerationPrompt` only)
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java`

- [ ] **Step 1: Add failing test**

Append:

```java
@Test
void generate_PromptContainsLanguageAndCoverageBlocks() {
    when(callSpec.entity(ExamQuestionsResponse.class)).thenReturn(threeExamQuestions());

    service.generate(cards(2), List.of(), 3, ExamQuestionSize.MEDIUM);

    assertThat(capturedPrompt.get()).contains("LANGUAGE:");
    assertThat(capturedPrompt.get()).contains("Detect the dominant natural language");
    assertThat(capturedPrompt.get()).contains("COVERAGE:");
    assertThat(capturedPrompt.get()).contains("early third, middle third");
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiExamServiceTests#generate_PromptContainsLanguageAndCoverageBlocks`
Expected: FAIL.

- [ ] **Step 3: Edit `buildGenerationPrompt(...)` to insert the new blocks**

Replace the `return (...).formatted(...)` expression at the end of `buildGenerationPrompt(...)` with:

```java
return ("You are a study assistant. Generate %d exam questions based on the source material below.\n\n"
        + "LANGUAGE:\n"
        + "Detect the dominant natural language of the supplied source material (flashcards,\n"
        + "documents, and attached PDFs together). Write every output string — question text\n"
        + "and expectedAnswerHints — in that same language. If sources mix languages, use the\n"
        + "most-prevalent one. Do not translate technical terms, proper nouns, or code.\n\n"
        + "QUESTION DEPTH:\n"
        + "%s\n\n"
        + "TOPIC FOCUS:\n"
        + "First identify the dominant subject matter of the supplied content. Generate\n"
        + "questions only about concepts that belong to that subject. Ignore incidental\n"
        + "metadata such as author names, page numbers, publication dates, headers, footers,\n"
        + "or off-topic asides.\n\n"
        + "COVERAGE:\n"
        + "Distribute the %d requested questions evenly across the entire source material.\n"
        + "- Treat each attached PDF and each text document as a separate source.\n"
        + "- Within each source, sample roughly equally from the early third, middle third,\n"
        + "  and final third (by page count for PDFs, by length for text).\n"
        + "- If multiple sources are supplied, allocate questions proportionally to source\n"
        + "  length, but ensure every source contributes at least one question when N is\n"
        + "  large enough.\n"
        + "- Do not cluster on introductions, abstracts, tables of contents, or the first\n"
        + "  few pages. Skip these unless they contain core subject matter.\n"
        + "- Before writing questions, sketch a brief internal coverage plan (which\n"
        + "  pages/sections each question will draw from). Do not include the plan in\n"
        + "  the JSON output.\n\n"
        + "SOME CARDS MAY HAVE MISSING CONTEXT:\n"
        + "Some flashcards may have had images removed. If a card's text alone is\n"
        + "insufficient to form a meaningful question (e.g. it references \"this diagram\"\n"
        + "or \"the image above\"), skip that card.\n\n"
        + "GENERAL RULES:\n"
        + "- Each question stands alone — do not reference \"the text\" or \"the document\".\n"
        + "- Test understanding, not exact wording.\n"
        + "- Use the attached PDF documents (including their figures, diagrams, and tables) as primary source material for question generation.\n"
        + "- For each question, provide 'expectedAnswerHints' which is a 2–3 sentence rubric\n"
        + "  describing what a perfect answer should contain. This rubric is never shown to the user.\n\n"
        + "=== FLASHCARDS ===\n"
        + "%s\n\n"
        + "=== DOCUMENTS ===\n"
        + "%s\n\n"
        + "=== ATTACHED PDFs ===\n"
        + "%s\n"
).formatted(count, sizeInstruction, count, cardSection, docSection, pdfSection);
```

Format-arg count grows from 5 to 6 (the new `%d` in the COVERAGE block consumes one).

- [ ] **Step 4: Run all `AiExamServiceTests` to verify pass**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiExamServiceTests`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java
git commit -m "feat(ai): add LANGUAGE and COVERAGE rules to exam generation prompt"
```

---

## Task 6: Migrate `AiExamService.grade(...)` to structured output

**Files:**

- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java`

- [ ] **Step 1: Rewrite the three grading tests to stub `.entity(ExamGradingResult.class)`**

In `AiExamServiceTests.java`, replace the three grading tests:

```java
@Test
void grade_ReturnsTypedResult() {
    var perQ = new ExamGradingResult.PerQuestion(90, "Good.");
    var overall = new ExamGradingResult.Overall(
        90,
        List.of("Strong recall"),
        List.of("Minor details"),
        List.of("Cell cycle"),
        List.of("Review notes")
    );
    when(callSpec.entity(ExamGradingResult.class))
        .thenReturn(new ExamGradingResult(List.of(perQ), overall));

    var questions = List.of(new ExamQuestion("Explain mitosis", "Mention phases and checkpoints"));
    var result = service.grade(questions, Map.of(0, "Mitosis has prophase, metaphase, anaphase, telophase."), ExamQuestionSize.MEDIUM);

    assertThat(result.perQuestion()).hasSize(1);
    assertThat(result.perQuestion().get(0).scorePercent()).isEqualTo(90);
    assertThat(result.overall().scorePercent()).isEqualTo(90);
}

@Test
void grade_ProviderFailure_throwsStableRetryMessage() {
    when(callSpec.entity(ExamGradingResult.class)).thenThrow(new RuntimeException("provider offline"));

    var questions = List.of(new ExamQuestion("Q1", "H1"));

    assertThatThrownBy(() -> service.grade(questions, Map.of(0, "A1"), ExamQuestionSize.SHORT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("AI grading request failed. Please try again.")
        .hasCauseInstanceOf(RuntimeException.class);
}
```

The nested record names match `dto/ExamGradingResult.java`: `ExamGradingResult.PerQuestion(int scorePercent, String feedback)` and `ExamGradingResult.Overall(int, List<String> strengths, List<String> weaknesses, List<String> topicsToRevisit, List<String> suggestedNextSteps)`.

Delete the old `grade_ParseFailure_stillUsesParseSpecificMessage` test — once `.entity(...)` is in use, parse failure manifests as a `RuntimeException` that is indistinguishable from provider failure and is already covered by `grade_ProviderFailure_throwsStableRetryMessage`. Leave a single short comment in the file noting why it was removed (`// parse-failure path collapsed into provider-failure under structured output`).

Also delete the `when(requestSpec.user(anyString()))` stub line from `setUp()` — the grading path no longer uses `.user(String)`.

- [ ] **Step 2: Add new grading-options test**

```java
@Test
void grade_ConfiguresStructuredOutputOptions() {
    var perQ = new ExamGradingResult.PerQuestion(80, "OK.");
    var overall = new ExamGradingResult.Overall(80, List.of(), List.of(), List.of(), List.of());
    when(callSpec.entity(ExamGradingResult.class))
        .thenReturn(new ExamGradingResult(List.of(perQ), overall));

    service.grade(List.of(new ExamQuestion("Q1", "H1")), Map.of(0, "A1"), ExamQuestionSize.SHORT);

    assertThat(capturedOptionsBuilder.get()).isNotNull();
    var built = (GoogleGenAiChatOptions) capturedOptionsBuilder.get().build();
    assertThat(built.getResponseMimeType()).isEqualTo("application/json");
    assertThat(built.getResponseSchema()).isNotBlank();
}
```

- [ ] **Step 3: Run tests to verify failures**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiExamServiceTests`
Expected: the new grading tests fail (the service still calls `.content()` for grading).

- [ ] **Step 4: Rewrite `AiExamService.grade(...)` to use structured output**

In `AiExamService.java`:

1. **Add cached schema** for grading. Update fields and constructor:

   ```java
   private final ChatClient chatClient;
   private final String questionsResponseSchema;
   private final String gradingResponseSchema;

   public AiExamService(ChatClient.Builder builder, JsonMapper objectMapper) {
       this.chatClient = builder.build();
       this.questionsResponseSchema = new BeanOutputConverter<>(ExamQuestionsResponse.class, objectMapper).getJsonSchema();
       this.gradingResponseSchema = new BeanOutputConverter<>(ExamGradingResult.class, objectMapper).getJsonSchema();
   }
   ```

   Remove the `objectMapper` field — no longer needed at runtime.

2. **Replace `grade(...)`** body:

   ```java
   public ExamGradingResult grade(
           List<ExamQuestion> questions,
           Map<Integer, String> userAnswers,
           ExamQuestionSize size) {

       String prompt = buildGradingPrompt(questions, userAnswers, size);

       try {
           return chatClient.prompt()
                   .options(GoogleGenAiChatOptions.builder()
                       .responseMimeType("application/json")
                       .responseSchema(gradingResponseSchema))
                   .user(u -> u.text(prompt))
                   .call()
                   .entity(ExamGradingResult.class);
       } catch (Exception e) {
           throw new IllegalStateException("AI grading request failed. Please try again.", e);
       }
   }
   ```

   Note: the grading call now also uses the `Consumer<PromptUserSpec>` form, so it goes through the same mock branch as generation and the prompt is captured the same way.

3. **`buildGradingPrompt(...)`** — remove the trailing inline JSON schema (`Respond ONLY with valid JSON. Schema:` ... up to the closing `}`). The method should end after the `EXAM DATA` block.

4. **Delete `extractJson(...)`** — no longer used anywhere in this class.

5. **Imports** — drop `JsonNode` if still imported.

- [ ] **Step 5: Run all `AiExamServiceTests` to verify pass**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiExamServiceTests`
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java
git commit -m "refactor(ai): migrate AiExamService.grade to Gemini structured output"
```

---

## Task 7: Add LANGUAGE block to `AiExamService.grade(...)` prompt

**Files:**

- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java` (`buildGradingPrompt` only)
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java`

- [ ] **Step 1: Add failing test**

Append:

```java
@Test
void grade_PromptContainsLanguageBlock() {
    var perQ = new ExamGradingResult.PerQuestion(80, "OK.");
    var overall = new ExamGradingResult.Overall(80, List.of(), List.of(), List.of(), List.of());
    when(callSpec.entity(ExamGradingResult.class))
        .thenReturn(new ExamGradingResult(List.of(perQ), overall));

    service.grade(List.of(new ExamQuestion("Q1", "H1")), Map.of(0, "A1"), ExamQuestionSize.SHORT);

    assertThat(capturedPrompt.get()).contains("LANGUAGE:");
    assertThat(capturedPrompt.get()).contains("language of the questions and user answers");
    assertThat(capturedPrompt.get()).doesNotContain("COVERAGE:");
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiExamServiceTests#grade_PromptContainsLanguageBlock`
Expected: FAIL.

- [ ] **Step 3: Edit `buildGradingPrompt(...)` to add the LANGUAGE block**

Modify the top of the method so the first appended block becomes:

```java
StringBuilder sb = new StringBuilder();
sb.append("You are an expert grader. Grade the following exam answers based on the provided questions and rubrics.\n\n");
sb.append("LANGUAGE:\n");
sb.append("Detect the dominant natural language of the questions and user answers below.\n");
sb.append("Write every output string — feedback, strengths, weaknesses, topicsToRevisit,\n");
sb.append("suggestedNextSteps — in that same language. Do not translate technical terms,\n");
sb.append("proper nouns, or code.\n\n");
sb.append("GRADING RULES:\n");
// ...existing rules continue unchanged...
```

Leave the rest of the method body as-is (rules, `EXAM DATA` block, end). The inline JSON schema block was already removed in Task 6.

- [ ] **Step 4: Run all `AiExamServiceTests` to verify pass**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiExamServiceTests`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiExamServiceTests.java
git commit -m "feat(ai): add LANGUAGE rule to exam grading prompt"
```

---

## Task 8: Migrate `AiFlashcardService` to structured output

**Files:**

- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java`

- [ ] **Step 1: Update `@BeforeEach` to capture options and stub `.entity(...)`**

Apply the same transformation as Task 2 Step 1 (capture `options` builder, keep stub for `.user(Consumer)`, keep stub for `.call()`). Add imports:

```java
import com.HendrikHoemberg.StudyHelper.dto.FlashcardsResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
```

Add a private `AtomicReference<org.springframework.ai.chat.prompt.ChatOptions.Builder> capturedOptionsBuilder` field and clear it in `setUp()`. Stub:

```java
when(requestSpec.options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class)))
    .thenAnswer(invocation -> {
        capturedOptionsBuilder.set(invocation.getArgument(0));
        return requestSpec;
    });
```

- [ ] **Step 2: Rewrite the existing tests to stub `.entity(FlashcardsResponse.class)`**

Add this helper at the bottom of the test class:

```java
private FlashcardsResponse twoFlashcards() {
    return new FlashcardsResponse(List.of(
        new GeneratedFlashcard("Front 1", "Back 1"),
        new GeneratedFlashcard("Front 2", "Back 2")
    ));
}

private FlashcardsResponse manyFlashcards(int n) {
    var list = new ArrayList<GeneratedFlashcard>();
    for (int i = 1; i <= n; i++) {
        list.add(new GeneratedFlashcard("Front " + i, "Back " + i));
    }
    return new FlashcardsResponse(list);
}
```

Then transform each test (keep every existing assertion that targets prompt text, media, or returned values):

| Existing test | New stub |
| --- | --- |
| `generate_TextDocument_buildsPromptWithoutMedia` | `when(callSpec.entity(FlashcardsResponse.class)).thenReturn(twoFlashcards());` |
| `generate_PdfDocument_buildsPromptAndAttachesPdfMedia` | same |
| `generate_BlankCardsAreDroppedAndValuesAreTrimmed` | `when(callSpec.entity(FlashcardsResponse.class)).thenReturn(new FlashcardsResponse(List.of(new GeneratedFlashcard("  Front 1  ","  Back 1  "), new GeneratedFlashcard("   ","ignored"), new GeneratedFlashcard("Front 2","   "))));` |
| `generate_AllCardsFilteredOut_ThrowsIllegalState` | `when(callSpec.entity(FlashcardsResponse.class)).thenReturn(new FlashcardsResponse(List.of(new GeneratedFlashcard("   ","   "), new GeneratedFlashcard("","ignored"))));` |
| `generate_MoreThanFiftyCards_CapsAtFifty` | `when(callSpec.entity(FlashcardsResponse.class)).thenReturn(manyFlashcards(55));` |
| `generate_ProviderFailure_throwsStableRetryMessage` | `when(callSpec.entity(FlashcardsResponse.class)).thenThrow(new RuntimeException("provider offline"));` |
| `generate_ParseFailure_throwsStableRetryMessage` | **Delete this test.** With schema-enforced output the parse-failure path collapses into provider failure; the catch-all in the service still produces `"Could not parse the AI response. Please try again."`, but there is no longer a clean way to provoke it from the mock without smuggling a malformed wrapper. Provider failure coverage above is sufficient. |
| `generate_NoUsableSources_throwsIllegalArgument` | Unchanged — does not stub the chat call at all. |

Delete the now-unused helpers `validFlashcardsJson()` and `manyFlashcardsJson(int)` from the test file.

- [ ] **Step 3: Add structured-output options test**

```java
@Test
void generate_ConfiguresStructuredOutputOptions() {
    when(callSpec.entity(FlashcardsResponse.class)).thenReturn(
        new FlashcardsResponse(List.of(new GeneratedFlashcard("F", "B")))
    );

    var resource = new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46});
    service.generate(new PdfDocument("any.pdf", resource));

    assertThat(capturedOptionsBuilder.get()).isNotNull();
    var built = (GoogleGenAiChatOptions) capturedOptionsBuilder.get().build();
    assertThat(built.getResponseMimeType()).isEqualTo("application/json");
    assertThat(built.getResponseSchema()).isNotBlank();
}
```

- [ ] **Step 4: Run tests to verify failures**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiFlashcardServiceTests`
Expected: tests fail (service still calls `.content()`).

- [ ] **Step 5: Rewrite `AiFlashcardService.generate(...)` to use structured output**

In `AiFlashcardService.java`:

1. **Imports** — add `BeanOutputConverter`, `GoogleGenAiChatOptions`, `FlashcardsResponse`. Drop `JsonNode`.

2. **Fields and constructor**:

   ```java
   private static final int MAX_FLASHCARDS = 50;

   private final ChatClient chatClient;
   private final String responseSchema;

   public AiFlashcardService(ChatClient.Builder builder, JsonMapper objectMapper) {
       this.chatClient = builder.build();
       this.responseSchema = new BeanOutputConverter<>(FlashcardsResponse.class, objectMapper).getJsonSchema();
   }
   ```

3. **`generate(...)` body** — replace the chat call + parse blocks with:

   ```java
   FlashcardsResponse response;
   try {
       response = chatClient.prompt()
           .options(GoogleGenAiChatOptions.builder()
               .responseMimeType("application/json")
               .responseSchema(responseSchema))
           .user(u -> {
               u.text(prompt);
               if (pdfMedia.length > 0) u.media(pdfMedia);
           })
           .call()
           .entity(FlashcardsResponse.class);
   } catch (Exception e) {
       throw new IllegalStateException("AI request failed, please retry with fewer or smaller PDFs.", e);
   }

   try {
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
           throw new IllegalStateException("AI returned no valid flashcards; please retry.");
       }

       return valid.stream().limit(MAX_FLASHCARDS).toList();

   } catch (IllegalStateException | IllegalArgumentException e) {
       throw e;
   } catch (Exception e) {
       throw new IllegalStateException("Could not parse the AI response. Please try again.", e);
   }
   ```

4. **Prompt** — in `buildPrompt(...)`, remove the trailing
   ```
   + "Respond ONLY with valid JSON, no extra text. Schema:\n"
   + "{\"flashcards\":[{\"frontText\":\"...\",\"backText\":\"...\"}]}"
   ```
   so the prompt ends after the `=== ATTACHED PDFs ===\n%s\n` line.

5. **Delete `extractJson(...)`** — no longer used.

- [ ] **Step 6: Run all `AiFlashcardServiceTests` to verify pass**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiFlashcardServiceTests`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java
git commit -m "refactor(ai): migrate AiFlashcardService to Gemini structured output"
```

---

## Task 9: Add LANGUAGE + light COVERAGE blocks to `AiFlashcardService` prompt

**Files:**

- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java` (`buildPrompt` only)
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java`

- [ ] **Step 1: Add failing test**

Append:

```java
@Test
void generate_PromptContainsLanguageAndCoverageBlocks() {
    when(callSpec.entity(FlashcardsResponse.class)).thenReturn(
        new FlashcardsResponse(List.of(new GeneratedFlashcard("F", "B")))
    );

    var resource = new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46});
    service.generate(new PdfDocument("any.pdf", resource));

    assertThat(capturedPrompt.get()).contains("LANGUAGE:");
    assertThat(capturedPrompt.get()).contains("Detect the dominant natural language");
    assertThat(capturedPrompt.get()).contains("COVERAGE:");
    assertThat(capturedPrompt.get()).contains("across the entire document");
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiFlashcardServiceTests#generate_PromptContainsLanguageAndCoverageBlocks`
Expected: FAIL.

- [ ] **Step 3: Edit `buildPrompt(...)` to insert the new blocks**

Replace the `return (...).formatted(...)` expression with:

```java
return ("You are a study assistant. Generate flashcards based on the source material below.\n\n"
    + "LANGUAGE:\n"
    + "Detect the dominant natural language of the supplied source material (document and\n"
    + "any attached PDF together). Write every output string — frontText, backText — in\n"
    + "that same language. If the source mixes languages, use the most-prevalent one. Do\n"
    + "not translate technical terms, proper nouns, or code.\n\n"
    + "Maximum flashcards: %d\n\n"
    + "COVERAGE:\n"
    + "Draw flashcards from across the entire document. Do not concentrate on the\n"
    + "opening pages; ensure later sections are represented proportionally to their\n"
    + "content density.\n\n"
    + "GENERAL RULES:\n"
    + "- First identify the dominant educational content of the source material.\n"
    + "- Ignore metadata, headers, footers, page numbers, and incidental details.\n"
    + "- Create concise front and back text for each flashcard.\n"
    + "- Keep each card self-contained and avoid duplicate cards.\n"
    + "- Use the attached PDF documents as primary source material when present.\n\n"
    + "=== DOCUMENTS ===\n"
    + "%s\n\n"
    + "=== ATTACHED PDFs ===\n"
    + "%s\n"
).formatted(MAX_FLASHCARDS, docSection, pdfSection);
```

Format-arg count stays at 3 (`MAX_FLASHCARDS`, `docSection`, `pdfSection`).

- [ ] **Step 4: Run all `AiFlashcardServiceTests` to verify pass**

Run: `./mvnw -q -DskipTests=false test -Dtest=AiFlashcardServiceTests`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardServiceTests.java
git commit -m "feat(ai): add LANGUAGE and COVERAGE rules to flashcard prompt"
```

---

## Task 10: Full build + test verification

- [ ] **Step 1: Run the full Maven verify**

Run: `./mvnw -q test`
Expected: `BUILD SUCCESS`, all tests pass across the three AI service test classes and every other test.

- [ ] **Step 2: Confirm no stray references to deleted code**

Run:

```bash
grep -RIn "extractJson" src/ || echo "no matches"
grep -RIn 'Respond ONLY with valid JSON' src/main/java/com/HendrikHoemberg/StudyHelper/service/ || echo "no matches"
```

Expected: both report `no matches`.

- [ ] **Step 3: Confirm structured output is configured for every AI call**

Run:

```bash
grep -RIn "responseMimeType" src/main/java/com/HendrikHoemberg/StudyHelper/service/
grep -RIn ".entity(" src/main/java/com/HendrikHoemberg/StudyHelper/service/
```

Expected: each AI service prints at least one match for both queries — `AiQuizService` (1 each), `AiExamService` (2 each: generate + grade), `AiFlashcardService` (1 each).

- [ ] **Step 4: Confirm new prompt blocks are present**

Run:

```bash
grep -c "LANGUAGE:" src/main/java/com/HendrikHoemberg/StudyHelper/service/AiQuizService.java
grep -c "COVERAGE:" src/main/java/com/HendrikHoemberg/StudyHelper/service/AiQuizService.java
grep -c "LANGUAGE:" src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java
grep -c "COVERAGE:" src/main/java/com/HendrikHoemberg/StudyHelper/service/AiExamService.java
grep -c "LANGUAGE:" src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java
grep -c "COVERAGE:" src/main/java/com/HendrikHoemberg/StudyHelper/service/AiFlashcardService.java
```

Expected counts (each headline appears once per affected prompt builder):
- `AiQuizService` → LANGUAGE: 1, COVERAGE: 1
- `AiExamService` → LANGUAGE: 2 (generation + grading), COVERAGE: 1 (generation only)
- `AiFlashcardService` → LANGUAGE: 1, COVERAGE: 1

- [ ] **Step 5: No additional commit**

This task is read-only verification. If any check fails, return to the relevant prior task and fix it.
