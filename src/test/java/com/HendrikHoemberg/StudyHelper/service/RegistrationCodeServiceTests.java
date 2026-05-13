package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.RegistrationCode;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.RegistrationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class RegistrationCodeServiceTests {

    private RegistrationCodeRepository registrationCodeRepository;
    private RegistrationCodeService registrationCodeService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        registrationCodeRepository = mock(RegistrationCodeRepository.class);
        fixedClock = Clock.fixed(Instant.parse("2026-05-13T08:30:00Z"), ZoneOffset.UTC);
        registrationCodeService = new RegistrationCodeService(registrationCodeRepository, fixedClock);
    }

    @Test
    void generateCode_StoresHashOnlyAndSetsThreeDayExpiry() {
        when(registrationCodeRepository.save(any(RegistrationCode.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        User creator = new User();
        creator.setUsername("admin");

        String rawCode = registrationCodeService.generateCode(creator);

        assertThat(rawCode).isNotBlank();
        ArgumentCaptor<RegistrationCode> captor = ArgumentCaptor.forClass(RegistrationCode.class);
        verify(registrationCodeRepository, times(1)).save(captor.capture());
        RegistrationCode saved = captor.getValue();
        assertThat(saved.getCodeHash()).isNotBlank();
        assertThat(saved.getCodeHash()).isNotEqualTo(rawCode);
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.now(fixedClock));
        assertThat(saved.getExpiresAt()).isEqualTo(Instant.now(fixedClock).plus(RegistrationCodeService.EXPIRY_DURATION));
        assertThat(saved.getCreatedBy()).isSameAs(creator);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getUsedBy()).isNull();
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void consume_RejectsInvalidOrExpiredWithGenericMessage() {
        User newUser = new User();
        newUser.setUsername("new-user");

        when(registrationCodeRepository.findFirstByCodeHashAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationCodeService.consume("invalid", newUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RegistrationCodeService.INVALID_CODE_MESSAGE);

        RegistrationCode expired = new RegistrationCode();
        expired.setExpiresAt(Instant.now(fixedClock).minusSeconds(1));
        when(registrationCodeRepository.findFirstByCodeHashAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> registrationCodeService.consume("expired", newUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RegistrationCodeService.INVALID_CODE_MESSAGE);

        verify(registrationCodeRepository, never()).save(expired);
    }

    @Test
    void consume_MarksCodeAsUsed() {
        User newUser = new User();
        newUser.setUsername("new-user");
        RegistrationCode code = new RegistrationCode();
        code.setId(11L);
        code.setExpiresAt(Instant.now(fixedClock).plusSeconds(120));

        when(registrationCodeRepository.findFirstByCodeHashAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(code));
        when(registrationCodeRepository.markAsUsedIfConsumable(
            code.getId(),
            Instant.now(fixedClock),
            newUser,
            Instant.now(fixedClock)
        )).thenReturn(1);

        registrationCodeService.consume("ABC123", newUser);

        verify(registrationCodeRepository, times(1)).markAsUsedIfConsumable(
            code.getId(),
            Instant.now(fixedClock),
            newUser,
            Instant.now(fixedClock)
        );
    }

    @Test
    void consume_RejectsUsedCodeWithGenericMessage() {
        User newUser = new User();
        newUser.setUsername("new-user");
        RegistrationCode used = new RegistrationCode();
        used.setId(33L);
        used.setUsedAt(Instant.now(fixedClock).minusSeconds(5));
        used.setExpiresAt(Instant.now(fixedClock).plusSeconds(120));

        when(registrationCodeRepository.findFirstByCodeHashAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(used));
        when(registrationCodeRepository.markAsUsedIfConsumable(
            used.getId(),
            Instant.now(fixedClock),
            newUser,
            Instant.now(fixedClock)
        )).thenReturn(0);

        assertThatThrownBy(() -> registrationCodeService.consume("USEDCODE", newUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RegistrationCodeService.INVALID_CODE_MESSAGE);
    }

    @Test
    void consume_RejectsRevokedCodeWithGenericMessage() {
        User newUser = new User();
        newUser.setUsername("new-user");
        RegistrationCode revoked = new RegistrationCode();
        revoked.setId(44L);
        revoked.setRevokedAt(Instant.now(fixedClock).minusSeconds(5));
        revoked.setExpiresAt(Instant.now(fixedClock).plusSeconds(120));

        when(registrationCodeRepository.findFirstByCodeHashAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(revoked));
        when(registrationCodeRepository.markAsUsedIfConsumable(
            revoked.getId(),
            Instant.now(fixedClock),
            newUser,
            Instant.now(fixedClock)
        )).thenReturn(0);

        assertThatThrownBy(() -> registrationCodeService.consume("REVOKED", newUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RegistrationCodeService.INVALID_CODE_MESSAGE);
    }

    @Test
    void consume_WhenAtomicMarkAsUsedReturnsZero_FailsWithGenericInvalidMessage() {
        User newUser = new User();
        newUser.setUsername("new-user");
        RegistrationCode code = new RegistrationCode();
        code.setId(22L);
        code.setExpiresAt(Instant.now(fixedClock).plusSeconds(120));

        when(registrationCodeRepository.findFirstByCodeHashAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(code));
        when(registrationCodeRepository.markAsUsedIfConsumable(
            code.getId(),
            Instant.now(fixedClock),
            newUser,
            Instant.now(fixedClock)
        )).thenReturn(0);

        assertThatThrownBy(() -> registrationCodeService.consume("ABC123", newUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(RegistrationCodeService.INVALID_CODE_MESSAGE);
        verify(registrationCodeRepository, times(1)).markAsUsedIfConsumable(
            code.getId(),
            Instant.now(fixedClock),
            newUser,
            Instant.now(fixedClock)
        );
    }

    @Test
    void generateCode_RetriesOnUniqueConstraintCollision() {
        when(registrationCodeRepository.save(any(RegistrationCode.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate hash"))
            .thenAnswer(invocation -> invocation.getArgument(0));

        User creator = new User();
        creator.setUsername("admin");

        String rawCode = registrationCodeService.generateCode(creator);

        assertThat(rawCode).isNotBlank();
        verify(registrationCodeRepository, times(2)).save(any(RegistrationCode.class));
    }

    @Test
    void listActiveAndHistorySummaries_ClassifiesByRepositoryBuckets() {
        Instant now = Instant.now(fixedClock);

        User creator = new User();
        creator.setUsername("admin");

        RegistrationCode active = new RegistrationCode();
        active.setId(1L);
        active.setCreatedAt(now.minusSeconds(60));
        active.setExpiresAt(now.plusSeconds(3600));
        active.setCreatedBy(creator);

        RegistrationCode used = new RegistrationCode();
        used.setId(2L);
        used.setCreatedAt(now.minusSeconds(120));
        used.setExpiresAt(now.plusSeconds(300));
        used.setUsedAt(now.minusSeconds(10));
        used.setCreatedBy(creator);

        when(registrationCodeRepository.findByUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(now))
            .thenReturn(List.of(active));
        when(registrationCodeRepository.findByUsedAtIsNotNullOrRevokedAtIsNotNullOrExpiresAtLessThanEqualOrderByCreatedAtDesc(now))
            .thenReturn(List.of(used));

        assertThat(registrationCodeService.listActiveSummaries())
            .singleElement()
            .satisfies(summary -> {
                assertThat(summary.id()).isEqualTo(1L);
                assertThat(summary.status()).isEqualTo("Unused");
            });

        assertThat(registrationCodeService.listHistorySummaries())
            .singleElement()
            .satisfies(summary -> {
                assertThat(summary.id()).isEqualTo(2L);
                assertThat(summary.status()).isEqualTo("Used");
            });
    }
}
