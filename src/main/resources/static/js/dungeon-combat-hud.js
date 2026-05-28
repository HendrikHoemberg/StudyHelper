// dungeon-combat-hud.js — combat presentation: the encounter splash screen and
// the JRPG combat HUD loop. Reads the current encounter state from the DOM and
// hands a plain state object to the (pure) renderer, then publishes the returned
// interactive option boxes for the input layer to hit-test.
var DungeonCombatHud = (function () {

    var combatLoopRunning = false;
    var splashPlayedForEncounter = null;
    var splashStartTime = 0;

    // Build the combat render state from the live DOM / canvas dataset.
    function readCombatState(canvas) {
        var healthText = document.querySelector('.sh-dungeon-hud-health')?.textContent || '5/5';
        var hp = healthText.split('/');
        var health = parseInt(hp[0], 10);
        if (isNaN(health)) health = 5;
        var healthCap = parseInt(hp[1], 10);
        if (isNaN(healthCap)) healthCap = 5;

        var scoreEl = document.querySelector('.sh-dungeon-hud-score');
        var score = parseInt(scoreEl ? scoreEl.textContent : '0', 10);
        if (isNaN(score)) score = 0;

        var progEl = document.querySelector('.sh-dungeon-hud-progress');
        var progress = progEl ? progEl.textContent : '0/0';

        var state = {
            health: health,
            healthCap: healthCap,
            score: score,
            progress: progress,
            boss: canvas.dataset.activeEncounterBoss === 'true',
            gauntletPos: parseInt(canvas.dataset.gauntletPosition || '0', 10),
            gauntletTotal: parseInt(canvas.dataset.gauntletTotal || '0', 10),
            monster: canvas.dataset.activeEncounterMonster || '',
            hoveredOptionIndex: window.dungeonHoveredOptionIndex,
            mode: null,
            flashcard: { front: '', back: '', revealed: false },
            quiz: { question: '', options: [] }
        };

        if (document.querySelector('.sh-dungeon-flashcard')) {
            state.mode = 'flashcard';
            state.flashcard.front = document.querySelector('.sh-dungeon-flashcard-front .sh-dungeon-flashcard-text')?.textContent || "";
            state.flashcard.back = document.querySelector('.sh-dungeon-flashcard-back .sh-dungeon-flashcard-text')?.textContent || "";
            state.flashcard.revealed = document.querySelector('.sh-dungeon-flashcard-details')?.open === true;
        } else if (document.querySelector('.sh-dungeon-quiz-form')) {
            state.mode = 'quiz';
            state.quiz.question = document.querySelector('.sh-dungeon-quiz-question')?.textContent || "";
            state.quiz.options = Array.from(document.querySelectorAll('.sh-dungeon-quiz-option')).map(function (opt) {
                var input = opt.querySelector('input');
                return {
                    text: opt.querySelector('span')?.textContent || "",
                    checked: input ? input.checked : false,
                    isCheckbox: input ? input.type === 'checkbox' : false
                };
            });
        }
        return state;
    }

    function runSplashLoop() {
        if (!window.dungeonSplashActive) return;
        var canvas = document.getElementById('dungeon-room-canvas');
        if (!canvas) {
            window.dungeonSplashActive = false;
            return;
        }
        var activeEncounterId = canvas.dataset.activeEncounterId;
        if (!activeEncounterId || activeEncounterId.length === 0) {
            window.dungeonSplashActive = false;
            return;
        }

        var elapsed = Date.now() - splashStartTime;
        if (elapsed >= 1500) {
            window.dungeonSplashActive = false;
            DungeonEngine.render();
            return;
        }

        DungeonRenderer.renderSplash({
            monster: window.dungeonSplashMonster,
            boss: canvas.dataset.activeEncounterBoss === 'true',
            gauntletTotal: parseInt(canvas.dataset.gauntletTotal || '0', 10)
        });
        requestAnimationFrame(runSplashLoop);
    }

    function runCombatLoop() {
        if (!combatLoopRunning) return;
        var canvas = document.getElementById('dungeon-room-canvas');
        if (!canvas) {
            combatLoopRunning = false;
            return;
        }
        var activeEncounterId = canvas.dataset.activeEncounterId;
        if (!activeEncounterId || activeEncounterId.length === 0) {
            combatLoopRunning = false;
            DungeonEngine.render();
            return;
        }

        window.dungeonOptionBoxes = DungeonRenderer.renderCombat(readCombatState(canvas)) || [];

        requestAnimationFrame(runCombatLoop);
    }

    // Drive the active encounter: play the entrance splash once, then run the
    // combat HUD loop. Called by the engine when an active encounter is present.
    function runActiveEncounter(canvas) {
        var activeEncounterId = canvas.dataset.activeEncounterId;
        if (!activeEncounterId || activeEncounterId.length === 0) return;

        // Splash entrance screen — played once per encounter.
        if (splashPlayedForEncounter !== activeEncounterId) {
            window.dungeonSplashActive = true;
            splashPlayedForEncounter = activeEncounterId;
            splashStartTime = Date.now();

            var activeEncBoss = canvas.dataset.activeEncounterBoss === 'true';
            // Monster type is supplied by the server (DungeonEncounter.monsterType):
            // DRAGON for bosses, CHAMPION for elite gauntlets, else a per-encounter
            // creature. Replaces the old encounterId hashCode % 4 heuristic.
            window.dungeonSplashMonster = canvas.dataset.activeEncounterMonster
                || (activeEncBoss ? 'DRAGON' : 'SLIME');

            DungeonAudio.playBattleStart();
            runSplashLoop();
            return;
        }

        if (window.dungeonSplashActive) {
            return;
        }

        if (!combatLoopRunning) {
            combatLoopRunning = true;
            runCombatLoop();
        }
    }

    // Stop the combat loop; returns whether it had been running (used by the
    // engine to detect a freshly-cleared encounter -> victory transition).
    function stopCombat() {
        var wasRunning = combatLoopRunning;
        combatLoopRunning = false;
        return wasRunning;
    }

    return {
        runActiveEncounter: runActiveEncounter,
        stopCombat: stopCombat
    };
})();
