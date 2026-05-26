# Pixel Dungeon Study Mode Design

## Overview

Add Dungeon as a separate study mode beside Flashcards, AI Quiz, and Exam. Dungeon is a turn-based pixel-art dungeon crawler that reuses the existing flashcard and AI quiz systems while presenting study prompts as combat encounters.

The first version supports two dungeon run types:

- Flashcard Dungeon: practice-only flashcard encounters from selected decks.
- AI Quiz Dungeon: AI-generated quiz encounters generated once at run start from selected decks.

Dungeon runs are practice-only for flashcards. They record dungeon results, but they do not update flashcard SRS scheduling fields.

## Gameplay

Dungeon uses a true tile grid with fog-of-war. The player moves one tile per turn with keyboard arrows or WASD on desktop and directional controls on mobile. Movement reveals nearby tiles. Marked event tiles trigger encounters when the player steps on them.

Each run generates one compact dungeon floor with rooms, corridors, branching paths, normal encounter tiles, treasure or heal tiles, and one boss tile. Normal encounter tiles each use one learning prompt. The boss tile starts a sequence of reserved prompts in succession, with no movement between boss prompts.

Flashcard encounters show the card front, allow the user to reveal the back, then ask whether they got it or missed it. AI quiz encounters use the existing quiz question model for multiple-choice, multiple-select, and true/false questions. Correct answers clear or damage enemies. Missed or wrong answers cost health.

The run ends when the boss sequence is cleared or when health reaches zero.

## Dungeon Size

Dungeon setup offers Small, Medium, and Large sizes. Size controls the number of learning prompts, including boss prompts.

| Size | Total prompts | Normal encounter tiles | Boss prompts |
| --- | ---: | ---: | ---: |
| Small | 8 | 6 | 2 |
| Medium | 12 | 9 | 3 |
| Large | 20 | 15 | 5 |

The boss is one map tile, but entering it starts the reserved boss prompt sequence. This keeps size tied to study workload rather than map tile count.

Available sizes are restricted by usable selected content:

- Fewer than 8 usable items blocks dungeon start.
- 8-11 usable items allows Small.
- 12-19 usable items allows Small and Medium.
- 20 or more usable items allows Small, Medium, and Large.

For Flashcard Dungeon, usable items are selected flashcards. For AI Quiz Dungeon, usable items are also the selected source flashcards for the purpose of enabling or disabling sizes; after a size is chosen, that size's total prompt count becomes the quiz question count requested at generation time.

## Architecture

Add Dungeon as a vertical slice rather than merging game logic into existing flashcard or quiz controllers.

Core additions:

- `StudyMode.DUNGEON`
- `SavedSessionType.DUNGEON`
- `DungeonMode` enum with `FLASHCARDS` and `AI_QUIZ`
- `DungeonSize` enum with Small, Medium, and Large prompt counts
- DTOs for `DungeonConfig`, `DungeonSessionState`, `DungeonMap`, `DungeonTile`, `DungeonEncounter`, and `DungeonRunStats`
- `DungeonSessionService` for validation, content selection, AI quiz generation, map generation, movement, fog updates, encounter answer handling, boss progression, completion, and defeat
- `DungeonController` for setup/start/resume/runtime endpoints
- Thymeleaf fragments for the game shell, encounter panel, and completion screen
- `dungeon.js` and `dungeon.css` for canvas rendering, input controls, fog display, and pixel-art styling

The server owns authoritative dungeon state. The canvas renders server state and sends movement or answer intents. This keeps refresh, resume, saved sessions, logging, and validation consistent with the existing app.

## Reused Systems

Dungeon should reuse existing app services where possible:

- Deck ownership and ordering from `DeckService`
- Flashcard lookup and flattening from `FlashcardService`
- AI quiz generation through the existing quiz generation path
- AI quota checks through `AiRequestQuotaService`
- Saved-session conflict, resume, and discard patterns through `SavedSessionService`
- Completion and abandoned-run logging through `StudyLogService`
- Study wizard conventions and source picker behavior from the current study setup flow

Dungeon must not call the flashcard SRS scheduling path when answering flashcard encounters.

## Data Flow

### Start Flow

1. The user chooses Dungeon in the study wizard.
2. The user selects Flashcard Dungeon or AI Quiz Dungeon.
3. The user selects Small, Medium, or Large.
4. The user selects source decks.
5. The server validates sources and size availability.
6. For Flashcard Dungeon, the server builds a practice encounter pool from selected deck flashcards.
7. For AI Quiz Dungeon, the server generates all quiz questions once, records quota use, and builds the encounter pool.
8. The server generates the grid, places normal encounter tiles, reserves the boss prompt sequence, stores `DungeonSessionState` in HTTP session and saved session, and renders the dungeon.

