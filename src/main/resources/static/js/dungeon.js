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
        },
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
        ],
        CHAMPION: [
            "......kkkk......",
            "....kkyyyyk.....",
            "...kyyyyyyyk....",
            "..kyyyyyyyykk...",
            "..kyyyyyyyyyyk..",
            ".kyywwwwyyyyykk.",
            ".kyywwwwyyyyyyk.",
            "kyyyywwyyyyyyyk.",
            "kyyyywwyyyyyyyk.",
            "kyyyyyyyyyyyyyyk",
            "kyyyyyyyyyyyyyyk",
            ".kyyyyyyyyyyyyk.",
            "..kyyyyyyyyyyk..",
            "...kyyyyyyyyk...",
            "....kyyyy.yyk...",
            "....kk....kk...."
        ]
    };

    function drawPixelSprite(ctx, spriteName, px, py, size) {
        if (spriteName === 'ELITE') {
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
            return;
        }
        if (spriteName === 'TRAP_TELEGRAPHED') {
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
            return;
        }
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
    function submitMove(direction) {
        htmx.ajax('POST', '/dungeon/move', {
            target: '#dungeon-session-content',
            swap: 'outerHTML',
            values: { direction: direction }
        });
    }

    // ============================================================
    // Minimap + Room Canvas Rendering
    // ============================================================
    function renderMinimap() {
        var canvas = document.getElementById('dungeon-minimap-canvas');
        if (!canvas) return;
        var ctx = canvas.getContext('2d');
        var lattice = parseInt(canvas.dataset.lattice, 10);
        var stateNode = document.getElementById('dungeon-minimap-state');
        if (!stateNode) return;
        var rooms = JSON.parse(stateNode.textContent || '[]');

        ctx.fillStyle = '#0a0a14';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        var cellSize = Math.floor(Math.min(canvas.width, canvas.height) / lattice);

        for (var rIdx = 0; rIdx < rooms.length; rIdx++) {
            var r = rooms[rIdx];
            for (var dir in r.doors) {
                if (!r.doors.hasOwnProperty(dir)) continue;
                var neighborId = r.doors[dir];
                var neighbor = null;
                for (var nIdx = 0; nIdx < rooms.length; nIdx++) {
                    if (rooms[nIdx].id === neighborId) { neighbor = rooms[nIdx]; break; }
                }
                if (!neighbor) continue;
                drawDoorConnector(ctx, r, neighbor, cellSize);
            }
        }

        for (var rIdx2 = 0; rIdx2 < rooms.length; rIdx2++) {
            drawRoomCell(ctx, rooms[rIdx2], cellSize);
        }
    }

    function drawRoomCell(ctx, room, cellSize) {
        var x = room.gridX * cellSize;
        var y = room.gridY * cellSize;
        var pad = 2;
        ctx.fillStyle = colorForRoom(room);
        ctx.fillRect(x + pad, y + pad, cellSize - pad * 2, cellSize - pad * 2);
        if (room.isCurrent) {
            ctx.strokeStyle = '#fff';
            ctx.lineWidth = 2;
            ctx.strokeRect(x + pad, y + pad, cellSize - pad * 2, cellSize - pad * 2);
        }
        ctx.fillStyle = '#000';
        ctx.font = '10px monospace';
        if (room.type === 'UNKNOWN') {
            ctx.fillStyle = '#aaa';
            ctx.fillText('?', x + cellSize / 2 - 4, y + cellSize / 2 + 4);
        } else {
            var glyphMap = {
                ENTRANCE: '\u2B21', COMBAT: '\u2694', ELITE: '\u2726',
                TREASURE: '\u25C6', HEAL: '\u2665', SHOP: '$',
                BOSS: '\u2620', SECRET: '?'
            };
            var glyph = glyphMap[room.type] || '';
            ctx.fillText(glyph, x + cellSize / 2 - 4, y + cellSize / 2 + 4);
        }
    }

    function colorForRoom(r) {
        if (r.type === 'UNKNOWN') return '#3a3a48';
        if (r.cleared) return '#4a4a55';
        switch (r.type) {
            case 'ENTRANCE': return '#2c5a8a';
            case 'COMBAT':   return '#a32d2d';
            case 'ELITE':    return '#7a2da3';
            case 'TREASURE': return '#c79b2a';
            case 'HEAL':     return '#2da366';
            case 'SHOP':     return '#2d8aa3';
            case 'BOSS':     return '#d63a3a';
            case 'SECRET':   return '#888';
            default:         return '#3a3a48';
        }
    }

    function drawDoorConnector(ctx, a, b, cellSize) {
        var ax = a.gridX * cellSize + cellSize / 2;
        var ay = a.gridY * cellSize + cellSize / 2;
        var bx = b.gridX * cellSize + cellSize / 2;
        var by = b.gridY * cellSize + cellSize / 2;
        ctx.strokeStyle = '#666';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(ax, ay);
        ctx.lineTo(bx, by);
        ctx.stroke();
    }

    function renderRoomCanvas() {
        var canvas = document.getElementById('dungeon-room-canvas');
        if (!canvas) return;
        var activeEncounterId = canvas.dataset.activeEncounterId;
        if (activeEncounterId && activeEncounterId.length > 0) {
            if (typeof renderJRPGCombat === 'function') {
                renderJRPGCombat(canvas, canvas.getContext('2d'), activeEncounterId);
            }
            return;
        }
        renderRoomScenery(canvas);
    }

    function renderRoomScenery(canvas) {
        var ctx = canvas.getContext('2d');
        ctx.fillStyle = backgroundFor(canvas.dataset.currentRoomType);
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        drawRoomCenterGlyph(ctx, canvas);
    }

    function backgroundFor(type) {
        switch (type) {
            case 'TREASURE': return '#3a2e10';
            case 'HEAL':     return '#0f2418';
            case 'SHOP':     return '#0d2630';
            case 'SECRET':   return '#1a1a22';
            case 'BOSS':     return '#2a0a0a';
            default:         return '#10101a';
        }
    }

    function drawRoomCenterGlyph(ctx, canvas) {
        ctx.fillStyle = '#fff';
        ctx.font = '64px monospace';
        ctx.textAlign = 'center';
        var glyphMap = {
            TREASURE: '\u25C6', HEAL: '\u2665', SHOP: '$',
            SECRET: '?', ENTRANCE: '\u2B21', BOSS: '\u2620'
        };
        var glyph = glyphMap[canvas.dataset.currentRoomType] || '';
        ctx.fillText(glyph, canvas.width / 2, canvas.height / 2);
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
        
        var gauntletTotal = parseInt(canvas.dataset.gauntletTotal || '0', 10);
        var title = window.dungeonSplashBoss
            ? "!!! BOSS-KAMPF !!!"
            : (gauntletTotal > 0 ? "ELITE CHALLENGE!" : "⚔️ GEGNER GEFUNDEN ⚔️");
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
        var scale = canvas.width / 512.0;

        // Clear canvas with deep dark blue background
        ctx.fillStyle = '#020617';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Draw scanlines
        ctx.fillStyle = 'rgba(51, 65, 85, 0.05)';
        for (var y = 0; y < canvas.height; y += Math.floor(4 * scale)) {
            ctx.fillRect(0, y, canvas.width, Math.floor(2 * scale));
        }

        // Fetch values from DOM
        var health = parseInt(document.querySelector('.sh-dungeon-hud-health')?.textContent || '5', 10);
        var score = parseInt(document.querySelector('.sh-dungeon-hud-score')?.textContent || '0', 10);
        var progress = document.querySelector('.sh-dungeon-hud-progress')?.textContent || '0/0';
        var activeEncBoss = canvas.dataset.activeEncounterBoss === 'true';
        var gauntletPos = parseInt(canvas.dataset.gauntletPosition || '0', 10);
        var gauntletTotal = parseInt(canvas.dataset.gauntletTotal || '0', 10);
        var enemyName = (window.dungeonSplashMonster || (activeEncBoss ? "DRAGON" : "SLIME")).toUpperCase();

        // Draw Player HUD Panel (Left)
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2 * scale;
        ctx.strokeRect(20 * scale, 20 * scale, 220 * scale, 65 * scale);
        ctx.fillStyle = 'rgba(15, 23, 42, 0.75)';
        ctx.fillRect(20 * scale, 20 * scale, 220 * scale, 65 * scale);

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
        ctx.textAlign = 'left';
        ctx.fillText("HELD (SCORE: " + score + ")", 30 * scale, 40 * scale);

        // HP progress bar
        ctx.fillStyle = '#1e293b';
        ctx.fillRect(30 * scale, 48 * scale, 120 * scale, 12 * scale);
        var healthPercent = Math.max(0, Math.min(1, health / 5.0));
        ctx.fillStyle = healthPercent > 0.5 ? '#10b981' : (healthPercent > 0.2 ? '#f59e0b' : '#ef4444');
        ctx.fillRect(30 * scale, 48 * scale, 120 * scale * healthPercent, 12 * scale);
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1 * scale;
        ctx.strokeRect(30 * scale, 48 * scale, 120 * scale, 12 * scale);

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold ' + Math.floor(12 * scale) + 'px "Courier New", Courier, monospace';
        ctx.fillText("HP: " + health + "/5", 160 * scale, 58 * scale);

        // Draw Enemy HUD Panel (Right)
        ctx.strokeStyle = activeEncBoss ? '#ef4444' : '#ffffff';
        ctx.lineWidth = 2 * scale;
        ctx.strokeRect(canvas.width - 240 * scale, 20 * scale, 220 * scale, 65 * scale);
        ctx.fillStyle = 'rgba(15, 23, 42, 0.75)';
        ctx.fillRect(canvas.width - 240 * scale, 20 * scale, 220 * scale, 65 * scale);

        ctx.fillStyle = activeEncBoss ? '#ef4444' : '#ffffff';
        ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
        ctx.fillText(enemyName, canvas.width - 230 * scale, 40 * scale);

        // Enemy progress/health bar
        ctx.fillStyle = '#1e293b';
        ctx.fillRect(canvas.width - 230 * scale, 48 * scale, 120 * scale, 12 * scale);
        
        var progParts = progress.split('/');
        var progressPercent = 0.5;
        if (progParts.length === 2) {
            var solved = parseInt(progParts[0], 10);
            var total = Math.max(1, parseInt(progParts[1], 10));
            progressPercent = Math.max(0, Math.min(1, solved / total));
        }
        ctx.fillStyle = activeEncBoss ? '#ef4444' : '#2dd4bf';
        ctx.fillRect(canvas.width - 230 * scale, 48 * scale, 120 * scale * (1 - progressPercent + 0.1), 12 * scale);
        ctx.strokeStyle = activeEncBoss ? '#ef4444' : '#ffffff';
        ctx.lineWidth = 1 * scale;
        ctx.strokeRect(canvas.width - 230 * scale, 48 * scale, 120 * scale, 12 * scale);

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold ' + Math.floor(12 * scale) + 'px "Courier New", Courier, monospace';
        ctx.fillText("HP: " + Math.round((1 - progressPercent) * 100) + "%", canvas.width - 100 * scale, 58 * scale);

        // Elite progress badge
        if (gauntletTotal > 0 && !activeEncBoss) {
            ctx.fillStyle = '#fbbf24';
            ctx.font = 'bold ' + Math.floor(10 * scale) + 'px "Courier New", Courier, monospace';
            ctx.textAlign = 'right';
            ctx.fillText("ELITE " + gauntletPos + "/" + gauntletTotal, canvas.width - 30 * scale, 40 * scale);
            ctx.textAlign = 'left';
        }

        // Enemy Sprite (Bobbing)
        var bob = Math.sin(Date.now() / 180) * 10;
        var monsterSprite;
        if (gauntletTotal > 0 && !activeEncBoss) {
            monsterSprite = 'CHAMPION';
        } else if (activeEncBoss) {
            monsterSprite = 'DRAGON';
        } else {
            monsterSprite = window.dungeonSplashMonster || 'SLIME';
        }
        var spriteSize = activeEncBoss ? 128 * scale : 96 * scale;
        var sx = (canvas.width - spriteSize) / 2;
        var sy = 100 * scale + bob * scale;
        drawPixelSprite(ctx, monsterSprite, sx, sy, spriteSize);

        // Dialog Message Box
        var boxX = 20 * scale;
        var boxY = 230 * scale;
        var boxW = canvas.width - 40 * scale;
        var boxH = canvas.height - boxY - 20 * scale;

        ctx.fillStyle = '#020617';
        ctx.fillRect(boxX, boxY, boxW, boxH);

        // Thick Double-Line retro borders
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 4 * scale;
        ctx.strokeRect(boxX, boxY, boxW, boxH);
        ctx.strokeStyle = '#020617';
        ctx.lineWidth = 2 * scale;
        ctx.strokeRect(boxX + 3 * scale, boxY + 3 * scale, boxW - 6 * scale, boxH - 6 * scale);
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1 * scale;
        ctx.strokeRect(boxX + 5 * scale, boxY + 5 * scale, boxW - 10 * scale, boxH - 10 * scale);

        var isFlashcard = !!document.querySelector('.sh-dungeon-flashcard');
        var isQuiz = !!document.querySelector('.sh-dungeon-quiz-form');

        ctx.fillStyle = '#ffffff';
        ctx.textAlign = 'left';

        var optionBoxes = [];
        var startY = boxY + boxH - 50 * scale;

        if (isFlashcard) {
            var frontText = document.querySelector('.sh-dungeon-flashcard-front .sh-dungeon-flashcard-text')?.textContent || "";
            var backText = document.querySelector('.sh-dungeon-flashcard-back .sh-dungeon-flashcard-text')?.textContent || "";
            var isRevealed = document.querySelector('.sh-dungeon-flashcard-details')?.open === true;

            // Loop to dynamically size fonts to fit available height!
            var fontSize = 15 * scale;
            var lineHeight = 20 * scale;
            var maxTextHeight = startY - (boxY + 60 * scale);
            var textHeight = 9999;
            var questionLines, answerLines;

            while (fontSize >= 9 * scale) {
                ctx.font = 'bold ' + Math.floor(fontSize) + 'px "Courier New", Courier, monospace';
                questionLines = wrapText(ctx, "FRAGE: " + frontText, boxX + 20 * scale, boxY + 55 * scale, boxW - 40 * scale, lineHeight, false);
                var qH = questionLines.length * lineHeight;
                
                if (isRevealed) {
                    answerLines = wrapText(ctx, backText, boxX + 20 * scale, boxY + 55 * scale + qH + 20 * scale, boxW - 40 * scale, lineHeight, false);
                    textHeight = qH + 20 * scale + answerLines.length * lineHeight;
                } else {
                    textHeight = qH;
                }

                if (textHeight <= maxTextHeight) {
                    break;
                }
                fontSize -= 0.5 * scale;
                lineHeight -= 0.8 * scale;
            }

            ctx.fillStyle = '#f59e0b';
            ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
            ctx.fillText("⚔️ ENCOUNTER: KARTE GEZOGEN ⚔️", boxX + 20 * scale, boxY + 28 * scale);

            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold ' + Math.floor(fontSize) + 'px "Courier New", Courier, monospace';
            var lines = wrapText(ctx, "FRAGE: " + frontText, boxX + 20 * scale, boxY + 55 * scale, boxW - 40 * scale, lineHeight, true);
            var questionHeight = lines.length * lineHeight;

            if (!isRevealed) {
                var btnW = 180 * scale;
                var btnH = 32 * scale;
                var btnX = boxX + (boxW - btnW) / 2;

                optionBoxes.push({
                    type: 'reveal',
                    x: btnX,
                    y: startY,
                    w: btnW,
                    h: btnH
                });

                var isHovered = window.dungeonHoveredOptionIndex === 0;

                ctx.fillStyle = isHovered ? '#1e293b' : '#020617';
                ctx.fillRect(btnX, startY, btnW, btnH);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 1 * scale;
                ctx.strokeRect(btnX, startY, btnW, btnH);

                ctx.fillStyle = '#ffffff';
                ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
                ctx.textAlign = 'center';
                ctx.fillText((isHovered ? "▶ " : "") + "ZEIGE ANTWORT [SPACE]", btnX + btnW / 2, startY + 20 * scale);
                ctx.textAlign = 'left';
            } else {
                ctx.fillStyle = '#2dd4bf';
                ctx.font = 'bold ' + Math.floor(12 * scale) + 'px "Courier New", Courier, monospace';
                ctx.fillText("ANTWORT:", boxX + 20 * scale, boxY + 55 * scale + questionHeight + 15 * scale);

                ctx.fillStyle = '#ffffff';
                ctx.font = 'bold ' + Math.floor(fontSize) + 'px "Courier New", Courier, monospace';
                wrapText(ctx, backText, boxX + 20 * scale, boxY + 55 * scale + questionHeight + 32 * scale, boxW - 40 * scale, lineHeight, true);

                var btnW = 110 * scale;
                var btnH = 32 * scale;

                // Falsch
                var xMissed = boxX + 60 * scale;
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
                ctx.lineWidth = 1 * scale;
                ctx.strokeRect(xMissed, startY, btnW, btnH);

                ctx.fillStyle = '#ef4444';
                ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
                ctx.textAlign = 'center';
                ctx.fillText((isHoveredMissed ? "▶ " : "") + "FALSCH [M]", xMissed + btnW / 2, startY + 20 * scale);

                // Richtig
                var xGot = boxX + boxW - 170 * scale;
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
                ctx.lineWidth = 1 * scale;
                ctx.strokeRect(xGot, startY, btnW, btnH);

                ctx.fillStyle = '#10b981';
                ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
                ctx.textAlign = 'center';
                ctx.fillText((isHoveredGot ? "▶ " : "") + "RICHTIG [G]", xGot + btnW / 2, startY + 20 * scale);

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

            // Loop to dynamically size fonts to fit available height!
            var fontSize = 15 * scale;
            var lineHeight = 20 * scale;
            var maxTextHeight = startY - (boxY + 60 * scale) - (rawOptions.length * 32 * scale);
            var questionLines;

            while (fontSize >= 9 * scale) {
                ctx.font = 'bold ' + Math.floor(fontSize) + 'px "Courier New", Courier, monospace';
                questionLines = wrapText(ctx, quizQuestion, boxX + 20 * scale, boxY + 52 * scale, boxW - 40 * scale, lineHeight, false);
                var qH = questionLines.length * lineHeight;
                
                if (qH <= maxTextHeight || fontSize <= 9 * scale) {
                    break;
                }
                fontSize -= 0.5 * scale;
                lineHeight -= 0.8 * scale;
            }

            ctx.fillStyle = '#2dd4bf';
            ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
            ctx.fillText("⚔️ BATTLE MODE: QUIZ FRAGE ⚔️", boxX + 20 * scale, boxY + 28 * scale);

            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold ' + Math.floor(fontSize) + 'px "Courier New", Courier, monospace';
            var lines = wrapText(ctx, quizQuestion, boxX + 20 * scale, boxY + 52 * scale, boxW - 40 * scale, lineHeight, true);
            var questionHeight = lines.length * lineHeight;

            var optionsStartY = Math.max(300 * scale, boxY + 52 * scale + questionHeight + 12 * scale);
            var optionHeight = 26 * scale;
            var optionSpacing = 5 * scale;

            rawOptions.forEach(function(opt, idx) {
                var optY = optionsStartY + idx * (optionHeight + optionSpacing);
                var isHovered = window.dungeonHoveredOptionIndex === idx;

                optionBoxes.push({
                    type: 'quiz_option',
                    index: idx,
                    x: boxX + 20 * scale,
                    y: optY,
                    w: boxW - 40 * scale,
                    h: optionHeight
                });

                if (isHovered) {
                    ctx.fillStyle = 'rgba(51, 65, 85, 0.35)';
                    ctx.fillRect(boxX + 15 * scale, optY - 2 * scale, boxW - 30 * scale, optionHeight + 4 * scale);
                }

                ctx.fillStyle = isHovered ? '#2dd4bf' : '#ffffff';
                ctx.font = 'bold ' + Math.floor(fontSize) + 'px "Courier New", Courier, monospace';

                var bullet = opt.isCheckbox ? (opt.checked ? "[X]" : "[ ]") : (opt.checked ? "(•)" : "( )");
                var cursor = isHovered ? "▶ " : "  ";
                ctx.fillText(cursor + bullet + " " + opt.text, boxX + 20 * scale, optY + 18 * scale);
            });

            var subW = 180 * scale;
            var subH = 34 * scale;
            var subX = boxX + (boxW - subW) / 2;

            optionBoxes.push({
                type: 'submit',
                x: subX,
                y: startY,
                w: subW,
                h: subH
            });

            var isHoveredSubmit = window.dungeonHoveredOptionIndex === rawOptions.length;
            ctx.fillStyle = isHoveredSubmit ? '#134e4a' : '#020617';
            ctx.fillRect(subX, startY, subW, subH);
            ctx.strokeStyle = '#2dd4bf';
            ctx.lineWidth = 1 * scale;
            ctx.strokeRect(subX, startY, subW, subH);

            ctx.fillStyle = '#2dd4bf';
            ctx.font = 'bold ' + Math.floor(12 * scale) + 'px "Courier New", Courier, monospace';
            ctx.textAlign = 'center';
            ctx.fillText((isHoveredSubmit ? "▶ " : "") + "ABSENDEN [ENTER]", subX + subW / 2, startY + 21 * scale);
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
            dungeonInit();
        });
    }

    // ============================================================
    // Entry Point
    // ============================================================
    function dungeonInit() {
        renderMinimap();
        renderRoomCanvas();
        var canvas = document.getElementById('dungeon-room-canvas');
        if (canvas && !canvas.dungeonListenersBound) {
            canvas.dungeonListenersBound = true;
            bindCanvasInteractiveListeners(canvas);
        }
    }

    if (typeof htmx !== 'undefined' && htmx) {
        htmx.onLoad(dungeonInit);
    } else {
        document.addEventListener('DOMContentLoaded', dungeonInit);
    }

    // Keyboard support
    document.addEventListener('keydown', function (e) {
        var canvas = document.getElementById('dungeon-room-canvas');
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
