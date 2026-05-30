package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.support.TestStates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DamagePipelineTests {

    @Test
    void luckyCoinAbsorbsBeforeShield() {
        DungeonSessionState s = TestStates.minimal()
            .withLoadout(new DungeonLoadout(List.of(RelicId.LUCKY_COIN), null))
            .withResources(new DungeonResources(3, 5, 2, 2, 0))
            .withProgress(new DungeonProgress(0, 0, 0, 0, 0, 0, 0));
        DungeonSessionState r = RelicEffects.applyWrongAnswerDamage(s, DungeonBalance.WRONG_ANSWER_DAMAGE);
        assertThat(r.progress().luckyCoinsConsumed()).isEqualTo(1);
        assertThat(r.resources().shields()).isEqualTo(2); // shield not spent
        assertThat(r.resources().health()).isEqualTo(3);  // no HP lost
    }

    @Test
    void shieldAbsorbsWhenNoLuckyCoin() {
        DungeonSessionState s = TestStates.minimal()
            .withResources(new DungeonResources(3, 5, 1, 2, 0));
        DungeonSessionState r = RelicEffects.applyWrongAnswerDamage(s, DungeonBalance.WRONG_ANSWER_DAMAGE);
        assertThat(r.resources().shields()).isEqualTo(0);
        assertThat(r.resources().health()).isEqualTo(3);
        assertThat(r.progress().shieldsUsed()).isEqualTo(1);
    }

    @Test
    void healthDamageWhenNothingAbsorbs() {
        DungeonSessionState s = TestStates.minimal()
            .withResources(new DungeonResources(3, 5, 0, 2, 0));
        DungeonSessionState r = RelicEffects.applyWrongAnswerDamage(s, DungeonBalance.WRONG_ANSWER_DAMAGE);
        assertThat(r.resources().health()).isEqualTo(2);
        assertThat(r.defeated()).isFalse();
    }

    @Test
    void lethalWithoutPhoenix_marksDefeated() {
        DungeonSessionState s = TestStates.minimal()
            .withResources(new DungeonResources(1, 5, 0, 2, 0));
        DungeonSessionState r = RelicEffects.applyWrongAnswerDamage(s, DungeonBalance.WRONG_ANSWER_DAMAGE);
        assertThat(r.resources().health()).isEqualTo(0);
        assertThat(r.defeated()).isTrue();
    }

    @Test
    void lethalWithPhoenix_revivesAndConsumesFeather() {
        DungeonSessionState s = TestStates.minimal()
            .withLoadout(new DungeonLoadout(List.of(RelicId.PHOENIX_FEATHER), null))
            .withResources(new DungeonResources(1, 5, 0, 2, 0));
        DungeonSessionState r = RelicEffects.applyWrongAnswerDamage(s, DungeonBalance.WRONG_ANSWER_DAMAGE);
        assertThat(r.resources().health()).isEqualTo(1);
        assertThat(r.defeated()).isFalse();
        assertThat(r.loadout().ownedRelics()).doesNotContain(RelicId.PHOENIX_FEATHER);
    }
}
