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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlashcardGenerationPersistenceServiceTests {

    private DeckRepository deckRepository;
    private FolderRepository folderRepository;
    private FlashcardRepository flashcardRepository;
    private FlashcardGenerationPersistenceService service;
    private User user;

    @BeforeEach
    void setUp() {
        deckRepository = mock(DeckRepository.class);
        folderRepository = mock(FolderRepository.class);
        flashcardRepository = mock(FlashcardRepository.class);
        service = new FlashcardGenerationPersistenceService(deckRepository, folderRepository, flashcardRepository);

        user = new User();
        user.setId(7L);
        user.setUsername("alice");
    }

    @Test
    void saveGeneratedCards_ExistingDeck_AppendsCardsToOwnedDeck() {
        Deck deck = new Deck();
        deck.setId(11L);
        deck.setName("Biology");
        deck.setUser(user);
        deck.setFlashcards(new ArrayList<>());

        when(deckRepository.findByIdAndUser(11L, user)).thenReturn(java.util.Optional.of(deck));
        when(flashcardRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Deck result = service.saveGeneratedCards(
            FlashcardGenerationDestination.EXISTING_DECK,
            11L,
            null,
            null,
            user,
            List.of(new GeneratedFlashcard("Front 1", "Back 1"), new GeneratedFlashcard("Front 2", "Back 2"))
        );

        assertThat(result).isSameAs(deck);
        assertThat(deck.getFlashcards()).hasSize(2);
        assertThat(deck.getFlashcards().get(0).getFrontText()).isEqualTo("Front 1");
        assertThat(deck.getFlashcards().get(0).getDeck()).isSameAs(deck);
        verify(deckRepository).findByIdAndUser(11L, user);
        verify(flashcardRepository).saveAll(any());
        verify(folderRepository, never()).findByIdAndUser(any(), any());
    }

    @Test
    void saveGeneratedCards_NewDeck_CreatesDeckInOwnedFolder() {
        Folder folder = new Folder();
        folder.setId(21L);
        folder.setName("Root");
        folder.setUser(user);

        Deck deck = new Deck();
        deck.setId(31L);
        deck.setName("New Deck");
        deck.setFolder(folder);
        deck.setUser(user);
        deck.setFlashcards(new ArrayList<>());

        when(folderRepository.findByIdAndUser(21L, user)).thenReturn(java.util.Optional.of(folder));
        when(deckRepository.save(any(Deck.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flashcardRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Deck result = service.saveGeneratedCards(
            FlashcardGenerationDestination.NEW_DECK,
            null,
            21L,
            "  New Deck  ",
            user,
            List.of(new GeneratedFlashcard("Front 1", "Back 1"))
        );

        assertThat(result.getId()).isNull();
        assertThat(result.getName()).isEqualTo("New Deck");
        assertThat(result.getFolder()).isSameAs(folder);
        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getFlashcards()).hasSize(1);
        assertThat(result.getFlashcards().get(0).getDeck()).isSameAs(result);
        verify(folderRepository).findByIdAndUser(21L, user);
        verify(deckRepository).save(any(Deck.class));
        verify(flashcardRepository).saveAll(any());
    }

    @Test
    void saveGeneratedCards_EmptyCards_DoesNotTouchDatabase() {
        assertThatThrownBy(() -> service.saveGeneratedCards(
            FlashcardGenerationDestination.EXISTING_DECK,
            11L,
            null,
            null,
            user,
            List.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("No generated flashcards to save.");

        verify(deckRepository, never()).findByIdAndUser(any(), any());
        verify(folderRepository, never()).findByIdAndUser(any(), any());
        verify(flashcardRepository, never()).saveAll(any());
    }

    @Test
    void saveGeneratedCards_NewDeck_BlankNameIsRejected() {
        assertThatThrownBy(() -> service.saveGeneratedCards(
            FlashcardGenerationDestination.NEW_DECK,
            null,
            21L,
            "   ",
            user,
            List.of(new GeneratedFlashcard("Front 1", "Back 1"))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Deck name");

        verify(folderRepository, never()).findByIdAndUser(any(), any());
        verify(deckRepository, never()).save(any());
        verify(flashcardRepository, never()).saveAll(any());
    }

    @Test
    void saveGeneratedCards_ExistingDeck_MissingDeckFailsOwnershipCheck() {
        when(deckRepository.findByIdAndUser(11L, user)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.saveGeneratedCards(
            FlashcardGenerationDestination.EXISTING_DECK,
            11L,
            null,
            null,
            user,
            List.of(new GeneratedFlashcard("Front 1", "Back 1"))
        ))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Deck not found");
    }

    @Test
    void saveGeneratedCards_DestinationMissingIsRejected() {
        assertThatThrownBy(() -> service.saveGeneratedCards(
            null,
            11L,
            21L,
            "Deck",
            user,
            List.of(new GeneratedFlashcard("Front 1", "Back 1"))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Destination");
    }

    @Test
    void saveGeneratedCards_ExistingDeckMissingDeckIdIsRejected() {
        assertThatThrownBy(() -> service.saveGeneratedCards(
            FlashcardGenerationDestination.EXISTING_DECK,
            null,
            null,
            null,
            user,
            List.of(new GeneratedFlashcard("Front 1", "Back 1"))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("deck id");
    }

    @Test
    void saveGeneratedCards_NewDeckMissingFolderIdIsRejected() {
        assertThatThrownBy(() -> service.saveGeneratedCards(
            FlashcardGenerationDestination.NEW_DECK,
            null,
            null,
            "Deck",
            user,
            List.of(new GeneratedFlashcard("Front 1", "Back 1"))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("folder id");
    }

}
