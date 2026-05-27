# Dungeon Room-Graph Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the tile-grid dungeon with a Binding-of-Isaac-style room graph: each room is one screen with one purpose, with a reveal-on-enter minimap and a score-as-currency relic system.

**Architecture:** A graph of `DungeonRoom`s connected by doors replaces the tile/fog grid. Existing service split (`DungeonNavigationService`, `DungeonEncounterService`, `DungeonDamage`, `DungeonSessionService` orchestrator) survives; their internals are rewritten. New `DungeonRelicService` owns relic acquisition; relic passive effects hook into existing chokepoints (`processAnswer`, `takeDamage`, minimap renderer).

**Tech Stack:** Spring Boot 3 + Thymeleaf + HTMX, Java 21 records for DTOs, AssertJ + JUnit 5 for tests, vanilla JS canvas in `dungeon.js`, server-rendered model state.

**Spec:** `docs/superpowers/specs/2026-05-27-dungeon-room-graph-redesign-design.md`

---

## File structure

**New files (Java DTOs):**
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicId.java` — enum of all 10 relic ids
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicPool.java` — enum {COMMON, ELITE, SHOP_EXCLUSIVE}
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicDefinition.java` — record (id, name key, desc key, pool)
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RoomType.java` — enum {ENTRANCE, COMBAT, ELITE, TREASURE, HEAL, SHOP, BOSS, SECRET}
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/GridPos.java` — record (x, y) for minimap layout
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/TreasureOffer.java` — record (List<RelicId>)
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/ShopOfferEntry.java` — record (RelicId, int price)
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/ShopOffer.java` — record (List<ShopOfferEntry>, ShopConsumable consumable)
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/ShopConsumable.java` — record (String i18nKey, int price, int healAmount); null if absent
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/SecretReward.java` — sealed with two variants: RelicReward, BundleReward (heal + shield + score)
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/PendingRelicPick.java` — record (PendingPickType type, String roomId, List<RelicId> offer, ShopOffer shopOffer); shopOffer non-null for SHOP
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/PendingPickType.java` — enum {TREASURE, ELITE, SHOP, SECRET}
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonRoom.java` — replaces DungeonTile semantics; see Task 4
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicCatalog.java` — static-init catalog with `definition(RelicId)` and `randomFromPool(...)`

**New files (Java services + relics):**
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonRelicService.java` — pick/buy/skipShop + apply-on-acquire effects
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/relic/RelicHooks.java` — central place for the small hook functions (no Spring bean — static helpers)

**Rewritten files:**
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonMap.java` — graph shape (rooms map, entrance/boss room ids, lattice size)
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSessionState.java` — drop `playerPosition`/`visibleTiles`; add `currentRoomId`/`healthCap`/`shieldCap`/`ownedRelics`/`pendingRelicPick`
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGenerator.java` — random-walk room placement
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationService.java` — door-based `move`
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java` — activation by roomId; relic-pick on Elite clear
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java` — orchestrate the new flow
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java` — relic hooks (Lucky Coin, Phoenix Feather); per-state `healthCap`
- `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DungeonController.java` — relic endpoints; new model attributes
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonRunStats.java` — add `relicsAcquired`
- `src/main/resources/templates/fragments/dungeon-game.html` — minimap + room view + relic shelf + modal mounts
- `src/main/resources/static/js/dungeon.js` — drop tile renderer; add minimap + room renderer; keep JRPG combat
- `src/main/resources/static/css/dungeon.css` — layout adjustments
- `src/main/resources/templates/fragments/dungeon-complete.html` — relics-found row
- `src/main/resources/messages.properties` — new keys
- `src/main/resources/messages_de.properties` — new keys (German)

**Deleted files:**
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonPosition.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTile.java`
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTileType.java`

**New Thymeleaf fragments:**
- `src/main/resources/templates/fragments/dungeon-treasure-modal.html`
- `src/main/resources/templates/fragments/dungeon-shop-modal.html`
- `src/main/resources/templates/fragments/dungeon-elite-reward-modal.html`
- `src/main/resources/templates/fragments/dungeon-secret-modal.html`

**New / rewritten tests:**
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGeneratorTests.java` — graph invariants
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationServiceTests.java` — door-based movement
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterServiceTests.java` — roomId activation + pendingRelicPick
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionServiceTests.java` — orchestration
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java` — relic hooks
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonRelicServiceTests.java` (new)
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonRelicEffectTests.java` (new)
- `src/test/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSessionStateTests.java` — new fields
- `src/test/java/com/HendrikHoemberg/StudyHelper/controller/DungeonControllerTests.java` — relic endpoints
- DELETE: `src/test/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSizeTests.java` — keep, no change needed (size totals unchanged)

---

## Conventions used in this plan

- Java 21 records for all DTOs. Records implementing `Serializable` use `implements Serializable`.
- Tests live next to their target class under `src/test/java/.../service/` or `.../dto/`.
- Commit messages follow conventional commits: `feat(dungeon): ...`, `refactor(dungeon): ...`, `test(dungeon): ...`.
- Run the full Maven test suite at major checkpoints: `./mvnw test -Dtest='Dungeon*'`.
- Where a test asserts on a generated random layout, always pass a seeded `Random` so assertions are deterministic.

---

## Task 1: Add Relic value types (no wiring)

These are pure data classes with no behavior. Add them up-front so later tasks can reference the types.

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicId.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicPool.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicDefinition.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicCatalog.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/dto/RelicCatalogTests.java`

- [ ] **Step 1: Create `RelicId` enum**

```java
package com.HendrikHoemberg.StudyHelper.dto;

public enum RelicId {
    IRON_PLATE,
    BUCKLER,
    LUCKY_CHARM,
    SHARP_FOCUS,
    COMPASS,
    LUCKY_COIN,
    PHOENIX_FEATHER,
    SPECTACLES,
    WAR_BANNER,
    MAP_SENSE
}
```

- [ ] **Step 2: Create `RelicPool` enum**

```java
package com.HendrikHoemberg.StudyHelper.dto;

public enum RelicPool {
    COMMON,
    ELITE,
    SHOP_EXCLUSIVE
}
```

- [ ] **Step 3: Create `RelicDefinition` record**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record RelicDefinition(
    RelicId id,
    String nameKey,   // i18n key for display name
    String descKey,   // i18n key for description
    RelicPool pool
) implements Serializable {}
```

- [ ] **Step 4: Write failing test for `RelicCatalog`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

class RelicCatalogTests {

    @Test
    void definition_returnsRelicForEveryId() {
        for (RelicId id : RelicId.values()) {
            RelicDefinition def = RelicCatalog.definition(id);
            assertThat(def).isNotNull();
            assertThat(def.id()).isEqualTo(id);
            assertThat(def.nameKey()).startsWith("dungeon.relic.");
            assertThat(def.descKey()).startsWith("dungeon.relic.");
        }
    }

    @Test
    void poolMembership_isExclusive() {
        assertThat(RelicCatalog.idsInPool(RelicPool.COMMON))
            .containsExactlyInAnyOrder(
                RelicId.IRON_PLATE, RelicId.BUCKLER, RelicId.LUCKY_CHARM,
                RelicId.SHARP_FOCUS, RelicId.COMPASS, RelicId.LUCKY_COIN);
        assertThat(RelicCatalog.idsInPool(RelicPool.ELITE))
            .containsExactlyInAnyOrder(
                RelicId.PHOENIX_FEATHER, RelicId.SPECTACLES, RelicId.WAR_BANNER);
        assertThat(RelicCatalog.idsInPool(RelicPool.SHOP_EXCLUSIVE))
            .containsExactlyInAnyOrder(RelicId.MAP_SENSE);
    }

    @Test
    void sample_returnsDistinctRelicsFromPool() {
        List<RelicId> picks = RelicCatalog.sample(RelicPool.COMMON, 3, new Random(42));
        assertThat(picks).hasSize(3).doesNotHaveDuplicates();
        assertThat(picks).allMatch(id ->
            RelicCatalog.definition(id).pool() == RelicPool.COMMON);
    }

    @Test
    void sample_seedIsDeterministic() {
        List<RelicId> a = RelicCatalog.sample(RelicPool.COMMON, 3, new Random(42));
        List<RelicId> b = RelicCatalog.sample(RelicPool.COMMON, 3, new Random(42));
        assertThat(a).isEqualTo(b);
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./mvnw test -Dtest=RelicCatalogTests`
Expected: compile failure (`RelicCatalog` not found).

- [ ] **Step 6: Implement `RelicCatalog`**

```java
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
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./mvnw test -Dtest=RelicCatalogTests`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicId.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicPool.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicDefinition.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/RelicCatalog.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/dto/RelicCatalogTests.java
git commit -m "feat(dungeon): add relic value types and catalog (no wiring)"
```

---

## Task 2: Add room-graph value types (alongside old tile types)

Old `DungeonPosition`/`DungeonTile`/`DungeonTileType` still exist. We add the new types next to them. Big swap happens in Task 4.

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/RoomType.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/GridPos.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/TreasureOffer.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/ShopOfferEntry.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/ShopOffer.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/ShopConsumable.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/SecretReward.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/PendingPickType.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/PendingRelicPick.java`
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonRoom.java`

These are pure records. No unit tests (they have no behavior); the next tasks exercise them through service tests.

- [ ] **Step 1: Create `RoomType`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

public enum RoomType {
    ENTRANCE,
    COMBAT,
    ELITE,
    TREASURE,
    HEAL,
    SHOP,
    BOSS,
    SECRET
}
```

- [ ] **Step 2: Create `GridPos`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record GridPos(int x, int y) implements Serializable {}
```

- [ ] **Step 3: Create `TreasureOffer`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record TreasureOffer(List<RelicId> relics) implements Serializable {
    public TreasureOffer {
        relics = List.copyOf(relics);
    }
}
```

- [ ] **Step 4: Create `ShopOfferEntry` + `ShopConsumable` + `ShopOffer`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record ShopOfferEntry(RelicId relic, int price) implements Serializable {}
```

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public record ShopConsumable(String i18nKey, int price, int healAmount) implements Serializable {}
```

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record ShopOffer(List<ShopOfferEntry> entries, ShopConsumable consumable) implements Serializable {
    public ShopOffer {
        entries = List.copyOf(entries);
    }
}
```

- [ ] **Step 5: Create `SecretReward` (sealed)**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;

public sealed interface SecretReward extends Serializable
    permits SecretReward.RelicReward, SecretReward.Bundle {

    record RelicReward(RelicId relic) implements SecretReward {}
    record Bundle(int healToFull, int shieldsGranted, int scoreBonus) implements SecretReward {}
}
```

- [ ] **Step 6: Create `PendingPickType` + `PendingRelicPick`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

public enum PendingPickType {
    TREASURE,
    ELITE,
    SHOP,
    SECRET
}
```

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;

public record PendingRelicPick(
    PendingPickType type,
    String roomId,
    List<RelicId> offer,        // for TREASURE / ELITE / SECRET; empty for SHOP (use shopOffer)
    ShopOffer shopOffer         // null for non-SHOP picks
) implements Serializable {
    public PendingRelicPick {
        offer = offer == null ? List.of() : List.copyOf(offer);
    }
}
```

- [ ] **Step 7: Create `DungeonRoom`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record DungeonRoom(
    String id,
    RoomType type,
    Map<DungeonDirection, String> doors,   // direction -> neighbor roomId
    GridPos gridPos,
    boolean visited,
    boolean cleared,
    String encounterId,                    // COMBAT: encounter id; null otherwise
    List<String> gauntletGroup,            // ELITE only: ordered encounter ids; empty for non-Elite
    TreasureOffer treasureOffer,           // TREASURE: relic choices; null otherwise
    TreasureOffer eliteOffer,              // ELITE: relic choices revealed after clear; null otherwise
    ShopOffer shopOffer,                   // SHOP only; null otherwise
    SecretReward secretReward              // SECRET only; null otherwise
) implements Serializable {

    public DungeonRoom {
        doors = doors == null ? Map.of() : Map.copyOf(doors);
        gauntletGroup = gauntletGroup == null ? List.of() : List.copyOf(gauntletGroup);
    }

    public DungeonRoom withDoors(Map<DungeonDirection, String> newDoors) {
        return new DungeonRoom(id, type, newDoors, gridPos, visited, cleared,
            encounterId, gauntletGroup, treasureOffer, eliteOffer, shopOffer, secretReward);
    }

    public DungeonRoom withVisited(boolean v) {
        return new DungeonRoom(id, type, doors, gridPos, v, cleared,
            encounterId, gauntletGroup, treasureOffer, eliteOffer, shopOffer, secretReward);
    }

    public DungeonRoom withCleared(boolean c) {
        return new DungeonRoom(id, type, doors, gridPos, visited, c,
            encounterId, gauntletGroup, treasureOffer, eliteOffer, shopOffer, secretReward);
    }
}
```

- [ ] **Step 8: Compile to verify there are no syntax errors**

Run: `./mvnw compile`
Expected: BUILD SUCCESS (warnings are fine; no errors).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/dto/RoomType.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/GridPos.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/TreasureOffer.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/ShopOfferEntry.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/ShopOffer.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/ShopConsumable.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/SecretReward.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/PendingPickType.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/PendingRelicPick.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonRoom.java
git commit -m "feat(dungeon): add room-graph value types alongside tile types"
```

---

## Task 3: Verify saved-session compatibility guard

The existing `SavedSessionService.loadDungeon` already catches `Exception` from JSON deserialization and sets an `incompatibleDiscardUserIds` flag. We need a test that confirms this still fires when the schema breaks. No production code change expected.

**Files:**
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/SavedSessionDungeonCompatTests.java` (new)

- [ ] **Step 1: Write the test**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.SavedSession;
import com.HendrikHoemberg.StudyHelper.entity.SavedSessionType;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.SavedSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SavedSessionDungeonCompatTests {

    @Test
    void loadDungeon_withIncompatiblePayload_returnsEmptyAndSetsFlag() {
        SavedSessionRepository repo = mock(SavedSessionRepository.class);
        SavedSession session = new SavedSession();
        session.setType(SavedSessionType.DUNGEON);
        session.setPayload("{\"this\":\"is not a DungeonSessionState\"}");
        User user = new User();
        user.setId(7L);
        when(repo.findByUser(user)).thenReturn(Optional.of(session));

        SavedSessionService svc = new SavedSessionService(repo, null, null, null);
        Optional<DungeonSessionState> loaded = svc.loadDungeon(user);

        assertThat(loaded).isEmpty();
        assertThat(svc.consumeIncompatibleDiscardFlag(user)).isTrue();
        verify(repo).delete(session);
    }
}
```

