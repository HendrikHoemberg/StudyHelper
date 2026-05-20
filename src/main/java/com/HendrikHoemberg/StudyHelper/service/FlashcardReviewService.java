package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FlashcardReviewService {

    private final FlashcardRepository flashcardRepository;

    public FlashcardReviewService(FlashcardRepository flashcardRepository) {
        this.flashcardRepository = flashcardRepository;
    }

    @Transactional(readOnly = true)
    public long countMistakes(User user) {
        return flashcardRepository.countMistakesByUser(user);
    }

    @Transactional(readOnly = true)
    public List<Flashcard> loadMistakeCards(User user) {
        return flashcardRepository.findMistakesByUser(user);
    }
}
