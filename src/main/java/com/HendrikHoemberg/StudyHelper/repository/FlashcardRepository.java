package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    List<Flashcard> findByDeck(Deck deck);
    List<Flashcard> findByDeckIn(List<Deck> decks);
    Optional<Flashcard> findByIdAndDeckUserUsername(Long id, String username);

    @Query("""
        select coalesce(sum(coalesce(c.frontImageSizeBytes, 0) + coalesce(c.backImageSizeBytes, 0)), 0)
        from Flashcard c
        where c.deck.user = :user
        """)
    long sumImageSizeBytesByUser(@Param("user") User user);

    @Query("select c.frontImageFilename from Flashcard c where c.deck.user = :user and c.frontImageFilename is not null")
    List<String> findFrontImageFilenamesByUser(@Param("user") User user);

    @Query("select c.backImageFilename from Flashcard c where c.deck.user = :user and c.backImageFilename is not null")
    List<String> findBackImageFilenamesByUser(@Param("user") User user);

    @Query("select c.id from Flashcard c where c.id in :ids")
    List<Long> findExistingIdsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
        select count(f)
        from Flashcard f
        where f.deck.user = :user and f.correctStreak = 0
        """)
    long countMistakesByUser(@Param("user") User user);

    @Query("""
        select f
        from Flashcard f
        join fetch f.deck d
        where d.user = :user and f.correctStreak = 0
        order by f.id asc
        """)
    List<Flashcard> findMistakesByUser(@Param("user") User user);

    @Query("""
        select count(f)
        from Flashcard f
        where f.deck.user = :user and (f.correctStreak is null or f.correctStreak < 2)
        """)
    long countNotMasteredByUser(@Param("user") User user);
}

