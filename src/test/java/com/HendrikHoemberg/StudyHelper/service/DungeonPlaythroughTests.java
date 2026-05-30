package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.support.DungeonStateInvariants;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration safety net: a deterministic random agent plays a full FLASHCARDS dungeon
 * through the real service orchestration. Catches integration bugs across the
 * move -> activate -> answer -> resolve -> clear -> pick pipeline that per-service unit
 * tests cannot reach. No Spring context or DB: the create-only deps of
 * DungeonSessionService are unused by move/answer/pick/shrine and are passed as null.
 */
class DungeonPlaythroughTests {

    private DungeonSessionService newService(long seed) {
        DungeonCombatService combat = new DungeonCombatService();
        DungeonRevenantService revenants = new DungeonRevenantService();
        return new DungeonSessionService(
            null, null, null,
            new DungeonMapGenerator(),
            new DungeonNavigationService(),
            new DungeonEncounterService(combat, revenants),
            new DungeonRelicService(),
            new DungeonShrineService(new Random(seed)),
            revenants);
    }

    /** Builds an initial state mirroring DungeonSessionService.buildEncounters + initialState. */
    private DungeonSessionState initialState(DungeonSize size, long seed) {
        int normalCount = size.normalEncounterCount();
        int bossCount = size.bossPromptCount();
        int groups = size.eliteGauntletCount();
        int perGroup = size.cardsPerEliteGauntlet();

        List<String> normalIds = new ArrayList<>();
        for (int i = 0; i < normalCount; i++) normalIds.add("fc_" + i);

        List<List<String>> eliteGroups = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            List<String> group = new ArrayList<>();
            for (int c = 0; c < perGroup; c++) group.add("fc_elite_" + g + "_" + c);
            eliteGroups.add(group);
        }

        List<String> bossIds = new ArrayList<>();
        for (int i = 0; i < bossCount; i++) bossIds.add("fc_boss_" + i);

        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>();
        long fid = 1;
        for (String id : normalIds) {
            encounters.put(id, DungeonEncounter.flashcard(id, false, "SLIME", fid++, "Q", "A", null, null));
        }
        for (List<String> group : eliteGroups) {
            for (String id : group) {
                encounters.put(id, DungeonEncounter.flashcard(id, false, "CHAMPION", fid++, "Q", "A", null, null));
            }
        }
        for (String id : bossIds) {
            encounters.put(id, DungeonEncounter.flashcard(id, true, "DRAGON", fid++, "Q", "A", null, null));
        }

        DungeonMap map = new DungeonMapGenerator().generate(size, normalIds, eliteGroups, new Random(seed));

