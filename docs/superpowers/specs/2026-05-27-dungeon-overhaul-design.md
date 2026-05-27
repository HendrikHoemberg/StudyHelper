# Dungeon Mode Overhaul — Design

**Date:** 2026-05-27
**Status:** Draft for review
**Scope:** Replace the deterministic dungeon layout with a procedural rooms+corridors generator, add optional elite side-rooms with a multi-card gauntlet mechanic, introduce a streak→shield reward loop, refactor `DungeonSessionService` into focused services, telegraph traps, and restructure the study wizard so source selection precedes mode selection.

## Motivation

Dungeon mode today plays as a linear quiz with a map skin. The layout is the same every run for a given size (despite "Procedurally Place" comments in `DungeonMapGenerator`), encounters are placed in path-iteration order, the only branching is short symmetric stubs that dead-end, correct answers reward the player with *not* losing HP, and the wizard makes the user pick a dungeon size before knowing whether their selected sources can support it.

The overhaul targets two pillars: **replay value** (real procedural maps, varied layouts) and **meaningful gameplay** (real choice via optional risk/reward elites, real reward via shields). Score-as-currency, defeat-softening, and boss redesign are explicitly out of scope for this pass.

## Architecture

`DungeonSessionService` today is a 366-line god-class handling map mutation, encounter activation, answer scoring, win/lose detection, and stats. The overhaul splits it along its real responsibilities into four cooperating services:

**`DungeonMapGenerator`** *(existing file, rewritten)*
- Pure function. Input: `DungeonSize`, normal encounter ids, elite gauntlet groups. Output: `DungeonMap`.
- Produces rooms+corridors layout, places encounters and elites, places rest tiles and secret compartments.
- Uses a seeded `Random` so tests can pin layouts; production uses `new Random()`.
- No state, no dependencies on other dungeon services.

**`DungeonNavigationService`** *(new)*
- Owns movement, fog reveal, walkability, and per-tile effects (heal, trap, treasure, secret-wall opening).
- Methods: `move(state, direction) → state`, `applyTileEffect(state, tile) → state`.
- Knows nothing about encounters. When the player lands on a tile that holds an encounter, it returns a state with `pendingActivationTile` set; the orchestrator wires that into the encounter service.
- For trap damage and heal application, it calls into `DungeonDamage` (see below) — both services share that helper so shield-first damage and HP-cap-respecting heals are consistent regardless of source.

**`DungeonEncounterService`** *(new)*
- Owns encounter lifecycle: activation, answering, gauntlet sequencing, boss progression.
- Owns streak counter and shield-grant rules (the *earning* side of shields).
- Methods: `activateAt(state, position) → state`, `answerFlashcard(state, gotIt) → state`, `answerQuiz(state, options) → state`.
- Calls `DungeonDamage.takeDamage(...)` for wrong-answer HP loss and `DungeonDamage.heal(...)` for elite full-heal.
- Does NOT call navigation; the orchestrator routes calls.

**`DungeonDamage`** *(new, small static helper)*
- Pure functions over `DungeonSessionState`. No injected dependencies.
- `takeDamage(state, amount) → state` — single chokepoint: consumes shields first, then HP. Increments `shieldsUsed` stat when a shield absorbs.
- `heal(state, amount) → state` — adds HP capped at `STARTING_HEALTH_CAP`.
- Lives here (not on the state record) so the state record stays a passive data class. Both navigation and encounter services call it; nothing calls back the other way.

**`DungeonSessionService`** *(existing file, slimmed)*
- Orchestrator only: `createFlashcardDungeon`, `createAiQuizDungeon`, `buildStats`.
- `move(state, direction)` → navigation.move → (if landed on encounter/elite/boss tile) encounter.activateAt.
- `answerFlashcard` / `answerQuiz` → encounter service, then re-evaluate win/defeat.
- Owns the final win/defeat detection and the assembly of the returned state.

**Dependency direction**: Session → {Navigation, Encounter}; both → MapGenerator only at create time; both → `DungeonDamage`. Navigation and Encounter do NOT depend on each other.

