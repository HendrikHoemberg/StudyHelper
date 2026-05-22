package com.HendrikHoemberg.StudyHelper.entity;

import com.HendrikHoemberg.StudyHelper.dto.Grade;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "review_log",
    indexes = {
        @Index(name = "idx_review_log_user_reviewed", columnList = "user_id, reviewed_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class ReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flashcard_id", nullable = false)
    private Flashcard flashcard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Grade grade;

    @Column(name = "interval_days_after", nullable = false)
    private int intervalDaysAfter;

    @Column(name = "ease_factor_after", nullable = false)
    private double easeFactorAfter;

    @Column(name = "was_new", nullable = false)
    private boolean wasNew;
}
