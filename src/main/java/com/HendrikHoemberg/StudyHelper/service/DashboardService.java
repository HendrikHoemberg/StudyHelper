package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DashboardDeckSummary;
import com.HendrikHoemberg.StudyHelper.dto.DashboardViewModel;
import com.HendrikHoemberg.StudyHelper.dto.DashboardViewModel.HeatmapEntry;
import com.HendrikHoemberg.StudyHelper.dto.SavedSessionSummary;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.StudyLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.StudyLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DashboardService {

    private static final int RECENT_DECK_LIMIT = 6;
    private static final int HEATMAP_WEEKS = 17;
    private static final int HEATMAP_DAYS = HEATMAP_WEEKS * 7; // 119
    private static final int DAILY_MINUTE_GOAL = 60;
    private static final int STREAK_MAX_LOOKBACK_DAYS = 365;

    private final DeckRepository deckRepository;
    private final StudyLogRepository studyLogRepository;
    private final FlashcardRepository flashcardRepository;
    private final SavedSessionService savedSessionService;
    private final FolderService folderService;

    public DashboardService(DeckRepository deckRepository,
                            StudyLogRepository studyLogRepository,
                            FlashcardRepository flashcardRepository,
                            SavedSessionService savedSessionService,
                            FolderService folderService) {
        this.deckRepository = deckRepository;
        this.studyLogRepository = studyLogRepository;
        this.flashcardRepository = flashcardRepository;
        this.savedSessionService = savedSessionService;
        this.folderService = folderService;
    }

    @Transactional(readOnly = true)
    public DashboardViewModel buildFor(User user) {
        Optional<SavedSessionSummary> resume = savedSessionService.findForUser(user);

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



        int streakDays = computeStreak(user);
        long dueTodayCount = flashcardRepository.countDueOrNewByUser(user, java.time.LocalDate.now());

        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.atTime(LocalTime.MAX);
        LocalDateTime startOfWeek = today.minusDays(6).atStartOfDay();
        LocalDateTime endOfWeek = endOfToday;

        long todaySec = studyLogRepository.sumDurationSecBetween(user, startOfToday, endOfToday);
        long todayCards = studyLogRepository.sumCardCountBetween(user, startOfToday, endOfToday);
        long todayCorrect = studyLogRepository.sumCorrectCountBetween(user, startOfToday, endOfToday);

        long weekSec = studyLogRepository.sumDurationSecBetween(user, startOfWeek, endOfWeek);
        long weekCards = studyLogRepository.sumCardCountBetween(user, startOfWeek, endOfWeek);
        long weekCorrect = studyLogRepository.sumCorrectCountBetween(user, startOfWeek, endOfWeek);

        int todayMinutes = (int) (todaySec / 60);
        int weeklyMinutes = (int) (weekSec / 60);
        int cardsReviewedToday = (int) todayCards;
        Integer todayAccuracyPercent = todayCards == 0
            ? null
            : (int) Math.round((todayCorrect * 100.0) / todayCards);

        List<HeatmapEntry> heatmap = buildHeatmap(user, today);
        int heatmapTotalSessions = heatmap.stream().mapToInt(HeatmapEntry::sessions).sum();

        return new DashboardViewModel(
            user.getUsername(),
            resume,
            pinned,
            recent,
            streakDays,
            dueTodayCount,
            todayMinutes,
            DAILY_MINUTE_GOAL,
            todayAccuracyPercent,
            cardsReviewedToday,
            weeklyMinutes,
            heatmap,
            heatmapTotalSessions
        );
    }

    int computeStreak(User user) {
        int streak = 0;
        LocalDate day = LocalDate.now();
        for (int i = 0; i < STREAK_MAX_LOOKBACK_DAYS; i++) {
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.atTime(LocalTime.MAX);
            if (studyLogRepository.existsByUserAndCompletedAtBetween(user, start, end)) {
                streak++;
                day = day.minusDays(1);
            } else {
                // Edge case: today has no logs yet but the user has a streak through yesterday.
                // We count a streak only as consecutive days *with* logs ending on today OR yesterday.
                if (i == 0) {
                    day = day.minusDays(1);
                    continue;
                }
                break;
            }
        }
        return streak;
    }

    List<HeatmapEntry> buildHeatmap(User user, LocalDate today) {
        LocalDate firstDay = today.minusDays(HEATMAP_DAYS - 1);
        LocalDateTime since = firstDay.atStartOfDay();

        List<Object[]> rows = studyLogRepository.aggregateDailyCountsSince(user, since);
        Map<LocalDate, Integer> byDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            int count = ((Number) row[1]).intValue();
            byDay.merge(date, count, Integer::sum);
        }

        List<HeatmapEntry> out = new ArrayList<>(HEATMAP_DAYS);
        for (int i = 0; i < HEATMAP_DAYS; i++) {
            LocalDate d = firstDay.plusDays(i);
            int sessions = byDay.getOrDefault(d, 0);
            out.add(new HeatmapEntry(d, sessions, levelFor(sessions), d.equals(today)));
        }
        return out;
    }

    private static int levelFor(int sessions) {
        if (sessions <= 0) return 0;
        if (sessions == 1) return 1;
        if (sessions == 2) return 2;
        if (sessions <= 4) return 3;
        return 4;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof Date d) return d.toLocalDate();
        if (value instanceof java.util.Date d) return new Date(d.getTime()).toLocalDate();
        if (value instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (value instanceof String s) return LocalDate.parse(s);
        throw new IllegalStateException("Unsupported date type from heatmap aggregate: " + value.getClass());
    }

    static String tagFor(Deck deck) {
        Folder folder = deck.getFolder();
        if (folder == null) return null;
        Folder root = folder;
        int safety = 32;
        while (root.getParentFolder() != null && safety-- > 0) {
            root = root.getParentFolder();
        }
        String name = root.getName();
        if (name == null || name.isBlank()) return null;
        String trimmed = name.strip();
        int end = Math.min(3, trimmed.length());
        return trimmed.substring(0, end).toUpperCase(Locale.ROOT);
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
            deck.isPinned(),
            tagFor(deck)
        );
    }

}
