package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DungeonRevenantService {

    public record AdvanceResult(DungeonSessionState state, boolean contacted) {}

    public DungeonSessionState spawnFrom(DungeonSessionState state, String encounterId, String originRoomId) {
        Revenant rev = new Revenant(encounterId, originRoomId, originRoomId);
        int cap = state.config().size().revenantCap();
        if (state.revenants().size() < cap) {
            List<Revenant> next = new ArrayList<>(state.revenants());
            next.add(rev);
            return state.withRevenants(next);
        }
        List<Revenant> grave = new ArrayList<>(state.revenantGraveyard());
        grave.add(rev);
        return state.withRevenantGraveyard(grave);
    }

    public AdvanceResult advance(DungeonSessionState state) {
        if (state.revenants().isEmpty()) return new AdvanceResult(state, false);

        String playerRoom = state.currentRoomId();
        List<Revenant> moved = new ArrayList<>(state.revenants().size());
        for (Revenant rev : state.revenants()) {
            String next = DungeonGraph.nextStepToward(state.map(), rev.currentRoomId(), playerRoom);
            moved.add(rev.withCurrentRoomId(next));
        }
        DungeonSessionState advanced = state.withRevenants(moved);

        for (Revenant rev : moved) {
            if (rev.currentRoomId().equals(playerRoom)) {
                return new AdvanceResult(activateReTest(advanced, rev), true);
            }
        }
        return new AdvanceResult(advanced, false);
    }

    public DungeonSessionState resolveReTest(DungeonSessionState state, boolean correct) {
        String id = state.combat().activeRevenantId();
        if (id == null) return state;

        if (correct) {
            List<Revenant> remaining = new ArrayList<>();
            for (Revenant rev : state.revenants()) {
                if (!rev.encounterId().equals(id)) remaining.add(rev);
            }
            DungeonSessionState s = state
                .withRevenants(remaining)
                .withProgress(state.progress().recordRevenantMastered())
                .withResources(state.resources().addScore(DungeonBalance.REVENANT_MASTERY_SCORE))
                .withCombat(state.combat().withActiveEncounterId(null).withActiveRevenantId(null));
            return pullFromGraveyard(s);
        }

        DungeonSessionState bitten = RelicEffects.applyWrongAnswerDamage(
            state, DungeonBalance.WRONG_ANSWER_DAMAGE);
        List<Revenant> repelled = new ArrayList<>();
        for (Revenant rev : bitten.revenants()) {
            repelled.add(rev.encounterId().equals(id) ? rev.withCurrentRoomId(rev.originRoomId()) : rev);
        }
        return bitten
            .withRevenants(repelled)
            .withCombat(bitten.combat().withActiveEncounterId(null).withActiveRevenantId(null));
    }

    public boolean bossSealed(DungeonSessionState state) {
        return !state.revenants().isEmpty() || !state.revenantGraveyard().isEmpty();
    }

    private DungeonSessionState activateReTest(DungeonSessionState state, Revenant rev) {
        return state.withCombat(state.combat()
            .withActiveEncounterId(rev.encounterId())
            .withActiveRevenantId(rev.encounterId()));
    }

    private DungeonSessionState pullFromGraveyard(DungeonSessionState state) {
        if (state.revenantGraveyard().isEmpty()) return state;
        int cap = state.config().size().revenantCap();
        if (state.revenants().size() >= cap) return state;
        List<Revenant> grave = new ArrayList<>(state.revenantGraveyard());
        Revenant risen = grave.remove(0);
        List<Revenant> active = new ArrayList<>(state.revenants());
        active.add(risen);
        return state.withRevenants(active).withRevenantGraveyard(grave);
    }
}
