// dungeon-minimap.js — minimap canvas renderer
var DungeonMinimap = (function () {
    function drawMinimap() {
        var canvas = document.getElementById('dungeon-minimap-canvas');
        if (!canvas) return;
        var ctx = canvas.getContext('2d');
        var lattice = parseInt(canvas.dataset.lattice, 10);
        var stateNode = document.getElementById('dungeon-minimap-state');
        if (!stateNode) return;
        var rooms = JSON.parse(stateNode.textContent || '[]');
        var bossSealed = canvas.dataset.bossSealed === 'true';

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
            drawRoomCell(ctx, rooms[rIdx2], cellSize, bossSealed);
        }
    }

    function drawRoomCell(ctx, room, cellSize, bossSealed) {
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
                BOSS: '\u2620'
            };
            var glyph = glyphMap[room.type] || '';
            ctx.fillText(glyph, x + cellSize / 2 - 4, y + cellSize / 2 + 4);
        }
        if (room.hasRevenant) {
            var rcx = x + cellSize / 2;
            var rcy = y + cellSize / 2;
            ctx.fillStyle = '#e040fb';
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 1.5;
            ctx.beginPath();
            ctx.arc(rcx, rcy, Math.max(3, cellSize * 0.18), 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();
        }
        if (bossSealed && room.type === 'BOSS') {
            drawLock(ctx, x + cellSize / 2, y + cellSize / 2, cellSize * 0.5);
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

    function drawLock(ctx, cx, cy, s) {
        ctx.strokeStyle = '#ffd54a';
        ctx.lineWidth = Math.max(1.5, s * 0.14);
        ctx.beginPath();
        ctx.arc(cx, cy - s * 0.12, s * 0.26, Math.PI, 0);
        ctx.stroke();
        ctx.fillStyle = '#ffd54a';
        ctx.fillRect(cx - s * 0.38, cy - s * 0.05, s * 0.76, s * 0.55);
        ctx.fillStyle = '#5a3d00';
        ctx.fillRect(cx - s * 0.06, cy + s * 0.1, s * 0.12, s * 0.22);
    }

    return {
        draw: drawMinimap,
    };
})();
