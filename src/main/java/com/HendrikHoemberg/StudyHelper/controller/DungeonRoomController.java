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

@Controller
public class DungeonRoomController {

    private final DungeonSessionService dungeonSessionService;
    private final SavedSessionService savedSessionService;
    private final StudyLogService studyLogService;
    private final UserService userService;
    private final DungeonViewModelBuilder viewModelBuilder;
    private final DungeonShrineService shrineService;

    public DungeonRoomController(DungeonSessionService dungeonSessionService,
                                  SavedSessionService savedSessionService,
                                  StudyLogService studyLogService,
                                  UserService userService,
                                  DungeonViewModelBuilder viewModelBuilder,
                                  DungeonShrineService shrineService) {
        this.dungeonSessionService = dungeonSessionService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
        this.userService = userService;
        this.viewModelBuilder = viewModelBuilder;
        this.shrineService = shrineService;
    }

    @PostMapping("/dungeon/relic/pick")
    public String pickRelic(@RequestParam RelicId relicId,
                            Model model, Principal principal, HttpSession session,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            if (state == null) return DungeonControllerAccess.redirectToStart();
            DungeonSessionState next = dungeonSessionService.pickRelic(state, relicId);
            return DungeonControllerAccess.stashAndRender(model, user, session, next, hxRequest,
                savedSessionService, studyLogService, viewModelBuilder, dungeonSessionService);
        });
    }

    @PostMapping("/dungeon/relic/buy")
    public String buyRelic(@RequestParam RelicId relicId,
                           Model model, Principal principal, HttpSession session,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            if (state == null) return DungeonControllerAccess.redirectToStart();
            DungeonSessionState next = dungeonSessionService.buyRelic(state, relicId);
            return DungeonControllerAccess.stashAndRender(model, user, session, next, hxRequest,
                savedSessionService, studyLogService, viewModelBuilder, dungeonSessionService);
        });
    }

    @PostMapping("/dungeon/relic/skip-shop")
    public String skipShop(Model model, Principal principal, HttpSession session,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            if (state == null) return DungeonControllerAccess.redirectToStart();
            DungeonSessionState next = dungeonSessionService.skipShop(state);
            return DungeonControllerAccess.stashAndRender(model, user, session, next, hxRequest,
                savedSessionService, studyLogService, viewModelBuilder, dungeonSessionService);
        });
    }

    @PostMapping("/dungeon/shrine/confirm")
    public String shrineConfirm(Model model, Principal principal, HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            if (state == null) return DungeonControllerAccess.redirectToStart();
            model.addAttribute("state", state);
            return "fragments/dungeon-shrine-modal :: shrineConfirm";
        });
    }

    @PostMapping("/dungeon/shrine/reset")
    public String shrineReset(Model model, Principal principal, HttpSession session) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            if (state == null) return DungeonControllerAccess.redirectToStart();
            model.addAttribute("state", state);
            return "fragments/dungeon-shrine-modal :: shrineMain";
        });
    }

    @PostMapping("/dungeon/shrine/roll")
    public String shrineRoll(Model model, Principal principal, HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            if (state == null) return DungeonControllerAccess.redirectToStart();
            DungeonShrineService.ShrineRollOutcome outcome = shrineService.computeOutcome(state);
            DungeonSessionState nextState = shrineService.applyRoll(state, outcome);
            session.setAttribute(DungeonControllerAccess.DUNGEON_SESSION_KEY, nextState);
            savedSessionService.saveDungeon(user, nextState);
            viewModelBuilder.prepareShrineResult(model, nextState, outcome.roll(), outcome.grantedRelic());
            return "fragments/dungeon-shrine-modal :: shrineResult";
        });
    }

    @PostMapping("/dungeon/shrine/drink")
    public String shrineDrink(Model model, Principal principal, HttpSession session,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            if (state == null) return DungeonControllerAccess.redirectToStart();
            DungeonSessionState next = shrineService.drink(state);
            return DungeonControllerAccess.stashAndRender(model, user, session, next, hxRequest,
                savedSessionService, studyLogService, viewModelBuilder, dungeonSessionService);
        });
    }

    @PostMapping("/dungeon/shrine/leave")
    public String shrineLeave(Model model, Principal principal, HttpSession session,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            if (state == null) return DungeonControllerAccess.redirectToStart();
            DungeonSessionState next = shrineService.leave(state);
            return DungeonControllerAccess.stashAndRender(model, user, session, next, hxRequest,
                savedSessionService, studyLogService, viewModelBuilder, dungeonSessionService);
        });
    }

    @PostMapping("/dungeon/collect/coin")
    @ResponseBody
    public String collectCoin(@RequestParam(required = false) String itemId,
                              HttpSession session, Principal principal,
                              HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            CollectResult result = dungeonSessionService.collectCoin(state, itemId);
            return DungeonControllerAccess.handleCollectResult(result, user, session, savedSessionService, response);
        });
    }

    @PostMapping("/dungeon/collect/shield")
    @ResponseBody
    public String collectShield(@RequestParam(required = false) String itemId,
                                HttpSession session, Principal principal,
                                HttpServletResponse response) {
        User user = userService.getByUsername(principal.getName());
        return DungeonControllerAccess.withSession(session, user, savedSessionService, state -> {
            CollectResult result = dungeonSessionService.collectShield(state, itemId);
            return DungeonControllerAccess.handleCollectResult(result, user, session, savedSessionService, response);
        });
    }

}
