package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
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

    @Transactional(readOnly = true)
    public List<int[]> getRootFolderCardCounts(User user) {
        List<Folder> folders = folderRepository.findByUserAndParentFolderIsNull(user);
        return folders.stream()
            .map(f -> new int[]{getAggregatedCardCount(f)})
            .toList();
    }

    @Transactional(readOnly = true)
    public Folder getFolder(Long id, User user) {
        return folderRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));
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

    @Transactional
    public Folder updateFolderColor(Long id, String colorHex, User user) {
        Folder folder = folderRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));
        folder.setColorHex(colorHex);
        return folderRepository.save(folder);
    }

    @Transactional(readOnly = true)
    public FolderView getFolderView(Long id, User user) {
        Folder folder = folderRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));

        List<Folder> subFolders = new ArrayList<>(folder.getSubFolders());
        List<Deck> decks = new ArrayList<>(folder.getDecks());
        // Initialize flashcard collections so they're accessible outside transaction
        decks.forEach(d -> d.getFlashcards().size());
        List<FileEntry> files = new ArrayList<>(folder.getFiles());
        List<Folder> breadcrumb = buildBreadcrumb(folder);
        int totalCardCount = getAggregatedCardCount(folder);

        return new FolderView(folder, subFolders, decks, files, breadcrumb, totalCardCount);
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

    /**
     * Recursively counts all flashcards across all decks in a folder
     * and all its descendant subfolders.
     */
    @Transactional(readOnly = true)
    public int getAggregatedCardCount(Folder folder) {
        int count = 0;
        for (Deck deck : folder.getDecks()) {
            count += deck.getFlashcards().size();
        }
        for (Folder sub : folder.getSubFolders()) {
            count += getAggregatedCardCount(sub);
        }
        return count;
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