(If the constructor signature of `SavedSessionService` differs from `(SavedSessionRepository, ?, ?, ?)`, peek at the real constructor and pass `null` for the dependencies the test doesn't need. The test only exercises `loadDungeon`.)

- [ ] **Step 2: Run the test to verify it passes against current production code**

Run: `./mvnw test -Dtest=SavedSessionDungeonCompatTests`
Expected: PASS. (If it fails, the existing safety net is broken — fix it before moving on.)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/HendrikHoemberg/StudyHelper/service/SavedSessionDungeonCompatTests.java
git commit -m "test(dungeon): verify saved-session incompatible-payload guard"
```

---

## Task 4: Destructive swap — rewrite `DungeonMap`, `DungeonSessionState`, `DungeonRunStats`; delete tile types

This is the largest task. The tile/position/tile-type types cannot coexist with the room-graph types in `DungeonMap`; once we delete them, every call site stops compiling and must be updated together. Tasks 5–9 each rewrite one of the affected services; this task only swaps the data layer and updates `DungeonRunStats`. Service tasks will leave the codebase non-compiling at intermediate checkpoints — that's expected.

**Files:**
- Modify (rewrite): `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonMap.java`
- Modify (rewrite): `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSessionState.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonRunStats.java`
- Delete: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonPosition.java`
- Delete: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTile.java`
- Delete: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTileType.java`
- Modify (rewrite): `src/test/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSessionStateTests.java`

- [ ] **Step 1: Rewrite `DungeonMap`**

Replace the entire file with:

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.Map;

public record DungeonMap(
    Map<String, DungeonRoom> rooms,
    String entranceRoomId,
    String bossRoomId,
    int lattice                       // square edge length of the layout lattice; informational only
) implements Serializable {

    public DungeonMap {
        rooms = Map.copyOf(rooms);
    }

    public DungeonRoom room(String id) {
        return rooms.get(id);
    }
}
```

- [ ] **Step 2: Rewrite `DungeonSessionState`**

Replace the entire file with:

```java
package com.HendrikHoemberg.StudyHelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record DungeonSessionState(
    DungeonConfig config,
    DungeonMap map,
    String currentRoomId,
    Map<String, DungeonEncounter> encounters,
    List<String> bossEncounterIds,
    int bossIndex,
    String activeEncounterId,
    int health,
    int healthCap,
    int shields,
    int shieldCap,
    int score,
    int answeredCount,
    int correctCount,
    boolean won,
    boolean defeated,
    int streak,
    List<String> gauntletQueue,
    int longestStreak,
    int elitesCleared,
    int shieldsUsed,
    int luckyCoinsConsumed,
    List<RelicId> ownedRelics,
    PendingRelicPick pendingRelicPick
) implements Serializable {

    public DungeonSessionState {
        ownedRelics = ownedRelics == null ? List.of() : List.copyOf(ownedRelics);
        gauntletQueue = gauntletQueue == null ? List.of() : List.copyOf(gauntletQueue);
        encounters = Map.copyOf(encounters);
        bossEncounterIds = List.copyOf(bossEncounterIds);
    }

    @JsonIgnore
    public boolean isComplete() {
        return won || defeated;
    }

    public DungeonEncounter activeEncounter() {
        return activeEncounterId == null ? null : encounters.get(activeEncounterId);
    }

    public DungeonRoom currentRoom() {
        return map.room(currentRoomId);
    }
}
```

(Field order is intentional: stats grouped together, mutable-during-run grouped together. Match this order everywhere a `new DungeonSessionState(...)` is built in later tasks.)

- [ ] **Step 3: Update `DungeonRunStats`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

public record DungeonRunStats(
    DungeonMode mode,
    DungeonSize size,
    int totalPrompts,
    int answeredPrompts,
    int correctPrompts,
    int healthRemaining,
    boolean won,
    int longestStreak,
    int elitesCleared,
    int shieldsUsed,
    int relicsAcquired
) {}
```

- [ ] **Step 4: Delete the tile types**

```bash
git rm src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonPosition.java
git rm src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTile.java
git rm src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTileType.java
```

- [ ] **Step 5: Rewrite `DungeonSessionStateTests`**

```java
package com.HendrikHoemberg.StudyHelper.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonSessionStateTests {

    @Test
    void newState_storesAllFieldsAndComputesActiveEncounterNull() {
        DungeonRoom entrance = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0), true, true,
            null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", entrance), "r0", "r0", 7);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");

        DungeonSessionState state = new DungeonSessionState(
            config, map, "r0",
            Map.of(), List.of(), 0, null,
            5, 5, 0, 2,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);

        assertThat(state.isComplete()).isFalse();
        assertThat(state.activeEncounter()).isNull();
        assertThat(state.currentRoom()).isEqualTo(entrance);
        assertThat(state.ownedRelics()).isEmpty();
    }

    @Test
    void isComplete_trueWhenWonOrDefeated() {
        DungeonRoom entrance = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0), true, true,
            null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", entrance), "r0", "r0", 7);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");

        DungeonSessionState won = new DungeonSessionState(
            config, map, "r0", Map.of(), List.of(), 0, null,
            5, 5, 0, 2, 0, 0, 0, true, false,
            0, List.of(), 0, 0, 0, 0, List.of(), null);
        DungeonSessionState lost = new DungeonSessionState(
            config, map, "r0", Map.of(), List.of(), 0, null,
            0, 5, 0, 2, 0, 0, 0, false, true,
            0, List.of(), 0, 0, 0, 0, List.of(), null);

        assertThat(won.isComplete()).isTrue();
        assertThat(lost.isComplete()).isTrue();
    }
}
```

- [ ] **Step 6: Compile — expect failures from old call sites**

Run: `./mvnw compile`
Expected: many compile errors in `DungeonMapGenerator`, `DungeonNavigationService`, `DungeonEncounterService`, `DungeonSessionService`, `DungeonController`, and their tests. This is intentional. Do NOT commit yet — the tree is broken until Tasks 5–9 are also done.

- [ ] **Step 7: Stash work-in-progress checkpoint (optional)**

If you want a recovery point, use a WIP commit on a branch:

```bash
git add -A
git commit -m "WIP: dungeon room-graph data-layer swap (does not compile)"
```

Otherwise leave the changes staged and continue straight to Task 5. The final commit for the destructive swap happens at the end of Task 9, when the codebase compiles again.

---

## Task 5: Rewrite `DungeonMapGenerator` (random-walk room graph)

Replace the carving algorithm with random-walk room placement, MST/loop-door connectivity, type assignment, and offer generation. Tests are rewritten in the same task.

**Files:**
- Modify (rewrite): `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGenerator.java`
- Modify (rewrite): `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGeneratorTests.java`

- [ ] **Step 1: Rewrite the generator**

Replace the file with:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DungeonMapGenerator {

    private static final int MAX_REGEN_ATTEMPTS = 10;
    private static final DungeonDirection[] DIRS = DungeonDirection.values();

    public DungeonMap generate(DungeonSize size,
                                List<String> normalEncounterIds,
                                List<List<String>> eliteGauntletGroups) {
        return generate(size, normalEncounterIds, eliteGauntletGroups, new Random());
    }

    public DungeonMap generate(DungeonSize size,
                                List<String> normalEncounterIds,
                                List<List<String>> eliteGauntletGroups,
                                Random rng) {
        if (normalEncounterIds.size() != size.normalEncounterCount()) {
            throw new IllegalArgumentException("Normal encounter count must match dungeon size.");
        }
        if (eliteGauntletGroups.size() != size.eliteGauntletCount()) {
            throw new IllegalArgumentException("Elite gauntlet group count must match dungeon size.");
        }

        for (int attempt = 0; attempt < MAX_REGEN_ATTEMPTS; attempt++) {
            try {
                return tryGenerate(size, normalEncounterIds, eliteGauntletGroups, rng);
            } catch (LayoutFailure ignored) {
            }
        }
        throw new IllegalStateException(
            "Could not generate a valid dungeon layout after " + MAX_REGEN_ATTEMPTS + " attempts.");
    }

    private DungeonMap tryGenerate(DungeonSize size,
                                    List<String> normalEncounterIds,
                                    List<List<String>> eliteGauntletGroups,
                                    Random rng) {
        int lattice = latticeSize(size);
        int targetRooms = totalRoomCount(size);

        // Step 1: random-walk room placement on the lattice
        Map<GridPos, String> latticeToRoomId = new LinkedHashMap<>();
        Map<String, GridPos> roomToLattice = new LinkedHashMap<>();
        Map<String, Map<DungeonDirection, String>> doors = new LinkedHashMap<>();

        GridPos center = new GridPos(lattice / 2, lattice / 2);
        String entranceId = "r0";
        latticeToRoomId.put(center, entranceId);
        roomToLattice.put(entranceId, center);
        doors.put(entranceId, new EnumMap<>(DungeonDirection.class));

        int placed = 1;
        int attempts = 0;
        int maxAttempts = targetRooms * 30;
        while (placed < targetRooms && attempts < maxAttempts) {
            attempts++;
            List<String> existing = new ArrayList<>(roomToLattice.keySet());
            String fromId = existing.get(rng.nextInt(existing.size()));
            GridPos from = roomToLattice.get(fromId);
            DungeonDirection dir = DIRS[rng.nextInt(DIRS.length)];
            GridPos to = move(from, dir);
            if (!inLattice(to, lattice) || latticeToRoomId.containsKey(to)) continue;

            String newId = "r" + placed;
            latticeToRoomId.put(to, newId);
            roomToLattice.put(newId, to);
            doors.put(newId, new EnumMap<>(DungeonDirection.class));
            connect(doors, fromId, newId, dir);
            placed++;
        }
        if (placed < targetRooms) throw new LayoutFailure();

        // Step 2: add 1-2 loop doors between adjacent-but-unconnected rooms
        addLoopDoors(latticeToRoomId, roomToLattice, doors, loopDoorCount(size), rng);

        // Step 3: assign room types
        Map<String, RoomType> types = assignRoomTypes(
            entranceId, roomToLattice, doors, size, rng);

        // Step 4: embed secret room (no doors; revealed via hosts)
        SecretRoomPlacement secret = embedSecretRoom(latticeToRoomId, roomToLattice, lattice, rng);
        String secretRoomId = null;
        if (secret != null) {
            secretRoomId = secret.id;
            latticeToRoomId.put(secret.pos, secret.id);
            roomToLattice.put(secret.id, secret.pos);
            doors.put(secret.id, new EnumMap<>(DungeonDirection.class));
            types.put(secret.id, RoomType.SECRET);
        }

        // Step 5: build rooms with offers + gauntlet groups
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>();
        Iterator<String> normalIter = new ArrayDeque<>(normalEncounterIds).iterator();
        Iterator<List<String>> eliteIter = new ArrayDeque<>(eliteGauntletGroups).iterator();

        String bossRoomId = pickBossRoom(entranceId, types, doors);
        types.put(bossRoomId, RoomType.BOSS);

        for (Map.Entry<String, RoomType> e : types.entrySet()) {
            String id = e.getKey();
            RoomType type = e.getValue();
            DungeonRoom room = buildRoom(id, type, doors.get(id), roomToLattice.get(id),
                normalIter, eliteIter, secretRoomId, secret, rng);
            rooms.put(id, room);
        }

        // Mark entrance visited+cleared
        DungeonRoom entranceRoom = rooms.get(entranceId).withVisited(true).withCleared(true);
        rooms.put(entranceId, entranceRoom);

        // Step 6: validate
        if (!bfsReachable(rooms, entranceId).contains(bossRoomId)) throw new LayoutFailure();
        if (normalIter.hasNext()) throw new LayoutFailure();
        if (eliteIter.hasNext()) throw new LayoutFailure();

        return new DungeonMap(rooms, entranceId, bossRoomId, lattice);
    }

    // ===== helpers =====

    private static class LayoutFailure extends RuntimeException {}

    private record SecretRoomPlacement(String id, GridPos pos, List<String> hostRoomIds) {}

    private int latticeSize(DungeonSize size) {
        return switch (size) { case SMALL -> 7; case MEDIUM -> 9; case LARGE -> 11; };
    }

    private int totalRoomCount(DungeonSize size) {
        return switch (size) { case SMALL -> 12; case MEDIUM -> 16; case LARGE -> 24; };
        // SMALL: 6 combat + 1 elite + 1 treasure + 1 heal + 1 shop + 1 boss + 1 entrance = 12 (secret is added separately as a non-walked room)
        // MEDIUM: 9 + 2 + 1 + 1 + 1 + 1 + 1 = 16
        // LARGE: 15 + 3 + 2 + 1 + 1 + 1 + 1 = 24
    }

    private int loopDoorCount(DungeonSize size) {
        return switch (size) { case SMALL, MEDIUM -> 1; case LARGE -> 2; };
    }

    private boolean inLattice(GridPos p, int lattice) {
        return p.x() >= 0 && p.y() >= 0 && p.x() < lattice && p.y() < lattice;
    }

    private GridPos move(GridPos p, DungeonDirection d) {
        return switch (d) {
            case UP -> new GridPos(p.x(), p.y() - 1);
            case DOWN -> new GridPos(p.x(), p.y() + 1);
            case LEFT -> new GridPos(p.x() - 1, p.y());
            case RIGHT -> new GridPos(p.x() + 1, p.y());
        };
    }

    private DungeonDirection opposite(DungeonDirection d) {
        return switch (d) {
            case UP -> DungeonDirection.DOWN;
            case DOWN -> DungeonDirection.UP;
            case LEFT -> DungeonDirection.RIGHT;
            case RIGHT -> DungeonDirection.LEFT;
        };
    }

    private void connect(Map<String, Map<DungeonDirection, String>> doors,
                          String a, String b, DungeonDirection aToB) {
        doors.get(a).put(aToB, b);
        doors.get(b).put(opposite(aToB), a);
    }

    private void addLoopDoors(Map<GridPos, String> latticeToRoomId,
                                Map<String, GridPos> roomToLattice,
                                Map<String, Map<DungeonDirection, String>> doors,
                                int loops, Random rng) {
        List<String[]> candidates = new ArrayList<>();
        for (Map.Entry<String, GridPos> e : roomToLattice.entrySet()) {
            String id = e.getKey();
            GridPos pos = e.getValue();
            for (DungeonDirection d : DIRS) {
                GridPos neighborPos = move(pos, d);
                String neighborId = latticeToRoomId.get(neighborPos);
                if (neighborId == null) continue;
                if (doors.get(id).containsKey(d)) continue;
                if (id.compareTo(neighborId) < 0) {  // dedupe pairs
                    candidates.add(new String[]{id, neighborId, d.name()});
                }
            }
        }
        Collections.shuffle(candidates, rng);
        for (int i = 0; i < Math.min(loops, candidates.size()); i++) {
            String[] c = candidates.get(i);
            connect(doors, c[0], c[1], DungeonDirection.valueOf(c[2]));
        }
    }

    private Map<String, RoomType> assignRoomTypes(String entranceId,
                                                    Map<String, GridPos> roomToLattice,
                                                    Map<String, Map<DungeonDirection, String>> doors,
                                                    DungeonSize size, Random rng) {
        Map<String, RoomType> types = new LinkedHashMap<>();
        for (String id : roomToLattice.keySet()) types.put(id, RoomType.COMBAT);
        types.put(entranceId, RoomType.ENTRANCE);

        Map<String, Integer> dist = bfsDistances(doors, entranceId);
        List<String> leavesAsc = sortedLeaves(doors, dist, true);
        List<String> leavesDesc = sortedLeaves(doors, dist, false);
        List<String> nonCritical = nonCriticalRooms(entranceId, pickBossCandidate(entranceId, dist),
            doors, dist, types);

        // SHOP — nearest leaf to entrance (excluding entrance itself)
        String shop = pickFirstAvailable(leavesAsc, types, entranceId, Set.of(RoomType.ENTRANCE));
        if (shop == null) throw new LayoutFailure();
        types.put(shop, RoomType.SHOP);

        // TREASURE — farther leaf
        String treasure = pickFirstAvailable(leavesDesc, types, entranceId, Set.of(RoomType.ENTRANCE));
        if (treasure == null) throw new LayoutFailure();
        types.put(treasure, RoomType.TREASURE);

        // LARGE has a second TREASURE
        if (size == DungeonSize.LARGE) {
            String treasure2 = pickFirstAvailable(leavesDesc, types, entranceId, Set.of());
            if (treasure2 == null) throw new LayoutFailure();
            types.put(treasure2, RoomType.TREASURE);
        }

        // HEAL — non-critical mid-distance room
        String heal = pickMidpoint(nonCritical, dist, types);
        if (heal == null) throw new LayoutFailure();
        types.put(heal, RoomType.HEAL);

        // ELITEs — additional leaves
        int eliteCount = size.eliteGauntletCount();
        List<String> remainingLeaves = new ArrayList<>(leavesDesc);
        remainingLeaves.removeAll(types.keySet().stream()
            .filter(id -> types.get(id) != RoomType.COMBAT && types.get(id) != RoomType.ENTRANCE)
            .toList());
        int assignedElites = 0;
        for (String leaf : remainingLeaves) {
            if (assignedElites >= eliteCount) break;
            if (types.get(leaf) == RoomType.COMBAT) {
                types.put(leaf, RoomType.ELITE);
                assignedElites++;
            }
        }
        // If too few leaves, attach Elites to any remaining non-critical COMBAT rooms
        if (assignedElites < eliteCount) {
            for (String id : nonCritical) {
                if (assignedElites >= eliteCount) break;
                if (types.get(id) == RoomType.COMBAT) {
                    types.put(id, RoomType.ELITE);
                    assignedElites++;
                }
            }
        }
        if (assignedElites < eliteCount) throw new LayoutFailure();

        return types;
    }

    private String pickBossCandidate(String entranceId, Map<String, Integer> dist) {
        String best = entranceId;
        int bestDist = -1;
        for (Map.Entry<String, Integer> e : dist.entrySet()) {
            if (e.getValue() > bestDist) {
                bestDist = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private String pickBossRoom(String entranceId,
                                  Map<String, RoomType> types,
                                  Map<String, Map<DungeonDirection, String>> doors) {
        Map<String, Integer> dist = bfsDistances(doors, entranceId);
        String best = entranceId;
        int bestDist = -1;
        for (Map.Entry<String, Integer> e : dist.entrySet()) {
            if (types.get(e.getKey()) != RoomType.COMBAT) continue;
            if (e.getValue() > bestDist) {
                bestDist = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private List<String> sortedLeaves(Map<String, Map<DungeonDirection, String>> doors,
                                        Map<String, Integer> dist, boolean ascending) {
        List<String> leaves = new ArrayList<>();
        for (Map.Entry<String, Map<DungeonDirection, String>> e : doors.entrySet()) {
            if (e.getValue().size() == 1) leaves.add(e.getKey());
        }
        leaves.sort(Comparator.comparingInt(dist::get));
        if (!ascending) Collections.reverse(leaves);
        return leaves;
    }

    private List<String> nonCriticalRooms(String entranceId, String bossId,
                                            Map<String, Map<DungeonDirection, String>> doors,
                                            Map<String, Integer> dist,
                                            Map<String, RoomType> types) {
        // Naive: any room not on a single shortest path from entrance to boss.
        // For our small graphs, "non-critical" = "not the entrance and not the boss."
        List<String> result = new ArrayList<>();
        for (String id : doors.keySet()) {
            if (!id.equals(entranceId) && !id.equals(bossId)) result.add(id);
        }
        return result;
    }

    private String pickFirstAvailable(List<String> ordered,
                                        Map<String, RoomType> types,
                                        String entranceId,
                                        Set<RoomType> blockedTypes) {
        for (String id : ordered) {
            if (id.equals(entranceId)) continue;
            RoomType t = types.get(id);
            if (t != RoomType.COMBAT) continue;
            if (blockedTypes.contains(t)) continue;
            return id;
        }
        return null;
    }

    private String pickMidpoint(List<String> candidates,
                                  Map<String, Integer> dist,
                                  Map<String, RoomType> types) {
        int max = candidates.stream().mapToInt(dist::get).max().orElse(0);
        int targetDist = max / 2;
        String best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (String id : candidates) {
            if (types.get(id) != RoomType.COMBAT) continue;
            int delta = Math.abs(dist.get(id) - targetDist);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = id;
            }
        }
        return best;
    }

    private SecretRoomPlacement embedSecretRoom(Map<GridPos, String> latticeToRoomId,
                                                  Map<String, GridPos> roomToLattice,
                                                  int lattice,
                                                  Random rng) {
        List<GridPos> candidates = new ArrayList<>();
        for (int x = 0; x < lattice; x++) {
            for (int y = 0; y < lattice; y++) {
                GridPos pos = new GridPos(x, y);
                if (latticeToRoomId.containsKey(pos)) continue;
                List<String> hosts = new ArrayList<>();
                for (DungeonDirection d : DIRS) {
                    String neighborId = latticeToRoomId.get(move(pos, d));
                    if (neighborId != null) hosts.add(neighborId);
                }
                if (hosts.size() >= 2) candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) return null;
        Collections.shuffle(candidates, rng);
        GridPos chosen = candidates.get(0);
        List<String> hosts = new ArrayList<>();
        for (DungeonDirection d : DIRS) {
            String neighborId = latticeToRoomId.get(move(chosen, d));
            if (neighborId != null) hosts.add(neighborId);
        }
        return new SecretRoomPlacement("secret", chosen, hosts);
    }

    private DungeonRoom buildRoom(String id, RoomType type,
                                    Map<DungeonDirection, String> roomDoors,
                                    GridPos gridPos,
                                    Iterator<String> normalIter,
                                    Iterator<List<String>> eliteIter,
                                    String secretRoomId,
                                    SecretRoomPlacement secret,
                                    Random rng) {
        String encounterId = null;
        List<String> gauntletGroup = List.of();
        TreasureOffer treasureOffer = null;
        TreasureOffer eliteOffer = null;
        ShopOffer shopOffer = null;
        SecretReward secretReward = null;

        switch (type) {
            case COMBAT -> encounterId = normalIter.next();
            case ELITE -> {
                gauntletGroup = eliteIter.next();
                encounterId = gauntletGroup.get(0);
                eliteOffer = new TreasureOffer(
                    RelicCatalog.sample(RelicPool.ELITE, 2, rng));
            }
            case TREASURE -> treasureOffer = new TreasureOffer(
                RelicCatalog.sample(RelicPool.COMMON, 3, rng));
            case SHOP -> {
                List<RelicId> picks = RelicCatalog.sampleFromUnion(
                    List.of(RelicPool.COMMON, RelicPool.SHOP_EXCLUSIVE), 2, rng);
                List<ShopOfferEntry> entries = new ArrayList<>();
                for (RelicId rid : picks) {
                    int price = (rid == RelicId.MAP_SENSE) ? 150 : 75;
                    entries.add(new ShopOfferEntry(rid, price));
                }
                ShopConsumable consumable = new ShopConsumable(
                    "dungeon.shop.consumable.heal", 30, 2);
                shopOffer = new ShopOffer(entries, consumable);
            }
            case SECRET -> {
                if (rng.nextBoolean()) {
                    List<RelicId> picks = RelicCatalog.sampleFromUnion(
                        List.of(RelicPool.COMMON, RelicPool.ELITE), 1, rng);
                    secretReward = new SecretReward.RelicReward(picks.get(0));
                } else {
                    secretReward = new SecretReward.Bundle(5, 1, 50);
                }
            }
            case ENTRANCE, HEAL, BOSS -> { /* no offers/encounters */ }
        }

        return new DungeonRoom(id, type, roomDoors, gridPos,
            false, false,
            encounterId, gauntletGroup,
            treasureOffer, eliteOffer, shopOffer, secretReward);
    }

    private Map<String, Integer> bfsDistances(Map<String, Map<DungeonDirection, String>> doors, String from) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        dist.put(from, 0);
        queue.add(from);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            for (String neighbor : doors.get(node).values()) {
                if (dist.containsKey(neighbor)) continue;
                dist.put(neighbor, dist.get(node) + 1);
                queue.add(neighbor);
            }
        }
        return dist;
    }

    private Set<String> bfsReachable(Map<String, DungeonRoom> rooms, String from) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(from);
        queue.add(from);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            DungeonRoom r = rooms.get(node);
            for (String neighbor : r.doors().values()) {
                if (seen.add(neighbor)) queue.add(neighbor);
            }
        }
        return seen;
    }
}
```

- [ ] **Step 2: Rewrite `DungeonMapGeneratorTests`**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonMapGeneratorTests {

    private final DungeonMapGenerator generator = new DungeonMapGenerator();

    @Test
    void generate_smallDungeonHasExpectedRoomTypeCounts() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(1));
        Map<RoomType, Long> counts = countByType(map);
        assertThat(counts.get(RoomType.ENTRANCE)).isEqualTo(1);
        assertThat(counts.get(RoomType.BOSS)).isEqualTo(1);
        assertThat(counts.get(RoomType.SHOP)).isEqualTo(1);
        assertThat(counts.get(RoomType.HEAL)).isEqualTo(1);
        assertThat(counts.get(RoomType.TREASURE)).isEqualTo(1);
        assertThat(counts.get(RoomType.ELITE)).isEqualTo(1);
        assertThat(counts.get(RoomType.COMBAT)).isEqualTo(6);
        assertThat(counts.getOrDefault(RoomType.SECRET, 0L)).isEqualTo(1);
    }

    @Test
    void generate_mediumDungeonHasExpectedRoomTypeCounts() {
        DungeonMap map = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(1));
        Map<RoomType, Long> counts = countByType(map);
        assertThat(counts.get(RoomType.COMBAT)).isEqualTo(9);
        assertThat(counts.get(RoomType.ELITE)).isEqualTo(2);
        assertThat(counts.get(RoomType.TREASURE)).isEqualTo(1);
    }

    @Test
    void generate_largeDungeonHasExpectedRoomTypeCounts() {
        DungeonMap map = generator.generate(
            DungeonSize.LARGE, normalIds(15), eliteGroups(3, 3), new Random(1));
        Map<RoomType, Long> counts = countByType(map);
        assertThat(counts.get(RoomType.COMBAT)).isEqualTo(15);
        assertThat(counts.get(RoomType.ELITE)).isEqualTo(3);
        assertThat(counts.get(RoomType.TREASURE)).isEqualTo(2);
    }

    @Test
    void generate_bossIsReachableFromEntrance() {
        DungeonMap map = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(2));
        Set<String> reachable = reach(map);
        assertThat(reachable).contains(map.bossRoomId());
    }

    @Test
    void generate_allNonSecretRoomsReachable() {
        DungeonMap map = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(3));
        Set<String> reachable = reach(map);
        Set<String> nonSecret = map.rooms().values().stream()
            .filter(r -> r.type() != RoomType.SECRET)
            .map(DungeonRoom::id)
            .collect(Collectors.toSet());
        assertThat(reachable).containsAll(nonSecret);
    }

    @Test
    void generate_secretRoomHasNoDoors() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(4));
        DungeonRoom secret = map.rooms().values().stream()
            .filter(r -> r.type() == RoomType.SECRET)
            .findFirst().orElseThrow();
        assertThat(secret.doors()).isEmpty();
    }

    @Test
    void generate_seedIsDeterministic() {
        DungeonMap a = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(99));
        DungeonMap b = generator.generate(
            DungeonSize.MEDIUM, normalIds(9), eliteGroups(2, 2), new Random(99));
        assertThat(a.rooms().keySet()).isEqualTo(b.rooms().keySet());
        assertThat(a.bossRoomId()).isEqualTo(b.bossRoomId());
        for (String id : a.rooms().keySet()) {
            assertThat(a.rooms().get(id).type())
                .as("type of %s", id)
                .isEqualTo(b.rooms().get(id).type());
        }
    }

    @Test
    void generate_eliteRoomsCarryGauntletGroupOfCorrectSize() {
        DungeonMap map = generator.generate(
            DungeonSize.LARGE, normalIds(15), eliteGroups(3, 3), new Random(5));
        List<DungeonRoom> elites = map.rooms().values().stream()
            .filter(r -> r.type() == RoomType.ELITE)
            .toList();
        assertThat(elites).hasSize(3);
        for (DungeonRoom e : elites) {
            assertThat(e.gauntletGroup()).hasSize(3);
            assertThat(e.encounterId()).isEqualTo(e.gauntletGroup().get(0));
            assertThat(e.eliteOffer()).isNotNull();
            assertThat(e.eliteOffer().relics()).hasSize(2);
        }
    }

    @Test
    void generate_shopRoomHasOfferWithTwoEntriesAndConsumable() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(6));
        DungeonRoom shop = map.rooms().values().stream()
            .filter(r -> r.type() == RoomType.SHOP).findFirst().orElseThrow();
        assertThat(shop.shopOffer()).isNotNull();
        assertThat(shop.shopOffer().entries()).hasSize(2);
        assertThat(shop.shopOffer().consumable()).isNotNull();
    }

    @Test
    void generate_treasureRoomHasThreeRelics() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(7));
        DungeonRoom t = map.rooms().values().stream()
            .filter(r -> r.type() == RoomType.TREASURE).findFirst().orElseThrow();
        assertThat(t.treasureOffer()).isNotNull();
        assertThat(t.treasureOffer().relics()).hasSize(3);
    }

    @Test
    void generate_entranceIsVisitedAndCleared() {
        DungeonMap map = generator.generate(
            DungeonSize.SMALL, normalIds(6), eliteGroups(1, 2), new Random(8));
        DungeonRoom entrance = map.rooms().get(map.entranceRoomId());
        assertThat(entrance.visited()).isTrue();
        assertThat(entrance.cleared()).isTrue();
    }

    // ===== helpers =====

    private List<String> normalIds(int n) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add("enc_" + i);
        return ids;
    }

    private List<List<String>> eliteGroups(int groups, int cardsEach) {
        List<List<String>> result = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            List<String> group = new ArrayList<>();
            for (int c = 0; c < cardsEach; c++) group.add("elite_" + g + "_" + c);
            result.add(group);
        }
        return result;
    }

    private Map<RoomType, Long> countByType(DungeonMap map) {
        return map.rooms().values().stream()
            .collect(Collectors.groupingBy(DungeonRoom::type, Collectors.counting()));
    }

    private Set<String> reach(DungeonMap map) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(map.entranceRoomId());
        queue.add(map.entranceRoomId());
        while (!queue.isEmpty()) {
            String node = queue.poll();
            for (String neighbor : map.rooms().get(node).doors().values()) {
                if (seen.add(neighbor)) queue.add(neighbor);
            }
        }
        return seen;
    }
}
```

- [ ] **Step 3: Compile and run the generator tests**

Run: `./mvnw test -Dtest=DungeonMapGeneratorTests`
Expected: tests pass. The rest of the codebase still has compile errors at call sites — that's fine; Tasks 6-9 fix them.

If any test fails, debug the algorithm. The most common failure modes: not enough leaves for the type budget on certain seeds (regenerate-loop should handle it), or `pickMidpoint` returning null on a degenerate graph (raise `LayoutFailure` to retry).

- [ ] **Step 4: Stage but do NOT commit yet**

The codebase still doesn't compile end-to-end. We commit once Tasks 5-9 are all in.

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGenerator.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGeneratorTests.java
```

---

## Task 6: Rewrite `DungeonNavigationService` for door-based movement

`move(state, direction)` becomes "if the current room has a door in that direction, set `currentRoomId` to the neighbor and mark visited." First-entry effects (HEAL) apply here. Encounter activation is signalled to the orchestrator via the return value; this service does not call the encounter service.

**Files:**
- Modify (rewrite): `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationService.java`
- Modify (rewrite): `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationServiceTests.java`

- [ ] **Step 1: Write the failing test file first**

Replace the whole tests file with:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonNavigationServiceTests {

    private final DungeonNavigationService nav = new DungeonNavigationService();

    @Test
    void move_intoRoomWithoutDoorReturnsUnchangedState() {
        DungeonSessionState state = twoRoomState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.UP);
        assertThat(result.state().currentRoomId()).isEqualTo("r0");
        assertThat(result.activatedEncounterRoomId()).isNull();
    }

    @Test
    void move_throughDoorUpdatesCurrentRoomAndMarksVisited() {
        DungeonSessionState state = twoRoomState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().currentRoomId()).isEqualTo("r1");
        assertThat(result.state().map().room("r1").visited()).isTrue();
    }

    @Test
    void move_intoCombatRoomSignalsEncounterActivation() {
        DungeonSessionState state = combatNeighborState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.activatedEncounterRoomId()).isEqualTo("r1");
    }

    @Test
    void move_intoHealRoomAppliesFullHealAndShieldGrantAndMarksCleared() {
        DungeonSessionState state = healNeighborStateWithLowHpAndNoShields();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().health()).isEqualTo(result.state().healthCap());
        assertThat(result.state().shields()).isEqualTo(1);
        assertThat(result.state().map().room("r1").cleared()).isTrue();
    }

    @Test
    void move_intoAlreadyClearedHealRoomDoesNothingExtra() {
        DungeonSessionState state = clearedHealNeighborState();
        int hpBefore = state.health();
        int shieldsBefore = state.shields();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state().health()).isEqualTo(hpBefore);
        assertThat(result.state().shields()).isEqualTo(shieldsBefore);
    }

    @Test
    void move_blockedWhileEncounterActive() {
        DungeonSessionState state = encounterActiveState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state()).isSameAs(state);
    }

    @Test
    void move_blockedWhenRunIsComplete() {
        DungeonSessionState state = wonState();
        DungeonNavigationService.MoveResult result = nav.move(state, DungeonDirection.RIGHT);
        assertThat(result.state()).isSameAs(state);
    }

    // ===== fixtures =====

    private DungeonSessionState twoRoomState() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            false, false, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 5, 0);
    }

    private DungeonSessionState combatNeighborState() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            false, false, "enc_0", List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 5, 0);
    }

    private DungeonSessionState healNeighborStateWithLowHpAndNoShields() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.HEAL,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            false, false, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 2, 0);
    }

    private DungeonSessionState clearedHealNeighborState() {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.HEAL,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r0", 3, 1);
    }

    private DungeonSessionState encounterActiveState() {
        DungeonSessionState base = combatNeighborState();
        return new DungeonSessionState(
            base.config(), base.map(), base.currentRoomId(),
            base.encounters(), base.bossEncounterIds(), base.bossIndex(),
            "enc_0",
            base.health(), base.healthCap(), base.shields(), base.shieldCap(),
            base.score(), base.answeredCount(), base.correctCount(),
            false, false,
            base.streak(), base.gauntletQueue(),
            base.longestStreak(), base.elitesCleared(), base.shieldsUsed(),
            base.luckyCoinsConsumed(), base.ownedRelics(), base.pendingRelicPick());
    }

    private DungeonSessionState wonState() {
        DungeonSessionState base = twoRoomState();
        return new DungeonSessionState(
            base.config(), base.map(), base.currentRoomId(),
            base.encounters(), base.bossEncounterIds(), base.bossIndex(),
            base.activeEncounterId(),
            base.health(), base.healthCap(), base.shields(), base.shieldCap(),
            base.score(), base.answeredCount(), base.correctCount(),
            true, false,
            base.streak(), base.gauntletQueue(),
            base.longestStreak(), base.elitesCleared(), base.shieldsUsed(),
            base.luckyCoinsConsumed(), base.ownedRelics(), base.pendingRelicPick());
    }

    private DungeonSessionState baseState(DungeonMap map, String currentRoomId,
                                            int hp, int shields) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return new DungeonSessionState(
            config, map, currentRoomId,
            Map.of(), List.of(), 0, null,
            hp, 5, shields, 2,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);
    }
}
```

- [ ] **Step 2: Implement the new `DungeonNavigationService`**

Replace the whole file with:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DungeonNavigationService {

    public record MoveResult(DungeonSessionState state, String activatedEncounterRoomId) {}

    public MoveResult move(DungeonSessionState state, DungeonDirection direction) {
        if (state.isComplete()) return new MoveResult(state, null);
        if (state.activeEncounterId() != null) return new MoveResult(state, null);

        DungeonRoom current = state.currentRoom();
        if (current == null) return new MoveResult(state, null);

        String nextRoomId = current.doors().get(direction);
        if (nextRoomId == null) return new MoveResult(state, null);

        DungeonRoom nextRoom = state.map().room(nextRoomId);
        if (nextRoom == null) return new MoveResult(state, null);

        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        boolean firstEntry = !nextRoom.visited();
        nextRoom = nextRoom.withVisited(true);

        DungeonSessionState working = state;

        // First-entry effect for HEAL
        if (firstEntry && nextRoom.type() == RoomType.HEAL) {
            int healed = working.healthCap() - working.health();
            if (healed > 0) {
                working = DungeonDamage.heal(working, healed);
            }
            if (working.shields() < working.shieldCap()) {
                working = withShields(working, working.shields() + 1);
            }
            nextRoom = nextRoom.withCleared(true);
        }

        rooms.put(nextRoomId, nextRoom);
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());

        DungeonSessionState moved = withMapAndPosition(working, nextMap, nextRoomId);

        String encounterRoomToActivate = null;
        if (nextRoom.type() == RoomType.COMBAT
            || nextRoom.type() == RoomType.ELITE
            || nextRoom.type() == RoomType.BOSS) {
            if (!nextRoom.cleared()) {
                encounterRoomToActivate = nextRoomId;
            }
        }

        return new MoveResult(moved, encounterRoomToActivate);
    }

    private DungeonSessionState withMapAndPosition(DungeonSessionState s, DungeonMap map, String roomId) {
        return new DungeonSessionState(
            s.config(), map, roomId,
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }

    private DungeonSessionState withShields(DungeonSessionState s, int shields) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            s.health(), s.healthCap(), shields, s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }
}
```

(Note: the `withMapAndPosition` and `withShields` helpers exist because Java records have no built-in `with...` for partial copies. Tasks 7–8 will share similar helpers; keep them local to each service for now and consider extracting later if duplication grows tiresome.)

- [ ] **Step 3: Run the navigation tests**

Run: `./mvnw test -Dtest=DungeonNavigationServiceTests`
Expected: PASS.

- [ ] **Step 4: Stage but do not commit yet**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationServiceTests.java
```

The rest of the codebase still has compile errors. Move on to Task 7.

---

## Task 7: Rewrite `DungeonEncounterService` for roomId activation + Elite relic pick

The existing service already handles streak/shield/boss/gauntlet. The rewrite is mostly mechanical: activation now keys on roomId (since the encounter id is stored on the room), the room is marked cleared on success, and Elite clear queues a `PendingRelicPick`. Relic passive hooks are wired in Task 11+.

**Files:**
- Modify (rewrite): `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java`
- Modify (rewrite): `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterServiceTests.java`

- [ ] **Step 1: Write the failing test file first**

Replace the whole tests file with:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonEncounterServiceTests {

    private final DungeonEncounterService svc = new DungeonEncounterService();

    @Test
    void activateAt_combatRoomMarksEncounterActive() {
        DungeonSessionState state = singleCombatRoomState();
        DungeonSessionState result = svc.activateAt(state, "r1");
        assertThat(result.activeEncounterId()).isEqualTo("enc_0");
    }

    @Test
    void activateAt_eliteRoomSetsGauntletQueueAndActivatesFirstEncounter() {
        DungeonSessionState state = eliteRoomState();
        DungeonSessionState result = svc.activateAt(state, "r1");
        assertThat(result.activeEncounterId()).isEqualTo("elite_0_0");
        assertThat(result.gauntletQueue()).containsExactly("elite_0_1");
    }

    @Test
    void answerFlashcard_correctIncrementsStreakAndCorrectCount() {
        DungeonSessionState state = activeFlashcardState();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.streak()).isEqualTo(1);
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.activeEncounterId()).isNull();
        assertThat(result.map().room("r1").cleared()).isTrue();
    }

    @Test
    void answerFlashcard_wrongTakesDamageAndResetsStreak() {
        DungeonSessionState state = activeFlashcardStateWithStreak(2);
        DungeonSessionState result = svc.answerFlashcard(state, false);
        assertThat(result.streak()).isEqualTo(0);
        assertThat(result.health()).isEqualTo(state.health() - 1);
    }

    @Test
    void answerFlashcard_thirdCorrectAnswerBanksAShield() {
        DungeonSessionState state = activeFlashcardStateWithStreak(2);
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.streak()).isEqualTo(3);
        assertThat(result.shields()).isEqualTo(state.shields() + 1);
    }

    @Test
    void answerFlashcard_eliteGauntletClearSetsPendingRelicPick() {
        DungeonSessionState state = activeEliteFinalStepState();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.pendingRelicPick()).isNotNull();
        assertThat(result.pendingRelicPick().type()).isEqualTo(PendingPickType.ELITE);
        assertThat(result.pendingRelicPick().roomId()).isEqualTo("r1");
        assertThat(result.elitesCleared()).isEqualTo(1);
        assertThat(result.health()).isEqualTo(result.healthCap());
    }

    @Test
    void answerFlashcard_eliteGauntletMidWrongAnswerAbortsAndKeepsRoomActive() {
        DungeonSessionState state = activeEliteMidStepState();
        DungeonSessionState result = svc.answerFlashcard(state, false);
        assertThat(result.activeEncounterId()).isNull();
        assertThat(result.gauntletQueue()).isEmpty();
        assertThat(result.map().room("r1").cleared()).isFalse();
    }

    @Test
    void answerFlashcard_bossSequenceProgressesAcrossPrompts() {
        DungeonSessionState state = activeBossStateAtIndexZero();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.bossIndex()).isEqualTo(1);
        assertThat(result.activeEncounterId()).isEqualTo("boss_1");
        assertThat(result.won()).isFalse();
    }

    @Test
    void answerFlashcard_bossSequenceFinalPromptWinsTheRun() {
        DungeonSessionState state = activeBossStateAtFinalPrompt();
        DungeonSessionState result = svc.answerFlashcard(state, true);
        assertThat(result.won()).isTrue();
        assertThat(result.activeEncounterId()).isNull();
    }

    // ===== fixtures (see file header in task 6 for shape) =====
    // These are mechanical given the state shape; the implementer copies the helpers from
    // DungeonNavigationServiceTests and tweaks fields as needed for each scenario.
    // Place all six fixtures below; full code shown for the first, schemas for the rest.

    private DungeonSessionState singleCombatRoomState() {
        DungeonEncounter enc = DungeonEncounter.flashcard(
            "enc_0", false, 1L, "Q", "A", null, null);
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.COMBAT,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, "enc_0", List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r1", Map.of("enc_0", enc), List.of(), 0, null);
    }

    private DungeonSessionState eliteRoomState() {
        DungeonEncounter e0 = DungeonEncounter.flashcard("elite_0_0", false, 1L, "Q1", "A1", null, null);
        DungeonEncounter e1 = DungeonEncounter.flashcard("elite_0_1", false, 2L, "Q2", "A2", null, null);
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.ELITE,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, "elite_0_0",
            List.of("elite_0_0", "elite_0_1"),
            null, new TreasureOffer(List.of(RelicId.PHOENIX_FEATHER, RelicId.SPECTACLES)),
            null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r1",
            Map.of("elite_0_0", e0, "elite_0_1", e1), List.of(), 0, null);
    }

    private DungeonSessionState activeFlashcardState() {
        DungeonSessionState base = singleCombatRoomState();
        DungeonEncounter enc = base.encounters().get("enc_0").activate();
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(base.encounters());
        encs.put("enc_0", enc);
        return withEncountersAndActive(base, encs, "enc_0");
    }

    private DungeonSessionState activeFlashcardStateWithStreak(int streak) {
        DungeonSessionState s = activeFlashcardState();
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            streak, s.gauntletQueue(),
            Math.max(s.longestStreak(), streak), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }

    private DungeonSessionState activeEliteFinalStepState() {
        DungeonSessionState base = eliteRoomState();
        DungeonEncounter first = base.encounters().get("elite_0_0").activate().clear(List.of(), true);
        DungeonEncounter second = base.encounters().get("elite_0_1").activate();
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(base.encounters());
        encs.put("elite_0_0", first);
        encs.put("elite_0_1", second);
        DungeonSessionState withAct = withEncountersAndActive(base, encs, "elite_0_1");
        return new DungeonSessionState(
            withAct.config(), withAct.map(), withAct.currentRoomId(),
            withAct.encounters(), withAct.bossEncounterIds(), withAct.bossIndex(),
            withAct.activeEncounterId(),
            withAct.health(), withAct.healthCap(), withAct.shields(), withAct.shieldCap(),
            withAct.score(), withAct.answeredCount(), withAct.correctCount(),
            withAct.won(), withAct.defeated(),
            withAct.streak(), List.of(),
            withAct.longestStreak(), withAct.elitesCleared(), withAct.shieldsUsed(),
            withAct.luckyCoinsConsumed(), withAct.ownedRelics(), withAct.pendingRelicPick());
    }

    private DungeonSessionState activeEliteMidStepState() {
        DungeonSessionState base = eliteRoomState();
        DungeonEncounter first = base.encounters().get("elite_0_0").activate();
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(base.encounters());
        encs.put("elite_0_0", first);
        DungeonSessionState withAct = withEncountersAndActive(base, encs, "elite_0_0");
        return new DungeonSessionState(
            withAct.config(), withAct.map(), withAct.currentRoomId(),
            withAct.encounters(), withAct.bossEncounterIds(), withAct.bossIndex(),
            withAct.activeEncounterId(),
            withAct.health(), withAct.healthCap(), withAct.shields(), withAct.shieldCap(),
            withAct.score(), withAct.answeredCount(), withAct.correctCount(),
            withAct.won(), withAct.defeated(),
            withAct.streak(), List.of("elite_0_1"),
            withAct.longestStreak(), withAct.elitesCleared(), withAct.shieldsUsed(),
            withAct.luckyCoinsConsumed(), withAct.ownedRelics(), withAct.pendingRelicPick());
    }

    private DungeonSessionState activeBossStateAtIndexZero() {
        DungeonEncounter b0 = DungeonEncounter.flashcard("boss_0", true, 10L, "B0", "BA0", null, null).activate();
        DungeonEncounter b1 = DungeonEncounter.flashcard("boss_1", true, 11L, "B1", "BA1", null, null);
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.BOSS,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r1", 3);
        return baseState(map, "r1", Map.of("boss_0", b0, "boss_1", b1),
            List.of("boss_0", "boss_1"), 0, "boss_0");
    }

    private DungeonSessionState activeBossStateAtFinalPrompt() {
        DungeonSessionState base = activeBossStateAtIndexZero();
        return new DungeonSessionState(
            base.config(), base.map(), base.currentRoomId(),
            base.encounters(), base.bossEncounterIds(), 1,
            "boss_1",
            base.health(), base.healthCap(), base.shields(), base.shieldCap(),
            base.score(), base.answeredCount(), base.correctCount(),
            base.won(), base.defeated(),
            base.streak(), base.gauntletQueue(),
            base.longestStreak(), base.elitesCleared(), base.shieldsUsed(),
            base.luckyCoinsConsumed(), base.ownedRelics(), base.pendingRelicPick());
    }

    private DungeonSessionState baseState(DungeonMap map, String currentRoomId,
                                            Map<String, DungeonEncounter> encs,
                                            List<String> bossIds, int bossIndex,
                                            String activeEncounterId) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return new DungeonSessionState(
            config, map, currentRoomId,
            encs, bossIds, bossIndex, activeEncounterId,
            5, 5, 0, 2,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);
    }

    private DungeonSessionState withEncountersAndActive(DungeonSessionState s,
                                                          Map<String, DungeonEncounter> encs,
                                                          String activeId) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            encs, s.bossEncounterIds(), s.bossIndex(),
            activeId,
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }
}
```

- [ ] **Step 2: Implement the new `DungeonEncounterService`**

Replace the whole file with:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DungeonEncounterService {

    static final int STREAK_FOR_SHIELD = 3;
    static final int WRONG_ANSWER_DAMAGE = 1;
    static final int COMBAT_CLEAR_SCORE = 10;
    static final int ELITE_CLEAR_SCORE = 50;
    static final int BOSS_PROMPT_SCORE = 20;

    public DungeonSessionState activateAt(DungeonSessionState state, String roomId) {
        if (state.activeEncounterId() != null) return state;
        DungeonRoom room = state.map().room(roomId);
        if (room == null) return state;
        if (room.cleared()) return state;

        return switch (room.type()) {
            case COMBAT -> activateNormal(state, room.encounterId(), false);
            case ELITE -> activateElite(state, room);
            case BOSS -> activateBoss(state);
            default -> state;
        };
    }

    public DungeonSessionState answerFlashcard(DungeonSessionState state, boolean gotIt) {
        DungeonEncounter enc = state.activeEncounter();
        if (enc == null) return state;
        if (enc.type() == DungeonEncounterType.QUIZ || enc.type() == DungeonEncounterType.BOSS_QUIZ) return state;
        return processAnswer(state, enc, gotIt ? List.of(1) : List.of(0), gotIt);
    }

    public DungeonSessionState answerQuiz(DungeonSessionState state, List<Integer> selectedOptions) {
        DungeonEncounter enc = state.activeEncounter();
        if (enc == null) return state;
        if (enc.type() == DungeonEncounterType.FLASHCARD || enc.type() == DungeonEncounterType.BOSS_FLASHCARD) return state;
        List<Integer> safe = selectedOptions == null ? List.of() : selectedOptions;
        QuizQuestion q = enc.quizQuestion();
        boolean correct = new HashSet<>(safe).equals(new HashSet<>(q.correctOptionIndices()));
        return processAnswer(state, enc, safe, correct);
    }

    // ===== activation paths =====

    private DungeonSessionState activateNormal(DungeonSessionState state, String encounterId, boolean boss) {
        if (encounterId == null) return state;
        DungeonEncounter enc = state.encounters().get(encounterId);
        if (enc == null || enc.status() != DungeonEncounterStatus.PENDING) return state;
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
        encs.put(encounterId, enc.activate());
        return withEncountersAndActive(state, encs, encounterId, state.gauntletQueue());
    }

    private DungeonSessionState activateElite(DungeonSessionState state, DungeonRoom eliteRoom) {
        List<String> group = eliteRoom.gauntletGroup();
        if (group.isEmpty()) return state;
        String firstId = group.get(0);
        DungeonEncounter first = state.encounters().get(firstId);
        if (first == null) return state;
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
        encs.put(firstId, first.activate());
        List<String> remaining = new ArrayList<>(group.subList(1, group.size()));
        return withEncountersAndActive(state, encs, firstId, remaining);
    }

    private DungeonSessionState activateBoss(DungeonSessionState state) {
        if (state.bossEncounterIds().isEmpty()) return state;
        String bossId = state.bossEncounterIds().get(state.bossIndex());
        return activateNormal(state, bossId, true);
    }

    // ===== answer pipeline =====

    private DungeonSessionState processAnswer(DungeonSessionState state, DungeonEncounter encounter,
                                                List<Integer> answer, boolean correct) {
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
        encs.put(encounter.id(), encounter.clear(answer, correct));

        int answeredCount = state.answeredCount() + 1;
        int correctCount = state.correctCount() + (correct ? 1 : 0);
        int newStreak = correct ? state.streak() + 1 : 0;
        int newShields = state.shields();
        if (correct && newStreak % STREAK_FOR_SHIELD == 0 && newShields < state.shieldCap()) {
            newShields++;
        }
        int newLongest = Math.max(state.longestStreak(), newStreak);
        int newScore = state.score();
        if (correct) {
            newScore += encounter.boss() ? BOSS_PROMPT_SCORE : COMBAT_CLEAR_SCORE;
        }

        DungeonSessionState working = new DungeonSessionState(
            state.config(), state.map(), state.currentRoomId(),
            Map.copyOf(encs), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            state.health(), state.healthCap(), newShields, state.shieldCap(),
            newScore, answeredCount, correctCount,
            state.won(), state.defeated(),
            newStreak, state.gauntletQueue(),
            newLongest, state.elitesCleared(), state.shieldsUsed(),
            state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());

        if (!correct) {
            working = DungeonDamage.takeDamage(working, WRONG_ANSWER_DAMAGE);
        }

        // Elite-gauntlet branch
        if (isInGauntletRoom(working) || !working.gauntletQueue().isEmpty()) {
            return resolveGauntlet(working, encounter, correct);
        }

        // Boss branch
        if (encounter.boss() && !working.defeated()) {
            return resolveBoss(working);
        }

        // Normal combat: clear the room
        return clearCombatRoom(working);
    }

    private boolean isInGauntletRoom(DungeonSessionState state) {
        DungeonRoom room = state.currentRoom();
        return room != null && room.type() == RoomType.ELITE && !room.cleared();
    }

    private DungeonSessionState resolveGauntlet(DungeonSessionState state,
                                                  DungeonEncounter encounter, boolean correct) {
        DungeonRoom room = state.currentRoom();
        List<String> group = room == null ? List.of() : room.gauntletGroup();

        if (!correct) {
            Map<String, DungeonEncounter> reset = new LinkedHashMap<>(state.encounters());
            for (String encId : group) {
                DungeonEncounter e = reset.get(encId);
                if (e != null) {
                    reset.put(encId, new DungeonEncounter(
                        e.id(), e.type(), DungeonEncounterStatus.PENDING, e.boss(),
                        e.flashcardId(), e.frontText(), e.backText(),
                        e.frontImageUrl(), e.backImageUrl(), e.quizQuestion(),
                        List.of(), null));
                }
            }
            return withEncountersAndActive(state, reset, null, List.of());
        }

        if (!state.gauntletQueue().isEmpty()) {
            String nextId = state.gauntletQueue().get(0);
            List<String> remaining = new ArrayList<>(state.gauntletQueue().subList(1, state.gauntletQueue().size()));
            Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
            DungeonEncounter next = encs.get(nextId);
            if (next != null && next.status() == DungeonEncounterStatus.PENDING) {
                encs.put(nextId, next.activate());
            }
            return withEncountersAndActive(state, encs, nextId, remaining);
        }

        // Gauntlet fully cleared: full heal + shield + queue ELITE relic pick + clear room
        DungeonSessionState healed = DungeonDamage.heal(state, state.healthCap());
        int grantedShields = Math.min(state.shieldCap(), healed.shields() + 1);
        int newElites = healed.elitesCleared() + 1;
        int eliteScore = healed.score() + ELITE_CLEAR_SCORE;

        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(healed.map().rooms());
        DungeonRoom cleared = room.withCleared(true);
        rooms.put(cleared.id(), cleared);
        DungeonMap nextMap = new DungeonMap(
            rooms, healed.map().entranceRoomId(), healed.map().bossRoomId(), healed.map().lattice());

        PendingRelicPick pick = new PendingRelicPick(
            PendingPickType.ELITE, cleared.id(),
            cleared.eliteOffer() == null ? List.of() : cleared.eliteOffer().relics(),
            null);

        return new DungeonSessionState(
            healed.config(), nextMap, healed.currentRoomId(),
            healed.encounters(), healed.bossEncounterIds(), healed.bossIndex(),
            null,
            healed.health(), healed.healthCap(), grantedShields, healed.shieldCap(),
            eliteScore, healed.answeredCount(), healed.correctCount(),
            healed.won(), healed.defeated(),
            healed.streak(), List.of(),
            healed.longestStreak(), newElites, healed.shieldsUsed(),
            healed.luckyCoinsConsumed(), healed.ownedRelics(), pick);
    }

    private DungeonSessionState resolveBoss(DungeonSessionState state) {
        int nextIndex = state.bossIndex() + 1;
        if (nextIndex < state.bossEncounterIds().size()) {
            String nextBossId = state.bossEncounterIds().get(nextIndex);
            Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
            DungeonEncounter next = encs.get(nextBossId);
            if (next != null && next.status() == DungeonEncounterStatus.PENDING) {
                encs.put(nextBossId, next.activate());
            }
            return new DungeonSessionState(
                state.config(), state.map(), state.currentRoomId(),
                Map.copyOf(encs), state.bossEncounterIds(), nextIndex,
                nextBossId,
                state.health(), state.healthCap(), state.shields(), state.shieldCap(),
                state.score(), state.answeredCount(), state.correctCount(),
                state.won(), state.defeated(),
                state.streak(), state.gauntletQueue(),
                state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
                state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());
        }
        // Final boss prompt cleared — mark won and clear boss room
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        DungeonRoom bossRoom = rooms.get(state.map().bossRoomId());
        if (bossRoom != null) {
            rooms.put(bossRoom.id(), bossRoom.withCleared(true));
        }
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());
        return new DungeonSessionState(
            state.config(), nextMap, state.currentRoomId(),
            state.encounters(), state.bossEncounterIds(), nextIndex,
            null,
            state.health(), state.healthCap(), state.shields(), state.shieldCap(),
            state.score(), state.answeredCount(), state.correctCount(),
            true, state.defeated(),
            state.streak(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
            state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());
    }

    private DungeonSessionState clearCombatRoom(DungeonSessionState state) {
        DungeonRoom room = state.currentRoom();
        if (room == null) return state;
        Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
        rooms.put(room.id(), room.withCleared(true));
        DungeonMap nextMap = new DungeonMap(
            rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());
        return new DungeonSessionState(
            state.config(), nextMap, state.currentRoomId(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            null,
            state.health(), state.healthCap(), state.shields(), state.shieldCap(),
            state.score(), state.answeredCount(), state.correctCount(),
            state.won(), state.defeated(),
            state.streak(), state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
            state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());
    }

    private DungeonSessionState withEncountersAndActive(DungeonSessionState s,
                                                          Map<String, DungeonEncounter> encs,
                                                          String activeId,
                                                          List<String> gauntletQueue) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            Map.copyOf(encs), s.bossEncounterIds(), s.bossIndex(),
            activeId,
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), gauntletQueue,
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }
}
```

