package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.SidebarFolderNode;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckGroup;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckOption;
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
    private final FlashcardService flashcardService;

    public FolderService(FolderRepository folderRepository, FileStorageService fileStorageService,
                         FlashcardService flashcardService) {
        this.folderRepository = folderRepository;
        this.fileStorageService = fileStorageService;
        this.flashcardService = flashcardService;
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
    public List<SidebarFolderNode> getSidebarTree(User user, Long activeFolderId) {
        List<Folder> roots = folderRepository.findByUserAndParentFolderIsNull(user);
        List<SidebarFolderNode> nodes = new ArrayList<>(roots.size());
        for (Folder root : roots) {
            List<SidebarFolderNode> childNodes = new ArrayList<>();
            int subDeckTotal = 0;
            boolean rootIsActive = root.getId().equals(activeFolderId);
            boolean rootIsOpen = rootIsActive;

            for (Folder sub : root.getSubFolders()) {
                int subDecks = sub.getDecks().size();
                subDeckTotal += subDecks;
                boolean subIsActive = sub.getId().equals(activeFolderId);
                if (subIsActive) {
                    rootIsOpen = true;
                }
                childNodes.add(new SidebarFolderNode(
                    sub.getId(), sub.getName(), sub.getColorHex(),
                    iconOf(sub), subDecks, List.of(), subIsActive, false
                ));
            }
            int rootTotal = root.getDecks().size() + subDeckTotal;
            nodes.add(new SidebarFolderNode(
                root.getId(), root.getName(), root.getColorHex(),
                iconOf(root), rootTotal, childNodes, rootIsActive, rootIsOpen
            ));
        }
        return nodes;
    }

    @Transactional(readOnly = true)
    public List<SidebarFolderNode> getSidebarTree(User user) {
        return getSidebarTree(user, null);
    }

    @Transactional(readOnly = true)
    public List<StudyDeckGroup> getStudyFolderTree(User user, List<Long> selectedDeckIds) {
        List<Folder> roots = folderRepository.findByUserAndParentFolderIsNull(user);
        return roots.stream()
            .map(f -> toStudyDeckGroup(f, selectedDeckIds))
            .filter(g -> !g.decks().isEmpty() || !g.subGroups().isEmpty())
            .toList();
    }

    @Transactional(readOnly = true)
    public List<StudyDeckGroup> getStudyFolderTree(User user) {
        return getStudyFolderTree(user, List.of());
    }

    @Transactional(readOnly = true)
    public List<StudyDeckOption> getAllDecksInFolder(Long folderId, User user) {
        Folder folder = folderRepository.findByIdAndUser(folderId, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));
        List<StudyDeckOption> options = new ArrayList<>();
        collectDecksRecursively(folder, options);
        return options;
    }

    private void collectDecksRecursively(Folder folder, List<StudyDeckOption> options) {
        String path = buildFolderPathString(folder);
        for (Deck deck : folder.getDecks()) {
            int usable = (int) deck.getFlashcards().stream()
                .filter(flashcardService::hasUsableTextForAi).count();
            options.add(new StudyDeckOption(
                deck.getId(), deck.getName(), folder.getId(), path, folder.getColorHex(),
                deck.getFlashcards().size(), usable
            ));
        }
        for (Folder sub : folder.getSubFolders()) {
            collectDecksRecursively(sub, options);
        }
    }

    private StudyDeckGroup toStudyDeckGroup(Folder folder, List<Long> selectedDeckIds) {
        List<StudyDeckOption> decks = folder.getDecks().stream()
            .map(deck -> {
                int total = deck.getFlashcards().size();
                int usable = (int) deck.getFlashcards().stream()
                    .filter(flashcardService::hasUsableTextForAi).count();
                return new StudyDeckOption(
                    deck.getId(),
                    deck.getName(),
                    folder.getId(),
                    buildFolderPathString(folder),
                    folder.getColorHex(),
                    total,
                    usable
                );
            })
            .toList();

        List<StudyDeckGroup> subGroups = folder.getSubFolders().stream()
            .map(f -> toStudyDeckGroup(f, selectedDeckIds))
            .filter(g -> !g.decks().isEmpty() || !g.subGroups().isEmpty())
            .toList();

        int totalDeckCount = decks.size() + subGroups.stream().mapToInt(StudyDeckGroup::totalDeckCount).sum();
        int selectableDeckCount = (int) decks.stream().filter(d -> d.cardCount() > 0).count()
            + subGroups.stream().mapToInt(StudyDeckGroup::selectableDeckCount).sum();
        int totalCardCount = decks.stream().mapToInt(StudyDeckOption::cardCount).sum()
            + subGroups.stream().mapToInt(StudyDeckGroup::totalCardCount).sum();

        boolean allSelected = true;
        boolean someSelected = false;

        for (StudyDeckOption deck : decks) {
            if (selectedDeckIds.contains(deck.deckId())) {
                someSelected = true;
            } else {
                allSelected = false;
            }
        }

        for (StudyDeckGroup sub : subGroups) {
            if (sub.isSelected()) {
                someSelected = true;
            } else if (sub.isIndeterminate()) {
                someSelected = true;
                allSelected = false;
            } else {
                allSelected = false;
            }
        }

        if (decks.isEmpty() && subGroups.isEmpty()) {
            allSelected = false;
        }

        boolean isSelected = allSelected && someSelected;
        boolean isIndeterminate = !allSelected && someSelected;

        String color = folder.getColorHex() != null && !folder.getColorHex().isBlank()
            ? folder.getColorHex() : "#6366f1";

        return new StudyDeckGroup(
            folder.getId(),
            folder.getName(),
            buildFolderPathString(folder),
            color,
            iconOf(folder),
            totalDeckCount,
            selectableDeckCount,
            totalCardCount,
            decks,
            subGroups,
            isSelected,
            isIndeterminate
        );
    }

    public String buildFolderPathString(Folder folder) {
        List<String> segments = new ArrayList<>();
        Folder current = folder;
        while (current != null) {
            segments.add(0, current.getName());
            current = current.getParentFolder();
        }
        return String.join(" / ", segments);
    }

    @Transactional(readOnly = true)
    public Folder getFolder(Long id, User user) {
        return folderRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));
    }

    @Transactional
    public Folder createFolder(String name, String colorHex, String iconName, Long parentId, User user) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setColorHex(colorHex != null && !colorHex.isBlank() ? colorHex : "#6366f1");
        folder.setIconName(iconName != null && !iconName.isBlank() ? iconName : "folder");
        folder.setUser(user);
        if (parentId != null) {
            Folder parent = folderRepository.findByIdAndUser(parentId, user)
                .orElseThrow(() -> new NoSuchElementException("Parent folder not found"));
            if (parent.getParentFolder() != null) {
                throw new IllegalArgumentException("Subfolders cannot contain further subfolders");
            }
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

    @Transactional
    public Folder updateFolder(Long id, String name, String colorHex, String iconName, User user) {
        Folder folder = folderRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));
        if (name != null && !name.isBlank()) folder.setName(name);
        if (colorHex != null && !colorHex.isBlank()) folder.setColorHex(colorHex);
        folder.setIconName(iconName != null && !iconName.isBlank() ? iconName : "folder");
        return folderRepository.save(folder);
    }

    private String iconOf(Folder folder) {
        return folder.getIconName() != null && !folder.getIconName().isBlank()
            ? folder.getIconName() : "folder";
    }

    @Transactional(readOnly = true)
    public FolderView getFolderView(Long id, User user) {
        return getFolderView(id, user, null, "asc");
    }

    @Transactional(readOnly = true)
    public FolderView getFolderView(Long id, User user, String sortBy, String direction) {
        Folder folder = folderRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NoSuchElementException("Folder not found"));

        List<Folder> subFolders = new ArrayList<>(folder.getSubFolders());
        List<Deck> decks = new ArrayList<>(folder.getDecks());
        decks.forEach(d -> d.getFlashcards().size());
        
        List<FileEntry> files = new ArrayList<>(folder.getFiles());
        if (sortBy != null) {
            sortFiles(files, sortBy, "desc".equalsIgnoreCase(direction));
        }

        List<Folder> breadcrumb = buildBreadcrumb(folder);
        int totalCardCount = getAggregatedCardCount(folder);

        return new FolderView(folder, subFolders, decks, files, breadcrumb, totalCardCount);
    }

    private void sortFiles(List<FileEntry> files, String sortBy, boolean desc) {
        files.sort((a, b) -> {
            int cmp = 0;
            switch (sortBy) {
                case "name" -> cmp = a.getOriginalFilename().compareToIgnoreCase(b.getOriginalFilename());
                case "type" -> cmp = a.getMimeType().compareToIgnoreCase(b.getMimeType());
                case "size" -> cmp = Long.compare(a.getFileSizeBytes(), b.getFileSizeBytes());
                case "date" -> cmp = a.getUploadedAt().compareTo(b.getUploadedAt());
                default -> { return 0; }
            }
            return desc ? -cmp : cmp;
        });
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
