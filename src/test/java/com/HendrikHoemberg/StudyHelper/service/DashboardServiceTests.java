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