- [ ] **Step 3: Run the encounter tests**

Run: `./mvnw test -Dtest=DungeonEncounterServiceTests`
Expected: PASS.

- [ ] **Step 4: Stage but do not commit yet**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterServiceTests.java
```

The codebase still has compile errors in `DungeonSessionService` and `DungeonDamage`. Continue to Task 8.

---

## Task 8: Update `DungeonDamage` for per-state caps (relic hooks come in Task 13)

`DungeonDamage` previously used a constant `STARTING_HEALTH_CAP`. With Iron Plate and Buckler, the cap moves to `state.healthCap()` / `state.shieldCap()`. The relic-aware hooks (Lucky Coin, Phoenix Feather) are added in Task 13.

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java`
- Modify (rewrite): `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java`

- [ ] **Step 1: Rewrite `DungeonDamage`**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

public final class DungeonDamage {

    public static final int STARTING_HEALTH = 5;
    public static final int STARTING_HEALTH_CAP = 5;
    public static final int STARTING_SHIELD_CAP = 2;

    private DungeonDamage() {}

    public static DungeonSessionState takeDamage(DungeonSessionState state, int amount) {
        if (state.shields() > 0) {
            return rebuild(state, state.health(), state.shields() - 1,
                state.defeated(), state.shieldsUsed() + 1);
        }
        int newHealth = Math.max(0, state.health() - amount);
        boolean defeated = state.defeated() || newHealth <= 0;
        return rebuild(state, newHealth, state.shields(), defeated, state.shieldsUsed());
    }