### Runtime Flow

1. The client sends a movement intent to the server.
2. The server validates the move, updates player position, reveals fog, and returns updated state.
3. If the destination has an unresolved encounter, movement locks and the encounter panel opens.
4. The client submits an answer.
5. The server grades the answer, updates health and score, marks the encounter resolved, advances boss sequence when applicable, and returns the next state.
6. On win or defeat, the server discards the saved session and records the dungeon result.

### Resume Flow

1. `GET /dungeon/resume` loads saved dungeon state.
2. For Flashcard Dungeon, the server reconciles missing flashcards.
3. If enough unresolved content remains, the run resumes.
4. If the run can no longer continue, it is discarded with a clear message.

## UI And Controls

The study wizard first step becomes a four-card mode picker:

- Flashcards
- AI Quiz
- Exam
- Dungeon

On desktop this uses a balanced 2x2 grid to avoid stretching the wizard card row. On tablet and mobile it stacks responsively using the existing card style without squeezing text.

Selecting Dungeon shows Dungeon-specific settings before source selection:

- Dungeon mode: Flashcards or AI Quiz
- Dungeon size: Small, Medium, Large
- Disabled size options with explanatory text when selected decks do not contain enough usable cards
- For AI Quiz Dungeon, the existing quiz format, difficulty, and optional AI instructions controls

The run screen uses a focused game layout:

- Canvas map at the top or left, depending on viewport
- Encounter panel beside or below the map
- Compact HUD for health, score, floor progress, and objective
- Directional buttons always visible on mobile
- Keyboard support for arrows and WASD on desktop
- Pixel-art styling with a low-resolution tile grid, crisp scaling, limited palette, and simple player, enemy, chest, and boss sprites

During movement, the encounter panel shows the current tile state or objective. During an encounter, movement controls are disabled. During the boss sequence, the panel shows boss progress such as "Boss 2 / 5".

## Error Handling

Setup validation:

- No decks selected shows the same style of wizard error as existing study flows.
- Too few usable cards or questions blocks start with a clear "need at least 8" message.
- Medium and Large are disabled before submit when possible and validated again server-side.
- AI quota exceeded reuses existing AI generation error handling and quota refresh behavior.
- AI generation failure returns to Dungeon setup with selected options preserved.

Runtime handling:

- Invalid movement, moving into walls, or moving while an encounter is active returns the current state without advancing.
- Invalid or stale answer submissions are rejected without changing health or score.
- Refresh or browser restart resumes from saved `DungeonSessionState`.
- Deleted flashcards during a saved Flashcard Dungeon are reconciled on resume.
- If too many flashcards are missing to continue, the run is discarded with a clear message.
- Health reaching zero marks the run defeated, discards the saved session, and records the result.
- Clearing the boss marks the run won, discards the saved session, and records the result.

## Testing

Unit tests:

- Dungeon size availability from usable item count.
- Boss prompt count and normal encounter split for each size.
- Dungeon map invariants: entrance exists, boss reachable, all encounter tiles reachable, encounter count matches size.
- Movement rules: walls block movement, valid moves update position, fog reveals, encounter tiles lock movement.
- Flashcard answer handling updates health, score, and encounter state without updating SRS scheduling fields.
- AI quiz answer handling grades single, multiple-select, and true/false questions correctly.
- Win and defeat transitions discard saved sessions and record study logs.
- Saved-session serialization and deserialization for dungeon state.
- Study log creation for completed and abandoned dungeon runs.

Controller tests:

- Dungeon start validates missing decks and insufficient cards.
- Flashcard Dungeon start does not use AI quota.
- AI Quiz Dungeon start checks AI quota and uses selected dungeon size as question count.
- Resume loads saved dungeon state.
- Runtime endpoints reject stale or invalid actions.

UI and browser verification:

- Study wizard first step renders as a 2x2 mode grid on desktop with four modes.
- Dungeon mode settings appear after selecting Dungeon.
- Size options disable correctly based on selected deck content.
- Canvas game shell renders nonblank on desktop and mobile.
- Mobile directional controls are visible and do not overlap the encounter panel.

## Open Implementation Notes

Keep the first version scoped to one floor per run. Additional floors, inventory, persistent character progression, richer rewards, or animated combat can be layered later after the core learning loop, saved-session behavior, and mobile controls are proven.
