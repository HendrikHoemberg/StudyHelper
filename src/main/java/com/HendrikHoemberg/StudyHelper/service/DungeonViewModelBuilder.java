package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.*;

@Component
public class DungeonViewModelBuilder {

    private static final Logger log = LoggerFactory.getLogger(DungeonViewModelBuilder.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final FolderService folderService;
    private final DashboardService dashboardService;
    private final DungeonMapGenerator mapGenerator;
    private final DungeonSessionService sessionService;

    public DungeonViewModelBuilder(FolderService folderService,
                                   DashboardService dashboardService,
                                   DungeonMapGenerator mapGenerator,
                                   DungeonSessionService sessionService) {
        this.folderService = folderService;
        this.dashboardService = dashboardService;
        this.mapGenerator = mapGenerator;
        this.sessionService = sessionService;
    }

    public void prepareWizard(Model model, User user, List<Long> deckIds, String error) {
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

    public void prepareGame(Model model, DungeonSessionState state) {
        model.addAttribute("state", state);
        model.addAttribute("activeEncounter", state.activeEncounter());
        model.addAttribute("currentRoom", state.currentRoom());
        model.addAttribute("stats", sessionService.buildStats(state));
        model.addAttribute("ownedRelics", state.loadout().ownedRelics());
        model.addAttribute("pendingPick", state.loadout().pendingRelicPick());
        model.addAttribute("hintMaskIndex", spectaclesHintMaskIndex(state, state.activeEncounter()));

        List<MinimapRoom> minimap = mapGenerator.buildMinimap(state);
        model.addAttribute("minimapRooms", minimap);
        try {
            model.addAttribute("minimapRoomsJson", objectMapper.writeValueAsString(minimap));
        } catch (Exception e) {
            log.error("Failed to serialize minimap rooms", e);
            model.addAttribute("minimapRoomsJson", "[]");
        }

        int gauntletPos = 0;
        int gauntletTotal = 0;
        if (state.combat().activeEncounterId() != null) {
            for (DungeonRoom r : state.map().rooms().values()) {
                if (r.gauntletGroup().contains(state.combat().activeEncounterId())) {
                    gauntletTotal = r.gauntletGroup().size();
                    gauntletPos = r.gauntletGroup().indexOf(state.combat().activeEncounterId()) + 1;
                    break;
                }
            }
        }
        model.addAttribute("gauntletPosition", gauntletPos);
        model.addAttribute("gauntletTotal", gauntletTotal);
    }

    public void prepareComplete(Model model, DungeonSessionState state) {
        DungeonRunStats stats = sessionService.buildStats(state);
        model.addAttribute("mode", StudyMode.DUNGEON);
        model.addAttribute("stats", stats);
        model.addAttribute("state", state);
    }

    public void prepareShrineResult(Model model, DungeonSessionState state, int roll, RelicId grantedRelic) {
        model.addAttribute("state", state);
        model.addAttribute("rollResult", roll);
        model.addAttribute("grantedRelic", grantedRelic);
    }

    public static Integer spectaclesHintMaskIndex(DungeonSessionState state, DungeonEncounter encounter) {
        if (!state.loadout().ownedRelics().contains(RelicId.SPECTACLES)) return null;
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
}
