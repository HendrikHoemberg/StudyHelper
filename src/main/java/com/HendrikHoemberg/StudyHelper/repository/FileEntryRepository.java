package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileEntryRepository extends JpaRepository<FileEntry, Long> {
    List<FileEntry> findByUser(User user);
    List<FileEntry> findByUserAndFolder(User user, Folder folder);
    Optional<FileEntry> findByIdAndUser(Long id, User user);
    Optional<FileEntry> findByStoredFilename(String storedFilename);

    @Query("select coalesce(sum(f.fileSizeBytes), 0) from FileEntry f where f.user = :user")
    long sumFileSizeBytesByUser(@Param("user") User user);

    @Query("select f.storedFilename from FileEntry f where f.user = :user")
    List<String> findStoredFilenamesByUser(@Param("user") User user);
}
