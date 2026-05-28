(function () {
    // Auto unlock audio context on initial gestures
    document.addEventListener('click', function () { DungeonAudio.init(); });
    document.addEventListener('keydown', function () { DungeonAudio.init(); });


    // ============================================================
    // Core Rendering & States
    // ============================================================
    function submitMove(direction) {
        htmx.ajax('POST', '/dungeon/move', {
            target: '#dungeon-session-content',
            swap: 'outerHTML',
            values: { direction: direction }
        });
    }

    // ============================================================
    // 2D Interactive Top-Down Exploration Engine
    // ============================================================
    // Exploration Room Item States
    var currentRoomItems = {
        roomId: null,
        pots: [], // {x, y, broken: false, shatterTime: 0}
        coins: [], // {x, y, vx, vy, collected: false, claimable: false, type: 'COIN' or 'SHIELD'}
        remains: null // {type: 'SLIME'/'SKELETON'..., x, y}
    };

    function spawnPotsForRoom(roomId) {
        var pots = [];
        var hash = 0;
        for (var i = 0; i < roomId.length; i++) {
            hash = roomId.charCodeAt(i) + ((hash << 5) - hash);
        }
        var rng = Math.abs(hash);
        var numPots = (rng % 2) + 2; // 2 or 3 pots
        
        var corners = [
            { minX: 160, maxX: 260, minY: 160, maxY: 240 },
            { minX: 700, maxX: 800, minY: 160, maxY: 240 },
            { minX: 160, maxX: 260, minY: 480, maxY: 560 },
            { minX: 700, maxX: 800, minY: 480, maxY: 560 }
        ];
        
        for (var i = corners.length - 1; i > 0; i--) {
            var j = (rng + i) % (i + 1);
            var temp = corners[i];
            corners[i] = corners[j];
            corners[j] = temp;
        }
        
        for (var i = 0; i < numPots; i++) {
            var zone = corners[i];
            var px = zone.minX + ((rng + i * 17) % (zone.maxX - zone.minX));
            var py = zone.minY + ((rng + i * 31) % (zone.maxY - zone.minY));
            pots.push({ x: px, y: py, broken: false, shatterTime: 0 });
        }
        return pots;
    }

    var playerX = 480;
    var playerY = 352;
    var playerSpeed = 4.5;
    window.dungeonActiveKeys = {};
    var loopRunning = false;
    var combatLoopRunning = false;
    var animationFrameId = null;
    window.dungeonCenterpieceInteracted = false;
    window.dungeonLastRoomId = null;

    function triggerRoomTransition(dir) {
        loopRunning = false;
        if (animationFrameId) {
            cancelAnimationFrame(animationFrameId);
            animationFrameId = null;
        }
        window.dungeonLastMoveDirection = dir;
        DungeonAudio.playMove();
        submitMove(dir);
    }

    function startExplorationLoop() {
        if (animationFrameId) {
            cancelAnimationFrame(animationFrameId);
        }
        animationFrameId = requestAnimationFrame(updateAndRenderExploration);
    }

    function updateAndRenderExploration() {
        if (!loopRunning) return;

        var canvas = document.getElementById('dungeon-room-canvas');
        if (!canvas) {
            loopRunning = false;
            return;
        }

        var activeEncounterId = canvas.dataset.activeEncounterId;
        if (activeEncounterId && activeEncounterId.length > 0) {
            loopRunning = false;
            renderRoomCanvas();
            return;
        }

        // 1. Calculate next position based on active keys
        var dx = 0;
        var dy = 0;
        if (window.dungeonActiveKeys['ArrowUp'] || window.dungeonActiveKeys['w']) dy -= 1;
        if (window.dungeonActiveKeys['ArrowDown'] || window.dungeonActiveKeys['s']) dy += 1;
        if (window.dungeonActiveKeys['ArrowLeft'] || window.dungeonActiveKeys['a']) dx -= 1;
        if (window.dungeonActiveKeys['ArrowRight'] || window.dungeonActiveKeys['d']) dx += 1;

        if (dx !== 0 && dy !== 0) {
            // Normalize diagonal speed
            dx *= 0.7071;
            dy *= 0.7071;
        }

        var nextX = playerX + dx * playerSpeed;
        var nextY = playerY + dy * playerSpeed;

        // Door layout configurations mapped from interactive nav buttons
        var hasUpDoor = !document.querySelector('.sh-dungeon-dir[value="UP"]')?.disabled;
        var hasDownDoor = !document.querySelector('.sh-dungeon-dir[value="DOWN"]')?.disabled;
        var hasLeftDoor = !document.querySelector('.sh-dungeon-dir[value="LEFT"]')?.disabled;
        var hasRightDoor = !document.querySelector('.sh-dungeon-dir[value="RIGHT"]')?.disabled;

        var minX = 96;
        var maxX = 864; // 1024 - 96 - 64 (player width)
        var minY = 96;
        var maxY = 608; // 768 - 96 - 64 (player height)

        // Wall collisions & door transitions
        if (nextX < minX) {
            if (hasLeftDoor && nextY >= 280 && nextY <= 420) {
                if (nextX < 32) {
                    triggerRoomTransition('LEFT');
                    return;
                }
            } else {
                nextX = minX;
            }
        }
        if (nextX > maxX) {
            if (hasRightDoor && nextY >= 280 && nextY <= 420) {
                if (nextX > 928) {
                    triggerRoomTransition('RIGHT');
                    return;
                }
            } else {
                nextX = maxX;
            }
        }
        if (nextY < minY) {
            if (hasUpDoor && nextX >= 410 && nextX <= 550) {
                if (nextY < 32) {
                    triggerRoomTransition('UP');
                    return;
                }
            } else {
                nextY = minY;
            }
        }
        if (nextY > maxY) {
            if (hasDownDoor && nextX >= 410 && nextX <= 550) {
                if (nextY > 672) {
                    triggerRoomTransition('DOWN');
                    return;
                }
            } else {
                nextY = maxY;
            }
        }

        playerX = nextX;
        playerY = nextY;

        // Pot collision & smashing
        for (var i = 0; i < currentRoomItems.pots.length; i++) {
            var pot = currentRoomItems.pots[i];
            if (pot.broken) continue;
            
            var potCenterX = pot.x + 24;
            var potCenterY = pot.y + 24;
            var playerCenterX = playerX + 32;
            var playerCenterY = playerY + 32;
            var dist = Math.sqrt(Math.pow(playerCenterX - potCenterX, 2) + Math.pow(playerCenterY - potCenterY, 2));
            
            if (dist < 40) {
                pot.broken = true;
                pot.shatterTime = Date.now();
                DungeonAudio.playSlash(); // Shatter sound
                
                var rand = Math.random();
                if (rand < 0.60) {
                    currentRoomItems.coins.push({
                        x: potCenterX,
                        y: potCenterY,
                        vx: (Math.random() * 2 - 1) * 2,
                        vy: (Math.random() * 2 - 1) * 2,
                        collected: false,
                        claimable: true,
                        type: 'COIN'
                    });
                } else if (rand < 0.65) {
                    currentRoomItems.coins.push({
                        x: potCenterX,
                        y: potCenterY,
                        vx: (Math.random() * 2 - 1) * 2,
                        vy: (Math.random() * 2 - 1) * 2,
                        collected: false,
                        claimable: true,
                        type: 'SHIELD'
                    });
                }
            }
        }

        // Update coins/shields physics and attraction
        for (var i = 0; i < currentRoomItems.coins.length; i++) {
            var item = currentRoomItems.coins[i];
            if (item.collected) continue;
            
            item.x += item.vx;
            item.y += item.vy;
            item.vx *= 0.88;
            item.vy *= 0.88;
            
            if (item.x < 110 || item.x > 910) {
                item.vx = -item.vx;
                item.x = Math.max(110, Math.min(910, item.x));
            }
            if (item.y < 110 || item.y > 650) {
                item.vy = -item.vy;
                item.y = Math.max(110, Math.min(650, item.y));
            }
            
            var playerCenterX = playerX + 32;
            var playerCenterY = playerY + 32;
            var dx = playerCenterX - item.x;
            var dy = playerCenterY - item.y;
            var dist = Math.sqrt(dx * dx + dy * dy);
            
            if (dist < 160) {
                var pull = (160 - dist) / 10;
                item.vx += (dx / dist) * pull * 0.22;
                item.vy += (dy / dist) * pull * 0.22;
            }
            
            if (dist < 36) {
                item.collected = true;
                
                if (item.type === 'COIN') {
                    DungeonAudio.playSelect();
                    var scoreEl = document.querySelector('.sh-dungeon-hud-score span:last-child');
                    if (scoreEl) {
                        var scoreVal = parseInt(scoreEl.textContent, 10);
                        scoreEl.textContent = scoreVal + 1;
                    }
                    if (item.claimable) {
                        fetch('/dungeon/collect/coin', { method: 'POST' });
                    }
                } else if (item.type === 'SHIELD') {
                    DungeonAudio.playShieldGain();
                    var shieldsContainer = document.querySelector('.sh-dungeon-hud-shields');
                    var shieldsHudItem = document.querySelector('.sh-dungeon-hud-item-shields');
                    if (shieldsContainer) {
                        shieldsHudItem.classList.remove('is-hidden');
                        var newIcon = document.createElement('iconify-icon');
                        newIcon.setAttribute('icon', 'lucide:shield');
                        shieldsContainer.appendChild(newIcon);
                    }
                    if (item.claimable) {
                        fetch('/dungeon/collect/shield', { method: 'POST' });
                    }
                }
            }
        }

        // 2. Interactive Object Collision
        var roomType = canvas.dataset.currentRoomType;
        var cleared = canvas.dataset.currentRoomCleared === 'true';

        var checkCenterpiece = ['TREASURE', 'SHOP', 'SECRET', 'SHRINE'].indexOf(roomType) !== -1 && !cleared;
        if (checkCenterpiece && !window.dungeonCenterpieceInteracted) {
            var centerX = 480;
            var centerY = 352;
            var dist = Math.sqrt(Math.pow(playerX - centerX, 2) + Math.pow(playerY - centerY, 2));
            if (dist < 48) {
                window.dungeonCenterpieceInteracted = true;
                DungeonAudio.playReveal();
                
                // Stop loop and show HTMX-loaded modal mount
                loopRunning = false;
                var mount = document.getElementById('dungeon-modal-mount');
                if (mount) {
                    mount.style.display = 'block';
                }
                if (animationFrameId) cancelAnimationFrame(animationFrameId);
                return;
            }
        }

        // 3. Redraw Scenery
        DungeonRenderer.renderExploration(roomType, cleared, hasUpDoor, hasDownDoor, hasLeftDoor, hasRightDoor, currentRoomItems, playerX, playerY);

        animationFrameId = requestAnimationFrame(updateAndRenderExploration);
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

        var elapsed = Date.now() - window.dungeonSplashStartTime;
        if (elapsed >= 1500) {
            window.dungeonSplashActive = false;
            renderRoomCanvas();
            return;
        }

        DungeonRenderer.renderSplash();
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
            renderRoomCanvas();
            return;
        }

        if (typeof DungeonRenderer.renderCombat === 'function') {
            DungeonRenderer.renderCombat();
        }

        requestAnimationFrame(runCombatLoop);
    }

    function renderRoomCanvas() {
        var canvas = document.getElementById('dungeon-room-canvas');
        if (!canvas) return;
        var activeEncounterId = canvas.dataset.activeEncounterId;
        if (activeEncounterId && activeEncounterId.length > 0) {
            loopRunning = false;
            if (animationFrameId) {
                cancelAnimationFrame(animationFrameId);
                animationFrameId = null;
            }

            // Splash entrance screen check
            if (window.dungeonSplashPlayedForEncounter !== activeEncounterId) {
                window.dungeonSplashActive = true;
                window.dungeonSplashPlayedForEncounter = activeEncounterId;
                window.dungeonSplashStartTime = Date.now();

                var activeEncBoss = canvas.dataset.activeEncounterBoss === 'true';
                var gauntletTotal = parseInt(canvas.dataset.gauntletTotal || '0', 10);
                window.dungeonSplashBoss = activeEncBoss;

                if (gauntletTotal > 0 && !activeEncBoss) {
                    window.dungeonSplashMonster = 'CHAMPION';
                } else if (activeEncBoss) {
                    window.dungeonSplashMonster = 'DRAGON';
                } else {
                    var monsterTypes = ['SLIME', 'SKELETON', 'GOBLIN', 'GHOST'];
                    var hash = 0;
                    for (var i = 0; i < activeEncounterId.length; i++) {
                        hash = activeEncounterId.charCodeAt(i) + ((hash << 5) - hash);
                    }
                    var idx = Math.abs(hash) % monsterTypes.length;
                    window.dungeonSplashMonster = monsterTypes[idx];
                }

                DungeonAudio.playBattleStart();
                runSplashLoop();
                return;
            }

            if (window.dungeonSplashActive) {
                return;
            }

            if (typeof DungeonRenderer.renderCombat === 'function') {
                if (!combatLoopRunning) {
                    combatLoopRunning = true;
                    runCombatLoop();
                }
            }
            return;
        }

        // Transitioning back to exploration mode
        var justFinishedCombat = combatLoopRunning;
        combatLoopRunning = false;

        var roomId = canvas.dataset.currentRoomId;
        var roomChanged = (window.dungeonLastRoomId !== roomId);
        window.dungeonLastRoomId = roomId;

        if (roomChanged) {
            // Initialize room items
            currentRoomItems.roomId = roomId;
            currentRoomItems.coins = [];
            currentRoomItems.remains = null;
            
            var roomType = canvas.dataset.currentRoomType;
            var cleared = canvas.dataset.currentRoomCleared === 'true';
            
            if (!cleared && ['TREASURE', 'SHOP', 'SECRET', 'HEAL', 'ENTRANCE', 'SHRINE', 'BOSS'].indexOf(roomType) === -1) {
                currentRoomItems.pots = spawnPotsForRoom(roomId);
            } else if (cleared && ['COMBAT', 'ELITE', 'BOSS'].indexOf(roomType) !== -1) {
                var monsterTypes = ['SLIME', 'SKELETON', 'GOBLIN', 'GHOST'];
                var hash = 0;
                for (var i = 0; i < roomId.length; i++) {
                    hash = roomId.charCodeAt(i) + ((hash << 5) - hash);
                }
                var idx = Math.abs(hash) % monsterTypes.length;
                if (roomType === 'BOSS') {
                    currentRoomItems.remains = { type: 'DRAGON', x: 480, y: 352 };
                } else if (roomType === 'ELITE') {
                    currentRoomItems.remains = { type: 'CHAMPION', x: 480, y: 352 };
                } else {
                    currentRoomItems.remains = { type: monsterTypes[idx], x: 480, y: 352 };
                }
                currentRoomItems.pots = [];
            } else {
                currentRoomItems.pots = spawnPotsForRoom(roomId);
            }

            // Spawn player relative to entered door orientation
            if (window.dungeonLastMoveDirection) {
                var dir = window.dungeonLastMoveDirection;
                window.dungeonLastMoveDirection = null; // Reset spawn reference
                if (dir === 'UP') {
                    playerX = 480;
                    playerY = 590;
                } else if (dir === 'DOWN') {
                    playerX = 480;
                    playerY = 110;
                } else if (dir === 'LEFT') {
                    playerX = 840;
                    playerY = 352;
                } else if (dir === 'RIGHT') {
                    playerX = 120;
                    playerY = 352;
                }
            } else {
                // Default center spawn for new run/resume
                playerX = 480;
                playerY = 352;
            }
        }

        if (justFinishedCombat) {
            DungeonAudio.playVictory();
            
            var numCoins = 3 + Math.floor(Math.random() * 3); // 3 to 5 coins
            var monsterType = window.dungeonSplashMonster || 'SLIME';
            
            currentRoomItems.remains = {
                type: monsterType,
                x: 480,
                y: 352
            };
            
            for (var i = 0; i < numCoins; i++) {
                var angle = Math.random() * Math.PI * 2;
                var speed = 3 + Math.random() * 4;
                currentRoomItems.coins.push({
                    x: 480 + 16,
                    y: 352 + 16,
                    vx: Math.cos(angle) * speed,
                    vy: Math.sin(angle) * speed,
                    collected: false,
                    claimable: false, // visual only to match server
                    type: 'COIN'
                });
            }
        }

        // Safe resetting of local movement state
        window.dungeonActiveKeys = {};
        window.dungeonCenterpieceInteracted = false;

        // Hide overlay modal initially
        var mount = document.getElementById('dungeon-modal-mount');
        if (mount) {
            mount.style.display = 'none';
        }

        loopRunning = true;
        startExplorationLoop();
    }

    var drawQueued = false;
    function triggerDraw() {
        if (drawQueued) return;
        drawQueued = true;
        requestAnimationFrame(function () {
            drawQueued = false;
            dungeonInit();
        });
    }
    window.dungeonTriggerDraw = triggerDraw;

    // ============================================================
    // Entry Point
    // ============================================================
    function dungeonInit() {
        DungeonMinimap.draw();
        renderRoomCanvas();
        DungeonInput.init();
    }

    if (typeof htmx !== 'undefined' && htmx) {
        htmx.onLoad(dungeonInit);
    } else {
        document.addEventListener('DOMContentLoaded', dungeonInit);
    }

})();