    public static DungeonSessionState heal(DungeonSessionState state, int amount) {
        int newHealth = Math.min(state.healthCap(), state.health() + amount);
        return rebuild(state, newHealth, state.shields(), state.defeated(), state.shieldsUsed());
    }

    private static DungeonSessionState rebuild(DungeonSessionState s, int hp, int shields,
                                                 boolean defeated, int shieldsUsed) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            hp, s.healthCap(), shields, s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), defeated,
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), shieldsUsed,
            s.luckyCoinsConsumed(), s.ownedRelics(), s.pendingRelicPick());
    }
}
```

- [ ] **Step 2: Rewrite `DungeonDamageTests`**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonDamageTests {

    @Test
    void takeDamage_consumesShieldFirst() {
        DungeonSessionState s = state(5, 5, 1, 2);
        DungeonSessionState after = DungeonDamage.takeDamage(s, 1);
        assertThat(after.health()).isEqualTo(5);
        assertThat(after.shields()).isEqualTo(0);
        assertThat(after.shieldsUsed()).isEqualTo(1);
    }

    @Test
    void takeDamage_reducesHealthWhenNoShield() {
        DungeonSessionState s = state(5, 5, 0, 2);
        DungeonSessionState after = DungeonDamage.takeDamage(s, 1);
        assertThat(after.health()).isEqualTo(4);
        assertThat(after.defeated()).isFalse();
    }

    @Test
    void takeDamage_setsDefeatedAtZeroHealth() {
        DungeonSessionState s = state(1, 5, 0, 2);
        DungeonSessionState after = DungeonDamage.takeDamage(s, 1);
        assertThat(after.health()).isEqualTo(0);
        assertThat(after.defeated()).isTrue();
    }

    @Test
    void heal_capsAtHealthCap() {
        DungeonSessionState s = state(4, 5, 0, 2);
        DungeonSessionState after = DungeonDamage.heal(s, 99);
        assertThat(after.health()).isEqualTo(5);
    }

    @Test
    void heal_respectsHigherHealthCapFromIronPlate() {
        DungeonSessionState s = state(5, 7, 0, 2);
        DungeonSessionState after = DungeonDamage.heal(s, 99);
        assertThat(after.health()).isEqualTo(7);
    }

    private DungeonSessionState state(int hp, int hpCap, int shields, int shieldCap) {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0), "r0", "r0", 3);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return new DungeonSessionState(
            config, map, "r0",
            Map.of(), List.of(), 0, null,
            hp, hpCap, shields, shieldCap,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);
    }
}
```

- [ ] **Step 3: Run the damage tests**

Run: `./mvnw test -Dtest=DungeonDamageTests`
Expected: PASS.

- [ ] **Step 4: Stage**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java
```

---

## Task 9: Rewrite `DungeonSessionService` orchestrator (restores compilability)

The orchestrator is the call-site of all the rewritten services. After this task, the entire backend compiles again.

**Files:**
- Modify (rewrite): `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java`
- Modify (rewrite): `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionServiceTests.java`

- [ ] **Step 1: Rewrite `DungeonSessionService`**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.Deck;
import com.HendrikHoemberg.StudyHelper.entity.Flashcard;
import com.HendrikHoemberg.StudyHelper.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DungeonSessionService {

    private final DeckService deckService;
    private final FlashcardService flashcardService;
    private final QuizSessionService quizSessionService;
    private final DungeonMapGenerator dungeonMapGenerator;
    private final DungeonNavigationService navigationService;
    private final DungeonEncounterService encounterService;
    private final DungeonRelicService relicService;

    public DungeonSessionService(DeckService deckService,
                                 FlashcardService flashcardService,
                                 QuizSessionService quizSessionService,
                                 DungeonMapGenerator dungeonMapGenerator,
                                 DungeonNavigationService navigationService,
                                 DungeonEncounterService encounterService,
                                 DungeonRelicService relicService) {
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.quizSessionService = quizSessionService;
        this.dungeonMapGenerator = dungeonMapGenerator;
        this.navigationService = navigationService;
        this.encounterService = encounterService;
        this.relicService = relicService;
    }

    @Transactional(readOnly = true)
    public DungeonSessionState createFlashcardDungeon(List<Long> selectedDeckIds, DungeonSize size, User user) {
        List<Long> normalizedIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(normalizedIds, user);
        List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
        validateSize(size, flashcards.size(), "flashcards");

        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>();
        List<String> normalEncounterIds = new ArrayList<>();
        List<String> bossEncounterIds = new ArrayList<>();
        List<List<String>> eliteGauntlets = buildFlashcardEncounters(
            flashcards, size, encounters, normalEncounterIds, bossEncounterIds);

        DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds, eliteGauntlets);
        return initialState(
            new DungeonConfig(DungeonMode.FLASHCARDS, size, normalizedIds,
                QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
            map, encounters, bossEncounterIds);
    }

    @Transactional
    public DungeonSessionState createAiQuizDungeon(List<Long> selectedDeckIds,
                                                    DungeonSize size,
                                                    QuizQuestionMode questionMode,
                                                    Difficulty difficulty,
                                                    String additionalInstructions,
                                                    HttpServletRequest request,
                                                    User user) throws Exception {
        List<Long> normalizedIds = StudySourceSupport.normalizeIds(selectedDeckIds);
        List<Deck> decks = deckService.getValidatedDecksInRequestedOrder(normalizedIds, user);
        List<Flashcard> flashcards = flashcardService.getFlashcardsFlattened(decks);
        validateSize(size, flashcards.size(), "flashcards");

        int questionCount = size.totalPrompts();
        QuizSessionState quizState = quizSessionService.createSession(
            normalizedIds, List.of(), request, questionCount,
            questionMode, difficulty, additionalInstructions, user);
        List<QuizQuestion> questions = quizState.questions();

        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>();
        List<String> normalEncounterIds = new ArrayList<>();
        List<String> bossEncounterIds = new ArrayList<>();
        List<List<String>> eliteGauntlets = buildQuizEncounters(
            questions, size, encounters, normalEncounterIds, bossEncounterIds);

        DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds, eliteGauntlets);
        return initialState(
            new DungeonConfig(DungeonMode.AI_QUIZ, size, normalizedIds,
                questionMode, difficulty, additionalInstructions),
            map, encounters, bossEncounterIds);
    }

    public DungeonSessionState move(DungeonSessionState state, DungeonDirection direction) {
        DungeonNavigationService.MoveResult result = navigationService.move(state, direction);
        DungeonSessionState moved = result.state();

        DungeonRoom destination = moved.currentRoom();

        // Set pending picks for TREASURE / SHOP / SECRET on first entry
        if (destination != null && !destination.cleared() && moved.pendingRelicPick() == null) {
            PendingRelicPick newPick = pickForRoom(destination);
            if (newPick != null) {
                moved = withPendingPick(moved, newPick);
            }
        }

        if (result.activatedEncounterRoomId() != null) {
            moved = encounterService.activateAt(moved, result.activatedEncounterRoomId());
        }
        return moved;
    }

    public DungeonSessionState answerFlashcard(DungeonSessionState state, boolean gotIt) {
        return encounterService.answerFlashcard(state, gotIt);
    }

    public DungeonSessionState answerQuiz(DungeonSessionState state, List<Integer> selectedOptions) {
        return encounterService.answerQuiz(state, selectedOptions);
    }

    public DungeonSessionState pickRelic(DungeonSessionState state, RelicId relicId) {
        return relicService.pick(state, relicId);
    }

    public DungeonSessionState buyRelic(DungeonSessionState state, RelicId relicId) {
        return relicService.buy(state, relicId);
    }

    public DungeonSessionState skipShop(DungeonSessionState state) {
        return relicService.skipShop(state);
    }

    public DungeonRunStats buildStats(DungeonSessionState state) {
        return new DungeonRunStats(
            state.config().mode(),
            state.config().size(),
            state.config().size().totalPrompts(),
            state.answeredCount(),
            state.correctCount(),
            state.health(),
            state.won(),
            state.longestStreak(),
            state.elitesCleared(),
            state.shieldsUsed(),
            state.ownedRelics().size());
    }

    // ===== helpers =====

    private List<List<String>> buildFlashcardEncounters(List<Flashcard> flashcards, DungeonSize size,
                                                          Map<String, DungeonEncounter> encs,
                                                          List<String> normalIds, List<String> bossIds) {
        for (int i = 0; i < size.normalEncounterCount(); i++) {
            Flashcard card = flashcards.get(i);
            String id = "fc_" + i;
            encs.put(id, DungeonEncounter.flashcard(id, false, card.getId(),
                card.getFrontText(), card.getBackText(),
                card.getFrontImageFilename(), card.getBackImageFilename()));
            normalIds.add(id);
        }
        int bossStart = size.normalEncounterCount();
        for (int i = 0; i < size.bossPromptCount(); i++) {
            Flashcard card = flashcards.get(bossStart + i);
            String id = "fc_boss_" + i;
            encs.put(id, DungeonEncounter.flashcard(id, true, card.getId(),
                card.getFrontText(), card.getBackText(),
                card.getFrontImageFilename(), card.getBackImageFilename()));
            bossIds.add(id);
        }
        int eliteStart = bossStart + size.bossPromptCount();
        List<List<String>> groups = new ArrayList<>();
        int cardsPerGroup = size.cardsPerEliteGauntlet();
        for (int g = 0; g < size.eliteGauntletCount(); g++) {
            List<String> group = new ArrayList<>();
            for (int c = 0; c < cardsPerGroup; c++) {
                int cardIdx = eliteStart + (g * cardsPerGroup) + c;
                Flashcard card = flashcards.get(cardIdx);
                String id = "fc_elite_" + g + "_" + c;
                encs.put(id, DungeonEncounter.flashcard(id, false, card.getId(),
                    card.getFrontText(), card.getBackText(),
                    card.getFrontImageFilename(), card.getBackImageFilename()));
                group.add(id);
            }
            groups.add(group);
        }
        return groups;
    }

    private List<List<String>> buildQuizEncounters(List<QuizQuestion> questions, DungeonSize size,
                                                     Map<String, DungeonEncounter> encs,
                                                     List<String> normalIds, List<String> bossIds) {
        for (int i = 0; i < size.normalEncounterCount(); i++) {
            String id = "qz_" + i;
            encs.put(id, DungeonEncounter.quiz(id, false, questions.get(i)));
            normalIds.add(id);
        }
        int bossStart = size.normalEncounterCount();
        for (int i = 0; i < size.bossPromptCount(); i++) {
            String id = "qz_boss_" + i;
            encs.put(id, DungeonEncounter.quiz(id, true, questions.get(bossStart + i)));
            bossIds.add(id);
        }
        int eliteStart = bossStart + size.bossPromptCount();
        List<List<String>> groups = new ArrayList<>();
        int cardsPerGroup = size.cardsPerEliteGauntlet();
        for (int g = 0; g < size.eliteGauntletCount(); g++) {
            List<String> group = new ArrayList<>();
            for (int c = 0; c < cardsPerGroup; c++) {
                int idx = eliteStart + (g * cardsPerGroup) + c;
                String id = "qz_elite_" + g + "_" + c;
                encs.put(id, DungeonEncounter.quiz(id, false, questions.get(idx)));
                group.add(id);
            }
            groups.add(group);
        }
        return groups;
    }

    private DungeonSessionState initialState(DungeonConfig config, DungeonMap map,
                                              Map<String, DungeonEncounter> encounters,
                                              List<String> bossEncounterIds) {
        return new DungeonSessionState(
            config, map, map.entranceRoomId(),
            Map.copyOf(encounters), List.copyOf(bossEncounterIds),
            0, null,
            DungeonDamage.STARTING_HEALTH, DungeonDamage.STARTING_HEALTH_CAP,
            0, DungeonDamage.STARTING_SHIELD_CAP,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);
    }

    private PendingRelicPick pickForRoom(DungeonRoom room) {
        return switch (room.type()) {
            case TREASURE -> room.treasureOffer() == null ? null :
                new PendingRelicPick(PendingPickType.TREASURE, room.id(), room.treasureOffer().relics(), null);
            case SHOP -> room.shopOffer() == null ? null :
                new PendingRelicPick(PendingPickType.SHOP, room.id(), List.of(), room.shopOffer());
            case SECRET -> {
                if (room.secretReward() instanceof SecretReward.RelicReward r) {
                    yield new PendingRelicPick(PendingPickType.SECRET, room.id(), List.of(r.relic()), null);
                }
                yield null;  // Bundle reward is auto-applied in Task 14; null here means "no pick UI"
            }
            default -> null;
        };
    }

    private DungeonSessionState withPendingPick(DungeonSessionState s, PendingRelicPick pick) {
        return new DungeonSessionState(
            s.config(), s.map(), s.currentRoomId(),
            s.encounters(), s.bossEncounterIds(), s.bossIndex(),
            s.activeEncounterId(),
            s.health(), s.healthCap(), s.shields(), s.shieldCap(),
            s.score(), s.answeredCount(), s.correctCount(),
            s.won(), s.defeated(),
            s.streak(), s.gauntletQueue(),
            s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
            s.luckyCoinsConsumed(), s.ownedRelics(), pick);
    }

    private void validateSize(DungeonSize size, int usableItems, String unit) {
        if (!size.isAvailableFor(usableItems)) {
            throw new IllegalArgumentException(
                "Not enough " + unit + " available for " + capitalize(size.name())
                    + " dungeon. Need " + size.totalPrompts() + ", have " + usableItems + ".");
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}
```

