package com.HendrikHoemberg.StudyHelper.render;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.service.DungeonMapGenerator;
import com.HendrikHoemberg.StudyHelper.service.DungeonViewModelBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DungeonFragmentRenderTests {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private DungeonViewModelBuilder viewModelBuilder;

    @Autowired
    private DungeonMapGenerator mapGenerator;

    private DungeonSessionState sampleState() {
        List<String> normalIds = IntStream.range(0, DungeonSize.SMALL.normalEncounterCount())
            .mapToObj(i -> "enc_" + i).toList();
        DungeonMap map = mapGenerator.generate(
            DungeonSize.SMALL, normalIds, List.of(List.of("e1_0", "e1_1")));
        return DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
                List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map).currentRoomId(map.entranceRoomId())
            .encounters(java.util.Map.of()).bossEncounterIds(List.of("boss_0"))
            .resources(new DungeonResources(5, 5, 1, 2, 120))
            .loadout(new DungeonLoadout(List.of(RelicId.IRON_PLATE, RelicId.MAP_SENSE), null))
            .build();
    }

    private String render(String templateName, Model model) {
        Context ctx = new Context(LocaleContextHolder.getLocale(), model.asMap());
        return templateEngine.process(templateName, ctx);
    }

    @Test
    void gameFragmentRendersWithoutError() {
        Model model = new ExtendedModelMap();
        viewModelBuilder.prepareGame(model, sampleState());
        String html = render("fragments/dungeon-game", model);
        assertThat(html).contains("dungeon-room-canvas");
    }

    @Test
    void completeFragmentRendersWithoutError() {
        DungeonSessionState won = sampleState().withWon(true);
        Model model = new ExtendedModelMap();
        viewModelBuilder.prepareComplete(model, won);
        String html = render("fragments/dungeon-complete", model);
        assertThat(html).isNotBlank();
    }
}
