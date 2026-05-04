package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.ExamGradingResult;
import com.HendrikHoemberg.StudyHelper.dto.ExamReport;
import com.HendrikHoemberg.StudyHelper.dto.ExamSessionState;
import com.HendrikHoemberg.StudyHelper.entity.Exam;
import com.HendrikHoemberg.StudyHelper.entity.ExamQuestionResult;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.ExamRepository;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final JsonMapper objectMapper;

    public ExamService(ExamRepository examRepository, JsonMapper objectMapper) {
        this.examRepository = examRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Exam saveCompleted(User user, ExamSessionState state, ExamGradingResult grading) {
        Exam exam = new Exam();
        exam.setUser(user);
        
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
        exam.setTitle("Exam · " + state.sourceSummary() + " · " + dateStr);
        
        exam.setCreatedAt(LocalDateTime.ofInstant(state.startedAt(), ZoneId.systemDefault()));
        exam.setCompletedAt(LocalDateTime.now());
        exam.setQuestionSize(state.config().size());
        exam.setQuestionCount(state.config().count());
        exam.setTimerMinutes(state.config().timerMinutes());
        exam.setLayout(state.config().layout());
        exam.setOverallScorePct(grading.overall().scorePercent());
        exam.setSourceSummary(state.sourceSummary());

        try {
            ExamReport report = new ExamReport(
                    grading.overall().scorePercent(),
                    grading.overall().strengths(),
                    grading.overall().weaknesses(),
                    grading.overall().topicsToRevisit(),
                    grading.overall().suggestedNextSteps()
            );
            exam.setReportJson(objectMapper.writeValueAsString(report));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize exam report", e);
        }

        List<ExamQuestionResult> questions = new ArrayList<>();
        for (int i = 0; i < state.questions().size(); i++) {
            ExamQuestionResult result = new ExamQuestionResult();
            result.setExam(exam);
            result.setPosition(i);
            result.setQuestionText(state.questions().get(i).questionText());
            result.setExpectedAnswerHints(state.questions().get(i).expectedAnswerHints());
            result.setUserAnswer(state.answers().getOrDefault(i, ""));
            
            ExamGradingResult.PerQuestion qGrading = grading.perQuestion().get(i);
            result.setScorePercent(qGrading.scorePercent());
            result.setFeedback(qGrading.feedback());
            
            questions.add(result);
        }
        exam.setQuestions(questions);

        return examRepository.save(exam);
    }

    public List<Exam> listForUser(User user) {
        return examRepository.findAllByUserOrderByCreatedAtDesc(user);
    }

    public Exam getOwnedById(User user, Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NoSuchElementException("Exam not found"));
        if (!exam.getUser().getId().equals(user.getId())) {
            throw new NoSuchElementException("Exam not found or access denied");
        }
        return exam;
    }

    @Transactional
    public void deleteOwned(User user, Long examId) {
        Exam exam = getOwnedById(user, examId);
        examRepository.delete(exam);
    }

    @Transactional
    public void renameOwned(User user, Long examId, String newTitle) {
        Exam exam = getOwnedById(user, examId);
        exam.setTitle(newTitle);
        examRepository.save(exam);
    }

    public ExamReport deserializeReport(String reportJson) {
        try {
            return objectMapper.readValue(reportJson, ExamReport.class);
        } catch (Exception e) {
            // Fallback for corrupt JSON
            return new ExamReport(0, List.of(), List.of(), List.of(), List.of());
        }
    }
}
