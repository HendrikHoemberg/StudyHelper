package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlashcardServiceTests {

    private FlashcardRepository flashcardRepository;
    private DeckRepository deckRepository;
    private FileStorageService fileStorageService;
    private StorageQuotaService storageQuotaService;
    private FlashcardService flashcardService;

    @BeforeEach
    void setUp() {
        flashcardRepository = mock(FlashcardRepository.class);
        deckRepository = mock(DeckRepository.class);
        fileStorageService = mock(FileStorageService.class);
        storageQuotaService = mock(StorageQuotaService.class);
        flashcardService = new FlashcardService(
            flashcardRepository,
            deckRepository,
            fileStorageService,
            storageQuotaService
        );
    }

    @Test
    void createFlashcard_WithImages_PersistsImageSizesAndChecksQuota() throws IOException {
        User user = new User();
        user.setUsername("alice");
        Deck deck = new Deck();
        deck.setId(11L);
        deck.setUser(user);

        MultipartFile frontImage = new MockMultipartFile("frontImage", "front.png", "image/png", new byte[120]);
        MultipartFile backImage = new MockMultipartFile("backImage", "back.png", "image/png", new byte[80]);

        when(deckRepository.findByIdAndUser(11L, user)).thenReturn(Optional.of(deck));
        when(fileStorageService.store(frontImage)).thenReturn("front-stored.png");
        when(fileStorageService.store(backImage)).thenReturn("back-stored.png");
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flashcard saved = flashcardService.createFlashcard("q", "a", 11L, user, frontImage, backImage);

        verify(storageQuotaService, times(1)).assertWithinQuota(user, 0L, 200L);
        assertThat(saved.getFrontImageFilename()).isEqualTo("front-stored.png");
        assertThat(saved.getBackImageFilename()).isEqualTo("back-stored.png");
        assertThat(saved.getFrontImageSizeBytes()).isEqualTo(120L);
        assertThat(saved.getBackImageSizeBytes()).isEqualTo(80L);
    }

    @Test
    void replaceImage_UpdatesSizeAndChecksQuotaWithReplacementDelta() throws IOException {
        User user = new User();
        user.setUsername("alice");
        Deck deck = new Deck();
        deck.setUser(user);

        Flashcard card = new Flashcard();
        card.setId(7L);
        card.setDeck(deck);
        card.setFrontImageFilename("old-front.png");
        card.setFrontImageSizeBytes(120L);

        MultipartFile replacement = new MockMultipartFile("image", "new-front.png", "image/png", new byte[300]);

        when(flashcardRepository.findByIdAndDeckUserUsername(7L, "alice")).thenReturn(Optional.of(card));
        when(fileStorageService.store(replacement)).thenReturn("new-front.png");
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        flashcardService.replaceImage(7L, "front", replacement, user);

        verify(storageQuotaService, times(1)).assertWithinQuota(user, 120L, 300L);
        verify(fileStorageService, times(1)).delete("old-front.png");
        assertThat(card.getFrontImageFilename()).isEqualTo("new-front.png");
        assertThat(card.getFrontImageSizeBytes()).isEqualTo(300L);
    }
}
