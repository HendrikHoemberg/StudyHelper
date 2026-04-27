package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final DeckRepository deckRepository;

    public FlashcardService(FlashcardRepository flashcardRepository, DeckRepository deckRepository) {
        this.flashcardRepository = flashcardRepository;
        this.deckRepository = deckRepository;
    }

    @Transactional
    public Flashcard createFlashcard(String frontText, String backText, Long deckId, User user) {
        Deck deck = deckRepository.findByIdAndUser(deckId, user)
            .orElseThrow(() -> new NoSuchElementException("Deck not found"));

        Flashcard card = new Flashcard();
        card.setFrontText(frontText);
        card.setBackText(backText);
        card.setDeck(deck);
        return flashcardRepository.save(card);
    }

    @Transactional
    public Flashcard updateFlashcard(Long id, String frontText, String backText, String username) {
        Flashcard card = flashcardRepository.findByIdAndDeckUserUsername(id, username)
            .orElseThrow(() -> new NoSuchElementException("Flashcard not found"));
        card.setFrontText(frontText);
        card.setBackText(backText);
        return flashcardRepository.save(card);
    }

    @Transactional
    public Long deleteFlashcard(Long id, String username) {
        Flashcard card = flashcardRepository.findByIdAndDeckUserUsername(id, username)
            .orElseThrow(() -> new NoSuchElementException("Flashcard not found"));
        Long deckId = card.getDeck().getId();
        flashcardRepository.delete(card);
        return deckId;
    }

    @Transactional(readOnly = true)
    public List<Flashcard> getFlashcardsForDeck(Long deckId, User user) {
        Deck deck = deckRepository.findByIdAndUser(deckId, user)
            .orElseThrow(() -> new NoSuchElementException("Deck not found"));
        return flashcardRepository.findByDeck(deck);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Flashcard>> getFlashcardsGroupedByDeck(List<Deck> orderedDecks) {
        Map<Long, List<Flashcard>> grouped = new LinkedHashMap<>();
        if (orderedDecks == null || orderedDecks.isEmpty()) {
            return grouped;
        }

        for (Deck deck : orderedDecks) {
            grouped.put(deck.getId(), new ArrayList<>());
        }

        List<Flashcard> cards = flashcardRepository.findByDeckIn(orderedDecks);
        for (Flashcard card : cards) {
            List<Flashcard> bucket = grouped.get(card.getDeck().getId());
            if (bucket != null) {
                bucket.add(card);
            }
        }

        return grouped;
    }

    @Transactional(readOnly = true)
    public List<Flashcard> getFlashcardsFlattened(List<Deck> orderedDecks) {
        if (orderedDecks == null || orderedDecks.isEmpty()) {
            return List.of();
        }
        return flashcardRepository.findByDeckIn(orderedDecks);
    }

    @Transactional(readOnly = true)
    public Flashcard getFlashcardForUser(Long cardId, User user) {
        return flashcardRepository.findByIdAndDeckUserUsername(cardId, user.getUsername())
            .orElseThrow(() -> new NoSuchElementException("Flashcard not found"));
    }
}
