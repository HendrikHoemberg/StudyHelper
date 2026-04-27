package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileStorageService fileStorageService;

    public FolderService(FolderRepository folderRepository, FileStorageService fileStorageService) {
        this.folderRepository = folderRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<Folder> getRootFolders(User user) {
        return folderRepository.findByUserAndParentFolderIsNull(user);
    }

    @Transactional
    public Folder createFolder(String name, String colorHex, Long parentId, User user) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setColorHex(colorHex != null && !colorHex.isBlank() ? colorHex : "#6c757d");
        folder.setUser(user);
        if (parentId != null) {
            Folder parent = folderRepository.findByIdAndUser(parentId, user)
                .orElseThrow(() -> new NoSuchElementException("Parent folder not found"));
            folder.setParentFolder(parent);
        }
        return folderRepository.save(folder);
    }

    @Transactional(readOnly = true)
    public FolderView getFolderView(Long id, User user) {
        Folder folder = folderRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));

        List<Folder> subFolders = new ArrayList<>(folder.getSubFolders());
        List<FileEntry> files = new ArrayList<>(folder.getFiles());
        List<Folder> breadcrumb = buildBreadcrumb(folder);

        return new FolderView(folder, subFolders, files, breadcrumb);
    }

    @Transactional
    public void deleteFolder(Long id, User user) throws IOException {
        Folder folder = folderRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));

        List<String> storedFilenames = collectStoredFilenames(folder);
        folderRepository.delete(folder);

        for (String name : storedFilenames) {
            fileStorageService.delete(name);
        }
    }

    private List<Folder> buildBreadcrumb(Folder folder) {
        List<Folder> crumbs = new ArrayList<>();
        Folder current = folder;
        while (current != null) {
            crumbs.add(0, current);
            current = current.getParentFolder();
        }
        return crumbs;
    }

    private List<String> collectStoredFilenames(Folder folder) {
        List<String> names = new ArrayList<>();
        folder.getFiles().forEach(f -> names.add(f.getStoredFilename()));
        folder.getSubFolders().forEach(sub -> names.addAll(collectStoredFilenames(sub)));
        return names;
    }
}
