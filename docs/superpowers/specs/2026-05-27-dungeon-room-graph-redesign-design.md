# Dungeon Room-Graph Redesign — Design

**Date:** 2026-05-27
**Status:** Draft for review
**Scope:** Replace the dungeon tile grid with a Binding-of-Isaac-style room graph. Each room is one screen with one purpose. Add a lean room-type menu (Combat, Elite, Treasure, Heal, Shop, Secret, Boss), a reveal-on-enter minimap, score-as-currency with a small punchy relic catalog, and the supporting UI rewrite. Stays one floor per run.

## Motivation

The current dungeon (after the 2026-05-27 overhaul) generates a rooms+corridors *tile grid* — but the player still walks tile-by-tile through corridors, fog reveals per tile, branching is cosmetic, every Combat tile plays the same, and the only "currency" (score) is a number you cannot spend. Rooms feel like wide hallways; the strategic choice is which corner to walk into, not which door to open.

This redesign targets two problems:

1. **The walk is friction.** In a study tool, the *game* is answering the card. Tile-walking between encounters costs input and screen real estate without contributing tension or decision-making.
2. **Rooms have no identity.** Every room is generic floor with maybe an encounter on it. There's no Treasure-room moment, no Shop, no "do I take the risky Elite for the relic" decision because there's nothing to pick from anyway.

The fix: collapse the tile grid into a graph of discrete rooms, give each room one purpose, and make Treasure/Shop/Elite rooms grant *relics* — small persistent run-scoped modifiers that produce build variety. Replay value comes from random room layouts × random relic combinations.

**Pillars:** room identity (each room does one thing), real choice (which door, which relic), legible decisions (minimap shows what you know, relic shelf shows your build).

## Architecture

The existing `DungeonSessionService` → `DungeonNavigationService` / `DungeonEncounterService` / `DungeonDamage` split from the previous overhaul survives. The internals of the first two are rewritten; `DungeonDamage` is unchanged in shape but gets new entry points for relic hooks.

**`DungeonMapGenerator`** *(existing file, rewritten)*
- Pure function. Input: `DungeonSize`, normal encounter ids, elite gauntlet groups, `RelicCatalog`. Output: `DungeonMap` (room graph).
- Generates rooms via random walk on a conceptual lattice, assigns room types, places relic offers, embeds one Secret room.
- Seeded `Random`; tests pin layouts.

**`DungeonNavigationService`** *(existing file, rewritten)*
- Owns movement and per-room first-entry effects.
- `move(state, direction) → state`: if `currentRoom.doors.containsKey(direction)`, set `currentRoomId = room.doors.get(direction)`, mark visited, reveal adjacent room markers, apply first-entry effect if any (Heal full-heals; non-combat rooms set a `pendingAction`; combat/elite/boss trigger encounter activation via the orchestrator).
- No walkability checks, no fog-per-tile, no trap effects (traps removed).

**`DungeonEncounterService`** *(existing file, extended)*
- Owns encounter lifecycle: activation, answering, gauntlet sequencing, boss progression, streak/shield bookkeeping. As today.
- New: on Elite clear, sets `pendingRelicPick = ElitePick(roomId, offer)`. The orchestrator routes the pick to the new `DungeonRelicService`.
- Activation is keyed by `roomId` instead of `position`.

**`DungeonRelicService`** *(new)*
- Owns the relic catalog, pick/buy/skip flows, and the application of "on-acquire" effects (Iron Plate's +1 max HP, Buckler's +1 max shield).
- Pure functions over `DungeonSessionState`. No persistence beyond the session state itself.
- Methods: `pick(state, relicId) → state`, `buy(state, relicId) → state` (validates score), `skipShop(state) → state`.
- Relic "passive" hooks live where they fire (scoring/streak/damage/minimap), not here — this service owns acquisition only.

