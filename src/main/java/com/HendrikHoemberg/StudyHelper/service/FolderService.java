package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.config.AppDefaults;
import com.HendrikHoemberg.StudyHelper.dto.FolderPickerNode;
import com.HendrikHoemberg.StudyHelper.dto.SidebarFolderNode;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckGroup;
import com.HendrikHoemberg.StudyHelper.dto.StudyDeckOption;
import com.HendrikHoemberg.StudyHelper.dto.QuizFileOption;
import com.HendrikHoemberg.StudyHelper.dto.QuizSourceGroup;
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
import com.HendrikHoemberg.StudyHelper.exception.ResourceNotFoundException;

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
        List<Folder> folders = folderRepository.findRootsWithSubFoldersByUser(user);
        return folders.stream()
            .map(f -> new int[]{getAggregatedCardCount(f)})
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SidebarFolderNode> getSidebarTree(User user, Long activeFolderId) {
        List<Folder> roots = folderRepository.findRootsWithSubFoldersByUser(user);
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
    public List<FolderPickerNode> getFolderPickerTree(User user) {
        List<Folder> roots = folderRepository.findRootsWithSubFoldersByUser(user);
        return roots.stream()
            .map(this::toFolderPickerNode)
            .toList();
    }

    private FolderPickerNode toFolderPickerNode(Folder folder) {
        List<FolderPickerNode> children = folder.getSubFolders().stream()
            .map(this::toFolderPickerNode)
            .toList();
        String color = colorOf(folder);
        return new FolderPickerNode(folder.getId(), folder.getName(), color, iconOf(folder), children);
    }

    @Transactional(readOnly = true)
    public List<StudyDeckGroup> getStudyFolderTree(User user, List<Long> selectedDeckIds) {
        List<Folder> roots = folderRepository.findRootsWithSubFoldersByUser(user);
        return roots.stream()
            .map(f -> toStudyDeckGroup(f, selectedDeckIds))
            .filter(g -> !g.decks().isEmpty() || !g.subGroups().isEmpty())
            .toList();
    }

    @Transactional(readOnly = true)
    public List<StudyDeckGroup> getStudyFolderTree(User user) {
        return getStudyFolderTree(user, List.of());
    }

    public record FolderSources(List<Long> deckIds, List<Long> fileIds) {}

    @Transactional(readOnly = true)
    public FolderSources getAllSourcesInFolder(Long folderId, User user) {
        Folder folder = folderRepository.findByIdAndUserWithSubFolders(folderId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        List<Long> deckIds = new ArrayList<>();
        List<Long> fileIds = new ArrayList<>();
        collectSourcesRecursively(folder, deckIds, fileIds);
        return new FolderSources(deckIds, fileIds);
    }

    private void collectSourcesRecursively(Folder folder, List<Long> deckIds, List<Long> fileIds) {
        for (Deck deck : folder.getDecks()) {
            int usable = (int) deck.getFlashcards().stream()
                .filter(flashcardService::hasUsableTextForAi).count();
            if (usable > 0) {
                deckIds.add(deck.getId());
            }
        }
        for (FileEntry file : folder.getFiles()) {
            String ext = DocumentExtractionService.extension(file.getOriginalFilename());
            boolean supported = DocumentExtractionService.SUPPORTED_EXTENSIONS.contains(ext)
                && file.getFileSizeBytes() <= DocumentExtractionService.MAX_FILE_SIZE_BYTES;
            if (supported) {
                fileIds.add(file.getId());
            }
        }
        for (Folder sub : folder.getSubFolders()) {
            collectSourcesRecursively(sub, deckIds, fileIds);
        }
    }

    @Transactional(readOnly = true)
    public List<StudyDeckOption> getAllDecksInFolder(Long folderId, User user) {
        Folder folder = folderRepository.findByIdAndUserWithSubFolders(folderId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        List<StudyDeckOption> options = new ArrayList<>();
        collectDecksRecursively(folder, options);
        return options;
    }

    @Transactional(readOnly = true)
    public List<QuizSourceGroup> getQuizSourceTree(User user, List<Long> selectedDeckIds, List<Long> selectedFileIds) {
        List<Folder> roots = folderRepository.findRootsWithSubFoldersByUser(user);
        return roots.stream()
            .map(f -> toQuizSourceGroup(f, selectedDeckIds, selectedFileIds))
            .filter(g -> !g.decks().isEmpty() || !g.files().isEmpty() || !g.subGroups().isEmpty())
            .toList();
    }

    private QuizSourceGroup toQuizSourceGroup(Folder folder, List<Long> selectedDeckIds, List<Long> selectedFileIds) {
        List<StudyDeckOption> decks = folder.getDecks().stream()
            .map(deck -> toDeckOption(folder, deck))
            .toList();

        List<QuizFileOption> files = folder.getFiles().stream()
            .filter(f -> {
                String ext = DocumentExtractionService.extension(f.getOriginalFilename());
                return DocumentExtractionService.SUPPORTED_EXTENSIONS.contains(ext);
            })
            .map(f -> {
                long size = f.getFileSizeBytes();
                boolean isSupported = size <= DocumentExtractionService.MAX_FILE_SIZE_BYTES; // 10MB
                return new QuizFileOption(
                    f.getId(),
                    f.getOriginalFilename(),
                    size,
                    DocumentExtractionService.extension(f.getOriginalFilename()),
                    isSupported
                );
            })
            .toList();

        List<QuizSourceGroup> subGroups = folder.getSubFolders().stream()
            .map(f -> toQuizSourceGroup(f, selectedDeckIds, selectedFileIds))
            .filter(g -> !g.decks().isEmpty() || !g.files().isEmpty() || !g.subGroups().isEmpty())
            .toList();

        int selectableSourceCount = (int) decks.stream().filter(d -> d.usableCardCount() > 0).count()
            + (int) files.stream().filter(QuizFileOption::isSupported).count()
            + subGroups.stream().mapToInt(QuizSourceGroup::selectableSourceCount).sum();

        int totalSourceCount = decks.size() + files.size()
            + subGroups.stream().mapToInt(QuizSourceGroup::totalSourceCount).sum();

        SelectionAccumulator selection = new SelectionAccumulator();
        for (StudyDeckOption deck : decks) {
            selection.leaf(selectedDeckIds.contains(deck.deckId()));
        }
        for (QuizFileOption file : files) {
            selection.leaf(selectedFileIds.contains(file.fileId()));
        }
        for (QuizSourceGroup sub : subGroups) {
            selection.group(sub.isSelected(), sub.isIndeterminate());
        }
        boolean isSelected = selection.isSelected();
        boolean isIndeterminate = selection.isIndeterminate();

        String color = colorOf(folder);

        return new QuizSourceGroup(
            folder.getId(),
            folder.getName(),
            buildFolderPathString(folder),
            color,
            iconOf(folder),
            totalSourceCount,
            selectableSourceCount,
            decks,
            files,
            subGroups,
            isSelected,
            isIndeterminate
        );
    }

    private void collectDecksRecursively(Folder folder, List<StudyDeckOption> options) {
        for (Deck deck : folder.getDecks()) {
            options.add(toDeckOption(folder, deck));
        }
        for (Folder sub : folder.getSubFolders()) {
            collectDecksRecursively(sub, options);
        }
    }

    /** Maps a deck to its picker option, counting total and AI-usable cards. */
    private StudyDeckOption toDeckOption(Folder folder, Deck deck) {
        int total = deck.getFlashcards().size();
        int usable = (int) deck.getFlashcards().stream()
            .filter(flashcardService::hasUsableTextForAi).count();
        return new StudyDeckOption(
            deck.getId(),
            deck.getName(),
            folder.getId(),
            buildFolderPathString(folder),
            folder.getColorHex(),
            deck.getColorHex(),
            deck.getIconName(),
            total,
            usable
        );
    }

    private StudyDeckGroup toStudyDeckGroup(Folder folder, List<Long> selectedDeckIds) {
        List<StudyDeckOption> decks = folder.getDecks().stream()
            .map(deck -> toDeckOption(folder, deck))
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

        SelectionAccumulator selection = new SelectionAccumulator();
        for (StudyDeckOption deck : decks) {
            selection.leaf(selectedDeckIds.contains(deck.deckId()));
        }
        for (StudyDeckGroup sub : subGroups) {
            selection.group(sub.isSelected(), sub.isIndeterminate());
        }
        boolean isSelected = selection.isSelected();
        boolean isIndeterminate = selection.isIndeterminate();

        String color = colorOf(folder);

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
            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
    }

    @Transactional
    public Folder createFolder(String name, String colorHex, String iconName, Long parentId, User user) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setColorHex(colorHex != null && !colorHex.isBlank() ? colorHex : AppDefaults.DEFAULT_COLOR_HEX);
        folder.setIconName(iconName != null && !iconName.isBlank() ? iconName : "folder");
        folder.setUser(user);
        if (parentId != null) {
            Folder parent = folderRepository.findByIdAndUser(parentId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found"));
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
            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        folder.setColorHex(colorHex);
        return folderRepository.save(folder);
    }

    @Transactional
    public Folder updateFolder(Long id, String name, String colorHex, String iconName, User user) {
        Folder folder = folderRepository.findByIdAndUser(id, user)
            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        if (name != null && !name.isBlank()) folder.setName(name);
        if (colorHex != null && !colorHex.isBlank()) folder.setColorHex(colorHex);
        folder.setIconName(iconName != null && !iconName.isBlank() ? iconName : "folder");
        return folderRepository.save(folder);
    }

    private String iconOf(Folder folder) {
        return folder.getIconName() != null && !folder.getIconName().isBlank()
            ? folder.getIconName() : "folder";
    }

    private String colorOf(Folder folder) {
        return folder.getColorHex() != null && !folder.getColorHex().isBlank()
            ? folder.getColorHex() : AppDefaults.DEFAULT_COLOR_HEX;
    }

    /**
     * Folds the selection state of a folder's children into the tri-state
     * (selected / indeterminate / unselected) used by the source-picker tree.
     * Shared by the study-deck and quiz-source tree builders.
     */
    private static final class SelectionAccumulator {
        private boolean allSelected = true;
        private boolean someSelected = false;
        private boolean empty = true;

        void leaf(boolean selected) {
            empty = false;
            if (selected) {
                someSelected = true;
            } else {
                allSelected = false;
            }
        }

        void group(boolean groupSelected, boolean groupIndeterminate) {
            empty = false;
            if (groupSelected) {
                someSelected = true;
            } else if (groupIndeterminate) {
                someSelected = true;
                allSelected = false;
            } else {
                allSelected = false;
            }
        }

        boolean isSelected() {
            return !empty && allSelected && someSelected;
        }

        boolean isIndeterminate() {
            return !allSelected && someSelected;
        }
    }

    @Transactional(readOnly = true)
    public FolderView getFolderView(Long id, User user) {
        return getFolderView(id, user, null, "asc");
    }

    @Transactional(readOnly = true)
    public FolderView getFolderView(Long id, User user, String sortBy, String direction) {
        Folder folder = folderRepository.findByIdAndUserWithSubFolders(id, user)
            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        List<Folder> subFolders = new ArrayList<>(folder.getSubFolders());
        subFolders.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        List<Deck> decks = new ArrayList<>(folder.getDecks());
        decks.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        // Materialize flashcards in one batch for the visible deck list.
        decks.forEach(d -> d.getFlashcards().size());
        
        List<FileEntry> files = new ArrayList<>(folder.getFiles());
        if (sortBy != null) {
            sortFiles(files, sortBy, "desc".equalsIgnoreCase(direction));
        } else {
            files.sort((a, b) -> b.getUploadedAt().compareTo(a.getUploadedAt()));
        }

        List<Folder> breadcrumb = buildBreadcrumb(folder);
        int totalCardCount = getAggregatedCardCount(folder);

        return new FolderView(folder, subFolders, decks, files, breadcrumb, totalCardCount);
    }

    @Transactional(readOnly = true)
    public FolderView getFolderView(Long id, User user, String sortBy, String direction, ActiveTab activeTab) {
        FolderView base = getFolderView(id, user, sortBy, direction);
        boolean isSubfolder = base.folder().getParentFolder() != null;
        if (isSubfolder && activeTab == ActiveTab.FOLDERS) {
            activeTab = ActiveTab.DECKS;
        }
        return new FolderView(
            base.folder(), base.subFolders(), base.decks(), base.files(),
            base.breadcrumb(), base.totalCardCount(), activeTab, isSubfolder
        );
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
        Folder folder = folderRepository.findByIdAndUserWithSubFolders(id, user)
            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

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
        folder.getDecks().forEach(deck -> deck.getFlashcards().forEach(card -> {
            if (card.getFrontImageFilename() != null) {
                names.add(card.getFrontImageFilename());
            }
            if (card.getBackImageFilename() != null) {
                names.add(card.getBackImageFilename());
            }
        }));
        folder.getSubFolders().forEach(sub -> names.addAll(collectStoredFilenames(sub)));
        return names;
    }
}
