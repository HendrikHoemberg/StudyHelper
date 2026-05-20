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
            .filter(d -> !d.isPinned())
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
            folderService.buildFolderPathString(deck.getFolder()),
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