- [ ] **Step 2: Slim `DungeonSessionServiceTests`**

Replace the existing test file with focused orchestration tests. The deep behaviors are now covered in service-level tests; this file verifies `move` correctly routes to navigation + encounter, and `buildStats` includes `relicsAcquired`.

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DungeonSessionServiceTests {

    @Test
    void move_routesNavigationResultAndActivatesEncounter() {
        DungeonNavigationService nav = mock(DungeonNavigationService.class);
        DungeonEncounterService enc = mock(DungeonEncounterService.class);
        DungeonRelicService relic = mock(DungeonRelicService.class);
        DungeonSessionState before = stateOnRoom("r0", RoomType.ENTRANCE);
        DungeonSessionState afterMove = stateOnRoom("r1", RoomType.COMBAT);
        DungeonSessionState afterActivate = afterMove;
        when(nav.move(before, DungeonDirection.RIGHT))
            .thenReturn(new DungeonNavigationService.MoveResult(afterMove, "r1"));
        when(enc.activateAt(afterMove, "r1")).thenReturn(afterActivate);

        DungeonSessionService svc = new DungeonSessionService(
            null, null, null, null, nav, enc, relic);
        DungeonSessionState result = svc.move(before, DungeonDirection.RIGHT);

        assertThat(result).isSameAs(afterActivate);
        verify(enc).activateAt(afterMove, "r1");
    }

    @Test
    void move_intoTreasureSetsPendingPick() {
        DungeonNavigationService nav = mock(DungeonNavigationService.class);
        DungeonEncounterService enc = mock(DungeonEncounterService.class);
        DungeonRelicService relic = mock(DungeonRelicService.class);

        TreasureOffer offer = new TreasureOffer(List.of(RelicId.IRON_PLATE, RelicId.BUCKLER, RelicId.LUCKY_CHARM));
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.TREASURE,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, null, List.of(), offer, null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r0", 3);
        DungeonSessionState before = stateOnRoomMap(map, "r0");
        DungeonSessionState afterMove = stateOnRoomMap(map, "r1");
        when(nav.move(any(), any()))
            .thenReturn(new DungeonNavigationService.MoveResult(afterMove, null));

        DungeonSessionService svc = new DungeonSessionService(
            null, null, null, null, nav, enc, relic);
        DungeonSessionState result = svc.move(before, DungeonDirection.RIGHT);

        assertThat(result.pendingRelicPick()).isNotNull();
        assertThat(result.pendingRelicPick().type()).isEqualTo(PendingPickType.TREASURE);
        assertThat(result.pendingRelicPick().offer()).hasSize(3);
        verifyNoInteractions(enc);
    }

    @Test
    void buildStats_includesRelicsAcquired() {
        DungeonSessionService svc = new DungeonSessionService(
            null, null, null, null, null, null, null);
        DungeonSessionState s = stateWithRelics(List.of(RelicId.IRON_PLATE, RelicId.BUCKLER));
        DungeonRunStats stats = svc.buildStats(s);
        assertThat(stats.relicsAcquired()).isEqualTo(2);
    }

    // ===== fixtures =====

    private DungeonSessionState stateOnRoom(String roomId, RoomType type) {
        DungeonRoom room = new DungeonRoom(roomId, type,
            Map.of(), new GridPos(0, 0),
            true, false, null, List.of(), null, null, null, null);
        DungeonMap map = new DungeonMap(Map.of(roomId, room), roomId, roomId, 3);
        return stateOnRoomMap(map, roomId);
    }

    private DungeonSessionState stateOnRoomMap(DungeonMap map, String roomId) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        return new DungeonSessionState(
            config, map, roomId,
            Map.of(), List.of(), 0, null,
            5, 5, 0, 2,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), null);
    }

    private DungeonSessionState stateWithRelics(List<RelicId> relics) {
        DungeonSessionState base = stateOnRoom("r0", RoomType.ENTRANCE);
        return new DungeonSessionState(
            base.config(), base.map(), base.currentRoomId(),
            base.encounters(), base.bossEncounterIds(), base.bossIndex(),
            base.activeEncounterId(),
            base.health(), base.healthCap(), base.shields(), base.shieldCap(),
            base.score(), base.answeredCount(), base.correctCount(),
            base.won(), base.defeated(),
            base.streak(), base.gauntletQueue(),
            base.longestStreak(), base.elitesCleared(), base.shieldsUsed(),
            base.luckyCoinsConsumed(), relics, base.pendingRelicPick());
    }
}
```

- [ ] **Step 3: Create a stub `DungeonRelicService` so the orchestrator compiles**

The full implementation arrives in Task 10. For now, create a no-op stub so `DungeonSessionService` compiles end-to-end.

Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonRelicService.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;
import com.HendrikHoemberg.StudyHelper.dto.RelicId;
import org.springframework.stereotype.Service;

@Service
public class DungeonRelicService {

    public DungeonSessionState pick(DungeonSessionState state, RelicId relicId) {
        return state;
    }

    public DungeonSessionState buy(DungeonSessionState state, RelicId relicId) {
        return state;
    }

    public DungeonSessionState skipShop(DungeonSessionState state) {
        return state;
    }
}
```

- [ ] **Step 4: Compile end-to-end**

Run: `./mvnw compile`
Expected: BUILD SUCCESS. If `DungeonController` still has errors (it references `playerPosition`/`mapTiles`/etc), that's expected — fix in Task 12.

If compile fails *outside* `DungeonController.java`, debug before moving on.

- [ ] **Step 5: Run the dungeon test suite**

Run: `./mvnw test -Dtest='Dungeon*'`
Expected: all PASS (we have not yet wired controller endpoints; controller-side tests stay in Task 12).

- [ ] **Step 6: Commit the destructive swap as a single coherent commit**

```bash
git add -A
git commit -m "feat(dungeon): replace tile grid with room-graph data model and services

Replace DungeonPosition/DungeonTile/DungeonTileType with DungeonRoom/RoomType.
Rewrite DungeonMap as a graph of rooms connected by doors. Rewrite map
generator (random-walk room placement, type assignment, offers), navigation
service (door-based move), encounter service (room-id activation, elite
relic pick), session service orchestration, damage helper (per-state caps).
Stub DungeonRelicService until Task 10 fills it in. Controller still needs
updating (Task 12)."
```

---

## Task 10: Implement `DungeonRelicService` with apply-on-acquire effects

Replaces the stub from Task 9. Owns acquisition (pick / buy / skipShop) and immediately-applied effects: Iron Plate (+1 healthCap, heal +1), Buckler (+1 shieldCap, +1 shield), Map Sense (no state effect — its impact is on minimap rendering, hooked in Task 14). Passive hooks remain in their respective service files.

**Files:**
- Modify (rewrite): `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonRelicService.java`
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonRelicServiceTests.java`

- [ ] **Step 1: Write the failing test file**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonRelicServiceTests {

    private final DungeonRelicService svc = new DungeonRelicService();

    @Test
    void pick_addsRelicAndClearsPendingPick() {
        DungeonSessionState state = stateWithPick(PendingPickType.TREASURE,
            List.of(RelicId.LUCKY_CHARM, RelicId.SHARP_FOCUS, RelicId.COMPASS));
        DungeonSessionState after = svc.pick(state, RelicId.LUCKY_CHARM);
        assertThat(after.ownedRelics()).containsExactly(RelicId.LUCKY_CHARM);
        assertThat(after.pendingRelicPick()).isNull();
    }

    @Test
    void pick_relicNotInOfferIsRejected() {
        DungeonSessionState state = stateWithPick(PendingPickType.TREASURE,
            List.of(RelicId.LUCKY_CHARM, RelicId.SHARP_FOCUS, RelicId.COMPASS));
        DungeonSessionState after = svc.pick(state, RelicId.IRON_PLATE);
        assertThat(after).isSameAs(state);
    }

    @Test
    void pick_ironPlateIncreasesHealthCapAndHeals() {
        DungeonSessionState state = stateWithPick(PendingPickType.TREASURE,
            List.of(RelicId.IRON_PLATE, RelicId.BUCKLER, RelicId.COMPASS));
        DungeonSessionState after = svc.pick(state, RelicId.IRON_PLATE);
        assertThat(after.healthCap()).isEqualTo(state.healthCap() + 1);
        assertThat(after.health()).isEqualTo(Math.min(state.health() + 1, after.healthCap()));
    }

    @Test
    void pick_bucklerIncreasesShieldCapAndGrantsShield() {
        DungeonSessionState state = stateWithPick(PendingPickType.TREASURE,
            List.of(RelicId.IRON_PLATE, RelicId.BUCKLER, RelicId.COMPASS));
        DungeonSessionState after = svc.pick(state, RelicId.BUCKLER);
        assertThat(after.shieldCap()).isEqualTo(state.shieldCap() + 1);
        assertThat(after.shields()).isEqualTo(state.shields() + 1);
    }

    @Test
    void buy_deductsScoreAndAddsRelic() {
        DungeonSessionState state = shopStateWithScore(150);
        DungeonSessionState after = svc.buy(state, RelicId.LUCKY_CHARM);
        assertThat(after.ownedRelics()).contains(RelicId.LUCKY_CHARM);
        assertThat(after.score()).isEqualTo(150 - 75);
    }

    @Test
    void buy_rejectsWhenInsufficientScore() {
        DungeonSessionState state = shopStateWithScore(50);
        DungeonSessionState after = svc.buy(state, RelicId.LUCKY_CHARM);
        assertThat(after).isSameAs(state);
    }

    @Test
    void buy_doesNotClearShopPick_canMakeMultiplePurchases() {
        DungeonSessionState state = shopStateWithScore(150);
        DungeonSessionState after = svc.buy(state, RelicId.LUCKY_CHARM);
        assertThat(after.pendingRelicPick()).isNotNull();
        assertThat(after.pendingRelicPick().type()).isEqualTo(PendingPickType.SHOP);
    }

    @Test
    void skipShop_marksShopClearedAndClearsPick() {
        DungeonSessionState state = shopStateWithScore(150);
        DungeonSessionState after = svc.skipShop(state);
        assertThat(after.pendingRelicPick()).isNull();
        assertThat(after.map().room("r1").cleared()).isTrue();
    }

    // ===== fixtures =====

    private DungeonSessionState stateWithPick(PendingPickType type, List<RelicId> offer) {
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.TREASURE,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, null, List.of(), new TreasureOffer(offer), null, null, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r0", 3);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        PendingRelicPick pick = new PendingRelicPick(type, "r1", offer, null);
        return new DungeonSessionState(
            config, map, "r1",
            Map.of(), List.of(), 0, null,
            4, 5, 0, 2,
            0, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), pick);
    }

    private DungeonSessionState shopStateWithScore(int score) {
        ShopOffer offer = new ShopOffer(
            List.of(new ShopOfferEntry(RelicId.LUCKY_CHARM, 75),
                    new ShopOfferEntry(RelicId.MAP_SENSE, 150)),
            new ShopConsumable("dungeon.shop.consumable.heal", 30, 2));
        DungeonRoom r0 = new DungeonRoom("r0", RoomType.ENTRANCE,
            Map.of(DungeonDirection.RIGHT, "r1"), new GridPos(0, 0),
            true, true, null, List.of(), null, null, null, null);
        DungeonRoom r1 = new DungeonRoom("r1", RoomType.SHOP,
            Map.of(DungeonDirection.LEFT, "r0"), new GridPos(1, 0),
            true, false, null, List.of(), null, null, offer, null);
        DungeonMap map = new DungeonMap(Map.of("r0", r0, "r1", r1), "r0", "r0", 3);
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        PendingRelicPick pick = new PendingRelicPick(PendingPickType.SHOP, "r1", List.of(), offer);
        return new DungeonSessionState(
            config, map, "r1",
            Map.of(), List.of(), 0, null,
            5, 5, 0, 2,
            score, 0, 0,
            false, false,
            0, List.of(),
            0, 0, 0, 0,
            List.of(), pick);
    }
}
```

