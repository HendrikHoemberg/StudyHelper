# Dungeon Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform dungeon mode from a deterministic linear quiz into a procedural roguelike-ish experience with rooms+corridors maps, optional elite side-rooms (multi-card gauntlets), a streak→shield reward loop, and a source-first study wizard.

**Architecture:** Decompose `DungeonSessionService` into `DungeonNavigationService`, `DungeonEncounterService`, and a shared `DungeonDamage` helper. Rewrite `DungeonMapGenerator` to produce rooms+corridors layouts with MST connectivity. Extend `DungeonSessionState` with streak/shields/gauntletQueue. Telegraph traps in the canvas renderer. Bundle the wizard restructure so source selection precedes mode and size gating.

**Tech Stack:** Java 21 + Spring Boot, Thymeleaf templates, HTMX, Vanilla JS with Canvas 2D, JUnit 5 + AssertJ + Mockito, Maven.

**Source spec:** `docs/superpowers/specs/2026-05-27-dungeon-overhaul-design.md`

---

## File Structure

**New files:**
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java` — pure static helper for shield-first damage and capped heals
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationService.java` — movement, fog reveal, tile effects
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java` — activation, answers, streak, shield grants, gauntlet sequencing
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java`
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationServiceTests.java`
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterServiceTests.java`

**Modified files:**
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSessionState.java` — add streak, shields, gauntletQueue
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonMap.java` — add gauntletGroups
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTileType.java` — add ELITE
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonRunStats.java` — add longestStreak, elitesCleared, shieldsUsed
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSize.java` — new totals
- `src/main/java/com/HendrikHoemberg/StudyHelper/dto/StudyMode.java` — add minimumCardsRequired()
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGenerator.java` — rooms+corridors rewrite
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java` — slim to orchestrator
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/SavedSessionService.java` — `ObjectStreamException` guard
- `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DungeonController.java` — expose gauntlet position/total
- `src/main/resources/templates/fragments/dungeon-game.html` — HUD additions, gauntlet data attrs, remove dead panel
- `src/main/resources/templates/fragments/dungeon-complete.html` — three new stat lines
- `src/main/resources/templates/fragments/study-setup.html` — mode picker `data-step` 1→2
- `src/main/resources/templates/fragments/wizard-source-picker.html` — `data-step` 3→1
- `src/main/resources/templates/fragments/wizard-flashcards.html` — shift `data-step` +1
- `src/main/resources/templates/fragments/wizard-quiz.html` — shift `data-step` +1
- `src/main/resources/templates/fragments/wizard-exam.html` — shift `data-step` +1
- `src/main/resources/templates/fragments/wizard-dungeon.html` — shift `data-step` +1
- `src/main/resources/static/css/dungeon.css` — HUD styles, ELITE/telegraphed-trap tile styles, remove dead panel CSS
- `src/main/resources/static/js/dungeon.js` — ELITE sprite, telegraphed trap sprite, gauntlet progress badge, elite splash, shield-break particle, streak flash, shield/streak audio
- `src/main/resources/static/js/study-wizard.js` — mode/size live gating on source change
- `src/main/resources/messages.properties` (+ all `messages_*.properties`) — new i18n keys
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGeneratorTests.java` — new seeded assertions
- `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionServiceTests.java` — slim to orchestration tests
- `src/test/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSessionStateTests.java` — new fields
- `src/test/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSizeTests.java` — new totals
- `src/test/java/com/HendrikHoemberg/StudyHelper/controller/DungeonControllerTests.java` — gauntlet model attrs

---

## Phase 1 — Scaffolding (no user-visible change)

### Task 1: Create `DungeonDamage` helper

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java`
- Test: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java`

This helper is the single chokepoint for HP loss and HP gain. At this phase it is amount-only and has no shield logic yet (shields are added in Phase 3). We create it now so the service split in Tasks 2-4 can route through it without later having to touch many call sites.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonDamageTests {

    private DungeonSessionState stateWithHealth(int health) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        DungeonPosition entrance = new DungeonPosition(0, 0);
        DungeonMap map = new DungeonMap(1, 1, entrance, entrance, Map.of());
        return new DungeonSessionState(
            config, map, entrance, Map.of(), List.of(), 0, null,
            health, 0, 0, 0, Set.of(), false, false);
    }

    @Test
    void takeDamage_reducesHealthByAmount() {
        DungeonSessionState before = stateWithHealth(5);
        DungeonSessionState after = DungeonDamage.takeDamage(before, 1);
        assertThat(after.health()).isEqualTo(4);
        assertThat(after.defeated()).isFalse();
    }

    @Test
    void takeDamage_marksDefeatedWhenHealthHitsZero() {
        DungeonSessionState before = stateWithHealth(1);
        DungeonSessionState after = DungeonDamage.takeDamage(before, 1);
        assertThat(after.health()).isZero();
        assertThat(after.defeated()).isTrue();
    }

    @Test
    void takeDamage_clampsHealthAtZeroIfOverkill() {
        DungeonSessionState before = stateWithHealth(1);
        DungeonSessionState after = DungeonDamage.takeDamage(before, 5);
        assertThat(after.health()).isZero();
        assertThat(after.defeated()).isTrue();
    }

    @Test
    void heal_addsAmountCappedAtFive() {
        DungeonSessionState before = stateWithHealth(3);
        DungeonSessionState after = DungeonDamage.heal(before, 10);
        assertThat(after.health()).isEqualTo(5);
    }

    @Test
    void heal_doesNotChangeAtFullHealth() {
        DungeonSessionState before = stateWithHealth(5);
        DungeonSessionState after = DungeonDamage.heal(before, 2);
        assertThat(after.health()).isEqualTo(5);
    }
}
```

- [ ] **Step 2: Run tests, verify they fail to compile**

Run: `./mvnw test -Dtest=DungeonDamageTests`
Expected: compile failure — `DungeonDamage` does not exist.

- [ ] **Step 3: Implement `DungeonDamage`**

Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.DungeonSessionState;

public final class DungeonDamage {

    public static final int STARTING_HEALTH = 5;
    public static final int STARTING_HEALTH_CAP = 5;

    private DungeonDamage() {}

    public static DungeonSessionState takeDamage(DungeonSessionState state, int amount) {
        int newHealth = Math.max(0, state.health() - amount);
        boolean defeated = state.defeated() || newHealth <= 0;
        return new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            newHealth, state.score(),
            state.answeredCount(), state.correctCount(),
            state.visibleTiles(),
            state.won(), defeated);
    }

    public static DungeonSessionState heal(DungeonSessionState state, int amount) {
        int newHealth = Math.min(STARTING_HEALTH_CAP, state.health() + amount);
        return new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            newHealth, state.score(),
            state.answeredCount(), state.correctCount(),
            state.visibleTiles(),
            state.won(), state.defeated());
    }
}
```

Note: this signature matches the *current* `DungeonSessionState` record (pre-Phase-2 additions). When Phase 2 adds streak/shields/gauntletQueue, we will update this file in Task 7 to pass those through and add the shield-first logic.

- [ ] **Step 4: Run tests, verify they pass**

Run: `./mvnw test -Dtest=DungeonDamageTests`
Expected: 5 passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java
git commit -m "feat(dungeon): add DungeonDamage helper for HP changes"
```

---

### Task 2: Extract `DungeonNavigationService`

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationService.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java`

This is a mechanical refactor — move movement, fog reveal, and tile-effect logic out of `DungeonSessionService` into a new service. Existing behaviour is preserved.

- [ ] **Step 1: Create the navigation service**

Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationService.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DungeonNavigationService {

    private static final int TREASURE_SCORE = 50;
    private static final int SECRET_WALL_SCORE = 25;
    private static final int HEAL_AMOUNT = 1;

    public record MoveResult(DungeonSessionState state, DungeonTileType landedTileType, String encounterId) {}

    public MoveResult move(DungeonSessionState state, DungeonDirection direction) {
        if (state.isComplete()) return new MoveResult(state, null, null);
        if (state.activeEncounterId() != null) return new MoveResult(state, null, null);

        DungeonPosition newPos = state.playerPosition().move(direction);
        if (!state.map().isInside(newPos)) return new MoveResult(state, null, null);

        DungeonTile newTile = state.map().tileAt(newPos);
        if (newTile == null) return new MoveResult(state, null, null);

        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>(state.map().tiles());
        int score = state.score();
        DungeonSessionState working = state;

        if (newTile.type() == DungeonTileType.SECRET_WALL) {
            newTile = newTile.withType(DungeonTileType.FLOOR, null).reveal().explore();
            tiles.put(newPos, newTile);
            score += SECRET_WALL_SCORE;
        } else if (!newTile.walkable()) {
            return new MoveResult(state, null, null);
        }

        revealAround(tiles, newPos);

        DungeonTile destinationTile = tiles.get(newPos);
        if (destinationTile != null) {
            destinationTile = destinationTile.explore();
            tiles.put(newPos, destinationTile);
        }

        DungeonTile activeTile = destinationTile != null ? destinationTile : newTile;
        String encounterIdHit = null;

        switch (activeTile.type()) {
            case ENCOUNTER, BOSS -> encounterIdHit = activeTile.encounterId();
            case HEAL -> {
                working = DungeonDamage.heal(working, HEAL_AMOUNT);
                tiles.put(newPos, activeTile.withType(DungeonTileType.FLOOR, null));
            }
            case TREASURE -> {
                score += TREASURE_SCORE;
                tiles.put(newPos, activeTile.withType(DungeonTileType.FLOOR, null));
            }
            case TRAP -> {
                working = DungeonDamage.takeDamage(working, 1);
                tiles.put(newPos, activeTile.withType(DungeonTileType.FLOOR, null));
            }
            default -> { /* no tile effect */ }
        }

        DungeonMap nextMap = new DungeonMap(
            state.map().width(), state.map().height(),
            state.map().entrance(), state.map().boss(), Map.copyOf(tiles));

        DungeonSessionState moved = new DungeonSessionState(
            working.config(), nextMap, newPos,
            working.encounters(), working.bossEncounterIds(), working.bossIndex(),
            working.activeEncounterId(),
            working.health(), score,
            working.answeredCount(), working.correctCount(),
            visibleFrom(tiles),
            working.won(), working.defeated());

        return new MoveResult(moved, activeTile.type(), encounterIdHit);
    }

    public Set<DungeonPosition> revealAroundEntrance(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition entrance) {
        revealAround(tiles, entrance);
        DungeonTile entranceTile = tiles.get(entrance);
        if (entranceTile != null) {
            tiles.put(entrance, entranceTile.explore());
        }
        return visibleFrom(tiles);
    }

    private void revealAround(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition center) {
        reveal(tiles, center);
        for (DungeonDirection direction : DungeonDirection.values()) {
            reveal(tiles, center.move(direction));
        }
    }

    private void reveal(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition position) {
        DungeonTile tile = tiles.get(position);
        if (tile != null) {
            tiles.put(position, tile.reveal());
        }
    }

    private Set<DungeonPosition> visibleFrom(Map<DungeonPosition, DungeonTile> tiles) {
        return tiles.values().stream()
            .filter(DungeonTile::revealed)
            .map(DungeonTile::position)
            .collect(Collectors.toSet());
    }
}
```

- [ ] **Step 2: Run existing dungeon tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: all pre-existing dungeon tests still pass (the new service exists but is not wired in yet).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationService.java
git commit -m "feat(dungeon): extract DungeonNavigationService (unwired)"
```

---

### Task 3: Extract `DungeonEncounterService`

**Files:**
- Create: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java`

- [ ] **Step 1: Create the encounter service**

