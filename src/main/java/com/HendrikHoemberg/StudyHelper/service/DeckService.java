package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.StudyDeckOption;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

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

    @Transactional(readOnly = true)
    public List<Deck> getValidatedDecksInRequestedOrder(List<Long> deckIds, User user) {
        List<Long> normalized = normalizeDeckIds(deckIds);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Select at least one deck.");
        }

        List<Deck> orderedDecks = new ArrayList<>();
        for (Long deckId : normalized) {
            Deck deck = deckRepository.findByIdAndUser(deckId, user)
                .orElseThrow(() -> new NoSuchElementException("Deck not found for user: " + deckId));
            deck.getFlashcards().size();
            deck.getFolder().getId();
            orderedDecks.add(deck);
        }

        return orderedDecks;
    }

    @Transactional(readOnly = true)
    public List<StudyDeckOption> getStudyDeckOptions(User user) {
        return deckRepository.findByUser(user).stream()
            .map(deck -> {
                deck.getFlashcards().size();
                return new StudyDeckOption(
                    deck.getId(),
                    deck.getName(),
                    buildFolderPath(deck.getFolder()),
                    deck.getFlashcards().size()
                );
            })
            .sorted((a, b) -> {
                int pathCmp = a.folderPath().compareToIgnoreCase(b.folderPath());
                if (pathCmp != 0) {
                    return pathCmp;
                }
                return a.deckName().compareToIgnoreCase(b.deckName());
            })
            .toList();
    }

    private List<Long> normalizeDeckIds(List<Long> deckIds) {
        if (deckIds == null || deckIds.isEmpty()) {
            return List.of();
        }

        List<Long> normalized = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long deckId : deckIds) {
            if (deckId != null && seen.add(deckId)) {
                normalized.add(deckId);
            }
        }
        return normalized;
    }

    private String buildFolderPath(Folder folder) {
        List<String> segments = new ArrayList<>();
        Folder current = folder;
        while (current != null) {
            segments.add(0, current.getName());
            current = current.getParentFolder();
        }
        return String.join(" / ", segments);
    }
}
