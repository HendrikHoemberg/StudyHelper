package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.DashboardViewModel;
import com.HendrikHoemberg.StudyHelper.dto.DashboardViewModel.HeatmapEntry;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.DashboardService;
import com.HendrikHoemberg.StudyHelper.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Controller
public class DashboardController {

    private static final int WEEKS = 17;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final UserService userService;
    private final DashboardService dashboardService;

    public DashboardController(UserService userService, DashboardService dashboardService) {
        this.userService = userService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal,
                            Model model,
                            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DashboardViewModel vm = dashboardService.buildFor(user);
        model.addAttribute("vm", vm);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("greetingSubtitle", buildGreetingSubtitle(vm));
        model.addAttribute("heatmapColumns", bucketIntoColumns(vm.heatmap()));
        model.addAttribute("heatmapMonths", monthLabels(vm.heatmap()));
        if (hxRequest != null) {
            model.addAttribute("refreshSidebar", true);
            return "fragments/explorer :: dashboardContent";
        }
        return "dashboard";
    }

    static String buildGreetingSubtitle(DashboardViewModel vm) {
        boolean hasStreak = vm.streakDays() > 0;
        boolean hasDue = vm.dueTodayCount() > 0;
        if (hasStreak && hasDue) {
            return "You're on a " + vm.streakDays() + "-day streak — keep going with "
                + vm.dueTodayCount() + " cards still to master.";
        }
        if (hasDue) {
            return vm.dueTodayCount() + " cards still to master.";
        }
        if (hasStreak) {
            return vm.streakDays() + "-day streak going — pick a deck to keep it alive.";
        }
        return "What do you want to study today?";
    }

    static List<List<HeatmapEntry>> bucketIntoColumns(List<HeatmapEntry> heatmap) {
        List<List<HeatmapEntry>> columns = new ArrayList<>(WEEKS);
        for (int w = 0; w < WEEKS; w++) {
            List<HeatmapEntry> col = new ArrayList<>(7);
            for (int d = 0; d < 7; d++) {
                col.add(heatmap.get(w * 7 + d));
            }
            columns.add(col);
        }
        return columns;
    }

    static List<String> monthLabels(List<HeatmapEntry> heatmap) {
        // Up to 4 distinct months in display order (oldest → newest).
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (HeatmapEntry e : heatmap) {
            labels.add(e.date().format(MONTH));
        }
        List<String> out = new ArrayList<>(labels);
        while (out.size() > 4) {
            out.remove(0);
        }
        return out;
    }
}
