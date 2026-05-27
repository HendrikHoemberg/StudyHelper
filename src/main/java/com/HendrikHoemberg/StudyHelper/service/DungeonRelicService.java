package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DungeonRelicService {

    public DungeonSessionState pick(DungeonSessionState state, RelicId relicId) {
        PendingRelicPick pick = state.pendingRelicPick();
        if (pick == null) return state;
        if (pick.type() == PendingPickType.SHOP) return state;
        if (!pick.offer().contains(relicId)) return state;
        return applyAcquired(state, relicId, true, 0);
    }

    public DungeonSessionState buy(DungeonSessionState state, RelicId relicId) {
        PendingRelicPick pick = state.pendingRelicPick();
        if (pick == null || pick.type() != PendingPickType.SHOP) return state;
        ShopOffer offer = pick.shopOffer();
        if (offer == null) return state;
        ShopOfferEntry entry = offer.entries().stream()
            .filter(e -> e.relic() == relicId).findFirst().orElse(null);
        if (entry == null) return state;
        if (state.score() < entry.price()) return state;
        return applyAcquired(state, relicId, false, entry.price());
    }

    public DungeonSessionState skipShop(DungeonSessionState state) {
        PendingRelicPick pick = state.pendingRelicPick();
        if (pick == null || pick.type() != PendingPickType.SHOP) return state;
        return clearPickAndMarkRoomCleared(state, pick.roomId());
    }

    private DungeonSessionState applyAcquired(DungeonSessionState state, RelicId relicId,
                                                boolean clearPick, int deductScore) {
        List<RelicId> owned = new ArrayList<>(state.ownedRelics());
        owned.add(relicId);

        int healthCap = state.healthCap();
        int shieldCap = state.shieldCap();
        int health = state.health();
        int shields = state.shields();

        switch (relicId) {
            case IRON_PLATE -> {
                healthCap += 1;
                health = Math.min(healthCap, health + 1);
            }
            case BUCKLER -> {
                shieldCap += 1;
                shields = Math.min(shieldCap, shields + 1);
            }
            default -> { }
        }

        int score = state.score() - deductScore;
        PendingRelicPick newPick = clearPick ? null : state.pendingRelicPick();
        DungeonSessionState rebuilt = new DungeonSessionState(
            state.config(), state.map(), state.currentRoomId(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            health, healthCap, shields, shieldCap,
            score, state.answeredCount(), state.correctCount(),
            state.won(), state.defeated(),
            state.streak(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
            state.luckyCoinsConsumed(), owned, newPick);

        if (clearPick) {
            String roomId = state.pendingRelicPick().roomId();
            return clearRoomById(rebuilt, roomId);
        }
        return rebuilt;
    }

    private DungeonSessionState clearPickAndMarkRoomCleared(DungeonSessionState s, String roomId) {
        DungeonSessionState cleared = clearRoomById(s, roomId);
        return new DungeonSessionState(
            cleared.config(), cleared.map(), cleared.currentRoomId(),
            cleared.encounters(), cleared.bossEncounterIds(), cleared.bossIndex(),
            cleared.activeEncounterId(),
            cleared.health(), cleared.healthCap(), cleared.shields(), cleared.shieldCap(),
            cleared.score(), cleared.answeredCount(), cleared.correctCount(),
            cleared.won(), cleared.defeated(),
            cleared.streak(), cleared.gauntletQueue(),
            cleared.longestStreak(), cleared.elitesCleared(), cleared.shieldsUsed(),
            cleared.luckyCoinsConsumed(), cleared.ownedRelics(), null);
    }

    private DungeonSessionState clearRoomById(DungeonSessionState s, String roomId) {
        DungeonRoom room = s.map().room(roomId);
        if (room == null) return s;
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(s.map().rooms());
        rooms.put(roomId, room.withCleared(true));
        DungeonMap nextMap = new DungeonMap(
            rooms, s.map().entranceRoomId(), s.map().bossRoomId(), s.map().lattice());
        return new DungeonSessionState(
            s.config(), nextMap, s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }
}
