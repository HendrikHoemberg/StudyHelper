package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.AiRequestUsage;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;

public interface AiRequestUsageRepository extends JpaRepository<AiRequestUsage, Long> {
    Optional<AiRequestUsage> findByUserAndUsageDate(User user, LocalDate usageDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AiRequestUsage u where u.user = :user and u.usageDate = :usageDate")
    Optional<AiRequestUsage> findByUserAndUsageDateForUpdate(@Param("user") User user,
                                                              @Param("usageDate") LocalDate usageDate);
}
