package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.DeckRepository;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeckServiceTests {

    private DeckRepository deckRepository;
    private FolderRepository folderRepository;
    private DeckService deckService;
    private User user;
    private Folder folder;

    @BeforeEach
    void setUp() {
        deckRepository = mock(DeckRepository.class);
        folderRepository = mock(FolderRepository.class);
        deckService = new DeckService(deckRepository, folderRepository, mock(FlashcardService.class));

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        folder = new Folder();
        folder.setId(10L);
        folder.setUser(user);
        folder.setColorHex("#abcdef");
    }

    @Test
    void createDeck_DefaultsBlankColorToParentFolderColor() {
        when(folderRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(folder));
        when(deckRepository.save(any(Deck.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Deck created = deckService.createDeck("New Deck", " ", "", 10L, user);

        assertThat(created.getColorHex()).isEqualTo("#abcdef");
    }

    @Test
    void createDeck_DefaultsBlankColorToPrimaryTealWhenParentFolderHasNoColor() {
        folder.setColorHex(" ");
        when(folderRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(folder));
        when(deckRepository.save(any(Deck.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Deck created = deckService.createDeck("New Deck", "", "", 10L, user);

        assertThat(created.getColorHex()).isEqualTo("#0f766e");
    }
}
