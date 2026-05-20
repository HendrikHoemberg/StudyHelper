package com.HendrikHoemberg.StudyHelper.repository;

import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Folder;
import com.HendrikHoemberg.StudyHelper.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeckRepository extends JpaRepository<Deck, Long> {
    List<Deck> findByUserAndFolder(User user, Folder folder);
    List<Deck> findByUser(User user);
    Optional<Deck> findByIdAndUser(Long id, User user);

    List<Deck> findByUserAndPinnedTrueOrderByNameAsc(User user);

    List<Deck> findByUserAndLastStudiedAtIsNotNullOrderByLastStudiedAtDesc(User user, Pageable pageable);

    @Query("""
        select coalesce(count(f), 0)
        from Flashcard f
        where f.deck = :deck and f.correctStreak >= 2
        """)
    long countMasteredByDeck(@Param("deck") Deck deck);

    @Query("""
        select coalesce(count(f), 0)
        from Flashcard f
        where f.deck = :deck
        """)
    long countCardsByDeck(@Param("deck") Deck deck);
}
