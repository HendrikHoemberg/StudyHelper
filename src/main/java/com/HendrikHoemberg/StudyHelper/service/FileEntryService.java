package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.NoSuchElementException;

@Service
public class FileEntryService {

    private final FileEntryRepository fileEntryRepository;
    private final FolderRepository folderRepository;
    private final FileStorageService fileStorageService;

    public FileEntryService(FileEntryRepository fileEntryRepository,
                            FolderRepository folderRepository,
                            FileStorageService fileStorageService) {
        this.fileEntryRepository = fileEntryRepository;
        this.folderRepository = folderRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public FileEntry upload(MultipartFile file, Long folderId, User user) throws IOException {
        Folder folder = folderRepository.findByIdAndUser(folderId, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));

        String storedFilename = fileStorageService.store(file);

        FileEntry entry = new FileEntry();
        entry.setOriginalFilename(file.getOriginalFilename());
        entry.setStoredFilename(storedFilename);
        entry.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        entry.setFileSizeBytes(file.getSize());
        entry.setFolder(folder);
        entry.setUser(user);

        return fileEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public FileEntry getFile(Long id, User user) {
        FileEntry entry = fileEntryRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("File not found"));
        // Initialize folder proxy within transaction so folderId is available outside
        entry.getFolder().getId();
        return entry;
    }

    @Transactional
    public Long deleteAndGetFolderId(Long id, User user) throws IOException {
        FileEntry entry = fileEntryRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("File not found"));
        Long folderId = entry.getFolder().getId();
        fileStorageService.delete(entry.getStoredFilename());
        fileEntryRepository.delete(entry);
        return folderId;
    }
}