**`DungeonDamage`** *(existing file, extended)*
- New hook points consulted before shield consumption: Lucky Coin (first-wrong-free), Phoenix Feather (resurrect at 1 HP on lethal).
- Hooks read from `state.ownedRelics` and produce updated state. Single chokepoint preserved.

**`DungeonSessionService`** *(orchestrator, lightly updated)*
- `move(state, direction)` → navigation.move → (if landed in encounter room) encounter.activateAt; (if landed in Heal/Treasure/Shop) navigation flags the action.
- `answerX` → encounter service → if `pendingRelicPick` set, expose to the controller for the modal.
- New: `pickRelic(state, relicId)`, `buyRelic(state, relicId)`, `skipShop(state)` → delegate to `DungeonRelicService`.
- Owns win/defeat detection (unchanged shape).

**Dependency direction**: Session → {Navigation, Encounter, Relic}; all three → `DungeonDamage` for HP changes; all three → `RelicCatalog` for relic lookups. Navigation, Encounter, and Relic do not depend on each other.

**Controller** gains three endpoints (`/dungeon/relic/pick`, `/dungeon/relic/buy`, `/dungeon/relic/skip-shop`). Existing `move`/`answer` endpoints unchanged in shape; their return DTO now includes `pendingRelicPick` when one is open.

## State model

**`DungeonRoom`** *(new)*
```
RoomId id
RoomType type                      // ENTRANCE | COMBAT | ELITE | TREASURE | HEAL | SHOP | BOSS | SECRET
Map<DungeonDirection, RoomId> doors  // at most 4 (N/E/S/W)
boolean visited                    // ever entered
boolean cleared                    // encounter resolved or one-time effect consumed
String encounterId                 // null for non-combat rooms
List<String> gauntletGroup         // ELITE only: ordered encounter ids
TreasureOffer treasureOffer        // TREASURE only: List<RelicId> of size 3
TreasureOffer eliteOffer           // ELITE only: List<RelicId> of size 2, granted after clear
ShopOffer shopOffer                // SHOP only: List<{RelicId, price}> of size 2 + optional consumable
SecretReward secretReward          // SECRET only: relic id or full-heal-bundle
GridPos gridPos                    // (x, y) on the minimap lattice; not used for gameplay
```

`gridPos` exists so the minimap renderer can lay rooms out spatially. It has no effect on movement or adjacency — adjacency is solely the `doors` map.

**`DungeonMap`** *(rewritten)*
```
Map<RoomId, DungeonRoom> rooms
RoomId entranceRoomId
RoomId bossRoomId
int lattice                        // square edge length for minimap layout (informational)
```

`width` / `height` / `tiles` / `gauntletGroups`-as-separate-field are gone. Gauntlet groups now live on the Elite room directly.

**`DungeonSessionState`** *(updated)*
```
DungeonConfig config
DungeonMap map
RoomId currentRoomId               // replaces DungeonPosition playerPosition
int health
int healthCap                      // NEW: starts at STARTING_HEALTH_CAP, mutated by Iron Plate
int shields
int shieldCap                      // NEW: starts at MAX_SHIELDS, mutated by Buckler
int streak
int score
int correctCount
int answeredCount
String activeEncounterId           // unchanged
int bossIndex                      // unchanged
List<String> gauntletQueue         // unchanged
List<RelicId> ownedRelics          // NEW
PendingRelicPick pendingRelicPick  // NEW: { type=TREASURE|ELITE|SHOP, roomId, offer }; null when none open
boolean won
boolean defeated
DungeonRunStats stats
```

Fields `playerPosition` and any tile-flag state are removed. `DungeonRunStats` gains `int relicsAcquired`.

**Deleted types:** `DungeonPosition`, `DungeonTile`, `DungeonTileType`. `DungeonDirection` is preserved but its semantics narrow from "tile step" to "door selection."