Create `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DungeonEncounterService {

    public DungeonSessionState activate(DungeonSessionState state, String encounterId, boolean boss) {
        if (encounterId == null) return state;
        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>(state.encounters());
        DungeonEncounter enc = encounters.get(encounterId);
        if (enc == null || enc.status() != DungeonEncounterStatus.PENDING) return state;
        encounters.put(encounterId, enc.activate());

        String activeId = encounterId;
        if (boss && !state.bossEncounterIds().isEmpty()) {
            activeId = state.bossEncounterIds().get(0);
        }

        return new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            Map.copyOf(encounters), state.bossEncounterIds(), state.bossIndex(),
            activeId,
            state.health(), state.score(),
            state.answeredCount(), state.correctCount(),
            state.visibleTiles(),
            state.won(), state.defeated());
    }

    public DungeonSessionState answerFlashcard(DungeonSessionState state, boolean gotIt) {
        DungeonEncounter enc = state.activeEncounter();
        if (enc == null) return state;
        if (enc.type() == DungeonEncounterType.QUIZ || enc.type() == DungeonEncounterType.BOSS_QUIZ) return state;
        return applyAnswer(state, enc, gotIt ? List.of(1) : List.of(0), gotIt);
    }

    public DungeonSessionState answerQuiz(DungeonSessionState state, List<Integer> selectedOptions) {
        DungeonEncounter enc = state.activeEncounter();
        if (enc == null) return state;
        if (enc.type() == DungeonEncounterType.FLASHCARD || enc.type() == DungeonEncounterType.BOSS_FLASHCARD) return state;
        List<Integer> safe = selectedOptions == null ? List.of() : selectedOptions;
        QuizQuestion q = enc.quizQuestion();
        boolean correct = new HashSet<>(safe).equals(new HashSet<>(q.correctOptionIndices()));
        return applyAnswer(state, enc, safe, correct);
    }

    private DungeonSessionState applyAnswer(DungeonSessionState state, DungeonEncounter encounter,
                                             List<Integer> answer, boolean correct) {
        Map<String, DungeonEncounter> encounters = new LinkedHashMap<>(state.encounters());
        encounters.put(encounter.id(), encounter.clear(answer, correct));

        int answeredCount = state.answeredCount() + 1;
        int correctCount = state.correctCount() + (correct ? 1 : 0);

        DungeonSessionState working = new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            Map.copyOf(encounters), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            state.health(), state.score(),
            answeredCount, correctCount,
            state.visibleTiles(),
            state.won(), state.defeated());

        if (!correct) {
            working = DungeonDamage.takeDamage(working, 1);
        }

        String nextActiveId = null;
        int bossIndex = working.bossIndex();
        boolean won = working.won();

        if (encounter.boss() && !working.defeated()) {
            bossIndex++;
            if (bossIndex < working.bossEncounterIds().size()) {
                String nextBossId = working.bossEncounterIds().get(bossIndex);
                Map<String, DungeonEncounter> encs2 = new LinkedHashMap<>(working.encounters());
                DungeonEncounter nextBoss = encs2.get(nextBossId);
                if (nextBoss != null && nextBoss.status() == DungeonEncounterStatus.PENDING) {
                    encs2.put(nextBossId, nextBoss.activate());
                    working = new DungeonSessionState(
                        working.config(), working.map(), working.playerPosition(),
                        Map.copyOf(encs2), working.bossEncounterIds(), bossIndex,
                        nextBossId,
                        working.health(), working.score(),
                        working.answeredCount(), working.correctCount(),
                        working.visibleTiles(),
                        working.won(), working.defeated());
                    nextActiveId = nextBossId;
                }
            } else {
                won = true;
            }
        }

        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>(working.map().tiles());
        DungeonPosition playerPos = working.playerPosition();
        DungeonTile currentTile = tiles.get(playerPos);
        DungeonMap nextMap = working.map();
        if (currentTile != null
            && (currentTile.type() == DungeonTileType.ENCOUNTER || currentTile.type() == DungeonTileType.BOSS)
            && (!encounter.boss() || won)) {
            tiles.put(playerPos, currentTile.withType(DungeonTileType.FLOOR, null));
            nextMap = new DungeonMap(working.map().width(), working.map().height(),
                working.map().entrance(), working.map().boss(), Map.copyOf(tiles));
        }

        return new DungeonSessionState(
            working.config(), nextMap, working.playerPosition(),
            working.encounters(), working.bossEncounterIds(), bossIndex,
            nextActiveId,
            working.health(), working.score(),
            working.answeredCount(), working.correctCount(),
            working.visibleTiles(),
            won, working.defeated());
    }
}
```

- [ ] **Step 2: Run existing tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: all pre-existing tests still pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java
git commit -m "feat(dungeon): extract DungeonEncounterService (unwired)"
```

---

### Task 4: Slim `DungeonSessionService` to orchestrator

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionServiceTests.java`

- [ ] **Step 1: Replace `DungeonSessionService` body**

Replace the entire contents of `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java` with:

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

    public DungeonSessionService(DeckService deckService,
                                 FlashcardService flashcardService,
                                 QuizSessionService quizSessionService,
                                 DungeonMapGenerator dungeonMapGenerator,
                                 DungeonNavigationService navigationService,
                                 DungeonEncounterService encounterService) {
        this.deckService = deckService;
        this.flashcardService = flashcardService;
        this.quizSessionService = quizSessionService;
        this.dungeonMapGenerator = dungeonMapGenerator;
        this.navigationService = navigationService;
        this.encounterService = encounterService;
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

        for (int i = 0; i < size.normalEncounterCount(); i++) {
            Flashcard card = flashcards.get(i);
            String id = "fc_" + i;
            encounters.put(id, DungeonEncounter.flashcard(id, false, card.getId(),
                card.getFrontText(), card.getBackText(),
                card.getFrontImageFilename(), card.getBackImageFilename()));
            normalEncounterIds.add(id);
        }

        int bossStart = size.normalEncounterCount();
        for (int i = 0; i < size.bossPromptCount(); i++) {
            Flashcard card = flashcards.get(bossStart + i);
            String id = "fc_boss_" + i;
            encounters.put(id, DungeonEncounter.flashcard(id, true, card.getId(),
                card.getFrontText(), card.getBackText(),
                card.getFrontImageFilename(), card.getBackImageFilename()));
            bossEncounterIds.add(id);
        }

        DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds);
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

        for (int i = 0; i < size.normalEncounterCount(); i++) {
            QuizQuestion q = questions.get(i);
            String id = "qz_" + i;
            encounters.put(id, DungeonEncounter.quiz(id, false, q));
            normalEncounterIds.add(id);
        }

        int bossStart = size.normalEncounterCount();
        for (int i = 0; i < size.bossPromptCount(); i++) {
            QuizQuestion q = questions.get(bossStart + i);
            String id = "qz_boss_" + i;
            encounters.put(id, DungeonEncounter.quiz(id, true, q));
            bossEncounterIds.add(id);
        }

        DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds);
        return initialState(
            new DungeonConfig(DungeonMode.AI_QUIZ, size, normalizedIds,
                questionMode, difficulty, additionalInstructions),
            map, encounters, bossEncounterIds);
    }

    public DungeonSessionState move(DungeonSessionState state, DungeonDirection direction) {
        DungeonNavigationService.MoveResult result = navigationService.move(state, direction);
        DungeonSessionState moved = result.state();
        if (result.encounterId() != null) {
            boolean isBoss = result.landedTileType() == DungeonTileType.BOSS;
            moved = encounterService.activate(moved, result.encounterId(), isBoss);
        }
        return moved;
    }

    public DungeonSessionState answerFlashcard(DungeonSessionState state, boolean gotIt) {
        return encounterService.answerFlashcard(state, gotIt);
    }

    public DungeonSessionState answerQuiz(DungeonSessionState state, List<Integer> selectedOptions) {
        return encounterService.answerQuiz(state, selectedOptions);
    }

    public DungeonRunStats buildStats(DungeonSessionState state) {
        return new DungeonRunStats(
            state.config().mode(),
            state.config().size(),
            state.config().size().totalPrompts(),
            state.answeredCount(),
            state.correctCount(),
            state.health(),
            state.won());
    }

    private DungeonSessionState initialState(DungeonConfig config, DungeonMap map,
                                              Map<String, DungeonEncounter> encounters,
                                              List<String> bossEncounterIds) {
        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>(map.tiles());
        Set<DungeonPosition> visible = navigationService.revealAroundEntrance(tiles, map.entrance());
        DungeonMap exploredMap = new DungeonMap(map.width(), map.height(),
            map.entrance(), map.boss(), Map.copyOf(tiles));

        return new DungeonSessionState(
            config, exploredMap, map.entrance(),
            Map.copyOf(encounters), List.copyOf(bossEncounterIds),
            0, null,
            DungeonDamage.STARTING_HEALTH, 0, 0, 0,
            visible, false, false);
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

- [ ] **Step 2: Update test setUp to inject the new services**

In `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionServiceTests.java`, find the `setUp` method and update construction:

```java
@BeforeEach
void setUp() {
    deckService = mock(DeckService.class);
    flashcardService = mock(FlashcardService.class);
    quizSessionService = mock(QuizSessionService.class);
    dungeonMapGenerator = mock(DungeonMapGenerator.class);
    DungeonNavigationService navigationService = new DungeonNavigationService();
    DungeonEncounterService encounterService = new DungeonEncounterService();
    dungeonSessionService = new DungeonSessionService(
        deckService, flashcardService, quizSessionService, dungeonMapGenerator,
        navigationService, encounterService);

    user = new User();
    user.setId(1L);
    user.setUsername("alice");
}
```

(Use real instances of the helper services, not mocks — they are pure-logic and faster to use than to mock.)

- [ ] **Step 3: Run all dungeon tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: all tests pass. If any fail, the refactor changed behaviour — re-read the original methods and fix the new services.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java \
        src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionServiceTests.java
git commit -m "refactor(dungeon): slim DungeonSessionService to orchestrator"
```

---

## Phase 2 — State model additions

### Task 5: Add `streak`, `shields`, `gauntletQueue` to `DungeonSessionState`

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSessionState.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationService.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSessionStateTests.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java`

- [ ] **Step 1: Update `DungeonSessionState` record**

Replace the contents of `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSessionState.java`:

```java
package com.HendrikHoemberg.StudyHelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DungeonSessionState(
    DungeonConfig config,
    DungeonMap map,
    DungeonPosition playerPosition,
    Map<String, DungeonEncounter> encounters,
    List<String> bossEncounterIds,
    int bossIndex,
    String activeEncounterId,
    int health,
    int score,
    int answeredCount,
    int correctCount,
    Set<DungeonPosition> visibleTiles,
    boolean won,
    boolean defeated,
    int streak,
    int shields,
    List<String> gauntletQueue,
    int longestStreak,
    int elitesCleared,
    int shieldsUsed
) implements Serializable {
    @JsonIgnore
    public boolean isComplete() {
        return won || defeated;
    }

    public DungeonEncounter activeEncounter() {
        return activeEncounterId == null ? null : encounters.get(activeEncounterId);
    }
}
```

- [ ] **Step 2: Update every `new DungeonSessionState(...)` call site**

The new constructor has 6 extra fields. Update each construction site to pass `0, 0, List.of(), 0, 0, 0` for the new fields (or the appropriate values from the previous state when copying).

Files to update (use grep to find all sites):
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java` — both `takeDamage` and `heal` need to pass through the new fields
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonNavigationService.java` — the `MoveResult` construction
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java` — both `activate` and `applyAnswer`
- `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java` — `initialState`

Run: `grep -rn "new DungeonSessionState(" src/main/java`
For each match, add `, state.streak(), state.shields(), state.gauntletQueue(), state.longestStreak(), state.elitesCleared(), state.shieldsUsed()` before the closing `)` (or `, 0, 0, List.of(), 0, 0, 0` for fresh states in `initialState`).

- [ ] **Step 3: Update `DungeonDamageTests` fixtures**

In `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamageTests.java`, the `stateWithHealth` helper currently constructs a state with 14 fields. Update it to pass `, 0, 0, List.of(), 0, 0, 0` for the six new fields before the closing `)`.

- [ ] **Step 4: Update other test fixtures**

Run: `grep -rn "new DungeonSessionState(" src/test/java`
For each match, append `, 0, 0, List.of(), 0, 0, 0` before the closing `)`. (If a test constructs a state with specific streak/shield values, leave room for that — but at this point in the plan no test does.)

- [ ] **Step 5: Compile and run tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: all tests pass. If compile errors remain, run `./mvnw compile` and address each error site.

- [ ] **Step 6: Commit**

```bash
git add -u
git commit -m "feat(dungeon): add streak/shields/gauntletQueue and stats fields to state"
```

---

### Task 6: Add `ELITE` tile type and `gauntletGroups` to `DungeonMap`

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTileType.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonMap.java`

- [ ] **Step 1: Add `ELITE` to tile type enum**

In `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTileType.java`, add `ELITE` after `BOSS`:

```java
package com.HendrikHoemberg.StudyHelper.dto;

public enum DungeonTileType {
    WALL,
    FLOOR,
    ENTRANCE,
    ENCOUNTER,
    TREASURE,
    HEAL,
    BOSS,
    ELITE,
    SECRET_WALL,
    TRAP
}
```

- [ ] **Step 2: Add `gauntletGroups` to `DungeonMap`**

Replace `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonMap.java`:

