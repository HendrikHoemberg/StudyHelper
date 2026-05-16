package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.Exam;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
public class ExamController {

    private static final String SESSION_KEY = "examSession";

    private final ExamSessionService examSessionService;
    private final ExamService examService;
    private final UserService userService;
    private final AiExamService aiExamService;
    private final AiRequestQuotaService aiRequestQuotaService;

    @Autowired
    public ExamController(ExamSessionService examSessionService,
                          ExamService examService,
                          UserService userService,
                          AiExamService aiExamService,
                          AiRequestQuotaService aiRequestQuotaService) {
        this.examSessionService = examSessionService;
        this.examService = examService;
        this.userService = userService;
        this.aiExamService = aiExamService;
        this.aiRequestQuotaService = aiRequestQuotaService;
    }

    @PostMapping("/exam/session")
    public String createSession(
            @RequestParam(required = false) List<Long> selectedDeckIds,
            @RequestParam(required = false) List<Long> selectedFileIds,
            @RequestParam(required = false) String additionalInstructions,
            HttpServletRequest request,
            @RequestParam(defaultValue = "MEDIUM") ExamQuestionSize questionSize,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(required = false) Integer timerMinutes,
            @RequestParam(defaultValue = "PER_PAGE") ExamLayout layout,
            Model model, Principal principal, HttpSession session, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {

        User user = userService.getByUsername(principal.getName());

        try {
            ExamSessionService.ExamSessionResult result = examSessionService.createSession(
                selectedDeckIds, selectedFileIds, additionalInstructions, request,
                questionSize, count, timerMinutes, layout, user
            );
            response.addHeader("HX-Trigger", "refresh-quota");
            session.setAttribute(SESSION_KEY, result.state());

            if ("true".equals(hxRequest)) {
                model.addAttribute("state", result.state());
                model.addAttribute("currentIndex", 0);
                return result.layout() == ExamLayout.PER_PAGE
                    ? "fragments/exam-question :: exam-question"
                    : "fragments/exam-single-page :: exam-single-page";
            }
            return "exam-page";

        } catch (Exception e) {
            return renderGenerationError(model, response, e);
        }
    }

    @PostMapping("/exam/session/preflight")
    public String preflightCreateSession(
            @RequestParam(required = false) List<Long> selectedDeckIds,
            @RequestParam(required = false) List<Long> selectedFileIds,
            HttpServletRequest request,
            @RequestParam(defaultValue = "MEDIUM") ExamQuestionSize questionSize,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(required = false) Integer timerMinutes,
            @RequestParam(defaultValue = "PER_PAGE") ExamLayout layout,
            Model model, Principal principal, HttpSession session, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());

        try {
            examSessionService.validateForPreflight(
                selectedDeckIds, selectedFileIds, request,
                questionSize, count, timerMinutes, layout, user
            );
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        } catch (Exception e) {
            return renderGenerationError(model, response, e);
        }
    }

