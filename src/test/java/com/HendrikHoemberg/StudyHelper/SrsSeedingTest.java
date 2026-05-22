package com.HendrikHoemberg.StudyHelper;

import com.HendrikHoemberg.StudyHelper.entity.*;
import com.HendrikHoemberg.StudyHelper.repository.*;
import com.HendrikHoemberg.StudyHelper.service.SrsSeedingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SrsSeedingTest {

    @Autowired SrsSeedingService seedingService;
    @Autowired FlashcardRepository flashcardRepository;
    @Autowired DeckRepository deckRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired UserRepository userRepository;

    @Test
    void establishedCardsAreSeededPartlyLearned() {
        User user = new User(); user.setUsername("seed-user"); user.setPassword("x");
        user = userRepository.save(user);
        Folder folder = new Folder(); folder.setName("F"); folder.setUser(user);
        folder = folderRepository.save(folder);
        Deck deck = new Deck(); deck.setName("D"); deck.setFolder(folder); deck.setUser(user);
        deck = deckRepository.save(deck);

        Flashcard established = new Flashcard();
        established.setFrontText("q"); established.setBackText("a");
        established.setDeck(deck); established.setCorrectStreak(5);
        established = flashcardRepository.save(established);

        Flashcard fresh = new Flashcard();
        fresh.setFrontText("q2"); fresh.setBackText("a2");
        fresh.setDeck(deck); fresh.setCorrectStreak(0);
        fresh = flashcardRepository.save(fresh);

        seedingService.seedUnscheduledCards();

        Flashcard e = flashcardRepository.findById(established.getId()).orElseThrow();
        assertThat(e.getRepetitions()).isEqualTo(2);
        assertThat(e.getIntervalDays()).isEqualTo(6);
        assertThat(e.getDueDate()).isEqualTo(java.time.LocalDate.now());

        Flashcard f = flashcardRepository.findById(fresh.getId()).orElseThrow();
        assertThat(f.getDueDate()).isNull(); // stays new
    }
}
