package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByUserAndParentFolderIsNull(User user);
    List<Folder> findByUserAndParentFolder(User user, Folder parentFolder);
    Optional<Folder> findByIdAndUser(Long id, User user);

    @EntityGraph(attributePaths = {"subFolders"})
    @Query("select distinct f from Folder f where f.user = :user and f.parentFolder is null")
    List<Folder> findRootsWithSubtreeByUser(@Param("user") User user);

    @EntityGraph(attributePaths = {"subFolders"})
    @Query("select distinct f from Folder f where f.id = :id and f.user = :user")
    Optional<Folder> findByIdAndUserWithSubtree(@Param("id") Long id, @Param("user") User user);
}
