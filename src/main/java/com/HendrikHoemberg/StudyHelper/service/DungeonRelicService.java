package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DungeonRelicService {

    public DungeonSessionState pick(DungeonSessionState state, RelicId relicId) {
        PendingRelicPick pick = state.loadout().pendingRelicPick();
        if (pick == null) return state;
        if (pick.type() == PendingPickType.SHOP) return state;
        if (!pick.offer().contains(relicId)) return state;
        return applyAcquired(state, relicId, true, 0);
    }

    public DungeonSessionState buy(DungeonSessionState state, RelicId relicId) {
        PendingRelicPick pick = state.loadout().pendingRelicPick();
        if (pick == null || pick.type() != PendingPickType.SHOP) return state;
        ShopOffer offer = pick.shopOffer();
        if (offer == null) return state;
        ShopOfferEntry entry = offer.entries().stream()
            .filter(e -> e.relic() == relicId).findFirst().orElse(null);
        if (entry == null) return state;
        if (state.resources().score() < entry.price()) return state;
        return applyAcquired(state, relicId, false, entry.price());
    }

    public DungeonSessionState skipShop(DungeonSessionState state) {
        PendingRelicPick pick = state.loadout().pendingRelicPick();
        if (pick == null || pick.type() != PendingPickType.SHOP) return state;
        return clearPickAndMarkRoomCleared(state, pick.roomId());
    }

    private DungeonSessionState applyAcquired(DungeonSessionState state, RelicId relicId,
                                                boolean clearPick, int deductScore) {
        DungeonLoadout nextLoadout = state.loadout().addRelic(relicId);
        DungeonResources nextResources = state.resources();

        switch (relicId) {
            case IRON_PLATE -> nextResources = nextResources
                .withHealthCap(nextResources.healthCap() + 1)
                .heal(1);
            case BUCKLER -> nextResources = nextResources
                .withShieldCap(nextResources.shieldCap() + 1)
                .addShield();
            default -> { }
        }

        if (deductScore != 0) {
            nextResources = nextResources.withScore(nextResources.score() - deductScore);
        }

        if (clearPick) {
            String roomId = state.loadout().pendingRelicPick().roomId();
            DungeonSessionState withRelic = state
                .withResources(nextResources)
                .withLoadout(nextLoadout.clearPendingPick());
            return markRoomCleared(withRelic, roomId);
        }
        return state.withResources(nextResources).withLoadout(nextLoadout);
    }

    private DungeonSessionState clearPickAndMarkRoomCleared(DungeonSessionState s, String roomId) {
        DungeonSessionState cleared = markRoomCleared(s, roomId);
        return cleared.withLoadout(cleared.loadout().clearPendingPick());
    }

    private DungeonSessionState markRoomCleared(DungeonSessionState s, String roomId) {
        DungeonRoom room = s.map().room(roomId);
        if (room == null) return s;
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(s.map().rooms());
        rooms.put(roomId, room.withCleared(true));
        DungeonMap nextMap = new DungeonMap(
            rooms, s.map().entranceRoomId(), s.map().bossRoomId(), s.map().lattice());
        return s.withMap(nextMap);
    }
}
