package com.HendrikHoemberg.StudyHelper.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "study_log",
    indexes = {
        @Index(name = "idx_study_log_user_completed", columnList = "user_id, completed_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class StudyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SavedSessionType type;

    @Column(nullable = false)
    private String title;

    @Column(name = "card_count", nullable = false)
    private int cardCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;
}
