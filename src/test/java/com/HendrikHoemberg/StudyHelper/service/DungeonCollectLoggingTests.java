package com.HendrikHoemberg.StudyHelper.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonCollectLoggingTests {

    private DungeonSessionService newService() {
        return new DungeonSessionService(null, null, null, null, null, null, null, new DungeonShrineService());
    }

    private DungeonSessionState stateWithPots(List<PotLoot> loot) {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.COMBAT,
            Map.of(), new GridPos(0, 0), true, false,
            "enc_0", List.of(), null, null, null, loot);
        DungeonMap map = new DungeonMap(Map.of("r0", r0), "r0", "r0", 3);
        return DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
                List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map).currentRoomId("r0")
            .encounters(Map.of()).bossEncounterIds(List.of())
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .build();
    }

    @Test
    void collectCoin_withFabricatedIndex_logsWarnAndRejects() {
        Logger logger = (Logger) LoggerFactory.getLogger(DungeonSessionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            DungeonSessionState state = stateWithPots(List.of(PotLoot.COIN));
            CollectResult result = newService().collectCoin(state, "r0_coin_999");

            assertThat(result.status()).isEqualTo(CollectStatus.REJECTED);
            assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("r0_coin_999"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
