package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public record DungeonLoadout(
    List<RelicId> ownedRelics,
    PendingRelicPick pendingRelicPick
) implements Serializable {

    public DungeonLoadout {
        ownedRelics = ownedRelics == null ? List.of() : List.copyOf(ownedRelics);
    }

    public static DungeonLoadout empty() {
        return new DungeonLoadout(List.of(), null);
    }

    public DungeonLoadout withOwnedRelics(List<RelicId> relics) {
        return new DungeonLoadout(relics, pendingRelicPick);
    }
    public DungeonLoadout withPendingRelicPick(PendingRelicPick pick) {
        return new DungeonLoadout(ownedRelics, pick);
    }

    public DungeonLoadout addRelic(RelicId rid) {
        List<RelicId> next = new ArrayList<>(ownedRelics);
        next.add(rid);
        return new DungeonLoadout(next, pendingRelicPick);
    }

    public DungeonLoadout clearPendingPick() {
        return withPendingRelicPick(null);
    }

    public DungeonLoadout consumePhoenixFeather() {
        if (!ownedRelics.contains(RelicId.PHOENIX_FEATHER)) return this;
        List<RelicId> next = new ArrayList<>(ownedRelics);
        next.remove(RelicId.PHOENIX_FEATHER);
        return new DungeonLoadout(next, pendingRelicPick);
    }
}
