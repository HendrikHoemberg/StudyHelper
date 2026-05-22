package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class SrsSeedingService {

    private final FlashcardRepository flashcardRepository;

    public SrsSeedingService(FlashcardRepository flashcardRepository) {
        this.flashcardRepository = flashcardRepository;
    }

    @Transactional
    public int seedUnscheduledCards() {
        List<Flashcard> cards = flashcardRepository.findUnscheduledEstablishedCards();
        LocalDate today = LocalDate.now();
        for (Flashcard fc : cards) {
            fc.setRepetitions(2);
            fc.setIntervalDays(SrsScheduler.SECOND_INTERVAL);
            fc.setEaseFactor(SrsScheduler.INITIAL_EF);
            fc.setDueDate(today);
        }
        flashcardRepository.saveAll(cards);
        return cards.size();
    }
}
