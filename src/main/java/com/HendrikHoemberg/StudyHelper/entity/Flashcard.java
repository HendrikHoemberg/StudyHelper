package com.HendrikHoemberg.StudyHelper.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "flashcards")
@Getter
@Setter
@NoArgsConstructor
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "front_text", columnDefinition = "TEXT", nullable = false)
    private String frontText;

    @Column(name = "back_text", columnDefinition = "TEXT", nullable = false)
    private String backText;

    @Column(name = "front_image_filename")
    private String frontImageFilename;

    @Column(name = "back_image_filename")
    private String backImageFilename;

    @Column(name = "front_image_size_bytes")
    private Long frontImageSizeBytes;

    @Column(name = "back_image_size_bytes")
    private Long backImageSizeBytes;

    @Column(name = "correct_streak")
    private Integer correctStreak;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "ease_factor")
    private Double easeFactor;

    @Column(name = "repetitions")
    private Integer repetitions;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;
}
