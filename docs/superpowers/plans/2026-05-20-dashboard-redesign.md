# Dashboard Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the redundant library-mirror dashboard with a lean *cockpit + launcher*: resume card, review-mistakes card, three action tiles, pinned rail, recent decks rail, recent activity list — each section conditional on real data. Add the tracking needed to power it: `Deck.lastStudiedAt`, `Deck.pinned`, `Flashcard.correctStreak`, and a new `StudyLog` history table.

**Architecture:** Spring Boot 4 + Spring Data JPA + Thymeleaf. Three additive nullable schema changes plus one new table — all handled by `ddl-auto=update`, no manual migration. A new `DashboardController` and `DashboardService` own the dashboard read path; a new `StudyLogService` (the "recorder") is called from each session-completion path inside a single `@Transactional` block. The dashboard handler is removed from `FolderController`. Frontend: `fragments/explorer.html :: dashboardContent` is rewritten end-to-end against a single `DashboardViewModel`.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Data JPA, Hibernate, MySQL (H2 in MySQL-compat mode for tests), Thymeleaf, htmx, Lombok, JUnit 5, AssertJ, Mockito.

**Naming note:** The spec calls the history table "StudySession", but a service named `StudySessionService` already exists for in-flight flashcard sessions. To avoid collision, the entity is named **`StudyLog`** (table: `study_log`) and the recorder service is **`StudyLogService`**. The spec's intent is preserved.

---

## File map

**Modify:**
- `src/main/java/com/HendrikHoemberg/StudyHelper/entity/Deck.java` — add `pinned`, `lastStudiedAt`
- `src/main/java/com/HendrikHoemberg/StudyHelper/entity/Flashcard.java` — add `correctStreak`
- `src/main/java/com/HendrikHoemberg/StudyHelper/repository/DeckRepository.java` — add `findByUserAndPinnedTrueOrderByNameAsc`, `findTop6ByUserAndLastStudiedAtIsNotNullOrderByLastStudiedAtDesc`, mastery count query
- `src/main/java/com/HendrikHoemberg/StudyHelper/repository/FlashcardRepository.java` — add mistake-count and mistake-load queries
- `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FolderController.java` — remove the `/dashboard` handler (lines 42–78)
- `src/main/java/com/HendrikHoemberg/StudyHelper/controller/StudySessionController.java` — call recorder on flashcard completion
- `src/main/java/com/HendrikHoemberg/StudyHelper/controller/QuizController.java` — call recorder on quiz completion
- `src/main/java/com/HendrikHoemberg/StudyHelper/controller/ExamController.java` — call recorder on exam completion
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/StudySessionService.java` — expose `recordAnswer` hook for streak update (or call from controller; see Task 9)
- `src/main/resources/templates/fragments/explorer.html` — rewrite `dashboardContent` fragment
- `src/main/resources/static/css/styles.css` (or wherever the dashboard CSS lives — see Task 17) — add new tile/card styles, remove dead styles

**Create:**
- `src/main/java/com/HendrikHoemberg/StudyHelper/entity/StudyLog.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/repository/StudyLogRepository.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DashboardViewModel.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DashboardDeckSummary.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/StudyLogSummary.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/StudyLogService.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DashboardService.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/FlashcardReviewService.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DeckPinService.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DashboardController.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/controller/ReviewMistakesController.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DeckPinController.java`

**Test files (create):**
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/StudyLogServiceTests.java`
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DashboardServiceTests.java`
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/FlashcardReviewServiceTests.java`
- `src/test/java/com/HendrikHoemberg/StudyHelper/controller/DashboardControllerTests.java`
- `src/test/java/com/HendrikHoemberg/StudyHelper/controller/DeckPinControllerTests.java`
- `src/test/java/com/HendrikHoemberg/StudyHelper/controller/ReviewMistakesControllerTests.java`

**Reused:**
- `entity/SavedSessionType.java` — reused for `StudyLog.type` (same three values: FLASHCARDS, QUIZ, EXAM).

---

## Test-running reference

This project uses Maven via the wrapper, JUnit 5, and the H2 in-memory database for tests.

Run a single test class:
```bash
./mvnw test -Dtest=StudyLogServiceTests
```

Run a single method:
```bash
./mvnw test -Dtest=StudyLogServiceTests#recordFlashcards_writesLogAndUpdatesStreakAndLastStudiedAt
```

Run the full suite:
```bash
./mvnw test
```

Commit message convention from `git log`: `feat:`, `refactor:`, `docs:`, `fix:` prefixes; one-line subject; body explains *why*. Always add the Co-Authored-By trailer.

---

## Task 1: Add `Deck.pinned`

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/entity/Deck.java`

- [ ] **Step 1: Add the field**

After line 33 (the `createdAt` field declaration), add:

```java
@Column(name = "pinned", nullable = false)
private boolean pinned = false;
```

Lombok's `@Getter`/`@Setter` on the class will generate `isPinned()` and `setPinned(boolean)`.

- [ ] **Step 2: Boot the app once to apply the schema change**

Run:
```bash
./mvnw spring-boot:run
```

Watch the log for the Hibernate `alter table decks add column pinned bit not null` (or `boolean`, depending on dialect). Stop the app (`Ctrl-C`) once startup completes successfully.

Expected: app starts cleanly. No new behavior yet.

- [ ] **Step 3: Run the existing test suite**

Run:
```bash
./mvnw test
```

Expected: all existing tests pass. The new column does not affect any existing code path.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/entity/Deck.java
git commit -m "$(cat <<'EOF'
feat: add Deck.pinned column for dashboard pin feature

Schema-only change; defaults to false. Pin UI and dashboard rail come in
later tasks.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Add `Deck.lastStudiedAt`

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/entity/Deck.java`

- [ ] **Step 1: Add the field**

After the `pinned` field added in Task 1:

```java
@Column(name = "last_studied_at")
private LocalDateTime lastStudiedAt;
```

The `LocalDateTime` import is already present in the file (line 8).

- [ ] **Step 2: Boot to apply the schema change**

Run:
```bash
./mvnw spring-boot:run
```

Expected: `alter table decks add column last_studied_at datetime` in the log; app starts cleanly. Stop the app.

- [ ] **Step 3: Run the test suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/entity/Deck.java
git commit -m "$(cat <<'EOF'
feat: add Deck.lastStudiedAt for recent-decks rail

Nullable; populated by the upcoming StudyLogService recorder on session
completion. No backfill.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Add `Flashcard.correctStreak`

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/entity/Flashcard.java`

- [ ] **Step 1: Add the field**

After the `backImageSizeBytes` field (line 35), add:

```java
@Column(name = "correct_streak")
private Integer correctStreak;
```

`Integer` (nullable boxed type) is intentional — null means "never answered".

- [ ] **Step 2: Boot to apply the schema change**

```bash
./mvnw spring-boot:run
```

Expected: `alter table flashcards add column correct_streak integer`. Stop the app.

- [ ] **Step 3: Run the test suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/entity/Flashcard.java
git commit -m "$(cat <<'EOF'
feat: add Flashcard.correctStreak for review-mistakes deck

null = never answered, 0 = wrong at last sighting, N>=1 = right N in a row.
Cards with streak>=2 are "mastered"; streak==0 cards populate the
review-mistakes virtual deck.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Create `StudyLog` entity

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/entity/StudyLog.java`

- [ ] **Step 1: Write the entity**

```java
package com.HendrikHoemberg.StudyHelper.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "study_log",
    indexes = {
        @Index(name = "idx_study_log_user_completed", columnList = "user_id, completed_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class StudyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SavedSessionType type;

    @Column(nullable = false)
    private String title;

    @Column(name = "card_count", nullable = false)
    private int cardCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;
}
```

`SavedSessionType` is reused (FLASHCARDS / QUIZ / EXAM) to avoid a duplicate enum.

- [ ] **Step 2: Boot to create the table**

```bash
./mvnw spring-boot:run
```

Expected: `create table study_log (...)` in the log. Stop the app.

- [ ] **Step 3: Run the test suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/entity/StudyLog.java
git commit -m "$(cat <<'EOF'
feat: add StudyLog entity for completed-session history

One row per completed study/quiz/exam session. Source of truth for the
dashboard "Recent activity" feed. Index on (user_id, completed_at DESC)
for the top-N read path.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Create `StudyLogRepository`

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/repository/StudyLogRepository.java`

- [ ] **Step 1: Write the repository**

```java
package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.StudyLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyLogRepository extends JpaRepository<StudyLog, Long> {
    List<StudyLog> findByUserOrderByCompletedAtDesc(User user, Pageable pageable);
}
```

The dashboard reads the top 5 via `PageRequest.of(0, 5)`.

- [ ] **Step 2: Run the test suite to make sure the repo wires up**

```bash
./mvnw test
```

Expected: all pass. Spring's component scan picks up the new repository on context load.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/repository/StudyLogRepository.java
git commit -m "$(cat <<'EOF'
feat: add StudyLogRepository

findByUserOrderByCompletedAtDesc supports the dashboard "Recent activity"
feed (top 5 via Pageable).

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Extend `DeckRepository` with dashboard queries

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/repository/DeckRepository.java`

