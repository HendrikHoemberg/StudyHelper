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
            
            // C1 STUB
            return "<div>Generated " + questions.size() + " questions for exam on " + sourceSummary + ". TODO C3 real runtime view.</div>";

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "<div>Error: " + e.getMessage() + "</div>";
        }
    }

    @PostMapping("/exam/answer")
    @ResponseBody
    public String saveAnswer(@RequestParam int index, @RequestParam String answer, HttpSession session) {
        ExamSessionState state = (ExamSessionState) session.getAttribute(SESSION_KEY);
        if (state == null) return "Session expired";

        Map<Integer, String> newAnswers = new HashMap<>(state.answers());
        newAnswers.put(index, answer);
        
        ExamSessionState newState = new ExamSessionState(
            state.config(), state.questions(), newAnswers, state.startedAt(), state.sourceSummary()
        );
        session.setAttribute(SESSION_KEY, newState);
        
        return "OK";
    }

    @GetMapping("/exam/next")
    public String next(@RequestParam int currentIndex, Model model) {
        return "<div>Next question after " + currentIndex + " stub.</div>";
    }

    @GetMapping("/exam/prev")
    public String prev(@RequestParam int currentIndex, Model model) {
        return "<div>Previous question before " + currentIndex + " stub.</div>";
    }

    @PostMapping("/exam/submit")
    public String submit(Model model, Principal principal, HttpSession session, HttpServletResponse response) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        
        ExamSessionState state = (ExamSessionState) session.getAttribute(SESSION_KEY);
        if (state == null) return "<div>Session expired</div>";

        try {
            ExamGradingResult grading = aiExamService.grade(state.questions(), state.answers(), state.config().size());
            Exam saved = examService.saveCompleted(user, state, grading);
            session.removeAttribute(SESSION_KEY);
            
            return "<div>Exam submitted and graded! Score: " + saved.getOverallScorePct() + "%. TODO C3 real results view.</div>";
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return "<div>Grading failed: " + e.getMessage() + "</div>";
        }
    }

    @GetMapping("/exams")
    public String listExams(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        List<Exam> exams = examService.listForUser(user);
        model.addAttribute("exams", exams);
        return "<div>List of " + exams.size() + " exams stub.</div>";
    }

    @GetMapping("/exams/{id}")
    public String viewExam(@PathVariable Long id, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());
        try {
            Exam exam = examService.getOwnedById(user, id);
            model.addAttribute("exam", exam);
            return "<div>Detail view for exam " + id + " stub. Title: " + exam.getTitle() + "</div>";
        } catch (NoSuchElementException e) {
            return "<div>Exam not found</div>";
        }
    }

    @PostMapping("/exams/{id}/rename")
    @ResponseBody
    public String renameExam(@PathVariable Long id, @RequestParam String newTitle, Principal principal) {
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
    public String deleteExam(@PathVariable Long id, Principal principal) {
        if (principal == null) return "Unauthorized";
        User user = userService.getByUsername(principal.getName());
        try {
            examService.deleteOwned(user, id);
            return "OK";
        } catch (NoSuchElementException e) {
            return "Error";
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