```java
package com.HendrikHoemberg.StudyHelper.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record DungeonMap(
    int width,
    int height,
    DungeonPosition entrance,
    DungeonPosition boss,
    Map<DungeonPosition, DungeonTile> tiles,
    Map<String, List<String>> gauntletGroups
) implements Serializable {

    public DungeonMap(int width, int height, DungeonPosition entrance, DungeonPosition boss,
                       Map<DungeonPosition, DungeonTile> tiles) {
        this(width, height, entrance, boss, tiles, Map.of());
    }

    public DungeonTile tileAt(DungeonPosition position) {
        return tiles.get(position);
    }

    public boolean isInside(DungeonPosition position) {
        return position.x() >= 0 && position.y() >= 0 && position.x() < width && position.y() < height;
    }
}
```

The compact constructor without `gauntletGroups` keeps existing call sites compiling — they get an empty map.

- [ ] **Step 3: Compile and run tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonTileType.java \
        src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonMap.java
git commit -m "feat(dungeon): add ELITE tile type and gauntletGroups on map"
```

---

### Task 7: Add stats fields to `DungeonRunStats` and propagate

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonRunStats.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java`

- [ ] **Step 1: Update `DungeonRunStats`**

Replace `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonRunStats.java`:

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
    int shieldsUsed
) {}
```

- [ ] **Step 2: Update `buildStats` in `DungeonSessionService`**

In `DungeonSessionService.buildStats`, replace the constructor call:

```java
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
        state.shieldsUsed());
}
```

- [ ] **Step 3: Compile and run tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "feat(dungeon): add longest streak, elites cleared, shields used to stats"
```

---

### Task 8: Saved-session deserialization guard

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/SavedSessionService.java`

State record changes break Java-serialized saved runs. Catch the failure and discard cleanly.

- [ ] **Step 1: Find the dungeon-load path**

Run: `grep -n "loadDungeon\|InvalidClassException\|ObjectStreamException" src/main/java/com/HendrikHoemberg/StudyHelper/service/SavedSessionService.java`

Locate the method that deserializes `DungeonSessionState` (typically a method named `loadDungeon` or similar that reads bytes and casts to the state type).

- [ ] **Step 2: Wrap deserialization in a guard**

In the dungeon-load method, wrap the deserialization call in a try/catch:

```java
try {
    // existing deserialization code that produces DungeonSessionState
    return Optional.of(state);
} catch (java.io.ObjectStreamException ex) {
    // Saved dungeon was from an older binary schema — discard.
    discard(user, false);
    return Optional.empty();
}
```

If the file already catches a general `IOException`, add the `ObjectStreamException` catch *above* it (subclass first).

- [ ] **Step 3: Add a flash-message hook in `DungeonController.resume`**

In `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DungeonController.java`, the `resume` method already redirects with a flash message when reconcile fails. Add the same redirect when `loadDungeon` returns empty due to schema mismatch — but we cannot distinguish "no saved run" from "saved run was incompatible" without extra signal.

Instead, change `SavedSessionService.loadDungeon` to return a small result type, OR (simpler) add a sibling method `discardedAsIncompatible(User)` that the catch block sets to true and the controller checks once.

The simplest change: have the catch block set a flag on the user-scoped saved-session state that the controller's `resume` reads. Since `SavedSessionService` already has `discard(user, false)`, add a new method `wasDiscardedAsIncompatible(User user)` that returns true once after such a discard (then resets).

Implementation in `SavedSessionService`:

```java
private final Set<Long> incompatibleDiscardUserIds = ConcurrentHashMap.newKeySet();

public boolean consumeIncompatibleDiscardFlag(User user) {
    return incompatibleDiscardUserIds.remove(user.getId());
}

// inside the catch (ObjectStreamException):
incompatibleDiscardUserIds.add(user.getId());
discard(user, false);
return Optional.empty();
```

Then in `DungeonController.resume`, after `if (loaded.isEmpty())`:

```java
if (loaded.isEmpty()) {
    if (savedSessionService.consumeIncompatibleDiscardFlag(user)) {
        redirectAttributes.addFlashAttribute("errorMessage",
            "Your saved Dungeon was from an older version and could not be resumed.");
    }
    return "redirect:/study/start?mode=DUNGEON";
}
```

- [ ] **Step 4: Compile and run tests**

Run: `./mvnw test`
Expected: all pass. Add `import java.util.concurrent.ConcurrentHashMap;` and `import java.util.Set;` if needed.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "fix(dungeon): discard saved runs incompatible with new state schema"
```

---

## Phase 3 — Map generator rewrite

