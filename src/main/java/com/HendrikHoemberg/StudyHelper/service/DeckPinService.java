package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class DeckPinService {

    private final DeckRepository deckRepository;

    public DeckPinService(DeckRepository deckRepository) {
        this.deckRepository = deckRepository;
    }

    @Transactional
    public boolean togglePin(User user, Long deckId) {
        Deck deck = deckRepository.findByIdAndUser(deckId, user)
            .orElseThrow(() -> new NoSuchElementException("Deck not found"));
        deck.setPinned(!deck.isPinned());
        deckRepository.save(deck);
        return deck.isPinned();
    }
}
