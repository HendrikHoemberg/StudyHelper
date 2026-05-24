package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJob;
import com.HendrikHoemberg.StudyHelper.entity.FlashcardGenerationJobStatus;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FlashcardGenerationJobRepository extends JpaRepository<FlashcardGenerationJob, Long> {
    Optional<FlashcardGenerationJob> findByIdAndUser(Long id, User user);
    List<FlashcardGenerationJob> findByStatusAndStartedAtBefore(FlashcardGenerationJobStatus status, Instant startedBefore);
}
