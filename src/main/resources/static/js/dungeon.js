(function () {
    var TILE_COLORS = {
        WALL: '#111827',
        FLOOR: '#334155',
        ENTRANCE: '#0f766e',
        ENCOUNTER: '#7c2d12',
        TREASURE: '#b45309',
        HEAL: '#166534',
        BOSS: '#7f1d1d'
    };

    var FOG_COLOR = '#1e293b';
    var EXPLORED_OVERLAY = 'rgba(30, 41, 59, 0.55)';

    function readMapTiles() {
        var script = document.getElementById('dungeon-map-state');
        if (!script) return null;
        try {
            return JSON.parse(script.textContent);
        } catch (e) {
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
        var canvas = document.getElementById('dungeon-map-canvas');
        if (!canvas) return;

        var ctx = canvas.getContext('2d');
        var mapTiles = readMapTiles();
        if (!mapTiles) return;

        var mapWidth = parseInt(canvas.dataset.mapWidth, 10);
        var mapHeight = parseInt(canvas.dataset.mapHeight, 10);
        var playerX = parseInt(canvas.dataset.playerX, 10);
        var playerY = parseInt(canvas.dataset.playerY, 10);
        var tileSize = Math.floor(canvas.width / mapWidth);

        var tileMap = {};
        for (var i = 0; i < mapTiles.length; i++) {
            var t = mapTiles[i];
            var key = t.x + ',' + t.y;
            tileMap[key] = t;
        }

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        for (var y = 0; y < mapHeight; y++) {
            for (var x = 0; x < mapWidth; x++) {
                var key = x + ',' + y;
                var tile = tileMap[key];
                if (!tile) continue;

                var px = x * tileSize;
                var py = y * tileSize;

                if (!tile.revealed) {
                    ctx.fillStyle = FOG_COLOR;
                    ctx.fillRect(px, py, tileSize, tileSize);
                    continue;
                }

                var color = TILE_COLORS[tile.type] || TILE_COLORS.FLOOR;
                ctx.fillStyle = color;
                ctx.fillRect(px, py, tileSize, tileSize);

                if (tile.revealed && !tile.explored) {
                    ctx.fillStyle = EXPLORED_OVERLAY;
                    ctx.fillRect(px, py, tileSize, tileSize);
                }

                drawTileIndicator(ctx, tile, px, py, tileSize);
            }
        }

        ctx.fillStyle = '#14b8a6';
        ctx.fillRect(playerX * tileSize + 4, playerY * tileSize + 4, tileSize - 8, tileSize - 8);
    }

    function drawTileIndicator(ctx, tile, px, py, size) {
        if (!tile.revealed) return;

        var cx = px + size / 2;
        var cy = py + size / 2;
        var s = size;

        switch (tile.type) {
            case 'ENCOUNTER':
                ctx.fillStyle = '#fcd34d';
                ctx.fillRect(cx - 4, cy - 4, 8, 8);
                break;
            case 'TREASURE':
                ctx.fillStyle = '#fbbf24';
                ctx.beginPath();
                ctx.moveTo(cx, cy - 5);
                ctx.lineTo(cx + 4, cy + 3);
                ctx.lineTo(cx - 4, cy + 3);
                ctx.closePath();
                ctx.fill();
                break;
            case 'HEAL':
                ctx.strokeStyle = '#4ade80';
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.moveTo(cx, cy - 5);
                ctx.lineTo(cx, cy + 5);
                ctx.moveTo(cx - 5, cy);
                ctx.lineTo(cx + 5, cy);
                ctx.stroke();
                break;
            case 'BOSS':
                ctx.fillStyle = '#fca5a5';
                ctx.beginPath();
                ctx.arc(cx, cy, 5, 0, Math.PI * 2);
                ctx.fill();
                break;
            case 'ENTRANCE':
                ctx.strokeStyle = '#5eead4';
                ctx.lineWidth = 2;
                ctx.strokeRect(cx - 5, cy - 5, 10, 10);
                break;
        }
    }

    document.addEventListener('DOMContentLoaded', drawDungeon);
    document.addEventListener('htmx:afterSwap', function (e) {
        if (e.detail.target && e.detail.target.id === 'dungeon-session-content') {
            drawDungeon();
        }
        if (!e.detail.target && document.getElementById('dungeon-map-canvas')) {
            drawDungeon();
        }
    });

    document.addEventListener('keydown', function (e) {
        var canvas = document.getElementById('dungeon-map-canvas');
        if (!canvas) return;

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