- [ ] **Step 1: Add the new query methods**

Replace the entire file with:

```java
package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeckRepository extends JpaRepository<Deck, Long> {
    List<Deck> findByUserAndFolder(User user, Folder folder);
    List<Deck> findByUser(User user);
    Optional<Deck> findByIdAndUser(Long id, User user);

    List<Deck> findByUserAndPinnedTrueOrderByNameAsc(User user);

    List<Deck> findByUserAndLastStudiedAtIsNotNullOrderByLastStudiedAtDesc(User user, Pageable pageable);

    @Query("""
        select coalesce(count(f), 0)
        from Flashcard f
        where f.deck = :deck and f.correctStreak >= 2
        """)
    long countMasteredByDeck(@Param("deck") Deck deck);

    @Query("""
        select coalesce(count(f), 0)
        from Flashcard f
        where f.deck = :deck
        """)
    long countCardsByDeck(@Param("deck") Deck deck);
}
```

- [ ] **Step 2: Run the test suite**

```bash
./mvnw test
```

Expected: all pass. New methods compile and JPQL parses on context load.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/repository/DeckRepository.java
git commit -m "$(cat <<'EOF'
feat: add deck queries for dashboard rails

- findByUserAndPinnedTrueOrderByNameAsc — pinned rail
- findByUserAndLastStudiedAtIsNotNullOrderByLastStudiedAtDesc — recent rail
- countMasteredByDeck / countCardsByDeck — mastery line on deck tiles

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Extend `FlashcardRepository` with review-mistakes queries

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/repository/FlashcardRepository.java`

- [ ] **Step 1: Add the new methods**

Add the following two methods inside the interface, after `findExistingIdsByIdIn` (just before the closing `}`):

```java
    @Query("""
        select count(f)
        from Flashcard f
        where f.deck.user = :user and f.correctStreak = 0
        """)
    long countMistakesByUser(@Param("user") User user);

    @Query("""
        select f
        from Flashcard f
        where f.deck.user = :user and f.correctStreak = 0
        order by f.id asc
        """)
    List<Flashcard> findMistakesByUser(@Param("user") User user);
```

- [ ] **Step 2: Run the test suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/repository/FlashcardRepository.java
git commit -m "$(cat <<'EOF'
feat: add review-mistakes queries to FlashcardRepository

countMistakesByUser drives the dashboard's "Review mistakes" card;
findMistakesByUser feeds the cross-deck review session.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Create `StudyLogService` (the recorder) — write failing test first

**Files:**
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/service/StudyLogServiceTests.java`

