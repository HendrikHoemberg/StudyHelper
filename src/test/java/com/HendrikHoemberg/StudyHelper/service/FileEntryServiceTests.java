package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileEntryServiceTests {

    private FileEntryRepository fileEntryRepository;
    private FolderRepository folderRepository;
    private FileStorageService fileStorageService;
    private StorageQuotaService storageQuotaService;
    private FileEntryService fileEntryService;

    @BeforeEach
    void setUp() {
        fileEntryRepository = mock(FileEntryRepository.class);
        folderRepository = mock(FolderRepository.class);
        fileStorageService = mock(FileStorageService.class);
        storageQuotaService = mock(StorageQuotaService.class);
        fileEntryService = new FileEntryService(
            fileEntryRepository,
            folderRepository,
            fileStorageService,
            storageQuotaService
        );
    }

    @Test
    void upload_InvokesQuotaCheckWithAddedBytes() throws IOException {
        User user = new User();
        user.setUsername("alice");
        Folder folder = new Folder();
        folder.setId(1L);

        MultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[250]);

        when(folderRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(folder));
        when(fileStorageService.store(file)).thenReturn("stored-notes.pdf");
        when(fileEntryRepository.save(any(FileEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fileEntryService.upload(file, 1L, user);

        verify(storageQuotaService, times(1)).assertWithinQuota(user, 0L, 250L);
    }

    @Test
    void replaceContents_InvokesQuotaCheckWithReplacementDelta() throws IOException {
        User user = new User();
        user.setUsername("alice");
        Folder folder = new Folder();
        folder.setId(4L);

        FileEntry existing = new FileEntry();
        existing.setId(10L);
        existing.setUser(user);
        existing.setFolder(folder);
        existing.setStoredFilename("stored-old.png");
        existing.setFileSizeBytes(120L);

        MultipartFile replacement = new MockMultipartFile("image", "new.png", "image/png", new byte[300]);

        when(fileEntryRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(existing));

        fileEntryService.replaceContents(10L, replacement, user);

        verify(storageQuotaService, times(1)).assertWithinQuota(user, 120L, 300L);
    }
}
