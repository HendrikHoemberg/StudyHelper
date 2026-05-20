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

        touchDecks(deckIds);

        StudyLog log = new StudyLog();
        log.setUser(user);
        log.setType(SavedSessionType.FLASHCARDS);
        log.setTitle(flashcardTitle(state));
        log.setCardCount(state.queue().size());
        log.setCorrectCount(state.correctAnswers());
        log.setDurationSec(null);
        log.setCompletedAt(LocalDateTime.now());
        studyLogRepository.save(log);
    }

    @Transactional
    public void recordQuiz(User user, QuizSessionState state) {
        if (state == null) return;

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
