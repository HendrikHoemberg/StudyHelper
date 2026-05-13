package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.FileEntryRepository;
import com.HendrikHoemberg.StudyHelper.repository.FlashcardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageQuotaServiceTests {

    private FileEntryRepository fileEntryRepository;
    private FlashcardRepository flashcardRepository;
    private StorageQuotaService storageQuotaService;

    @BeforeEach
    void setUp() {
        fileEntryRepository = mock(FileEntryRepository.class);
        flashcardRepository = mock(FlashcardRepository.class);
        storageQuotaService = new StorageQuotaService(fileEntryRepository, flashcardRepository);
    }

    @Test
    void usedBytes_SumsFileAndFlashcardImageBytes() {
        User user = userWithQuota(1_000L);
        when(fileEntryRepository.sumFileSizeBytesByUser(user)).thenReturn(300L);
        when(flashcardRepository.sumImageSizeBytesByUser(user)).thenReturn(250L);

        long usedBytes = storageQuotaService.usedBytes(user);

        assertThat(usedBytes).isEqualTo(550L);
    }

    @Test
    void assertWithinQuota_AllowsReplacementWhenNetUsageDecreases() {
        User user = userWithQuota(1_000L);
        when(fileEntryRepository.sumFileSizeBytesByUser(user)).thenReturn(700L);
        when(flashcardRepository.sumImageSizeBytesByUser(user)).thenReturn(200L);

        assertThatCode(() -> storageQuotaService.assertWithinQuota(user, 250L, 120L))
            .doesNotThrowAnyException();
    }

    @Test
    void assertWithinQuota_RejectsWhenProjectedUsageExceedsQuota() {
        User user = userWithQuota(1_000L);
        when(fileEntryRepository.sumFileSizeBytesByUser(user)).thenReturn(800L);
        when(flashcardRepository.sumImageSizeBytesByUser(user)).thenReturn(150L);

        assertThatThrownBy(() -> storageQuotaService.assertWithinQuota(user, 0L, 100L))
            .isInstanceOf(StorageQuotaExceededException.class)
            .hasMessageContaining("Storage quota exceeded");
    }

    private User userWithQuota(long quotaBytes) {
        User user = new User();
        user.setUsername("alice");
        user.setStorageQuotaBytes(quotaBytes);
        return user;
    }
}
