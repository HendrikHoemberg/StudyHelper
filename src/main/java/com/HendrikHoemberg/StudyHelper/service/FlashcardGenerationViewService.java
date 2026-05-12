package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.FlashcardPdfOption;
import com.HendrikHoemberg.StudyHelper.dto.FolderOption;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import com.HendrikHoemberg.StudyHelper.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class FlashcardGenerationViewService {

    private final FileEntryRepository fileEntryRepository;
    private final FolderRepository folderRepository;

    public FlashcardGenerationViewService(FileEntryRepository fileEntryRepository,
                                          FolderRepository folderRepository) {
        this.fileEntryRepository = fileEntryRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional(readOnly = true)
    public List<FlashcardPdfOption> getPdfOptions(User user) {
        return fileEntryRepository.findByUser(user).stream()
            .filter(this::isSupportedPdf)
            .map(file -> new FlashcardPdfOption(
                file.getId(),
                file.getOriginalFilename(),
                buildFolderPath(file.getFolder()),
                colorOf(file.getFolder()),
                file.getFileSizeBytes()
            ))
            .sorted(Comparator.comparing(FlashcardPdfOption::filename, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FolderOption> getFolderOptions(User user) {
        List<FolderOption> options = new ArrayList<>();
        for (Folder root : folderRepository.findByUserAndParentFolderIsNull(user)) {
            collectFolderOptions(root, options);
        }
        return options;
    }

    private void collectFolderOptions(Folder folder, List<FolderOption> options) {
        options.add(new FolderOption(folder.getId(), buildFolderPath(folder), colorOf(folder)));
        for (Folder child : folder.getSubFolders()) {
            collectFolderOptions(child, options);
        }
    }

    private boolean isSupportedPdf(FileEntry file) {
        String filename = file.getOriginalFilename();
        boolean pdf = filename != null && filename.toLowerCase().endsWith(".pdf");
        return pdf && file.getFileSizeBytes() != null
            && file.getFileSizeBytes() <= DocumentExtractionService.MAX_FILE_SIZE_BYTES;
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

    private String colorOf(Folder folder) {
        return folder.getColorHex() != null && !folder.getColorHex().isBlank()
            ? folder.getColorHex()
            : "#6366f1";
    }
}