- [ ] **Step 2: Replace the stub with the real implementation**

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DungeonRelicService {

    public DungeonSessionState pick(DungeonSessionState state, RelicId relicId) {
        PendingRelicPick pick = state.pendingRelicPick();
        if (pick == null) return state;
        if (pick.type() == PendingPickType.SHOP) return state;  // shop uses buy()
        if (!pick.offer().contains(relicId)) return state;
        return applyAcquired(state, relicId, /* clearPick= */ true, /* deductScore= */ 0);
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
        return applyAcquired(state, relicId, /* clearPick= */ false, /* deductScore= */ entry.price());
    }

    public DungeonSessionState skipShop(DungeonSessionState state) {
        PendingRelicPick pick = state.pendingRelicPick();
        if (pick == null || pick.type() != PendingPickType.SHOP) return state;
        return clearPickAndMarkRoomCleared(state, pick.roomId());
    }

    // ===== helpers =====

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
            default -> { /* passive or render-only effect */ }
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
```

- [ ] **Step 3: Run the relic service tests**

Run: `./mvnw test -Dtest=DungeonRelicServiceTests`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonRelicService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonRelicServiceTests.java
git commit -m "feat(dungeon): implement relic pick/buy/skip with apply-on-acquire effects"
```

---

## Task 11: Wire Lucky Coin and Phoenix Feather into `DungeonDamage`

Two damage-path hooks: Lucky Coin (first wrong answer per run is free; tracked by `luckyCoinsConsumed` against count owned) and Phoenix Feather (resurrect at 1 HP on lethal damage; consumes one).

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java`

- [ ] **Step 1: Add failing tests**

Append to `DungeonDamageTests`:

```java
@Test
void takeDamage_luckyCoinAbsorbsFirstHitFreeOfShieldOrHp() {
    DungeonSessionState s = stateWithRelics(state(5, 5, 0, 2), List.of(RelicId.LUCKY_COIN));
    DungeonSessionState after = DungeonDamage.takeDamage(s, 1);
    assertThat(after.health()).isEqualTo(5);
    assertThat(after.shields()).isEqualTo(0);
    assertThat(after.luckyCoinsConsumed()).isEqualTo(1);
}

@Test
void takeDamage_secondLuckyCoinAbsorbsSecondHitWhenStacked() {
    DungeonSessionState s = stateWithRelics(state(5, 5, 0, 2),
        List.of(RelicId.LUCKY_COIN, RelicId.LUCKY_COIN));
    DungeonSessionState afterFirst = DungeonDamage.takeDamage(s, 1);
    DungeonSessionState afterSecond = DungeonDamage.takeDamage(afterFirst, 1);
    assertThat(afterSecond.health()).isEqualTo(5);
    assertThat(afterSecond.luckyCoinsConsumed()).isEqualTo(2);
}

@Test
void takeDamage_phoenixFeatherResurrectsAtOneHpOnLethal() {
    DungeonSessionState s = stateWithRelics(state(1, 5, 0, 2), List.of(RelicId.PHOENIX_FEATHER));
    DungeonSessionState after = DungeonDamage.takeDamage(s, 5);
    assertThat(after.health()).isEqualTo(1);
    assertThat(after.defeated()).isFalse();
    assertThat(after.ownedRelics()).doesNotContain(RelicId.PHOENIX_FEATHER);
}

private DungeonSessionState stateWithRelics(DungeonSessionState base, List<RelicId> relics) {
    return new DungeonSessionState(
        base.config(), base.map(), base.currentRoomId(),
        base.encounters(), base.bossEncounterIds(), base.bossIndex(),
        base.activeEncounterId(),
        base.health(), base.healthCap(), base.shields(), base.shieldCap(),
        base.score(), base.answeredCount(), base.correctCount(),
        base.won(), base.defeated(),
        base.streak(), base.gauntletQueue(),
        base.longestStreak(), base.elitesCleared(), base.shieldsUsed(),
        base.luckyCoinsConsumed(), relics, base.pendingRelicPick());
}
```

- [ ] **Step 2: Update `DungeonDamage.takeDamage`**

Replace the body of `takeDamage` with:

```java
public static DungeonSessionState takeDamage(DungeonSessionState state, int amount) {
    // Lucky Coin: first N wrong answers are free, where N = count(LUCKY_COIN in ownedRelics)
    int luckyCoinsOwned = (int) state.ownedRelics().stream()
        .filter(r -> r == RelicId.LUCKY_COIN).count();
    if (state.luckyCoinsConsumed() < luckyCoinsOwned) {
        return rebuildWithLucky(state, state.luckyCoinsConsumed() + 1);
    }
    // Shields first
    if (state.shields() > 0) {
        return rebuild(state, state.health(), state.shields() - 1,
            state.defeated(), state.shieldsUsed() + 1);
    }
    int newHealth = Math.max(0, state.health() - amount);
    if (newHealth <= 0) {
        // Phoenix Feather: consume one and revive at 1 HP
        int feathersOwned = (int) state.ownedRelics().stream()
            .filter(r -> r == RelicId.PHOENIX_FEATHER).count();
        if (feathersOwned > 0) {
            List<RelicId> remaining = new ArrayList<>(state.ownedRelics());
            remaining.remove(RelicId.PHOENIX_FEATHER);
            return rebuildWithOwned(state, 1, state.shields(), false,
                state.shieldsUsed(), state.luckyCoinsConsumed(), remaining);
        }
    }
    boolean defeated = state.defeated() || newHealth <= 0;
    return rebuild(state, newHealth, state.shields(), defeated, state.shieldsUsed());
}

private static DungeonSessionState rebuildWithLucky(DungeonSessionState s, int luckyCoinsConsumed) {
    return rebuildWithOwned(s, s.health(), s.shields(), s.defeated(),
        s.shieldsUsed(), luckyCoinsConsumed, s.ownedRelics());
}

private static DungeonSessionState rebuildWithOwned(DungeonSessionState s, int hp, int shields,
                                                      boolean defeated, int shieldsUsed,
                                                      int luckyCoinsConsumed, List<RelicId> owned) {
    return new DungeonSessionState(
        s.config(), s.map(), s.currentRoomId(),
        s.encounters(), s.bossEncounterIds(), s.bossIndex(),
        s.activeEncounterId(),
        hp, s.healthCap(), shields, s.shieldCap(),
        s.score(), s.answeredCount(), s.correctCount(),
        s.won(), defeated,
        s.streak(), s.gauntletQueue(),
        s.longestStreak(), s.elitesCleared(), shieldsUsed,
        luckyCoinsConsumed, owned, s.pendingRelicPick());
}
```

(Add `import com.HendrikHoemberg.StudyHelper.dto.RelicId;` and `import java.util.ArrayList; import java.util.List;` to the file.)

- [ ] **Step 3: Run tests**

Run: `./mvnw test -Dtest=DungeonDamageTests`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java
git commit -m "feat(dungeon): wire Lucky Coin and Phoenix Feather into damage path"
```

---

## Task 12: Wire Lucky Charm, Sharp Focus, War Banner into encounter answer pipeline

Three passive hooks inside `processAnswer`: Lucky Charm adds +5 score per correct, Sharp Focus drops the streak-for-shield threshold from 3 to 2, War Banner grants +1 shield (capped) when a COMBAT room is cleared.

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterServiceTests.java`

- [ ] **Step 1: Add failing tests**

Append to `DungeonEncounterServiceTests`:

```java
@Test
void answerCorrect_luckyCharmAdds5ToScore() {
    DungeonSessionState state = activeFlashcardState();
    state = withRelics(state, List.of(RelicId.LUCKY_CHARM));
    DungeonSessionState result = svc.answerFlashcard(state, true);
    assertThat(result.score()).isEqualTo(DungeonEncounterService.COMBAT_CLEAR_SCORE + 5);
}

@Test
void answerCorrect_sharpFocusBanksShieldEveryTwoCorrect() {
    DungeonSessionState state = activeFlashcardStateWithStreak(1);
    state = withRelics(state, List.of(RelicId.SHARP_FOCUS));
    DungeonSessionState result = svc.answerFlashcard(state, true);
    assertThat(result.streak()).isEqualTo(2);
    assertThat(result.shields()).isEqualTo(state.shields() + 1);
}

@Test
void answerCorrect_warBannerGrantsShieldOnCombatClear() {
    DungeonSessionState state = activeFlashcardState();
    state = withRelics(state, List.of(RelicId.WAR_BANNER));
    DungeonSessionState result = svc.answerFlashcard(state, true);
    assertThat(result.shields()).isEqualTo(Math.min(state.shieldCap(), state.shields() + 1));
}

private DungeonSessionState withRelics(DungeonSessionState s, List<RelicId> relics) {
    return new DungeonSessionState(
        s.config(), s.map(), s.currentRoomId(),
        s.encounters(), s.bossEncounterIds(), s.bossIndex(),
        s.activeEncounterId(),
        s.health(), s.healthCap(), s.shields(), s.shieldCap(),
        s.score(), s.answeredCount(), s.correctCount(),
        s.won(), s.defeated(),
        s.streak(), s.gauntletQueue(),
        s.longestStreak(), s.elitesCleared(), s.shieldsUsed(),
        s.luckyCoinsConsumed(), relics, s.pendingRelicPick());
}
```

- [ ] **Step 2: Wire the hooks in `processAnswer`**

In `DungeonEncounterService`, replace the score and streak-threshold lines inside `processAnswer`:

Find:
```java
int newStreak = correct ? state.streak() + 1 : 0;
int newShields = state.shields();
if (correct && newStreak % STREAK_FOR_SHIELD == 0 && newShields < state.shieldCap()) {
    newShields++;
}
int newLongest = Math.max(state.longestStreak(), newStreak);
int newScore = state.score();
if (correct) {
    newScore += encounter.boss() ? BOSS_PROMPT_SCORE : COMBAT_CLEAR_SCORE;
}
```

Replace with:
```java
int effectiveStreakThreshold = state.ownedRelics().contains(RelicId.SHARP_FOCUS) ? 2 : STREAK_FOR_SHIELD;
int newStreak = correct ? state.streak() + 1 : 0;
int newShields = state.shields();
if (correct && newStreak % effectiveStreakThreshold == 0 && newShields < state.shieldCap()) {
    newShields++;
}
int newLongest = Math.max(state.longestStreak(), newStreak);
int newScore = state.score();
if (correct) {
    int base = encounter.boss() ? BOSS_PROMPT_SCORE : COMBAT_CLEAR_SCORE;
    int bonus = state.ownedRelics().contains(RelicId.LUCKY_CHARM) ? 5 : 0;
    newScore += base + bonus;
}
```

In `clearCombatRoom`, just before constructing the new state, add the War Banner hook. Replace `clearCombatRoom`'s body:

```java
private DungeonSessionState clearCombatRoom(DungeonSessionState state) {
    DungeonRoom room = state.currentRoom();
    if (room == null) return state;
    Map<String, DungeonRoom> rooms = new LinkedHashMap<>(state.map().rooms());
    rooms.put(room.id(), room.withCleared(true));
    DungeonMap nextMap = new DungeonMap(
        rooms, state.map().entranceRoomId(), state.map().bossRoomId(), state.map().lattice());

    int shieldsAfter = state.shields();
    if (state.ownedRelics().contains(RelicId.WAR_BANNER) && shieldsAfter < state.shieldCap()) {
        shieldsAfter++;
    }

    return new DungeonSessionState(
        state.config(), nextMap, state.currentRoomId(),
        state.encounters(), state.bossEncounterIds(), state.bossIndex(),
        null,
        state.health(), state.healthCap(), shieldsAfter, state.shieldCap(),
        state.score(), state.answeredCount(), state.correctCount(),
        state.won(), state.defeated(),
        state.streak(), state.gauntletQueue(),
        state.longestStreak(), state.elitesCleared(), state.shieldsUsed(),
        state.luckyCoinsConsumed(), state.ownedRelics(), state.pendingRelicPick());
}
```

(Add `import com.HendrikHoemberg.StudyHelper.dto.RelicId;` if not already present.)

- [ ] **Step 3: Run tests**

Run: `./mvnw test -Dtest=DungeonEncounterServiceTests`
Expected: PASS, including the three new tests and all pre-existing ones.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterServiceTests.java
git commit -m "feat(dungeon): wire Lucky Charm, Sharp Focus, War Banner relic hooks"
```

---

## Task 13: Wire Spectacles into quiz encounter rendering

Spectacles greys out one wrong option on multi-choice quiz prompts. Server-side: when computing the model attributes for an active QUIZ encounter, if Spectacles is owned, pick one wrong option index deterministically (based on encounter id) and expose it as `hintMaskIndex` for the renderer. No state mutation.

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DungeonController.java` (`prepareGame`)
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/controller/DungeonControllerTests.java`

(Note: this depends on Task 14 having been completed if `DungeonControllerTests` was rewritten. If you're working strictly in plan order, the controller-level test for Spectacles is verified manually in the browser-verification step at the end of Task 14. If you'd rather assert it now, add a small focused unit test on a helper method as shown below.)

- [ ] **Step 1: Extract the hint-mask logic into a helper for testability**

Add to `DungeonController` (or a new static class if you prefer):

```java
static Integer spectaclesHintMaskIndex(DungeonSessionState state, DungeonEncounter encounter) {
    if (!state.ownedRelics().contains(RelicId.SPECTACLES)) return null;
    if (encounter == null) return null;
    if (encounter.type() != DungeonEncounterType.QUIZ
        && encounter.type() != DungeonEncounterType.BOSS_QUIZ) return null;
    QuizQuestion q = encounter.quizQuestion();
    if (q == null) return null;
    Set<Integer> correctSet = new HashSet<>(q.correctOptionIndices());
    List<Integer> wrongIndices = new ArrayList<>();
    for (int i = 0; i < q.options().size(); i++) {
        if (!correctSet.contains(i)) wrongIndices.add(i);
    }
    if (wrongIndices.isEmpty()) return null;
    int seed = Math.abs(Objects.hash(encounter.id()));
    return wrongIndices.get(seed % wrongIndices.size());
}
```

- [ ] **Step 2: Add the unit test**

In `DungeonControllerTests` (or a new `DungeonSpectaclesHintTests` if the controller test class proves cumbersome to instantiate):

```java
@Test
void spectaclesHintMaskIndex_returnsAWrongOptionIndexWhenRelicOwned() {
    QuizQuestion q = new QuizQuestion(
        "q?", List.of("A", "B", "C", "D"), List.of(1),
        QuizQuestionType.SINGLE_SELECT, null);
    DungeonEncounter enc = DungeonEncounter.quiz("q_42", false, q);
    DungeonSessionState state = stateWithRelics(List.of(RelicId.SPECTACLES));
    Integer mask = DungeonController.spectaclesHintMaskIndex(state, enc);
    assertThat(mask).isIn(0, 2, 3);  // anything except the correct index 1
}

@Test
void spectaclesHintMaskIndex_isNullWhenRelicNotOwned() {
    QuizQuestion q = new QuizQuestion("q?", List.of("A", "B"), List.of(0),
        QuizQuestionType.SINGLE_SELECT, null);
    DungeonEncounter enc = DungeonEncounter.quiz("q_1", false, q);
    DungeonSessionState state = stateWithRelics(List.of());
    assertThat(DungeonController.spectaclesHintMaskIndex(state, enc)).isNull();
}
```

(Fixture `stateWithRelics` is the same shape as elsewhere; copy from Task 12.)

- [ ] **Step 3: Expose `hintMaskIndex` in `prepareGame`**

In `DungeonController.prepareGame`, after `model.addAttribute("activeEncounter", state.activeEncounter())`, add:

```java
model.addAttribute("hintMaskIndex",
    spectaclesHintMaskIndex(state, state.activeEncounter()));
```

The Thymeleaf change (Task 16) consumes this attribute to grey out the option.

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest=DungeonControllerTests` (or your standalone hint-test class).
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/DungeonController.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/controller/DungeonControllerTests.java
git commit -m "feat(dungeon): expose Spectacles hint-mask index for quiz encounters"
```

---

## Task 14: Rewrite `DungeonController` for relic endpoints + minimap model

Adds three new endpoints, replaces the `mapTiles(...)` model with a `minimapRooms(...)` model that respects Compass and Map Sense, and updates `prepareGame` to expose the room/relic data the new template will read.

**Files:**
- Modify (rewrite): `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DungeonController.java`
- Modify (rewrite): `src/test/java/com/HendrikHoemberg/StudyHelper/controller/DungeonControllerTests.java`

- [ ] **Step 1: Add the new endpoints and rewrite `prepareGame`**

Replace the `prepareGame` body and add new endpoints. The full controller is shown below for clarity — replace the existing file with this version (preserving any imports you've already added for Spectacles in Task 13):

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.*;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.util.*;

@Controller
public class DungeonController {

    private static final Logger log = LoggerFactory.getLogger(DungeonController.class);
    private static final String DUNGEON_SESSION_KEY = "dungeonSessionState";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DungeonSessionService dungeonSessionService;
    private final SavedSessionService savedSessionService;
    private final StudyLogService studyLogService;
    private final UserService userService;
    private final FolderService folderService;
    private final DashboardService dashboardService;

    public DungeonController(DungeonSessionService dungeonSessionService,
                             SavedSessionService savedSessionService,
                             StudyLogService studyLogService,
                             UserService userService,
                             FolderService folderService,
                             DashboardService dashboardService) {
        this.dungeonSessionService = dungeonSessionService;
        this.savedSessionService = savedSessionService;
        this.studyLogService = studyLogService;
        this.userService = userService;
        this.folderService = folderService;
        this.dashboardService = dashboardService;
    }

    // ===== existing start/resume endpoints (unchanged) =====
    // Keep the existing @PostMapping("/dungeon/start") and @GetMapping("/dungeon/resume")
    // method bodies verbatim — they don't reference tile types.

    @PostMapping("/dungeon/move")
    public String move(@RequestParam DungeonDirection direction,
                       Model model, Principal principal, HttpSession session,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        state = dungeonSessionService.move(state, direction);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/answer/flashcard")
    public String answerFlashcard(@RequestParam boolean gotIt,
                                   Model model, Principal principal, HttpSession session,
                                   @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        state = dungeonSessionService.answerFlashcard(state, gotIt);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/answer/quiz")
    public String answerQuiz(@RequestParam(required = false) List<Integer> selectedOptions,
                              Model model, Principal principal, HttpSession session,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        state = dungeonSessionService.answerQuiz(state, selectedOptions);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/relic/pick")
    public String pickRelic(@RequestParam RelicId relicId,
                              Model model, Principal principal, HttpSession session,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        state = dungeonSessionService.pickRelic(state, relicId);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/relic/buy")
    public String buyRelic(@RequestParam RelicId relicId,
                             Model model, Principal principal, HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        state = dungeonSessionService.buyRelic(state, relicId);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    @PostMapping("/dungeon/relic/skip-shop")
    public String skipShop(Model model, Principal principal, HttpSession session,
                             @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        User user = userService.getByUsername(principal.getName());
        DungeonSessionState state = getState(session, user);
        if (state == null) return "redirect:/study/start?mode=DUNGEON";
        state = dungeonSessionService.skipShop(state);
        return stashAndRender(model, user, session, state, hxRequest);
    }

    // ===== rendering =====

    private String stashAndRender(Model model, User user, HttpSession session,
                                   DungeonSessionState state, String hxRequest) {
        session.setAttribute(DUNGEON_SESSION_KEY, state);
        if (state.isComplete()) {
            savedSessionService.discard(user, false);
            DungeonRunStats stats = dungeonSessionService.buildStats(state);
            studyLogService.recordDungeon(user, stats, state.config().selectedDeckIds());
            model.addAttribute("mode", StudyMode.DUNGEON);
            model.addAttribute("stats", stats);
            if (hxRequest != null) return "fragments/dungeon-complete :: dungeonComplete";
            model.addAttribute("studyStateView", "dungeonComplete");
            return "study-page";
        }
        savedSessionService.saveDungeon(user, state);
        return prepareGame(model, state, hxRequest);
    }

    private String prepareGame(Model model, DungeonSessionState state, String hxRequest) {
        model.addAttribute("mode", StudyMode.DUNGEON);
        model.addAttribute("studyStateView", "dungeon");
        model.addAttribute("state", state);
        model.addAttribute("activeEncounter", state.activeEncounter());
        model.addAttribute("currentRoom", state.currentRoom());
        model.addAttribute("stats", dungeonSessionService.buildStats(state));
        model.addAttribute("ownedRelics", state.ownedRelics());
        model.addAttribute("pendingPick", state.pendingRelicPick());
        model.addAttribute("hintMaskIndex",
            spectaclesHintMaskIndex(state, state.activeEncounter()));

        List<Map<String, Object>> minimap = minimapRooms(state);
        model.addAttribute("minimapRooms", minimap);
        try {
            model.addAttribute("minimapRoomsJson", objectMapper.writeValueAsString(minimap));
        } catch (Exception e) {
            log.error("Failed to serialize minimap rooms", e);
            model.addAttribute("minimapRoomsJson", "[]");
        }

        int gauntletPos = 0;
        int gauntletTotal = 0;
        if (state.activeEncounterId() != null) {
            for (DungeonRoom r : state.map().rooms().values()) {
                if (r.gauntletGroup().contains(state.activeEncounterId())) {
                    gauntletTotal = r.gauntletGroup().size();
                    gauntletPos = r.gauntletGroup().indexOf(state.activeEncounterId()) + 1;
                    break;
                }
            }
        }
        model.addAttribute("gauntletPosition", gauntletPos);
        model.addAttribute("gauntletTotal", gauntletTotal);

        if (hxRequest != null) return "fragments/dungeon-game :: dungeonGame";
        model.addAttribute("studyStateView", "dungeon");
        return "study-page";
    }

    /**
     * Per-room display data for the minimap:
     *   { id, type, gridX, gridY, visible (bool), revealedType (bool), cleared (bool), isCurrent (bool),
     *     doors: { "UP": neighborId | null, ... } }
     *
     * Visibility rules:
     *   - Visited rooms: visible = true, revealedType = true.
     *   - Neighbors of visited (via door): visible = true, revealedType only if COMPASS owned.
     *   - SECRET room: visible only if MAP_SENSE owned OR both host rooms visited.
     *   - Everything else: visible = false (excluded from the response).
     */
    List<Map<String, Object>> minimapRooms(DungeonSessionState state) {
        boolean hasCompass = state.ownedRelics().contains(RelicId.COMPASS);
        boolean hasMapSense = state.ownedRelics().contains(RelicId.MAP_SENSE);

        Set<String> visitedIds = new HashSet<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            if (r.visited()) visitedIds.add(r.id());
        }
        Set<String> adjacentToVisited = new HashSet<>();
        for (String id : visitedIds) {
            DungeonRoom r = state.map().room(id);
            for (String neighbor : r.doors().values()) {
                if (!visitedIds.contains(neighbor)) adjacentToVisited.add(neighbor);
            }
        }

        // Secret room reveal
        Set<String> secretRevealed = new HashSet<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            if (r.type() != RoomType.SECRET) continue;
            if (hasMapSense) {
                secretRevealed.add(r.id());
                continue;
            }
            // SECRET rooms have no doors. They are revealed when both lattice-neighbor
            // hosts have been visited. Compute hosts by checking lattice-adjacent rooms.
            int hostsVisited = 0;
            int hostsTotal = 0;
            for (DungeonRoom maybeHost : state.map().rooms().values()) {
                if (maybeHost.id().equals(r.id())) continue;
                GridPos a = r.gridPos();
                GridPos b = maybeHost.gridPos();
                if (Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) == 1) {
                    hostsTotal++;
                    if (visitedIds.contains(maybeHost.id())) hostsVisited++;
                }
            }
            if (hostsTotal > 0 && hostsVisited == hostsTotal) secretRevealed.add(r.id());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (DungeonRoom r : state.map().rooms().values()) {
            boolean isVisited = visitedIds.contains(r.id());
            boolean isAdjacent = adjacentToVisited.contains(r.id());
            boolean isSecret = r.type() == RoomType.SECRET;
            boolean visible = isVisited || isAdjacent || (isSecret && secretRevealed.contains(r.id()));
            if (!visible) continue;

            boolean revealedType = isVisited
                || (isAdjacent && hasCompass)
                || (isSecret && secretRevealed.contains(r.id()));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id());
            m.put("type", revealedType ? r.type().name() : "UNKNOWN");
            m.put("gridX", r.gridPos().x());
            m.put("gridY", r.gridPos().y());
            m.put("visited", isVisited);
            m.put("cleared", r.cleared());
            m.put("isCurrent", r.id().equals(state.currentRoomId()));
            Map<String, String> doors = new LinkedHashMap<>();
            for (Map.Entry<DungeonDirection, String> e : r.doors().entrySet()) {
                doors.put(e.getKey().name(), e.getValue());
            }
            m.put("doors", doors);
            result.add(m);
        }
        return result;
    }

    static Integer spectaclesHintMaskIndex(DungeonSessionState state, DungeonEncounter encounter) {
        if (!state.ownedRelics().contains(RelicId.SPECTACLES)) return null;
        if (encounter == null) return null;
        if (encounter.type() != DungeonEncounterType.QUIZ
            && encounter.type() != DungeonEncounterType.BOSS_QUIZ) return null;
        QuizQuestion q = encounter.quizQuestion();
        if (q == null) return null;
        Set<Integer> correctSet = new HashSet<>(q.correctOptionIndices());
        List<Integer> wrongIndices = new ArrayList<>();
        for (int i = 0; i < q.options().size(); i++) {
            if (!correctSet.contains(i)) wrongIndices.add(i);
        }
        if (wrongIndices.isEmpty()) return null;
        int seed = Math.abs(Objects.hash(encounter.id()));
        return wrongIndices.get(seed % wrongIndices.size());
    }

    private DungeonSessionState getState(HttpSession session, User user) {
        DungeonSessionState state = (DungeonSessionState) session.getAttribute(DUNGEON_SESSION_KEY);
        if (state == null) state = savedSessionService.loadDungeon(user).orElse(null);
        return state;
    }

    // === KEEP the existing start/resume/error-handling/wizard helpers verbatim. ===
    // Reproduced for clarity only if they need touch; copy the existing ones from the previous file.
}
```

(The existing `/dungeon/start`, `/dungeon/resume`, `handleStartError`, `prepareWizardModel`, `generationDetails`, and `dungeonResumeDiscardMessage` methods don't reference any deleted types — keep them as-is. The two changes you need to make are: delete the old `mapTiles(...)` method and replace `prepareGame` with the version above.)

- [ ] **Step 2: Rewrite `DungeonControllerTests` for the new endpoints**

Add focused tests for the relic endpoints, the Spectacles hint helper, and the minimap reveal logic. Keep existing start/resume/answer/move tests; update any that referenced `playerPosition` to use `currentRoomId` instead.

```java
@Test
void minimapRooms_visitedRoomsRevealType() {
    DungeonSessionState state = stateWithTwoRoomsRightDoor("r0", true, "r1", false);
    List<Map<String, Object>> map = controller.minimapRooms(state);
    Map<String, Object> r0 = findById(map, "r0");
    assertThat(r0.get("type")).isEqualTo("ENTRANCE");
}

@Test
void minimapRooms_adjacentUnvisitedRoomsHaveUnknownTypeWithoutCompass() {
    DungeonSessionState state = stateWithTwoRoomsRightDoor("r0", true, "r1", false);
    List<Map<String, Object>> map = controller.minimapRooms(state);
    Map<String, Object> r1 = findById(map, "r1");
    assertThat(r1.get("type")).isEqualTo("UNKNOWN");
}

@Test
void minimapRooms_adjacentUnvisitedRoomsHaveRevealedTypeWithCompass() {
    DungeonSessionState state = stateWithTwoRoomsRightDoor("r0", true, "r1", false);
    state = withRelics(state, List.of(RelicId.COMPASS));
    List<Map<String, Object>> map = controller.minimapRooms(state);
    Map<String, Object> r1 = findById(map, "r1");
    assertThat(r1.get("type")).isEqualTo("COMBAT");
}

@Test
void minimapRooms_secretRoomRevealedByMapSense() {
    DungeonSessionState state = stateWithSecretAdjacentToTwoRooms(false, false);
    state = withRelics(state, List.of(RelicId.MAP_SENSE));
    List<Map<String, Object>> map = controller.minimapRooms(state);
    assertThat(findById(map, "secret")).isNotNull();
    assertThat(findById(map, "secret").get("type")).isEqualTo("SECRET");
}

@Test
void minimapRooms_secretRoomHiddenWhenOnlyOneHostVisited() {
    DungeonSessionState state = stateWithSecretAdjacentToTwoRooms(true, false);
    List<Map<String, Object>> map = controller.minimapRooms(state);
    assertThat(findById(map, "secret")).isNull();
}
```

Fixture builders (`stateWithTwoRoomsRightDoor`, `stateWithSecretAdjacentToTwoRooms`, `findById`, `withRelics`) follow the same shape as previous tests; copy from `DungeonNavigationServiceTests` and add the secret-room scaffolding inline.

- [ ] **Step 3: Run the test suite**

Run: `./mvnw test -Dtest='Dungeon*'`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/controller/DungeonController.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/controller/DungeonControllerTests.java
git commit -m "feat(dungeon): add relic endpoints, minimap model with Compass/Map Sense"
```

---

## Task 15: Rewrite `dungeon-game.html` for the room-graph layout

Replaces the tile-canvas template with the minimap + room view + relic shelf layout. Combat encounter rendering stays JS-driven on the room canvas; only the template scaffolding changes.

**Files:**
- Modify (rewrite): `src/main/resources/templates/fragments/dungeon-game.html`

- [ ] **Step 1: Replace the whole file**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>

<div th:fragment="dungeonGame" id="dungeon-session-content" class="sh-study-session-active">

    <div class="sh-dungeon-root">

        <!-- HUD -->
        <div class="sh-dungeon-hud">
            <div class="sh-dungeon-hud-item">
                <span class="sh-dungeon-hud-label" th:text="#{dungeon.health}">Health</span>
                <span class="sh-dungeon-hud-value sh-dungeon-hud-health"
                      th:text="${state.health + '/' + state.healthCap}">5/5</span>
            </div>
            <div class="sh-dungeon-hud-item">
                <span class="sh-dungeon-hud-label" th:text="#{dungeon.score}">Score</span>
                <span class="sh-dungeon-hud-value sh-dungeon-hud-score">
                    <iconify-icon icon="lucide:coins"></iconify-icon>
                    <span th:text="${state.score}">0</span>
                </span>
            </div>
            <div class="sh-dungeon-hud-item sh-dungeon-hud-item-shields"
                 th:classappend="${state.shields == 0} ? 'is-hidden'">
                <span class="sh-dungeon-hud-label" th:text="#{dungeon.shields}">Shields</span>
                <span class="sh-dungeon-hud-value sh-dungeon-hud-shields">
                    <iconify-icon icon="lucide:shield"
                                  th:each="i : ${#numbers.sequence(1, state.shields > 0 ? state.shields : 1)}"
                                  th:if="${state.shields > 0}"></iconify-icon>
                </span>
            </div>
            <div class="sh-dungeon-hud-item sh-dungeon-hud-item-streak"
                 th:classappend="${state.streak == 0} ? 'is-hidden'">
                <span class="sh-dungeon-hud-label" th:text="#{dungeon.streak}">Streak</span>
                <span class="sh-dungeon-hud-value sh-dungeon-hud-streak"
                      th:classappend="${state.streak > 0 and state.streak % 3 == 0} ? 'is-pulsing'"
                      th:text="'×' + ${state.streak}">×0</span>
            </div>
            <div class="sh-dungeon-hud-item">
                <span class="sh-dungeon-hud-label" th:text="#{dungeon.progress}">Progress</span>
                <span class="sh-dungeon-hud-value"
                      th:text="${state.correctCount + '/' + state.answeredCount}">0/0</span>
            </div>
        </div>

        <!-- Main layout: room view + side panel (minimap + relics) -->
        <div class="sh-dungeon-layout" th:classappend="${activeEncounter != null} ? 'is-encounter-active'">

            <!-- Room view (canvas hosts JRPG combat or room scenery) -->
            <div class="sh-dungeon-room-panel">
                <canvas id="dungeon-room-canvas"
                        class="sh-dungeon-canvas sh-dungeon-room-canvas"
                        width="1024" height="768"
                        th:data-current-room-id="${state.currentRoomId}"
                        th:data-current-room-type="${currentRoom != null ? currentRoom.type : 'ENTRANCE'}"
                        th:data-current-room-cleared="${currentRoom != null and currentRoom.cleared}"
                        th:data-active-encounter-id="${activeEncounter != null ? activeEncounter.id : ''}"
                        th:data-active-encounter-boss="${activeEncounter != null ? activeEncounter.boss : 'false'}"
                        th:data-gauntlet-position="${gauntletPosition}"
                        th:data-gauntlet-total="${gauntletTotal}">
                </canvas>

                <!-- Directional controls (mobile + desktop) -->
                <div class="sh-dungeon-controls" th:classappend="${activeEncounter != null} ? 'is-disabled'">
                    <div class="sh-dungeon-controls-row">
                        <button class="sh-btn sh-btn-ghost sh-dungeon-dir"
                                hx-post="/dungeon/move"
                                hx-target="#dungeon-session-content"
                                hx-swap="outerHTML"
                                name="direction" value="UP"
                                th:disabled="${activeEncounter != null
                                                or currentRoom == null
                                                or !currentRoom.doors.containsKey(T(com.HendrikHoemberg.StudyHelper.dto.DungeonDirection).UP)}"
                                aria-label="Move up">
                            <iconify-icon icon="lucide:arrow-up"></iconify-icon>
                        </button>
                    </div>
                    <div class="sh-dungeon-controls-row">
                        <button class="sh-btn sh-btn-ghost sh-dungeon-dir"
                                hx-post="/dungeon/move"
                                hx-target="#dungeon-session-content"
                                hx-swap="outerHTML"
                                name="direction" value="LEFT"
                                th:disabled="${activeEncounter != null
                                                or currentRoom == null
                                                or !currentRoom.doors.containsKey(T(com.HendrikHoemberg.StudyHelper.dto.DungeonDirection).LEFT)}"
                                aria-label="Move left">
                            <iconify-icon icon="lucide:arrow-left"></iconify-icon>
                        </button>
                        <button class="sh-btn sh-btn-ghost sh-dungeon-dir"
                                hx-post="/dungeon/move"
                                hx-target="#dungeon-session-content"
                                hx-swap="outerHTML"
                                name="direction" value="DOWN"
                                th:disabled="${activeEncounter != null
                                                or currentRoom == null
                                                or !currentRoom.doors.containsKey(T(com.HendrikHoemberg.StudyHelper.dto.DungeonDirection).DOWN)}"
                                aria-label="Move down">
                            <iconify-icon icon="lucide:arrow-down"></iconify-icon>
                        </button>
                        <button class="sh-btn sh-btn-ghost sh-dungeon-dir"
                                hx-post="/dungeon/move"
                                hx-target="#dungeon-session-content"
                                hx-swap="outerHTML"
                                name="direction" value="RIGHT"
                                th:disabled="${activeEncounter != null
                                                or currentRoom == null
                                                or !currentRoom.doors.containsKey(T(com.HendrikHoemberg.StudyHelper.dto.DungeonDirection).RIGHT)}"
                                aria-label="Move right">
                            <iconify-icon icon="lucide:arrow-right"></iconify-icon>
                        </button>
                    </div>
                </div>
            </div>

            <!-- Side panel: minimap + relic shelf -->
            <div class="sh-dungeon-side-panel">
                <div class="sh-dungeon-minimap-container">
                    <canvas id="dungeon-minimap-canvas"
                            class="sh-dungeon-minimap"
                            width="320" height="320"
                            th:data-lattice="${state.map.lattice}">
                    </canvas>
                    <script id="dungeon-minimap-state" type="application/json">
                        [(${minimapRoomsJson})]
                    </script>
                </div>

                <div class="sh-dungeon-relic-shelf"
                     th:classappend="${ownedRelics.isEmpty()} ? 'is-empty'">
                    <span class="sh-dungeon-relic-shelf-label"
                          th:text="#{dungeon.relics}"
                          th:if="${!ownedRelics.isEmpty()}">Relics</span>
                    <div class="sh-dungeon-relic-shelf-items">
                        <span th:each="rid : ${ownedRelics}"
                              class="sh-dungeon-relic-icon"
                              th:title="#{__${'dungeon.relic.' + #strings.toLowerCase(rid.name().replace('_','-')) + '.desc'}__}">
                            <iconify-icon th:icon="${T(com.HendrikHoemberg.StudyHelper.controller.RelicIcons).iconFor(rid)}"></iconify-icon>
                        </span>
                    </div>
                </div>
            </div>

        </div>

        <!-- Modal mount: rendered when pendingPick != null -->
        <div id="dungeon-modal-mount" th:if="${pendingPick != null}">
            <div th:if="${pendingPick.type.name() == 'TREASURE'}"
                 th:replace="~{fragments/dungeon-treasure-modal :: modal}"></div>
            <div th:if="${pendingPick.type.name() == 'ELITE'}"
                 th:replace="~{fragments/dungeon-elite-reward-modal :: modal}"></div>
            <div th:if="${pendingPick.type.name() == 'SHOP'}"
                 th:replace="~{fragments/dungeon-shop-modal :: modal}"></div>
            <div th:if="${pendingPick.type.name() == 'SECRET'}"
                 th:replace="~{fragments/dungeon-secret-modal :: modal}"></div>
        </div>

    </div>
