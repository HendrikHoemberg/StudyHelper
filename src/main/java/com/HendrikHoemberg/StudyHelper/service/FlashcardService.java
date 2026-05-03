package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class FlashcardService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final FlashcardRepository flashcardRepository;
    private final DeckRepository deckRepository;
    private final FileStorageService fileStorageService;

    public FlashcardService(FlashcardRepository flashcardRepository,
                            DeckRepository deckRepository,
                            FileStorageService fileStorageService) {
        this.flashcardRepository = flashcardRepository;
        this.deckRepository = deckRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public Flashcard createFlashcard(String frontText, String backText, Long deckId, User user,
                                     MultipartFile frontImage, MultipartFile backImage) {
        Deck deck = deckRepository.findByIdAndUser(deckId, user)
            .orElseThrow(() -> new NoSuchElementException("Deck not found"));

        Flashcard card = new Flashcard();
        card.setFrontText(frontText);
        card.setBackText(backText);
        card.setDeck(deck);
        card.setFrontImageFilename(storeImage(frontImage));
        card.setBackImageFilename(storeImage(backImage));
        return flashcardRepository.save(card);
    }

    @Transactional
    public Flashcard updateFlashcard(Long id, String frontText, String backText, String username,
                                     MultipartFile frontImage, boolean removeFrontImage,
                                     MultipartFile backImage, boolean removeBackImage) {
        Flashcard card = flashcardRepository.findByIdAndDeckUserUsername(id, username)
            .orElseThrow(() -> new NoSuchElementException("Flashcard not found"));
        card.setFrontText(frontText);
        card.setBackText(backText);

        card.setFrontImageFilename(
            resolveImage(card.getFrontImageFilename(), frontImage, removeFrontImage));
        card.setBackImageFilename(
            resolveImage(card.getBackImageFilename(), backImage, removeBackImage));

        return flashcardRepository.save(card);
    }

    @Transactional
    public Long deleteFlashcard(Long id, String username) {
        Flashcard card = flashcardRepository.findByIdAndDeckUserUsername(id, username)
            .orElseThrow(() -> new NoSuchElementException("Flashcard not found"));
        Long deckId = card.getDeck().getId();
        deleteImageFile(card.getFrontImageFilename());
        deleteImageFile(card.getBackImageFilename());
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

    private String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_IMAGE_TYPES.contains(mime)) {
            throw new IllegalArgumentException("Unsupported image type. Use JPEG, PNG, WebP, or GIF.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image exceeds the 5 MB limit.");
        }
        try {
            return fileStorageService.store(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store image: " + e.getMessage(), e);
        }
    }

    private String resolveImage(String existing, MultipartFile newFile, boolean remove) {
        if (newFile != null && !newFile.isEmpty()) {
            deleteImageFile(existing);
            return storeImage(newFile);
        }
        if (remove) {
            deleteImageFile(existing);
            return null;
        }
        return existing;
    }

    private void deleteImageFile(String filename) {
        if (filename == null) return;
        try {
            fileStorageService.delete(filename);
        } catch (IOException ignored) {
        }
    }
}
