package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonResources;
import com.HendrikHoemberg.StudyHelper.dto.DungeonRoom;
import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.RelicId;

import java.util.*;

public final class RelicEffects {

    private static final Map<RelicId, RelicEffect> EFFECTS;
    private static final List<DamageMitigator> MITIGATORS;
    private static final List<DeathSave> DEATH_SAVES;
    private static final Set<RelicId> DAMAGE_RELICS;
    private static final Set<RelicId> VIEW_ONLY =
        EnumSet.of(RelicId.SPECTACLES, RelicId.MAP_SENSE, RelicId.COMPASS);

    static {
        Map<RelicId, RelicEffect> effects = new EnumMap<>(RelicId.class);
        effects.put(RelicId.IRON_PLATE, new IronPlateEffect());
        effects.put(RelicId.BUCKLER, new BucklerEffect());
        effects.put(RelicId.WAR_BANNER, new WarBannerEffect());
        effects.put(RelicId.SHARP_FOCUS, new SharpFocusEffect());
        effects.put(RelicId.LUCKY_CHARM, new LuckyCharmEffect());
        EFFECTS = Collections.unmodifiableMap(effects);

        List<DamageMitigator> mitigators = new ArrayList<>();
        mitigators.add(new LuckyCoinMitigator());
        mitigators.add(new ShieldMitigator());
        mitigators.sort(Comparator.comparingInt(DamageMitigator::priority));
        MITIGATORS = List.copyOf(mitigators);

        DEATH_SAVES = List.of(new PhoenixFeatherDeathSave());
        DAMAGE_RELICS = EnumSet.of(RelicId.LUCKY_COIN, RelicId.PHOENIX_FEATHER);
    }

    private RelicEffects() {}

    public static DungeonSessionState onAcquire(DungeonSessionState s, RelicId id) {
        RelicEffect e = EFFECTS.get(id);
        return e == null ? s : e.onAcquire(s);
    }

    public static DungeonSessionState onRoomClear(DungeonSessionState s, DungeonRoom room) {
        for (RelicId id : s.loadout().ownedRelics()) {
            RelicEffect e = EFFECTS.get(id);
            if (e != null) s = e.onRoomClear(s, room);
        }
        return s;
    }

    public static int streakThreshold(DungeonSessionState s, int base) {
        int result = base;
        for (RelicId id : s.loadout().ownedRelics()) {
            RelicEffect e = EFFECTS.get(id);
            if (e != null) result = e.modifyStreakThreshold(result);
        }
        return result;
    }

    public static int bonusScoreOnCorrect(DungeonSessionState s) {
        int bonus = 0;
        for (RelicId id : s.loadout().ownedRelics()) {
            RelicEffect e = EFFECTS.get(id);
            if (e != null) bonus += e.bonusScoreOnCorrect();
        }
        return bonus;
    }

    public static DungeonSessionState applyWrongAnswerDamage(DungeonSessionState s, int damage) {
        for (DamageMitigator m : MITIGATORS) {
            Optional<DungeonSessionState> absorbed = m.tryAbsorb(s, damage);
            if (absorbed.isPresent()) return absorbed.get();
        }
        DungeonResources damaged = s.resources().takeHealthDamage(damage);
        DungeonSessionState afterDamage = s.withResources(damaged);
        if (damaged.health() == 0) {
            for (DeathSave ds : DEATH_SAVES) {
                Optional<DungeonSessionState> saved = ds.trySave(afterDamage);
                if (saved.isPresent()) return saved.get();
            }
            return afterDamage.withDefeated(true);
        }
        return afterDamage;
    }

    public static boolean isClassified(RelicId id) {
        return EFFECTS.containsKey(id) || DAMAGE_RELICS.contains(id) || VIEW_ONLY.contains(id);
    }
}