**Controller is unchanged.** All controller methods still call `dungeonSessionService.move(...)` etc. — the split is internal.

## State model

**New fields on `DungeonSessionState`:**

```
int streak                      // consecutive correct answers, resets on wrong
int shields                     // 0..MAX_SHIELDS, absorbs next HP loss
List<String> gauntletQueue      // remaining encounter ids in active elite gauntlet
```

`gauntletQueue` lives on session state (not on the encounter) because the elite is a *sequence* of encounters, not a single one. When the player steps on an ELITE tile, the first id in the gauntlet group is activated and the rest are queued. After each correct answer, the next id is activated. Any wrong answer aborts the gauntlet (HP loss via shield-first chokepoint, gauntlet cleared, tile stays ELITE so the player can retry).

**New tile type:** `ELITE` (added to `DungeonTileType`). Walkable. `encounterId` points to the *first* encounter of its gauntlet.

**New field on `DungeonMap`:**

```
Map<String, List<String>> gauntletGroups
```

Keyed by the gauntlet's first encounter id; value is the full ordered list of encounter ids in that gauntlet. Looked up by the encounter service when an ELITE tile is activated.

**No new fields on `DungeonEncounter`.** Elite encounters reuse existing `FLASHCARD` / `QUIZ` types. The "this is part of an elite gauntlet" property is conveyed by being in a `gauntletGroups` entry, not by a flag on the encounter. Keeps encounters fungible.

**`DungeonRunStats` additions:** `int longestStreak`, `int elitesCleared`, `int shieldsUsed`.

**Saved-session compatibility:** `DungeonSessionState` is serialized via Java serialization into `SavedSessionService`. Adding fields to the record breaks deserialization of in-flight saved runs.

**Decision:** Discard any pre-existing saved dungeon runs on first load after deploy. Wrap the `loadDungeon` call in a try/catch on `InvalidClassException` (and on any `ObjectStreamException` subclass) and return `Optional.empty()`. Show a one-time flash message: "Your saved dungeon was from an older version and could not be resumed." No migration code; the loss is bounded to users mid-run during deploy.

## Map generation algorithm

**Inputs:** `DungeonSize`, list of normal encounter ids, list of elite gauntlet groups (each group = ordered list of 2 or 3 encounter ids).

**Grid sizes** (unchanged): SMALL 9×9, MEDIUM 11×11, LARGE 13×13.

**Algorithm — uses a seeded `Random` so runs are reproducible for tests:**

1. **Place rooms.** Target room count: SMALL 4, MEDIUM 6, LARGE 8. Each room is a random 2×2 to 4×3 rectangle. Reject placements that overlap or touch an existing room (1-tile gap minimum). Up to 50 placement attempts per room. If we cannot place the target count, accept fewer down to a minimum of 3; below that, regenerate with a new seed.

2. **Connect rooms with corridors.** Build a minimum spanning tree across room centers using Manhattan distance. For each MST edge, carve an L-shaped corridor (horizontal then vertical, or vertical then horizontal, picked randomly) between the two room centers. Then add 1-2 extra corridors between nearby non-MST room pairs to create *loops* — the source of branching choices.

3. **Pick entrance and boss rooms.** Choose the two rooms whose centers have the greatest BFS distance on the carved floor. Entrance tile placed at one room's center; boss tile placed at the other room's center.

4. **Place elite side-rooms.** For each elite gauntlet group: pick a leaf room (only one corridor connection) that isn't the entrance or boss room. Mark the corridor entry tile leading into that room as `ELITE` and store the gauntlet's first encounter id there. Register the full group in `DungeonMap.gauntletGroups`. If too few leaf rooms exist, attach an elite to any non-critical-path room.

5. **Place normal encounters.** Walk the BFS path from entrance to boss; distribute encounters into non-elite, non-entrance, non-boss rooms by *spatial spacing* (not iteration order). Bias toward 1-2 encounters per room rather than clustering all in one. Boss-prompt encounters are NOT placed on the map; they're consumed when the player steps on the BOSS tile (current behavior).

