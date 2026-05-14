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

@Service
public class FlashcardService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final FlashcardRepository flashcardRepository;
    private final DeckRepository deckRepository;
    private final FileStorageService fileStorageService;
    private final StorageQuotaService storageQuotaService;
    private final UploadValidator uploadValidator;

    public FlashcardService(FlashcardRepository flashcardRepository,
                            DeckRepository deckRepository,
                            FileStorageService fileStorageService,
                            StorageQuotaService storageQuotaService,
                            UploadValidator uploadValidator) {
        this.flashcardRepository = flashcardRepository;
        this.deckRepository = deckRepository;
        this.fileStorageService = fileStorageService;
        this.storageQuotaService = storageQuotaService;
        this.uploadValidator = uploadValidator;
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
        long frontBytes = imageSize(frontImage);
        long backBytes = imageSize(backImage);
        storageQuotaService.assertWithinQuota(user, 0L, frontBytes + backBytes);
        card.setFrontImageFilename(storeImage(frontImage));
        card.setBackImageFilename(storeImage(backImage));
        card.setFrontImageSizeBytes(frontBytes == 0 ? null : frontBytes);
        card.setBackImageSizeBytes(backBytes == 0 ? null : backBytes);
        return flashcardRepository.save(card);
    }

    @Transactional
    public Flashcard updateFlashcard(Long id, String frontText, String backText, String username,
                                     MultipartFile frontImage, boolean removeFrontImage,
                                     MultipartFile backImage, boolean removeBackImage) {
        Flashcard card = flashcardRepository.findByIdAndDeckUserUsername(id, username)
            .orElseThrow(() -> new NoSuchElementException("Flashcard not found"));
        User user = card.getDeck().getUser();
        card.setFrontText(frontText);
        card.setBackText(backText);

        long oldFrontBytes = nullableBytes(card.getFrontImageSizeBytes());
        long oldBackBytes = nullableBytes(card.getBackImageSizeBytes());
        long newFrontBytes = resolveImageSize(oldFrontBytes, frontImage, removeFrontImage);
        long newBackBytes = resolveImageSize(oldBackBytes, backImage, removeBackImage);

        long oldTotalBytes = oldFrontBytes + oldBackBytes;
        long newTotalBytes = newFrontBytes + newBackBytes;
        long bytesRemoved = Math.max(0L, oldTotalBytes - newTotalBytes);
        long bytesAdded = Math.max(0L, newTotalBytes - oldTotalBytes);
        storageQuotaService.assertWithinQuota(user, bytesRemoved, bytesAdded);

        card.setFrontImageFilename(
            resolveImage(card.getFrontImageFilename(), frontImage, removeFrontImage));
        card.setBackImageFilename(
            resolveImage(card.getBackImageFilename(), backImage, removeBackImage));
        card.setFrontImageSizeBytes(newFrontBytes == 0 ? null : newFrontBytes);
        card.setBackImageSizeBytes(newBackBytes == 0 ? null : newBackBytes);

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

    @Transactional
    public void replaceImage(Long id, String side, MultipartFile image, User user) {
        Flashcard card = flashcardRepository.findByIdAndDeckUserUsername(id, user.getUsername())
            .orElseThrow(() -> new NoSuchElementException("Flashcard not found"));

        String oldFilename = "front".equals(side) ? card.getFrontImageFilename() : card.getBackImageFilename();
        long oldBytes = "front".equals(side)
            ? nullableBytes(card.getFrontImageSizeBytes())
            : nullableBytes(card.getBackImageSizeBytes());
        long newBytes = imageSize(image);
        storageQuotaService.assertWithinQuota(user, oldBytes, newBytes);
        String newFilename = storeImage(image);
        deleteImageFile(oldFilename);

        if ("front".equals(side)) {
            card.setFrontImageFilename(newFilename);
            card.setFrontImageSizeBytes(newBytes == 0 ? null : newBytes);
        } else {
            card.setBackImageFilename(newFilename);
            card.setBackImageSizeBytes(newBytes == 0 ? null : newBytes);
        }
        flashcardRepository.save(card);
    }

    public boolean hasUsableTextForAi(Flashcard card) {
        boolean hasFront = card.getFrontText() != null && !card.getFrontText().isBlank();
        boolean hasBack = card.getBackText() != null && !card.getBackText().isBlank();
        return hasFront || hasBack;
    }

    private String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image exceeds the 10 MB limit.");
        }
        uploadValidator.validateImage(file);
        try {
            return fileStorageService.store(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store image: " + e.getMessage(), e);
        }
    }

    private long imageSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return 0L;
        }
        return file.getSize();
    }

    private long nullableBytes(Long bytes) {
        return bytes == null ? 0L : bytes;
    }

    private long resolveImageSize(long existingBytes, MultipartFile newFile, boolean remove) {
        if (newFile != null && !newFile.isEmpty()) {
            return newFile.getSize();
        }
        if (remove) {
            return 0L;
        }
        return existingBytes;
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
