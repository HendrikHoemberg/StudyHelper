package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByUserAndParentFolderIsNull(User user);
    List<Folder> findByUserAndParentFolder(User user, Folder parentFolder);
    Optional<Folder> findByIdAndUser(Long id, User user);
}
