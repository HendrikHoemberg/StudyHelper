package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.config.AppDefaults;
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
import com.HendrikHoemberg.StudyHelper.exception.ResourceNotFoundException;
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
    public Deck createDeck(String name, String colorHex, String iconName, Long folderId, User user) {
        Folder folder = folderRepository.findByIdAndUser(folderId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        Deck deck = new Deck();
        deck.setName(name);
        deck.setColorHex(resolveDeckColor(colorHex, folder));
        deck.setIconName(iconName != null && !iconName.isBlank() ? iconName : "layers");
        deck.setFolder(folder);
        deck.setUser(user);
        return deckRepository.save(deck);
    }

    private String resolveDeckColor(String colorHex, Folder folder) {
        if (colorHex != null && !colorHex.isBlank()) {
            return colorHex;
        }
        if (folder.getColorHex() != null && !folder.getColorHex().isBlank()) {
            return folder.getColorHex();
        }
        return AppDefaults.DEFAULT_COLOR_HEX;
    }

    @Transactional(readOnly = true)
    public Deck getDeck(Long id, User user) {
        Deck deck = deckRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new ResourceNotFoundException("Deck not found"));
        // Initialize lazy collections within transaction
        deck.getFlashcards().size();
        deck.getFolder().getName();
        return deck;
    }

    @Transactional
    public Deck updateDeck(Long id, String newName, String colorHex, String iconName, User user) {
        Deck deck = deckRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new ResourceNotFoundException("Deck not found"));
        if (newName != null && !newName.isBlank()) {
            deck.setName(newName);
        }
        if (colorHex != null && !colorHex.isBlank()) {
            deck.setColorHex(colorHex);
        }
        deck.setIconName(iconName != null && !iconName.isBlank() ? iconName : "layers");
        return deckRepository.save(deck);
    }

    @Transactional
    public Long deleteDeck(Long id, User user) {
        Deck deck = deckRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new ResourceNotFoundException("Deck not found"));
        Long folderId = deck.getFolder().getId();
        deckRepository.delete(deck);
        return folderId;
    }

    @Transactional(readOnly = true)
    public List<Deck> getDecksForFolder(Long folderId, User user) {
        Folder folder = folderRepository.findByIdAndUser(folderId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        return deckRepository.findByUserAndFolder(user, folder);
    }

    @Transactional(readOnly = true)
    public List<Deck> getValidatedDecksInRequestedOrder(List<Long> deckIds, User user) {
        List<Long> normalized = normalizeDeckIds(deckIds);
        List<Deck> orderedDecks = new ArrayList<>();
        for (Long deckId : normalized) {
            Deck deck = deckRepository.findByIdAndUser(deckId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Deck not found for user: " + deckId));
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
                    deck.getColorHex(),
                    deck.getIconName(),
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
