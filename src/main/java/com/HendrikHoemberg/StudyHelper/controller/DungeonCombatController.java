package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
public class DungeonCombatController {

    private final DungeonSessionService dungeonSessionService;
    private final SavedSessionService savedSessionService;
    private final StudyLogService studyLogService;
    private final UserService userService;
    private final DungeonViewModelBuilder viewModelBuilder;

    public DungeonCombatController(DungeonSessionService dungeonSessionService,
                                    SavedSessionService savedSessionService,
                                    StudyLogService studyLogService,
                                    UserService userService,
                                    DungeonViewModelBuilder viewModelBuilder) {
        this.dungeonSessionService = dungeonSessionService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
        this.userService = userService;
        this.viewModelBuilder = viewModelBuilder;
    }

    @PostMapping("/dungeon/move")
    public String move(@RequestParam DungeonDirection direction,
                       Model model,
                       Principal principal,
                       HttpSession session,
                       HttpServletResponse response,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = DungeonControllerAccess.getState(session, user, savedSessionService);
        if (state == null) return DungeonControllerAccess.redirectToStart();
        ActionResult result = dungeonSessionService.move(state, direction);
        if (result instanceof ActionResult.Failure failure) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader("HX-Trigger", "dungeon-action-error");
            model.addAttribute("actionError", failure.message());
            return DungeonControllerAccess.renderGame(model, failure.state(), hxRequest, viewModelBuilder);
        }
        return DungeonControllerAccess.stashAndRender(model, user, session, result.state(), hxRequest,
            savedSessionService, studyLogService, viewModelBuilder, dungeonSessionService);
    }

    @PostMapping("/dungeon/answer/flashcard")
    public String answerFlashcard(@RequestParam boolean gotIt,
                                  Model model,
                                  Principal principal,
                                  HttpSession session,
                                  @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = DungeonControllerAccess.getState(session, user, savedSessionService);
        if (state == null) return DungeonControllerAccess.redirectToStart();
        state = dungeonSessionService.answerFlashcard(state, gotIt);
        return DungeonControllerAccess.stashAndRender(model, user, session, state, hxRequest,
            savedSessionService, studyLogService, viewModelBuilder, dungeonSessionService);
    }

    @PostMapping("/dungeon/answer/quiz")
    public String answerQuiz(@RequestParam(required = false) List<Integer> selectedOptions,
                             Model model,
                             Principal principal,
                             HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = DungeonControllerAccess.getState(session, user, savedSessionService);
        if (state == null) return DungeonControllerAccess.redirectToStart();
        state = dungeonSessionService.answerQuiz(state, selectedOptions);
        return DungeonControllerAccess.stashAndRender(model, user, session, state, hxRequest,
            savedSessionService, studyLogService, viewModelBuilder, dungeonSessionService);
    }
}