    private String renderGenerationError(Model model, HttpServletResponse response, Exception exception) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        // An AI generation failure means the provider was reached after quota
        // was consumed, so the client still needs to refresh its quota widget.
        if (exception instanceof AiGenerationException) {
            response.addHeader("HX-Trigger", "refresh-quota");
        }
        model.addAttribute("aiErrorTitle", "AI generation failed");
        String message = exception == null ? null : exception.getMessage();
        model.addAttribute("aiErrorMessage", message == null || message.isBlank()
            ? "Please try again."
            : message);
        model.addAttribute("aiErrorDetails", generationDetails(exception));
        return "fragments/ai-generation-error :: aiGenerationError";
    }

    private String generationDetails(Exception ex) {
        if (ex instanceof AiGenerationException aiEx && aiEx.diagnostics() != null) {
            return aiEx.diagnostics().toDisplayString();
        }
        return AiGenerationDiagnostics.fromException("EXAM", "REQUEST_VALIDATION", ex).toDisplayString();
    }

    @PostMapping("/exam/answer")
    public String saveAnswer(@RequestParam int index,
                             @RequestParam String answer,
                             HttpSession session, Model model) {
        ExamSessionState state = (ExamSessionState) session.getAttribute(SESSION_KEY);
        if (state == null) return "redirect:/study/start";

        Map<Integer, String> newAnswers = new HashMap<>(state.answers());
        newAnswers.put(index, answer);

        ExamSessionState newState = new ExamSessionState(
            state.config(), state.questions(), newAnswers, state.startedAt(), state.sourceSummary()
        );
        session.setAttribute(SESSION_KEY, newState);

        model.addAttribute("state", newState);
        model.addAttribute("currentIndex", index + 1);
        return "fragments/exam-question :: exam-question";
    }

    @GetMapping("/exam/next")
    public String next(@RequestParam int currentIndex, HttpSession session, Model model) {
        ExamSessionState state = (ExamSessionState) session.getAttribute(SESSION_KEY);
        if (state == null) return "redirect:/study/start";

        model.addAttribute("state", state);
        model.addAttribute("currentIndex", currentIndex + 1);
        return "fragments/exam-question :: exam-question";
    }

    @GetMapping("/exam/prev")
    public String prev(@RequestParam int currentIndex, HttpSession session, Model model) {
        ExamSessionState state = (ExamSessionState) session.getAttribute(SESSION_KEY);
        if (state == null) return "redirect:/study/start";

        model.addAttribute("state", state);
        model.addAttribute("currentIndex", currentIndex - 1);
        return "fragments/exam-question :: exam-question";
    }

    @PostMapping("/exam/submit")
    public String submit(@RequestParam(required = false) Integer index,
                         @RequestParam(required = false) String answer,
                         @RequestParam(required = false) Map<String, String> allAnswers,
                         Model model, Principal principal, HttpSession session, HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());

        ExamSessionState state = (ExamSessionState) session.getAttribute(SESSION_KEY);
        if (state == null) return "<div>Session expired</div>";

        // Update answers one last time if they came in with the submit
        Map<Integer, String> finalAnswers = new HashMap<>(state.answers());
        if (index != null && answer != null) {
            finalAnswers.put(index, answer);
        } else if (allAnswers != null) {
            // SINGLE_PAGE layout sends answers[0], answers[1]...
            allAnswers.forEach((key, value) -> {
                if (key.startsWith("answers[")) {
                    try {
                        int idx = Integer.parseInt(key.substring(8, key.length() - 1));
                        finalAnswers.put(idx, value);
                    } catch (Exception ignored) {}
                }
            });
        }

        try {
            aiRequestQuotaService.checkAndRecord(user);
            response.addHeader("HX-Trigger", "refresh-quota");
            ExamGradingResult grading = aiExamService.grade(state.questions(), finalAnswers, state.config().size());

            // Create a new state with final answers for saving
            ExamSessionState finalState = new ExamSessionState(
                state.config(), state.questions(), finalAnswers, state.startedAt(), state.sourceSummary()
            );

            Exam saved = examService.saveCompleted(user, finalState, grading);
            session.removeAttribute(SESSION_KEY);

            model.addAttribute("exam", saved);
            model.addAttribute("report", grading.overall());
            return "fragments/exam-result :: exam-result";
        } catch (AiQuotaExceededException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "<div>Grading failed: " + e.getMessage() + "</div>";
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return "<div>Grading failed: " + e.getMessage() + "</div>";
        }
    }

    @GetMapping("/exams")
    public String listExams(Model model, Principal principal,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        List<Exam> exams = examService.listForUser(user);
        model.addAttribute("exams", exams);

        if ("true".equals(hxRequest)) {
            model.addAttribute("refreshSidebar", true);
            return "fragments/exams-list :: exams-list";
        }
        return "exams-page";
    }

    @GetMapping("/exams/{id}")
    public String viewExam(@PathVariable Long id, Model model, Principal principal,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        Exam exam = examService.getOwnedById(user, id);
        ExamReport report = examService.deserializeReport(exam.getReportJson());
        model.addAttribute("exam", exam);
        model.addAttribute("report", report);

        if ("true".equals(hxRequest)) {
            model.addAttribute("refreshSidebar", true);
            return "fragments/exam-detail :: exam-detail";
        }
        return "exams-page";
    }

    @PostMapping("/exams/{id}/rename")
    @ResponseBody
    public String renameExam(@PathVariable Long id, @RequestParam("title") String newTitle, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        examService.renameOwned(user, id, newTitle);
        return newTitle;
    }

    @DeleteMapping("/exams/{id}")
    @ResponseBody
    public String deleteExam(@PathVariable Long id, Principal principal,
                             @RequestHeader(value = "HX-Target", required = false) String hxTarget,
                             HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        examService.deleteOwned(user, id);

        // HX-Target is 'explorer-detail' when the delete comes from the detail view;
        // send the client back to the list since the detail page no longer exists.
        if ("explorer-detail".equals(hxTarget)) {
            response.setHeader("HX-Redirect", "/exams");
        }

        return "";
    }

}
