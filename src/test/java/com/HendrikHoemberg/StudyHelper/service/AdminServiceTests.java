package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.ExamRepository;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceTests {

    private UserRepository userRepository;
    private FileEntryRepository fileEntryRepository;
    private FlashcardRepository flashcardRepository;
    private ExamRepository examRepository;
    private StorageQuotaService storageQuotaService;
    private AiRequestQuotaService aiRequestQuotaService;
    private FileStorageService fileStorageService;
    private EntityManager entityManager;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        fileEntryRepository = mock(FileEntryRepository.class);
        flashcardRepository = mock(FlashcardRepository.class);
        examRepository = mock(ExamRepository.class);
        storageQuotaService = mock(StorageQuotaService.class);
        aiRequestQuotaService = mock(AiRequestQuotaService.class);
        fileStorageService = mock(FileStorageService.class);
        entityManager = mock(EntityManager.class);
        adminService = new AdminService(
            userRepository,
            fileEntryRepository,
            flashcardRepository,
            examRepository,
            storageQuotaService,
            aiRequestQuotaService,
            fileStorageService,
            entityManager
        );
    }

    @Test
    void updateQuotas_UpdatesTargetUserValues() {
        User target = new User();
        target.setId(5L);
        target.setStorageQuotaBytes(10L);
        target.setDailyAiRequestLimit(1);
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        adminService.updateQuotas(5L, 100L, 7);

        assertThat(target.getStorageQuotaBytes()).isEqualTo(100L);
        assertThat(target.getDailyAiRequestLimit()).isEqualTo(7);
        verify(userRepository, times(1)).save(target);
    }

    @Test
    void disableUser_RejectsSelfDisable() {
        User actingAdmin = new User();
        actingAdmin.setId(1L);
        User target = new User();
        target.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> adminService.disableUser(1L, actingAdmin))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("You cannot disable your own account.");

        verify(userRepository, never()).save(target);
    }

    @Test
    void hardDeleteDisabledUser_DeletesStorageFilesAndDataRows() throws Exception {
        User actingAdmin = new User();
        actingAdmin.setId(1L);
        User target = new User();
        target.setId(2L);
        target.setEnabled(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        when(fileEntryRepository.findStoredFilenamesByUser(target)).thenReturn(List.of("file-a.pdf"));
        when(flashcardRepository.findFrontImageFilenamesByUser(target)).thenReturn(List.of("front.png"));
        when(flashcardRepository.findBackImageFilenamesByUser(target)).thenReturn(List.of("back.png"));

        Query usageDeleteQuery = mock(Query.class);
        Query codeDeleteQuery = mock(Query.class);
        when(entityManager.createQuery("delete from AiRequestUsage a where a.user = :user")).thenReturn(usageDeleteQuery);
        when(entityManager.createQuery("delete from RegistrationCode r where r.createdBy = :user or r.usedBy = :user")).thenReturn(codeDeleteQuery);
        when(usageDeleteQuery.setParameter("user", target)).thenReturn(usageDeleteQuery);
        when(codeDeleteQuery.setParameter("user", target)).thenReturn(codeDeleteQuery);

        adminService.hardDeleteDisabledUser(2L, actingAdmin);

        verify(fileStorageService).delete("file-a.pdf");
        verify(fileStorageService).delete("front.png");
        verify(fileStorageService).delete("back.png");
        verify(examRepository).deleteAllByUser(target);
        verify(usageDeleteQuery).executeUpdate();
        verify(codeDeleteQuery).executeUpdate();
        verify(userRepository).delete(target);
    }

    @Test
    void hardDeleteDisabledUser_RejectsEnabledUser() {
        User actingAdmin = new User();
        actingAdmin.setId(1L);
        User target = new User();
        target.setId(2L);
        target.setEnabled(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> adminService.hardDeleteDisabledUser(2L, actingAdmin))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only disabled users can be deleted.");

        verify(userRepository, never()).delete(target);
    }
}
