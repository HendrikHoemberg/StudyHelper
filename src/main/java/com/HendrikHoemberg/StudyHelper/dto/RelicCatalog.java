package com.HendrikHoemberg.StudyHelper.dto;

import java.util.*;

public final class RelicCatalog {

    private static final Map<RelicId, RelicDefinition> DEFS;
    private static final Map<RelicPool, List<RelicId>> BY_POOL;

    static {
        Map<RelicId, RelicDefinition> defs = new EnumMap<>(RelicId.class);
        defs.put(RelicId.IRON_PLATE, def(RelicId.IRON_PLATE, "iron-plate", RelicPool.COMMON));
        defs.put(RelicId.BUCKLER, def(RelicId.BUCKLER, "buckler", RelicPool.COMMON));
        defs.put(RelicId.LUCKY_CHARM, def(RelicId.LUCKY_CHARM, "lucky-charm", RelicPool.COMMON));
        defs.put(RelicId.SHARP_FOCUS, def(RelicId.SHARP_FOCUS, "sharp-focus", RelicPool.COMMON));
        defs.put(RelicId.COMPASS, def(RelicId.COMPASS, "compass", RelicPool.COMMON));
        defs.put(RelicId.LUCKY_COIN, def(RelicId.LUCKY_COIN, "lucky-coin", RelicPool.COMMON));
        defs.put(RelicId.PHOENIX_FEATHER, def(RelicId.PHOENIX_FEATHER, "phoenix-feather", RelicPool.ELITE));
        defs.put(RelicId.SPECTACLES, def(RelicId.SPECTACLES, "spectacles", RelicPool.ELITE));
        defs.put(RelicId.WAR_BANNER, def(RelicId.WAR_BANNER, "war-banner", RelicPool.ELITE));
        defs.put(RelicId.MAP_SENSE, def(RelicId.MAP_SENSE, "map-sense", RelicPool.SHOP_EXCLUSIVE));
        DEFS = Collections.unmodifiableMap(defs);

        Map<RelicPool, List<RelicId>> byPool = new EnumMap<>(RelicPool.class);
        for (RelicPool p : RelicPool.values()) byPool.put(p, new ArrayList<>());
        for (RelicDefinition d : DEFS.values()) byPool.get(d.pool()).add(d.id());
        for (RelicPool p : RelicPool.values()) byPool.put(p, List.copyOf(byPool.get(p)));
        BY_POOL = Collections.unmodifiableMap(byPool);
    }

    private RelicCatalog() {}

    public static RelicDefinition definition(RelicId id) {
        return DEFS.get(id);
    }

    public static List<RelicId> idsInPool(RelicPool pool) {
        return BY_POOL.get(pool);
    }

    public static List<RelicId> sample(RelicPool pool, int count, Random rng) {
        List<RelicId> shuffled = new ArrayList<>(BY_POOL.get(pool));
        Collections.shuffle(shuffled, rng);
        return List.copyOf(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    public static List<RelicId> sampleFromUnion(List<RelicPool> pools, int count, Random rng) {
        List<RelicId> union = new ArrayList<>();
        for (RelicPool p : pools) union.addAll(BY_POOL.get(p));
        Collections.shuffle(union, rng);
        return List.copyOf(union.subList(0, Math.min(count, union.size())));
    }

    private static RelicDefinition def(RelicId id, String slug, RelicPool pool) {
        String base = "dungeon.relic." + slug;
        return new RelicDefinition(id, base + ".name", base + ".desc", pool);
    }
}
