package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class DeckService {

    private final DeckRepository deckRepository;
    private final FolderRepository folderRepository;

    public DeckService(DeckRepository deckRepository, FolderRepository folderRepository) {
        this.deckRepository = deckRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional
    public Deck createDeck(String name, Long folderId, User user) {
        Folder folder = folderRepository.findByIdAndUser(folderId, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));

        Deck deck = new Deck();
        deck.setName(name);
        deck.setFolder(folder);
        deck.setUser(user);
        return deckRepository.save(deck);
    }

    @Transactional(readOnly = true)
    public Deck getDeck(Long id, User user) {
        Deck deck = deckRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Deck not found"));
        // Initialize lazy collections within transaction
        deck.getFlashcards().size();
        deck.getFolder().getName();
        return deck;
    }

    @Transactional
    public Deck renameDeck(Long id, String newName, User user) {
        Deck deck = deckRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Deck not found"));
        deck.setName(newName);
        return deckRepository.save(deck);
    }

    @Transactional
    public Long deleteDeck(Long id, User user) {
        Deck deck = deckRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Deck not found"));
        Long folderId = deck.getFolder().getId();
        deckRepository.delete(deck);
        return folderId;
    }

    @Transactional(readOnly = true)
    public List<Deck> getDecksForFolder(Long folderId, User user) {
        Folder folder = folderRepository.findByIdAndUser(folderId, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));
        return deckRepository.findByUserAndFolder(user, folder);
    }
}
