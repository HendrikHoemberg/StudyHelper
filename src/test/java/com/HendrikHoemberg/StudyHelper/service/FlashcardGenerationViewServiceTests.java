package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlashcardGenerationViewServiceTests {

    private FileEntryRepository fileEntryRepository;
    private FolderRepository folderRepository;
    private FlashcardGenerationViewService service;
    private User user;
    private Folder root;
    private Folder child;

    @BeforeEach
    void setUp() {
        fileEntryRepository = mock(FileEntryRepository.class);
        folderRepository = mock(FolderRepository.class);
        service = new FlashcardGenerationViewService(fileEntryRepository, folderRepository);

        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        root = folder(10L, "Computer Science", "#123456", null);
        child = folder(11L, "Algorithms", "#abcdef", root);
        root.getSubFolders().add(child);
    }

    @Test
    void getPdfOptions_ReturnsOnlySupportedPdfsWithFolderPath() {
        FileEntry supportedPdf = file(1L, "Lecture.pdf", 1024L, child);
        FileEntry tooLargePdf = file(2L, "Huge.pdf", DocumentExtractionService.MAX_FILE_SIZE_BYTES + 1, child);
        FileEntry textFile = file(3L, "notes.md", 100L, child);
        when(fileEntryRepository.findByUser(user)).thenReturn(List.of(textFile, tooLargePdf, supportedPdf));

        var result = service.getPdfOptions(user);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).filename()).isEqualTo("Lecture.pdf");
        assertThat(result.get(0).folderId()).isEqualTo(11L);
        assertThat(result.get(0).folderPath()).isEqualTo("Computer Science / Algorithms");
        assertThat(result.get(0).folderColorHex()).isEqualTo("#abcdef");
    }

    @Test
    void getFolderOptions_ReturnsRootAndChildFoldersWithPaths() {
        when(folderRepository.findByUserAndParentFolderIsNull(user)).thenReturn(List.of(root));

        var result = service.getFolderOptions(user);

        assertThat(result).extracting("id").containsExactly(10L, 11L);
        assertThat(result).extracting("path").containsExactly("Computer Science", "Computer Science / Algorithms");
    }

    private Folder folder(Long id, String name, String color, Folder parent) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setColorHex(color);
        folder.setParentFolder(parent);
        folder.setUser(user);
        return folder;
    }

    private FileEntry file(Long id, String filename, Long size, Folder folder) {
        FileEntry entry = new FileEntry();
        entry.setId(id);
        entry.setOriginalFilename(filename);
        entry.setFileSizeBytes(size);
        entry.setFolder(folder);
        entry.setUser(user);
        return entry;
    }
}
