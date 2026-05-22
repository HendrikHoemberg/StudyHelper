package com.HendrikHoemberg.StudyHelper;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.*;
import com.HendrikHoemberg.StudyHelper.repository.*;
import com.HendrikHoemberg.StudyHelper.service.StudySessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RecordAnswerSchedulingTest {

    @Autowired StudySessionService studySessionService;
    @Autowired FlashcardRepository flashcardRepository;
    @Autowired ReviewLogRepository reviewLogRepository;
    @Autowired DeckRepository deckRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired UserRepository userRepository;

    @Test
    void goodAnswerSchedulesCardAndWritesReviewLog() {
        User user = new User();
        user.setUsername("srs-test-user");
        user.setPassword("x");
        user = userRepository.save(user);

        Folder folder = new Folder();
        folder.setName("F");
        folder.setUser(user);
        folder = folderRepository.save(folder);

        Deck deck = new Deck();
        deck.setName("D");
        deck.setFolder(folder);
        deck.setUser(user);
        deck = deckRepository.save(deck);

        Flashcard card = new Flashcard();
        card.setFrontText("q");
        card.setBackText("a");
        card.setDeck(deck);
        card = flashcardRepository.save(card);

        StudyCardView view = new StudyCardView(card.getId(), "q", "a",
            deck.getId(), "D", "F", null, null, null, null);
        StudySessionConfig config = new StudySessionConfig(
            List.of(deck.getId()), SessionMode.DECK_BY_DECK, DeckOrderMode.SELECTED_ORDER, false, 20);
        StudySessionState state = new StudySessionState(
            config, Map.of(deck.getId(), List.of(view)), List.of(view), 0, 0, 0, 0, List.of());

        long beforeLogs = reviewLogRepository.count();
        studySessionService.recordAnswer(state, card.getId(), Grade.GOOD);

        Flashcard reloaded = flashcardRepository.findById(card.getId()).orElseThrow();
        assertThat(reloaded.getRepetitions()).isEqualTo(1);
        assertThat(reloaded.getIntervalDays()).isEqualTo(1);
        assertThat(reloaded.getDueDate()).isEqualTo(java.time.LocalDate.now().plusDays(1));
        assertThat(reviewLogRepository.count()).isEqualTo(beforeLogs + 1);
    }
}
