package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.StudyLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StudyLogRepository extends JpaRepository<StudyLog, Long> {

    List<StudyLog> findByUserOrderByCompletedAtDesc(User user, Pageable pageable);

    boolean existsByUserAndCompletedAtBetween(User user, LocalDateTime start, LocalDateTime end);

    @Query("""
        select coalesce(sum(coalesce(l.durationSec, 0)), 0)
        from StudyLog l
        where l.user = :user and l.completedAt between :start and :end
        """)
    long sumDurationSecBetween(@Param("user") User user,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    @Query("""
        select coalesce(sum(l.cardCount), 0)
        from StudyLog l
        where l.user = :user and l.completedAt between :start and :end
        """)
    long sumCardCountBetween(@Param("user") User user,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    @Query("""
        select coalesce(sum(l.correctCount), 0)
        from StudyLog l
        where l.user = :user and l.completedAt between :start and :end
        """)
    long sumCorrectCountBetween(@Param("user") User user,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    @Query("""
        select function('DATE', l.completedAt) as day, count(l)
        from StudyLog l
        where l.user = :user and l.completedAt >= :start
        group by function('DATE', l.completedAt)
        """)
    List<Object[]> aggregateDailyCountsSince(@Param("user") User user,
                                             @Param("start") LocalDateTime start);
}
