package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import com.HendrikHoemberg.StudyHelper.dto.GeneratedFlashcard;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class FlashcardGenerationPersistenceService {

    private final DeckRepository deckRepository;
    private final FolderRepository folderRepository;
    private final FlashcardRepository flashcardRepository;

    public FlashcardGenerationPersistenceService(DeckRepository deckRepository,
                                                 FolderRepository folderRepository,
                                                 FlashcardRepository flashcardRepository) {
        this.deckRepository = deckRepository;
        this.folderRepository = folderRepository;
        this.flashcardRepository = flashcardRepository;
    }

    @Transactional
    public Deck saveGeneratedCards(FlashcardGenerationDestination destination,
                                   Long existingDeckId,
                                   Long newDeckFolderId,
                                   String newDeckName,
                                   User user,
                                   List<GeneratedFlashcard> generatedCards) {
        if (destination == null) {
            throw new IllegalArgumentException("Destination is required.");
        }
        if (generatedCards == null || generatedCards.isEmpty()) {
            throw new IllegalArgumentException("No generated flashcards to save.");
        }

        return switch (destination) {
            case EXISTING_DECK -> persistToExistingDeck(generatedCards, user, existingDeckId);
            case NEW_DECK -> persistToNewDeck(generatedCards, user, newDeckFolderId, newDeckName);
        };
    }

    @Transactional(readOnly = true)
    public void validateDestination(FlashcardGenerationDestination destination,
                                    Long existingDeckId,
                                    Long newDeckFolderId,
                                    String newDeckName,
                                    User user) {
        if (destination == null) {
            throw new IllegalArgumentException("Destination is required.");
        }

        switch (destination) {
            case EXISTING_DECK -> findValidatedExistingDeck(existingDeckId, user);
            case NEW_DECK -> findValidatedNewDeckFolder(newDeckFolderId, newDeckName, user);
        }
    }

    private Deck findValidatedExistingDeck(Long deckId, User user) {
        if (deckId == null) {
            throw new IllegalArgumentException("Existing deck id is required.");
        }
        return deckRepository.findByIdAndUser(deckId, user)
            .orElseThrow(() -> new NoSuchElementException("Deck not found"));
    }

    private Folder findValidatedNewDeckFolder(Long folderId, String deckName, User user) {
        if (folderId == null) {
            throw new IllegalArgumentException("Destination folder id is required.");
        }
        if (deckName == null || deckName.isBlank()) {
            throw new IllegalArgumentException("Deck name is required.");
        }

        Folder folder = folderRepository.findByIdAndUser(folderId, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));

        String normalizedName = deckName.trim();
        boolean duplicateName = deckRepository.findByUserAndFolder(user, folder).stream()
            .anyMatch(deck -> normalizedName.equals(deck.getName()));
        if (duplicateName) {
            throw new IllegalArgumentException("A deck named \"" + normalizedName + "\" already exists in this folder.");
        }
        return folder;
    }

    private Deck persistToExistingDeck(List<GeneratedFlashcard> generatedCards, User user, Long deckId) {
        Deck deck = findValidatedExistingDeck(deckId, user);

        List<Flashcard> cards = buildFlashcards(generatedCards, deck);
        deck.getFlashcards().addAll(cards);
        flashcardRepository.saveAll(cards);
        return deck;
    }

    private Deck persistToNewDeck(List<GeneratedFlashcard> generatedCards, User user, Long folderId, String deckName) {
        Folder folder = findValidatedNewDeckFolder(folderId, deckName, user);

        Deck deck = new Deck();
        deck.setName(deckName.trim());
        deck.setFolder(folder);
        deck.setUser(user);
        Deck savedDeck = deckRepository.save(deck);

        List<Flashcard> cards = buildFlashcards(generatedCards, savedDeck);
        savedDeck.getFlashcards().addAll(cards);
        flashcardRepository.saveAll(cards);
        return savedDeck;
    }

    private List<Flashcard> buildFlashcards(List<GeneratedFlashcard> generatedCards, Deck deck) {
        List<Flashcard> cards = new ArrayList<>(generatedCards.size());
        for (GeneratedFlashcard generated : generatedCards) {
            Flashcard card = new Flashcard();
            card.setFrontText(generated.frontText());
            card.setBackText(generated.backText());
            card.setDeck(deck);
            cards.add(card);
        }
        return cards;
    }
}
