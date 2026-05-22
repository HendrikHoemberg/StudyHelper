package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DashboardViewModel;
import com.HendrikHoemberg.StudyHelper.dto.DashboardViewModel.HeatmapEntry;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.StudyLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.StudyLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
        assertThat(vm.streakDays()).isZero();
        assertThat(vm.dueTodayCount()).isZero();
        assertThat(vm.todayMinutes()).isZero();
        assertThat(vm.dailyMinuteGoal()).isEqualTo(60);
        assertThat(vm.todayAccuracyPercent()).isNull();
        assertThat(vm.cardsReviewedToday()).isZero();
        assertThat(vm.heatmap()).hasSize(119);
        assertThat(vm.heatmapTotalSessions()).isZero();
        assertThat(vm.heatmap().get(118).today()).isTrue();
        assertThat(vm.heatmap().get(117).today()).isFalse();
        assertThat(vm.heatmap()).allSatisfy(e -> assertThat(e.level()).isZero());
    }

    @Test
    void populatedUser_returnsAllSections() {
        var em = emFactory.getObject();
        User user = persistUser("hendrik");
        Folder folder = persistFolder(user, "Informatik");
        Deck pinned = persistDeck(user, folder, "Pinned deck", true, LocalDateTime.now().minusDays(1));
        Deck recent = persistDeck(user, folder, "Recent deck", false, LocalDateTime.now().minusHours(1));
        Deck stale = persistDeck(user, folder, "Stale", false, null);

        persistCard(recent, 0);
        persistCard(recent, 2);
        em.flush();

        StudyLog log = newLog(user, "Recent deck", 2, 1, LocalDateTime.now());
        log.setDurationSec(600);
        studyLogRepository.save(log);

        DashboardViewModel vm = dashboardService.buildFor(user);

        assertThat(vm.reviewMistakesCount()).isEqualTo(1);
        assertThat(vm.pinnedDecks()).extracting("deckName").containsExactly("Pinned deck");
        assertThat(vm.recentDecks()).extracting("deckName").containsExactly("Recent deck");
        assertThat(vm.recentDecks().get(0).masteredCards()).isEqualTo(1);
        assertThat(vm.recentDecks().get(0).totalCards()).isEqualTo(2);
        assertThat(vm.recentDecks().get(0).tagCode()).isEqualTo("INF");
        assertThat(vm.pinnedDecks().get(0).tagCode()).isEqualTo("INF");
        assertThat(vm.recentDecks()).extracting("deckName").doesNotContain("Stale");
        assertThat(vm.dueTodayCount()).isEqualTo(2);          // both cards are new (null dueDate)
        assertThat(vm.todayMinutes()).isEqualTo(10);
        assertThat(vm.cardsReviewedToday()).isEqualTo(2);
        assertThat(vm.todayAccuracyPercent()).isEqualTo(50); // 1 of 2
        assertThat(vm.streakDays()).isEqualTo(1);
        assertThat(vm.heatmapTotalSessions()).isEqualTo(1);
        assertThat(vm.heatmap().get(118).sessions()).isEqualTo(1);
        assertThat(vm.heatmap().get(118).level()).isEqualTo(1);
    }

    @Test
    void streak_countsConsecutiveDaysWithLogs() {
        User user = persistUser("streaker");
        LocalDate today = LocalDate.now();
        persistLogOnDay(user, today);
        persistLogOnDay(user, today.minusDays(1));
        persistLogOnDay(user, today.minusDays(2));
        // gap on day -3, so streak should be 3
        persistLogOnDay(user, today.minusDays(4));
        persistLogOnDay(user, today.minusDays(5));

        DashboardViewModel vm = dashboardService.buildFor(user);
        assertThat(vm.streakDays()).isEqualTo(3);
    }

    @Test
    void streak_countsFromYesterdayWhenTodayHasNoLogs() {
        User user = persistUser("yest");
        LocalDate today = LocalDate.now();
        persistLogOnDay(user, today.minusDays(1));
        persistLogOnDay(user, today.minusDays(2));

        DashboardViewModel vm = dashboardService.buildFor(user);
        assertThat(vm.streakDays()).isEqualTo(2);
    }

    @Test
    void tagCode_isNullWhenRootFolderHasNoName() {
        var em = emFactory.getObject();
        User user = persistUser("noname");
        Folder folder = persistFolder(user, "");
        Deck deck = persistDeck(user, folder, "Orphan", true, LocalDateTime.now().minusHours(1));
        em.flush();

        DashboardViewModel vm = dashboardService.buildFor(user);
        assertThat(vm.pinnedDecks()).hasSize(1);
        assertThat(vm.pinnedDecks().get(0).tagCode()).isNull();
    }

    @Test
    void tagCode_walksToRootFolder() {
        var em = emFactory.getObject();
        User user = persistUser("nested");
        Folder root = persistFolder(user, "Mathematik");
        Folder child = new Folder();
        child.setName("Lineare Algebra");
        child.setUser(user);
        child.setParentFolder(root);
        em.persist(child);
        Deck deck = persistDeck(user, child, "LA II", true, LocalDateTime.now().minusHours(1));
        em.flush();

        DashboardViewModel vm = dashboardService.buildFor(user);
        assertThat(vm.pinnedDecks().get(0).tagCode()).isEqualTo("MAT");
    }

    @Test
    void heatmap_alwaysHas119EntriesWithTodayLast() {
        User user = persistUser("heat");
        DashboardViewModel vm = dashboardService.buildFor(user);
        assertThat(vm.heatmap()).hasSize(119);
        assertThat(vm.heatmap().get(0).date()).isEqualTo(LocalDate.now().minusDays(118));
        assertThat(vm.heatmap().get(118).date()).isEqualTo(LocalDate.now());
        assertThat(vm.heatmap().get(118).today()).isTrue();
    }

    private User persistUser(String prefix) {
        var em = emFactory.getObject();
        User u = new User();
        u.setUsername(prefix + "-" + System.nanoTime());
        u.setPassword("x".repeat(60));
        em.persist(u);
        return u;
    }

    private Folder persistFolder(User user, String name) {
        var em = emFactory.getObject();
        Folder f = new Folder();
        f.setName(name);
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

    private StudyLog newLog(User user, String title, int cards, int correct, LocalDateTime at) {
        StudyLog log = new StudyLog();
        log.setUser(user);
        log.setType(SavedSessionType.FLASHCARDS);
        log.setTitle(title);
        log.setCardCount(cards);
        log.setCorrectCount(correct);
        log.setCompletedAt(at);
        return log;
    }

    private void persistLogOnDay(User user, LocalDate day) {
        StudyLog log = newLog(user, "log " + day, 1, 1, day.atTime(LocalTime.NOON));
        log.setDurationSec(60);
        studyLogRepository.save(log);
    }
}
