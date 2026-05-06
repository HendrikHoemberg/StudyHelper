package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Exam;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ExamController {

    private static final String SESSION_KEY = "examSession";
    
    private final AiExamService aiExamService;
    private final ExamService examService;
    private final UserService userService;
    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final FileEntryService fileEntryService;
    private final DocumentExtractionService documentExtractionService;

    public ExamController(AiExamService aiExamService,
                          ExamService examService,
                          UserService userService,
                          DeckService deckService,
                          FlashcardService flashcardService,
                          FileEntryService fileEntryService,
                          DocumentExtractionService documentExtractionService) {
        this.aiExamService = aiExamService;
        this.examService = examService;
        this.userService = userService;
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.fileEntryService = fileEntryService;
        this.documentExtractionService = documentExtractionService;
    }

    @PostMapping("/exam/session")
    public String createSession(
            @RequestParam(required = false) List<Long> selectedDeckIds,
            @RequestParam(required = false) List<Long> selectedFileIds,
            @RequestParam(defaultValue = "MEDIUM") ExamQuestionSize questionSize,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(required = false) Integer timerMinutes,
            @RequestParam(defaultValue = "PER_PAGE") ExamLayout layout,
            Model model, Principal principal, HttpSession session, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {

        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());

        List<Long> deckIds = normalizeIds(selectedDeckIds);
        List<Long> fileIds = normalizeIds(selectedFileIds);

        if (deckIds.isEmpty() && fileIds.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            // This would normally go back to setup with an error, but since we are stubbing for now:
            return "<div>Error: Please select at least one source.</div>";
        }

        try {
            List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(deckIds, user);
            List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
            
            List<AiExamService.DocumentInput> documents = new ArrayList<>();
            List<String> sourceNames = new ArrayList<>();
            decks.forEach(d -> sourceNames.add(d.getName()));
            
            long totalChars = 0;
            for (Long fileId : fileIds) {
                com.HendrikHoemberg.StudyHelper.entity.FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                String text = documentExtractionService.extractText(file);
                documents.add(new AiExamService.DocumentInput(file.getOriginalFilename(), text));
                sourceNames.add(file.getOriginalFilename());
                totalChars += text.length();
            }

            if (totalChars > 150_000) {
                throw new IllegalArgumentException("Selection too large — please deselect some sources.");
            }

            int qCount = Math.max(1, Math.min(count, 20));
            List<ExamQuestion> questions = aiExamService.generate(flashcards, documents, qCount, questionSize);

            String sourceSummary = sourceNames.stream().limit(3).collect(Collectors.joining(", "));
            if (sourceNames.size() > 3) sourceSummary += " + " + (sourceNames.size() - 3) + " more";

            ExamSessionState state = new ExamSessionState(
                new ExamConfig(deckIds, fileIds, questionSize, qCount, timerMinutes, layout),
                questions, new HashMap<>(), Instant.now(), sourceSummary
            );

            session.setAttribute(SESSION_KEY, state);
            
            if ("true".equals(hxRequest)) {
                model.addAttribute("state", state);
                model.addAttribute("currentIndex", 0);
                if (layout == ExamLayout.PER_PAGE) {
                    return "fragments/exam-question :: exam-question";
                } else {
                    return "fragments/exam-single-page :: exam-single-page";
                }
            }

            return "exam-page";

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "<div>Error: " + e.getMessage() + "</div>";
        }
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
        if (principal == null) return "redirect:/login";
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
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return "<div>Grading failed: " + e.getMessage() + "</div>";
        }
    }

    @GetMapping("/exams")
    public String listExams(Model model, Principal principal,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        List<Exam> exams = examService.listForUser(user);
        model.addAttribute("exams", exams);
        
        if ("true".equals(hxRequest)) {
            model.addAttribute("sidebarOob", true);
            return "fragments/exams-list :: exams-list";
        }
        return "exams-page";
    }

    @GetMapping("/exams/{id}")
    public String viewExam(@PathVariable Long id, Model model, Principal principal,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        try {
            Exam exam = examService.getOwnedById(user, id);
            ExamReport report = examService.deserializeReport(exam.getReportJson());
            model.addAttribute("exam", exam);
            model.addAttribute("report", report);
            
            if ("true".equals(hxRequest)) {
                model.addAttribute("sidebarOob", true);
                return "fragments/exam-detail :: exam-detail";
            }
            return "exams-page";
        } catch (NoSuchElementException e) {
            // Plan says ownership errors -> 404. 
            // In a real app we'd use @ResponseStatus(HttpStatus.NOT_FOUND) or throw a custom exception.
            return "redirect:/exams"; 
        }
    }

    @PostMapping("/exams/{id}/rename")
    @ResponseBody
    public String renameExam(@PathVariable Long id, @RequestParam("title") String newTitle, Principal principal) {
        if (principal == null) return "Unauthorized";
        User user = userService.getByUsername(principal.getName());
        try {
            examService.renameOwned(user, id, newTitle);
            return newTitle;
        } catch (NoSuchElementException e) {
            return "Error";
        }
    }

    @DeleteMapping("/exams/{id}")
    @ResponseBody
    public String deleteExam(@PathVariable Long id, Principal principal,
                             @RequestHeader(value = "HX-Target", required = false) String hxTarget,
                             HttpServletResponse response) {
        if (principal == null) return "Unauthorized";
        User user = userService.getByUsername(principal.getName());
        try {
            examService.deleteOwned(user, id);
            
            // If coming from detail page, redirect to list.
            // HX-Target will usually be 'explorer-detail' (or similar) when in detail view.
            if ("explorer-detail".equals(hxTarget)) {
                response.setHeader("HX-Redirect", "/exams");
            }
            
            return ""; // Empty body for card removal in list view
        } catch (NoSuchElementException e) {
            return "Error";
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
