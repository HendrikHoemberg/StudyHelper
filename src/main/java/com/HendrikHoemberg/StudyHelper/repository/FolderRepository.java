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

    /**
     * Fetches all root folders for a user with direct subfolders eagerly loaded.
     *
     * Decks, files, and deck flashcards are intentionally left out of this graph:
     * these associations are Lists, and joining several bag collections together
     * can produce Hibernate multiple-bag fetch errors or large cartesian result
     * sets. Tree builders traverse those collections inside one transaction and
     * rely on hibernate.default_batch_fetch_size to load them in bounded batches.
     */
    @EntityGraph(attributePaths = {"subFolders"})
    @Query("select distinct f from Folder f where f.user = :user and f.parentFolder is null")
    List<Folder> findRootsWithSubFoldersByUser(@Param("user") User user);

    /**
     * Fetches one folder by id+user with direct subfolders eagerly loaded. Other
     * collections are batch fetched during traversal; see findRootsWithSubFoldersByUser.
     */
    @EntityGraph(attributePaths = {"subFolders"})
    @Query("select distinct f from Folder f where f.id = :id and f.user = :user")
    Optional<Folder> findByIdAndUserWithSubFolders(@Param("id") Long id, @Param("user") User user);
}
