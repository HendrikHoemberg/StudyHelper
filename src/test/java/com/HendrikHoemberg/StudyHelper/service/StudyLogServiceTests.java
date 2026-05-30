package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonMode;
import com.HendrikHoemberg.StudyHelper.dto.DungeonRunStats;
import com.HendrikHoemberg.StudyHelper.dto.DungeonSize;
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
    void recordFlashcards_writesLogAndUpdatesLastStudiedAt() {
        StudyCardView v1 = new StudyCardView(card1.getId(), "F1", "B1", deck.getId(), "Anatomy", "Root / Anatomy", "#000", "layers", null, null);
        StudyCardView v2 = new StudyCardView(card2.getId(), "F2", "B2", deck.getId(), "Anatomy", "Root / Anatomy", "#000", "layers", null, null);

        StudySessionState state = new StudySessionState(
            new StudySessionConfig(List.of(deck.getId()), com.HendrikHoemberg.StudyHelper.dto.SessionMode.DECK_BY_DECK, com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode.SELECTED_ORDER, false, 20),
            Map.of(deck.getId(), List.of(v1, v2)),
            List.of(v1, v2),
            2, 2, 1, 1, List.of(card2.getId())
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

        // Streaks are NOT touched by recordFlashcards
        Flashcard reloaded1 = flashcardRepository.findById(card1.getId()).orElseThrow();
        Flashcard reloaded2 = flashcardRepository.findById(card2.getId()).orElseThrow();
        assertThat(reloaded1.getCorrectStreak()).isNull();
        assertThat(reloaded2.getCorrectStreak()).isNull();

        // Deck lastStudiedAt updated
        Deck reloadedDeck = deckRepository.findById(deck.getId()).orElseThrow();
        assertThat(reloadedDeck.getLastStudiedAt()).isNotNull();
    }

    @Test
    void recordFlashcards_doesNotTouchStreaks() {
        // Pre-set streaks
        card1.setCorrectStreak(3);
        card2.setCorrectStreak(0);
        flashcardRepository.save(card1);
        flashcardRepository.save(card2);

        StudyCardView v1 = new StudyCardView(card1.getId(), "F1", "B1", deck.getId(), "Anatomy", "Root / Anatomy", "#000", "layers", null, null);
        StudyCardView v2 = new StudyCardView(card2.getId(), "F2", "B2", deck.getId(), "Anatomy", "Root / Anatomy", "#000", "layers", null, null);
        StudySessionState state = new StudySessionState(
            new StudySessionConfig(List.of(deck.getId()), com.HendrikHoemberg.StudyHelper.dto.SessionMode.DECK_BY_DECK, com.HendrikHoemberg.StudyHelper.dto.DeckOrderMode.SELECTED_ORDER, false, 20),
            Map.of(deck.getId(), List.of(v1, v2)),
            List.of(v1, v2),
            2, 2, 1, 1, List.of(card2.getId())
        );

        studyLogService.recordFlashcards(user, state);

        // Streaks unchanged
        assertThat(flashcardRepository.findById(card1.getId()).orElseThrow().getCorrectStreak()).isEqualTo(3);
        assertThat(flashcardRepository.findById(card2.getId()).orElseThrow().getCorrectStreak()).isEqualTo(0);
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
                com.HendrikHoemberg.StudyHelper.dto.Difficulty.MEDIUM),
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

    @Test
    void recordDungeon_savesWonDungeonLog() {
        DungeonRunStats stats = new DungeonRunStats(DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 8, 6, 2, true, 0, 0, 0, 0, 0);
        studyLogService.recordDungeon(user, stats, List.of(deck.getId()));

        var logs = studyLogRepository.findByUserOrderByCompletedAtDesc(user,
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(logs).hasSize(1);
        StudyLog log = logs.get(0);
        assertThat(log.getType()).isEqualTo(SavedSessionType.DUNGEON);
        assertThat(log.getTitle()).isEqualTo("Dungeon · Flashcards · Small · Victory");
        assertThat(log.getCardCount()).isEqualTo(8);
        assertThat(log.getCorrectCount()).isEqualTo(6);
        assertThat(log.getCompletedAt()).isNotNull();
    }

    @Test
    void recordDungeon_savesDefeatDungeonLog() {
        DungeonRunStats stats = new DungeonRunStats(DungeonMode.AI_QUIZ, DungeonSize.MEDIUM, 12, 7, 4, 0, false, 0, 0, 0, 0, 0);
        studyLogService.recordDungeon(user, stats, List.of());

        var logs = studyLogRepository.findByUserOrderByCompletedAtDesc(user,
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(logs).hasSize(1);
        StudyLog log = logs.get(0);
        assertThat(log.getTitle()).isEqualTo("Dungeon · AI Quiz · Medium · Defeat");
        assertThat(log.getCardCount()).isEqualTo(7);
    }

    @Test
    void recordDungeonAbandoned_savesAbandonedDungeonLog() {
        DungeonRunStats stats = new DungeonRunStats(DungeonMode.FLASHCARDS, DungeonSize.SMALL, 8, 3, 2, 4, false, 0, 0, 0, 0, 0);
        studyLogService.recordDungeonAbandoned(user, stats, List.of(deck.getId()));

        var logs = studyLogRepository.findByUserOrderByCompletedAtDesc(user,
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(logs).hasSize(1);
        StudyLog log = logs.get(0);
        assertThat(log.getTitle()).isEqualTo("Dungeon · Flashcards · Small · Abandoned");
        assertThat(log.getCardCount()).isEqualTo(3);
    }
}
