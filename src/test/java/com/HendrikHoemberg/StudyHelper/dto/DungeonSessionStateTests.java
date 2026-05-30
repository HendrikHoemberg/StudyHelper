package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class DungeonSessionStateTests {

    private DungeonSessionState minimal() {
        DungeonRoom entrance = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0), true, true,
            null, List.of(), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", entrance), "r0", "r0", 7);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return DungeonSessionState.builder()
            .config(config).map(map).currentRoomId("r0")
            .encounters(Map.of()).bossEncounterIds(List.of())
            .resources(new DungeonResources(5, 5, 0, 2, 0))
            .build();
    }

    @Test
    void newState_isNotCompleteByDefault() {
        DungeonSessionState s = minimal();
        assertThat(s.isComplete()).isFalse();
        assertThat(s.activeEncounter()).isNull();
        assertThat(s.currentRoom().id()).isEqualTo("r0");
        assertThat(s.loadout().ownedRelics()).isEmpty();
    }

    @Test
    void isComplete_trueWhenWonOrDefeated() {
        assertThat(minimal().withWon(true).isComplete()).isTrue();
        assertThat(minimal().withDefeated(true).isComplete()).isTrue();
    }

    @Test
    void withResources_replacesOnlyResourcesSlice() {
        DungeonSessionState s = minimal();
        DungeonResources next = s.resources().withHealth(2);
        DungeonSessionState after = s.withResources(next);
        assertThat(after.resources().health()).isEqualTo(2);
        assertThat(after.progress()).isSameAs(s.progress());
        assertThat(after.combat()).isSameAs(s.combat());
        assertThat(after.loadout()).isSameAs(s.loadout());
    }

    @Test
    void activeEncounter_resolvesViaCombatSlice() {
        DungeonSessionState s = minimal();
        DungeonEncounter enc = DungeonEncounter.flashcard("fc_0", false, "SLIME", 1L,
            "Front", "Back", null, null);
        DungeonSessionState live = s
            .withEncounters(Map.of("fc_0", enc))
            .withCombat(s.combat().withActiveEncounterId("fc_0"));
        assertThat(live.activeEncounter()).isEqualTo(enc);
    }

    @Test
    void newStateHasEmptyRevenantLists() {
        DungeonSessionState s = DungeonSessionState.builder().build();
        assertThat(s.revenants()).isEmpty();
        assertThat(s.revenantGraveyard()).isEmpty();
    }

    @Test
    void withRevenantsReplacesList() {
        DungeonSessionState s = DungeonSessionState.builder().build()
            .withRevenants(java.util.List.of(new Revenant("fc_1", "r2", "r2")));
        assertThat(s.revenants()).hasSize(1);
        assertThat(s.revenantGraveyard()).isEmpty();
    }

    @Test
    void toBuilder_roundTripsAllFields() {
        DungeonSessionState s = minimal()
            .withResources(new DungeonResources(3, 7, 1, 3, 50))
            .withProgress(new DungeonProgress(4, 3, 2, 5, 1, 0, 0, 0))
            .withCombat(new DungeonCombat("fc_1", List.of("fc_2"), 1, null))
            .withLoadout(new DungeonLoadout(List.of(RelicId.COMPASS), null));
        assertThat(s.toBuilder().build()).isEqualTo(s);
    }
}
