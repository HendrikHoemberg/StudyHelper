package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.ReviewLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

    @Query("""
        select count(r)
        from ReviewLog r
        where r.user = :user and r.wasNew = true
          and r.reviewedAt >= :start and r.reviewedAt < :end
        """)
    long countNewIntroducedBetween(@Param("user") User user,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
}
