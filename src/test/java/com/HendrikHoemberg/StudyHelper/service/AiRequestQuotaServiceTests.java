package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.AiRequestUsage;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.AiRequestUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRequestQuotaServiceTests {

    private AiRequestUsageRepository aiRequestUsageRepository;
    private AiRequestQuotaService aiRequestQuotaService;
    private Clock fixedClock;
    private User user;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        aiRequestUsageRepository = mock(AiRequestUsageRepository.class);
        fixedClock = Clock.fixed(Instant.parse("2026-05-13T10:00:00Z"), ZoneOffset.UTC);
        aiRequestQuotaService = new AiRequestQuotaService(aiRequestUsageRepository, fixedClock);
        today = LocalDate.now(fixedClock);

        user = new User();
        user.setUsername("alice");
        user.setDailyAiRequestLimit(2);
    }

    @Test
    void todayUsed_ReturnsZeroWhenNoUsageRowExists() {
        when(aiRequestUsageRepository.findByUserAndUsageDate(user, today)).thenReturn(Optional.empty());

        assertThat(aiRequestQuotaService.todayUsed(user)).isZero();
    }

    @Test
    void todayUsed_UsesGeminiPacificResetDay() {
        Clock geminiResetClock = Clock.fixed(
            Instant.parse("2026-05-13T06:30:00Z"),
            ZoneId.of("America/Los_Angeles")
        );
        aiRequestQuotaService = new AiRequestQuotaService(aiRequestUsageRepository, geminiResetClock);
        LocalDate geminiUsageDate = LocalDate.of(2026, 5, 12);
        when(aiRequestUsageRepository.findByUserAndUsageDate(user, geminiUsageDate)).thenReturn(Optional.empty());

        assertThat(aiRequestQuotaService.todayUsed(user)).isZero();
        verify(aiRequestUsageRepository).findByUserAndUsageDate(user, geminiUsageDate);
    }

    @Test
    void assertHasQuota_ThrowsWhenUsedReachedLimit() {
        AiRequestUsage usage = new AiRequestUsage();
        usage.setUser(user);
        usage.setUsageDate(today);
        usage.setRequestCount(2);
        when(aiRequestUsageRepository.findByUserAndUsageDate(user, today)).thenReturn(Optional.of(usage));

        assertThatThrownBy(() -> aiRequestQuotaService.assertHasQuota(user))
            .isInstanceOf(AiQuotaExceededException.class)
            .hasMessage("Daily AI request limit reached.");
    }

    @Test
    void recordRequest_IncrementsExistingRow() {
        AiRequestUsage usage = new AiRequestUsage();
        usage.setUser(user);
        usage.setUsageDate(today);
        usage.setRequestCount(1);
        when(aiRequestUsageRepository.findByUserAndUsageDate(user, today)).thenReturn(Optional.of(usage));

        aiRequestQuotaService.recordRequest(user);

        assertThat(usage.getRequestCount()).isEqualTo(2);
        verify(aiRequestUsageRepository, times(1)).save(usage);
    }

    @Test
    void checkAndRecord_WhenQuotaAvailable_RecordsOneRequest() {
        AiRequestUsage usage = new AiRequestUsage();
        usage.setUser(user);
        usage.setUsageDate(today);
        usage.setRequestCount(1);
        when(aiRequestUsageRepository.findByUserAndUsageDateForUpdate(user, today)).thenReturn(Optional.of(usage));
        when(aiRequestUsageRepository.save(usage)).thenReturn(usage);

        assertThatCode(() -> aiRequestQuotaService.checkAndRecord(user)).doesNotThrowAnyException();

        assertThat(usage.getRequestCount()).isEqualTo(2);
        verify(aiRequestUsageRepository, times(1)).save(usage);
    }

    @Test
    void aiRequestUsage_HasUniqueConstraintOnUserAndDate() {
        Table table = AiRequestUsage.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        UniqueConstraint[] constraints = table.uniqueConstraints();
        assertThat(constraints).isNotEmpty();
        assertThat(constraints[0].columnNames()).containsExactlyInAnyOrder("user_id", "usage_date");
    }

    @Test
    void checkAndRecord_WhenAtLimit_ThrowsAndDoesNotSave() {
        AiRequestUsage usage = new AiRequestUsage();
        usage.setUser(user);
        usage.setUsageDate(today);
        usage.setRequestCount(2);
        when(aiRequestUsageRepository.findByUserAndUsageDateForUpdate(user, today)).thenReturn(Optional.of(usage));

        assertThatThrownBy(() -> aiRequestQuotaService.checkAndRecord(user))
            .isInstanceOf(AiQuotaExceededException.class)
            .hasMessage("Daily AI request limit reached.");

        verify(aiRequestUsageRepository, never()).save(any(AiRequestUsage.class));
    }

    @Test
    void checkAndRecord_UsesLockedLookupPath() {
        AiRequestUsage usage = new AiRequestUsage();
        usage.setUser(user);
        usage.setUsageDate(today);
        usage.setRequestCount(0);
        when(aiRequestUsageRepository.findByUserAndUsageDateForUpdate(user, today)).thenReturn(Optional.of(usage));
        when(aiRequestUsageRepository.save(usage)).thenReturn(usage);

        aiRequestQuotaService.checkAndRecord(user);

        verify(aiRequestUsageRepository, times(1)).findByUserAndUsageDateForUpdate(user, today);
    }
}