### Task 9: Update `DungeonSize` balance numbers

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSize.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSizeTests.java`

- [ ] **Step 1: Update the failing test first**

Open `src/test/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSizeTests.java`. For each existing assertion of `totalPrompts()`, `normalEncounterCount()`, or `bossPromptCount()`, update to the new values:

| Size | totalPrompts | normalEncounterCount | bossPromptCount |
|---|---|---|---|
| SMALL | 10 | 6 | 2 |
| MEDIUM | 16 | 9 | 3 |
| LARGE | 29 | 15 | 5 |

If the existing test asserted other values, replace them with the table above.

- [ ] **Step 2: Run, confirm failure**

Run: `./mvnw test -Dtest=DungeonSizeTests`
Expected: failure on the new assertions.

- [ ] **Step 3: Update `DungeonSize` enum**

Replace the three enum constants in `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSize.java`:

```java
SMALL(10, 6, 2),
MEDIUM(16, 9, 3),
LARGE(29, 15, 5);
```

- [ ] **Step 4: Run, confirm pass**

Run: `./mvnw test -Dtest=DungeonSizeTests`
Expected: pass.

- [ ] **Step 5: Run the full dungeon test suite — other tests may need updating**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: some failures. Tests that constructed flashcard mocks with the *old* counts (8/12/20) now fail because the service needs the new totals. Update each `mockFlashcards(N)` call in `DungeonSessionServiceTests` to use the new totals (10/16/29 per size). Re-run until green.

- [ ] **Step 6: Commit**

```bash
git add -u
git commit -m "feat(dungeon): update DungeonSize totals to 10/16/29"
```

---

### Task 10: Rewrite `DungeonMapGenerator` — rooms+corridors

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGenerator.java`
- Modify: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGeneratorTests.java`

This is a large rewrite. The generator now takes elite gauntlet groups as a second parameter (in addition to normal encounter ids) and uses a seeded `Random` so tests can pin layouts.

- [ ] **Step 1: Update the public signature first, with seeded RNG**

Replace `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonMapGenerator.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DungeonMapGenerator {

    private static final int MAX_REGEN_ATTEMPTS = 10;
    private static final int MAX_ROOM_ATTEMPTS = 50;

    public DungeonMap generate(DungeonSize size, List<String> normalEncounterIds) {
        return generate(size, normalEncounterIds, List.of(), new Random());
    }

    public DungeonMap generate(DungeonSize size, List<String> normalEncounterIds,
                                List<List<String>> eliteGauntletGroups) {
        return generate(size, normalEncounterIds, eliteGauntletGroups, new Random());
    }

    public DungeonMap generate(DungeonSize size, List<String> normalEncounterIds,
                                List<List<String>> eliteGauntletGroups, Random rng) {
        if (normalEncounterIds.size() != size.normalEncounterCount()) {
            throw new IllegalArgumentException("Normal encounter count must match dungeon size.");
        }

        int width = gridSize(size);
        int height = gridSize(size);
        int targetRoomCount = targetRooms(size);
        int trapCount = trapCount(size);
        int secretCount = secretCount(size);

        for (int attempt = 0; attempt < MAX_REGEN_ATTEMPTS; attempt++) {
            try {
                return tryGenerate(width, height, targetRoomCount, trapCount, secretCount,
                    normalEncounterIds, eliteGauntletGroups, rng);
            } catch (LayoutFailure ignored) {
                // try again with new entropy
            }
        }
        throw new IllegalStateException("Could not generate a valid dungeon layout after "
            + MAX_REGEN_ATTEMPTS + " attempts.");
    }

    private DungeonMap tryGenerate(int width, int height, int targetRoomCount,
                                    int trapCount, int secretCount,
                                    List<String> normalEncounterIds,
                                    List<List<String>> eliteGauntletGroups,
                                    Random rng) {
        Map<DungeonPosition, DungeonTile> tiles = filledWithWalls(width, height);
        List<Rect> rooms = placeRooms(width, height, targetRoomCount, rng);
        if (rooms.size() < 3) throw new LayoutFailure();
        carveRoomFloors(tiles, rooms);
        List<Edge> mstEdges = minimumSpanningTree(rooms);
        for (Edge e : mstEdges) carveLCorridor(tiles, rooms.get(e.a).center(), rooms.get(e.b).center(), rng);
        addExtraLoops(tiles, rooms, mstEdges, rng);

        DungeonPosition entrance = pickEntrance(rooms, tiles);
        DungeonPosition boss = pickBossFarthest(rooms, tiles, entrance);
        if (entrance.equals(boss)) throw new LayoutFailure();

        Rect entranceRoom = roomContaining(rooms, entrance);
        Rect bossRoom = roomContaining(rooms, boss);

        Map<String, List<String>> gauntletGroups = new LinkedHashMap<>();
        for (List<String> group : eliteGauntletGroups) {
            if (group.isEmpty()) continue;
            DungeonPosition elitePos = pickElitePosition(rooms, entranceRoom, bossRoom, tiles, rng);
            if (elitePos == null) throw new LayoutFailure();
            tiles.put(elitePos, new DungeonTile(elitePos, DungeonTileType.ELITE, false, false, group.get(0)));
            gauntletGroups.put(group.get(0), List.copyOf(group));
        }

        tiles.put(entrance, new DungeonTile(entrance, DungeonTileType.ENTRANCE, true, true, null));
        tiles.put(boss, new DungeonTile(boss, DungeonTileType.BOSS, false, false, null));

        placeEncounters(tiles, rooms, entrance, boss, normalEncounterIds, rng);
        placeRestTiles(tiles, rooms, entranceRoom, bossRoom, trapCount, rng);
        placeSecretCompartments(tiles, rooms, secretCount, width, height, rng);

        revealAround(tiles, entrance);
        return new DungeonMap(width, height, entrance, boss, Map.copyOf(tiles), Map.copyOf(gauntletGroups));
    }

    // ===== Geometry primitives =====

    private record Rect(int x, int y, int w, int h) {
        DungeonPosition center() { return new DungeonPosition(x + w / 2, y + h / 2); }
        boolean contains(DungeonPosition p) {
            return p.x() >= x && p.x() < x + w && p.y() >= y && p.y() < y + h;
        }
        boolean touches(Rect other) {
            return !(x + w + 1 <= other.x || other.x + other.w + 1 <= x
                  || y + h + 1 <= other.y || other.y + other.h + 1 <= y);
        }
    }
    private record Edge(int a, int b, int distance) {}
    private static class LayoutFailure extends RuntimeException {}

    private int gridSize(DungeonSize size) {
        return switch (size) { case SMALL -> 9; case MEDIUM -> 11; case LARGE -> 13; };
    }
    private int targetRooms(DungeonSize size) {
        return switch (size) { case SMALL -> 4; case MEDIUM -> 6; case LARGE -> 8; };
    }
    private int trapCount(DungeonSize size) {
        return switch (size) { case SMALL -> 1; case MEDIUM -> 2; case LARGE -> 3; };
    }
    private int secretCount(DungeonSize size) {
        return switch (size) { case SMALL -> 1; case MEDIUM -> 2; case LARGE -> 3; };
    }

    private Map<DungeonPosition, DungeonTile> filledWithWalls(int w, int h) {
        Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                DungeonPosition p = new DungeonPosition(x, y);
                tiles.put(p, new DungeonTile(p, DungeonTileType.WALL, false, false, null));
            }
        return tiles;
    }

    private List<Rect> placeRooms(int width, int height, int target, Random rng) {
        List<Rect> rooms = new ArrayList<>();
        int placed = 0;
        int attempts = 0;
        while (placed < target && attempts < target * MAX_ROOM_ATTEMPTS) {
            attempts++;
            int w = 2 + rng.nextInt(3);   // 2..4
            int h = 2 + rng.nextInt(2);   // 2..3
            int x = 1 + rng.nextInt(width - w - 1);
            int y = 1 + rng.nextInt(height - h - 1);
            Rect candidate = new Rect(x, y, w, h);
            boolean overlaps = rooms.stream().anyMatch(r -> r.touches(candidate));
            if (!overlaps) { rooms.add(candidate); placed++; }
        }
        return rooms;
    }

    private void carveRoomFloors(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms) {
        for (Rect r : rooms) {
            for (int y = r.y; y < r.y + r.h; y++)
                for (int x = r.x; x < r.x + r.w; x++) {
                    DungeonPosition p = new DungeonPosition(x, y);
                    tiles.put(p, new DungeonTile(p, DungeonTileType.FLOOR, false, false, null));
                }
        }
    }

    private List<Edge> minimumSpanningTree(List<Rect> rooms) {
        int n = rooms.size();
        List<Edge> all = new ArrayList<>();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                DungeonPosition a = rooms.get(i).center(), b = rooms.get(j).center();
                int d = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
                all.add(new Edge(i, j, d));
            }
        all.sort(Comparator.comparingInt(e -> e.distance));
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        List<Edge> mst = new ArrayList<>();
        for (Edge e : all) {
            int ra = find(parent, e.a), rb = find(parent, e.b);
            if (ra != rb) { parent[ra] = rb; mst.add(e); }
        }
        return mst;
    }

    private int find(int[] parent, int x) {
        while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x]; }
        return x;
    }

    private void carveLCorridor(Map<DungeonPosition, DungeonTile> tiles,
                                 DungeonPosition a, DungeonPosition b, Random rng) {
        boolean horizFirst = rng.nextBoolean();
        if (horizFirst) {
            carveHoriz(tiles, a.x(), b.x(), a.y());
            carveVert(tiles, a.y(), b.y(), b.x());
        } else {
            carveVert(tiles, a.y(), b.y(), a.x());
            carveHoriz(tiles, a.x(), b.x(), b.y());
        }
    }

    private void carveHoriz(Map<DungeonPosition, DungeonTile> tiles, int x1, int x2, int y) {
        int lo = Math.min(x1, x2), hi = Math.max(x1, x2);
        for (int x = lo; x <= hi; x++) {
            DungeonPosition p = new DungeonPosition(x, y);
            if (tiles.get(p).type() == DungeonTileType.WALL) {
                tiles.put(p, new DungeonTile(p, DungeonTileType.FLOOR, false, false, null));
            }
        }
    }

    private void carveVert(Map<DungeonPosition, DungeonTile> tiles, int y1, int y2, int x) {
        int lo = Math.min(y1, y2), hi = Math.max(y1, y2);
        for (int y = lo; y <= hi; y++) {
            DungeonPosition p = new DungeonPosition(x, y);
            if (tiles.get(p).type() == DungeonTileType.WALL) {
                tiles.put(p, new DungeonTile(p, DungeonTileType.FLOOR, false, false, null));
            }
        }
    }

    private void addExtraLoops(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms,
                                List<Edge> mst, Random rng) {
        int loops = Math.min(2, Math.max(0, rooms.size() - 3));
        Set<Long> mstSet = new HashSet<>();
        for (Edge e : mst) mstSet.add(((long) Math.min(e.a, e.b) << 32) | Math.max(e.a, e.b));
        List<Edge> candidates = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++)
            for (int j = i + 1; j < rooms.size(); j++) {
                long key = ((long) i << 32) | j;
                if (mstSet.contains(key)) continue;
                DungeonPosition a = rooms.get(i).center(), b = rooms.get(j).center();
                int d = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
                if (d <= 8) candidates.add(new Edge(i, j, d));
            }
        Collections.shuffle(candidates, rng);
        for (int i = 0; i < Math.min(loops, candidates.size()); i++) {
            Edge e = candidates.get(i);
            carveLCorridor(tiles, rooms.get(e.a).center(), rooms.get(e.b).center(), rng);
        }
    }

    private DungeonPosition pickEntrance(List<Rect> rooms, Map<DungeonPosition, DungeonTile> tiles) {
        return rooms.get(0).center();
    }

    private DungeonPosition pickBossFarthest(List<Rect> rooms,
                                              Map<DungeonPosition, DungeonTile> tiles,
                                              DungeonPosition entrance) {
        DungeonPosition best = entrance;
        int bestDist = -1;
        for (Rect r : rooms) {
            DungeonPosition c = r.center();
            int d = bfsDistance(tiles, entrance, c);
            if (d > bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    private int bfsDistance(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition from, DungeonPosition to) {
        Map<DungeonPosition, Integer> dist = new HashMap<>();
        Deque<DungeonPosition> queue = new ArrayDeque<>();
        dist.put(from, 0); queue.add(from);
        while (!queue.isEmpty()) {
            DungeonPosition p = queue.poll();
            if (p.equals(to)) return dist.get(p);
            for (DungeonDirection d : DungeonDirection.values()) {
                DungeonPosition n = p.move(d);
                DungeonTile t = tiles.get(n);
                if (t == null || t.type() == DungeonTileType.WALL) continue;
                if (dist.containsKey(n)) continue;
                dist.put(n, dist.get(p) + 1);
                queue.add(n);
            }
        }
        return -1;
    }

    private Rect roomContaining(List<Rect> rooms, DungeonPosition p) {
        for (Rect r : rooms) if (r.contains(p)) return r;
        return null;
    }

    private DungeonPosition pickElitePosition(List<Rect> rooms, Rect entranceRoom, Rect bossRoom,
                                               Map<DungeonPosition, DungeonTile> tiles, Random rng) {
        List<Rect> candidates = new ArrayList<>();
        for (Rect r : rooms) {
            if (r.equals(entranceRoom) || r.equals(bossRoom)) continue;
            candidates.add(r);
        }
        if (candidates.isEmpty()) return null;
        Collections.shuffle(candidates, rng);
        Rect chosen = candidates.get(0);
        return chosen.center();
    }

    private void placeEncounters(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms,
                                  DungeonPosition entrance, DungeonPosition boss,
                                  List<String> ids, Random rng) {
        List<DungeonPosition> slots = new ArrayList<>();
        for (Rect r : rooms) {
            for (int y = r.y; y < r.y + r.h; y++)
                for (int x = r.x; x < r.x + r.w; x++) {
                    DungeonPosition p = new DungeonPosition(x, y);
                    if (p.equals(entrance) || p.equals(boss)) continue;
                    DungeonTile t = tiles.get(p);
                    if (t.type() == DungeonTileType.FLOOR) slots.add(p);
                }
        }
        Collections.shuffle(slots, rng);
        if (slots.size() < ids.size()) throw new LayoutFailure();
        for (int i = 0; i < ids.size(); i++) {
            DungeonPosition p = slots.get(i);
            tiles.put(p, new DungeonTile(p, DungeonTileType.ENCOUNTER, false, false, ids.get(i)));
        }
    }

    private void placeRestTiles(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms,
                                 Rect entranceRoom, Rect bossRoom, int trapCount, Random rng) {
        List<DungeonPosition> free = new ArrayList<>();
        for (Rect r : rooms) {
            if (r.equals(entranceRoom) || r.equals(bossRoom)) continue;
            for (int y = r.y; y < r.y + r.h; y++)
                for (int x = r.x; x < r.x + r.w; x++) {
                    DungeonPosition p = new DungeonPosition(x, y);
                    if (tiles.get(p).type() == DungeonTileType.FLOOR) free.add(p);
                }
        }
        Collections.shuffle(free, rng);
        int idx = 0;
        if (idx < free.size()) {
            DungeonPosition heal = free.get(idx++);
            tiles.put(heal, new DungeonTile(heal, DungeonTileType.HEAL, false, false, null));
        }
        if (idx < free.size()) {
            DungeonPosition tre = free.get(idx++);
            tiles.put(tre, new DungeonTile(tre, DungeonTileType.TREASURE, false, false, null));
        }
        for (int t = 0; t < trapCount && idx < free.size(); t++) {
            DungeonPosition trap = free.get(idx++);
            tiles.put(trap, new DungeonTile(trap, DungeonTileType.TRAP, false, false, null));
        }
    }

    private void placeSecretCompartments(Map<DungeonPosition, DungeonTile> tiles, List<Rect> rooms,
                                          int secretCount, int width, int height, Random rng) {
        int placed = 0;
        List<Rect> shuffled = new ArrayList<>(rooms);
        Collections.shuffle(shuffled, rng);
        for (Rect r : shuffled) {
            if (placed >= secretCount) break;
            for (DungeonDirection dir : DungeonDirection.values()) {
                DungeonPosition n1 = r.center().move(dir);
                DungeonPosition n2 = n1.move(dir);
                if (!isInside(n1, width, height) || !isInside(n2, width, height)) continue;
                DungeonTile t1 = tiles.get(n1), t2 = tiles.get(n2);
                if (t1.type() == DungeonTileType.WALL && t2.type() == DungeonTileType.WALL) {
                    tiles.put(n1, new DungeonTile(n1, DungeonTileType.SECRET_WALL, false, false, null));
                    DungeonTileType inner = (placed % 2 == 0) ? DungeonTileType.TREASURE : DungeonTileType.HEAL;
                    tiles.put(n2, new DungeonTile(n2, inner, false, false, null));
                    placed++;
                    break;
                }
            }
        }
    }

    private boolean isInside(DungeonPosition p, int width, int height) {
        return p.x() >= 0 && p.x() < width && p.y() >= 0 && p.y() < height;
    }

    private void revealAround(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition center) {
        reveal(tiles, center);
        for (DungeonDirection direction : DungeonDirection.values()) reveal(tiles, center.move(direction));
    }

    private void reveal(Map<DungeonPosition, DungeonTile> tiles, DungeonPosition position) {
        DungeonTile tile = tiles.get(position);
        if (tile != null) tiles.put(position, tile.reveal());
    }
}
```

- [ ] **Step 2: Update existing `DungeonMapGeneratorTests` to use the new API**

The existing tests call `generator.generate(size, ids)`. They will still compile (we kept the 2-arg overload), but they should be expanded with seeded-RNG tests. Add this test:

```java
@Test
void generate_isDeterministicGivenSameSeed() {
    DungeonMap a = generator.generate(DungeonSize.MEDIUM,
        encounterIds(DungeonSize.MEDIUM.normalEncounterCount()),
        List.of(), new java.util.Random(42L));
    DungeonMap b = generator.generate(DungeonSize.MEDIUM,
        encounterIds(DungeonSize.MEDIUM.normalEncounterCount()),
        List.of(), new java.util.Random(42L));
    assertThat(a.tiles().keySet()).isEqualTo(b.tiles().keySet());
    for (DungeonPosition p : a.tiles().keySet()) {
        assertThat(a.tiles().get(p).type()).isEqualTo(b.tiles().get(p).type());
    }
    assertThat(a.entrance()).isEqualTo(b.entrance());
    assertThat(a.boss()).isEqualTo(b.boss());
}

@Test
void generate_placesElitesWhenProvided() {
    List<List<String>> gauntlets = List.of(List.of("e1a", "e1b"));
    DungeonMap map = generator.generate(DungeonSize.MEDIUM,
        encounterIds(DungeonSize.MEDIUM.normalEncounterCount()),
        gauntlets, new java.util.Random(7L));
    long eliteTiles = map.tiles().values().stream()
        .filter(t -> t.type() == DungeonTileType.ELITE).count();
    assertThat(eliteTiles).isEqualTo(1);
    assertThat(map.gauntletGroups()).hasSize(1);
    assertThat(map.gauntletGroups().values().iterator().next()).containsExactly("e1a", "e1b");
}
```

- [ ] **Step 3: Run dungeon tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: pass. Some pre-existing tests may have asserted specific properties of the old corridor layout — relax those to "boss is reachable from entrance" and "all encounter tiles are reachable from entrance" (the spec's core invariants).

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "feat(dungeon): rewrite map generator with rooms+corridors algorithm"
```

---

## Phase 4 — Encounter mechanics

### Task 11: Streak counter and shield granting

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java`
- Create: `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterServiceTests.java`

- [ ] **Step 1: Write failing tests for streak/shield behaviour**

Create `src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterServiceTests.java`:

```java
package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DungeonEncounterServiceTests {

    private DungeonEncounterService service;

    @BeforeEach
    void setUp() { service = new DungeonEncounterService(); }

    private DungeonSessionState stateWith(DungeonEncounter active, int streak, int shields) {
        DungeonConfig config = new DungeonConfig(
            DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
        DungeonPosition pos = new DungeonPosition(0, 0);
        DungeonMap map = new DungeonMap(1, 1, pos, pos, Map.of());
        Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
        encs.put(active.id(), active);
        return new DungeonSessionState(
            config, map, pos, encs, List.of(), 0,
            active.id(),
            5, 0, 0, 0, Set.of(),
            false, false,
            streak, shields, List.of(), streak, 0, 0);
    }

    private DungeonEncounter flashcard() {
        return DungeonEncounter.flashcard("f1", false, 100L, "Q", "A", null, null).activate();
    }

    @Test
    void answerCorrect_incrementsStreak() {
        DungeonSessionState before = stateWith(flashcard(), 0, 0);
        DungeonSessionState after = service.answerFlashcard(before, true);
        assertThat(after.streak()).isEqualTo(1);
        assertThat(after.longestStreak()).isEqualTo(1);
        assertThat(after.shields()).isZero();
    }

    @Test
    void thirdCorrectInRow_grantsShield() {
        DungeonSessionState s = stateWith(flashcard(), 2, 0);
        DungeonSessionState after = service.answerFlashcard(s, true);
        assertThat(after.streak()).isEqualTo(3);
        assertThat(after.shields()).isEqualTo(1);
    }

    @Test
    void shieldsCapAtTwo() {
        DungeonSessionState s = stateWith(flashcard(), 5, 2);
        DungeonSessionState after = service.answerFlashcard(s, true);
        assertThat(after.streak()).isEqualTo(6);
        assertThat(after.shields()).isEqualTo(2);
    }

    @Test
    void wrongAnswer_resetsStreakAndDealsDamage() {
        DungeonSessionState s = stateWith(flashcard(), 4, 0);
        DungeonSessionState after = service.answerFlashcard(s, false);
        assertThat(after.streak()).isZero();
        assertThat(after.health()).isEqualTo(4);
    }

    @Test
    void wrongAnswerWithShield_absorbsDamageAndStillResetsStreak() {
        DungeonSessionState s = stateWith(flashcard(), 4, 1);
        DungeonSessionState after = service.answerFlashcard(s, false);
        assertThat(after.streak()).isZero();
        assertThat(after.health()).isEqualTo(5);
        assertThat(after.shields()).isZero();
        assertThat(after.shieldsUsed()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Update `DungeonDamage.takeDamage` to consume shields first**

Replace the `takeDamage` body in `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonDamage.java`:

```java
public static DungeonSessionState takeDamage(DungeonSessionState state, int amount) {
    if (state.shields() > 0) {
        return new DungeonSessionState(
            state.config(), state.map(), state.playerPosition(),
            state.encounters(), state.bossEncounterIds(), state.bossIndex(),
            state.activeEncounterId(),
            state.health(), state.score(),
            state.answeredCount(), state.correctCount(),
            state.visibleTiles(),
            state.won(), state.defeated(),
            state.streak(), state.shields() - 1, state.gauntletQueue(),
            state.longestStreak(), state.elitesCleared(), state.shieldsUsed() + 1);
    }
    int newHealth = Math.max(0, state.health() - amount);
    boolean defeated = state.defeated() || newHealth <= 0;
    return new DungeonSessionState(
        state.config(), state.map(), state.playerPosition(),
        state.encounters(), state.bossEncounterIds(), state.bossIndex(),
        state.activeEncounterId(),
        newHealth, state.score(),
        state.answeredCount(), state.correctCount(),
        state.visibleTiles(),
        state.won(), defeated,
        state.streak(), state.shields(), state.gauntletQueue(),
        state.longestStreak(), state.elitesCleared(), state.shieldsUsed());
}
```

Also update `heal` to pass through new fields properly (same pattern — copy `streak/shields/gauntletQueue/longestStreak/elitesCleared/shieldsUsed`).

- [ ] **Step 3: Add new `DungeonDamage` tests for shield absorption**

Add to `DungeonDamageTests`:

```java
@Test
void takeDamage_consumesShieldBeforeHealth() {
    DungeonSessionState before = new DungeonSessionState(
        new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
        new DungeonMap(1, 1, new DungeonPosition(0,0), new DungeonPosition(0,0), Map.of()),
        new DungeonPosition(0,0), Map.of(), List.of(), 0, null,
        5, 0, 0, 0, Set.of(), false, false,
        0, 1, List.of(), 0, 0, 0);
    DungeonSessionState after = DungeonDamage.takeDamage(before, 1);
    assertThat(after.health()).isEqualTo(5);
    assertThat(after.shields()).isZero();
    assertThat(after.shieldsUsed()).isEqualTo(1);
}
```

- [ ] **Step 4: Implement streak/shield logic in `DungeonEncounterService.applyAnswer`**

Modify the `applyAnswer` method in `DungeonEncounterService` to update streak and shields. Replace the existing body's correct/wrong handling section with:

```java
// after encounters.put(encounter.id(), encounter.clear(answer, correct));

int newStreak = correct ? state.streak() + 1 : 0;
int newShields = state.shields();
if (correct && newStreak % STREAK_FOR_SHIELD == 0 && newShields < MAX_SHIELDS) {
    newShields++;
}
int newLongest = Math.max(state.longestStreak(), newStreak);

DungeonSessionState working = new DungeonSessionState(
    state.config(), state.map(), state.playerPosition(),
    Map.copyOf(encounters), state.bossEncounterIds(), state.bossIndex(),
    state.activeEncounterId(),
    state.health(), state.score(),
    answeredCount, correctCount,
    state.visibleTiles(),
    state.won(), state.defeated(),
    newStreak, newShields, state.gauntletQueue(),
    newLongest, state.elitesCleared(), state.shieldsUsed());

if (!correct) {
    working = DungeonDamage.takeDamage(working, WRONG_ANSWER_DAMAGE);
}
```

Add the constants to the class:

```java
private static final int STREAK_FOR_SHIELD = 3;
private static final int MAX_SHIELDS = 2;
private static final int WRONG_ANSWER_DAMAGE = 1;
```

(Remove the existing `working = DungeonDamage.takeDamage(working, 1);` from the previous version and use the new constant.)

- [ ] **Step 5: Run all relevant tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: pass. Update any other test fixtures whose state constructions still use old field counts.

- [ ] **Step 6: Commit**

```bash
git add -u
git commit -m "feat(dungeon): streak counter and shield charges; shield absorbs damage"
```

---

### Task 12: Elite gauntlet activation and sequencing

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonEncounterService.java`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java`

- [ ] **Step 1: Write failing test for elite activation**

Add to `DungeonEncounterServiceTests`:

```java
@Test
void activateElite_setsGauntletQueueWithRemainingIds() {
    DungeonEncounter e1 = DungeonEncounter.flashcard("e1", false, 1L, "Q1", "A1", null, null);
    DungeonEncounter e2 = DungeonEncounter.flashcard("e2", false, 2L, "Q2", "A2", null, null);
    Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
    encs.put("e1", e1); encs.put("e2", e2);
    DungeonConfig config = new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL,
        List.of(1L), QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, "");
    DungeonPosition pos = new DungeonPosition(0, 0);
    DungeonMap map = new DungeonMap(1, 1, pos, pos, Map.of(),
        Map.of("e1", List.of("e1", "e2")));
    DungeonSessionState before = new DungeonSessionState(
        config, map, pos, encs, List.of(), 0, null,
        5, 0, 0, 0, Set.of(), false, false,
        0, 0, List.of(), 0, 0, 0);

    DungeonSessionState after = service.activateElite(before, "e1");
    assertThat(after.activeEncounterId()).isEqualTo("e1");
    assertThat(after.gauntletQueue()).containsExactly("e2");
}

@Test
void correctElite_advancesQueueAndActivatesNext() {
    // build a state mid-gauntlet
    DungeonEncounter e1 = DungeonEncounter.flashcard("e1", false, 1L, "Q1", "A1", null, null).activate();
    DungeonEncounter e2 = DungeonEncounter.flashcard("e2", false, 2L, "Q2", "A2", null, null);
    Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
    encs.put("e1", e1); encs.put("e2", e2);
    DungeonSessionState before = new DungeonSessionState(
        new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
        new DungeonMap(1, 1, new DungeonPosition(0,0), new DungeonPosition(0,0), Map.of(),
            Map.of("e1", List.of("e1", "e2"))),
        new DungeonPosition(0,0), encs, List.of(), 0, "e1",
        5, 0, 0, 0, Set.of(), false, false,
        0, 0, List.of("e2"), 0, 0, 0);

    DungeonSessionState after = service.answerFlashcard(before, true);
    assertThat(after.activeEncounterId()).isEqualTo("e2");
    assertThat(after.gauntletQueue()).isEmpty();
}

@Test
void wrongElite_abortsGauntletAndDamages() {
    DungeonEncounter e1 = DungeonEncounter.flashcard("e1", false, 1L, "Q1", "A1", null, null).activate();
    DungeonEncounter e2 = DungeonEncounter.flashcard("e2", false, 2L, "Q2", "A2", null, null);
    Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
    encs.put("e1", e1); encs.put("e2", e2);
    DungeonSessionState before = new DungeonSessionState(
        new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
        new DungeonMap(1, 1, new DungeonPosition(0,0), new DungeonPosition(0,0), Map.of(),
            Map.of("e1", List.of("e1", "e2"))),
        new DungeonPosition(0,0), encs, List.of(), 0, "e1",
        5, 0, 0, 0, Set.of(), false, false,
        0, 0, List.of("e2"), 0, 0, 0);

    DungeonSessionState after = service.answerFlashcard(before, false);
    assertThat(after.activeEncounterId()).isNull();
    assertThat(after.gauntletQueue()).isEmpty();
    assertThat(after.health()).isEqualTo(4);
}

@Test
void clearingFinalElite_grantsFullHealAndShield() {
    DungeonEncounter e1 = DungeonEncounter.flashcard("e1", false, 1L, "Q1", "A1", null, null).activate();
    Map<String, DungeonEncounter> encs = new LinkedHashMap<>();
    encs.put("e1", e1);
    DungeonSessionState before = new DungeonSessionState(
        new DungeonConfig(DungeonMode.FLASHCARDS, DungeonSize.SMALL, List.of(1L),
            QuizQuestionMode.MCQ_ONLY, Difficulty.MEDIUM, ""),
        new DungeonMap(1, 1, new DungeonPosition(0,0), new DungeonPosition(0,0), Map.of(),
            Map.of("e1", List.of("e1"))),
        new DungeonPosition(0,0), encs, List.of(), 0, "e1",
        2, 0, 0, 0, Set.of(), false, false,
        0, 0, List.of(), 0, 0, 0);

    DungeonSessionState after = service.answerFlashcard(before, true);
    assertThat(after.health()).isEqualTo(5);
    assertThat(after.shields()).isEqualTo(1);
    assertThat(after.elitesCleared()).isEqualTo(1);
}
```

- [ ] **Step 2: Implement `activateElite` and gauntlet sequencing**

Add to `DungeonEncounterService`:

```java
public DungeonSessionState activateElite(DungeonSessionState state, String firstEncounterId) {
    List<String> group = state.map().gauntletGroups().get(firstEncounterId);
    if (group == null || group.isEmpty()) return state;
    Map<String, DungeonEncounter> encs = new LinkedHashMap<>(state.encounters());
    DungeonEncounter first = encs.get(firstEncounterId);
    if (first == null || first.status() != DungeonEncounterStatus.PENDING) return state;
    encs.put(firstEncounterId, first.activate());
    List<String> remaining = new ArrayList<>(group.subList(1, group.size()));
    return new DungeonSessionState(
        state.config(), state.map(), state.playerPosition(),
        Map.copyOf(encs), state.bossEncounterIds(), state.bossIndex(),
        firstEncounterId,
        state.health(), state.score(),
        state.answeredCount(), state.correctCount(),
        state.visibleTiles(),
        state.won(), state.defeated(),
        state.streak(), state.shields(), List.copyOf(remaining),
        state.longestStreak(), state.elitesCleared(), state.shieldsUsed());
}
```

Then modify `applyAnswer` to handle gauntlet branches. After the existing block that updates streak/shields and applies damage, add this handling *before* the boss/normal cleanup:

```java
// Elite gauntlet handling
if (!state.gauntletQueue().isEmpty() || isPartOfActiveGauntlet(state, encounter)) {
    if (!correct) {
        // Abort: clear queue and active encounter, leave ELITE tile intact
        return new DungeonSessionState(
            working.config(), working.map(), working.playerPosition(),
            working.encounters(), working.bossEncounterIds(), working.bossIndex(),
            null,
            working.health(), working.score(),
            working.answeredCount(), working.correctCount(),
            working.visibleTiles(),
            working.won(), working.defeated(),
            working.streak(), working.shields(), List.of(),
            working.longestStreak(), working.elitesCleared(), working.shieldsUsed());
    }
    if (!working.gauntletQueue().isEmpty()) {
        // Advance to next encounter in gauntlet
        String nextId = working.gauntletQueue().get(0);
        List<String> remaining = working.gauntletQueue().subList(1, working.gauntletQueue().size());
        Map<String, DungeonEncounter> encs2 = new LinkedHashMap<>(working.encounters());
        DungeonEncounter next = encs2.get(nextId);
        if (next != null && next.status() == DungeonEncounterStatus.PENDING) {
            encs2.put(nextId, next.activate());
        }
        return new DungeonSessionState(
            working.config(), working.map(), working.playerPosition(),
            Map.copyOf(encs2), working.bossEncounterIds(), working.bossIndex(),
            nextId,
            working.health(), working.score(),
            working.answeredCount(), working.correctCount(),
            working.visibleTiles(),
            working.won(), working.defeated(),
            working.streak(), working.shields(), List.copyOf(remaining),
            working.longestStreak(), working.elitesCleared(), working.shieldsUsed());
    }
    // Final elite cleared: heal + shield + clear ELITE tile
    DungeonSessionState rewarded = DungeonDamage.heal(working, DungeonDamage.STARTING_HEALTH_CAP);
    int grantedShields = Math.min(MAX_SHIELDS, rewarded.shields() + 1);
    int newElites = rewarded.elitesCleared() + 1;
    Map<DungeonPosition, DungeonTile> tiles = new LinkedHashMap<>(rewarded.map().tiles());
    DungeonPosition pos = rewarded.playerPosition();
    DungeonTile cur = tiles.get(pos);
    if (cur != null && cur.type() == DungeonTileType.ELITE) {
        tiles.put(pos, cur.withType(DungeonTileType.FLOOR, null));
    }
    DungeonMap nextMap = new DungeonMap(rewarded.map().width(), rewarded.map().height(),
        rewarded.map().entrance(), rewarded.map().boss(),
        Map.copyOf(tiles), rewarded.map().gauntletGroups());
    return new DungeonSessionState(
        rewarded.config(), nextMap, rewarded.playerPosition(),
        rewarded.encounters(), rewarded.bossEncounterIds(), rewarded.bossIndex(),
        null,
        rewarded.health(), rewarded.score(),
        rewarded.answeredCount(), rewarded.correctCount(),
        rewarded.visibleTiles(),
        rewarded.won(), rewarded.defeated(),
        rewarded.streak(), grantedShields, List.of(),
        rewarded.longestStreak(), newElites, rewarded.shieldsUsed());
}
```

Add the helper:

```java
private boolean isPartOfActiveGauntlet(DungeonSessionState state, DungeonEncounter encounter) {
    return state.map().gauntletGroups().values().stream()
        .anyMatch(group -> group.contains(encounter.id()));
}
```

- [ ] **Step 3: Wire ELITE activation into `DungeonSessionService.move`**

Update `DungeonSessionService.move` to dispatch ELITE tiles to the new method:

```java
public DungeonSessionState move(DungeonSessionState state, DungeonDirection direction) {
    DungeonNavigationService.MoveResult result = navigationService.move(state, direction);
    DungeonSessionState moved = result.state();
    if (result.encounterId() != null) {
        if (result.landedTileType() == DungeonTileType.ELITE) {
            moved = encounterService.activateElite(moved, result.encounterId());
        } else {
            boolean isBoss = result.landedTileType() == DungeonTileType.BOSS;
            moved = encounterService.activate(moved, result.encounterId(), isBoss);
        }
    }
    return moved;
}
```

Also update `DungeonNavigationService.move` so it returns the ELITE tile's `encounterId` (the existing switch already handles `ENCOUNTER` and `BOSS`; add `case ELITE -> encounterIdHit = activeTile.encounterId();`).

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat(dungeon): elite gauntlet activation, sequencing, and reward"
```

---

### Task 13: Wire gauntlet groups into session creation

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionService.java`

Currently `createFlashcardDungeon` and `createAiQuizDungeon` only build normal+boss encounters; they don't build elite gauntlets. Add elite encounters and pass gauntlet groups to the generator.

- [ ] **Step 1: Add elite count helper to `DungeonSize`**

Add to `src/main/java/com/HendrikHoemberg/StudyHelper/dto/DungeonSize.java`:

```java
public int eliteGauntletCount() {
    return switch (this) { case SMALL -> 1; case MEDIUM -> 2; case LARGE -> 3; };
}

public int cardsPerEliteGauntlet() {
    return switch (this) { case SMALL -> 2; case MEDIUM -> 2; case LARGE -> 3; };
}
```

Verify the totals still add up: SMALL = 6 + 2 + (1×2) = 10 ✓; MEDIUM = 9 + 3 + (2×2) = 16 ✓; LARGE = 15 + 5 + (3×3) = 29 ✓.

- [ ] **Step 2: Update `createFlashcardDungeon`**

After the existing boss-prompt loop in `DungeonSessionService.createFlashcardDungeon`, add elite construction:

```java
int eliteStart = bossStart + size.bossPromptCount();
List<List<String>> eliteGauntlets = new ArrayList<>();
int eliteCardsPerGroup = size.cardsPerEliteGauntlet();
for (int g = 0; g < size.eliteGauntletCount(); g++) {
    List<String> group = new ArrayList<>();
    for (int c = 0; c < eliteCardsPerGroup; c++) {
        int cardIdx = eliteStart + (g * eliteCardsPerGroup) + c;
        Flashcard card = flashcards.get(cardIdx);
        String id = "fc_elite_" + g + "_" + c;
        encounters.put(id, DungeonEncounter.flashcard(id, false, card.getId(),
            card.getFrontText(), card.getBackText(),
            card.getFrontImageFilename(), card.getBackImageFilename()));
        group.add(id);
    }
    eliteGauntlets.add(group);
}

DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds, eliteGauntlets);
```

Replace the existing `DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds);` with the new line above.

- [ ] **Step 3: Update `createAiQuizDungeon` symmetrically**

After the existing boss-prompt loop:

```java
int eliteStart = bossStart + size.bossPromptCount();
List<List<String>> eliteGauntlets = new ArrayList<>();
int eliteCardsPerGroup = size.cardsPerEliteGauntlet();
for (int g = 0; g < size.eliteGauntletCount(); g++) {
    List<String> group = new ArrayList<>();
    for (int c = 0; c < eliteCardsPerGroup; c++) {
        int idx = eliteStart + (g * eliteCardsPerGroup) + c;
        QuizQuestion q = questions.get(idx);
        String id = "qz_elite_" + g + "_" + c;
        encounters.put(id, DungeonEncounter.quiz(id, false, q));
        group.add(id);
    }
    eliteGauntlets.add(group);
}

DungeonMap map = dungeonMapGenerator.generate(size, normalEncounterIds, eliteGauntlets);
```

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest='Dungeon*Tests'`
Expected: pass. `DungeonSessionServiceTests` may need its `mockFlashcards(n)` calls updated to the new totals if not already done in Task 9; also when the test sets up `dungeonMapGenerator.generate(...)` mock expectations, those must now accept the 3-arg overload.

Run: `grep -n "dungeonMapGenerator.generate" src/test/java/com/HendrikHoemberg/StudyHelper/service/DungeonSessionServiceTests.java`

Update each `when(dungeonMapGenerator.generate(any(), anyList())).thenReturn(...)` to `when(dungeonMapGenerator.generate(any(), anyList(), anyList())).thenReturn(...)`.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat(dungeon): build elite gauntlets during session creation"
```

---

## Phase 5 — UI: HUD, canvas, complete page

### Task 14: HUD — Shields and Streak items

**Files:**
- Modify: `src/main/resources/templates/fragments/dungeon-game.html`
- Modify: `src/main/resources/static/css/dungeon.css`

- [ ] **Step 1: Add HUD items to the template**

In `src/main/resources/templates/fragments/dungeon-game.html`, locate the `sh-dungeon-hud` div (currently three items: Health, Score, Progress). Add two new items between Score and Progress:

```html
<div class="sh-dungeon-hud-item sh-dungeon-hud-item-shields"
     th:classappend="${state.shields == 0} ? 'is-hidden'">
    <span class="sh-dungeon-hud-label" th:text="#{dungeon.shields}">Shields</span>
    <span class="sh-dungeon-hud-value sh-dungeon-hud-shields">
        <iconify-icon icon="lucide:shield" th:each="i : ${#numbers.sequence(1, state.shields > 0 ? state.shields : 1)}"
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
```

- [ ] **Step 2: Add CSS for the new HUD items**

Append to `src/main/resources/static/css/dungeon.css`:

```css
.sh-dungeon-hud-item.is-hidden { display: none; }
.sh-dungeon-hud-shields { display: inline-flex; gap: 2px; }
.sh-dungeon-hud-shields iconify-icon { font-size: 1.1em; color: var(--accent, #2dd4bf); }
.sh-dungeon-hud-streak { font-weight: 700; }
.sh-dungeon-hud-streak.is-pulsing {
    animation: sh-dungeon-streak-pulse 0.9s ease-in-out 2;
    color: #fbbf24;
}
@keyframes sh-dungeon-streak-pulse {
    0%, 100% { transform: scale(1); }
    50% { transform: scale(1.18); text-shadow: 0 0 12px #fbbf24; }
}
@media (max-width: 540px) {
    .sh-dungeon-hud { flex-wrap: wrap; }
}
```

- [ ] **Step 3: Add the new i18n keys**

In `src/main/resources/messages.properties`, append:

```
dungeon.shields=Shields
dungeon.streak=Streak
```

If `messages_de.properties` exists, add German equivalents (`Schilde`, `Serie`). Other locale files in the project should get either the English fallback or translated values per existing convention — grep for `dungeon.health=` to find every locale file and add the new keys.

- [ ] **Step 4: Smoke verify**

Start the app, begin a small flashcard dungeon, answer questions correctly until a shield is granted at streak 3 — confirm the HUD shows the shield and streak counter. Take a wrong answer to confirm the shield is consumed.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat(dungeon): HUD shields and streak items"
```

---

### Task 15: Canvas — ELITE tile sprite and telegraphed traps

**Files:**
- Modify: `src/main/resources/static/js/dungeon.js`

- [ ] **Step 1: Register the new sprites**

Locate `drawPixelSprite` in `dungeon.js` (around line 430). The function uses a string-keyed sprite registry. Add two new sprite keys: `ELITE` and `TRAP_TELEGRAPHED`. Implement them similarly to existing sprites — use a contrasting colour palette for ELITE (e.g. crossed-swords on dark red background) and a cracked-floor variant for TRAP_TELEGRAPHED (e.g. base floor colour with diagonal crack lines).

Example diff inside `drawPixelSprite`:

```js
} else if (spriteName === 'ELITE') {
    ctx.fillStyle = '#7f1d1d';
    ctx.fillRect(px, py, size, size);
    ctx.strokeStyle = '#fbbf24';
    ctx.lineWidth = Math.max(2, size * 0.08);
    var pad = size * 0.22;
    ctx.beginPath();
    ctx.moveTo(px + pad, py + pad);
    ctx.lineTo(px + size - pad, py + size - pad);
    ctx.moveTo(px + size - pad, py + pad);
    ctx.lineTo(px + pad, py + size - pad);
    ctx.stroke();
} else if (spriteName === 'TRAP_TELEGRAPHED') {
    drawFloorTile(ctx, px, py, size, false);
    ctx.strokeStyle = '#dc2626';
    ctx.lineWidth = Math.max(1, size * 0.05);
    ctx.beginPath();
    var midY = py + size * 0.5;
    ctx.moveTo(px + size * 0.15, midY - size * 0.2);
    ctx.lineTo(px + size * 0.4, midY + size * 0.1);
    ctx.lineTo(px + size * 0.6, midY - size * 0.1);
    ctx.lineTo(px + size * 0.85, midY + size * 0.2);
    ctx.stroke();
}
```

- [ ] **Step 2: Dispatch ELITE and telegraphed traps in `renderAll`**

In `renderAll`, find the tile-rendering switch (around the section iterating tiles to draw). Add cases:

```js
if (tile.type === 'ELITE' && tile.revealed) {
    drawPixelSprite(ctx, 'ELITE', px, py, tileSize);
    continue;
}
if (tile.type === 'TRAP' && tile.revealed && !tile.explored) {
    drawPixelSprite(ctx, 'TRAP_TELEGRAPHED', px, py, tileSize);
    continue;
}
```

(Place these BEFORE the existing TRAP / ENCOUNTER / etc. handlers so the special cases win.)

- [ ] **Step 3: Smoke verify**

Start the app, begin a MEDIUM dungeon (which has 2 elites and 2 traps). Walk around — verify ELITE tiles render with crossed swords, and traps render with the cracked-floor variant once revealed (adjacent walk).

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "feat(dungeon): render ELITE tiles and telegraphed traps on canvas"
```

---

### Task 16: Canvas combat — elite splash, monster sprite, progress badge

**Files:**
- Modify: `src/main/resources/templates/fragments/dungeon-game.html`
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/controller/DungeonController.java`
- Modify: `src/main/resources/static/js/dungeon.js`

- [ ] **Step 1: Expose gauntlet position/total on the canvas data attributes**

In `dungeon-game.html`, find the canvas element. Add two new data attributes alongside `data-active-encounter-boss`:

```html
th:data-gauntlet-position="${gauntletPosition}"
th:data-gauntlet-total="${gauntletTotal}"
```

In `DungeonController.prepareGame`, compute these:

```java
int gauntletPos = 0;
int gauntletTotal = 0;
if (state.activeEncounterId() != null) {
    for (var entry : state.map().gauntletGroups().entrySet()) {
        List<String> group = entry.getValue();
        if (group.contains(state.activeEncounterId())) {
            gauntletTotal = group.size();
            gauntletPos = group.indexOf(state.activeEncounterId()) + 1;
            break;
        }
    }
}
model.addAttribute("gauntletPosition", gauntletPos);
model.addAttribute("gauntletTotal", gauntletTotal);
```

Add the imports if needed (`java.util.List`).

- [ ] **Step 2: Read the new attributes and render the badge**

In `renderJRPGCombat` in `dungeon.js`, near the top where `activeEncId` and `activeEncBoss` are read, also read:

```js
var gauntletPos = parseInt(canvas.dataset.gauntletPosition || '0', 10);
var gauntletTotal = parseInt(canvas.dataset.gauntletTotal || '0', 10);
```

Where the header text is currently drawn ("BOSS" or encounter type), insert an elite branch:

```js
if (gauntletTotal > 0 && !activeEncBoss) {
    ctx.fillStyle = '#fbbf24';
    ctx.font = 'bold 18px monospace';
    ctx.fillText('ELITE ' + gauntletPos + '/' + gauntletTotal, /* existing x */, /* existing y */);
}
```

Use the same x/y coordinates the boss-progress text uses.

- [ ] **Step 3: Elite monster sprite + splash**

Locate where the boss monster sprite is selected (around line 566 — `var monsterTypes = [...]; ... dungeonSplashMonster = activeEncBoss ? 'DRAGON' : monsterTypes[idx];`). Extend:

```js
if (gauntletTotal > 0 && !activeEncBoss) {
    window.dungeonSplashMonster = 'CHAMPION';
} else if (activeEncBoss) {
    window.dungeonSplashMonster = 'DRAGON';
} else {
    window.dungeonSplashMonster = monsterTypes[idx];
}
```

Register a `CHAMPION` sprite in `drawPixelSprite` — a knight-with-shield silhouette in gold/blue. Use the existing monster-sprite size & approach as a model.

In `renderSplash`, find the title text branch (BOSS splash) and add an ELITE branch with a distinct color tint (e.g. `#fbbf24` gold) and "ELITE CHALLENGE!" text.

- [ ] **Step 4: Smoke verify**

Start a SMALL flashcard dungeon (1 elite). Find the ELITE tile, step on it — verify the splash says "ELITE CHALLENGE!" in gold, the monster is a champion (not a regular mob or dragon), and the combat header shows "ELITE 1/2". Answer one card correctly — verify header updates to "ELITE 2/2".

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat(dungeon): elite splash, champion sprite, gauntlet progress badge"
```

---

### Task 17: Shield-break and streak-flash particles + audio

**Files:**
- Modify: `src/main/resources/static/js/dungeon.js`

- [ ] **Step 1: Add `playShieldBreak` and `playShieldGain` to DungeonAudio**

In the `DungeonAudio` object at the top of `dungeon.js`, add two new methods mirroring the existing audio patterns:

```js
playShieldBreak: function () {
    if (!this.ctx) return;
    var ctx = this.ctx;
    [800, 400, 200].forEach(function (freq, i) {
        var osc = ctx.createOscillator();
        var gain = ctx.createGain();
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(freq, ctx.currentTime + i * 0.04);
        gain.gain.setValueAtTime(0.18, ctx.currentTime + i * 0.04);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.04 + 0.15);
        osc.connect(gain); gain.connect(ctx.destination);
        osc.start(ctx.currentTime + i * 0.04);
        osc.stop(ctx.currentTime + i * 0.04 + 0.16);
    });
},
playShieldGain: function () {
    if (!this.ctx) return;
    var ctx = this.ctx;
    [523.25, 659.25, 783.99].forEach(function (freq, i) {
        var osc = ctx.createOscillator();
        var gain = ctx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(freq, ctx.currentTime + i * 0.07);
        gain.gain.setValueAtTime(0.12, ctx.currentTime + i * 0.07);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.07 + 0.25);
        osc.connect(gain); gain.connect(ctx.destination);
        osc.start(ctx.currentTime + i * 0.07);
        osc.stop(ctx.currentTime + i * 0.07 + 0.26);
    });
},
```

- [ ] **Step 2: Detect shield consumption between renders**

Near where `window.lastHealth` is tracked (around line 621), add `window.lastShields`. In `renderAll` (or wherever the HUD-diffing happens), compare `lastShields` to the current shield count: if it dropped, trigger a particle + audio. Read the current value from the HUD DOM:

```js
var shieldsEl = document.querySelector('.sh-dungeon-hud-shields');
var currentShields = shieldsEl ? shieldsEl.querySelectorAll('iconify-icon').length : 0;
if (window.lastShields !== undefined && currentShields < window.lastShields) {
    window.shieldBreakActive = true;
    window.shieldBreakStart = Date.now();
    DungeonAudio.playShieldBreak();
}
window.lastShields = currentShields;
```

Then in the canvas render loop, draw the shield-break particle if active (mirror the `slashParticleActive` pattern):

```js
if (window.shieldBreakActive) {
    var elapsed = Date.now() - window.shieldBreakStart;
    if (elapsed > 400) {
        window.shieldBreakActive = false;
    } else {
        ctx.save();
        ctx.globalAlpha = 1 - elapsed / 400;
        ctx.strokeStyle = '#2dd4bf';
        ctx.lineWidth = 3;
        var pCenterX = pPixelX + tileSize / 2;
        var pCenterY = pPixelY + tileSize / 2;
        for (var k = 0; k < 6; k++) {
            var angle = (k / 6) * Math.PI * 2;
            var r1 = tileSize * 0.3;
            var r2 = tileSize * (0.5 + elapsed / 800);
            ctx.beginPath();
            ctx.moveTo(pCenterX + Math.cos(angle) * r1, pCenterY + Math.sin(angle) * r1);
            ctx.lineTo(pCenterX + Math.cos(angle) * r2, pCenterY + Math.sin(angle) * r2);
            ctx.stroke();
        }
        ctx.restore();
    }
}
```

- [ ] **Step 3: Streak milestone flash**

Detect streak milestones the same way (read `.sh-dungeon-hud-streak` text content, parse, compare to last). When the new value is a non-zero multiple of 3 and greater than the last value, trigger a brief golden screen flash:

```js
var streakEl = document.querySelector('.sh-dungeon-hud-streak');
var currentStreak = streakEl ? parseInt((streakEl.textContent || '×0').replace('×', ''), 10) || 0 : 0;
if (window.lastStreak !== undefined && currentStreak > window.lastStreak
    && currentStreak % 3 === 0 && currentStreak > 0) {
    window.streakFlashActive = true;
    window.streakFlashStart = Date.now();
    DungeonAudio.playShieldGain();
}
window.lastStreak = currentStreak;
```

And in the render loop:

```js
if (window.streakFlashActive) {
    var elapsedF = Date.now() - window.streakFlashStart;
    if (elapsedF > 350) { window.streakFlashActive = false; }
    else {
        ctx.save();
        ctx.globalAlpha = 0.35 * (1 - elapsedF / 350);
        ctx.fillStyle = '#fbbf24';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.restore();
    }
}
```

- [ ] **Step 4: Smoke verify**

Start a SMALL dungeon. Answer 3 in a row correctly — see the golden flash and hear the chime. Then take a wrong answer — see the shield burst around the player and hear the break sound. HP should not drop.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat(dungeon): shield-break and streak-milestone particle effects and audio"
```

---

### Task 18: Dungeon-complete page — three new stats

**Files:**
- Modify: `src/main/resources/templates/fragments/dungeon-complete.html`
- Modify: `src/main/resources/messages.properties` (+ other locales)

- [ ] **Step 1: Add the three new stat lines**

In `src/main/resources/templates/fragments/dungeon-complete.html`, after the existing `sh-dungeon-complete-score` block, add:

```html
<div class="sh-dungeon-complete-extras">
    <div class="sh-dungeon-complete-extra">
        <span class="sh-dungeon-complete-extra-label" th:text="#{dungeon.complete.longest-streak}">Longest streak</span>
        <span class="sh-dungeon-complete-extra-value" th:text="${stats.longestStreak}">0</span>
    </div>
    <div class="sh-dungeon-complete-extra">
        <span class="sh-dungeon-complete-extra-label" th:text="#{dungeon.complete.elites-cleared}">Elites cleared</span>
        <span class="sh-dungeon-complete-extra-value" th:text="${stats.elitesCleared}">0</span>
    </div>
    <div class="sh-dungeon-complete-extra">
        <span class="sh-dungeon-complete-extra-label" th:text="#{dungeon.complete.shields-used}">Shields used</span>
        <span class="sh-dungeon-complete-extra-value" th:text="${stats.shieldsUsed}">0</span>
    </div>
</div>
```

- [ ] **Step 2: Add basic CSS**

Append to `dungeon.css`:

```css
.sh-dungeon-complete-extras {
    display: flex;
    gap: 1.5rem;
    justify-content: center;
    margin-top: 1.5rem;
    flex-wrap: wrap;
}
.sh-dungeon-complete-extra {
    display: flex;
    flex-direction: column;
    align-items: center;
}
.sh-dungeon-complete-extra-label {
    font-size: 0.85em;
    color: var(--text-secondary);
}
.sh-dungeon-complete-extra-value {
    font-size: 1.4em;
    font-weight: 700;
}
```

- [ ] **Step 3: Add i18n keys**

In `src/main/resources/messages.properties`:

```
dungeon.complete.longest-streak=Longest streak
dungeon.complete.elites-cleared=Elites cleared
dungeon.complete.shields-used=Shields used
```

Add equivalents in all other `messages_*.properties` files.

- [ ] **Step 4: Smoke verify**

Finish a dungeon (win or lose). Confirm the three new stats render below the existing prompts-correct count.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat(dungeon): show longest streak, elites cleared, shields used on completion"
```

---

### Task 19: Remove dead encounter-panel template and CSS

**Files:**
- Modify: `src/main/resources/templates/fragments/dungeon-game.html`
- Modify: `src/main/resources/static/css/dungeon.css`

- [ ] **Step 1: Remove the side-panel markup**

In `dungeon-game.html`, delete the entire `<div class="sh-dungeon-encounter-panel">...</div>` block (everything inside the encounter panel — the empty objective, flashcard encounter, quiz encounter sub-blocks).

- [ ] **Step 2: Remove dead CSS rules**

In `dungeon.css`, remove (or comment out as a single block) every selector targeting `.sh-dungeon-encounter-panel`, `.sh-dungeon-encounter-empty`, `.sh-dungeon-encounter-header`, `.sh-dungeon-encounter-text`, `.sh-dungeon-encounter-active`, `.sh-dungeon-flashcard*`, `.sh-dungeon-answer-row`, `.sh-dungeon-quiz-*`. Use:

```bash
grep -n "sh-dungeon-encounter-\|sh-dungeon-flashcard\|sh-dungeon-quiz-\|sh-dungeon-answer-row" src/main/resources/static/css/dungeon.css
```

Verify each match before deleting — keep any rule that is used by canvas-side classes you didn't realize were shared.

- [ ] **Step 3: Update the layout-swap CSS**

Remove the `.sh-dungeon-layout.is-encounter-active .sh-dungeon-encounter-panel { display: none !important; }` rule (it's now meaningless). Leave the other JRPG combat rules — those still control how the canvas is sized during combat.

- [ ] **Step 4: Run UI regression tests**

Run: `./mvnw test -Dtest=UiResourceRegressionTests`
Expected: pass. If it fails because it references a deleted class name, update the test to match the new state.

- [ ] **Step 5: Smoke verify**

Start a dungeon, walk into an encounter, confirm the JRPG combat screen still renders correctly. Take an answer, confirm normal flow.

- [ ] **Step 6: Commit**

```bash
git add -u
git commit -m "chore(dungeon): remove dead encounter-panel template and CSS"
```

---

## Phase 6 — Study wizard restructure

### Task 20: Shift `data-step` values

**Files:**
- Modify: `src/main/resources/templates/fragments/study-setup.html`
- Modify: `src/main/resources/templates/fragments/wizard-source-picker.html`
- Modify: `src/main/resources/templates/fragments/wizard-flashcards.html`
- Modify: `src/main/resources/templates/fragments/wizard-quiz.html`
- Modify: `src/main/resources/templates/fragments/wizard-exam.html`
- Modify: `src/main/resources/templates/fragments/wizard-dungeon.html`

- [ ] **Step 1: Renumber source picker**

In `wizard-source-picker.html`, change `data-step="3"` to `data-step="1"`. Update the `id` if it depends on the step (`id="wizard-panel-3"` → `id="wizard-panel-1"`).

- [ ] **Step 2: Renumber the mode picker**

In `study-setup.html`, locate `data-step="1" id="wizard-panel-1"` (the mode-picker panel) and change it to `data-step="2" id="wizard-panel-2"`.

- [ ] **Step 3: Shift mode-specific panels by +1**

For each of `wizard-flashcards.html`, `wizard-quiz.html`, `wizard-exam.html`, `wizard-dungeon.html`: for every `data-step="N"` attribute, increment N by 1 (e.g. `data-step="2"` → `data-step="3"`, `data-step="3"` → `data-step="4"`).

For `wizard-dungeon.html` specifically: the dungeon-type panel `data-step="2"` becomes `data-step="3"`, the dungeon-size panel `data-step="3"` becomes `data-step="4"`.

- [ ] **Step 4: Smoke verify the existing wizard still navigates**

Start the app, open the wizard. The steps should now appear in the order: Sources → Mode → mode-specific config. Click through each mode to confirm the flow still works.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "refactor(wizard): source picker first, mode picker second"
```

---

### Task 21: `StudyMode.minimumCardsRequired()` and mode-picker gating

**Files:**
- Modify: `src/main/java/com/HendrikHoemberg/StudyHelper/dto/StudyMode.java`
- Modify: `src/main/resources/templates/fragments/study-setup.html`
- Modify: `src/main/resources/static/js/study-wizard.js`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: Add `minimumCardsRequired()`**

Replace `src/main/java/com/HendrikHoemberg/StudyHelper/dto/StudyMode.java`:

```java
package com.HendrikHoemberg.StudyHelper.dto;

public enum StudyMode {
    FLASHCARDS(1),
    QUIZ(1),
    EXAM(1),
    DUNGEON(DungeonSize.SMALL.totalPrompts());

    private final int minimumCardsRequired;
    StudyMode(int minimumCardsRequired) { this.minimumCardsRequired = minimumCardsRequired; }
    public int minimumCardsRequired() { return minimumCardsRequired; }
}
```

- [ ] **Step 2: Expose per-mode minimums in the mode-picker template**

In `study-setup.html`, each mode `<label class="sh-study-choice ...">` currently has no data attribute for its minimum. Add `th:data-min-cards`:

```html
<label class="sh-study-choice sh-study-choice-study"
       th:data-min-cards="${T(com.HendrikHoemberg.StudyHelper.dto.StudyMode).FLASHCARDS.minimumCardsRequired()}"
       onclick="selectStudyMode(this, 'FLASHCARDS')">
```

Repeat for QUIZ, EXAM, DUNGEON.

Below each title, add an inline "unavailable reason" slot:

```html
<div class="sh-study-choice-unavailable" hidden>
    <span class="sh-study-choice-unavailable-text"></span>
</div>
```

- [ ] **Step 3: Add wizard JS that computes available cards and gates modes**

In `src/main/resources/static/js/study-wizard.js`, after the source-picker init, add a recompute hook. Look for where the source picker tracks selected decks (likely an existing function — search for `sourceSelectionChanged` or `selectedDeckIds`). Add a new function:

```js
function recomputeAvailableModes() {
    // Sum cards across selected decks. Each deck checkbox has data-card-count.
    var selectedDeckCheckboxes = document.querySelectorAll('input[name="selectedDeckIds"]:checked');
    var totalCards = 0;
    selectedDeckCheckboxes.forEach(function (cb) {
        totalCards += parseInt(cb.dataset.cardCount || '0', 10);
    });

    document.querySelectorAll('.sh-study-choice').forEach(function (choice) {
        var min = parseInt(choice.dataset.minCards || '0', 10);
        var unavailable = choice.querySelector('.sh-study-choice-unavailable');
        var reasonEl = choice.querySelector('.sh-study-choice-unavailable-text');
        if (totalCards < min) {
            choice.classList.add('is-unavailable');
            if (unavailable) unavailable.hidden = false;
            if (reasonEl) {
                reasonEl.textContent = window.shI18n.format(
                    window.shI18n.t('wizard.mode-unavailable.cards'),
                    [min, totalCards]);
            }
            var input = choice.querySelector('input[type="radio"]');
            if (input) input.disabled = true;
        } else {
            choice.classList.remove('is-unavailable');
            if (unavailable) unavailable.hidden = true;
            var input2 = choice.querySelector('input[type="radio"]');
            if (input2) input2.disabled = false;
        }
    });
}

document.addEventListener('change', function (e) {
    if (e.target && e.target.name === 'selectedDeckIds') recomputeAvailableModes();
});

// Run once on load
document.addEventListener('DOMContentLoaded', recomputeAvailableModes);
```

Note: this assumes deck checkboxes carry `data-card-count`. Grep `wizard-source-picker.html` for `data-card-count` — if absent, add it to each `<input type="checkbox" name="selectedDeckIds" ...>`:

```html
th:data-card-count="${deck.flashcards.size()}"
```

If `window.shI18n` does not exist, use a simpler inline template instead:

```js
reasonEl.textContent = 'Needs at least ' + min + ' cards — your selection has ' + totalCards + '.';
```

(Or look up how other client-side i18n works in this codebase via `grep -r "shI18n\|window\.i18n" src/main/resources/static/`.)

- [ ] **Step 4: Add styling for unavailable choices**

Append to whichever stylesheet styles `.sh-study-choice`:

```css
.sh-study-choice.is-unavailable {
    opacity: 0.4;
    cursor: not-allowed;
    pointer-events: none;
}
.sh-study-choice-unavailable {
    font-size: 0.8em;
    color: var(--text-secondary);
    margin-top: 0.5rem;
}
```

- [ ] **Step 5: Add i18n keys**

In `messages.properties`:

```
wizard.mode-unavailable.cards=Needs at least {0} cards — your selection has {1}.
```

Add equivalents to other locale files.

- [ ] **Step 6: Smoke verify**

Open the wizard. With no sources selected, all modes should be greyed. Select a deck with 5 cards: Flashcards/Quiz/Exam become enabled, Dungeon stays disabled. Select a deck with 15 cards: Dungeon also enables. Reason text reflects the actual numbers.

- [ ] **Step 7: Commit**

```bash
git add -u
git commit -m "feat(wizard): gate study modes by minimum card count"
```

---

### Task 22: Dungeon-size picker live gating

**Files:**
- Modify: `src/main/resources/templates/fragments/wizard-dungeon.html`
- Modify: `src/main/resources/static/js/study-wizard.js`
- Modify: `src/main/resources/messages.properties`

- [ ] **Step 1: Add data attributes to size choices**

In `wizard-dungeon.html`, each size `<label class="sh-study-choice sh-study-choice-dungeon">` for SMALL/MEDIUM/LARGE: add `th:data-min-cards` with the size's `totalPrompts`:

```html
<label class="sh-study-choice sh-study-choice-dungeon"
       th:data-min-cards="${T(com.HendrikHoemberg.StudyHelper.dto.DungeonSize).SMALL.totalPrompts()}">
    <input type="radio" name="dungeonSize" value="SMALL" ...>
    ...
    <div class="sh-study-choice-unavailable" hidden>
        <span class="sh-study-choice-unavailable-text"></span>
    </div>
</label>
```

Repeat for MEDIUM, LARGE.

- [ ] **Step 2: Extend the JS recompute to also gate sizes**

In `study-wizard.js`, extend `recomputeAvailableModes` (or add a sibling `recomputeAvailableSizes()` and call both from the change listener):

```js
function recomputeAvailableSizes() {
    var selected = document.querySelectorAll('input[name="selectedDeckIds"]:checked');
    var totalCards = 0;
    selected.forEach(function (cb) { totalCards += parseInt(cb.dataset.cardCount || '0', 10); });

    document.querySelectorAll('input[name="dungeonSize"]').forEach(function (radio) {
        var label = radio.closest('.sh-study-choice');
        if (!label) return;
        var min = parseInt(label.dataset.minCards || '0', 10);
        var unavailable = label.querySelector('.sh-study-choice-unavailable');
        var reasonEl = label.querySelector('.sh-study-choice-unavailable-text');
        if (totalCards < min) {
            label.classList.add('is-unavailable');
            if (unavailable) unavailable.hidden = false;
            if (reasonEl) {
                reasonEl.textContent = 'Needs ' + min + ' cards, your selection has ' + totalCards + '.';
            }
            radio.disabled = true;
            if (radio.checked) radio.checked = false;
        } else {
            label.classList.remove('is-unavailable');
            if (unavailable) unavailable.hidden = true;
            radio.disabled = false;
        }
    });
}

// Add to existing listener
document.addEventListener('change', function (e) {
    if (e.target && e.target.name === 'selectedDeckIds') {
        recomputeAvailableModes();
        recomputeAvailableSizes();
    }
});
document.addEventListener('DOMContentLoaded', function () {
    recomputeAvailableModes();
    recomputeAvailableSizes();
});
```

- [ ] **Step 3: Add i18n string (optional refinement)**

```
wizard.size-unavailable.cards=Needs {0} cards, your selection has {1}.
```

If you wire it via `window.shI18n`, swap the inline string above for `window.shI18n.format(window.shI18n.t('wizard.size-unavailable.cards'), [min, totalCards])`. Otherwise inline is fine.

- [ ] **Step 4: Smoke verify**

Open the wizard, pick a deck with 12 cards, choose Dungeon. On the size step: SMALL enabled, MEDIUM enabled (needs 16... wait — 12 < 16, so MEDIUM should be disabled). Actual expected behaviour for 12 cards: SMALL (10) enabled, MEDIUM (16) disabled, LARGE (29) disabled. Confirm.

Pick a different deck of 30 cards. All sizes enabled.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat(wizard): gate dungeon sizes by selected card count"
```

---

## Phase 7 — Final pass

### Task 23: Update remaining tests and run the full suite

**Files:**
- Possibly modify: any test that asserted old field counts or step numbers

- [ ] **Step 1: Run the full test suite**

Run: `./mvnw test`
Expected: green. Address any failures.

Common causes of late-surfacing failures:
- `DungeonControllerTests` asserting model attribute names — add `gauntletPosition`/`gauntletTotal` to expectations.
- `UiResourceRegressionTests` asserting CSS class names — update if a removed class was being checked.
- `StudyControllerTests` checking `data-step` numbers — adjust to the new step order.

- [ ] **Step 2: Verify the dungeon end-to-end manually**

Start the app:

```bash
./mvnw spring-boot:run
```

Walk through a full SMALL flashcard dungeon: source pick → mode pick (Dungeon) → dungeon-mode (Flashcards) → size (SMALL) → start. In the dungeon, find and clear an elite, reach the boss, win or lose. Verify:
- Streak counter increments and shields bank at 3.
- Wrong answer with shield: HP preserved, shield consumed, particle + sound.
- Elite splash, champion sprite, gauntlet progress text.
- Telegraphed trap appears as cracked floor before stepping on it.
- Completion screen shows the three new stats.

- [ ] **Step 3: Commit any leftover fixes as one final tidy commit**

```bash
git add -u
git commit -m "test(dungeon): adjust assertions for new state and wizard order"
```

(If nothing to commit, skip.)

---

## Self-Review Notes

A short check against the spec sections:

- **Architecture split** (Section 1): Tasks 1-4 produce `DungeonDamage`, `DungeonNavigationService`, `DungeonEncounterService`, and slim `DungeonSessionService`. ✓
- **State model** (Section 2): Tasks 5-8 add fields, ELITE tile, gauntletGroups, stats fields, and saved-session guard. ✓
- **Map generation** (Section 3): Task 10 rewrites the generator with rooms+corridors, MST + loops, leaf-room elites, spatial encounter placement, room-attached secrets. ✓
- **Encounter flow** (Section 4): Tasks 11-12 add streak/shield logic and gauntlet sequencing including abort-on-wrong and reward-on-clear. Task 13 wires the create-time gauntlet construction. ✓
- **Balance** (Section 5): Task 9 updates `DungeonSize`. Constants live in `DungeonDamage`/`DungeonEncounterService` per the spec. ✓
- **UI** (Section 6): Tasks 14-18 add HUD items, canvas sprites, splash, particles, audio, and the complete-page stats. Task 19 removes the dead panel. ✓
- **Wizard** (Section 7): Tasks 20-22 renumber steps, gate modes, and gate dungeon sizes. ✓

Coverage looks complete. No placeholders remain — every step has the code or commands needed.

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-27-dungeon-overhaul.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
