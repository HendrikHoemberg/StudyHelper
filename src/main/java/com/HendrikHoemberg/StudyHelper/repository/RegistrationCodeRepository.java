package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.RegistrationCode;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RegistrationCodeRepository extends JpaRepository<RegistrationCode, Long> {

    Optional<RegistrationCode> findFirstByCodeHashAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(String codeHash);

    List<RegistrationCode> findByUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Instant now);

    List<RegistrationCode> findByUsedAtIsNotNullOrRevokedAtIsNotNullOrExpiresAtLessThanEqualOrderByCreatedAtDesc(Instant now);

    List<RegistrationCode> findAllByOrderByCreatedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RegistrationCode rc
        set rc.usedAt = :usedAt, rc.usedBy = :usedBy
        where rc.id = :id
          and rc.usedAt is null
          and rc.revokedAt is null
          and rc.expiresAt > :now
        """)
    int markAsUsedIfConsumable(@Param("id") Long id,
                               @Param("usedAt") Instant usedAt,
                               @Param("usedBy") User usedBy,
                               @Param("now") Instant now);
}
