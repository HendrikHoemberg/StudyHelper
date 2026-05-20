package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.StudyLog;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyLogRepository extends JpaRepository<StudyLog, Long> {
    List<StudyLog> findByUserOrderByCompletedAtDesc(User user, Pageable pageable);
}
