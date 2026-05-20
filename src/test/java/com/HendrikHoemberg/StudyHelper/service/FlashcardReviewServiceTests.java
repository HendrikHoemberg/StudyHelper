package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
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

        Flashcard a1 = persistCard(em, deckA, 0);
        Flashcard a2 = persistCard(em, deckA, 0);
        persistCard(em, deckA, 2);
        persistCard(em, deckA, null);
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