        return DungeonSessionState.builder()
            .config(new DungeonConfig(DungeonMode.FLASHCARDS, size, List.of(1L),
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""))
            .map(map).currentRoomId(map.entranceRoomId())
            .encounters(encounters).bossEncounterIds(bossIds)
            .resources(new DungeonResources(
                DungeonBalance.INITIAL_HEALTH, DungeonBalance.INITIAL_HEALTH_CAP, 0,
                DungeonBalance.INITIAL_SHIELD_CAP, 0))
            .build();
    }

    @Test
    void alwaysCorrectAgentReachesWin_forEverySizeAndSeed() {
        for (DungeonSize size : DungeonSize.values()) {
            for (long seed : new long[]{1L, 2L, 7L, 42L}) {
                DungeonSessionService svc = newService(seed);
                DungeonSessionState state = play(svc, initialState(size, seed), true, seed);
                assertThat(state.won())
                    .as("always-correct agent wins size=%s seed=%d", size, seed)
                    .isTrue();
                assertThat(state.defeated()).isFalse();
            }
        }
    }

    @Test
    void alwaysWrongAgentReachesDefeat_forEverySizeAndSeed() {
        for (DungeonSize size : DungeonSize.values()) {
            for (long seed : new long[]{1L, 2L, 7L, 42L}) {
                DungeonSessionService svc = newService(seed);
                DungeonSessionState state = play(svc, initialState(size, seed), false, seed);
                assertThat(state.defeated())
                    .as("always-wrong agent is defeated size=%s seed=%d", size, seed)
                    .isTrue();
                assertThat(state.won()).isFalse();
            }
        }
    }

    @Test
    void movingAdvancesRoamerTowardPlayer() {
        DungeonSessionService svc = newService(7L);
        DungeonSessionState s = initialState(DungeonSize.SMALL, 7L);
        String entrance = s.map().entranceRoomId();
        // pick a neighbor room that won't set a pending relic pick
        String neighbor = null;
        DungeonDirection dir = null;
        for (Map.Entry<DungeonDirection, String> door : s.map().room(entrance).doors().entrySet()) {
            RoomType type = s.map().room(door.getValue()).type();
            if (type != RoomType.SHOP && type != RoomType.TREASURE && type != RoomType.SHRINE) {
                neighbor = door.getValue();
                dir = door.getKey();
                break;
            }
        }
        assertThat(neighbor).isNotNull();
        s = s.withRevenants(List.of(new Revenant("fc_0", neighbor, neighbor)));
        DungeonSessionState moved = svc.move(s, dir).state();
        DungeonStateInvariants.assertValid(moved);
        boolean acted = moved.combat().activeRevenantId() != null
            || !moved.revenants().get(0).currentRoomId().equals(neighbor);
        assertThat(acted).isTrue();
    }

    /** Runs the agent loop until terminal or the step budget is exhausted. */
    private DungeonSessionState play(DungeonSessionService svc, DungeonSessionState start,
                                     boolean answerCorrectly, long seed) {
        DungeonSessionState s = start;
        int budget = s.map().rooms().size() * 50;
        for (int step = 0; step < budget && !s.isComplete(); step++) {
            s = act(svc, s, answerCorrectly);
            DungeonStateInvariants.assertValid(s);
            assertReachable(s);
        }
        return s;
    }

    private DungeonSessionState act(DungeonSessionService svc, DungeonSessionState s, boolean answerCorrectly) {
        if (s.combat().activeEncounterId() != null) {
            return svc.answerFlashcard(s, answerCorrectly);
        }
        PendingRelicPick pick = s.loadout().pendingRelicPick();
        if (pick != null) {
            switch (pick.type()) {
                case TREASURE, ELITE -> {
                    return svc.pickRelic(s, pick.offer().get(0));
                }
                case SHOP -> {
                    ShopOfferEntry first = pick.shopOffer().entries().get(0);
                    return s.resources().score() >= first.price()
                        ? svc.buyRelic(s, first.relic())
                        : svc.skipShop(s);
                }
                case SHRINE -> {
                    return svc.shrineLeave(s);
                }
            }
        }
        DungeonDirection dir = answerCorrectly
            ? stepToward(s, s.map().bossRoomId())
            : loseStep(s);
        if (dir == null) dir = anyOpenDoor(s);
        if (dir == null) return s;
        return svc.move(s, dir).state();
    }

    /** BFS over doors; returns the first-hop direction from the current room toward target. */
    private DungeonDirection stepToward(DungeonSessionState s, String target) {
        if (target == null) return null;
        String start = s.currentRoomId();
        if (start.equals(target)) return null;
        Map<String, String> parent = new HashMap<>();
        Map<String, DungeonDirection> dirFromParent = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        parent.put(start, start);
        queue.add(start);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            for (Map.Entry<DungeonDirection, String> e : s.map().room(node).doors().entrySet()) {
                String next = e.getValue();
                if (parent.containsKey(next)) continue;
                parent.put(next, node);
                dirFromParent.put(next, e.getKey());
                if (next.equals(target)) {
                    String cur = next;
                    while (!parent.get(cur).equals(start)) cur = parent.get(cur);
                    return dirFromParent.get(cur);
                }
                queue.add(next);
            }
        }
        return null;
    }

    /**
     * Lose-agent movement: head to the nearest uncleared COMBAT/ELITE room WITHOUT ever
     * entering the boss room. A wrong boss answer can advance/win the boss, so the boss is
     * never targeted nor traversed. The ELITE never clears under wrong answers (the gauntlet
     * resets), so it is an inexhaustible damage source that guarantees eventual defeat.
     */
    private DungeonDirection loseStep(DungeonSessionState s) {
        String boss = s.map().bossRoomId();
        String start = s.currentRoomId();

        // Standing in an uncleared elite: step to a non-boss neighbor so we can re-enter and
        // re-trigger the gauntlet next iteration (each re-entry costs 1 wrong-answer damage).
        DungeonRoom here = s.map().room(start);
        if (!here.cleared() && here.type() == RoomType.ELITE) {
            return nonBossDoor(here, boss);
        }

        Map<String, String> parent = new HashMap<>();
        Map<String, DungeonDirection> dirFromParent = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        parent.put(start, start);
        queue.add(start);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            DungeonRoom room = s.map().room(node);
            if (!node.equals(start) && !room.cleared()
                    && (room.type() == RoomType.COMBAT || room.type() == RoomType.ELITE)) {
                String cur = node;
                while (!parent.get(cur).equals(start)) cur = parent.get(cur);
                return dirFromParent.get(cur);
            }
            for (Map.Entry<DungeonDirection, String> e : room.doors().entrySet()) {
                String next = e.getValue();
                if (next.equals(boss) || parent.containsKey(next)) continue;
                parent.put(next, node);
                dirFromParent.put(next, e.getKey());
                queue.add(next);
            }
        }
        return null;
    }

    private DungeonDirection nonBossDoor(DungeonRoom room, String bossRoomId) {
        for (Map.Entry<DungeonDirection, String> e : room.doors().entrySet()) {
            if (!e.getValue().equals(bossRoomId)) return e.getKey();
        }
        return null;
    }

    private DungeonDirection anyOpenDoor(DungeonSessionState s) {
        Iterator<DungeonDirection> it = s.map().room(s.currentRoomId()).doors().keySet().iterator();
        return it.hasNext() ? it.next() : null;
    }

    @Test
    void buildStatsExposesCardsMastered() {
        DungeonSessionService svc = newService(1L);
        DungeonSessionState s = initialState(DungeonSize.SMALL, 1L)
            .withProgress(new DungeonProgress(0, 0, 0, 0, 0, 0, 0, 4));
        DungeonRunStats stats = svc.buildStats(s);
        assertThat(stats.cardsMastered()).isEqualTo(4);
    }

    private void assertReachable(DungeonSessionState s) {
        String start = s.map().entranceRoomId();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            for (String next : s.map().room(queue.poll()).doors().values()) {
                if (seen.add(next)) queue.add(next);
            }
        }
        assertThat(seen).as("current room %s reachable from entrance", s.currentRoomId())
            .contains(s.currentRoomId());
    }
}
