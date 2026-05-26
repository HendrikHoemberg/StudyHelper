(function () {
    // ============================================================
    // Native Web Audio API Synthesizer (DungeonAudio)
    // ============================================================
    var DungeonAudio = {
        ctx: null,
        init: function () {
            if (!this.ctx) {
                var AudioContextClass = window.AudioContext || window.webkitAudioContext;
                if (AudioContextClass) {
                    this.ctx = new AudioContextClass();
                }
            }
            if (this.ctx && this.ctx.state === 'suspended') {
                this.ctx.resume();
            }
        },
        playMove: function () {
            this.init();
            if (!this.ctx) return;
            try {
                var osc = this.ctx.createOscillator();
                var gain = this.ctx.createGain();
                osc.connect(gain);
                gain.connect(this.ctx.destination);

                osc.type = 'triangle';
                osc.frequency.setValueAtTime(140, this.ctx.currentTime);
                osc.frequency.exponentialRampToValueAtTime(320, this.ctx.currentTime + 0.08);

                gain.gain.setValueAtTime(0.03, this.ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.08);

                osc.start();
                osc.stop(this.ctx.currentTime + 0.08);
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        },
        playBattleStart: function () {
            this.init();
            if (!this.ctx) return;
            try {
                var now = this.ctx.currentTime;
                // Double oscillator riser for dramatic tension
                [100, 150].forEach(function (baseFreq) {
                    var osc = DungeonAudio.ctx.createOscillator();
                    var gain = DungeonAudio.ctx.createGain();
                    osc.connect(gain);
                    gain.connect(DungeonAudio.ctx.destination);

                    osc.type = 'sawtooth';
                    osc.frequency.setValueAtTime(baseFreq, now);
                    osc.frequency.exponentialRampToValueAtTime(baseFreq * 6, now + 0.9);

                    gain.gain.setValueAtTime(0.04, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.9);

                    osc.start();
                    osc.stop(now + 0.9);
                });
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        },
        playSlash: function () {
            this.init();
            if (!this.ctx) return;
            try {
                var now = this.ctx.currentTime;
                // 1. Procedural Noise buffer for metal friction/slice
                var bufferSize = this.ctx.sampleRate * 0.15;
                var buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
                var data = buffer.getChannelData(0);
                for (var i = 0; i < bufferSize; i++) {
                    data[i] = Math.random() * 2 - 1;
                }

                var noise = this.ctx.createBufferSource();
                noise.buffer = buffer;

                var filter = this.ctx.createBiquadFilter();
                filter.type = 'bandpass';
                filter.frequency.setValueAtTime(800, now);
                filter.frequency.exponentialRampToValueAtTime(3200, now + 0.15);

                var noiseGain = this.ctx.createGain();
                noiseGain.gain.setValueAtTime(0.06, now);
                noiseGain.gain.exponentialRampToValueAtTime(0.001, now + 0.15);

                noise.connect(filter);
                filter.connect(noiseGain);
                noiseGain.connect(this.ctx.destination);
                noise.start();

                // 2. High-pitch chime oscillator sweep for blade ring
                var osc = this.ctx.createOscillator();
                var oscGain = this.ctx.createGain();
                osc.connect(oscGain);
                oscGain.connect(this.ctx.destination);

                osc.type = 'sine';
                osc.frequency.setValueAtTime(1200, now);
                osc.frequency.exponentialRampToValueAtTime(200, now + 0.18);

                oscGain.gain.setValueAtTime(0.04, now);
                oscGain.gain.exponentialRampToValueAtTime(0.001, now + 0.18);

                osc.start();
                osc.stop(now + 0.18);
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        },
        playDamage: function () {
            this.init();
            if (!this.ctx) return;
            try {
                var now = this.ctx.currentTime;
                var osc = this.ctx.createOscillator();
                var gain = this.ctx.createGain();
                osc.connect(gain);
                gain.connect(this.ctx.destination);

                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(110, now);
                osc.frequency.linearRampToValueAtTime(35, now + 0.25);

                gain.gain.setValueAtTime(0.12, now);
                gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);

                osc.start();
                osc.stop(now + 0.25);
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        },
        playVictory: function () {
            this.init();
            if (!this.ctx) return;
            try {
                var now = this.ctx.currentTime;
                // Upbeat ascending C Major chord
                var notes = [261.63, 329.63, 392.00, 523.25, 659.25]; // C4, E4, G4, C5, E5
                notes.forEach(function (freq, index) {
                    var osc = DungeonAudio.ctx.createOscillator();
                    var gain = DungeonAudio.ctx.createGain();
                    osc.connect(gain);
                    gain.connect(DungeonAudio.ctx.destination);

                    osc.type = 'triangle';
                    osc.frequency.setValueAtTime(freq, now + index * 0.08);

                    gain.gain.setValueAtTime(0, now + index * 0.08);
                    gain.gain.linearRampToValueAtTime(0.04, now + index * 0.08 + 0.02);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + index * 0.08 + 0.28);

                    osc.start(now + index * 0.08);
                    osc.stop(now + index * 0.08 + 0.28);
                });
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        }
    };

    // Auto unlock audio context on initial gestures
    document.addEventListener('click', function () { DungeonAudio.init(); });
    document.addEventListener('keydown', function () { DungeonAudio.init(); });

    // ============================================================
    // Programmatic 16x16 Pixel-Art Matrices
    // ============================================================
    var SPRITES = {
        PLAYER: [
            "....kkkkkk......",
            "...kssssssk.....",
            "..kssssssssk....",
            ".ksddddddddsk...",
            ".ksdggggggdsk...",
            ".ksdgwwwwgdsk...",
            ".ksdggggggdsk...",
            "..ksddddddsk....",
            "...kkssskk......",
            "..kdkssskdk.....",
            ".kddkssskddk....",
            "kkddkssskddkk...",
            "kdddkssskdddk...",
            ".kkkkskskkkk....",
            "....ksksk.......",
            "....kk.kk......."
        ],
        SLIME: [
            "................",
            "......kkkk......",
            "....kkvvvvkk....",
            "...kvvvvvvvvkk..",
            "..kvvvvvvvvvvvk.",
            ".kvvvvvvvvvvvvvk",
            ".kvvwvvvvvvwvvvk",
            "kvvwkwvvvvwkwvvk",
            "kvvvvkkvvvkkvvvk",
            "kvvvvvvvvvvvvvvk",
            "kvvvvvvvvvvvvvvk",
            "kvvvvvvvvvvvvvvk",
            ".kvvvvvvvvvvvvvk",
            "..kvvvvvvvvvvvk.",
            "...kkvvvvvvvkk..",
            ".....kkkkkkk...."
        ],
        SKELETON: [
            ".....kkkkkk.....",
            "....kwwwwwwk....",
            "...kwwkwwkwwk...",
            "..kwwkkwwkkwwk..",
            "..kwwwwwwwwwwk..",
            "...kwwkkkkwwk...",
            "....kwwwwwwk....",
            ".....kkkkkk.....",
            "......kwwk......",
            "....kkwwwwkk....",
            "...kwwkwwkwwk...",
            "..kwwk.ww.kwwk..",
            "..kwk..ww..kwk..",
            "...k...ww...k...",
            "......kwwk......",
            ".....kk..kk....."
        ],
        GOBLIN: [
            "......kkkk......",
            "....kkrrrrkk....",
            "...krrrrrrrrk...",
            "..krrrrrrrrrrk..",
            ".kkrrkkkkkkrrkk.",
            "kvvkrvvkkvvrkvvk",
            "kvvvvvkwwkvvvvvk",
            "kvvvvvkkkkvvvvvk",
            ".kvvvvvvvvvvvvk.",
            "..kvvkkvvkkvvk..",
            "...kvvvvvvvvk...",
            "....kbbbbbbk....",
            "...kbbbbbbbbk...",
            "..kbbbbbbbbbbk..",
            "..kk.bb..bb.kk..",
            ".....kk..kk....."
        ],
        GHOST: [
            ".....kkkkkk.....",
            "....kfffffffk...",
            "...kfffffffffk..",
            "..kffrffffrfffk.",
            ".kffrwkffrwkfffk",
            ".kfffrkfffrkfffk",
            "kfffffffffffffffk",
            "kfffffffffffffffk",
            "kfffffffffffffffk",
            "kfffffffffffffffk",
            "kffffffkfffffffk.",
            ".kffffk.kfffffk.",
            "..kffk...kfffk..",
            "...kk.....kkk...",
            "................",
            "................"
        ],
        DRAGON: [
            ".....kkkkkk.....",
            "...kkrrrrrrkk...",
            "..krrrrrrrrrrk..",
            ".krrroyyoyyrrrk.",
            "krrroywwwyorrrrk",
            "krrrywwkwwyrrrrk",
            "krrryykkkyyrrrrk",
            "krrrrryyyrrrrrrk",
            "krrrrkkkkkrrrrrk",
            "krrrryyyyyyrrrrk",
            ".krrrykkkkyrrrk.",
            "..krrryyyrrrrk..",
            "...kkrrrrrrkk...",
            "....kbbbbbbk....",
            "....kbb..bbk....",
            "....kk....kk...."
        ],
        CHEST: [
            "................",
            "....kkkkkkkk....",
            "...kbbbbbbbbk...",
            "..kbbbbbbbbbbk..",
            ".kooooooooooook.",
            ".koykoyykoykoyk.",
            ".kkkkkkkkkkkkkk.",
            "kbbbbbbkkbbbbbbk",
            "kbbbbbbkkbbbbbbk",
            "koykkkkoykkkkoyk",
            "kbbbbbbbbbbbbbbk",
            "kbbbbbbbbbbbbbbk",
            "kbbbbbbbbbbbbbbk",
            ".kkbbbbbbbbbbkk.",
            "..kkkkkkkkkkkk..",
            "................"
        ],
        POTION: [
            "......kkkk......",
            "......kssk......",
            "......kssk......",
            ".....kooook.....",
            "....kssssssk....",
            "...kssrrrrssk...",
            "..kssrrrrrrssk..",
            "..ksrrrrrrrrks..",
            ".ksrrrrwwrrrwwks",
            ".ksrrrrrrrrrrks.",
            ".ksrrrrrrrrrrks.",
            "..ksrrrrrrrrks..",
            "..kssrrrrrrssk..",
            "...kssssssssk...",
            "....kkkkkkkk....",
            "................"
        ],
        PORTAL: [
            "....kkkkkkkk....",
            "...kssssssssk...",
            "..kssggggggssk..",
            ".ksggwwwwwwggks.",
            ".ksgwwggggwwgks.",
            "kssgwwggggwwgssk",
            "kssgwwgwwgwwgssk",
            "kssgwwgwwgwwgssk",
            "kssgwwgwwgwwgssk",
            "kssgwwggggwwgssk",
            "kssggwwwwwwggssk",
            "kssgggggggggssk.",
            "ksssssssssssssk.",
            "ksssssssssssssk.",
            "kkkkkkkkkkkkkkk.",
            "................"
        ],
        TRAP: [
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "......k..k......",
            ".....ks..sk.....",
            "....kss..ssk....",
            "...kssk..kssk...",
            "..ksssk..ksssk..",
            ".ksssskkkssssk..",
            "kkkkkkkkkkkkkkk.",
            "................"
        ]
    };

    function drawPixelSprite(ctx, spriteName, px, py, size) {
        var sprite = SPRITES[spriteName];
        if (!sprite) return;
        var pixelSize = size / 16;
        for (var r = 0; r < 16; r++) {
            var row = sprite[r];
            for (var c = 0; c < 16; c++) {
                var char = row[c];
                if (char === '.') continue;
                var color = '#000000';
                switch (char) {
                    case 'k': color = '#020617'; break; // Near black
                    case 's': color = '#94a3b8'; break; // Light slate
                    case 'd': color = '#475569'; break; // Dark slate
                    case 'g': color = '#2dd4bf'; break; // Electric teal
                    case 'w': color = '#ffffff'; break; // White
                    case 'r': color = '#ef4444'; break; // Crimson
                    case 'o': color = '#f59e0b'; break; // Amber
                    case 'b': color = '#7c2d12'; break; // Rich wood brown
                    case 'p': color = '#c084fc'; break; // Soft purple
                    case 'y': color = '#f59e0b'; break; // Golden yellow
                    case 'v': color = '#10b981'; break; // Slime emerald
                    case 'f': color = '#cbd5e1'; break; // Ghost light gray
                }
                ctx.fillStyle = color;
                ctx.fillRect(px + c * pixelSize, py + r * pixelSize, pixelSize + 0.4, pixelSize + 0.4);
            }
        }
    }

    // ============================================================
    // Deterministic Procedural Brick Textures
    // ============================================================
    function drawWallTile(ctx, px, py, size, isDark) {
        var base = isDark ? '#0f172a' : '#94a3b8';
        var shadow = isDark ? '#020617' : '#475569';
        var highlight = isDark ? '#1e293b' : '#cbd5e1';

        ctx.fillStyle = base;
        ctx.fillRect(px, py, size, size);

        // Brick border lines
        ctx.fillStyle = shadow;
        ctx.fillRect(px, py + size - 2, size, 2); 
        ctx.fillRect(px + size - 2, py, 2, size); 

        // Mortar horizontal lines
        ctx.fillRect(px, py + Math.floor(size / 2) - 1, size, 2);

        // Vertical mortar joints
        ctx.fillRect(px + Math.floor(size / 2) - 1, py, 2, Math.floor(size / 2));
        ctx.fillRect(px + Math.floor(size / 4) - 1, py + Math.floor(size / 2), 2, Math.floor(size / 2));
        ctx.fillRect(px + Math.floor(3 * size / 4) - 1, py + Math.floor(size / 2), 2, Math.floor(size / 2));

        // Edge highlights
        ctx.fillStyle = highlight;
        ctx.fillRect(px + 1, py + 1, size - 3, 1);
        ctx.fillRect(px + 1, py + Math.floor(size / 2) + 1, size - 3, 1);
    }

    function drawFloorTile(ctx, px, py, size, isDark) {
        var base = isDark ? '#1a1f2c' : '#f8fafc';
        var detail = isDark ? '#232938' : '#f1f5f9';
        var crack = isDark ? '#0b0f19' : '#cbd5e1';

        ctx.fillStyle = base;
        ctx.fillRect(px, py, size, size);

        // Grid overlay helper
        ctx.fillStyle = detail;
        ctx.fillRect(px, py, 2, 2);
        ctx.fillRect(px + size - 2, py + size - 2, 2, 2);

        // Deterministic procedural cracks
        var seed = Math.sin(px * 12.9898 + py * 78.233) * 43758.5453;
        var rand = seed - Math.floor(seed);
        if (rand < 0.15) {
            ctx.fillStyle = crack;
            ctx.fillRect(px + Math.floor(size / 3), py + Math.floor(size / 2), 1, 1);
            ctx.fillRect(px + Math.floor(2 * size / 3), py + Math.floor(size / 4), 1, 1);
        } else if (rand < 0.25) {
            ctx.fillStyle = crack;
            ctx.fillRect(px + Math.floor(size / 4), py + Math.floor(size / 4), 2, 1);
            ctx.fillRect(px + Math.floor(size / 4) + 1, py + Math.floor(size / 4) + 1, 1, 2);
        }
    }

    // ============================================================
    // Core Rendering & States
    // ============================================================
    function readMapTiles() {
        var script = document.getElementById('dungeon-map-state');
        if (!script) return null;
        try {
            return JSON.parse(script.textContent);
        } catch (e) {
            console.error("readMapTiles: parse failed", e);
            return null;
        }
    }

    function submitMove(direction) {
        htmx.ajax('POST', '/dungeon/move', {
            target: '#dungeon-session-content',
            swap: 'outerHTML',
            values: { direction: direction }
        });
    }

    function drawDungeon() {
        try {
            var canvas = document.getElementById('dungeon-map-canvas');
            if (!canvas) return;

            var mapWidth = parseInt(canvas.dataset.mapWidth, 10);
            var mapHeight = parseInt(canvas.dataset.mapHeight, 10);
            var targetX = parseInt(canvas.dataset.playerX, 10);
            var targetY = parseInt(canvas.dataset.playerY, 10);

            var activeEncId = canvas.dataset.activeEncounterId;
            var activeEncBoss = canvas.dataset.activeEncounterBoss === 'true';

            // 1. Detect Battle Start & Trigger Splash Transition
            if (activeEncId && activeEncId !== window.lastActiveEncounterId) {
                window.dungeonSplashActive = true;
                window.dungeonSplashStart = Date.now();
                window.dungeonSplashBoss = activeEncBoss;
                
                // Determine monster type deterministically
                var monsterTypes = ['SLIME', 'SKELETON', 'GOBLIN', 'GHOST'];
                var idx = (targetX * 7 + targetY * 13) % monsterTypes.length;
                window.dungeonSplashMonster = activeEncBoss ? 'DRAGON' : monsterTypes[idx];

                // Play Audio Riser
                DungeonAudio.playBattleStart();

                // Trigger automatic splash clear after 1200ms
                setTimeout(function () {
                    window.dungeonSplashActive = false;
                    drawDungeon();
                }, 1200);
            }
            window.lastActiveEncounterId = activeEncId;

            // 2. Play Footstep on Movement
            if (window.currentPlayerX !== undefined && (window.currentPlayerX !== targetX || window.currentPlayerY !== targetY)) {
                if (!window.dungeonSplashActive) {
                    DungeonAudio.playMove();
                }
            }

            // 3. Shake & Flash & Audio on HUD changes (Heal, Damage, Score)
            var health = parseInt(document.querySelector('.sh-dungeon-hud-health')?.textContent || '5', 10);
            var score = parseInt(document.querySelector('.sh-dungeon-hud-score')?.textContent || '0', 10);

            if (window.lastHealth !== undefined && health < window.lastHealth) {
                DungeonAudio.playDamage();
                var container = document.getElementById('dungeon-session-content');
                if (container) {
                    container.classList.add('sh-dungeon-shake-active', 'sh-dungeon-flash-danger');
                    setTimeout(function() {
                        container.classList.remove('sh-dungeon-shake-active', 'sh-dungeon-flash-danger');
                    }, 400);
                }
            } else if (window.lastHealth !== undefined && health > window.lastHealth) {
                DungeonAudio.playVictory();
            }

            if (window.lastScore !== undefined && score > window.lastScore) {
                DungeonAudio.playSlash();
                // Spawn a quick sword slash visual effect
                window.slashParticleActive = true;
                window.slashParticleStart = Date.now();
                setTimeout(function() {
                    window.slashParticleActive = false;
                }, 300);

                var container = document.getElementById('dungeon-session-content');
                if (container) {
                    container.classList.add('sh-dungeon-flash-success');
                    setTimeout(function() {
                        container.classList.remove('sh-dungeon-flash-success');
                    }, 400);
                }
            }

            window.lastHealth = health;
            window.lastScore = score;

            // Smooth Interpolation
            if (window.currentPlayerX === undefined) {
                window.currentPlayerX = targetX;
                window.currentPlayerY = targetY;
            }

            if (window.dungeonAnimFrame) {
                cancelAnimationFrame(window.dungeonAnimFrame);
            }

            function animate() {
                var dx = targetX - window.currentPlayerX;
                var dy = targetY - window.currentPlayerY;
                var speed = 0.22;
                window.currentPlayerX += dx * speed;
                window.currentPlayerY += dy * speed;

                if (Math.abs(dx) < 0.01 && Math.abs(dy) < 0.01) {
                    window.currentPlayerX = targetX;
                    window.currentPlayerY = targetY;
                    renderAll(canvas, mapWidth, mapHeight, window.currentPlayerX, window.currentPlayerY);
                } else {
                    renderAll(canvas, mapWidth, mapHeight, window.currentPlayerX, window.currentPlayerY);
                    window.dungeonAnimFrame = requestAnimationFrame(animate);
                }
            }

            // If splash is running, render immediately
            if (window.dungeonSplashActive) {
                renderAll(canvas, mapWidth, mapHeight, targetX, targetY);
            } else {
                animate();
            }

        } catch (err) {
            console.error("drawDungeon error:", err);
        }
    }

    function renderAll(canvas, mapWidth, mapHeight, pX, pY) {
        var ctx = canvas.getContext('2d');
        var themeColors = document.documentElement.dataset.theme === 'dark';

        // Splash screen overrides map rendering
        if (window.dungeonSplashActive) {
            renderSplash(canvas, ctx);
            return;
        }

        var mapTiles = readMapTiles();
        if (!mapTiles) return;

        var tileSize = Math.floor(canvas.width / mapWidth);
        var tileMap = {};
        for (var i = 0; i < mapTiles.length; i++) {
            var t = mapTiles[i];
            tileMap[t.x + ',' + t.y] = t;
        }

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // 1. Draw procedural walls and floors
        for (var y = 0; y < mapHeight; y++) {
            for (var x = 0; x < mapWidth; x++) {
                var tile = tileMap[x + ',' + y];
                if (!tile) continue;

                var px = x * tileSize;
                var py = y * tileSize;

                if (!tile.revealed) {
                    ctx.fillStyle = themeColors ? '#020617' : '#cbd5e1';
                    ctx.fillRect(px, py, tileSize, tileSize);
                    continue;
                }

                // Render Tile Texture
                if (tile.type === 'WALL' || tile.type === 'SECRET_WALL') {
                    drawWallTile(ctx, px, py, tileSize, themeColors);
                } else {
                    drawFloorTile(ctx, px, py, tileSize, themeColors);
                }

                // Exploration dim overlay
                if (tile.revealed && !tile.explored) {
                    ctx.fillStyle = themeColors ? 'rgba(2, 6, 23, 0.4)' : 'rgba(255, 255, 255, 0.35)';
                    ctx.fillRect(px, py, tileSize, tileSize);
                }

                // Render Sprite Entities
                if (tile.revealed) {
                    switch (tile.type) {
                        case 'ENTRANCE':
                            drawPixelSprite(ctx, 'PORTAL', px, py, tileSize);
                            break;
                        case 'TREASURE':
                            drawPixelSprite(ctx, 'CHEST', px, py, tileSize);
                            break;
                        case 'HEAL':
                            drawPixelSprite(ctx, 'POTION', px, py, tileSize);
                            break;
                        case 'BOSS':
                            drawPixelSprite(ctx, 'DRAGON', px, py, tileSize);
                            break;
                        case 'TRAP':
                            drawPixelSprite(ctx, 'TRAP', px, py, tileSize);
                            break;
                        case 'ENCOUNTER':
                            // Determine monster type
                            var monsterTypes = ['SLIME', 'SKELETON', 'GOBLIN', 'GHOST'];
                            var idx = (tile.x * 7 + tile.y * 13) % monsterTypes.length;
                            drawPixelSprite(ctx, monsterTypes[idx], px, py, tileSize);
                            break;
                    }
                }
            }
        }

        // 2. Flickering Torchlight Cone
        var pCenterX = pX * tileSize + tileSize / 2;
        var pCenterY = pY * tileSize + tileSize / 2;

        var flicker = Math.sin(Date.now() / 110) * 6;
        var lightGrad = ctx.createRadialGradient(
            pCenterX, pCenterY, tileSize * 0.5,
            pCenterX, pCenterY, tileSize * (3.4 + flicker * 0.03)
        );

        if (themeColors) {
            lightGrad.addColorStop(0, 'rgba(2, 6, 23, 0)');
            lightGrad.addColorStop(0.4, 'rgba(2, 6, 23, 0.2)');
            lightGrad.addColorStop(0.75, 'rgba(2, 6, 23, 0.68)');
            lightGrad.addColorStop(1, 'rgba(2, 6, 23, 0.98)');
        } else {
            lightGrad.addColorStop(0, 'rgba(241, 245, 249, 0)');
            lightGrad.addColorStop(0.4, 'rgba(241, 245, 249, 0.15)');
            lightGrad.addColorStop(0.75, 'rgba(241, 245, 249, 0.55)');
            lightGrad.addColorStop(1, 'rgba(241, 245, 249, 0.88)');
        }

        ctx.fillStyle = lightGrad;
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // 3. Draw Player Knight
        var pPixelX = pX * tileSize;
        var pPixelY = pY * tileSize;
        drawPixelSprite(ctx, 'PLAYER', pPixelX, pPixelY, tileSize);

        // 4. Draw sword slash combat particle
        if (window.slashParticleActive) {
            var elapsed = Date.now() - window.slashParticleStart;
            ctx.save();
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 5;
            ctx.shadowColor = '#f59e0b';
            ctx.shadowBlur = 12;
            ctx.beginPath();
            
            // Draw an electric arc slash centered on player
            var factor = elapsed / 300;
            var arcX1 = pCenterX - tileSize + factor * tileSize * 2;
            var arcY1 = pCenterY + tileSize - factor * tileSize * 2;
            var arcX2 = arcX1 + tileSize * 0.4;
            var arcY2 = arcY1 - tileSize * 0.4;

            ctx.moveTo(arcX1, arcY1);
            ctx.lineTo(arcX2, arcY2);
            ctx.stroke();
            ctx.restore();
        }
    }

    // ============================================================
    // Retro Fullscreen Splash Screen Render
    // ============================================================
    function renderSplash(canvas, ctx) {
        ctx.fillStyle = '#020617';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Retro scanline patterns
        ctx.fillStyle = 'rgba(51, 65, 85, 0.08)';
        for (var y = 0; y < canvas.height; y += 4) {
            ctx.fillRect(0, y, canvas.width, 2);
        }

        // Bobbing vertical offset
        var bob = Math.sin(Date.now() / 140) * 12;

        // Draw large 4x scaled monster
        var spriteSize = 112; // 16 * 7
        var sx = (canvas.width - spriteSize) / 2;
        var sy = (canvas.height - spriteSize) / 2 - 32 + bob;

        drawPixelSprite(ctx, window.dungeonSplashMonster || 'SLIME', sx, sy, spriteSize);

        // Animated warning brackets
        var bracketOffset = Math.abs(Math.sin(Date.now() / 200)) * 6;
        ctx.fillStyle = '#ef4444';
        ctx.font = 'bold 20px "Courier New", Courier, monospace';
        ctx.textAlign = 'center';
        
        var title = window.dungeonSplashBoss ? "!!! BOSS-KAMPF !!!" : "⚔️ GEGNER GEFUNDEN ⚔️";
        ctx.fillText(title, canvas.width / 2, canvas.height - 90);

        // Subtitle
        ctx.fillStyle = '#94a3b8';
        ctx.font = '13px "Courier New", Courier, monospace';
        var monsterLabel = (window.dungeonSplashMonster || "MONSTER").toUpperCase();
        ctx.fillText("EIN WILDER " + monsterLabel + " BEGEGNET DIR!", canvas.width / 2, canvas.height - 60);

        ctx.fillStyle = '#64748b';
        ctx.font = 'italic 11px "Courier New", Courier, monospace';
        ctx.fillText("Bereite dich auf den Kampf vor...", canvas.width / 2, canvas.height - 40);
    }

    // Direct listener on DOMContentLoaded
    document.addEventListener('DOMContentLoaded', drawDungeon);

    // HTMX Lifecycle events
    function registerLifecycleHooks(element) {
        if (!element) return;
        element.addEventListener('htmx:afterSwap', function (e) {
            if (document.getElementById('dungeon-map-canvas')) {
                drawDungeon();
            }
        });
    }

    registerLifecycleHooks(document);
    if (document.body) {
        registerLifecycleHooks(document.body);
    } else {
        document.addEventListener('DOMContentLoaded', function () {
            registerLifecycleHooks(document.body);
        });
    }

    // Keyboard support
    document.addEventListener('keydown', function (e) {
        var canvas = document.getElementById('dungeon-map-canvas');
        if (!canvas) return;

        // Block movement key inputs during active battle encounters or transition splash
        if (document.querySelector('.sh-dungeon-encounter-active') || window.dungeonSplashActive) return;

        var dir = null;
        switch (e.key) {
            case 'ArrowUp':
            case 'w':
            case 'W':
                dir = 'UP';
                break;
            case 'ArrowDown':
            case 's':
            case 'S':
                dir = 'DOWN';
                break;
            case 'ArrowLeft':
            case 'a':
            case 'A':
                dir = 'LEFT';
                break;
            case 'ArrowRight':
            case 'd':
            case 'D':
                dir = 'RIGHT';
                break;
        }
        if (dir) {
            e.preventDefault();
            submitMove(dir);
        }
    });
})();