**Saved-session compatibility:** the schema change breaks deserialization of in-flight saved runs. Reuse the existing try/catch on `InvalidClassException` / `ObjectStreamException` added in the previous overhaul; show the same one-time flash: "Your saved dungeon was from an older version and could not be resumed." No migration code.

## Map generation algorithm

**Inputs:** `DungeonSize`, normal encounter ids, elite gauntlet groups, `RelicCatalog`.

**Room counts per size:**

| Size | Combat | Elite | Treasure | Heal | Shop | Secret | Boss | Entrance | **Total rooms** |
|---|---|---|---|---|---|---|---|---|---|
| Small | 6 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 13 |
| Medium | 9 | 2 | 1 | 1 | 1 | 1 | 1 | 1 | 17 |
| Large | 15 | 3 | 2 | 1 | 1 | 1 | 1 | 1 | 25 |

Card totals match the current `DungeonSize` constants (10 / 16 / 29) — Combat-room count × 1 card + Elite-room count × cards-per-gauntlet + boss-prompt count. The Secret room is always present; finding it is the gameplay.

**Lattice sizes** (for minimap layout, not movement): Small 7×7, Medium 9×9, Large 11×11.

**Algorithm** (seeded `Random` so tests can pin layouts):

1. **Random-walk room placement.** Place Entrance at lattice center. While room count < target: pick an existing placed room with at least one empty cardinal-neighbor lattice cell; pick a random empty cell; place a room there with a door connecting them. Produces a connected tree of rooms.
2. **Add 1–2 loop doors.** Find adjacent-on-lattice but not-yet-connected room pairs; add a door between a small number of them (1 for Small/Medium, 2 for Large). Creates routing choices.
3. **Assign room types.** All placed rooms start COMBAT. Then in this order:
   - BOSS → room with greatest graph distance from Entrance.
   - SHOP → a leaf room (one door) nearest the Entrance, so the player can buy before committing deep.
   - TREASURE → a leaf farther from Entrance.
   - HEAL → a non-critical-path room near the midpoint of Entrance → Boss distance.
   - ELITE × N → leaf rooms, excluding Entrance/Boss/Shop/Treasure rooms. If too few leaves exist, attach to any non-critical-path COMBAT room.
   - Convert any over-budget rooms back to COMBAT and trim to the budget.
