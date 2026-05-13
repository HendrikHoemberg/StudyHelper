package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.FileSummary;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class FileEntryService {

    private final FileEntryRepository fileEntryRepository;
    private final FolderRepository folderRepository;
    private final FileStorageService fileStorageService;
    private final StorageQuotaService storageQuotaService;

    public FileEntryService(FileEntryRepository fileEntryRepository,
                            FolderRepository folderRepository,
                            FileStorageService fileStorageService,
                            StorageQuotaService storageQuotaService) {
        this.fileEntryRepository = fileEntryRepository;
        this.folderRepository = folderRepository;
        this.fileStorageService = fileStorageService;
        this.storageQuotaService = storageQuotaService;
    }

    @Transactional
    public FileEntry upload(MultipartFile file, Long folderId, User user) throws IOException {
        Folder folder = folderRepository.findByIdAndUser(folderId, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));

        storageQuotaService.assertWithinQuota(user, 0L, file.getSize());
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
    public FileEntry getByIdAndUser(Long id, User user) {
        return fileEntryRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("File not found"));
    }

    @Transactional(readOnly = true)
    public FileEntry getFile(Long id, User user) {
        FileEntry entry = fileEntryRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("File not found"));
        // Initialize folder proxy within transaction so folderId is available outside
        entry.getFolder().getId();
        return entry;
    }

    @Transactional(readOnly = true)
    public List<FileSummary> getFileSummaries(User user) {
        List<FileEntry> entries = fileEntryRepository.findByUser(user);
        List<FileSummary> summaries = new ArrayList<>(entries.size());
        for (FileEntry entry : entries) {
            Folder folder = entry.getFolder();
            String folderPath = buildFolderPath(folder);
            summaries.add(new FileSummary(
                entry.getId(),
                entry.getOriginalFilename(),
                folder.getId(),
                folderPath,
                folder.getColorHex(),
                entry.getMimeType(),
                entry.getFileSizeBytes(),
                entry.getUploadedAt()
            ));
        }
        summaries.sort(Comparator.comparing(FileSummary::uploadedAt).reversed());
        return summaries;
    }

    @Transactional
    public Long replaceContents(Long id, MultipartFile image, User user) throws IOException {
        FileEntry entry = fileEntryRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("File not found"));

        long existingBytes = entry.getFileSizeBytes() == null ? 0L : entry.getFileSizeBytes();
        storageQuotaService.assertWithinQuota(user, existingBytes, image.getSize());
        fileStorageService.replaceContents(entry.getStoredFilename(), image);
        entry.setMimeType(image.getContentType() != null ? image.getContentType() : "application/octet-stream");
        entry.setFileSizeBytes(image.getSize());

        fileEntryRepository.save(entry);
        return entry.getFolder().getId();
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

    @Transactional
    public FileEntry renameFile(Long id, String newName, User user) {
        FileEntry entry = fileEntryRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("File not found"));
        entry.setOriginalFilename(newName);
        return fileEntryRepository.save(entry);
    }

    private String buildFolderPath(Folder folder) {
        List<String> segments = new ArrayList<>();
        Folder current = folder;
        while (current != null) {
            segments.add(0, current.getName());
            current = current.getParentFolder();
        }
        return String.join(" / ", segments);
    }
}
