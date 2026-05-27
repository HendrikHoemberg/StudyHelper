package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.util.*;

@Controller
public class DungeonController {

    private static final Logger log = LoggerFactory.getLogger(DungeonController.class);
    private static final String DUNGEON_SESSION_KEY = "dungeonSessionState";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DungeonSessionService dungeonSessionService;
    private final SavedSessionService savedSessionService;
    private final StudyLogService studyLogService;
    private final UserService userService;
    private final FolderService folderService;
    private final DashboardService dashboardService;

    public DungeonController(DungeonSessionService dungeonSessionService,
                             SavedSessionService savedSessionService,
                             StudyLogService studyLogService,
                             UserService userService,
                             FolderService folderService,
                             DashboardService dashboardService) {
        this.dungeonSessionService = dungeonSessionService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
        this.userService = userService;
        this.folderService = folderService;
        this.dashboardService = dashboardService;
    }

    @PostMapping("/dungeon/start")
    public String start(@RequestParam DungeonMode dungeonMode,
                        @RequestParam DungeonSize dungeonSize,
                        @RequestParam(required = false) List<Long> selectedDeckIds,
                        @RequestParam(defaultValue = "MCQ_ONLY") QuizQuestionMode quizQuestionMode,
                        @RequestParam(defaultValue = "MEDIUM") Difficulty difficulty,
                        @RequestParam(required = false) String additionalInstructions,
                        @RequestParam(name = "confirmDiscard", defaultValue = "false") boolean confirmDiscard,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Model model,
                        Principal principal,
                        HttpSession session,
                        @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());

        if (!confirmDiscard) {
            var existing = savedSessionService.findForUser(user);
            if (existing.isPresent()) {
                model.addAttribute("savedSession", existing.get());
                model.addAttribute("startNewMode", StudyMode.DUNGEON);
                model.addAttribute("studyWizardCancelUrl", "/dashboard");
                response.setStatus(HttpServletResponse.SC_OK);
                response.setHeader("HX-Retarget", "#modal-placeholder");
                response.setHeader("HX-Reswap", "innerHTML");
                return "fragments/saved-session :: conflict";
            }
        }

