package com.HendrikHoemberg.StudyHelper.entity;

import com.HendrikHoemberg.StudyHelper.dto.ExamLayout;
import com.HendrikHoemberg.StudyHelper.dto.ExamQuestionSize;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String title;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    private ExamQuestionSize questionSize;

    private int questionCount;

    private Integer timerMinutes;

    @Enumerated(EnumType.STRING)
    private ExamLayout layout;

    private int overallScorePct;

    private String sourceSummary;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String reportJson;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamQuestionResult> questions = new ArrayList<>();
}
