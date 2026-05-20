package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.SavedSession;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SavedSessionRepository extends JpaRepository<SavedSession, Long> {

    Optional<SavedSession> findByUser(User user);

    void deleteByUser(User user);
}
