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
    private final FlashcardService flashcardService;

    public DeckService(DeckRepository deckRepository, FolderRepository folderRepository,
                       FlashcardService flashcardService) {
        this.deckRepository = deckRepository;
        this.folderRepository = folderRepository;
        this.flashcardService = flashcardService;
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
                int total = deck.getFlashcards().size();
                int usable = (int) deck.getFlashcards().stream()
                    .filter(flashcardService::hasUsableTextForAi).count();
                return new StudyDeckOption(
                    deck.getId(),
                    deck.getName(),
                    deck.getFolder().getId(),
                    buildFolderPath(deck.getFolder()),
                    deck.getFolder().getColorHex(),
                    total,
                    usable
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

    @Transactional(readOnly = true)
    public Long findFolderIdByDeckId(Long deckId, User user) {
        return deckRepository.findByIdAndUser(deckId, user)
            .map(deck -> deck.getFolder().getId())
            .orElse(null);
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
        if (folder == null) return "";
        if (folder.getParentFolder() == null) {
            return folder.getName();
        }
        return buildFolderPath(folder.getParentFolder()) + " / " + folder.getName();
    }
}