</div>

</body>
</html>
```

- [ ] **Step 2: Create `RelicIcons` helper for the template**

The Thymeleaf `T(...)` expression above references a tiny helper class. Create:

`src/main/java/com/HendrikHoemberg/StudyHelper/controller/RelicIcons.java`

```java
package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.dto.RelicId;

public final class RelicIcons {
    private RelicIcons() {}

    public static String iconFor(RelicId id) {
        return switch (id) {
            case IRON_PLATE -> "lucide:heart-plus";
            case BUCKLER -> "lucide:shield-plus";
            case LUCKY_CHARM -> "lucide:clover";
            case SHARP_FOCUS -> "lucide:target";
            case COMPASS -> "lucide:compass";
            case LUCKY_COIN -> "lucide:coin";
            case PHOENIX_FEATHER -> "lucide:feather";
            case SPECTACLES -> "lucide:glasses";
            case WAR_BANNER -> "lucide:flag";
            case MAP_SENSE -> "lucide:map";
        };
    }
}
```

- [ ] **Step 3: Compile to verify the template references and helper resolve**

Run: `./mvnw compile`
Expected: BUILD SUCCESS. (Thymeleaf syntax errors won't surface until runtime; we cover that in Task 20.)

- [ ] **Step 4: Stage but do not commit yet — modal fragments come in Task 17**

```bash
git add src/main/resources/templates/fragments/dungeon-game.html \
        src/main/java/com/HendrikHoemberg/StudyHelper/controller/RelicIcons.java
```

---

## Task 16: Rewrite `dungeon.js` for minimap + room canvas; preserve JRPG combat

Drops the tile renderer entirely. Keeps the JRPG combat renderer (`renderJRPGCombat`, splash machinery, particles, audio) by moving its hookpoint from "the giant map canvas" to "the room canvas when an encounter is active." Adds a minimap renderer on the smaller side canvas.

**Files:**
- Modify (largely rewritten): `src/main/resources/static/js/dungeon.js`
- Modify: `src/main/resources/static/css/dungeon.css`

This is the largest UI change. Rather than reproducing the entire ~1650-line file inline, the steps below describe the surgery to perform. The implementer should keep all JRPG combat code, audio module, and particle systems intact; the changes are at the data-loading and dispatch level.

- [ ] **Step 1: Identify and DELETE the tile-grid renderer functions**

In `dungeon.js`, find and delete the following sections:
- `renderAll()` (or whatever the function is named that paints the whole tile grid)
- `drawTile(...)`, `drawFogOverlay(...)`, `drawPlayerOnGrid(...)`
- Anything reading `#dungeon-map-state` (the per-tile JSON script)
- Anything reading `data-map-width`, `data-map-height`, `data-player-x`, `data-player-y` on the canvas
- Trap telegraphing helpers (`renderTelegraphedTrap`, etc.)
- Secret-wall-as-tile rendering

(Keep all functions whose names start with `renderJRPGCombat`, `playSlashParticle`, `DungeonAudio`, `splash`, `particle`, `enemySprite`. Those move to the room canvas.)

- [ ] **Step 2: Add the minimap renderer**

Add a new function near the top of the file:

```javascript
function renderMinimap() {
  const canvas = document.getElementById('dungeon-minimap-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const lattice = parseInt(canvas.dataset.lattice, 10);
  const stateNode = document.getElementById('dungeon-minimap-state');
  if (!stateNode) return;
  const rooms = JSON.parse(stateNode.textContent || '[]');

  ctx.fillStyle = '#0a0a14';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  const cellSize = Math.floor(Math.min(canvas.width, canvas.height) / lattice);

  // Draw door connectors first so rooms render on top
  for (const r of rooms) {
    for (const [dir, neighborId] of Object.entries(r.doors || {})) {
      const neighbor = rooms.find(o => o.id === neighborId);
      if (!neighbor) continue;
      drawDoorConnector(ctx, r, neighbor, cellSize);
    }
  }

  for (const r of rooms) drawRoomCell(ctx, r, cellSize);
}

function drawRoomCell(ctx, room, cellSize) {
  const x = room.gridX * cellSize;
  const y = room.gridY * cellSize;
  const pad = 2;
  ctx.fillStyle = colorForRoom(room);
  ctx.fillRect(x + pad, y + pad, cellSize - pad * 2, cellSize - pad * 2);
  if (room.isCurrent) {
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 2;
    ctx.strokeRect(x + pad, y + pad, cellSize - pad * 2, cellSize - pad * 2);
  }
  if (room.type === 'UNKNOWN') {
    ctx.fillStyle = '#222';
    ctx.fillText('?', x + cellSize / 2 - 4, y + cellSize / 2 + 4);
  } else {
    drawRoomTypeGlyph(ctx, room, x, y, cellSize);
  }
}

function colorForRoom(r) {
  if (r.type === 'UNKNOWN') return '#3a3a48';
  if (r.cleared) return '#4a4a55';
  switch (r.type) {
    case 'ENTRANCE': return '#2c5a8a';
    case 'COMBAT':   return '#a32d2d';
    case 'ELITE':    return '#7a2da3';
    case 'TREASURE': return '#c79b2a';
    case 'HEAL':     return '#2da366';
    case 'SHOP':     return '#2d8aa3';
    case 'BOSS':     return '#d63a3a';
    case 'SECRET':   return '#888';
    default:         return '#3a3a48';
  }
}

function drawRoomTypeGlyph(ctx, room, x, y, cellSize) {
  // Minimal pixel glyphs; replace with sprite atlas later if desired.
  ctx.fillStyle = '#000';
  ctx.font = '10px monospace';
  const glyph = {
    ENTRANCE: '⬡', COMBAT: '⚔', ELITE: '✦',
    TREASURE: '◆', HEAL: '♥', SHOP: '$',
    BOSS: '☠', SECRET: '?'
  }[room.type] || '';
  ctx.fillText(glyph, x + cellSize / 2 - 4, y + cellSize / 2 + 4);
}

function drawDoorConnector(ctx, a, b, cellSize) {
  const ax = a.gridX * cellSize + cellSize / 2;
  const ay = a.gridY * cellSize + cellSize / 2;
  const bx = b.gridX * cellSize + cellSize / 2;
  const by = b.gridY * cellSize + cellSize / 2;
  ctx.strokeStyle = '#666';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(ax, ay);
  ctx.lineTo(bx, by);
  ctx.stroke();
}
```

- [ ] **Step 3: Add the room-canvas dispatch**

Replace the previous "render the map canvas" entrypoint with a room-canvas dispatcher:

```javascript
function renderRoomCanvas() {
  const canvas = document.getElementById('dungeon-room-canvas');
  if (!canvas) return;
  const activeEncounterId = canvas.dataset.activeEncounterId;
  if (activeEncounterId && activeEncounterId.length > 0) {
    // Active encounter -> existing JRPG combat renderer
    renderJRPGCombat(canvas);  // KEEP THIS FUNCTION
    return;
  }
  renderRoomScenery(canvas);
}

function renderRoomScenery(canvas) {
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = backgroundFor(canvas.dataset.currentRoomType);
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  drawRoomDoors(ctx, canvas);
  drawRoomCenterGlyph(ctx, canvas);
}

function backgroundFor(type) {
  switch (type) {
    case 'TREASURE': return '#3a2e10';
    case 'HEAL':     return '#0f2418';
    case 'SHOP':     return '#0d2630';
    case 'SECRET':   return '#1a1a22';
    case 'BOSS':     return '#2a0a0a';
    default:         return '#10101a';
  }
}

function drawRoomDoors(ctx, canvas) {
  // Use the room-data attributes you choose to add — alternatively read minimap JSON
  // to find the current room's doors. Draw simple lighter rectangles on edges
  // matching present doors.
}

function drawRoomCenterGlyph(ctx, canvas) {
  ctx.fillStyle = '#fff';
  ctx.font = '64px monospace';
  ctx.textAlign = 'center';
  const glyph = {
    TREASURE: '◆', HEAL: '♥', SHOP: '$',
    SECRET: '?', ENTRANCE: '⬡', BOSS: '☠'
  }[canvas.dataset.currentRoomType] || '';
  ctx.fillText(glyph, canvas.width / 2, canvas.height / 2);
}
```

- [ ] **Step 4: Update the entrypoint that runs after each HTMX swap**

Find the existing `htmx.onLoad(...)` or similar hook in `dungeon.js`. Replace its body so it calls `renderMinimap()` and `renderRoomCanvas()` instead of the old tile renderer:

```javascript
function dungeonInit() {
  renderMinimap();
  renderRoomCanvas();
  bindKeyboardControls();  // keep existing
}

if (window.htmx) {
  htmx.onLoad(dungeonInit);
} else {
  document.addEventListener('DOMContentLoaded', dungeonInit);
}
```

- [ ] **Step 5: Update CSS for the new layout**

Edit `dungeon.css`:
- Add `.sh-dungeon-side-panel` styles (flex column, fixed/fluid width).
- Add `.sh-dungeon-minimap` styles (square, bordered).
- Add `.sh-dungeon-relic-shelf` styles (icon row with hover popovers).
- Remove rules targeting `.sh-dungeon-map-panel` tile internals (e.g. per-tile pixel styles, fog overlay), keeping the panel itself.
- Tighten `.sh-dungeon-canvas.sh-dungeon-room-canvas` to fit the JRPG combat layout on a smaller canvas.

(The exact CSS is implementation-dependent. Match the existing pixel-art aesthetic — palette already defined in the file.)

