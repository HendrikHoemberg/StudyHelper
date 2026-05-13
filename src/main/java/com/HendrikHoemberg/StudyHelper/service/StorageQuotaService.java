package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageQuotaService {

    private final FileEntryRepository fileEntryRepository;
    private final FlashcardRepository flashcardRepository;

    public StorageQuotaService(FileEntryRepository fileEntryRepository, FlashcardRepository flashcardRepository) {
        this.fileEntryRepository = fileEntryRepository;
        this.flashcardRepository = flashcardRepository;
    }

    @Transactional(readOnly = true)
    public long usedBytes(User user) {
        long fileBytes = fileEntryRepository.sumFileSizeBytesByUser(user);
        long flashcardImageBytes = flashcardRepository.sumImageSizeBytesByUser(user);
        return fileBytes + flashcardImageBytes;
    }

    @Transactional(readOnly = true)
    public void assertWithinQuota(User user, long bytesRemoved, long bytesAdded) {
        long safeRemoved = Math.max(0L, bytesRemoved);
        long safeAdded = Math.max(0L, bytesAdded);
        long currentUsed = usedBytes(user);
        long projected = currentUsed - safeRemoved + safeAdded;
        long quota = user.getStorageQuotaBytes();
        if (projected > quota) {
            throw new StorageQuotaExceededException(
                "Storage quota exceeded. Used " + currentUsed + " of " + quota
                    + " bytes, requested +" + safeAdded + " bytes (replacing -" + safeRemoved + ")."
            );
        }
    }
}