4. **Embed Secret room.** Pick a lattice cell that is *empty* and adjacent (on the lattice) to two existing rooms; place SECRET there with **no doors** (it's discovered, not connected). Record the two "host" rooms; entering either of them gives a small "draft from a hidden wall" hint and reveals the SECRET marker on the minimap. Owning the Map Sense relic reveals it immediately.
5. **Generate Treasure offer.** Sample 3 distinct RelicIds from the Common pool.
6. **Generate Elite offers.** For each Elite room, sample 2 distinct RelicIds from the Elite pool.
7. **Generate Shop offer.** Sample 2 distinct RelicIds from `Common ∪ {Map Sense}` with prices (see Balance). Optionally include one consumable slot ("Heal 2 HP — 30 score").
8. **Pick Secret reward.** 50/50: one free relic from `Common ∪ Elite` (Secret can grant Elite-pool relics, but not Map Sense — which is Shop-exclusive), or a bundle (full heal + 1 shield + 50 score).

**Validation guards** (regenerate on failure, cap 10 attempts):
- Boss reachable from Entrance via doors.
- At least one Shop, one Heal, one Treasure placed.
- All Elites placed.
- Secret room has at least one host room.

Past the cap, throw `IllegalStateException` — would only fire on a tuning bug.

**What's gone from the previous generator:** L-corridor carving, MST math, per-tile placement, trap placement, secret-wall-as-tile, fog reveal. Approximately the entire body of the current `DungeonMapGenerator` is replaced.

## Room semantics

Each room is one screen with one purpose. Entering reveals contents immediately. Doors are traversable at any time *unless* an encounter is active (existing movement-while-encounter-active rule).

**ENTRANCE.** Empty starter room. Doors only. Re-entering = no effect.

**COMBAT.** One encounter (flashcard or quiz prompt). Wrong answer = `DungeonDamage.takeDamage(1)` (shield-first). Right = streak++, room cleared, pass-through forever after.

**ELITE.** Gauntlet of 2-3 prompts (unchanged from current overhaul). Clear = full heal + 1 shield + pick 1 of 2 relics (Elite pool). Wrong mid-gauntlet = abort (shield absorbs damage if available), retry on re-entry. Stays ELITE until cleared.

**TREASURE.** No enemy. On first entry: room view shows three relic cards. Player picks one (POST). Other two discarded. Room becomes cleared pass-through. No cost.

**HEAL.** No enemy. On first entry: heal to full + grant +1 shield (capped). Room becomes cleared pass-through. One-time campfire.

**SHOP.** No enemy. On first entry: shows 2 relics with prices in score, plus optional "Heal 2 HP — 30 score" consumable. Player may buy any/all they can afford or skip. Re-entering before clearing returns the same offer. Becomes cleared pass-through when the player explicitly leaves (any door press after first entry).

**SECRET.** Hidden until revealed. Revealed by: (a) entering a host room (hint shown), (b) owning Map Sense, (c) having visited *both* host rooms (auto-reveal). Once revealed, the player can route to it via the host room — at which point it appears as a normal connection on the minimap. Contents: see secretReward.

**BOSS.** Boss prompt sequence (unchanged from current). Boss room has only the entrance door; no escape mid-fight (movement-while-encounter-active covers this). Clear = run won (no relic pick — relics only matter within the run).

**Cleared-room behavior.** Once a room's purpose is consumed, walking through it is free and silent. Backtracking is not punished — the loop topology and SECRET hosting both make backtracking part of normal play.

**Traps:** removed entirely. No trap rooms, no trap-as-modifier, no trap sprite, no trap audio. `DungeonDamage.takeDamage` only fires from wrong answers.

## Encounter & answer loop

Largely preserved from the existing post-overhaul code. The only structural change is keying by `roomId` instead of `position` and adding a `pendingRelicPick` after Elite clears.

**Activation.** `DungeonEncounterService.activateAt(state, roomId)`:
- `COMBAT` → activate that room's encounter.
- `ELITE` → look up gauntlet group on the room; activate first encounter, queue rest.
- `BOSS` → activate boss encounter sequence.
- Anything else → no-op (called only by orchestrator after navigation).

**Answer path.** Both `answerFlashcard` and `answerQuiz` funnel into a single private `processAnswer(state, encounter, correct, answerData)`:

1. Mark encounter `CLEARED` (or leave as `ACTIVE` and abort, if gauntlet just failed).
2. Update streak/shields:
   - if correct: `streak++`, then if `streak % effectiveStreakThreshold(state) == 0` and `shields < shieldCap`: `shields++`.
   - if wrong: `streak = 0`; route to `DungeonDamage.takeDamage(state, WRONG_ANSWER_DAMAGE)`.
3. Apply per-correct score (base + Lucky Charm bonus + War Banner shield grant if COMBAT clear).
4. Resolve what's next based on context:
   - Boss gauntlet → advance `bossIndex` or set `won = true`.
   - Elite gauntlet → advance queue; if cleared, full heal + shield + set `pendingRelicPick = ElitePick(roomId, eliteOffer)`. Stats: `elitesCleared++`.
   - Normal encounter → clear the room.

**`DungeonDamage.takeDamage(state, amount)`** — single HP-loss chokepoint, with new relic hooks before existing shield logic:

```
if Lucky Coin present and stats.luckyCoinsConsumed < count(LuckyCoin in ownedRelics):
    stats.luckyCoinsConsumed++
    return state unchanged
if shields > 0:
    shields--
    stats.shieldsUsed++
    return state unchanged in HP
hp -= amount
if hp <= 0:
    if Phoenix Feather present and not yet consumed:
        hp = 1
        remove one Phoenix Feather from ownedRelics
        return state
    defeated = true
return state
```

**`DungeonDamage.heal(state, amount)`** unchanged — caps at `state.healthCap` (now per-state, not constant, so Iron Plate works).

**Boss flow:** unchanged. Damage routes through `takeDamage` so shields and Phoenix still apply.

## Relic catalog

10 starter relics, three pools. All effects are stacking-friendly (owning two Iron Plates gives +2 max HP). The room budget naturally caps total relics per run to ~3–6.

**Common pool** (Treasure, Shop, Secret):

| ID | Name | Effect | Hook |
|---|---|---|---|
| IRON_PLATE | Iron Plate | +1 `healthCap`, heal +1 now | apply-on-acquire |
| BUCKLER | Buckler | +1 `shieldCap`, grant +1 shield now | apply-on-acquire |
| LUCKY_CHARM | Lucky Charm | +5 score per correct answer | `processAnswer` correct branch |
| SHARP_FOCUS | Sharp Focus | `effectiveStreakThreshold` → 2 | streak check |
| COMPASS | Compass | Adjacent unentered rooms show type icon on minimap | minimap renderer |
| LUCKY_COIN | Lucky Coin | First wrong answer in run deals 0 damage | `takeDamage` pre-hook |

**Elite pool** (only from Elite clears):

| ID | Name | Effect | Hook |
|---|---|---|---|
| PHOENIX_FEATHER | Phoenix Feather | On lethal damage, resurrect at 1 HP, consume one | `takeDamage` lethal branch |
| SPECTACLES | Spectacles | Multi-choice quiz: one wrong option greyed out (no-op on flashcards) | quiz encounter DTO |
| WAR_BANNER | War Banner | Clearing a COMBAT room grants +1 shield (capped) | `processAnswer` on COMBAT clear |

**Shop-exclusive:**

| ID | Name | Effect | Hook |
|---|---|---|---|
| MAP_SENSE | Map Sense | Secret room marker appears on minimap immediately | minimap renderer + apply-on-acquire |

**Shop pricing** (in score, per-relic at generation):
- Common-pool relic: 75
- Map Sense: 150
- Heal-2 consumable: 30

The base score economy still works: Combat clear = 10 score, Elite clear = 50 score, Boss prompt clear = 20 score each, Lucky Charm adds +5 per correct, Secret-bundle = +50. A typical Medium run grants roughly 200–350 score, enough for one Shop purchase. Map Sense is a real choice between economy and information.

**Stack rules:** infinite stacking. Multiple Iron Plates → cumulative max HP. Multiple Phoenix Feathers → multiple resurrections. Multiple Lucky Coins → multiple free hits, tracked by `stats.luckyCoinsConsumed` against owned count.

**Tooltip honesty:** Spectacles tooltip on Elite pool says "Quiz only — no effect on flashcards." The player should not feel cheated when picking it in a flashcard run.

**No curses, no removal, no synergies.** Synergies emerge naturally from stacking (Sharp Focus + War Banner = shields stockpile fast).

## UI

**Screen layout** (replaces the current full-canvas map + JRPG combat overlay):

```
┌─────────────────────────────────────────────────────────┐
│  HUD: ❤×3 / 5  🛡×1 / 2  ⚡×2  💰 240   Progress 4/13   │
├──────────────────────────┬──────────────────────────────┤
│                          │                              │
│                          │         MINIMAP              │
│      ROOM VIEW           │     (revealed rooms)         │
│   (current room)         │                              │
│                          ├──────────────────────────────┤
│                          │  RELIC SHELF                 │
│                          │  🪙 ❤ 🛡 ✨                  │
├──────────────────────────┴──────────────────────────────┤
│              DIRECTION CONTROLS  ▲ ◀ ▼ ▶                │
└─────────────────────────────────────────────────────────┘
```

On narrow viewports the right column stacks below the room view: HUD → Room View → Minimap → Relic Shelf → Controls.

**Room view** (single canvas, smaller than today's full map):
- Combat room not cleared → the existing JRPG combat scene (enemy sprite, animations, splash, particles). This is the part of the current `dungeon.js` that works well and ports straight over.
- Combat room cleared → empty room background with door openings.
- Treasure / Shop → room background + modal overlay with the offer.
- Heal → room background + "Rest at fire" button (auto-applies on entry the first time).
- Secret → room background + "Open Chest" button.
- Boss → JRPG combat scene with boss sprite, boss-splash, boss-progress badge (existing machinery).
- Doors drawn as openings on the N/E/S/W edges; only present edges show openings.

**Minimap** (new — replaces the fog-of-war tile renderer):
- Grid of room cells using each room's `gridPos`.
- Visited rooms: solid color by type (red=combat-uncleared, grey=combat-cleared, gold=treasure-uncleared, etc.).
- Adjacent unvisited rooms: faded `?` (or type icon if Compass owned).
- Unvisited and not-adjacent-to-visited: not drawn.
- Current room: highlighted with player marker.
- Doors between rooms: small connector lines.
- Secret room: invisible unless Map Sense owned or both host rooms visited; then drawn as a special marker connected to its host(s).
- Click-to-move is **not** enabled (we chose WASD/door traversal). Minimap is informational.

**HUD** (mostly unchanged):
- Hearts (current/max), shield count (current/max), score with coin icon, streak (existing pulse animation), progress (encounters cleared / total).
- New: relic shelf below the minimap. Iconify icons; hover/tap shows tooltip with relic name and effect text. Owned-count badge when stacked.

**Direction controls:**
- WASD/arrows on desktop; on-screen buttons on mobile (unchanged shape).
- Buttons disabled for directions with no door in the current room.
- Disabled while encounter active.
- Optional nicety: hovering/focusing a button highlights the destination room on the minimap.

**Treasure / Shop / Secret / Elite-reward modals:**
- Each modal shows relic cards (icon, name, effect, price for Shop).
- POST endpoints: `/dungeon/relic/pick`, `/dungeon/relic/buy`, `/dungeon/relic/skip-shop`.
- HTMX swaps in updated room view + HUD + minimap + relic shelf.

**What dies in `dungeon.js`:** tile-grid renderer, fog squares, walkability hover, per-tile reveal animations, trap-telegraphing renderer, secret-wall tile rendering. The combat renderer survives intact and moves into the room view's combat path.

**What stays:** `renderJRPGCombat`, splash machinery, particles, audio, HUD update wiring, HTMX swap hooks.

**Server-side template changes:**
- `dungeon-game.html` rewritten: drop the map canvas + tile JSON script; add minimap canvas, room canvas, relic shelf, modal mounts.
- New fragments: `dungeon-treasure-modal.html`, `dungeon-shop-modal.html`, `dungeon-elite-reward-modal.html`, `dungeon-secret-modal.html`.
- `dungeon-complete.html` gains a "Relics found" line and a relic-icon row.

**i18n additions** (representative):
- `dungeon.room.{combat,elite,treasure,heal,shop,secret,boss}` — room type names.
- `dungeon.relic.{id}.name` and `.desc` — one pair per relic.
- `dungeon.relic.pick` — "Pick a relic."
- `dungeon.shop.buy` / `.skip` / `.insufficient-score`.
- `dungeon.secret-found` — "You feel a draft from a hidden wall..."
- `dungeon.complete.relics-found` — "Relics found."

## Balance constants

| Constant | Value | Where |
|---|---|---|
| `STARTING_HEALTH` | 5 | `DungeonEncounterService` |
| `STARTING_HEALTH_CAP` | 5 | seeds `state.healthCap` |
| `BASE_STREAK_FOR_SHIELD` | 3 | streak threshold (Sharp Focus halves to 2) |
| `MAX_SHIELDS` | 2 | seeds `state.shieldCap` |
| `WRONG_ANSWER_DAMAGE` | 1 | `DungeonDamage.takeDamage` callsite |
| `COMBAT_CLEAR_SCORE` | 10 | per-correct score, then +Lucky Charm |
| `ELITE_CLEAR_SCORE` | 50 | bonus after gauntlet completion |
| `BOSS_PROMPT_SCORE` | 20 | per boss-prompt correct |
| `SECRET_BUNDLE_SCORE` | 50 | secret reward bundle option |
| `SHOP_RELIC_PRICE_COMMON` | 75 | Shop relic price |
| `SHOP_RELIC_PRICE_MAP_SENSE` | 150 | Shop relic price |
| `SHOP_CONSUMABLE_HEAL_PRICE` | 30 | "Heal 2 HP" cost |
| `SHOP_CONSUMABLE_HEAL_AMOUNT` | 2 | "Heal 2 HP" effect |

`DungeonSize.totalPrompts()` and `isAvailableFor(int)` numbers are unchanged from the previous overhaul (10 / 16 / 29 cards).

## Implementation sequencing

The room graph and tile grid are mutually exclusive — once the tile types are deleted, everything that touched them must be updated in the same change. This overhaul is harder to break into trivial commits than the previous one; the sequence below is the smallest practical set.

1. **Add new types alongside old (no wiring).** `RoomType`, `DungeonRoom`, `GridPos`, `RelicId` enum, `RelicCatalog`, `RelicPool`, `RelicEffect`, `PendingRelicPick`, `TreasureOffer`, `ShopOffer`, `SecretReward`. Existing code untouched.
2. **Saved-session compatibility guard.** Verify the existing try/catch on `InvalidClassException` still catches this wider schema change and shows the existing flash message. Add a test if missing.
3. **Big destructive swap.** Rewrite `DungeonMap` to the graph shape. Delete `DungeonPosition`, `DungeonTile`, `DungeonTileType`. Rewrite `DungeonMapGenerator` to the random-walk algorithm. Rewrite `DungeonNavigationService` to door-based `move`. Update `DungeonEncounterService.activateAt` signature to `(state, roomId)`. Update `DungeonSessionState`: drop `playerPosition`, add `currentRoomId`, `healthCap`, `shieldCap`, `ownedRelics`, `pendingRelicPick`. Tests in `DungeonMapGeneratorTests` and `DungeonNavigationServiceTests` rewritten in the same commit.
4. **Add `DungeonRelicService` + acquisition endpoints.** `/dungeon/relic/pick`, `/dungeon/relic/buy`, `/dungeon/relic/skip-shop`. Wire apply-on-acquire effects for Iron Plate, Buckler, Map Sense.
5. **Wire relic passive hooks.** One commit per logical group is fine:
   - Damage hooks: Phoenix Feather (resurrect), Lucky Coin (first-wrong-free) in `DungeonDamage`.
   - Scoring + streak: Lucky Charm, Sharp Focus, War Banner in `DungeonEncounterService`.
   - Minimap reveals: Compass, Map Sense in the server-rendered minimap state.
   - Quiz hint: Spectacles in the encounter DTO.
6. **UI rewrite.** New `dungeon-game.html`, new minimap canvas, new room canvas, relic shelf, modal mounts and fragments. `dungeon.js` keeps the JRPG combat renderer + audio + particles; deletes the tile-grid renderer; adds minimap renderer.
7. **i18n strings + `dungeon-complete.html` additions.** Room type names, relic name/desc pairs, completion screen relic row.

Steps 1–2 are pure scaffolding. Step 3 makes the game playable on the room graph with no relics. Steps 4–7 add the depth.

## Test plan

- **`DungeonMapGeneratorTests`** (rewritten):
  - Graph reachability: BFS from Entrance via doors reaches every non-Secret room.
  - Room type counts match the per-size budget exactly.
  - Boss room is the graph-farthest from Entrance.
  - At least one Shop / Heal / Treasure / Elite placed.
  - Elite rooms are leaves (one door) unless none available.
  - Secret room has at least one host and is not in the initially-revealed set.
  - Lattice positions are unique per room.
  - Seeded determinism: same seed → identical output.

- **`DungeonNavigationServiceTests`** (slimmed):
  - `move(state, direction)` with no door in that direction → state unchanged.
  - `move` with door → `currentRoomId` updates, `visited = true`, adjacent markers reveal.
  - Entering HEAL room first time → full heal + shield grant, `cleared = true`. Re-entering → no change.
  - Entering TREASURE/SHOP first time → sets `pendingRelicPick`, doesn't auto-clear (cleared on player action).

- **`DungeonEncounterServiceTests`** (expanded):
  - Activation by roomId routes COMBAT / ELITE / BOSS correctly.
  - Elite clear sets `pendingRelicPick` of type ELITE with the room's `eliteOffer`.
  - Boss clear does NOT set `pendingRelicPick`; sets `won = true`.
  - Gauntlet abort behavior unchanged (rests at room, retry on re-entry).

- **`DungeonRelicServiceTests`** (new):
  - `pick(state, relicId)` adds to `ownedRelics`, applies apply-on-acquire effects, clears `pendingRelicPick`.
  - `pick` with relicId not in the offer → rejected, state unchanged.
  - `buy(state, relicId)` validates score, deducts on success, rejects on insufficient.
  - `skipShop(state)` marks SHOP cleared, clears `pendingRelicPick`.

- **`DungeonRelicEffectTests`** (new):
  - For each relic with a passive hook, build a state with the relic owned, fire the hook, assert effect.
  - Multiples: two Iron Plates → +2 healthCap; two Lucky Coins → two free hits.

- **`DungeonDamageTests`** (expanded):
  - Lucky Coin absorbs first wrong-answer damage; shield not consumed.
  - Phoenix Feather resurrects on lethal; consumed from ownedRelics.
  - Multiple Phoenix Feathers consumed one at a time.

- **`DungeonSessionServiceTests`** (slimmed):
  - Orchestration only: `move` → navigation → (activate encounter if combat room), `answerX` → encounter → (route relic pick if pending), `pickRelic` → relic service.

- **`DungeonControllerTests`** (extended):
  - New relic endpoints validate active state, reject stale picks, return updated session content fragment.
  - `move` and `answer` endpoints unchanged.

- **`DungeonSessionStateTests`** (expanded):
  - Serialization round-trip for new fields including `ownedRelics`, `pendingRelicPick`, `healthCap`, `shieldCap`.

**Browser verification** (manual, per the project's verify skill):
- Full run on desktop: generate Small/Medium/Large; minimap reveals as expected; clear each room type once; verify each relic's effect by acquiring it and watching the next interaction.
- Mobile narrow viewport: minimap, room view, modals, controls all reachable, no overflow.

## Out of scope

Explicitly NOT in this spec:

- Multi-floor pacing or descend-or-bank meta-choice.
- Negative or curse relics; relic removal mechanics.
- Hand-coded relic synergies beyond natural stacking.
- Cosmetic floor themes, enemy palette swaps, music variation.
- Boss redesign beyond changing the post-clear flow (still N prompts in sequence).
- Persistence between runs: no meta-progression, no unlocks, no leaderboards, no daily seeds.
- Source picker / wizard changes beyond what existing dungeon-size availability already does.
- AI-generation flow changes.
- Tile-based "puzzle rooms" (out of scope by virtue of dropping tiles entirely).

These remain candidates for follow-up specs if the room-graph version proves worth investing further.
