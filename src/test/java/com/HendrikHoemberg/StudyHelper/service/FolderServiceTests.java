package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.TestSourceGroup;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    void getTestSourceTree_FiltersUnsupportedFilesAndCalculatesCounts() {
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

        List<TestSourceGroup> tree = folderService.getTestSourceTree(user, List.of(), List.of());

        assertThat(tree).hasSize(1);
        TestSourceGroup group = tree.get(0);
        assertThat(group.decks()).hasSize(1);
        assertThat(group.files()).hasSize(1);
        assertThat(group.files().get(0).filename()).isEqualTo("test.pdf");
        assertThat(group.totalSourceCount()).isEqualTo(2); // 1 deck + 1 pdf
    }
}
