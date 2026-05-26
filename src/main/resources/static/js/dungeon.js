(function () {
    // Dynamic color theme manager for high-end aesthetic styling
    function getThemeColors() {
        var isDark = document.documentElement.dataset.theme === 'dark';
        if (isDark) {
            return {
                tileColors: {
                    WALL: '#0f172a',        // Deep, rich dark slate wall
                    FLOOR: '#1a1f2c',       // High-end dark theme deck background
                    ENTRANCE: '#115e59',   // Forest teal entrance
                    ENCOUNTER: '#451a03',  // Dark amber encounter floor
                    TREASURE: '#713f12',   // Warm brass treasure floor
                    HEAL: '#064e3b',       // Emerald/forest green heal floor
                    BOSS: '#450a0a'        // Crimson boss floor
                },
                fogColor: '#020617',       // Near pitch black fog
                exploredOverlay: 'rgba(2, 6, 23, 0.45)', // Overlay dims unexplored but revealed tiles
                gridColor: 'rgba(51, 65, 85, 0.25)', // Thin, crisp grid lines
                playerColor: '#2dd4bf',    // Bright electric neon-teal player
                playerGlowColor: 'rgba(45, 212, 191, 0.65)',
                indicatorColors: {
                    ENCOUNTER: '#fbbf24',  // Bright amber
                    TREASURE: '#f59e0b',   // Bright orange
                    HEAL: '#34d399',       // Mint emerald
                    BOSS: '#ef4444',       // Vibrant blood red
                    ENTRANCE: '#2dd4bf'    // Glowing teal
                }
            };
        } else {
            return {
                tileColors: {
                    WALL: '#94a3b8',       // Light slate wall
                    FLOOR: '#f8fafc',      // Clean off-white paper floor
                    ENTRANCE: '#ccfbf1',   // Clean pale teal
                    ENCOUNTER: '#ffedd5',  // Warm light orange
                    TREASURE: '#fef9c3',   // Soft golden yellow
                    HEAL: '#dcfce7',       // Soft sage green
                    BOSS: '#fee2e2'        // Soft pastel rose
                },
                fogColor: '#e2e8f0',       // Light gray fog
                exploredOverlay: 'rgba(255, 255, 255, 0.4)',
                gridColor: 'rgba(148, 163, 184, 0.2)',
                playerColor: '#0f766e',    // Deep solid teal player
                playerGlowColor: 'rgba(15, 118, 110, 0.35)',
                indicatorColors: {
                    ENCOUNTER: '#d97706',  // Amber
                    TREASURE: '#b45309',   // Bronze
                    HEAL: '#15803d',       // Deep green
                    BOSS: '#dc2626',       // Crimson red
                    ENTRANCE: '#0f766e'    // Forest teal
                }
            };
        }
    }

    function readMapTiles() {
        var script = document.getElementById('dungeon-map-state');
        if (!script) {
            console.log("readMapTiles: script element '#dungeon-map-state' not found");
            return null;
        }
        try {
            var data = JSON.parse(script.textContent);
            return data;
        } catch (e) {
            console.error("readMapTiles: Failed to parse JSON text content:", script.textContent, e);
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
        console.log("drawDungeon starting execution...");
        try {
            var canvas = document.getElementById('dungeon-map-canvas');
            if (!canvas) {
                console.log("drawDungeon abort: canvas '#dungeon-map-canvas' not found in DOM");
                return;
            }

            var ctx = canvas.getContext('2d');
            var mapTiles = readMapTiles();
            if (!mapTiles) {
                console.log("drawDungeon abort: mapTiles could not be loaded");
                return;
            }

            var mapWidth = parseInt(canvas.dataset.mapWidth, 10);
            var mapHeight = parseInt(canvas.dataset.mapHeight, 10);
            var playerX = parseInt(canvas.dataset.playerX, 10);
            var playerY = parseInt(canvas.dataset.playerY, 10);
            var tileSize = Math.floor(canvas.width / mapWidth);

            console.log("drawDungeon parameters successfully read:", {
                mapWidth: mapWidth,
                mapHeight: mapHeight,
                playerX: playerX,
                playerY: playerY,
                tileSize: tileSize,
                tilesCount: mapTiles.length
            });

            var tileMap = {};
            for (var i = 0; i < mapTiles.length; i++) {
                var t = mapTiles[i];
                var key = t.x + ',' + t.y;
                tileMap[key] = t;
            }

            var theme = getThemeColors();

            ctx.clearRect(0, 0, canvas.width, canvas.height);

            // 1. Draw tiles (wall, floor, fog, type colors)
            for (var y = 0; y < mapHeight; y++) {
                for (var x = 0; x < mapWidth; x++) {
                    var key = x + ',' + y;
                    var tile = tileMap[key];
                    if (!tile) continue;

                    var px = x * tileSize;
                    var py = y * tileSize;

                    if (!tile.revealed) {
                        ctx.fillStyle = theme.fogColor;
                        ctx.fillRect(px, py, tileSize, tileSize);
                        continue;
                    }

                    var color = theme.tileColors[tile.type] || theme.tileColors.FLOOR;
                    ctx.fillStyle = color;
                    ctx.fillRect(px, py, tileSize, tileSize);

                    if (tile.revealed && !tile.explored) {
                        ctx.fillStyle = theme.exploredOverlay;
                        ctx.fillRect(px, py, tileSize, tileSize);
                    }

                    drawTileIndicator(ctx, tile, px, py, tileSize, theme);
                }
            }

            // 2. Draw modern high-end grid lines to give depth to the pixel look
            ctx.strokeStyle = theme.gridColor;
            ctx.lineWidth = 1;
            ctx.beginPath();
            for (var x = 0; x <= mapWidth; x++) {
                var px = x * tileSize;
                ctx.moveTo(px, 0);
                ctx.lineTo(px, mapHeight * tileSize);
            }
            for (var y = 0; y <= mapHeight; y++) {
                var py = y * tileSize;
                ctx.moveTo(0, py);
                ctx.lineTo(mapWidth * tileSize, py);
            }
            ctx.stroke();

            // 3. Draw player with glow shadow and a crisp dual-tone inner-core avatar
            var px = playerX * tileSize;
            var py = playerY * tileSize;

            ctx.save();
            ctx.shadowColor = theme.playerGlowColor;
            ctx.shadowBlur = 8;
            ctx.fillStyle = theme.playerColor;

            var padding = 6;
            var playerSize = tileSize - padding * 2;

            ctx.fillRect(px + padding, py + padding, playerSize, playerSize);

            // Draw internal high-contrast center dot
            ctx.shadowBlur = 0;
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(px + padding + 3, py + padding + 3, playerSize - 6, playerSize - 6);
            ctx.restore();

            console.log("drawDungeon execution successfully completed");
        } catch (err) {
            console.error("drawDungeon error caught:", err);
        }
    }

    function drawTileIndicator(ctx, tile, px, py, size, theme) {
        if (!tile.revealed) return;

        var cx = px + size / 2;
        var cy = py + size / 2;

        switch (tile.type) {
            case 'ENCOUNTER':
                ctx.fillStyle = theme.indicatorColors.ENCOUNTER;
                ctx.fillRect(cx - 4, cy - 4, 8, 8);
                break;
            case 'TREASURE':
                ctx.fillStyle = theme.indicatorColors.TREASURE;
                ctx.beginPath();
                ctx.moveTo(cx, cy - 5);
                ctx.lineTo(cx + 4, cy + 3);
                ctx.lineTo(cx - 4, cy + 3);
                ctx.closePath();
                ctx.fill();
                break;
            case 'HEAL':
                ctx.strokeStyle = theme.indicatorColors.HEAL;
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.moveTo(cx, cy - 5);
                ctx.lineTo(cx, cy + 5);
                ctx.moveTo(cx - 5, cy);
                ctx.lineTo(cx + 5, cy);
                ctx.stroke();
                break;
            case 'BOSS':
                ctx.fillStyle = theme.indicatorColors.BOSS;
                ctx.beginPath();
                ctx.arc(cx, cy, 5, 0, Math.PI * 2);
                ctx.fill();
                break;
            case 'ENTRANCE':
                ctx.strokeStyle = theme.indicatorColors.ENTRANCE;
                ctx.lineWidth = 2;
                ctx.strokeRect(cx - 5, cy - 5, 10, 10);
                break;
        }
    }

    // Direct listener on DOMContentLoaded
    document.addEventListener('DOMContentLoaded', drawDungeon);

    // Multi-lifecycle event triggers on document body & document to ensure absolute reliability
    function registerLifecycleHooks(element) {
        if (!element) return;
        element.addEventListener('htmx:afterSwap', function (e) {
            console.log("htmx:afterSwap event triggered on target:", e.detail.target);
            if (document.getElementById('dungeon-map-canvas')) {
                drawDungeon();
            }
        });
        element.addEventListener('htmx:afterSettle', function (e) {
            console.log("htmx:afterSettle event triggered on target:", e.detail.target);
            if (document.getElementById('dungeon-map-canvas')) {
                drawDungeon();
            }
        });
        element.addEventListener('htmx:load', function (e) {
            console.log("htmx:load event triggered on element:", e.target);
            if (document.getElementById('dungeon-map-canvas')) {
                drawDungeon();
            }
        });
    }

    // Register on both document level and body level for absolute safety coverage
    registerLifecycleHooks(document);
    if (document.body) {
        registerLifecycleHooks(document.body);
    } else {
        document.addEventListener('DOMContentLoaded', function () {
            registerLifecycleHooks(document.body);
        });
    }

    document.addEventListener('keydown', function (e) {
        var canvas = document.getElementById('dungeon-map-canvas');
        if (!canvas) return;

        if (document.querySelector('.sh-dungeon-encounter-active')) return;

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
