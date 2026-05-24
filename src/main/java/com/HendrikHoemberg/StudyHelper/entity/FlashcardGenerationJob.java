package com.HendrikHoemberg.StudyHelper.entity;

import com.HendrikHoemberg.StudyHelper.dto.DocumentMode;
import com.HendrikHoemberg.StudyHelper.dto.FlashcardGenerationDestination;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "flashcard_generation_jobs")
@Getter
@Setter
@NoArgsConstructor
public class FlashcardGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlashcardGenerationJobStatus status = FlashcardGenerationJobStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentMode documentMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FlashcardGenerationDestination destination;

    @Column(nullable = false, length = 1000)
    private String sourceFileIdsCsv;

    private Long existingDeckId;
    private Long newDeckFolderId;

    @Column(length = 255)
    private String newDeckName;

    @Column(length = 1000)
    private String additionalInstructions;

    @Column(nullable = false)
    private int estimatedRequestCost;

    @Column(nullable = false)
    private int chargedRequestCost;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false)
    private int completedChunkCount;

    @Column(nullable = false)
    private int generatedCardCount;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String chunkPlanSnapshot;

    private Long savedDeckId;

    @Column(nullable = false)
    private boolean throttled;

    @Column(length = 1000)
    private String failureMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;
    private Instant finishedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = FlashcardGenerationJobStatus.QUEUED;
    }
}