- [ ] **Step 1: Write the failing test**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.QuizConfig;
import com.HendrikHoemberg.StudyHelper.dto.QuizQuestion;
import com.HendrikHoemberg.StudyHelper.dto.QuizSessionState;
import com.HendrikHoemberg.StudyHelper.dto.QuestionType;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionConfig;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.dto.StudyCardView;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Exam;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.StudyLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.StudyLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StudyLogServiceTests {

    @Autowired StudyLogService studyLogService;
    @Autowired StudyLogRepository studyLogRepository;
    @Autowired DeckRepository deckRepository;
    @Autowired FlashcardRepository flashcardRepository;
    @Autowired org.springframework.orm.jpa.JpaTransactionManager txManager;
    @Autowired org.springframework.beans.factory.ObjectFactory<jakarta.persistence.EntityManager> emFactory;

    User user;
    Folder folder;
    Deck deck;
    Flashcard card1;
    Flashcard card2;

    @BeforeEach
    void setUp() {
        var em = emFactory.getObject();
        user = new User();
        user.setUsername("alice-" + System.nanoTime());
        user.setPassword("x".repeat(60));
        em.persist(user);

        folder = new Folder();
        folder.setName("Root");
        folder.setColorHex("#000000");
        folder.setIconName("folder");
        folder.setUser(user);
        em.persist(folder);

        deck = new Deck();
        deck.setName("Anatomy");
        deck.setUser(user);
        deck.setFolder(folder);
        em.persist(deck);

        card1 = new Flashcard();
        card1.setFrontText("F1");
        card1.setBackText("B1");
        card1.setDeck(deck);
        em.persist(card1);

        card2 = new Flashcard();
        card2.setFrontText("F2");
        card2.setBackText("B2");
        card2.setDeck(deck);
        em.persist(card2);

        em.flush();
    }

    @Test
    void recordFlashcards_writesLogAndUpdatesStreakAndLastStudiedAt() {
        StudyCardView v1 = new StudyCardView(card1.getId(), "F1", "B1", deck.getId(), "Anatomy", "Root / Anatomy", "#000", "layers", null, null);
        StudyCardView v2 = new StudyCardView(card2.getId(), "F2", "B2", deck.getId(), "Anatomy", "Root / Anatomy", "#000", "layers", null, null);

        // card1 answered correctly, card2 answered incorrectly
        StudySessionState state = new StudySessionState(
            new StudySessionConfig(List.of(deck.getId()), com.HendrikHoemberg.StudyHelper.dto.SessionMode.DECK_BY_DECK, com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode.SELECTED_ORDER),
            Map.of(deck.getId(), List.of(v1, v2)),
            List.of(v1, v2),
            2,    // currentIndex (complete)
            2,    // totalAnswered
            1,    // correctAnswers
            1,    // incorrectAnswers
            List.of(card2.getId())  // incorrectCardIds
        );

        studyLogService.recordFlashcards(user, state);

        // StudyLog row written
        var logs = studyLogRepository.findByUserOrderByCompletedAtDesc(user,
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(logs).hasSize(1);
        StudyLog log = logs.get(0);
        assertThat(log.getType()).isEqualTo(SavedSessionType.FLASHCARDS);
        assertThat(log.getCardCount()).isEqualTo(2);
        assertThat(log.getCorrectCount()).isEqualTo(1);
        assertThat(log.getCompletedAt()).isNotNull();
        assertThat(log.getTitle()).contains("Anatomy");

        // Streak updated: card1 -> 1, card2 -> 0
        Flashcard reloaded1 = flashcardRepository.findById(card1.getId()).orElseThrow();
        Flashcard reloaded2 = flashcardRepository.findById(card2.getId()).orElseThrow();
        assertThat(reloaded1.getCorrectStreak()).isEqualTo(1);
        assertThat(reloaded2.getCorrectStreak()).isEqualTo(0);

        // Deck lastStudiedAt updated
        Deck reloadedDeck = deckRepository.findById(deck.getId()).orElseThrow();
        assertThat(reloadedDeck.getLastStudiedAt()).isNotNull();
    }

    @Test
    void recordFlashcards_secondCorrectIncrementsStreak() {
        // Pre-set streak to 1
        card1.setCorrectStreak(1);
        flashcardRepository.save(card1);

        StudyCardView v1 = new StudyCardView(card1.getId(), "F1", "B1", deck.getId(), "Anatomy", "Root / Anatomy", "#000", "layers", null, null);
        StudySessionState state = new StudySessionState(
            new StudySessionConfig(List.of(deck.getId()), com.HendrikHoemberg.StudyHelper.dto.SessionMode.DECK_BY_DECK, com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode.SELECTED_ORDER),
            Map.of(deck.getId(), List.of(v1)),
            List.of(v1),
            1, 1, 1, 0, List.of()
        );

        studyLogService.recordFlashcards(user, state);

        Flashcard reloaded = flashcardRepository.findById(card1.getId()).orElseThrow();
        assertThat(reloaded.getCorrectStreak()).isEqualTo(2);  // graduated to mastered
    }

    @Test
    void recordQuiz_writesLogAndTouchesDecksWithoutChangingStreaks() {
        // Pre-set streaks to verify they are NOT changed by quiz mode
        card1.setCorrectStreak(2);
        card2.setCorrectStreak(0);
        flashcardRepository.save(card1);
        flashcardRepository.save(card2);

        QuizQuestion q1 = new QuizQuestion(QuestionType.SINGLE_CHOICE, "?", List.of("a", "b"), List.of(0));
        QuizSessionState state = new QuizSessionState(
            new QuizConfig(List.of(deck.getId()), List.of(), 1,
                com.HendrikHoemberg.StudyHelper.dto.QuizQuestionMode.MCQ_ONLY,
                com.HendrikHoemberg.StudyHelper.dto.Difficulty.MEDIUM, null),
            List.of(q1),
            1,
            Map.of(0, List.of(0))
        );

        studyLogService.recordQuiz(user, state);

        var logs = studyLogRepository.findByUserOrderByCompletedAtDesc(user,
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getType()).isEqualTo(SavedSessionType.QUIZ);
        assertThat(logs.get(0).getCorrectCount()).isEqualTo(1);
        assertThat(logs.get(0).getCardCount()).isEqualTo(1);

        // Streaks unchanged
        assertThat(flashcardRepository.findById(card1.getId()).orElseThrow().getCorrectStreak()).isEqualTo(2);
        assertThat(flashcardRepository.findById(card2.getId()).orElseThrow().getCorrectStreak()).isEqualTo(0);

        // Deck lastStudiedAt still updated
        assertThat(deckRepository.findById(deck.getId()).orElseThrow().getLastStudiedAt()).isNotNull();
    }

    @Test
    void recordExam_writesLogAndDoesNotUpdateStreaks() {
        card1.setCorrectStreak(2);
        flashcardRepository.save(card1);

        Exam exam = new Exam();
        exam.setUser(user);
        exam.setTitle("Anatomy midterm");
        exam.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        exam.setCompletedAt(LocalDateTime.now());
        exam.setQuestionCount(10);
        exam.setOverallScorePct(70);
        exam.setSourceSummary("Anatomy");

        studyLogService.recordExam(user, exam, List.of(deck.getId()));

        var logs = studyLogRepository.findByUserOrderByCompletedAtDesc(user,
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(logs).hasSize(1);
        StudyLog log = logs.get(0);
        assertThat(log.getType()).isEqualTo(SavedSessionType.EXAM);
        assertThat(log.getCardCount()).isEqualTo(10);
        assertThat(log.getCorrectCount()).isEqualTo(7);  // round(10 * 70 / 100)
        assertThat(log.getDurationSec()).isNotNull();

        // Streak untouched
        assertThat(flashcardRepository.findById(card1.getId()).orElseThrow().getCorrectStreak()).isEqualTo(2);
        // Deck lastStudiedAt updated
        assertThat(deckRepository.findById(deck.getId()).orElseThrow().getLastStudiedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run the test — expect compile failure**

```bash
./mvnw test -Dtest=StudyLogServiceTests
```

Expected: compile fails with "cannot find symbol StudyLogService". This is the failing-test signal.

- [ ] **Step 3: Implement `StudyLogService`**

Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/StudyLogService.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.QuizSessionState;
import com.HendrikHoemberg.StudyHelper.dto.StudyCardView;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Exam;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.StudyLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.StudyLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Records a completed study/quiz/exam session: writes a StudyLog row,
 * updates deck.lastStudiedAt for every involved deck, and (flashcards only)
 * updates flashcard.correctStreak.
 *
 * All three operations run in a single transaction so partial writes never
 * happen — if anything fails, the user still sees the completion screen but
 * no half-written history is left behind.
 */
@Service
public class StudyLogService {

    private final StudyLogRepository studyLogRepository;
    private final DeckRepository deckRepository;
    private final FlashcardRepository flashcardRepository;

    public StudyLogService(StudyLogRepository studyLogRepository,
                           DeckRepository deckRepository,
                           FlashcardRepository flashcardRepository) {
        this.studyLogRepository = studyLogRepository;
        this.deckRepository = deckRepository;
        this.flashcardRepository = flashcardRepository;
    }

    @Transactional
    public void recordFlashcards(User user, StudySessionState state) {
        if (state == null || state.queue().isEmpty()) return;

        // Update streaks for every card answered in this session.
        Set<Long> incorrectIds = new HashSet<>(state.incorrectCardIds());
        Set<Long> deckIds = new HashSet<>();
        for (StudyCardView v : state.queue()) {
            deckIds.add(v.deckId());
            Flashcard fc = flashcardRepository.findById(v.cardId()).orElse(null);
            if (fc == null) continue;
            if (incorrectIds.contains(v.cardId())) {
                fc.setCorrectStreak(0);
            } else {
                Integer cur = fc.getCorrectStreak();
                fc.setCorrectStreak((cur == null ? 0 : cur) + 1);
            }
            flashcardRepository.save(fc);
        }

        // Update lastStudiedAt for every involved deck.
        touchDecks(deckIds);

        // Write the log row.
        StudyLog log = new StudyLog();
        log.setUser(user);
        log.setType(SavedSessionType.FLASHCARDS);
        log.setTitle(flashcardTitle(state));
        log.setCardCount(state.queue().size());
        log.setCorrectCount(state.correctAnswers());
        log.setDurationSec(null);    // flashcard mode is untimed
        log.setCompletedAt(LocalDateTime.now());
        studyLogRepository.save(log);
    }

    @Transactional
    public void recordQuiz(User user, QuizSessionState state) {
        if (state == null) return;

        // Quiz mode does NOT update correctStreak (AI-generated questions are
        // not 1:1 mapped to source flashcards).
        Set<Long> deckIds = state.config().selectedDeckIds() == null
            ? Set.of()
            : new HashSet<>(state.config().selectedDeckIds());
        touchDecks(deckIds);

        StudyLog log = new StudyLog();
        log.setUser(user);
        log.setType(SavedSessionType.QUIZ);
        log.setTitle(quizTitle(user, deckIds));
        log.setCardCount(state.questions().size());
        log.setCorrectCount(state.correctCount());
        log.setDurationSec(null);
        log.setCompletedAt(LocalDateTime.now());
        studyLogRepository.save(log);
    }

    @Transactional
    public void recordExam(User user, Exam exam, List<Long> deckIds) {
        if (exam == null) return;

        // Exam mode does not update correctStreak.
        if (deckIds != null) touchDecks(new HashSet<>(deckIds));

        int cardCount = exam.getQuestionCount();
        int correct = (int) Math.round(cardCount * exam.getOverallScorePct() / 100.0);

        Integer duration = null;
        if (exam.getCreatedAt() != null && exam.getCompletedAt() != null) {
            duration = (int) Duration.between(exam.getCreatedAt(), exam.getCompletedAt()).toSeconds();
            if (duration < 0) duration = null;
        }

        StudyLog log = new StudyLog();
        log.setUser(user);
        log.setType(SavedSessionType.EXAM);
        log.setTitle(exam.getTitle() != null ? exam.getTitle() : "Exam");
        log.setCardCount(cardCount);
        log.setCorrectCount(correct);
        log.setDurationSec(duration);
        log.setCompletedAt(exam.getCompletedAt() != null ? exam.getCompletedAt() : LocalDateTime.now());
        studyLogRepository.save(log);
    }

    private void touchDecks(Set<Long> deckIds) {
        if (deckIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : deckIds) {
            Deck d = deckRepository.findById(id).orElse(null);
            if (d == null) continue;
            d.setLastStudiedAt(now);
            deckRepository.save(d);
        }
    }

    private String flashcardTitle(StudySessionState state) {
        // Use the first card's deck label if uniform, else "N decks".
        Set<String> labels = new HashSet<>();
        for (StudyCardView v : state.queue()) {
            labels.add(v.deckName());
        }
        if (labels.size() == 1) return labels.iterator().next();
        return labels.size() + " decks";
    }

    private String quizTitle(User user, Set<Long> deckIds) {
        if (deckIds.isEmpty()) return "Quiz";
        if (deckIds.size() == 1) {
            Deck d = deckRepository.findById(deckIds.iterator().next()).orElse(null);
            return d == null ? "Quiz" : "Quiz · " + d.getName();
        }
        return "Quiz · " + deckIds.size() + " decks";
    }
}
```

- [ ] **Step 4: Run the test — expect PASS**

```bash
./mvnw test -Dtest=StudyLogServiceTests
```

Expected: all four test methods pass.

- [ ] **Step 5: Run full suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/StudyLogService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/StudyLogServiceTests.java
git commit -m "$(cat <<'EOF'
feat: add StudyLogService to record completed sessions

Writes a StudyLog row, updates deck.lastStudiedAt, and (flashcards only)
updates flashcard.correctStreak — all in one transaction so partial writes
never happen.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Hook the recorder into flashcard completion

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/StudySessionController.java`

- [ ] **Step 1: Inject `StudyLogService` and call it at completion**

Edit `StudySessionController.java`:

(a) Add the import at the top with the other service imports:
```java
import com.HendrikHoemberg.StudyHelper.service.StudyLogService;
```

(b) Add the field (after `savedSessionService`):
```java
    private final StudyLogService studyLogService;
```

(c) Update the constructor signature and assignment:
```java
    public StudySessionController(StudySessionService studySessionService,
                                  FlashcardService flashcardService,
                                  UserService userService,
                                  SavedSessionService savedSessionService,
                                  StudyLogService studyLogService) {
        this.studySessionService = studySessionService;
        this.flashcardService = flashcardService;
        this.userService = userService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
    }
```

(d) Replace the `stashAndPersist` helper near the bottom of the file with this version that records on completion-transition:

```java
    private void stashAndPersist(HttpSession httpSession, User user, StudySessionState state) {
        Object prior = httpSession.getAttribute(SESSION_KEY);
        boolean wasComplete = prior instanceof StudySessionState s && studySessionService.isComplete(s);
        httpSession.setAttribute(SESSION_KEY, state);
        if (studySessionService.isComplete(state)) {
            savedSessionService.discard(user);
            if (!wasComplete) {
                try {
                    studyLogService.recordFlashcards(user, state);
                } catch (Exception ignored) {
                    // Never block the user's completion view because of bookkeeping.
                }
            }
        } else {
            savedSessionService.saveFlashcards(user, state);
        }
    }
```

The `!wasComplete` guard ensures we only record once even if the user reloads the complete page (which re-enters `stashAndPersist` for some flows). The try/catch is a deliberate safety net — bookkeeping must never block the user.

- [ ] **Step 2: Run the existing study controller tests**

```bash
./mvnw test -Dtest=StudyControllerTests,StudySessionControllerTests
```

Expected: all pass. Existing tests don't assert on StudyLog rows, but they exercise the completion path and must still work.

- [ ] **Step 3: Run full suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/StudySessionController.java
git commit -m "$(cat <<'EOF'
feat: record flashcard sessions on completion

Wires StudyLogService into the flashcard answer endpoint. Records only on
the first transition to complete, and never propagates bookkeeping errors
to the user.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Hook the recorder into quiz completion

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/QuizController.java`

- [ ] **Step 1: Inject and call**

(a) Add the import:
```java
import com.HendrikHoemberg.StudyHelper.service.StudyLogService;
```

(b) Add the field and constructor parameter:
```java
    private final UserService userService;
    private final SavedSessionService savedSessionService;
    private final StudyLogService studyLogService;

    public QuizController(UserService userService,
                          SavedSessionService savedSessionService,
                          StudyLogService studyLogService) {
        this.userService = userService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
    }
```

(c) Replace `stashAndPersist` (lines 162–169) with:

```java
    private void stashAndPersist(HttpSession httpSession, User user, QuizSessionState state) {
        Object prior = httpSession.getAttribute(SESSION_KEY);
        boolean wasComplete = prior instanceof QuizSessionState s && s.isComplete();
        httpSession.setAttribute(SESSION_KEY, state);
        if (state.isComplete()) {
            savedSessionService.discard(user);
            if (!wasComplete) {
                try { studyLogService.recordQuiz(user, state); }
                catch (Exception ignored) {}
            }
        } else {
            savedSessionService.saveQuiz(user, state);
        }
    }
```

- [ ] **Step 2: Run the quiz controller tests**

```bash
./mvnw test -Dtest=QuizControllerTests
```

Expected: pass. (If the test does not exist as a separate class, it'll fail with "No tests found" — that's fine; rely on the full suite below.)

- [ ] **Step 3: Run full suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/QuizController.java
git commit -m "$(cat <<'EOF'
feat: record quiz sessions on completion

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Hook the recorder into exam completion

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/ExamController.java`

- [ ] **Step 1: Inject and call**

(a) Add the import:
```java
import com.HendrikHoemberg.StudyHelper.service.StudyLogService;
```

(b) Add the field and constructor parameter. The existing constructor will already have several fields — add `studyLogService` as a new final field and a new constructor parameter; assign it inside.

(c) In `submit()` (around line 280), immediately after `Exam saved = examService.saveCompleted(user, finalState, grading);` (and before `model.addAttribute("exam", saved);`), add:

```java
            try {
                List<Long> involvedDeckIds = state.config() == null || state.config().deckIds() == null
                    ? List.of()
                    : List.copyOf(state.config().deckIds());
                studyLogService.recordExam(user, saved, involvedDeckIds);
            } catch (Exception ignored) {
                // Never block the result view because of bookkeeping.
            }
```

If `state.config().deckIds()` is not the actual accessor on `ExamConfig`, adjust to whatever the existing field accessor is (open `src/main/java/com/HendrikHoemberg/StudyHelper/dto/ExamConfig.java` and use the real accessor; record component name will be `deckIds()` or similar).

- [ ] **Step 2: Run the exam controller tests**

```bash
./mvnw test -Dtest=ExamControllerTests
```

Expected: pass.

- [ ] **Step 3: Run full suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/ExamController.java
git commit -m "$(cat <<'EOF'
feat: record exam sessions on completion

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Create `FlashcardReviewService` — test first

**Files:**
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/service/FlashcardReviewServiceTests.java`

- [ ] **Step 1: Write the failing test**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FlashcardReviewServiceTests {

    @Autowired FlashcardReviewService reviewService;
    @Autowired FlashcardRepository flashcardRepository;
    @Autowired ObjectFactory<EntityManager> emFactory;

    @Test
    void countAndLoad_returnsOnlyStreakZeroForUser() {
        var em = emFactory.getObject();

        User userA = persistUser(em, "alice");
        User userB = persistUser(em, "bob");
        Deck deckA = persistDeck(em, userA);
        Deck deckB = persistDeck(em, userB);

        // alice: 2 cards with streak=0, 1 with streak=2, 1 with null
        Flashcard a1 = persistCard(em, deckA, 0);
        Flashcard a2 = persistCard(em, deckA, 0);
        persistCard(em, deckA, 2);
        persistCard(em, deckA, null);
        // bob: 1 card with streak=0 (must NOT appear for alice)
        persistCard(em, deckB, 0);
        em.flush();

        assertThat(reviewService.countMistakes(userA)).isEqualTo(2L);
        assertThat(reviewService.loadMistakeCards(userA))
            .extracting(Flashcard::getId)
            .containsExactlyInAnyOrder(a1.getId(), a2.getId());

        assertThat(reviewService.countMistakes(userB)).isEqualTo(1L);
    }

    private User persistUser(EntityManager em, String prefix) {
        User u = new User();
        u.setUsername(prefix + "-" + System.nanoTime());
        u.setPassword("x".repeat(60));
        em.persist(u);
        return u;
    }

    private Deck persistDeck(EntityManager em, User user) {
        Folder folder = new Folder();
        folder.setName("Root");
        folder.setColorHex("#000000");
        folder.setIconName("folder");
        folder.setUser(user);
        em.persist(folder);

        Deck d = new Deck();
        d.setName("Deck");
        d.setUser(user);
        d.setFolder(folder);
        em.persist(d);
        return d;
    }

    private Flashcard persistCard(EntityManager em, Deck deck, Integer streak) {
        Flashcard c = new Flashcard();
        c.setFrontText("F");
        c.setBackText("B");
        c.setDeck(deck);
        c.setCorrectStreak(streak);
        em.persist(c);
        return c;
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./mvnw test -Dtest=FlashcardReviewServiceTests
```

Expected: `cannot find symbol FlashcardReviewService`.

- [ ] **Step 3: Implement the service**

Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/FlashcardReviewService.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the "Review mistakes" cross-deck virtual collection: all of a user's
 * flashcards where correctStreak == 0 (got it wrong at last sighting).
 */
@Service
public class FlashcardReviewService {

    private final FlashcardRepository flashcardRepository;

    public FlashcardReviewService(FlashcardRepository flashcardRepository) {
        this.flashcardRepository = flashcardRepository;
    }

    @Transactional(readOnly = true)
    public long countMistakes(User user) {
        return flashcardRepository.countMistakesByUser(user);
    }

    @Transactional(readOnly = true)
    public List<Flashcard> loadMistakeCards(User user) {
        return flashcardRepository.findMistakesByUser(user);
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./mvnw test -Dtest=FlashcardReviewServiceTests
```

Expected: pass.

- [ ] **Step 5: Full suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/FlashcardReviewService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/FlashcardReviewServiceTests.java
git commit -m "$(cat <<'EOF'
feat: add FlashcardReviewService for review-mistakes deck

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Create `DeckPinService` and endpoint

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DeckPinService.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DeckPinController.java`
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/DeckPinControllerTests.java`

- [ ] **Step 1: Write the failing controller test**

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeckPinControllerTests {

    @Autowired MockMvc mvc;
    @Autowired DeckRepository deckRepository;
    @Autowired ObjectFactory<EntityManager> emFactory;
    @Autowired PasswordEncoder passwordEncoder;

    User owner;
    User other;
    Deck deck;

    @BeforeEach
    void setUp() {
        var em = emFactory.getObject();
        owner = persistUser(em, "owner");
        other = persistUser(em, "other");
        Folder folder = new Folder();
        folder.setName("Root");
        folder.setColorHex("#000");
        folder.setIconName("folder");
        folder.setUser(owner);
        em.persist(folder);
        deck = new Deck();
        deck.setName("D");
        deck.setUser(owner);
        deck.setFolder(folder);
        em.persist(deck);
        em.flush();
    }

    @Test
    void togglePin_flipsValueForOwner() throws Exception {
        mvc.perform(post("/decks/" + deck.getId() + "/pin")
                .with(csrf())
                .with(user(owner.getUsername())))
            .andExpect(status().isOk());

        assertThat(deckRepository.findById(deck.getId()).orElseThrow().isPinned()).isTrue();

        mvc.perform(post("/decks/" + deck.getId() + "/pin")
                .with(csrf())
                .with(user(owner.getUsername())))
            .andExpect(status().isOk());

        assertThat(deckRepository.findById(deck.getId()).orElseThrow().isPinned()).isFalse();
    }

    @Test
    void togglePin_rejectsNonOwner() throws Exception {
        mvc.perform(post("/decks/" + deck.getId() + "/pin")
                .with(csrf())
                .with(user(other.getUsername())))
            .andExpect(status().isNotFound());

        assertThat(deckRepository.findById(deck.getId()).orElseThrow().isPinned()).isFalse();
    }

    private User persistUser(EntityManager em, String prefix) {
        User u = new User();
        u.setUsername(prefix + "-" + System.nanoTime());
        u.setPassword(passwordEncoder.encode("password123"));
        em.persist(u);
        return u;
    }
}
```

- [ ] **Step 2: Run — expect failure (404 on POST since route does not exist)**

```bash
./mvnw test -Dtest=DeckPinControllerTests
```

Expected: tests fail because the endpoint returns 404.

- [ ] **Step 3: Implement the service**

`src/main/java/com/HendrikHoemberg/StudyHelper/service/DeckPinService.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class DeckPinService {

    private final DeckRepository deckRepository;

    public DeckPinService(DeckRepository deckRepository) {
        this.deckRepository = deckRepository;
    }

    @Transactional
    public boolean togglePin(User user, Long deckId) {
        Deck deck = deckRepository.findByIdAndUser(deckId, user)
            .orElseThrow(() -> new NoSuchElementException("Deck not found"));
        deck.setPinned(!deck.isPinned());
        deckRepository.save(deck);
        return deck.isPinned();
    }
}
```

- [ ] **Step 4: Implement the controller**

`src/main/java/com/HendrikHoemberg/StudyHelper/controller/DeckPinController.java`:

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DeckPinService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.util.NoSuchElementException;

@Controller
public class DeckPinController {

    private final UserService userService;
    private final DeckPinService deckPinService;

    public DeckPinController(UserService userService, DeckPinService deckPinService) {
        this.userService = userService;
        this.deckPinService = deckPinService;
    }

    @PostMapping("/decks/{id}/pin")
    public ResponseEntity<Void> togglePin(@PathVariable Long id, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        try {
            deckPinService.togglePin(user, id);
            return ResponseEntity.ok().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
```

- [ ] **Step 5: Run the test — expect PASS**

```bash
./mvnw test -Dtest=DeckPinControllerTests
```

Expected: both tests pass.

- [ ] **Step 6: Full suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DeckPinService.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/controller/DeckPinController.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/controller/DeckPinControllerTests.java
git commit -m "$(cat <<'EOF'
feat: add deck pin toggle (POST /decks/{id}/pin)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Create the dashboard view-model DTOs

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DashboardViewModel.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DashboardDeckSummary.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/StudyLogSummary.java`

- [ ] **Step 1: Write `DashboardDeckSummary`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

public record DashboardDeckSummary(
    Long deckId,
    String deckName,
    String folderPath,
    String colorHex,
    String iconName,
    long totalCards,
    long masteredCards,
    boolean pinned
) {}
```

- [ ] **Step 2: Write `StudyLogSummary`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;

import java.time.LocalDateTime;

public record StudyLogSummary(
    SavedSessionType type,
    String title,
    int cardCount,
    int correctCount,
    LocalDateTime completedAt
) {}
```

- [ ] **Step 3: Write `DashboardViewModel`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.util.List;
import java.util.Optional;

public record DashboardViewModel(
    String greetingName,
    Optional<SavedSessionSummary> resume,
    long reviewMistakesCount,
    List<DashboardDeckSummary> pinnedDecks,
    List<DashboardDeckSummary> recentDecks,
    List<StudyLogSummary> recentActivity
) {}
```

- [ ] **Step 4: Compile check**

```bash
./mvnw compile
```

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/dto/DashboardViewModel.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/DashboardDeckSummary.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/StudyLogSummary.java
git commit -m "$(cat <<'EOF'
feat: add dashboard view-model DTOs

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Implement `DashboardService` — test first

**Files:**
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DashboardServiceTests.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DashboardService.java`

- [ ] **Step 1: Write the failing test**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DashboardViewModel;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.StudyLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.StudyLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectFactory;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DashboardServiceTests {

    @Autowired DashboardService dashboardService;
    @Autowired StudyLogRepository studyLogRepository;
    @Autowired ObjectFactory<EntityManager> emFactory;

    @Test
    void brandNewUser_hasEmptySections() {
        User user = persistUser("new");
        DashboardViewModel vm = dashboardService.buildFor(user);

        assertThat(vm.greetingName()).contains("new");
        assertThat(vm.resume()).isEmpty();
        assertThat(vm.reviewMistakesCount()).isZero();
        assertThat(vm.pinnedDecks()).isEmpty();
        assertThat(vm.recentDecks()).isEmpty();
        assertThat(vm.recentActivity()).isEmpty();
    }

    @Test
    void populatedUser_returnsAllSections() {
        var em = emFactory.getObject();
        User user = persistUser("hendrik");
        Folder folder = persistFolder(user);
        Deck pinned = persistDeck(user, folder, "Pinned deck", true, LocalDateTime.now().minusDays(1));
        Deck recent = persistDeck(user, folder, "Recent deck", false, LocalDateTime.now().minusHours(1));
        Deck stale = persistDeck(user, folder, "Stale", false, null);

        // Two cards on `recent`: one mastered, one mistake
        persistCard(recent, 0);
        persistCard(recent, 2);
        em.flush();

        StudyLog log = new StudyLog();
        log.setUser(user);
        log.setType(SavedSessionType.FLASHCARDS);
        log.setTitle("Recent deck");
        log.setCardCount(2);
        log.setCorrectCount(1);
        log.setCompletedAt(LocalDateTime.now());
        studyLogRepository.save(log);

        DashboardViewModel vm = dashboardService.buildFor(user);

        assertThat(vm.reviewMistakesCount()).isEqualTo(1);
        assertThat(vm.pinnedDecks()).extracting("deckName").containsExactly("Pinned deck");
        assertThat(vm.recentDecks()).extracting("deckName").containsExactly("Recent deck");
        assertThat(vm.recentDecks().get(0).masteredCards()).isEqualTo(1);
        assertThat(vm.recentDecks().get(0).totalCards()).isEqualTo(2);
        assertThat(vm.recentActivity()).hasSize(1);
        // 'Stale' (lastStudiedAt == null) excluded
        assertThat(vm.recentDecks()).extracting("deckName").doesNotContain("Stale");
    }

    private User persistUser(String prefix) {
        var em = emFactory.getObject();
        User u = new User();
        u.setUsername(prefix + "-" + System.nanoTime());
        u.setPassword("x".repeat(60));
        em.persist(u);
        return u;
    }

    private Folder persistFolder(User user) {
        var em = emFactory.getObject();
        Folder f = new Folder();
        f.setName("Root");
        f.setColorHex("#000");
        f.setIconName("folder");
        f.setUser(user);
        em.persist(f);
        return f;
    }

    private Deck persistDeck(User user, Folder folder, String name, boolean pinned, LocalDateTime lastStudiedAt) {
        var em = emFactory.getObject();
        Deck d = new Deck();
        d.setName(name);
        d.setUser(user);
        d.setFolder(folder);
        d.setPinned(pinned);
        d.setLastStudiedAt(lastStudiedAt);
        em.persist(d);
        return d;
    }

    private Flashcard persistCard(Deck deck, Integer streak) {
        var em = emFactory.getObject();
        Flashcard c = new Flashcard();
        c.setFrontText("F");
        c.setBackText("B");
        c.setDeck(deck);
        c.setCorrectStreak(streak);
        em.persist(c);
        return c;
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./mvnw test -Dtest=DashboardServiceTests
```

Expected: `cannot find symbol DashboardService`.

- [ ] **Step 3: Implement `DashboardService`**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DashboardDeckSummary;
import com.HendrikHoemberg.StudyHelper.dto.DashboardViewModel;
import com.HendrikHoemberg.StudyHelper.dto.SavedSessionSummary;
import com.HendrikHoemberg.StudyHelper.dto.StudyLogSummary;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.StudyLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.StudyLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DashboardService {

    private static final int RECENT_DECK_LIMIT = 6;
    private static final int RECENT_ACTIVITY_LIMIT = 5;

    private final DeckRepository deckRepository;
    private final StudyLogRepository studyLogRepository;
    private final SavedSessionService savedSessionService;
    private final FlashcardReviewService flashcardReviewService;
    private final FolderService folderService;

    public DashboardService(DeckRepository deckRepository,
                            StudyLogRepository studyLogRepository,
                            SavedSessionService savedSessionService,
                            FlashcardReviewService flashcardReviewService,
                            FolderService folderService) {
        this.deckRepository = deckRepository;
        this.studyLogRepository = studyLogRepository;
        this.savedSessionService = savedSessionService;
        this.flashcardReviewService = flashcardReviewService;
        this.folderService = folderService;
    }

    @Transactional(readOnly = true)
    public DashboardViewModel buildFor(User user) {
        Optional<SavedSessionSummary> resume = savedSessionService.findForUser(user);
        long mistakesCount = flashcardReviewService.countMistakes(user);

        List<DashboardDeckSummary> pinned = deckRepository
            .findByUserAndPinnedTrueOrderByNameAsc(user)
            .stream()
            .map(this::toSummary)
            .toList();

        List<DashboardDeckSummary> recent = deckRepository
            .findByUserAndLastStudiedAtIsNotNullOrderByLastStudiedAtDesc(
                user, PageRequest.of(0, RECENT_DECK_LIMIT))
            .stream()
            .map(this::toSummary)
            .toList();

        List<StudyLogSummary> activity = studyLogRepository
            .findByUserOrderByCompletedAtDesc(user, PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
            .stream()
            .map(this::toSummary)
            .toList();

        return new DashboardViewModel(
            user.getUsername(),
            resume,
            mistakesCount,
            pinned,
            recent,
            activity
        );
    }

    private DashboardDeckSummary toSummary(Deck deck) {
        long total = deckRepository.countCardsByDeck(deck);
        long mastered = deckRepository.countMasteredByDeck(deck);
        return new DashboardDeckSummary(
            deck.getId(),
            deck.getName(),
            folderService.buildPathLabel(deck.getFolder()),
            deck.getColorHex(),
            deck.getIconName(),
            total,
            mastered,
            deck.isPinned()
        );
    }

    private StudyLogSummary toSummary(StudyLog log) {
        return new StudyLogSummary(
            log.getType(),
            log.getTitle(),
            log.getCardCount(),
            log.getCorrectCount(),
            log.getCompletedAt()
        );
    }
}
```

If `FolderService` does not already have `buildPathLabel(Folder)`, look at the existing folder-path resolution and reuse whatever produces the human-readable label used in `StudyDeckOption.folderPath()` (the dashboard library grid already shows this). If no such helper exists yet, add a small one to `FolderService`:

```java
public String buildPathLabel(Folder folder) {
    if (folder == null) return "";
    StringBuilder sb = new StringBuilder(folder.getName());
    Folder cur = folder.getParent();
    while (cur != null) {
        sb.insert(0, cur.getName() + " / ");
        cur = cur.getParent();
    }
    return sb.toString();
}
```

(If you add this to FolderService, also make sure the method is public and the import is right.)

- [ ] **Step 4: Run the test — expect PASS**

```bash
./mvnw test -Dtest=DashboardServiceTests
```

Expected: both methods pass.

- [ ] **Step 5: Full suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DashboardService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DashboardServiceTests.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/service/FolderService.java
git commit -m "$(cat <<'EOF'
feat: add DashboardService to assemble the cockpit view model

Reads pinned + recent decks (with mastery counts), recent activity, mistake
count, and the resume summary into a single DashboardViewModel.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

(Drop `FolderService.java` from the add list if you did not modify it.)

---

## Task 16: Add `DashboardController` and remove the old handler from `FolderController`

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DashboardController.java`
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/DashboardControllerTests.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/FolderController.java`

- [ ] **Step 1: Write a failing controller test**

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectFactory<EntityManager> emFactory;

    User user;

    @BeforeEach
    void setUp() {
        var em = emFactory.getObject();
        user = new User();
        user.setUsername("u-" + System.nanoTime());
        user.setPassword("x".repeat(60));
        em.persist(user);
        em.flush();
    }

    @Test
    void getDashboard_returnsDashboardViewWithViewModel() throws Exception {
        mvc.perform(get("/dashboard").with(user(user.getUsername())))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"))
            .andExpect(model().attributeExists("vm"));
    }

    @Test
    void getDashboard_htmxReturnsFragment() throws Exception {
        mvc.perform(get("/dashboard")
                .with(user(user.getUsername()))
                .header("HX-Request", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("sh-dashboard-content")));
    }
}
```

- [ ] **Step 2: Run — expect failure**

```bash
./mvnw test -Dtest=DashboardControllerTests
```

The existing handler in `FolderController` will respond (so step 1 may pass partly), but `vm` is not in the model yet — first test should fail. Proceed regardless; the next steps make it pass.

- [ ] **Step 3: Create `DashboardController`**

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DashboardViewModel;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DashboardService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.security.Principal;

@Controller
public class DashboardController {

    private final UserService userService;
    private final DashboardService dashboardService;

    public DashboardController(UserService userService, DashboardService dashboardService) {
        this.userService = userService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal,
                            Model model,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DashboardViewModel vm = dashboardService.buildFor(user);
        model.addAttribute("vm", vm);
        model.addAttribute("username", user.getUsername());
        if (hxRequest != null) {
            model.addAttribute("refreshSidebar", true);
            return "fragments/explorer :: dashboardContent";
        }
        return "dashboard";
    }
}
```

- [ ] **Step 4: Remove the old handler from `FolderController`**

Delete `FolderController.listFolders(...)` — the entire method from line 42 through line 78 in the original file (the `@GetMapping("/dashboard")` block). Also remove the now-unused imports of `FileSummary`, `StudyDeckOption`, and the `deckService` / `fileEntryService` fields **only if no other method in `FolderController` references them** (check the rest of the file before removing).

The two services are likely still used elsewhere in `FolderController` (e.g., when a folder page loads). Only remove the imports if a compile error confirms they are unused.

- [ ] **Step 5: Run the test — expect PASS**

```bash
./mvnw test -Dtest=DashboardControllerTests
```

Expected: both tests pass.

- [ ] **Step 6: Full suite — fix any `FolderControllerTests` breakage**

```bash
./mvnw test
```

If `FolderControllerTests` had a test that hit `GET /dashboard` and asserted on the old grid behavior, update it: that route is now owned by `DashboardController` (already covered by `DashboardControllerTests`). Move or delete the duplicate assertion. Run again until green.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/DashboardController.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/controller/DashboardControllerTests.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/controller/FolderController.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/controller/FolderControllerTests.java
git commit -m "$(cat <<'EOF'
feat: extract dashboard handler into DashboardController

The /dashboard route now serves the cockpit view model instead of a flat
library grid. The old handler is removed from FolderController.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

(Drop `FolderControllerTests.java` from the add list if not changed.)

---

## Task 17: Add the review-mistakes start endpoint

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/ReviewMistakesController.java`
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/ReviewMistakesControllerTests.java`

- [ ] **Step 1: Write the failing test**

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewMistakesControllerTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectFactory<EntityManager> emFactory;

    User user;

    @BeforeEach
    void setUp() {
        var em = emFactory.getObject();
        user = new User();
        user.setUsername("u-" + System.nanoTime());
        user.setPassword("x".repeat(60));
        em.persist(user);
        Folder folder = new Folder();
        folder.setName("Root");
        folder.setColorHex("#000");
        folder.setIconName("folder");
        folder.setUser(user);
        em.persist(folder);
        Deck deck = new Deck();
        deck.setName("D");
        deck.setUser(user);
        deck.setFolder(folder);
        em.persist(deck);
        Flashcard c = new Flashcard();
        c.setFrontText("F"); c.setBackText("B"); c.setDeck(deck); c.setCorrectStreak(0);
        em.persist(c);
        em.flush();
    }

    @Test
    void startReview_redirectsToSessionNext() throws Exception {
        mvc.perform(post("/study/review-mistakes").with(csrf()).with(user(user.getUsername())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/session/next"));
    }

    @Test
    void startReview_whenNoMistakes_redirectsToDashboard() throws Exception {
        // Wipe by creating a new user with no mistakes
        var em = emFactory.getObject();
        User empty = new User();
        empty.setUsername("empty-" + System.nanoTime());
        empty.setPassword("x".repeat(60));
        em.persist(empty);
        em.flush();

        mvc.perform(post("/study/review-mistakes").with(csrf()).with(user(empty.getUsername())))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }
}
```

- [ ] **Step 2: Run — expect failure**

```bash
./mvnw test -Dtest=ReviewMistakesControllerTests
```

Expected: 404 (no controller yet).

- [ ] **Step 3: Implement the controller**

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode;
import com.HendrikHoemberg.StudyHelper.dto.SessionMode;
import com.HendrikHoemberg.StudyHelper.dto.StudyCardView;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionConfig;
import com.HendrikHoemberg.StudyHelper.dto.StudySessionState;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.FlashcardReviewService;
import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ReviewMistakesController {

    private final UserService userService;
    private final FlashcardReviewService reviewService;
    private final SavedSessionService savedSessionService;

    public ReviewMistakesController(UserService userService,
                                    FlashcardReviewService reviewService,
                                    SavedSessionService savedSessionService) {
        this.userService = userService;
        this.reviewService = reviewService;
        this.savedSessionService = savedSessionService;
    }

    @PostMapping("/study/review-mistakes")
    public String start(Principal principal, HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        List<Flashcard> cards = reviewService.loadMistakeCards(user);
        if (cards.isEmpty()) return "redirect:/dashboard";

        Map<Long, List<StudyCardView>> byDeck = new LinkedHashMap<>();
        List<StudyCardView> queue = new java.util.ArrayList<>();
        for (Flashcard fc : cards) {
            StudyCardView v = new StudyCardView(
                fc.getId(),
                fc.getFrontText(),
                fc.getBackText(),
                fc.getDeck().getId(),
                fc.getDeck().getName(),
                fc.getDeck().getName(),
                fc.getDeck().getColorHex(),
                fc.getDeck().getIconName(),
                null,
                null
            );
            byDeck.computeIfAbsent(v.deckId(), k -> new java.util.ArrayList<>()).add(v);
            queue.add(v);
        }

        StudySessionConfig config = new StudySessionConfig(
            List.copyOf(byDeck.keySet()),
            SessionMode.SHUFFLED,
            DeckOrderMode.SELECTED_ORDER
        );
        StudySessionState state = new StudySessionState(
            config,
            byDeck,
            List.copyOf(queue),
            0, 0, 0, 0, List.of()
        );

        savedSessionService.discard(user);
        session.setAttribute("studySessionState", state);
        savedSessionService.saveFlashcards(user, state);
        return "redirect:/session/next";
    }
}
```

Confirm `SessionMode.SHUFFLED` exists by skimming `dto/SessionMode.java`. If the enum value is named differently (e.g. `SHUFFLE`), use that name instead.

- [ ] **Step 4: Run — expect PASS**

```bash
./mvnw test -Dtest=ReviewMistakesControllerTests
```

Expected: both tests pass.

- [ ] **Step 5: Full suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/ReviewMistakesController.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/controller/ReviewMistakesControllerTests.java
git commit -m "$(cat <<'EOF'
feat: add POST /study/review-mistakes to start a cross-deck review session

Builds a flashcard StudySessionState from every card with correctStreak == 0
and hands it to the existing study flow. Cards leave the mistakes pool
naturally as soon as they're answered correctly twice in a row.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 18: Rewrite the dashboard fragment

**Files:**
- Modify: `src/main/resources/templates/fragments/explorer.html`

- [ ] **Step 1: Replace `dashboardContent` fragment**

Replace the entire `<div th:fragment="dashboardContent" ...>` ... `</div>` block (currently lines 6–197) with this new fragment. **Delete the inner `libraryGrid` fragment as well** — it is no longer used.

```html
<div th:fragment="dashboardContent" class="sh-dashboard-content sh-fade-in">

    <th:block th:if="${refreshSidebar}">
        <aside th:replace="~{fragments/sidebar :: sidebar}"></aside>
    </th:block>

    <div th:if="${error}" class="sh-alert sh-alert-danger">
        <div class="sh-alert-body" th:text="${error}"></div>
        <button type="button" class="sh-alert-close" hx-on:click="this.closest('.sh-alert').remove()">
            <iconify-icon icon="lucide:x"></iconify-icon>
        </button>
    </div>

    <main class="sh-dashboard-main sh-dashboard-cockpit">

        <header class="sh-dashboard-greeting">
            <h1 class="sh-page-title" th:text="'Welcome back, ' + ${vm.greetingName()}">Welcome back</h1>
            <p class="sh-page-subtitle">What do you want to study today?</p>
        </header>

        <!-- Resume card -->
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

        <!-- Action tiles -->
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
                <div th:each="d : ${vm.pinnedDecks()}"
                     th:replace="~{fragments/explorer :: dashboardDeckTile(${d})}"></div>
            </div>
        </section>

        <!-- Recent decks rail -->
        <section th:if="${!vm.recentDecks().isEmpty()}" class="sh-dashboard-section">
            <h2 class="sh-section-title">Recently studied</h2>
            <div class="sh-deck-rail">
                <div th:each="d : ${vm.recentDecks()}"
                     th:replace="~{fragments/explorer :: dashboardDeckTile(${d})}"></div>
            </div>
        </section>

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

    </main>
</div>

<!-- Reusable tile fragment for both rails -->
<div th:fragment="dashboardDeckTile(d)"
     class="sh-deck-tile"
     th:style="'--folder-color:' + (${d.colorHex()} ?: '#888')">
    <a th:href="@{/decks/{id}(id=${d.deckId()})}"
       hx-get th:hx-get="@{/decks/{id}(id=${d.deckId()})}"
       hx-target="#explorer-detail" hx-push-url="true"
       class="sh-deck-cover">
        <span class="sh-deck-cover-icon">
            <iconify-icon th:attr="icon=${d.iconName() != null ? (d.iconName().contains(':') ? d.iconName() : 'lucide:' + d.iconName()) : 'lucide:layers'}"></iconify-icon>
        </span>
    </a>
    <div class="sh-deck-body">
        <div class="sh-deck-title" th:text="${d.deckName()}">Name</div>
        <div class="sh-deck-meta" th:text="${d.folderPath()}">Folder</div>
        <div class="sh-deck-meta"
             th:text="${d.masteredCards()} + ' / ' + ${d.totalCards()} + ' mastered'">0 / 0 mastered</div>
    </div>
    <div class="sh-file-tile-actions">
        <button class="sh-deck-edit-btn"
                th:title="${d.pinned() ? 'Unpin' : 'Pin'}"
                th:hx-post="@{/decks/{id}/pin(id=${d.deckId()})}"
                hx-target="#explorer-detail"
                hx-trigger="click">
            <iconify-icon th:attr="icon=${d.pinned() ? 'lucide:pin-off' : 'lucide:pin'}"></iconify-icon>
        </button>
    </div>
</div>
```

- [ ] **Step 2: Update `dashboard.html` if needed**

The top-level page `templates/dashboard.html` includes `fragments/explorer :: dashboardContent`. No change needed there — the fragment name is unchanged.

- [ ] **Step 3: Boot and view in a browser**

Start the app:
```bash
./mvnw spring-boot:run
```

Log in as a seeded user and open `http://localhost:8080/dashboard`. Verify visually:

- Greeting + subtitle render
- Action tiles render (always)
- Empty new user: no resume card, no review card, no rails, no activity list
- Studying some flashcards (then completing) populates "Recently studied" and "Recent activity" on next dashboard load
- Answering a card wrong puts a card into "Review mistakes" — count appears on dashboard

Stop the app.

- [ ] **Step 4: Run the test suite**

```bash
./mvnw test
```

Expected: `DashboardControllerTests` passes (already covered the fragment id contains `sh-dashboard-content`).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/fragments/explorer.html
git commit -m "$(cat <<'EOF'
feat: rewrite dashboard fragment as cockpit + launcher

Greeting, resume, review-mistakes, action tiles, pinned/recent rails, and
recent activity. Every section hides itself when empty. Library grid and
fake sidebar widgets removed.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 19: Add CSS for the new components and remove dead styles

**Files:**
- Modify: the project's main CSS file. Locate with:
  ```bash
  grep -rln "sh-deck-grid\|sh-dashboard-aside" src/main/resources/static
  ```
  Expected: a single stylesheet (likely `src/main/resources/static/css/main.css` or similar). Use that path below — the placeholder `STYLES.css` should be the actual file you found.

- [ ] **Step 1: Locate the stylesheet**

```bash
grep -rln "sh-deck-grid\|sh-dashboard-aside" src/main/resources/static
```

Note the path it returns. The following steps refer to it as `STYLES.css`.

- [ ] **Step 2: Append the new component styles**

Append the following block at the end of `STYLES.css`:

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

.sh-dashboard-greeting .sh-page-title {
    margin-bottom: 0.25rem;
}

.sh-action-tile-row {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
}

.sh-action-tile {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.75rem;
    padding: 1.5rem 1rem;
    border-radius: 1rem;
    background: var(--sh-surface-2, #1c1c20);
    color: var(--sh-text, #fff);
    text-decoration: none;
    font-weight: 600;
    border: 1px solid var(--sh-border, #2a2a30);
    transition: transform 120ms ease, background 120ms ease;
}

.sh-action-tile iconify-icon {
    font-size: 2rem;
}

.sh-action-tile:hover {
    transform: translateY(-2px);
    background: var(--sh-surface-3, #25252b);
}

.sh-review-card {
    display: flex;
    align-items: center;
    gap: 1rem;
    padding: 1rem 1.25rem;
    border-radius: 0.75rem;
    background: linear-gradient(135deg, rgba(255, 120, 80, 0.12), rgba(255, 120, 80, 0.04));
    border: 1px solid rgba(255, 120, 80, 0.35);
    color: inherit;
    text-decoration: none;
}

.sh-review-card-icon iconify-icon {
    font-size: 1.5rem;
    color: rgb(255, 120, 80);
}

.sh-review-card-body {
    display: flex;
    flex-direction: column;
    flex: 1;
}

.sh-review-card-title {
    font-weight: 600;
}

.sh-review-card-meta {
    font-size: 0.875rem;
    opacity: 0.75;
}

.sh-review-card-cta {
    font-weight: 600;
    color: rgb(255, 120, 80);
}

.sh-dashboard-section .sh-section-title {
    margin-bottom: 0.75rem;
}

.sh-deck-rail {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 1rem;
}

.sh-activity-list {
    list-style: none;
    padding: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
}

.sh-activity-row {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.75rem 1rem;
    border-radius: 0.5rem;
    background: var(--sh-surface-2, #1c1c20);
    border: 1px solid var(--sh-border, #2a2a30);
}

.sh-activity-icon iconify-icon {
    font-size: 1.25rem;
    opacity: 0.85;
}

.sh-activity-title {
    font-weight: 600;
}

.sh-activity-meta {
    font-size: 0.875rem;
    opacity: 0.7;
}

@media (max-width: 640px) {
    .sh-action-tile-row {
        grid-template-columns: 1fr;
    }
}
```

The CSS uses CSS custom properties that match patterns in the rest of the stylesheet (`--sh-surface-2`, etc.). If those exact names are not present in the existing stylesheet, replace with the literal hex equivalents you find by skimming the top of `STYLES.css`.

- [ ] **Step 3: Remove the dead styles**

In the same `STYLES.css`, delete the rules for these now-unused classes:

- `.sh-deck-grid` (the old library grid)
- `.sh-dashboard-aside`
- `.sh-side-card`, `.sh-side-card-title`
- `.sh-goal-stat`, `.sh-goal-value`, `.sh-goal-label`
- `.sh-activity-item`, `.sh-activity-dot` (the OLD hardcoded activity widget — these are *different* from the new `.sh-activity-row` rule above; do not delete `.sh-activity-row`)
- `.sh-page-actions` *only if* nothing else uses it (search the codebase first)
- `.sh-search-bar-lg` *only if* nothing else uses it

Each deletion: search for the class with `grep -rn "sh-side-card" src/main/resources` before deleting to confirm no other template uses it.

- [ ] **Step 4: Boot and visually verify**

```bash
./mvnw spring-boot:run
```

Open the dashboard. Confirm:

- Action tiles render as three cards in a row on desktop, stacked on mobile.
- Review card has the orange accent.
- Deck rail tiles render the icon, name, folder path, and "X / Y mastered" line.
- Recent activity list renders.

Stop the app.

- [ ] **Step 5: Run the full test suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/
git commit -m "$(cat <<'EOF'
feat: add cockpit dashboard styles, remove dead library-grid styles

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 20: Wire pin button to refresh the dashboard

The pin button in the rewritten fragment posts to `/decks/{id}/pin` and targets `#explorer-detail`. But the controller currently returns an empty 200 body — that would clear the dashboard. Make the pin endpoint return the refreshed dashboard fragment when invoked from the dashboard rails.

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DeckPinController.java`

- [ ] **Step 1: Update the controller to return the refreshed fragment for HTMX requests**

Replace the full controller with:

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DashboardService;
import com.HendrikHoemberg.StudyHelper.service.DeckPinService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.security.Principal;
import java.util.NoSuchElementException;

@Controller
public class DeckPinController {

    private final UserService userService;
    private final DeckPinService deckPinService;
    private final DashboardService dashboardService;

    public DeckPinController(UserService userService,
                             DeckPinService deckPinService,
                             DashboardService dashboardService) {
        this.userService = userService;
        this.deckPinService = deckPinService;
        this.dashboardService = dashboardService;
    }

    @PostMapping("/decks/{id}/pin")
    public Object togglePin(@PathVariable Long id,
                            Principal principal,
                            Model model,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        try {
            deckPinService.togglePin(user, id);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (hxRequest != null) {
            model.addAttribute("vm", dashboardService.buildFor(user));
            model.addAttribute("username", user.getUsername());
            return "fragments/explorer :: dashboardContent";
        }
        return ResponseEntity.ok().build();
    }
}
```

The mixed return type (`Object`) is acceptable here — Spring handles `ResponseEntity` and `String` view names from the same handler. Existing pin-controller tests will still pass because they did not set `HX-Request`.

- [ ] **Step 2: Run the existing test**

```bash
./mvnw test -Dtest=DeckPinControllerTests
```

Expected: still passes.

- [ ] **Step 3: Full suite**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 4: Visual check**

Boot the app, click the pin button on a deck tile in the dashboard. The rail updates without a full page refresh. Stop the app.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/DeckPinController.java
git commit -m "$(cat <<'EOF'
feat: return refreshed dashboard fragment from pin toggle for htmx callers

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 21: Manual smoke test and verification

Final pass: run through the full user journey to catch anything the tests miss.

- [ ] **Step 1: Boot the app fresh**

```bash
./mvnw spring-boot:run
```

- [ ] **Step 2: Walk the golden path**

As a seeded user:

1. Open `/dashboard`. Confirm greeting + 3 action tiles render, no other sections.
2. Click "Start studying" → wizard opens.
3. Pick a deck with some cards, finish a flashcard study session (answer one wrong, the rest right).
4. After completion, click "Back to Dashboard". Confirm:
   - "Review mistakes" card now shows `1 card to review`.
   - "Recently studied" rail now shows the deck you just studied, with mastery `0 / N` (or higher).
   - "Recent activity" list now shows one row.
5. Click "Review mistakes" → flashcard session starts with only the wrong card. Answer it correctly. Note streak is now 1 (still in mistakes? Yes — needs `>=2` to graduate).
6. Go back to dashboard. "Review mistakes" still shows `1 card to review` (correct).
7. Click "Review mistakes" again. Answer correctly again. Streak is now 2. Card graduates.
8. Dashboard: "Review mistakes" card disappears. Deck tile shows `1 / N mastered`.
9. Pin one of the recent decks. Confirm it appears in the "Pinned" rail above "Recently studied".
10. Unpin it. Confirm it disappears from "Pinned".
11. Start a new session, get partway through, close the tab.
12. Re-open `/dashboard`. The "Resume" card appears at the top of the dashboard.

- [ ] **Step 3: Edge case checks**

- Run a quiz to completion. Confirm a row appears in "Recent activity" with type "Quiz" (icon: list-checks). Confirm involved deck's `lastStudiedAt` is updated.
- Run an exam to completion. Confirm a row appears with type "Exam".
- Log in as a brand-new user (or use the admin panel to seed one). Confirm dashboard shows only greeting + action tiles.

- [ ] **Step 4: Stop the app and run the test suite once more**

```bash
./mvnw test
```

Expected: all pass.

- [ ] **Step 5: Final commit (only if anything was tweaked during smoke testing)**

If you adjusted any code during smoke testing, commit it:

```bash
git status
git add <files>
git commit -m "$(cat <<'EOF'
fix: smoke-test follow-ups for dashboard redesign

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

If nothing changed, no commit is needed.

- [ ] **Step 6: Verify the branch**

```bash
git log --oneline -25
```

Expected: a clean sequence of feat: commits from Task 1 onward. The branch is ready to merge.

---

## Notes for the implementer

- **`SessionMode.SHUFFLED` vs other names:** if `SessionMode.SHUFFLED` does not exist, check `dto/SessionMode.java` and use the actual enum constant (`SHUFFLE` or similar). The review-mistakes session needs shuffled order; if no shuffle mode exists, fall back to `DECK_BY_DECK`.
- **Folder path label:** `DashboardService.toSummary(Deck)` calls `folderService.buildPathLabel(...)`. If `FolderService` already has a method that returns the same format as `StudyDeckOption.folderPath()` (used in the old dashboard grid), reuse that instead of adding a new one.
- **Hibernate auto-DDL:** the project uses `ddl-auto=update`, so all schema changes apply on app startup. No Flyway/Liquibase changes needed.
- **Bookkeeping resilience:** every controller hook into `StudyLogService` is wrapped in `try { ... } catch (Exception ignored) {}` so that a logging failure never blocks the user's completion screen. This is deliberate — log rows are not the user's product, the completion screen is.
- **Per-card review history is intentionally NOT added:** only `correctStreak` (one int). The review-mistakes deck is good enough at this granularity; a full review log would be a separate spec.
