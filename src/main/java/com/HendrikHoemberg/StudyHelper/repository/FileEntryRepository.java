package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileEntryRepository extends JpaRepository<FileEntry, Long> {
    List<FileEntry> findByUser(User user);
    List<FileEntry> findByUserAndFolder(User user, Folder folder);
    Optional<FileEntry> findByIdAndUser(Long id, User user);
    Optional<FileEntry> findByStoredFilename(String storedFilename);
}
