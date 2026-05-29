package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.util.WebUtils;

import java.util.function.Function;
import java.util.function.Supplier;

class DungeonControllerAccess {

    static final String DUNGEON_SESSION_KEY = "dungeonSessionState";

    static DungeonSessionState getState(HttpSession session, User user, SavedSessionService savedSessionService) {
        DungeonSessionState state = (DungeonSessionState) session.getAttribute(DUNGEON_SESSION_KEY);
        if (state == null) {
            state = savedSessionService.loadDungeon(user).orElse(null);
        }
        return state;
    }

    static <T> T withSession(HttpSession session, User user, SavedSessionService savedSessionService,
                             Function<DungeonSessionState, T> body) {
        synchronized (WebUtils.getSessionMutex(session)) {
            DungeonSessionState state = getState(session, user, savedSessionService);
            return body.apply(state);
        }
    }

    static <T> T withSessionLock(HttpSession session, Supplier<T> body) {
        synchronized (WebUtils.getSessionMutex(session)) {
            return body.get();
        }
    }

    static String stashAndRender(Model model, User user, HttpSession session,
                                  DungeonSessionState state, String hxRequest,
                                  SavedSessionService savedSessionService,
                                  StudyLogService studyLogService,
                                  DungeonViewModelBuilder viewModelBuilder,
                                  DungeonSessionService dungeonSessionService) {
        session.setAttribute(DUNGEON_SESSION_KEY, state);
        if (state.isComplete()) {
            savedSessionService.discard(user, false);
            DungeonRunStats stats = dungeonSessionService.buildStats(state);
            studyLogService.recordDungeon(user, stats, state.config().selectedDeckIds());
            viewModelBuilder.prepareComplete(model, state);
            if (hxRequest != null) {
                return "fragments/dungeon-complete :: dungeonComplete";
            }
            model.addAttribute("studyStateView", "dungeonComplete");
            return "study-page";
        }
        savedSessionService.saveDungeon(user, state);
        return renderGame(model, state, hxRequest, viewModelBuilder);
    }

    static String renderGame(Model model, DungeonSessionState state, String hxRequest,
                              DungeonViewModelBuilder viewModelBuilder) {
        model.addAttribute("mode", StudyMode.DUNGEON);
        model.addAttribute("studyStateView", "dungeon");
        viewModelBuilder.prepareGame(model, state);
        if (hxRequest != null) return "fragments/dungeon-game :: dungeonGame";
        model.addAttribute("studyStateView", "dungeon");
        return "study-page";
    }

    static String redirectToStart() {
        return "redirect:/study/start?mode=DUNGEON";
    }

    static String handleCollectResult(CollectResult result, User user, HttpSession session,
                                       SavedSessionService savedSessionService,
                                       HttpServletResponse response) {
        switch (result.status()) {
            case OK -> {
                session.setAttribute(DUNGEON_SESSION_KEY, result.state());
                savedSessionService.saveDungeon(user, result.state());
                return "success";
            }
            case DUPLICATE -> {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                return "";
            }
            default -> {
                return "";
            }
        }
    }

    private DungeonControllerAccess() {}
}
