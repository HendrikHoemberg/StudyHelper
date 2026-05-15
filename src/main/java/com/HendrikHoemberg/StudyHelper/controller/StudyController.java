package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.*;

@Controller
public class StudyController {

    private static final Logger log = LoggerFactory.getLogger(StudyController.class);

    private final StudySessionService studySessionService;
    private final QuizSessionService quizSessionService;
    private final DeckService deckService;
    private final FolderService folderService;
    private final UserService userService;
    private final DocumentExtractionService documentExtractionService;
    private final FileEntryService fileEntryService;
    private final ExamSessionService examSessionService;

    @Autowired
    public StudyController(StudySessionService studySessionService,
                           QuizSessionService quizSessionService,
                           DeckService deckService,
                           FolderService folderService,
                           UserService userService,
                           DocumentExtractionService documentExtractionService,
                           FileEntryService fileEntryService,
                           ExamSessionService examSessionService) {
        this.studySessionService = studySessionService;
        this.quizSessionService = quizSessionService;
        this.deckService = deckService;
        this.folderService = folderService;
        this.userService = userService;
        this.documentExtractionService = documentExtractionService;
        this.fileEntryService = fileEntryService;
        this.examSessionService = examSessionService;
    }

    @GetMapping("/study/start")
    public String start(@RequestParam(required = false) StudyMode mode,
                        @RequestParam(required = false) Long deckId,
                        @RequestParam(required = false) Long folderId,
                        @RequestParam(required = false) Long fileId,
                        Model model,
                        Principal principal,
                        HttpSession session,
                        @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userService.getByUsername(principal.getName());
        model.addAttribute("username", user.getUsername());

        List<Long> preselectedDeckIds = new ArrayList<>();
        List<Long> preselectedFileIds = new ArrayList<>();

        if (deckId != null) {
            try {
                deckService.getDeck(deckId, user);
                preselectedDeckIds.add(deckId);
            } catch (NoSuchElementException e) {
                log.debug("Ignoring preselect for missing deckId={} (user={})", deckId, user.getUsername());
            }
        }

        if (fileId != null) {
            try {
                FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                if (documentExtractionService.isSupported(file)) {
                    preselectedFileIds.add(fileId);
                }
            } catch (NoSuchElementException e) {
                log.debug("Ignoring preselect for missing fileId={} (user={})", fileId, user.getUsername());
            }
        }

        if (folderId != null) {
            try {
                FolderService.FolderSources sources = folderService.getAllSourcesInFolder(folderId, user);
                preselectedDeckIds.addAll(sources.deckIds());
                preselectedFileIds.addAll(sources.fileIds());
            } catch (NoSuchElementException e) {
                log.debug("Ignoring preselect for missing folderId={} (user={})", folderId, user.getUsername());
            }
        }

        model.addAttribute("mode", mode);
        prepareWizardModel(model, user, preselectedDeckIds, preselectedFileIds, null, session);

        if (hxRequest != null) {
            model.addAttribute("refreshSidebar", true);
            return "fragments/study-setup :: studySetup";
        }

        model.addAttribute("studyStateView", "setup");
        return "study-page";
    }