        try {
            DungeonSessionState state;
            if (dungeonMode == DungeonMode.FLASHCARDS) {
                state = dungeonSessionService.createFlashcardDungeon(selectedDeckIds, dungeonSize, user);
            } else {
                state = dungeonSessionService.createAiQuizDungeon(
                    selectedDeckIds, dungeonSize, quizQuestionMode, difficulty,
                    additionalInstructions, request, user);
                response.addHeader("HX-Trigger", "refresh-quota");
            }
            savedSessionService.discard(user);
            session.setAttribute(DUNGEON_SESSION_KEY, state);
            savedSessionService.saveDungeon(user, state);
            return prepareGame(model, state, hxRequest);
        } catch (IllegalArgumentException ex) {
            return handleStartError(model, user, dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, ex, session, response, hxRequest);
        } catch (Exception ex) {
            return handleStartError(model, user, dungeonMode, dungeonSize, selectedDeckIds,
                quizQuestionMode, difficulty, additionalInstructions, ex, session, response, hxRequest);
        }
    }

    @GetMapping("/dungeon/resume")
    public String resume(Model model,
                         Principal principal,
                         HttpSession session,
                         RedirectAttributes redirectAttributes,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        Optional<DungeonSessionState> loaded = savedSessionService.loadDungeon(user);
        if (loaded.isEmpty()) {
            if (savedSessionService.consumeIncompatibleDiscardFlag(user)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Your saved Dungeon was from an older version and could not be resumed.");
            }
            return "redirect:/study/start?mode=DUNGEON";
        }

        DungeonSessionState state = loaded.get();
        if (state.config().mode() == DungeonMode.FLASHCARDS) {
            SavedSessionService.ReconcileDungeonResult result =
                savedSessionService.reconcileDungeonFlashcards(state, user);
            if (!result.canContinue()) {
                DungeonRunStats stats = dungeonSessionService.buildStats(result.state());
                studyLogService.recordDungeonAbandoned(user, stats, state.config().selectedDeckIds());
                savedSessionService.discard(user, false);
                redirectAttributes.addFlashAttribute("errorMessage",
                    dungeonResumeDiscardMessage(result.removedCount()));
                return "redirect:/study/start?mode=DUNGEON";
            }
            state = result.state();
        }

        session.setAttribute(DUNGEON_SESSION_KEY, state);
        savedSessionService.saveDungeon(user, state);
        return prepareGame(model, state, hxRequest);
    }

    @PostMapping("/dungeon/move")
    public String move(@RequestParam DungeonDirection direction,
                       Model model,
                       Principal principal,
                       HttpSession session,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) {
            return "redirect:/study/start?mode=DUNGEON";
        }
        state = dungeonSessionService.move(state, direction);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/answer/flashcard")
    public String answerFlashcard(@RequestParam boolean gotIt,
                                  Model model,
                                  Principal principal,
                                  HttpSession session,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) {
            return "redirect:/study/start?mode=DUNGEON";
        }
        state = dungeonSessionService.answerFlashcard(state, gotIt);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/answer/quiz")
    public String answerQuiz(@RequestParam(required = false) List<Integer> selectedOptions,
                             Model model,
                             Principal principal,
                             HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) {
            return "redirect:/study/start?mode=DUNGEON";
        }
        state = dungeonSessionService.answerQuiz(state, selectedOptions);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/relic/pick")
    public String pickRelic(@RequestParam RelicId relicId,
                              Model model, Principal principal, HttpSession session,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        state = dungeonSessionService.pickRelic(state, relicId);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/relic/buy")
    public String buyRelic(@RequestParam RelicId relicId,
                             Model model, Principal principal, HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        state = dungeonSessionService.buyRelic(state, relicId);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/relic/skip-shop")
    public String skipShop(Model model, Principal principal, HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        state = dungeonSessionService.skipShop(state);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/shrine/confirm")
    public String shrineConfirm(Model model, Principal principal, HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        model.addAttribute("state", state);
        return "fragments/dungeon-shrine-modal :: shrineConfirm";
    }

    @PostMapping("/dungeon/shrine/reset")
    public String shrineReset(Model model, Principal principal, HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        model.addAttribute("state", state);
        return "fragments/dungeon-shrine-modal :: shrineMain";
    }

    @PostMapping("/dungeon/shrine/roll")
    public String shrineRoll(Model model, Principal principal, HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";

        int roll = new Random().nextInt(6) + 1;
        RelicId grantedRelic = null;
        if (roll == 6) {
            List<RelicId> unowned = new ArrayList<>();
            for (RelicId r : RelicId.values()) {
                if (!state.ownedRelics().contains(r)) unowned.add(r);
            }
            if (!unowned.isEmpty()) {
                grantedRelic = unowned.get(new Random().nextInt(unowned.size()));
            } else {
                grantedRelic = RelicId.IRON_PLATE;
            }
        }

        DungeonSessionState nextState = dungeonSessionService.shrineRoll(state, roll, grantedRelic);
        session.setAttribute(DUNGEON_SESSION_KEY, nextState);
        savedSessionService.saveDungeon(user, nextState);

        model.addAttribute("state", nextState);
        model.addAttribute("rollResult", roll);
        model.addAttribute("grantedRelic", grantedRelic);

        return "fragments/dungeon-shrine-modal :: shrineResult";
    }

    @PostMapping("/dungeon/shrine/drink")
    public String shrineDrink(Model model, Principal principal, HttpSession session,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";

        DungeonSessionState nextState = dungeonSessionService.shrineDrink(state);
        return stashAndRender(model, user, session, nextState, hxRequest);
    }

    @PostMapping("/dungeon/shrine/leave")
    public String shrineLeave(Model model, Principal principal, HttpSession session,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";

        DungeonSessionState nextState = dungeonSessionService.shrineLeave(state);
        return stashAndRender(model, user, session, nextState, hxRequest);
    }

    @PostMapping("/dungeon/collect/coin")
    @ResponseBody
    public String collectCoin(HttpSession session, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "";

        DungeonSessionState nextState = new DungeonSessionState(
            state.config(), state.map(), state.currentRoomId(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            state.health(), state.healthCap(), state.shields(), state.shieldCap(),
            state.score() + 1, state.answeredCount(), state.correctCount(),
            state.won(), state.defeated(),
            state.streak(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
            state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());

        session.setAttribute(DUNGEON_SESSION_KEY, nextState);
        savedSessionService.saveDungeon(user, nextState);
        return "success";
    }

    @PostMapping("/dungeon/collect/shield")
    @ResponseBody
    public String collectShield(HttpSession session, Principal principal) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "";

        int nextShields = Math.min(state.shieldCap(), state.shields() + 1);
        DungeonSessionState nextState = new DungeonSessionState(
            state.config(), state.map(), state.currentRoomId(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            state.health(), state.healthCap(), nextShields, state.shieldCap(),
            state.score(), state.answeredCount(), state.correctCount(),
            state.won(), state.defeated(),
            state.streak(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
            state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());

        session.setAttribute(DUNGEON_SESSION_KEY, nextState);
        savedSessionService.saveDungeon(user, nextState);
        return "success";
    }

    private String stashAndRender(Model model, User user, HttpSession session,
                                   DungeonSessionState state, String hxRequest) {
        session.setAttribute(DUNGEON_SESSION_KEY, state);
        if (state.isComplete()) {
            savedSessionService.discard(user, false);
            DungeonRunStats stats = dungeonSessionService.buildStats(state);
            studyLogService.recordDungeon(user, stats, state.config().selectedDeckIds());
            model.addAttribute("mode", StudyMode.DUNGEON);
            model.addAttribute("stats", stats);
            model.addAttribute("state", state);
            if (hxRequest != null) {
                return "fragments/dungeon-complete :: dungeonComplete";
            }
            model.addAttribute("studyStateView", "dungeonComplete");
            return "study-page";
        }
        savedSessionService.saveDungeon(user, state);
        return prepareGame(model, state, hxRequest);
    }

    private DungeonSessionState getState(HttpSession session, User user) {
        DungeonSessionState state = (DungeonSessionState) session.getAttribute(DUNGEON_SESSION_KEY);
        if (state == null) {
            state = savedSessionService.loadDungeon(user).orElse(null);
        }
        return state;
    }

    private String handleStartError(Model model, User user, DungeonMode dungeonMode, DungeonSize dungeonSize,
                                     List<Long> selectedDeckIds, QuizQuestionMode quizQuestionMode,
                                     Difficulty difficulty, String additionalInstructions,
                                     Exception ex, HttpSession session, HttpServletResponse response, String hxRequest) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        if (ex instanceof AiGenerationException || ex.getCause() instanceof AiGenerationException
            || ex instanceof AiQuotaExceededException || ex.getCause() instanceof AiQuotaExceededException) {
            response.addHeader("HX-Trigger", "refresh-quota");
        }
        model.addAttribute("mode", StudyMode.DUNGEON);
        model.addAttribute("dungeonMode", dungeonMode);
        model.addAttribute("dungeonSize", dungeonSize);
        model.addAttribute("selectedDeckIds", selectedDeckIds == null ? List.of() : selectedDeckIds);
        model.addAttribute("quizQuestionMode", quizQuestionMode);
        model.addAttribute("difficulty", difficulty);
        model.addAttribute("additionalInstructions", additionalInstructions == null ? "" : additionalInstructions);

        prepareWizardModel(model, user, selectedDeckIds, ex.getMessage(), session);
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("aiErrorDetails", generationDetails(ex));

        if (hxRequest != null) {
            return "fragments/study-setup :: studySetup";
        }
        model.addAttribute("studyStateView", "setup");
        return "study-page";
    }

    private void prepareWizardModel(Model model, User user, List<Long> deckIds, String error, HttpSession session) {
        List<Long> normalizedDecks = StudySourceSupport.normalizeIds(deckIds);

        model.addAttribute("deckGroups", folderService.getStudyFolderTree(user, normalizedDecks));
        model.addAttribute("preselectedDeckIds", normalizedDecks);
        model.addAttribute("preselectedFileIds", List.of());
        model.addAttribute("pdfMode", Map.of());
        model.addAttribute("studyError", error);
        model.addAttribute("sessionModes", SessionMode.values());
        model.addAttribute("deckOrderModes", DeckOrderMode.values());
        model.addAttribute("quizQuestionModes", QuizQuestionMode.values());
        model.addAttribute("difficulties", Difficulty.values());
        model.addAttribute("dungeonSmallMinCards", DungeonSize.SMALL.totalPrompts());
        model.addAttribute("dungeonMediumMinCards", DungeonSize.MEDIUM.totalPrompts());
        model.addAttribute("dungeonLargeMinCards", DungeonSize.LARGE.totalPrompts());

        long dueTodaySessionCount = dashboardService.buildFor(user).dueTodaySessionCount();
        model.addAttribute("dueTodaySessionCount", dueTodaySessionCount);

        model.addAttribute("selectionTotalChars", 0L);
        model.addAttribute("selectionWarn", false);
        model.addAttribute("selectionExceedsCap", false);
    }

    private String generationDetails(Exception ex) {
        if (ex instanceof AiGenerationException aiEx && aiEx.diagnostics() != null) {
            return aiEx.diagnostics().toDisplayString();
        }
        return AiGenerationDiagnostics.fromException("DUNGEON", "REQUEST_VALIDATION", ex).toDisplayString();
    }

    private String prepareGame(Model model, DungeonSessionState state, String hxRequest) {
        model.addAttribute("mode", StudyMode.DUNGEON);
        model.addAttribute("studyStateView", "dungeon");
        model.addAttribute("state", state);
        model.addAttribute("activeEncounter", state.activeEncounter());
        model.addAttribute("currentRoom", state.currentRoom());
        model.addAttribute("stats", dungeonSessionService.buildStats(state));
        model.addAttribute("ownedRelics", state.ownedRelics());
        model.addAttribute("pendingPick", state.pendingRelicPick());
        model.addAttribute("hintMaskIndex",
            spectaclesHintMaskIndex(state, state.activeEncounter()));

        List<Map<String, Object>> minimap = minimapRooms(state);
        model.addAttribute("minimapRooms", minimap);
        try {
            model.addAttribute("minimapRoomsJson", objectMapper.writeValueAsString(minimap));
        } catch (Exception e) {
            log.error("Failed to serialize minimap rooms", e);
            model.addAttribute("minimapRoomsJson", "[]");
        }

        int gauntletPos = 0;
        int gauntletTotal = 0;
        if (state.activeEncounterId() != null) {
            for (DungeonRoom r : state.map().rooms().values()) {
                if (r.gauntletGroup().contains(state.activeEncounterId())) {
                    gauntletTotal = r.gauntletGroup().size();
                    gauntletPos = r.gauntletGroup().indexOf(state.activeEncounterId()) + 1;
                    break;
                }
            }
        }
        model.addAttribute("gauntletPosition", gauntletPos);
        model.addAttribute("gauntletTotal", gauntletTotal);

        if (hxRequest != null) return "fragments/dungeon-game :: dungeonGame";
        model.addAttribute("studyStateView", "dungeon");
        return "study-page";
    }

    /**
     * Per-room display data for the minimap:
     *   { id, type, gridX, gridY, visible (bool), revealedType (bool), cleared (bool), isCurrent (bool),
     *     doors: { "UP": neighborId | null, ... } }
     *
     * Visibility rules:
     *   - Visited rooms: visible = true, revealedType = true.
     *   - Neighbors of visited (via door): visible = true, revealedType only if COMPASS owned.
     *   - SECRET room: visible only if MAP_SENSE owned OR both host rooms visited.
     *   - Everything else: visible = false (excluded from the response).
     */
    List<Map<String, Object>> minimapRooms(DungeonSessionState state) {
        boolean hasCompass = state.ownedRelics().contains(RelicId.COMPASS);
        boolean hasMapSense = state.ownedRelics().contains(RelicId.MAP_SENSE);

        Set<String> visitedIds = new HashSet<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            if (r.visited()) visitedIds.add(r.id());
        }
        Set<String> adjacentToVisited = new HashSet<>();
        for (String id : visitedIds) {
            DungeonRoom r = state.map().room(id);
            for (String neighbor : r.doors().values()) {
                if (!visitedIds.contains(neighbor)) adjacentToVisited.add(neighbor);
            }
        }

        // Secret room reveal
        Set<String> secretRevealed = new HashSet<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            if (r.type() != RoomType.SECRET) continue;
            if (hasMapSense) {
                secretRevealed.add(r.id());
                continue;
            }
            // Reveal when both lattice-neighbor hosts have been visited
            int hostsVisited = 0;
            int hostsTotal = 0;
            for (DungeonRoom maybeHost : state.map().rooms().values()) {
                if (maybeHost.id().equals(r.id())) continue;
                GridPos a = r.gridPos();
                GridPos b = maybeHost.gridPos();
                if (Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) == 1) {
                    hostsTotal++;
                    if (visitedIds.contains(maybeHost.id())) hostsVisited++;
                }
            }
            if (hostsTotal > 0 && hostsVisited == hostsTotal) secretRevealed.add(r.id());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            boolean isVisited = visitedIds.contains(r.id());
            boolean isAdjacent = adjacentToVisited.contains(r.id());
            boolean isSecret = r.type() == RoomType.SECRET;
            boolean visible = isVisited || isAdjacent || (isSecret && secretRevealed.contains(r.id()));
            if (!visible) continue;

            boolean revealedType = isVisited
                || (isAdjacent && hasCompass)
                || (isSecret && secretRevealed.contains(r.id()));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id());
            m.put("type", revealedType ? r.type().name() : "UNKNOWN");
            m.put("gridX", r.gridPos().x());
            m.put("gridY", r.gridPos().y());
            m.put("visited", isVisited);
            m.put("cleared", r.cleared());
            m.put("isCurrent", r.id().equals(state.currentRoomId()));
            Map<String, String> doors = new LinkedHashMap<>();
            for (Map.Entry<DungeonDirection, String> e : r.doors().entrySet()) {
                doors.put(e.getKey().name(), e.getValue());
            }
            m.put("doors", doors);
            result.add(m);
        }
        return result;
    }

    static Integer spectaclesHintMaskIndex(DungeonSessionState state, DungeonEncounter encounter) {
        if (!state.ownedRelics().contains(RelicId.SPECTACLES)) return null;
        if (encounter == null) return null;
        if (encounter.type() != DungeonEncounterType.QUIZ
            && encounter.type() != DungeonEncounterType.BOSS_QUIZ) return null;
        QuizQuestion q = encounter.quizQuestion();
        if (q == null) return null;
        Set<Integer> correctSet = new HashSet<>(q.correctOptionIndices());
        List<Integer> wrongIndices = new ArrayList<>();
        for (int i = 0; i < q.options().size(); i++) {
            if (!correctSet.contains(i)) wrongIndices.add(i);
        }
        if (wrongIndices.isEmpty()) return null;
        int seed = Math.abs(Objects.hash(encounter.id()));
        return wrongIndices.get(seed % wrongIndices.size());
    }

    private String dungeonResumeDiscardMessage(int removedCount) {
        return "Your saved Dungeon run could not continue because "
            + removedCount + " flashcard(s) are no longer available.";
    }
}
