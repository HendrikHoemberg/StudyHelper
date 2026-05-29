// dungeon-exploration.js — top-down exploration: movement, room item physics
// (pots/coins/shields), room transitions, and the exploration render loop.
// Gathers room state from the DOM and hands a plain state object to the
// (pure) renderer. The orchestration entry point lives in dungeon-engine.js.
var DungeonExploration = (function () {

    // Exploration room item state
    var currentRoomItems = {
        roomId: null,
        pots: [],     // {x, y, broken, shatterTime, index}
        coins: [],    // {x, y, vx, vy, collected, claimable, type, itemId}
        remains: null // {type, x, y}
    };

    var playerX = 480;
    var playerY = 352;
    var playerSpeed = 4.5;
    var loopRunning = false;
    var animationFrameId = null;

    function submitMove(direction) {
        htmx.ajax('POST', '/dungeon/move', {
            target: '#dungeon-session-content',
            swap: 'outerHTML',
            values: { direction: direction }
        });
    }

    function spawnPotsForRoom(roomId, potLoot) {
        var pots = [];
        var hash = 0;
        for (var i = 0; i < roomId.length; i++) {
            hash = roomId.charCodeAt(i) + ((hash << 5) - hash);
        }
        var rng = Math.abs(hash);
        var loot = (potLoot && potLoot.length > 0) ? potLoot : ['EMPTY', 'EMPTY'];
        var numPots = loot.length;

        var corners = [
            { minX: 160, maxX: 260, minY: 160, maxY: 240 },
            { minX: 700, maxX: 800, minY: 160, maxY: 240 },
            { minX: 160, maxX: 260, minY: 480, maxY: 560 },
            { minX: 700, maxX: 800, minY: 480, maxY: 560 }
        ];

        for (var c = corners.length - 1; c > 0; c--) {
            var j = (rng + c) % (c + 1);
            var temp = corners[c];
            corners[c] = corners[j];
            corners[j] = temp;
        }

        for (var p = 0; p < numPots && p < corners.length; p++) {
            var zone = corners[p];
            var px = zone.minX + ((rng + p * 17) % (zone.maxX - zone.minX));
            var py = zone.minY + ((rng + p * 31) % (zone.maxY - zone.minY));
            pots.push({ x: px, y: py, broken: false, shatterTime: 0, index: p, loot: loot[p] });
        }
        return pots;
    }

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

    function stopLoop() {
        loopRunning = false;
        if (animationFrameId) {
            cancelAnimationFrame(animationFrameId);
            animationFrameId = null;
        }
    }

    function startLoop() {
        if (animationFrameId) {
            cancelAnimationFrame(animationFrameId);
        }
        animationFrameId = requestAnimationFrame(updateAndRender);
    }

    function updateAndRender() {
        if (!loopRunning) return;

        var canvas = document.getElementById('dungeon-room-canvas');
        if (!canvas) {
            loopRunning = false;
            return;
        }

        var activeEncounterId = canvas.dataset.activeEncounterId;
        if (activeEncounterId && activeEncounterId.length > 0) {
            loopRunning = false;
            DungeonEngine.render();
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

                var roomId = currentRoomItems.roomId;
                if (pot.loot === 'COIN') {
                    currentRoomItems.coins.push({
                        x: potCenterX,
                        y: potCenterY,
                        vx: (Math.random() * 2 - 1) * 2,
                        vy: (Math.random() * 2 - 1) * 2,
                        collected: false,
                        claimable: true,
                        type: 'COIN',
                        itemId: roomId + '_coin_' + pot.index
                    });
                } else if (pot.loot === 'SHIELD') {
                    currentRoomItems.coins.push({
                        x: potCenterX,
                        y: potCenterY,
                        vx: (Math.random() * 2 - 1) * 2,
                        vy: (Math.random() * 2 - 1) * 2,
                        collected: false,
                        claimable: true,
                        type: 'SHIELD',
                        itemId: roomId + '_shield_' + pot.index
                    });
                }
            }
        }

        // Update coins/shields physics and attraction
        for (var k = 0; k < currentRoomItems.coins.length; k++) {
            var item = currentRoomItems.coins[k];
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

            var pcX = playerX + 32;
            var pcY = playerY + 32;
            var ddx = pcX - item.x;
            var ddy = pcY - item.y;
            var idist = Math.sqrt(ddx * ddx + ddy * ddy);

            if (idist < 160) {
                var pull = (160 - idist) / 10;
                item.vx += (ddx / idist) * pull * 0.22;
                item.vy += (ddy / idist) * pull * 0.22;
            }

            if (idist < 36) {
                item.collected = true;

                if (item.type === 'COIN') {
                    DungeonAudio.playSelect();
                    var scoreEl = document.querySelector('.sh-dungeon-hud-score span:last-child');
                    if (scoreEl) {
                        var scoreVal = parseInt(scoreEl.textContent, 10);
                        scoreEl.textContent = scoreVal + 1;
                    }
                    if (item.claimable && item.itemId) {
                        fetch('/dungeon/collect/coin?itemId=' + encodeURIComponent(item.itemId), { method: 'POST' });
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
                    if (item.claimable && item.itemId) {
                        fetch('/dungeon/collect/shield?itemId=' + encodeURIComponent(item.itemId), { method: 'POST' });
                    }
                }
            }
        }

        // 2. Interactive Object Collision
        var roomType = canvas.dataset.currentRoomType;
        var cleared = canvas.dataset.currentRoomCleared === 'true';

        var checkCenterpiece = ['TREASURE', 'SHOP', 'SHRINE'].indexOf(roomType) !== -1 && !cleared;
        if (checkCenterpiece && !window.dungeonCenterpieceInteracted) {
            var centerX = 480;
            var centerY = 352;
            var cdist = Math.sqrt(Math.pow(playerX - centerX, 2) + Math.pow(playerY - centerY, 2));
            if (cdist < 48) {
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
        DungeonRenderer.renderExploration({
            type: roomType,
            cleared: cleared,
            doors: { up: hasUpDoor, down: hasDownDoor, left: hasLeftDoor, right: hasRightDoor },
            roomItems: currentRoomItems,
            playerX: playerX,
            playerY: playerY
        });

        animationFrameId = requestAnimationFrame(updateAndRender);
    }

    // Enter (or re-enter) exploration mode for the current room.
    // justFinishedCombat: true when we just transitioned back from a combat clear.
    function enterExploration(canvas, justFinishedCombat) {
        var roomId = canvas.dataset.currentRoomId;
        var roomChanged = (window.dungeonLastRoomId !== roomId);
        window.dungeonLastRoomId = roomId;

        if (roomChanged) {
            currentRoomItems.roomId = roomId;
            currentRoomItems.coins = [];
            currentRoomItems.remains = null;

            var roomType = canvas.dataset.currentRoomType;
            var cleared = canvas.dataset.currentRoomCleared === 'true';
            var roomMonster = canvas.dataset.currentRoomMonster || '';
            var potLoot = (canvas.dataset.currentRoomPots || '').split(',').filter(Boolean);

            if (!cleared && ['TREASURE', 'SHOP', 'HEAL', 'ENTRANCE', 'SHRINE', 'BOSS'].indexOf(roomType) === -1) {
                currentRoomItems.pots = spawnPotsForRoom(roomId, potLoot);
            } else if (cleared && ['COMBAT', 'ELITE', 'BOSS'].indexOf(roomType) !== -1) {
                // Monster type is supplied by the server (see DungeonEncounter.monsterType).
                if (roomMonster) {
                    currentRoomItems.remains = { type: roomMonster, x: 480, y: 352 };
                }
                currentRoomItems.pots = [];
            } else {
                currentRoomItems.pots = spawnPotsForRoom(roomId, potLoot);
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
        startLoop();
    }

    return {
        enterExploration: enterExploration,
        stopLoop: stopLoop
    };
})();