    @PostMapping("/study/setup/update")
    public String updateSetup(@RequestParam(required = false) StudyMode mode,
                              @RequestParam(required = false) List<Long> selectedDeckIds,
                              @RequestParam(required = false) List<Long> selectedFileIds,
                              @RequestParam(required = false) Long toggledFolderId,
                              @RequestParam(required = false) Long removeId,
                              @RequestParam(required = false) Long removeFileId,
                              @RequestParam(required = false, defaultValue = "false") boolean clearAll,
                              HttpServletRequest request,
                              Model model,
                              Principal principal,
                              HttpSession session) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userService.getByUsername(principal.getName());
        List<Long> decks = new ArrayList<>(normalizeIds(selectedDeckIds));
        List<Long> files = new ArrayList<>(normalizeIds(selectedFileIds));
        Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);

        if (clearAll) {
            decks.clear();
            files.clear();
            pdfMode.clear();
        } else if (removeId != null) {
            decks.remove(removeId);
        } else if (removeFileId != null) {
            files.remove(removeFileId);
            pdfMode.remove(removeFileId);
        } else if (toggledFolderId != null) {
            FolderService.FolderSources sources = folderService.getAllSourcesInFolder(toggledFolderId, user);
            List<Long> folderDecks = sources.deckIds();
            List<Long> folderFiles = sources.fileIds();

            boolean allSelected = new HashSet<>(decks).containsAll(folderDecks) &&
                                 new HashSet<>(files).containsAll(folderFiles);

            if (allSelected && (!folderDecks.isEmpty() || !folderFiles.isEmpty())) {
                decks.removeAll(folderDecks);
                files.removeAll(folderFiles);
                folderFiles.forEach(pdfMode::remove);
            } else {
                for (Long id : folderDecks) if (!decks.contains(id)) decks.add(id);
                for (Long id : folderFiles) if (!files.contains(id)) files.add(id);
            }
        }

        model.addAttribute("mode", mode);
        prepareWizardModel(model, user, decks, files, pdfMode, null, session);
        return "fragments/wizard-source-picker :: setupPicker";
    }

    @PostMapping("/study/session")
    public String createSession(@RequestParam StudyMode mode,
                                @RequestParam(required = false) List<Long> selectedDeckIds,
                                @RequestParam(required = false) List<Long> selectedFileIds,
                                @RequestParam(required = false) String additionalInstructions,
                                HttpServletRequest request,
                                // Flashcard params
                                @RequestParam(defaultValue = "DECK_BY_DECK") SessionMode sessionMode,
                                @RequestParam(defaultValue = "SELECTED_ORDER") DeckOrderMode deckOrderMode,
                                @RequestParam(required = false) String orderedDeckIds,
                                // Quiz params
                                @RequestParam(defaultValue = "MCQ_ONLY") QuizQuestionMode quizQuestionMode,
                                @RequestParam(defaultValue = "MEDIUM") Difficulty difficulty,
                                @RequestParam(defaultValue = "5") int questionCount,
                                // Exam params
                                @RequestParam(defaultValue = "MEDIUM") ExamQuestionSize questionSize,
                                @RequestParam(defaultValue = "5") int count,
                                @RequestParam(required = false) Integer timerMinutes,
                                @RequestParam(defaultValue = "PER_PAGE") ExamLayout layout,
                                Model model,
                                Principal principal,
                                HttpSession session,
                                HttpServletResponse response,
                                @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());

        if (mode == StudyMode.FLASHCARDS) {
            try {
                List<Long> orderedSelection = resolveOrderedSelection(selectedDeckIds, orderedDeckIds, sessionMode);
                StudySessionConfig config = new StudySessionConfig(orderedSelection, sessionMode, deckOrderMode);
                StudySessionState state = studySessionService.buildSession(config, user);
                session.setAttribute("studySessionState", state);
                return delegateToFlashcards(model, user, state, hxRequest);
            } catch (Exception ex) {
                return handleError(mode, selectedDeckIds, selectedFileIds, pdfMode, ex, model, user, session, response, hxRequest);
            }
        } else if (mode == StudyMode.QUIZ) {
            try {
                QuizSessionState state = quizSessionService.createSession(
                    selectedDeckIds, selectedFileIds, request, questionCount,
                    quizQuestionMode, difficulty, additionalInstructions, user
                );
                response.addHeader("HX-Trigger", "refresh-quota");
                model.addAttribute("mode", mode);
                session.setAttribute("quizSessionState", state);
                return delegateToQuiz(model, state, hxRequest);
            } catch (Exception ex) {
                return handleError(mode, selectedDeckIds, selectedFileIds, pdfMode, ex, model, user, session, response, hxRequest);
            }
        } else if (mode == StudyMode.EXAM) {
            try {
                ExamSessionService.ExamSessionResult result = examSessionService.createSession(
                    selectedDeckIds, selectedFileIds, additionalInstructions, request,
                    questionSize, count, timerMinutes, layout, user
                );
                response.addHeader("HX-Trigger", "refresh-quota");
                session.setAttribute("examSession", result.state());
                model.addAttribute("mode", mode);
                if (hxRequest != null) {
                    model.addAttribute("state", result.state());
                    model.addAttribute("currentIndex", 0);
                    return result.layout() == ExamLayout.PER_PAGE
                        ? "fragments/exam-question :: exam-question"
                        : "fragments/exam-single-page :: exam-single-page";
                }
                model.addAttribute("studyStateView", "exam");
                return "exam-page";
            } catch (Exception ex) {
                return handleError(mode, selectedDeckIds, selectedFileIds, pdfMode, ex, model, user, session, response, hxRequest);
            }
        }

        return "redirect:/study/start";
    }

    @PostMapping("/study/session/preflight")
    public String preflightCreateSession(@RequestParam StudyMode mode,
                                         @RequestParam(required = false) List<Long> selectedDeckIds,
                                         @RequestParam(required = false) List<Long> selectedFileIds,
                                         HttpServletRequest request,
                                         @RequestParam(defaultValue = "MCQ_ONLY") QuizQuestionMode quizQuestionMode,
                                         @RequestParam(defaultValue = "MEDIUM") Difficulty difficulty,
                                         @RequestParam(defaultValue = "5") int questionCount,
                                         @RequestParam(defaultValue = "MEDIUM") ExamQuestionSize questionSize,
                                         @RequestParam(defaultValue = "5") int count,
                                         @RequestParam(required = false) Integer timerMinutes,
                                         @RequestParam(defaultValue = "PER_PAGE") ExamLayout layout,
                                         Model model,
                                         Principal principal,
                                         HttpSession session,
                                         HttpServletResponse response,
                                         @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        Map<Long, DocumentMode> pdfMode = DocumentModeResolver.parseFromRequest(request);
        if (principal == null) return "redirect:/login";
        User user = userService.getByUsername(principal.getName());

        try {
            if (mode == StudyMode.QUIZ) {
                quizSessionService.validateForPreflight(
                    selectedDeckIds, selectedFileIds, request,
                    quizQuestionMode, difficulty, user
                );
            } else if (mode == StudyMode.EXAM) {
                examSessionService.validateForPreflight(
                    selectedDeckIds, selectedFileIds, request,
                    questionSize, count, timerMinutes, layout, user
                );
            } else if (mode == StudyMode.FLASHCARDS) {
                SessionMode sessionMode = parseSessionMode(request.getParameter("sessionMode"));
                DeckOrderMode deckOrderMode = parseDeckOrderMode(request.getParameter("deckOrderMode"));
                List<Long> orderedSelection = resolveOrderedSelection(
                    selectedDeckIds,
                    request.getParameter("orderedDeckIds"),
                    sessionMode
                );
                StudySessionConfig config = new StudySessionConfig(orderedSelection, sessionMode, deckOrderMode);
                studySessionService.buildSession(config, user);
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        } catch (Exception ex) {
            return handleError(mode, selectedDeckIds, selectedFileIds, pdfMode, ex, model, user, session, response, hxRequest);
        }
    }

    private SessionMode parseSessionMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return SessionMode.DECK_BY_DECK;
        }
        return SessionMode.valueOf(raw);
    }

    private DeckOrderMode parseDeckOrderMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return DeckOrderMode.SELECTED_ORDER;
        }
        return DeckOrderMode.valueOf(raw);
    }

    private void prepareWizardModel(Model model, User user, List<Long> deckIds, List<Long> fileIds, String error, HttpSession session) {
        prepareWizardModel(model, user, deckIds, fileIds, Map.of(), error, session);
    }

    @SuppressWarnings("unchecked")
    private void prepareWizardModel(Model model, User user, List<Long> deckIds, List<Long> fileIds,
                                    Map<Long, DocumentMode> pdfMode, String error, HttpSession session) {
        List<Long> normalizedDecks = normalizeIds(deckIds);
        List<Long> normalizedFiles = normalizeIds(fileIds);
        Map<Long, DocumentMode> safePdfMode = pdfMode == null ? Map.of() : pdfMode;

        model.addAttribute("deckGroups", folderService.getStudyFolderTree(user, normalizedDecks));
        model.addAttribute("quizSourceTree", folderService.getQuizSourceTree(user, normalizedDecks, normalizedFiles));
        model.addAttribute("preselectedDeckIds", normalizedDecks);
        model.addAttribute("preselectedFileIds", normalizedFiles);
        model.addAttribute("pdfMode", safePdfMode);
        model.addAttribute("studyError", error);
        model.addAttribute("sessionModes", SessionMode.values());
        model.addAttribute("deckOrderModes", DeckOrderMode.values());
        model.addAttribute("quizQuestionModes", QuizQuestionMode.values());
        model.addAttribute("difficulties", Difficulty.values());

        // Quiz character count logic
        Map<Long, Integer> charCache = (Map<Long, Integer>) session.getAttribute("quizFileCharCache");
        if (charCache == null) {
            charCache = new HashMap<>();
            session.setAttribute("quizFileCharCache", charCache);
        }

        long totalChars = 0;
        for (Long fileId : normalizedFiles) {
            try {
                FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                if (DocumentModeResolver.resolve(file, safePdfMode) == DocumentMode.FULL_PDF) {
                    continue;
                }
                if (charCache.containsKey(fileId)) {
                    totalChars += charCache.get(fileId);
                } else {
                    if (isPdf(file) && !documentExtractionService.isSupported(file)) {
                        throw new IllegalArgumentException("Please select a supported PDF under 10 MB.");
                    }
                    String text = requireExtractedText(file);
                    charCache.put(fileId, text.length());
                    totalChars += text.length();
                }
            } catch (Exception e) {
                log.debug("Skipping char-count for fileId={}: {}", fileId, e.getMessage());
            }
        }
        model.addAttribute("selectionTotalChars", totalChars);
        model.addAttribute("selectionWarn", totalChars >= 50_000);
        model.addAttribute("selectionExceedsCap", totalChars > 150_000);
    }

    private String handleError(StudyMode mode, List<Long> deckIds, List<Long> fileIds, Map<Long, DocumentMode> pdfMode,
                               Exception error, Model model, User user, HttpSession session,
                               HttpServletResponse response, String hxRequest) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        // An Ai generation failure means the request reached the provider after
        // quota was consumed, so the client still needs to refresh its quota widget.
        if (error instanceof AiGenerationException) {
            response.addHeader("HX-Trigger", "refresh-quota");
        }
        model.addAttribute("mode", mode);
        prepareWizardModel(model, user, deckIds, fileIds, pdfMode, error.getMessage(), session);
        model.addAttribute("aiErrorDetails", generationDetails(mode, error));
        if (hxRequest != null) return "fragments/study-setup :: studySetup";
        model.addAttribute("studyStateView", "setup");
        return "study-page";
    }

    private String generationDetails(StudyMode mode, Exception ex) {
        if (ex instanceof AiGenerationException aiEx && aiEx.diagnostics() != null) {
            return aiEx.diagnostics().toDisplayString();
        }
        String type = mode == null ? "STUDY" : mode.name();
        return AiGenerationDiagnostics.fromException(type, "REQUEST_VALIDATION", ex).toDisplayString();
    }

    private String delegateToFlashcards(Model model, User user, StudySessionState state, String hxRequest) {
        model.addAttribute("username", user.getUsername());
        model.addAttribute("mode", StudyMode.FLASHCARDS);
        if (studySessionService.isComplete(state)) {
            model.addAttribute("stats", studySessionService.buildStats(state));
            model.addAttribute("incorrectCardCount", state.incorrectCardIds().size());
            if (hxRequest != null) return "fragments/study-complete :: studyComplete";
            model.addAttribute("studyStateView", "complete");
            return "study-page";
        }
        StudyCardView currentCard = studySessionService.nextCard(state);
        model.addAttribute("state", state);
        model.addAttribute("currentCard", currentCard);
        model.addAttribute("currentCardNumber", state.currentIndex() + 1);
        model.addAttribute("totalCards", state.queue().size());
        model.addAttribute("isDeckByDeck", state.config().sessionMode() == SessionMode.DECK_BY_DECK);
        if (hxRequest != null) return "fragments/study-card :: studyCard";
        model.addAttribute("studyStateView", "card");
        return "study-page";
    }

    private String delegateToQuiz(Model model, QuizSessionState state, String hxRequest) {
        int idx = state.currentIndex();
        model.addAttribute("mode", StudyMode.QUIZ);
        model.addAttribute("currentQuestion", state.questions().get(idx));
        model.addAttribute("questionNumber", idx + 1);
        model.addAttribute("totalQuestions", state.questions().size());
        model.addAttribute("answered", false);
        if (hxRequest != null) return "fragments/quiz-question :: quizQuestion";
        model.addAttribute("studyStateView", "question");
        return "study-page";
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private boolean isPdf(FileEntry file) {
        String filename = file == null ? null : file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    private String requireExtractedText(FileEntry file) throws Exception {
        String text = documentExtractionService.extractText(file);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("This PDF has no extractable text. Try Full PDF mode.");
        }
        return text;
    }

    private List<Long> resolveOrderedSelection(List<Long> selected, String ordered, SessionMode mode) {
        List<Long> normalized = normalizeIds(selected);
        if (mode != SessionMode.DECK_BY_DECK || ordered == null || ordered.isBlank()) return normalized;
        List<Long> manual = Arrays.stream(ordered.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList();
        List<Long> resolved = new ArrayList<>(manual.stream().filter(normalized::contains).toList());
        normalized.stream().filter(id -> !resolved.contains(id)).forEach(resolved::add);
        return resolved;
    }
}
