package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.AdminUserSummary;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.ExamRepository;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import com.HendrikHoemberg.StudyHelper.exception.ResourceNotFoundException;
import java.util.Set;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final FileEntryRepository fileEntryRepository;
    private final FlashcardRepository flashcardRepository;
    private final ExamRepository examRepository;
    private final StorageQuotaService storageQuotaService;
    private final AiRequestQuotaService aiRequestQuotaService;
    private final FileStorageService fileStorageService;
    private final EntityManager entityManager;

    public AdminService(UserRepository userRepository,
                        FileEntryRepository fileEntryRepository,
                        FlashcardRepository flashcardRepository,
                        ExamRepository examRepository,
                        StorageQuotaService storageQuotaService,
                        AiRequestQuotaService aiRequestQuotaService,
                        FileStorageService fileStorageService,
                        EntityManager entityManager) {
        this.userRepository = userRepository;
        this.fileEntryRepository = fileEntryRepository;
        this.flashcardRepository = flashcardRepository;
        this.examRepository = examRepository;
        this.storageQuotaService = storageQuotaService;
        this.aiRequestQuotaService = aiRequestQuotaService;
        this.fileStorageService = fileStorageService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<AdminUserSummary> listUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(user -> new AdminUserSummary(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.isEnabled(),
                storageQuotaService.usedBytes(user),
                user.getStorageQuotaBytes(),
                aiRequestQuotaService.todayUsed(user),
                user.getDailyAiRequestLimit(),
                user.getCreatedAt()
            ))
            .toList();
    }

    @Transactional
    public void updateQuotas(Long userId, long storageQuotaBytes, int dailyAiLimit) {
        if (storageQuotaBytes < 0) {
            throw new IllegalArgumentException("Storage quota must be non-negative.");
        }
        if (dailyAiLimit < 0) {
            throw new IllegalArgumentException("Daily AI limit must be non-negative.");
        }

        User target = getUser(userId);
        target.setStorageQuotaBytes(storageQuotaBytes);
        target.setDailyAiRequestLimit(dailyAiLimit);
        userRepository.save(target);
    }

    @Transactional
    public void disableUser(Long targetId, User actingAdmin) {
        User target = getUser(targetId);
        if (target.getId().equals(actingAdmin.getId())) {
            throw new IllegalArgumentException("You cannot disable your own account.");
        }
        target.setEnabled(false);
        userRepository.save(target);
    }

    @Transactional
    public void enableUser(Long targetId) {
        User target = getUser(targetId);
        target.setEnabled(true);
        userRepository.save(target);
    }

    @Transactional
    public void hardDeleteDisabledUser(Long targetId, User actingAdmin) {
        User target = getUser(targetId);
        if (target.getId().equals(actingAdmin.getId())) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }
        if (target.isEnabled()) {
            throw new IllegalArgumentException("Only disabled users can be deleted.");
        }

        Set<String> storageFilenames = new LinkedHashSet<>();
        storageFilenames.addAll(fileEntryRepository.findStoredFilenamesByUser(target));
        storageFilenames.addAll(flashcardRepository.findFrontImageFilenamesByUser(target));
        storageFilenames.addAll(flashcardRepository.findBackImageFilenamesByUser(target));

        examRepository.deleteAllByUser(target);
        entityManager.createQuery("delete from AiRequestUsage a where a.user = :user")
            .setParameter("user", target)
            .executeUpdate();
        entityManager.createQuery("delete from RegistrationCode r where r.createdBy = :user or r.usedBy = :user")
            .setParameter("user", target)
            .executeUpdate();
        userRepository.delete(target);

        for (String filename : storageFilenames) {
            if (filename == null || filename.isBlank()) {
                continue;
            }
            try {
                fileStorageService.delete(filename);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to clean up storage file: " + filename, ex);
            }
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