- [ ] **Step 6: Smoke-compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS. JS/CSS aren't compiled by Maven but the templates that reference them must still resolve.

- [ ] **Step 7: Stage (do not commit until modals are in)**

```bash
git add src/main/resources/static/js/dungeon.js src/main/resources/static/css/dungeon.css
```

---

## Task 17: Create the four modal fragments and wire them into HTMX

Each modal posts to a controller endpoint, swaps the whole `#dungeon-session-content` element with the result.

**Files:**
- Create: `src/main/resources/templates/fragments/dungeon-treasure-modal.html`
- Create: `src/main/resources/templates/fragments/dungeon-elite-reward-modal.html`
- Create: `src/main/resources/templates/fragments/dungeon-shop-modal.html`
- Create: `src/main/resources/templates/fragments/dungeon-secret-modal.html`

- [ ] **Step 1: Treasure modal (pick 1 of 3)**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>
<div th:fragment="modal" class="sh-dungeon-modal-overlay">
    <div class="sh-dungeon-modal sh-dungeon-treasure-modal">
        <h2 class="sh-dungeon-modal-title" th:text="#{dungeon.treasure-title}">Treasure</h2>
        <p class="sh-dungeon-modal-prompt" th:text="#{dungeon.relic.pick}">Pick a relic</p>
        <div class="sh-dungeon-relic-choices">
            <button th:each="rid : ${pendingPick.offer}"
                    class="sh-dungeon-relic-choice"
                    hx-post="/dungeon/relic/pick"
                    hx-target="#dungeon-session-content"
                    hx-swap="outerHTML"
                    name="relicId" th:value="${rid.name()}">
                <iconify-icon th:icon="${T(com.HendrikHoemberg.StudyHelper.controller.RelicIcons).iconFor(rid)}"></iconify-icon>
                <div class="sh-dungeon-relic-choice-name"
                     th:text="#{__${'dungeon.relic.' + #strings.toLowerCase(rid.name().replace('_','-')) + '.name'}__}">Name</div>
                <div class="sh-dungeon-relic-choice-desc"
                     th:text="#{__${'dungeon.relic.' + #strings.toLowerCase(rid.name().replace('_','-')) + '.desc'}__}">Description</div>
            </button>
        </div>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 2: Elite-reward modal (pick 1 of 2)**

Same shape as treasure modal; change title to `#{dungeon.elite-reward-title}`, change `class="sh-dungeon-elite-reward-modal"`. The offer list contains 2 entries instead of 3 — the template iterates whatever's in `pendingPick.offer`, so no template logic change needed.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>
<div th:fragment="modal" class="sh-dungeon-modal-overlay">
    <div class="sh-dungeon-modal sh-dungeon-elite-reward-modal">
        <h2 class="sh-dungeon-modal-title" th:text="#{dungeon.elite-reward-title}">Elite Reward</h2>
        <p class="sh-dungeon-modal-prompt" th:text="#{dungeon.relic.pick}">Pick a relic</p>
        <div class="sh-dungeon-relic-choices">
            <button th:each="rid : ${pendingPick.offer}"
                    class="sh-dungeon-relic-choice"
                    hx-post="/dungeon/relic/pick"
                    hx-target="#dungeon-session-content"
                    hx-swap="outerHTML"
                    name="relicId" th:value="${rid.name()}">
                <iconify-icon th:icon="${T(com.HendrikHoemberg.StudyHelper.controller.RelicIcons).iconFor(rid)}"></iconify-icon>
                <div class="sh-dungeon-relic-choice-name"
                     th:text="#{__${'dungeon.relic.' + #strings.toLowerCase(rid.name().replace('_','-')) + '.name'}__}">Name</div>
                <div class="sh-dungeon-relic-choice-desc"
                     th:text="#{__${'dungeon.relic.' + #strings.toLowerCase(rid.name().replace('_','-')) + '.desc'}__}">Description</div>
            </button>
        </div>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 3: Shop modal (buy multiple, skip to leave)**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>
<div th:fragment="modal" class="sh-dungeon-modal-overlay">
    <div class="sh-dungeon-modal sh-dungeon-shop-modal">
        <h2 class="sh-dungeon-modal-title" th:text="#{dungeon.shop-title}">Shop</h2>
        <p class="sh-dungeon-modal-prompt">
            <iconify-icon icon="lucide:coins"></iconify-icon>
            <span th:text="${state.score}">0</span>
        </p>
        <div class="sh-dungeon-relic-choices">
            <button th:each="entry : ${pendingPick.shopOffer.entries}"
                    class="sh-dungeon-relic-choice"
                    th:disabled="${state.score < entry.price or ownedRelics.contains(entry.relic)}"
                    hx-post="/dungeon/relic/buy"
                    hx-target="#dungeon-session-content"
                    hx-swap="outerHTML"
                    name="relicId" th:value="${entry.relic.name()}">
                <iconify-icon th:icon="${T(com.HendrikHoemberg.StudyHelper.controller.RelicIcons).iconFor(entry.relic)}"></iconify-icon>
                <div class="sh-dungeon-relic-choice-name"
                     th:text="#{__${'dungeon.relic.' + #strings.toLowerCase(entry.relic.name().replace('_','-')) + '.name'}__}">Name</div>
                <div class="sh-dungeon-relic-choice-desc"
                     th:text="#{__${'dungeon.relic.' + #strings.toLowerCase(entry.relic.name().replace('_','-')) + '.desc'}__}">Description</div>
                <div class="sh-dungeon-relic-choice-price">
                    <iconify-icon icon="lucide:coins"></iconify-icon>
                    <span th:text="${entry.price}">75</span>
                </div>
            </button>
        </div>
        <button class="sh-btn sh-btn-ghost sh-dungeon-shop-skip"
                hx-post="/dungeon/relic/skip-shop"
                hx-target="#dungeon-session-content"
                hx-swap="outerHTML"
                th:text="#{dungeon.shop.skip}">Leave shop</button>
    </div>
</div>
</body>
</html>
```

(The Heal-2 consumable can be added in a follow-up — it requires a separate endpoint or a generalized `/dungeon/shop/buy` that distinguishes relic vs consumable. Per the spec, keep the catalog lean and skip the consumable in this first cut; the field is wired through generation for forward compatibility.)

- [ ] **Step 4: Secret modal (single free relic OR auto-applied bundle)**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
<body>
<div th:fragment="modal" class="sh-dungeon-modal-overlay">
    <div class="sh-dungeon-modal sh-dungeon-secret-modal">
        <h2 class="sh-dungeon-modal-title" th:text="#{dungeon.secret-title}">Hidden Chamber</h2>
        <p class="sh-dungeon-modal-prompt" th:text="#{dungeon.secret-found}">You feel a draft...</p>
        <div class="sh-dungeon-relic-choices">
            <button th:each="rid : ${pendingPick.offer}"
                    class="sh-dungeon-relic-choice"
                    hx-post="/dungeon/relic/pick"
                    hx-target="#dungeon-session-content"
                    hx-swap="outerHTML"
                    name="relicId" th:value="${rid.name()}">
                <iconify-icon th:icon="${T(com.HendrikHoemberg.StudyHelper.controller.RelicIcons).iconFor(rid)}"></iconify-icon>
                <div class="sh-dungeon-relic-choice-name"
                     th:text="#{__${'dungeon.relic.' + #strings.toLowerCase(rid.name().replace('_','-')) + '.name'}__}">Name</div>
                <div class="sh-dungeon-relic-choice-desc"
                     th:text="#{__${'dungeon.relic.' + #strings.toLowerCase(rid.name().replace('_','-')) + '.desc'}__}">Description</div>
            </button>
        </div>
    </div>
</div>
</body>
</html>
```

(For the Bundle variant of `SecretReward`, the controller's `pickForRoom` returns `null` — see Task 9. That means no pending pick appears, so we need to auto-apply the bundle when the player enters. Add an `applyBundleOnEntry` step into `DungeonSessionService.move` *after* the call to `navigationService.move`, before computing `pickForRoom`. The implementer should add ~10 lines: if the destination is SECRET with a Bundle reward and not yet cleared, apply the bundle to the state and mark the room cleared. Tests for this auto-apply belong in `DungeonSessionServiceTests`.)

- [ ] **Step 5: Compile + restart the app and visually inspect**

Run: `./mvnw compile`
Then run the app (`./mvnw spring-boot:run`) and:
- Start a Small dungeon
- Walk into rooms; verify minimap reveals
- Trigger a Treasure room and verify the modal renders
- Pick a relic and verify it appears in the relic shelf

(If running the app is heavy in this dev loop, defer manual verification to Task 20.)

- [ ] **Step 6: Commit the UI rewrite (Tasks 15-17 together)**

```bash
git add src/main/resources/templates/fragments/dungeon-game.html \
        src/main/resources/templates/fragments/dungeon-treasure-modal.html \
        src/main/resources/templates/fragments/dungeon-elite-reward-modal.html \
        src/main/resources/templates/fragments/dungeon-shop-modal.html \
        src/main/resources/templates/fragments/dungeon-secret-modal.html \
        src/main/resources/static/js/dungeon.js \
        src/main/resources/static/css/dungeon.css \
        src/main/java/com/HendrikHoemberg/StudyHelper/controller/RelicIcons.java
git commit -m "feat(dungeon): rewrite UI for room-graph layout with minimap and relic shelf"
```

---

## Task 18: Add i18n strings

All new template references depend on these keys. Both `messages.properties` and `messages_de.properties` must be updated to avoid runtime missing-key warnings.

**Files:**
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_de.properties`

- [ ] **Step 1: Append to `messages.properties`**

Append these blocks to the end of the file:

```
# Dungeon — room types
dungeon.room.entrance=Entrance
dungeon.room.combat=Combat Room
dungeon.room.elite=Elite Room
dungeon.room.treasure=Treasure Room
dungeon.room.heal=Resting Site
dungeon.room.shop=Shop
dungeon.room.boss=Boss Chamber
dungeon.room.secret=Hidden Chamber

# Dungeon — modal titles + prompts
dungeon.relics=Relics
dungeon.relic.pick=Pick a relic
dungeon.treasure-title=Treasure
dungeon.elite-reward-title=Elite Reward
dungeon.shop-title=Shop
dungeon.secret-title=Hidden Chamber
dungeon.secret-found=You feel a draft from a hidden wall...
dungeon.shop.skip=Leave shop
dungeon.shop.buy=Buy
dungeon.shop.insufficient-score=Not enough score

# Dungeon — relic names + descriptions
dungeon.relic.iron-plate.name=Iron Plate
dungeon.relic.iron-plate.desc=+1 max HP, heal +1 now.
dungeon.relic.buckler.name=Buckler
dungeon.relic.buckler.desc=+1 max shield, grant +1 shield now.
dungeon.relic.lucky-charm.name=Lucky Charm
dungeon.relic.lucky-charm.desc=+5 score per correct answer.
dungeon.relic.sharp-focus.name=Sharp Focus
dungeon.relic.sharp-focus.desc=Streak banks a shield every 2 correct (down from 3).
dungeon.relic.compass.name=Compass
dungeon.relic.compass.desc=Adjacent unentered rooms show their type on the minimap.
dungeon.relic.lucky-coin.name=Lucky Coin
dungeon.relic.lucky-coin.desc=First wrong answer in the run deals no damage.
dungeon.relic.phoenix-feather.name=Phoenix Feather
dungeon.relic.phoenix-feather.desc=On defeat, resurrect at 1 HP. Consumed on use.
dungeon.relic.spectacles.name=Spectacles
dungeon.relic.spectacles.desc=One wrong option is greyed out on quiz prompts. No effect on flashcards.
dungeon.relic.war-banner.name=War Banner
dungeon.relic.war-banner.desc=Clearing a Combat room grants +1 shield (capped).
dungeon.relic.map-sense.name=Map Sense
dungeon.relic.map-sense.desc=The hidden chamber appears on the minimap.

# Dungeon — shop consumable (future-proofing)
dungeon.shop.consumable.heal=Heal 2 HP

# Dungeon — completion screen additions
dungeon.complete.relics-found=Relics acquired
```

- [ ] **Step 2: Append German translations to `messages_de.properties`**

```
# Dungeon — Raumtypen
dungeon.room.entrance=Eingang
dungeon.room.combat=Kampfraum
dungeon.room.elite=Eliteraum
dungeon.room.treasure=Schatzkammer
dungeon.room.heal=Rastplatz
dungeon.room.shop=Laden
dungeon.room.boss=Bosskammer
dungeon.room.secret=Verborgene Kammer

# Dungeon — Modaltitel + Prompts
dungeon.relics=Relikte
dungeon.relic.pick=Wähle ein Relikt
dungeon.treasure-title=Schatz
dungeon.elite-reward-title=Elite-Belohnung
dungeon.shop-title=Laden
dungeon.secret-title=Verborgene Kammer
dungeon.secret-found=Du spürst einen Luftzug aus einer verborgenen Wand...
dungeon.shop.skip=Laden verlassen
dungeon.shop.buy=Kaufen
dungeon.shop.insufficient-score=Nicht genug Punkte

# Dungeon — Reliktnamen + Beschreibungen
dungeon.relic.iron-plate.name=Eisenplatte
dungeon.relic.iron-plate.desc=+1 Max-LP, heilt jetzt +1.
dungeon.relic.buckler.name=Faustschild
dungeon.relic.buckler.desc=+1 Max-Schild, gewährt jetzt +1 Schild.
dungeon.relic.lucky-charm.name=Glücksbringer
dungeon.relic.lucky-charm.desc=+5 Punkte pro richtiger Antwort.
dungeon.relic.sharp-focus.name=Scharfer Fokus
dungeon.relic.sharp-focus.desc=Serie bringt alle 2 richtigen Antworten einen Schild (statt 3).
dungeon.relic.compass.name=Kompass
dungeon.relic.compass.desc=Angrenzende unbetretene Räume zeigen ihren Typ auf der Minikarte.
dungeon.relic.lucky-coin.name=Glücksmünze
dungeon.relic.lucky-coin.desc=Die erste falsche Antwort des Laufs verursacht keinen Schaden.
dungeon.relic.phoenix-feather.name=Phönixfeder
dungeon.relic.phoenix-feather.desc=Bei Niederlage Wiederbelebung mit 1 LP. Wird dabei verbraucht.
dungeon.relic.spectacles.name=Brille
dungeon.relic.spectacles.desc=Eine falsche Option wird bei Quiz-Aufgaben ausgegraut. Keine Wirkung bei Karteikarten.
dungeon.relic.war-banner.name=Kriegsbanner
dungeon.relic.war-banner.desc=Das Räumen eines Kampfraums gewährt +1 Schild (Maximum beachten).
dungeon.relic.map-sense.name=Kartensinn
dungeon.relic.map-sense.desc=Die verborgene Kammer erscheint auf der Minikarte.

# Dungeon — Verbrauchsgegenstand im Laden (Zukunft)
dungeon.shop.consumable.heal=2 LP heilen

# Dungeon — Erweiterungen für den Abschlussbildschirm
dungeon.complete.relics-found=Relikte erhalten
```

- [ ] **Step 3: Smoke-restart the app to check for missing-key warnings**

Run: `./mvnw spring-boot:run` (or just compile if you trust startup will catch them at runtime).
Expected: no `MessageSource` warnings in the console for `dungeon.*` keys.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/messages.properties src/main/resources/messages_de.properties
git commit -m "i18n(dungeon): add room type, relic, and modal strings (EN + DE)"
```

---

## Task 19: Update `dungeon-complete.html` for relic stats

Adds a single line showing the count of relics acquired, plus a small icon row of the relics themselves. The data comes from the existing `stats` object (now containing `relicsAcquired`).

**Files:**
- Modify: `src/main/resources/templates/fragments/dungeon-complete.html`

- [ ] **Step 1: Open the file and find the existing stats list**

Look for the block that renders `dungeon.complete.longest-streak`, `dungeon.complete.elites-cleared`, `dungeon.complete.shields-used`. Immediately after those three lines, add a fourth in the same style:

```html
<div class="sh-dungeon-complete-stat">
    <span th:text="#{dungeon.complete.relics-found}">Relics acquired</span>
    <span th:text="${stats.relicsAcquired}">0</span>
</div>
```

If you'd like, also add a small icon row of the acquired relics, sourced from the session state. Pass `state` as a model attribute in `stashAndRender` (it's already passed implicitly through the completion fragment — check `prepareGame` and add `state` to the `model` in the completion branch if it isn't there yet):

```html
<div class="sh-dungeon-complete-relics" th:if="${state != null and !state.ownedRelics.isEmpty()}">
    <iconify-icon th:each="rid : ${state.ownedRelics}"
                  th:icon="${T(com.HendrikHoemberg.StudyHelper.controller.RelicIcons).iconFor(rid)}"
                  th:title="#{__${'dungeon.relic.' + #strings.toLowerCase(rid.name().replace('_','-')) + '.name'}__}">
    </iconify-icon>
</div>
```

(If `state` isn't yet on the completion model, add `model.addAttribute("state", state);` in the completion branch of `stashAndRender`. This is a one-line change.)

- [ ] **Step 2: Compile and smoke-run**

Run: `./mvnw compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/fragments/dungeon-complete.html \
        src/main/java/com/HendrikHoemberg/StudyHelper/controller/DungeonController.java
git commit -m "feat(dungeon): show relics acquired and icons on completion screen"
```

---

## Task 20: Full-suite verification + manual browser run

The final task verifies that the rewrite holds together end-to-end. No new code; just running and observing.

- [ ] **Step 1: Run the full dungeon test suite**

Run: `./mvnw test -Dtest='Dungeon*'`
Expected: all PASS. If any test fails, fix before continuing.

- [ ] **Step 2: Run the full project test suite**

Run: `./mvnw test`
Expected: all PASS. The room-graph rewrite must not have broken anything outside the dungeon module. If a non-dungeon test fails, the most likely cause is a stale reference to a deleted dto type — search for `DungeonPosition`, `DungeonTile`, `DungeonTileType` across the project and fix any leftover references.

- [ ] **Step 3: Start the app and exercise the dungeon manually**

```bash
./mvnw spring-boot:run
```

Browser checklist (single Small dungeon run):
- Wizard → select Dungeon → Flashcards → Small → start.
- The dungeon-game view renders: HUD, room canvas, minimap canvas, control buttons.
- Walk in each cardinal direction; minimap reveals adjacent rooms; buttons disable for walls.
- Trigger a COMBAT room; combat scene renders; answer flashcard.
- Find the TREASURE room; modal opens; pick a relic; verify it appears in the relic shelf.
- Find the SHOP room; modal opens; buy a relic; verify score deduction; press "Leave shop"; verify shop becomes cleared.
- Find the HEAL room; verify HP fills + shield grants; re-enter and verify no double-apply.
- Find the ELITE room; clear gauntlet; relic-reward modal opens; pick.
- Find the BOSS room; clear gauntlet; verify dungeon-complete screen shows new stats.
- Take a wrong answer with shields = 0 to verify HP reduction and `takeDamage` path.

Mobile narrow viewport (DevTools, ~375px):
- Room canvas, minimap, controls, relic shelf all visible and tappable.
- Modals fit without overflow.

- [ ] **Step 4: Spot-check a Medium run**

Same checklist, Medium size. Verify there are 2 Elite rooms, 2 Treasure rooms NOT present (only 1 in Medium), 9 Combat rooms.

- [ ] **Step 5: If a regression surfaces, address it in a focused commit**

Fix any defect, run `./mvnw test`, commit with `fix(dungeon): ...`. Do not bundle multiple fixes into one commit.

- [ ] **Step 6: Final commit (if any verification fixes were needed)**

Otherwise, mark the plan complete.

---

## Spec coverage map

Quick check that each spec section has at least one task:

| Spec section | Task(s) |
|---|---|
| Motivation | n/a (background) |
| Architecture — service split | Tasks 6, 7, 9, 10 |
| State model | Tasks 4, 8 |
| Map generation algorithm | Task 5 |
| Room semantics | Tasks 7 (combat/elite/boss), 6 (heal), 9 (treasure/shop/secret pendingPick), 17 (modals) |
| Encounter & answer loop | Tasks 7, 11, 12, 13 |
| Relic catalog | Tasks 1, 10, 11, 12, 13, 14 |
| UI | Tasks 15, 16, 17 |
| Balance constants | Tasks 5, 7, 8, 10 |
| Implementation sequencing | Plan reflects spec ordering with one mechanical reorder: relic hooks are split by service file (Tasks 11-13) rather than by relic |
| Test plan | Each task includes its own test step |

## Out of scope (preserved from spec)

- Multi-floor pacing.
- Negative or curse relics.
- Hand-coded relic synergies.
- Cosmetic floor themes.
- Boss redesign beyond removing the post-clear reward.
- Persistence between runs.
- Wizard / source-picker changes beyond the existing dungeon-size availability gating.
- Heal-2 consumable in the Shop modal (data plumbed; UI deferred — non-blocking).
