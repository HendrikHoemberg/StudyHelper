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
        },
        playSelect: function () {
            this.init();
            if (!this.ctx) return;
            try {
                var now = this.ctx.currentTime;
                var osc = this.ctx.createOscillator();
                var gain = this.ctx.createGain();
                osc.connect(gain);
                gain.connect(this.ctx.destination);

                osc.type = 'sine';
                osc.frequency.setValueAtTime(600, now);
                osc.frequency.exponentialRampToValueAtTime(900, now + 0.05);

                gain.gain.setValueAtTime(0.02, now);
                gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05);

                osc.start();
                osc.stop(now + 0.05);
            } catch (e) {
                console.warn("Audio play select failed:", e);
            }
        },
        playReveal: function () {
            this.init();
            if (!this.ctx) return;
            try {
                var now = this.ctx.currentTime;
                [0, 0.05].forEach(function (delay) {
                    var osc = DungeonAudio.ctx.createOscillator();
                    var gain = DungeonAudio.ctx.createGain();
                    osc.connect(gain);
                    gain.connect(DungeonAudio.ctx.destination);

                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(523.25, now + delay);
                    osc.frequency.exponentialRampToValueAtTime(1046.50, now + delay + 0.2);

                    gain.gain.setValueAtTime(0, now + delay);
                    gain.gain.linearRampToValueAtTime(0.03, now + delay + 0.04);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + delay + 0.2);

                    osc.start(now + delay);
                    osc.stop(now + delay + 0.2);
                });
            } catch (e) {
                console.warn("Audio play reveal failed:", e);
            }
        },
        playSubmit: function () {
            this.init();
            if (!this.ctx) return;
            try {
                var now = this.ctx.currentTime;
                [523.25, 783.99].forEach(function (freq) {
                    var osc = DungeonAudio.ctx.createOscillator();
                    var gain = DungeonAudio.ctx.createGain();
                    osc.connect(gain);
                    gain.connect(DungeonAudio.ctx.destination);

                    osc.type = 'triangle';
                    osc.frequency.setValueAtTime(freq, now);

                    gain.gain.setValueAtTime(0.04, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3);

                    osc.start();
                    osc.stop(now + 0.3);
                });
            } catch (e) {
                console.warn("Audio play submit failed:", e);
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

            if (!canvas.dungeonListenersBound) {
                canvas.dungeonListenersBound = true;
                bindCanvasInteractiveListeners(canvas);
            }

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
                    // Self-healing: if the DOM state is not yet ready/parsed during a synchronous tick,
                    // reschedule via requestAnimationFrame to retry once it settles.
                    if (!readMapTiles()) {
                        window.dungeonAnimFrame = requestAnimationFrame(animate);
                    }
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

        var activeEncId = canvas.dataset.activeEncounterId;
        if (activeEncId) {
            renderJRPGCombat(canvas, ctx, activeEncId);
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

    // ============================================================
    // Standard Monospace Text Wrapper Utility
    // ============================================================
    function wrapText(ctx, text, x, y, maxWidth, lineHeight, draw) {
        if (!text) return [];
        var words = text.split(' ');
        var lines = [];
        var currentLine = '';

        for (var i = 0; i < words.length; i++) {
            var word = words[i];
            if (word.indexOf('\n') !== -1) {
                var subwords = word.split('\n');
                for (var j = 0; j < subwords.length; j++) {
                    var testLine = currentLine + (currentLine ? ' ' : '') + subwords[j];
                    var metrics = ctx.measureText(testLine);
                    if (metrics.width > maxWidth && currentLine) {
                        lines.push(currentLine);
                        currentLine = subwords[j];
                    } else {
                        currentLine = testLine;
                    }
                    if (j < subwords.length - 1) {
                        lines.push(currentLine);
                        currentLine = '';
                    }
                }
            } else {
                var testLine = currentLine + (currentLine ? ' ' : '') + word;
                var metrics = ctx.measureText(testLine);
                if (metrics.width > maxWidth && currentLine) {
                    lines.push(currentLine);
                    currentLine = word;
                } else {
                    currentLine = testLine;
                }
            }
        }
        if (currentLine) {
            lines.push(currentLine);
        }

        if (draw) {
            for (var k = 0; k < lines.length; k++) {
                ctx.fillText(lines[k], x, y + k * lineHeight);
            }
        }
        return lines;
    }

    // ============================================================
    // Permanent Interactive JRPG Battle Renderer
    // ============================================================
    function renderJRPGCombat(canvas, ctx, activeEncId) {
        // Clear canvas with deep dark blue background
        ctx.fillStyle = '#020617';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Draw pixelated scanlines in background
        ctx.fillStyle = 'rgba(51, 65, 85, 0.05)';
        for (var y = 0; y < canvas.height; y += 4) {
            ctx.fillRect(0, y, canvas.width, 2);
        }

        // Fetch values from DOM
        var health = parseInt(document.querySelector('.sh-dungeon-hud-health')?.textContent || '5', 10);
        var score = parseInt(document.querySelector('.sh-dungeon-hud-score')?.textContent || '0', 10);
        var progress = document.querySelector('.sh-dungeon-hud-progress')?.textContent || '0/0';
        var activeEncBoss = canvas.dataset.activeEncounterBoss === 'true';
        var enemyName = (window.dungeonSplashMonster || (activeEncBoss ? "DRAGON" : "SLIME")).toUpperCase();

        // Draw Player HUD Panel (Left)
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.strokeRect(20, 20, 220, 65);
        ctx.fillStyle = 'rgba(15, 23, 42, 0.75)';
        ctx.fillRect(20, 20, 220, 65);

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 12px "Courier New", Courier, monospace';
        ctx.textAlign = 'left';
        ctx.fillText("HELD (SCORE: " + score + ")", 30, 40);

        // HP progress bar
        ctx.fillStyle = '#1e293b';
        ctx.fillRect(30, 48, 120, 12);
        var healthPercent = Math.max(0, Math.min(1, health / 5.0));
        ctx.fillStyle = healthPercent > 0.5 ? '#10b981' : (healthPercent > 0.2 ? '#f59e0b' : '#ef4444');
        ctx.fillRect(30, 48, 120 * healthPercent, 12);
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1;
        ctx.strokeRect(30, 48, 120, 12);

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 11px "Courier New", Courier, monospace';
        ctx.fillText("HP: " + health + "/5", 160, 58);

        // Draw Enemy HUD Panel (Right)
        ctx.strokeStyle = activeEncBoss ? '#ef4444' : '#ffffff';
        ctx.lineWidth = 2;
        ctx.strokeRect(canvas.width - 240, 20, 220, 65);
        ctx.fillStyle = 'rgba(15, 23, 42, 0.75)';
        ctx.fillRect(canvas.width - 240, 20, 220, 65);

        ctx.fillStyle = activeEncBoss ? '#ef4444' : '#ffffff';
        ctx.font = 'bold 12px "Courier New", Courier, monospace';
        ctx.fillText(enemyName, canvas.width - 230, 40);

        // Enemy progress/health bar
        ctx.fillStyle = '#1e293b';
        ctx.fillRect(canvas.width - 230, 48, 120, 12);
        
        var progParts = progress.split('/');
        var progressPercent = 0.5;
        if (progParts.length === 2) {
            var solved = parseInt(progParts[0], 10);
            var total = Math.max(1, parseInt(progParts[1], 10));
            progressPercent = Math.max(0, Math.min(1, solved / total));
        }
        ctx.fillStyle = activeEncBoss ? '#ef4444' : '#2dd4bf';
        ctx.fillRect(canvas.width - 230, 48, 120 * (1 - progressPercent + 0.1), 12);
        ctx.strokeStyle = activeEncBoss ? '#ef4444' : '#ffffff';
        ctx.strokeRect(canvas.width - 230, 48, 120, 12);

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 11px "Courier New", Courier, monospace';
        ctx.fillText("HP: " + Math.round((1 - progressPercent) * 100) + "%", canvas.width - 100, 58);

        // Enemy Sprite (Bobbing)
        var bob = Math.sin(Date.now() / 180) * 10;
        var monsterSprite = window.dungeonSplashMonster || (activeEncBoss ? 'DRAGON' : 'SLIME');
        var spriteSize = activeEncBoss ? 128 : 96;
        var sx = (canvas.width - spriteSize) / 2;
        var sy = 100 + bob;
        drawPixelSprite(ctx, monsterSprite, sx, sy, spriteSize);

        // Dialog Message Box
        var boxX = 20;
        var boxY = 230;
        var boxW = canvas.width - 40;
        var boxH = canvas.height - boxY - 20;

        ctx.fillStyle = '#020617';
        ctx.fillRect(boxX, boxY, boxW, boxH);

        // Thick Double-Line retro borders
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 4;
        ctx.strokeRect(boxX, boxY, boxW, boxH);
        ctx.strokeStyle = '#020617';
        ctx.lineWidth = 2;
        ctx.strokeRect(boxX + 3, boxY + 3, boxW - 6, boxH - 6);
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1;
        ctx.strokeRect(boxX + 5, boxY + 5, boxW - 10, boxH - 10);

        var isFlashcard = !!document.querySelector('.sh-dungeon-flashcard');
        var isQuiz = !!document.querySelector('.sh-dungeon-quiz-form');

        ctx.fillStyle = '#ffffff';
        ctx.textAlign = 'left';

        var optionBoxes = [];

        if (isFlashcard) {
            var frontText = document.querySelector('.sh-dungeon-flashcard-front .sh-dungeon-flashcard-text')?.textContent || "";
            var backText = document.querySelector('.sh-dungeon-flashcard-back .sh-dungeon-flashcard-text')?.textContent || "";
            var isRevealed = document.querySelector('.sh-dungeon-flashcard-details')?.open === true;

            ctx.fillStyle = '#f59e0b';
            ctx.font = 'bold 12px "Courier New", Courier, monospace';
            ctx.fillText("⚔️ ENCOUNTER: KARTE GEZOGEN ⚔️", boxX + 20, boxY + 28);

            ctx.fillStyle = '#ffffff';
            ctx.font = '13px "Courier New", Courier, monospace';
            var lines = wrapText(ctx, "FRAGE: " + frontText, boxX + 20, boxY + 55, boxW - 40, 18, true);
            var questionHeight = lines.length * 18;

            if (!isRevealed) {
                var btnY = boxY + 55 + questionHeight + 35;
                var btnW = 180;
                var btnH = 32;
                var btnX = boxX + (boxW - btnW) / 2;

                optionBoxes.push({
                    type: 'reveal',
                    x: btnX,
                    y: btnY,
                    w: btnW,
                    h: btnH
                });

                var isHovered = window.dungeonHoveredOptionIndex === 0;

                ctx.fillStyle = isHovered ? '#1e293b' : '#020617';
                ctx.fillRect(btnX, btnY, btnW, btnH);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 1;
                ctx.strokeRect(btnX, btnY, btnW, btnH);

                ctx.fillStyle = '#ffffff';
                ctx.font = 'bold 12px "Courier New", Courier, monospace';
                ctx.textAlign = 'center';
                ctx.fillText((isHovered ? "▶ " : "") + "ZEIGE ANTWORT", btnX + btnW / 2, btnY + 20);
                ctx.textAlign = 'left';
            } else {
                ctx.fillStyle = '#2dd4bf';
                ctx.font = 'bold 11px "Courier New", Courier, monospace';
                ctx.fillText("ANTWORT:", boxX + 20, boxY + 55 + questionHeight + 15);

                ctx.fillStyle = '#ffffff';
                ctx.font = '13px "Courier New", Courier, monospace';
                var backLines = wrapText(ctx, backText, boxX + 20, boxY + 55 + questionHeight + 32, boxW - 40, 18, true);
                var answerHeight = backLines.length * 18;

                var btnW = 100;
                var btnH = 32;
                var startY = boxY + 55 + questionHeight + 32 + answerHeight + 25;

                // Falsch
                var xMissed = boxX + 80;
                optionBoxes.push({
                    type: 'answer',
                    value: false,
                    x: xMissed,
                    y: startY,
                    w: btnW,
                    h: btnH
                });

                var isHoveredMissed = window.dungeonHoveredOptionIndex === 0;
                ctx.fillStyle = isHoveredMissed ? '#7f1d1d' : '#0f172a';
                ctx.fillRect(xMissed, startY, btnW, btnH);
                ctx.strokeStyle = '#ef4444';
                ctx.lineWidth = 1;
                ctx.strokeRect(xMissed, startY, btnW, btnH);

                ctx.fillStyle = '#ef4444';
                ctx.font = 'bold 12px "Courier New", Courier, monospace';
                ctx.textAlign = 'center';
                ctx.fillText((isHoveredMissed ? "▶ " : "") + "FALSCH [M]", xMissed + btnW / 2, startY + 20);

                // Richtig
                var xGot = boxX + boxW - 180;
                optionBoxes.push({
                    type: 'answer',
                    value: true,
                    x: xGot,
                    y: startY,
                    w: btnW,
                    h: btnH
                });

                var isHoveredGot = window.dungeonHoveredOptionIndex === 1;
                ctx.fillStyle = isHoveredGot ? '#064e3b' : '#0f172a';
                ctx.fillRect(xGot, startY, btnW, btnH);
                ctx.strokeStyle = '#10b981';
                ctx.lineWidth = 1;
                ctx.strokeRect(xGot, startY, btnW, btnH);

                ctx.fillStyle = '#10b981';
                ctx.font = 'bold 12px "Courier New", Courier, monospace';
                ctx.textAlign = 'center';
                ctx.fillText((isHoveredGot ? "▶ " : "") + "RICHTIG [G]", xGot + btnW / 2, startY + 20);

                ctx.textAlign = 'left';
            }

        } else if (isQuiz) {
            var quizQuestion = document.querySelector('.sh-dungeon-quiz-question')?.textContent || "";
            var rawOptions = Array.from(document.querySelectorAll('.sh-dungeon-quiz-option')).map(function(opt) {
                var input = opt.querySelector('input');
                return {
                    text: opt.querySelector('span')?.textContent || "",
                    checked: input ? input.checked : false,
                    isCheckbox: input ? input.type === 'checkbox' : false
                };
            });

            ctx.fillStyle = '#2dd4bf';
            ctx.font = 'bold 12px "Courier New", Courier, monospace';
            ctx.fillText("⚔️ BATTLE MODE: QUIZ FRAGE ⚔️", boxX + 20, boxY + 28);

            ctx.fillStyle = '#ffffff';
            ctx.font = '13px "Courier New", Courier, monospace';
            var lines = wrapText(ctx, quizQuestion, boxX + 20, boxY + 52, boxW - 40, 18, true);
            var questionHeight = lines.length * 18;

            var optionsStartY = Math.max(320, boxY + 52 + questionHeight + 15);
            var optionHeight = 24;
            var optionSpacing = 6;

            rawOptions.forEach(function(opt, idx) {
                var optY = optionsStartY + idx * (optionHeight + optionSpacing);
                var isHovered = window.dungeonHoveredOptionIndex === idx;

                optionBoxes.push({
                    type: 'quiz_option',
                    index: idx,
                    x: boxX + 20,
                    y: optY,
                    w: boxW - 40,
                    h: optionHeight
                });

                if (isHovered) {
                    ctx.fillStyle = 'rgba(51, 65, 85, 0.35)';
                    ctx.fillRect(boxX + 15, optY - 2, boxW - 30, optionHeight + 4);
                }

                ctx.fillStyle = isHovered ? '#2dd4bf' : '#ffffff';
                ctx.font = 'bold 13px "Courier New", Courier, monospace';

                var bullet = opt.isCheckbox ? (opt.checked ? "[X]" : "[ ]") : (opt.checked ? "(•)" : "( )");
                var cursor = isHovered ? "▶ " : "  ";
                ctx.fillText(cursor + bullet + " " + opt.text, boxX + 20, optY + 16);
            });

            // Submit button
            var submitY = optionsStartY + rawOptions.length * (optionHeight + optionSpacing) + 12;
            var subW = 150;
            var subH = 30;
            var subX = boxX + (boxW - subW) / 2;

            optionBoxes.push({
                type: 'submit',
                x: subX,
                y: submitY,
                w: subW,
                h: subH
            });

            var isHoveredSubmit = window.dungeonHoveredOptionIndex === rawOptions.length;
            ctx.fillStyle = isHoveredSubmit ? '#134e4a' : '#020617';
            ctx.fillRect(subX, submitY, subW, subH);
            ctx.strokeStyle = '#2dd4bf';
            ctx.lineWidth = 1;
            ctx.strokeRect(subX, submitY, subW, subH);

            ctx.fillStyle = '#2dd4bf';
            ctx.font = 'bold 11px "Courier New", Courier, monospace';
            ctx.textAlign = 'center';
            ctx.fillText((isHoveredSubmit ? "▶ " : "") + "ABSENDEN [ENTER]", subX + subW / 2, submitY + 18);
            ctx.textAlign = 'left';
        }

        window.dungeonOptionBoxes = optionBoxes;
    }

    // ============================================================
    // Canvas Interactive Click and Hover Event Listeners Binding
    // ============================================================
    function bindCanvasInteractiveListeners(canvas) {
        canvas.addEventListener('mousemove', function (e) {
            var activeEncId = canvas.dataset.activeEncounterId;
            if (!activeEncId || window.dungeonSplashActive) return;

            var rect = canvas.getBoundingClientRect();
            var mouseX = (e.clientX - rect.left) * (canvas.width / rect.width);
            var mouseY = (e.clientY - rect.top) * (canvas.height / rect.height);

            var hoveredIdx = -1;
            var boxes = window.dungeonOptionBoxes || [];
            for (var i = 0; i < boxes.length; i++) {
                var box = boxes[i];
                if (mouseX >= box.x && mouseX <= box.x + box.w &&
                    mouseY >= box.y && mouseY <= box.y + box.h) {
                    if (box.type === 'quiz_option') {
                        hoveredIdx = box.index;
                    } else if (box.type === 'reveal') {
                        hoveredIdx = 0;
                    } else if (box.type === 'answer') {
                        hoveredIdx = box.value ? 1 : 0;
                    } else if (box.type === 'submit') {
                        hoveredIdx = boxes.length - 1;
                    }
                    break;
                }
            }

            if (hoveredIdx !== window.dungeonHoveredOptionIndex) {
                window.dungeonHoveredOptionIndex = hoveredIdx;
                if (hoveredIdx !== -1) {
                    DungeonAudio.playSelect();
                }
                triggerDraw();
            }
        });

        canvas.addEventListener('click', function (e) {
            var activeEncId = canvas.dataset.activeEncounterId;
            if (!activeEncId || window.dungeonSplashActive) return;

            var rect = canvas.getBoundingClientRect();
            var mouseX = (e.clientX - rect.left) * (canvas.width / rect.width);
            var mouseY = (e.clientY - rect.top) * (canvas.height / rect.height);

            var boxes = window.dungeonOptionBoxes || [];
            for (var i = 0; i < boxes.length; i++) {
                var box = boxes[i];
                if (mouseX >= box.x && mouseX <= box.x + box.w &&
                    mouseY >= box.y && mouseY <= box.y + box.h) {
                    
                    if (box.type === 'quiz_option') {
                        var form = document.querySelector('.sh-dungeon-quiz-form');
                        if (form) {
                            var inputs = form.querySelectorAll('input');
                            if (inputs[box.index]) {
                                inputs[box.index].checked = !inputs[box.index].checked;
                                DungeonAudio.playSelect();
                                triggerDraw();
                            }
                        }
                    } else if (box.type === 'submit') {
                        var form = document.querySelector('.sh-dungeon-quiz-form');
                        if (form) {
                            DungeonAudio.playSubmit();
                            htmx.trigger(form, 'submit');
                        }
                    } else if (box.type === 'reveal') {
                        var details = document.querySelector('.sh-dungeon-flashcard-details');
                        if (details) {
                            details.open = true;
                            DungeonAudio.playReveal();
                            triggerDraw();
                        }
                    } else if (box.type === 'answer') {
                        var valStr = box.value ? 'true' : 'false';
                        var button = document.querySelector('.sh-dungeon-answer-row button[value="' + valStr + '"]');
                        if (button) {
                            DungeonAudio.playSubmit();
                            button.click();
                        }
                    }
                    break;
                }
            }
        });
    }

    var drawQueued = false;
    function triggerDraw() {
        if (drawQueued) return;
        drawQueued = true;
        requestAnimationFrame(function () {
            drawQueued = false;
            drawDungeon();
        });
    }

    // Direct listener on DOMContentLoaded
    document.addEventListener('DOMContentLoaded', triggerDraw);

    // HTMX Lifecycle events
    function registerLifecycleHooks(element) {
        if (!element) return;
        element.addEventListener('htmx:afterSwap', function (e) {
            if (document.getElementById('dungeon-map-canvas')) {
                triggerDraw();
            }
        });
        element.addEventListener('htmx:afterSettle', function (e) {
            if (document.getElementById('dungeon-map-canvas')) {
                triggerDraw();
            }
        });
        element.addEventListener('htmx:load', function (e) {
            if (document.getElementById('dungeon-map-canvas')) {
                triggerDraw();
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
        var activeEncId = canvas.dataset.activeEncounterId;
        if (activeEncId && !window.dungeonSplashActive) {
            var isFlashcard = !!document.querySelector('.sh-dungeon-flashcard');
            var isQuiz = !!document.querySelector('.sh-dungeon-quiz-form');

            if (isQuiz) {
                if (['1', '2', '3', '4'].indexOf(e.key) !== -1) {
                    e.preventDefault();
                    var idx = parseInt(e.key, 10) - 1;
                    var form = document.querySelector('.sh-dungeon-quiz-form');
                    if (form) {
                        var inputs = form.querySelectorAll('input');
                        if (inputs[idx]) {
                            inputs[idx].checked = !inputs[idx].checked;
                            DungeonAudio.playSelect();
                            triggerDraw();
                        }
                    }
                    return;
                }
                if (e.key === 'Enter') {
                    e.preventDefault();
                    var form = document.querySelector('.sh-dungeon-quiz-form');
                    if (form) {
                        DungeonAudio.playSubmit();
                        htmx.trigger(form, 'submit');
                    }
                    return;
                }
            }

            if (isFlashcard) {
                var details = document.querySelector('.sh-dungeon-flashcard-details');
                var isRevealed = details ? details.open : false;

                if (e.key === ' ' || e.key === 'Spacebar') {
                    e.preventDefault();
                    if (details && !isRevealed) {
                        details.open = true;
                        DungeonAudio.playReveal();
                        triggerDraw();
                    }
                    return;
                }
                if (isRevealed) {
                    if (e.key === 'g' || e.key === 'G') {
                        e.preventDefault();
                        var button = document.querySelector('.sh-dungeon-answer-row button[value="true"]');
                        if (button) {
                            DungeonAudio.playSubmit();
                            button.click();
                        }
                        return;
                    }
                    if (e.key === 'm' || e.key === 'M') {
                        e.preventDefault();
                        var button = document.querySelector('.sh-dungeon-answer-row button[value="false"]');
                        if (button) {
                            DungeonAudio.playSubmit();
                            button.click();
                        }
                        return;
                    }
                }
            }
            return;
        }

        if (window.dungeonSplashActive) return;

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