6. **Place rest tiles in remaining room cells.** Counts scaled by size (per Balance table below). HEAL placed roughly midway from entrance to boss. Trap placement avoids the entrance room and the boss room. Each trap tile carries the existing `revealed`/`explored` flags; the client uses these to render telegraphing.

7. **Secret compartments.** Keep current secret-wall logic but restrict placement to walls touching rooms (not corridor walls). Feels like "hidden doors in a chamber" rather than "random wall in a hallway."

8. **Reveal around entrance.** Unchanged.

**Validation guards.** If the carved floor is disconnected (shouldn't happen with MST, but defensive) or if we cannot place all encounters + elites + rest tiles, regenerate with a new seed. Cap regeneration attempts at 10. Past that, throw `IllegalStateException` — would only fire on a future tuning bug.

## Encounter flow

**Activation** — `DungeonEncounterService.activateAt(state, position)`:
- Tile `ENCOUNTER`: activate that encounter (current behavior).
- Tile `ELITE`: look up the gauntlet group via `map.gauntletGroups`, activate the first encounter, set `gauntletQueue` to the remaining ids.
- Tile `BOSS`: activate boss encounter (current behavior).

**Answer path.** Both `answerFlashcard` and `answerQuiz` funnel into a single private `processAnswer(state, encounter, correct, answerData)`:

1. Mark encounter as `CLEARED` (or leave as `ACTIVE` and abort, if part of a gauntlet that just failed — see below).
2. Update streak/shields:
   - if correct: `streak++`; if `streak % STREAK_FOR_SHIELD == 0` and `shields < MAX_SHIELDS`: `shields++`
   - if wrong: `streak = 0`; `takeDamage(state, WRONG_ANSWER_DAMAGE)`
3. Resolve what comes next based on context:
   - Boss gauntlet (`encounter.boss == true`): advance `bossIndex` or set `won = true`.
   - Elite gauntlet (`gauntletQueue` non-empty): advance queue or finish elite.
   - Normal encounter: end encounter, clear tile.

**`DungeonDamage.takeDamage(state, amount)`** — single chokepoint for all HP loss (wrong answer OR trap). Lives in the shared `DungeonDamage` helper described in the Architecture section.

```
if shields > 0:
    shields--
    record shieldsUsed++ in stats
    return state unchanged in HP
else:
    health -= amount
    if health <= 0: defeated = true
```

Shields absorb trap damage too. Traps still consume a shield rather than HP. Visible feedback (shield icon disappears) makes the loss legible.

`DungeonDamage.heal(state, amount)` adds HP capped at `STARTING_HEALTH_CAP` and is used by the navigation service for HEAL tiles and by the encounter service for the elite full-heal reward.

**Gauntlet sequencing** (elite rooms):
- Correct + queue non-empty → activate next id in queue, drop it from queue.
- Correct + queue empty → elite fully cleared. Tile becomes `FLOOR`. Player receives **full heal + 1 shield charge** (capped at `MAX_SHIELDS`). `elitesCleared++`.
- Wrong answer at any point → `takeDamage` (shield absorbs if available). Abort gauntlet: clear `gauntletQueue` and `activeEncounterId`. Tile stays `ELITE` so the player can return and retry. Re-attempting re-activates the SAME encounter ids (no fresh draws — keeps card-pool bookkeeping simple).

**Boss flow** — minor change only: damage routes through `takeDamage`, so shields earned earlier can absorb boss-prompt mistakes. Otherwise unchanged.

**Movement-while-encounter-active** — unchanged rule: any `activeEncounterId != null` blocks movement. Player commits once an encounter starts; only way out is to finish it (or, for gauntlets, fail and abort).

**Edge case**: a wrong answer on a gauntlet question that would otherwise defeat the player but they have a shield → shield absorbs, player survives at low HP, gauntlet still aborts. Shield protects from death but doesn't rescue the gauntlet.

## Balance numbers

| Knob | SMALL | MEDIUM | LARGE |
|---|---|---|---|
| Grid | 9×9 | 11×11 | 13×13 |
| Rooms (target) | 4 | 6 | 8 |
| Normal encounters | 6 | 9 | 15 |
| Boss prompts | 2 | 3 | 5 |
| Elite gauntlets | 1 | 2 | 3 |
| Cards per elite gauntlet | 2 | 2 | 3 |
| Traps | 1 | 2 | 3 |
| Heals on map | 1 | 1 | 1 |
| Treasures on map | 1 | 1 | 1 |
| Secret compartments | 1 | 2 | 3 |
| **Total cards required** | **10** | **16** | **29** |

Total cards required = `normalEncounters + bossPrompts + (eliteGauntlets × cardsPerGauntlet)`.

`DungeonSize.totalPrompts()` and `isAvailableFor(int)` update to these new totals. The wizard's size-availability check uses this directly, so users with smaller decks see fewer size options.

**Constants** (in `DungeonEncounterService`):
- `STARTING_HEALTH = 5`
- `STARTING_HEALTH_CAP = 5` (no max-HP increases from elites)
- `STREAK_FOR_SHIELD = 3`
- `MAX_SHIELDS = 2`
- `ELITE_HEAL` = sets `health` back to `STARTING_HEALTH_CAP`
- `TRAP_DAMAGE = 1`
- `WRONG_ANSWER_DAMAGE = 1`
- `TREASURE_SCORE = 50`
- `SECRET_WALL_SCORE = 25`

## UI impact

The encounter rendering currently runs entirely in-canvas via `renderJRPGCombat()` in `dungeon.js`. The HTML side-panel template content (`.sh-dungeon-encounter-panel` and friends in `dungeon-game.html`) is hidden via `display: none !important` in `dungeon.css` and is effectively dead code. This spec **removes** that dead markup as part of the cleanup, since we're already in the area.

**HUD additions** (real HTML, top of `dungeon-game.html`):
- `Shields` HUD item — 0-2 shield icons rendered via Iconify (`lucide:shield`). Hidden when 0. Fade-in when earned, fade-out when consumed.
- `Streak` HUD item — `×N`. Hidden when 0. Subtle pulse animation when N is a multiple of `STREAK_FOR_SHIELD` (signals a shield is about to bank).
- On narrow viewports the HUD collapses to two rows. Acceptable.

**Canvas combat-screen additions** (in `renderJRPGCombat`):
- **Elite gauntlet progress badge**: small text in the combat panel header showing `Elite N/Total`, mirroring the existing boss indicator pattern.
- **Splash screen**: extend to recognize the elite case. Show a brief "Elite Challenge!" splash with a distinct color tint. Use existing splash machinery (`dungeonSplashActive`, `dungeonSplashBoss` → add `dungeonSplashElite`).
- **Elite monster sprite**: when the active encounter is part of a gauntlet, render a distinct monster (e.g. knight/champion) instead of the standard mob or dragon.
- **Shield consumption feedback**: when `shields` decremented this turn (diff against `window.lastShields`), play a brief shield-shatter particle effect over the player sprite plus a new `DungeonAudio.playShieldBreak()` sound. Mirrors the existing `slashParticleActive` pattern.
- **Streak milestone feedback**: when streak hits a multiple of `STREAK_FOR_SHIELD`, brief golden flash plus `DungeonAudio.playShieldGain()`.

**Canvas map-screen additions** (in `renderAll`):
- **ELITE tile sprite**: new sprite registered in `drawPixelSprite` (e.g. crossed-swords glyph). Tinted background distinguishes it from regular ENCOUNTER tiles.
- **Telegraphed trap sprite**: new "cracked floor" variant. Activated when `tile.revealed == true && tile.explored == false && tile.type === 'TRAP'`. Backend already sends `revealed` and `explored`; the only new client logic is rendering a different sprite for that combination.

**Server-side data plumbing for the canvas additions:**
- Expose on the active encounter (in the model attributes used by `dungeon-game.html`): `gauntletPosition` (1-indexed current step in gauntlet, or 0) and `gauntletTotal` (gauntlet length, or 0). Read by JS via new `data-gauntlet-position` / `data-gauntlet-total` attributes on the canvas, alongside existing `data-active-encounter-boss`.

**Dungeon-complete page** (`dungeon-complete.html`):
- Below the existing prompts-correct count, show: longest streak, elites cleared, shields used. Three small stat lines, same styling as the existing count.

**i18n keys to add** (in `messages*.properties`):
- `dungeon.shields` — "Shields"
- `dungeon.streak` — "Streak"
- `dungeon.elite-progress` — "Elite {0}/{1}"
- `dungeon.elite-aborted` — "Elite challenge failed — try again"
- `dungeon.complete.longest-streak` — "Longest streak"
- `dungeon.complete.elites-cleared` — "Elites cleared"
- `dungeon.complete.shields-used` — "Shields used"

## Study wizard restructure

The current wizard flow lets the user configure a LARGE dungeon and discover only at submit that their sources can't support it. The restructure makes source selection the first step, then gates mode and size choices on the live source size.

**Step renumbering** in `study-setup.html` and the four mode-specific panels:
- `wizard-source-picker.html`: `data-step` moves from `3` to `1`. Source picker becomes the first thing the user sees.
- Mode picker (in `study-setup.html`): `data-step` moves from `1` to `2`.
- Each mode-specific fragment (`wizard-flashcards`, `wizard-quiz`, `wizard-exam`, `wizard-dungeon`) shifts its internal `data-step` values up by 1.

**Live availability feedback at step 2 (mode picker):**

Once at least one source is selected at step 1, the wizard computes the total card count (and, for AI modes, the source character total which already exists as `selectionTotalChars`). The mode picker at step 2 then:
- Greys out any mode whose minimum requirement isn't met.
- Shows a small inline reason under each greyed mode: `Needs at least 10 flashcards — your selection has 6.`
- The "Next" button is enabled only when an enabled mode is selected.

Today only Dungeon has a hard minimum (10 cards for SMALL). Flashcards/Quiz/Exam have minimums of 1 or 0. Add `StudyMode.minimumCardsRequired()` returning these constants so the gating is data-driven and trivially extended.

**Live availability feedback at the dungeon size panel:**

When the user lands on the dungeon size panel (now `data-step="3"` for Dungeon), the wizard checks the live source size and disables size options that don't fit. Same pattern: greyed card plus inline reason ("Needs 16 cards, your selection has 12"). Reactive — if the user navigates back to step 1 and changes sources, the available sizes recompute.

**Where this logic lives:**
- Server: `DungeonSize.availableForUsableItems(int)` already exists. Reuse it.
- Server: add `StudyMode.minimumCardsRequired()` constants.
- Client (`study-wizard.js`): when the source-picker selection changes, recompute and update which mode cards and which size cards are enabled. The card count is already tracked on the source picker for `selectionTotalChars`; extend the same listeners to publish a card count.

**What stays the same:**
- HTMX-driven submit flow.
- Saved-session conflict modal still fires before any step is shown.
- AI quiz/exam settings (question mode, difficulty, instructions) remain on their existing steps within mode-specific panels.
- Visual design and step-indicator pill component.

**What this affects beyond Dungeon:**
- All four wizard fragments need `data-step` shifted up by 1.
- The step-indicator labels (rendered by JS into `#sh-wizard-steps`) need updating to reflect the new order.
- Existing tests for `StudyController` and the dungeon controller need their step expectations updated. Mechanical.

**i18n additions:**
- `wizard.mode-unavailable.cards` — `Needs at least {0} cards — your selection has {1}.`
- `wizard.size-unavailable.cards` — `Needs {0} cards, your selection has {1}.`

**Scope guard:** this section restricts itself to step-order and availability gating. It does NOT redesign the source picker UI, change the saved-session flow, or rework the AI-generation modal.

## Testing strategy

The decomposition is what makes this tractable. Each service gets its own focused tests:

- **`DungeonMapGeneratorTests`** (exists, gets expanded): seed the generator with fixed seeds and assert exact layouts. Add cases for: room count, MST connectivity (all rooms reachable from entrance), elite placement on leaf rooms only, encounter spatial distribution, trap-not-in-entrance-or-boss-room, secret compartments attached to rooms.
- **`DungeonNavigationServiceTests`** (new): construct a minimal map fixture; assert movement, walkability, fog reveal, tile-effect application (heal, trap, treasure, secret-wall opening). No encounters involved.
- **`DungeonEncounterServiceTests`** (new): construct fixtures with a player on an encounter/elite/boss; assert streak/shield updates, gauntlet sequencing, abort-on-wrong, full-heal-on-elite-clear.
- **`DungeonDamageTests`** (new): pure-function tests for `takeDamage` (shield absorbs, shields exhaust to HP, defeated at 0) and `heal` (caps at `STARTING_HEALTH_CAP`). Shared by navigation and encounter behaviour.
- **`DungeonSessionServiceTests`** (exists, slimmed): cover orchestration and lifecycle only. The detailed mechanics tests move into the per-service files above.
- **`DungeonSessionStateTests`** (exists): expand for new fields.

Existing controller and integration tests are unaffected by the split since the public surface of `DungeonSessionService` is preserved.

## Migration plan / sequencing

Even though the user opted for a full overhaul in one spec, the implementation lands as a sequence of independent commits to keep each step reviewable:

1. **Service split (mechanical refactor)** — extract `DungeonNavigationService`, `DungeonEncounterService`, and `DungeonDamage` from `DungeonSessionService` with no logic changes. Route existing HP-loss paths through `DungeonDamage.takeDamage` (still amount-only, no shields yet). Existing tests should still pass with adjusted imports.
2. **State model additions** — add `streak`, `shields`, `gauntletQueue` to `DungeonSessionState`; add `gauntletGroups` to `DungeonMap`; add `ELITE` to `DungeonTileType`; add `longestStreak`/`elitesCleared`/`shieldsUsed` to `DungeonRunStats`. Wire safe defaults so existing flow keeps working.
3. **Saved-session compatibility guard** — try/catch `ObjectStreamException` on load, return empty + flash message.
4. **Map generator rewrite** — replace `DungeonMapGenerator` with the rooms+corridors algorithm. Update `DungeonMapGeneratorTests` with new seeded assertions. No elite logic yet (gauntlet groups list stays empty).
5. **Balance number update** — update `DungeonSize` constants to new totals.
6. **Encounter service mechanics** — implement streak, shields, `takeDamage` chokepoint. Boss flow now uses `takeDamage`.
7. **Elite gauntlet support** — encounter service activates ELITE tiles, sequences gauntlet, awards heal+shield, aborts on wrong. Generator places elites.
8. **Trap telegraphing** — client renders cracked-floor variant when trap is revealed but not explored.
9. **HUD additions** — Shields and Streak HUD items in `dungeon-game.html`.
10. **Canvas combat additions** — elite progress badge, elite splash, elite monster sprite, shield-break particle, streak-flash particle, audio.
11. **Dungeon-complete additions** — three new stat lines.
12. **Dead template cleanup** — remove the hidden `.sh-dungeon-encounter-panel` markup.
13. **Wizard restructure** — step renumbering, `StudyMode.minimumCardsRequired()`, mode-picker gating, size-picker gating, i18n strings.

Each numbered step is a coherent commit. Steps 1-3 are pure scaffolding (no user-visible change). Steps 4-7 are the gameplay overhaul. Steps 8-12 are UI. Step 13 is the wizard change.

## Out of scope

Explicitly NOT in this spec:
- Boss redesign (phases, time pressure, reflect mechanic).
- Defeat-softening (continuing the underlying study session after a dungeon defeat).
- Score-as-currency (locked doors, shop, score has no in-run use beyond display).
- Per-deck dungeon history, leaderboards, daily seeds, meta-progression.
- Source picker UI redesign (only its step position changes).
- AI-generation modal changes.
- Any change to non-dungeon study modes' configuration steps beyond the `data-step` renumber.

These remain candidates for follow-up specs if the overhauled mode proves worth investing further.
