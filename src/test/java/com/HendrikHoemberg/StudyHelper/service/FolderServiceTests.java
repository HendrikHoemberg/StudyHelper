package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.QuizSourceGroup;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderServiceTests {

    private FolderRepository folderRepository;
    private FileStorageService fileStorageService;
    private FlashcardService flashcardService;
    private FolderService folderService;
    private User user;

    @BeforeEach
    void setUp() {
        folderRepository = mock(FolderRepository.class);
        fileStorageService = mock(FileStorageService.class);
        flashcardService = mock(FlashcardService.class);
        folderService = new FolderService(folderRepository, fileStorageService, flashcardService);

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
    }

    @Test
    void getQuizSourceTree_FiltersUnsupportedFilesAndCalculatesCounts() {
        Folder root = new Folder();
        root.setId(1L);
        root.setName("Root");
        root.setUser(user);

        Deck deck = new Deck();
        deck.setId(10L);
        deck.setName("Test Deck");
        deck.setFolder(root);
        deck.setFlashcards(new ArrayList<>());
        root.setDecks(List.of(deck));

        FileEntry pdf = new FileEntry();
        pdf.setId(101L);
        pdf.setOriginalFilename("test.pdf");
        pdf.setFileSizeBytes(1024L);
        pdf.setFolder(root);

        FileEntry png = new FileEntry();
        png.setId(102L);
        png.setOriginalFilename("image.png");
        png.setFileSizeBytes(1024L);
        png.setFolder(root);

        root.setFiles(List.of(pdf, png));
        root.setSubFolders(new ArrayList<>());

        when(folderRepository.findByUserAndParentFolderIsNull(user)).thenReturn(List.of(root));

        List<QuizSourceGroup> tree = folderService.getQuizSourceTree(user, List.of(), List.of());

        assertThat(tree).hasSize(1);
        QuizSourceGroup group = tree.get(0);
        assertThat(group.decks()).hasSize(1);
        assertThat(group.files()).hasSize(1);
        assertThat(group.files().get(0).filename()).isEqualTo("test.pdf");
        assertThat(group.totalSourceCount()).isEqualTo(2); // 1 deck + 1 pdf
    }

    @Test
    void deleteFolder_IncludesFlashcardFrontAndBackImageFilesInStorageDeletion() throws Exception {
        Folder folder = new Folder();
        folder.setId(1L);
        folder.setUser(user);
        folder.setSubFolders(List.of());

        FileEntry fileEntry = new FileEntry();
        fileEntry.setStoredFilename("uploaded.pdf");
        folder.setFiles(List.of(fileEntry));

        Flashcard flashcard = new Flashcard();
        flashcard.setFrontImageFilename("front-image.png");
        flashcard.setBackImageFilename("back-image.png");

        Deck deck = new Deck();
        deck.setFlashcards(List.of(flashcard));
        folder.setDecks(List.of(deck));

        when(folderRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(folder));

        folderService.deleteFolder(1L, user);

        verify(fileStorageService, times(1)).delete("uploaded.pdf");
        verify(fileStorageService, times(1)).delete("front-image.png");
        verify(fileStorageService, times(1)).delete("back-image.png");
    }
}
