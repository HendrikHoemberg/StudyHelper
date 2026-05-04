package com.HendrikHoemberg.StudyHelper.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ExamQuestionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    private int position;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String questionText;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String expectedAnswerHints;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String userAnswer;

    private int scorePercent;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String feedback;
}
