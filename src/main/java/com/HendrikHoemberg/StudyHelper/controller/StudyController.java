package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.controller.ExamController;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.FileEntry;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.security.Principal;
import java.util.*;

@Controller
public class StudyController {

    private final StudySessionService studySessionService;
    private final AiQuizService aiQuizService;
    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final FolderService folderService;
    private final UserService userService;
    private final DocumentExtractionService documentExtractionService;
    private final FileEntryService fileEntryService;
    private final ExamController examController;

    public StudyController(StudySessionService studySessionService,
                           AiQuizService aiQuizService,
                           DeckService deckService,
                           FlashcardService flashcardService,
                           FolderService folderService,
                           UserService userService,
                           DocumentExtractionService documentExtractionService,
                           FileEntryService fileEntryService,
                           ExamController examController) {
        this.studySessionService = studySessionService;
        this.aiQuizService = aiQuizService;
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.folderService = folderService;
        this.userService = userService;
        this.documentExtractionService = documentExtractionService;
        this.fileEntryService = fileEntryService;
        this.examController = examController;
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
            } catch (NoSuchElementException ignored) {}
        }

        if (fileId != null) {
            try {
                FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                if (documentExtractionService.isSupported(file)) {
                    preselectedFileIds.add(fileId);
                }
            } catch (NoSuchElementException ignored) {}
        }

        if (folderId != null) {
            try {
                FolderService.FolderSources sources = folderService.getAllSourcesInFolder(folderId, user);
                preselectedDeckIds.addAll(sources.deckIds());
                preselectedFileIds.addAll(sources.fileIds());
            } catch (NoSuchElementException ignored) {}
        }

        model.addAttribute("mode", mode);
        prepareWizardModel(model, user, preselectedDeckIds, preselectedFileIds, null, session);

        if (hxRequest != null) {
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
                              Model model,
                              Principal principal,
                              HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        List<Long> decks = new ArrayList<>(normalizeIds(selectedDeckIds));
        List<Long> files = new ArrayList<>(normalizeIds(selectedFileIds));

        if (clearAll) {
            decks.clear();
            files.clear();
        } else if (removeId != null) {
            decks.remove(removeId);
        } else if (removeFileId != null) {
            files.remove(removeFileId);
        } else if (toggledFolderId != null) {
            FolderService.FolderSources sources = folderService.getAllSourcesInFolder(toggledFolderId, user);
            List<Long> folderDecks = sources.deckIds();
            List<Long> folderFiles = sources.fileIds();

            boolean allSelected = new HashSet<>(decks).containsAll(folderDecks) && 
                                 new HashSet<>(files).containsAll(folderFiles);

            if (allSelected && (!folderDecks.isEmpty() || !folderFiles.isEmpty())) {
                decks.removeAll(folderDecks);
                files.removeAll(folderFiles);
            } else {
                for (Long id : folderDecks) if (!decks.contains(id)) decks.add(id);
                for (Long id : folderFiles) if (!files.contains(id)) files.add(id);
            }
        }

        model.addAttribute("mode", mode);
        prepareWizardModel(model, user, decks, files, null, session);
        return "fragments/wizard-source-picker :: setupPicker";
    }

    @PostMapping("/study/session")
    public String createSession(@RequestParam StudyMode mode,
                                @RequestParam(required = false) List<Long> selectedDeckIds,
                                @RequestParam(required = false) List<Long> selectedFileIds,
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
                return handleError(mode, selectedDeckIds, selectedFileIds, ex.getMessage(), model, user, session, response, hxRequest);
            }
        } else if (mode == StudyMode.QUIZ) {
            try {
                List<Long> deckIds = normalizeIds(selectedDeckIds);
                List<Long> fileIds = normalizeIds(selectedFileIds);
                if (deckIds.isEmpty() && fileIds.isEmpty()) {
                    throw new IllegalArgumentException("Please select at least one deck or document.");
                }

                List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(deckIds, user);
                List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
                List<AiQuizService.DocumentInput> documents = new ArrayList<>();
                long totalChars = 0;
                for (Long fileId : fileIds) {
                    FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                    String text = documentExtractionService.extractText(file);
                    documents.add(new AiQuizService.DocumentInput(file.getOriginalFilename(), text));
                    totalChars += text.length();
                }

                if (totalChars > 150_000) throw new IllegalArgumentException("Selection too large — please deselect some sources.");

                int qCount = Math.max(1, Math.min(questionCount, 20));
                List<QuizQuestion> questions = aiQuizService.generate(flashcards, documents, qCount, quizQuestionMode, difficulty);

                QuizSessionState state = new QuizSessionState(
                    new QuizConfig(deckIds, fileIds, qCount, quizQuestionMode, difficulty),
                    questions, 0, new HashMap<>()
                );
                model.addAttribute("mode", mode);
                session.setAttribute("quizSessionState", state);
                return delegateToQuiz(model, state, hxRequest);
            } catch (Exception ex) {
                return handleError(mode, selectedDeckIds, selectedFileIds, ex.getMessage(), model, user, session, response, hxRequest);
            }
        } else if (mode == StudyMode.EXAM) {
            return examController.createSession(selectedDeckIds, selectedFileIds, questionSize, count, timerMinutes, layout, model, principal, session, response, hxRequest);
        }

        return "redirect:/study/start";
    }

    private void prepareWizardModel(Model model, User user, List<Long> deckIds, List<Long> fileIds, String error, HttpSession session) {
        List<Long> normalizedDecks = normalizeIds(deckIds);
        List<Long> normalizedFiles = normalizeIds(fileIds);

        model.addAttribute("deckGroups", folderService.getStudyFolderTree(user, normalizedDecks));
        model.addAttribute("quizSourceTree", folderService.getQuizSourceTree(user, normalizedDecks, normalizedFiles));
        model.addAttribute("preselectedDeckIds", normalizedDecks);
        model.addAttribute("preselectedFileIds", normalizedFiles);
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
            if (charCache.containsKey(fileId)) {
                totalChars += charCache.get(fileId);
            } else {
                try {
                    FileEntry file = fileEntryService.getByIdAndUser(fileId, user);
                    String text = documentExtractionService.extractText(file);
                    charCache.put(fileId, text.length());
                    totalChars += text.length();
                } catch (Exception ignored) {}
            }
        }
        model.addAttribute("selectionTotalChars", totalChars);
        model.addAttribute("selectionWarn", totalChars >= 50_000);
        model.addAttribute("selectionExceedsCap", totalChars > 150_000);
    }

    private String handleError(StudyMode mode, List<Long> deckIds, List<Long> fileIds, String error, Model model, User user, HttpSession session, HttpServletResponse response, String hxRequest) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("mode", mode);
        prepareWizardModel(model, user, deckIds, fileIds, error, session);
        if (hxRequest != null) return "fragments/study-setup :: studySetup";
        model.addAttribute("studyStateView", "setup");
        return "study-page";
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

    private List<Long> resolveOrderedSelection(List<Long> selected, String ordered, SessionMode mode) {
        List<Long> normalized = normalizeIds(selected);
        if (mode != SessionMode.DECK_BY_DECK || ordered == null || ordered.isBlank()) return normalized;
        List<Long> manual = Arrays.stream(ordered.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList();
        List<Long> resolved = new ArrayList<>(manual.stream().filter(normalized::contains).toList());
        normalized.stream().filter(id -> !resolved.contains(id)).forEach(resolved::add);
        return resolved;
    }
}
