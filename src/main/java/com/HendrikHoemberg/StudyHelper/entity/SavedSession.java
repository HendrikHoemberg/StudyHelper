package com.HendrikHoemberg.StudyHelper.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "saved_session",
        uniqueConstraints = @UniqueConstraint(name = "uk_saved_session_user", columnNames = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class SavedSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SavedSessionType type;

    @Lob
    @Column(name = "payload", columnDefinition = "LONGTEXT", nullable = false)
    private String payload;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "progress_label", length = 64, nullable = false)
    private String progressLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
