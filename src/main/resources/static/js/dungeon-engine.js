// dungeon-engine.js — orchestrator. Wires the dungeon modules together, owns the
// init / HTMX re-render entry point, and dispatches each render between the
// exploration loop (dungeon-exploration.js) and the combat HUD (dungeon-combat-hud.js).
// Depends on: dungeon-sprites.js, dungeon-audio.js, dungeon-minimap.js,
//             dungeon-renderer.js, dungeon-exploration.js, dungeon-combat-hud.js,
//             dungeon-input.js (all loaded as globals).
var DungeonEngine = (function () {

    // Decide what to draw based on the current canvas state: an active encounter
    // runs the combat HUD, otherwise we are exploring.
    function render() {
        var canvas = document.getElementById('dungeon-room-canvas');
        if (!canvas) return;

        var activeEncounterId = canvas.dataset.activeEncounterId;
        if (activeEncounterId && activeEncounterId.length > 0) {
            DungeonExploration.stopLoop();
            DungeonCombatHud.runActiveEncounter(canvas);
            return;
        }

        var justFinishedCombat = DungeonCombatHud.stopCombat();
        DungeonExploration.enterExploration(canvas, justFinishedCombat);
    }

    var drawQueued = false;
    function triggerDraw() {
        if (drawQueued) return;
        drawQueued = true;
        requestAnimationFrame(function () {
            drawQueued = false;
            init();
        });
    }

    function init() {
        DungeonRenderer.init();   // (re)bind the canvas/context, incl. after HTMX swaps
        DungeonMinimap.draw();
        render();
        DungeonInput.init();
    }

    // Auto unlock audio context on initial gestures
    document.addEventListener('click', function () { DungeonAudio.init(); });
    document.addEventListener('keydown', function () { DungeonAudio.init(); });

    // Exposed for the input layer, which re-renders after answer/reveal toggles.
    window.dungeonTriggerDraw = triggerDraw;

    if (typeof htmx !== 'undefined' && htmx) {
        htmx.onLoad(init);
    } else {
        document.addEventListener('DOMContentLoaded', init);
    }

    return {
        render: render,
        init: init,
        triggerDraw: triggerDraw
    };
})();
