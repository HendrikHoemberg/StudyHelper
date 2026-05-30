// dungeon-renderer.js — exploration and combat canvas renderer.
// Pure render layer: every draw function receives an explicit state object and
// performs NO DOM or window queries. renderCombat returns the interactive option
// boxes so the caller (combat-hud) can publish them for the input layer.
var DungeonRenderer = (function () {
    var canvas, ctx;

    function init() {
        canvas = document.getElementById('dungeon-room-canvas');
        if (canvas) ctx = canvas.getContext('2d');
    }

    function drawPixelSprite(spriteName, px, py, size) {
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
            drawFloorTile(px, py, size, false);
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
                    case 'k': color = '#020617'; break;
                    case 's': color = '#94a3b8'; break;
                    case 'd': color = '#475569'; break;
                    case 'g': color = '#2dd4bf'; break;
                    case 'w': color = '#ffffff'; break;
                    case 'r': color = '#ef4444'; break;
                    case 'o': color = '#f59e0b'; break;
                    case 'b': color = '#7c2d12'; break;
                    case 'p': color = '#c084fc'; break;
                    case 'y': color = '#f59e0b'; break;
                    case 'v': color = '#10b981'; break;
                    case 'f': color = '#cbd5e1'; break;
                }
                ctx.fillStyle = color;
                ctx.fillRect(px + c * pixelSize, py + r * pixelSize, pixelSize + 0.4, pixelSize + 0.4);
            }
        }
    }

    function drawGhostSprite(spriteName, px, py, size) {
        ctx.save();
        ctx.shadowColor = 'rgba(130, 200, 255, 0.9)';
        ctx.shadowBlur = size * 0.25;
        ctx.globalAlpha = 0.6;
        drawPixelSprite(spriteName, px, py, size);
        ctx.restore();
    }

    function drawWallTile(px, py, size, isDark) {
        var base = isDark ? '#0f172a' : '#94a3b8';
        var shadow = isDark ? '#020617' : '#475569';
        var highlight = isDark ? '#1e293b' : '#cbd5e1';

        ctx.fillStyle = base;
        ctx.fillRect(px, py, size, size);

        ctx.fillStyle = shadow;
        ctx.fillRect(px, py + size - 2, size, 2);
        ctx.fillRect(px + size - 2, py, 2, size);

        ctx.fillRect(px, py + Math.floor(size / 2) - 1, size, 2);

        ctx.fillRect(px + Math.floor(size / 2) - 1, py, 2, Math.floor(size / 2));
        ctx.fillRect(px + Math.floor(size / 4) - 1, py + Math.floor(size / 2), 2, Math.floor(size / 2));
        ctx.fillRect(px + Math.floor(3 * size / 4) - 1, py + Math.floor(size / 2), 2, Math.floor(size / 2));

        ctx.fillStyle = highlight;
        ctx.fillRect(px + 1, py + 1, size - 3, 1);
        ctx.fillRect(px + 1, py + Math.floor(size / 2) + 1, size - 3, 1);
    }

    function drawFloorTile(px, py, size, isDark) {
        var woodBase = isDark ? '#3a1e05' : '#653a15';
        var woodDetail = isDark ? '#4d2807' : '#7f4b1e';
        var woodLine = isDark ? '#1e0c01' : '#3a1c04';
        var woodGrain = isDark ? '#2a1402' : '#522d0b';

        ctx.fillStyle = woodBase;
        ctx.fillRect(px, py, size, size);

        var numPlanks = 4;
        var plankH = size / numPlanks;
        for (var i = 0; i < numPlanks; i++) {
            var plankY = py + i * plankH;

            ctx.fillStyle = woodLine;
            ctx.fillRect(px, plankY, size, 1);

            ctx.fillStyle = woodGrain;
            ctx.fillRect(px + 4, plankY + Math.floor(plankH * 0.3), size - 8, 1);
            ctx.fillRect(px + 8, plankY + Math.floor(plankH * 0.7), size - 16, 1);

            var seed = Math.sin(px * 12.9898 + (py + i * 37) * 78.233) * 43758.5453;
            var rand = seed - Math.floor(seed);
            if (rand < 0.5) {
                var endX = px + Math.floor(size * 0.3 + rand * size * 0.4);
                ctx.fillStyle = woodLine;
                ctx.fillRect(endX, plankY, 1, plankH);
            }
        }

        ctx.fillStyle = woodDetail;
        ctx.fillRect(px, py, size, 1);
        ctx.fillRect(px, py, 1, size);
    }

    // state: { type, cleared, doors:{up,down,left,right}, roomItems, playerX, playerY }
    function renderExploration(state) {
        var type = state.type;
        var cleared = state.cleared;
        var hasUp = state.doors.up, hasDown = state.doors.down;
        var hasLeft = state.doors.left, hasRight = state.doors.right;
        var roomItems = state.roomItems;
        var pX = state.playerX, pY = state.playerY;

        ctx.fillStyle = '#0b0f19';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        var isDark = ['BOSS', 'TREASURE'].indexOf(type) !== -1;

        for (var fy = 96; fy <= 608; fy += 64) {
            for (var fx = 96; fx <= 864; fx += 64) {
                drawFloorTile(fx, fy, 64, isDark);
            }
        }

        var brickSize = 32;

        function isDoorway(x, y) {
            if (hasUp && y < 96 && x >= 448 && x < 576) return true;
            if (hasDown && y >= 672 && x >= 448 && x < 576) return true;
            if (hasLeft && x < 96 && y >= 320 && y < 448) return true;
            if (hasRight && x >= 928 && y >= 320 && y < 448) return true;
            return false;
        }

        for (var wy = 0; wy < canvas.height; wy += brickSize) {
            for (var wx = 0; wx < canvas.width; wx += brickSize) {
                var isBoundary = (wx < 96 || wx >= 928 || wy < 96 || wy >= 672);
                if (isBoundary) {
                    if (isDoorway(wx, wy)) {
                        drawFloorTile(wx, wy, brickSize, isDark);
                    } else {
                        drawWallTile(wx, wy, brickSize, isDark);
                    }
                }
            }
        }

        if (roomItems.remains) {
            var rem = roomItems.remains;
            var remainsSprite = rem.type + '_REMAINS';
            drawPixelSprite(remainsSprite, rem.x, rem.y, 64);
        }

        for (var i = 0; i < roomItems.pots.length; i++) {
            var pot = roomItems.pots[i];
            if (pot.broken) {
                var age = Date.now() - pot.shatterTime;
                if (age < 1500) {
                    ctx.save();
                    ctx.globalAlpha = Math.max(0, 1 - age / 1500);
                    ctx.fillStyle = '#b45309';
                    ctx.fillRect(pot.x + 8, pot.y + 12, 10, 6);
                    ctx.fillRect(pot.x + 28, pot.y + 18, 6, 8);
                    ctx.fillRect(pot.x + 16, pot.y + 32, 8, 5);
                    ctx.fillRect(pot.x + 36, pot.y + 8, 5, 5);
                    ctx.restore();
                }
            } else {
                drawPixelSprite('POT', pot.x, pot.y, 48);
            }
        }

        for (var ci = 0; ci < roomItems.coins.length; ci++) {
            var item = roomItems.coins[ci];
            if (item.collected) continue;

            if (item.type === 'COIN') {
                drawPixelSprite('COIN', item.x - 12, item.y - 12, 24);
            } else if (item.type === 'SHIELD') {
                ctx.fillStyle = '#2dd4bf';
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 1.5;
                ctx.beginPath();
                ctx.moveTo(item.x, item.y - 10);
                ctx.lineTo(item.x + 8, item.y - 6);
                ctx.lineTo(item.x + 8, item.y + 2);
                ctx.lineTo(item.x, item.y + 10);
                ctx.lineTo(item.x - 8, item.y + 2);
                ctx.lineTo(item.x - 8, item.y - 6);
                ctx.closePath();
                ctx.fill();
                ctx.stroke();
            }
        }

        if (type === 'ENTRANCE') {
            drawPixelSprite('PORTAL', 480, 352, 64);
        } else if (type === 'TREASURE' && !cleared) {
            drawPixelSprite('CHEST', 480, 352, 64);
        } else if (type === 'HEAL' && !cleared) {
            drawPixelSprite('POTION', 480, 352, 64);
        } else if (type === 'SHOP') {
            drawPixelSprite('CHEST', 480, 352, 64);
        } else if (type === 'SHRINE' && !cleared) {
            drawPixelSprite('SHRINE', 480, 352, 64);
        }

        var revDoors = state.revenantDoors || {};
        var peekSize = 56;
        if (revDoors.up)    drawGhostSprite('GHOST', 512 - peekSize / 2, 44, peekSize);
        if (revDoors.down)  drawGhostSprite('GHOST', 512 - peekSize / 2, 668, peekSize);
        if (revDoors.left)  drawGhostSprite('GHOST', 44, 384 - peekSize / 2, peekSize);
        if (revDoors.right) drawGhostSprite('GHOST', 924, 384 - peekSize / 2, peekSize);
        drawPixelSprite('PLAYER', pX, pY, 64);
    }

    // state: { monster, boss, gauntletTotal }
    function renderSplash(state) {
        ctx.fillStyle = '#020617';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        ctx.fillStyle = 'rgba(51, 65, 85, 0.08)';
        for (var y = 0; y < canvas.height; y += 4) {
            ctx.fillRect(0, y, canvas.width, 2);
        }

        var bob = Math.sin(Date.now() / 140) * 12;

        var spriteSize = 112;
        var sx = (canvas.width - spriteSize) / 2;
        var sy = (canvas.height - spriteSize) / 2 - 32 + bob;

        var isRevenant = state.isRevenant === true;
        if (isRevenant) {
            drawGhostSprite(state.monster || 'GHOST', sx, sy, spriteSize);
        } else {
            drawPixelSprite(state.monster || 'SLIME', sx, sy, spriteSize);
        }

        ctx.fillStyle = isRevenant ? '#c084fc' : '#ef4444';
        ctx.font = 'bold 20px "Courier New", Courier, monospace';
        ctx.textAlign = 'center';

        var title;
        if (isRevenant) {
            title = (window.dungeonI18n && window.dungeonI18n.revenantSplashTitle) || "A MISTAKE RETURNS!";
        } else {
            title = state.boss
                ? "!!! BOSS-KAMPF !!!"
                : (state.gauntletTotal > 0 ? "ELITE CHALLENGE!" : "⚔️ GEGNER GEFUNDEN ⚔️");
        }
        ctx.fillText(title, canvas.width / 2, canvas.height - 90);

        if (!isRevenant) {
            ctx.fillStyle = '#94a3b8';
            ctx.font = '13px "Courier New", Courier, monospace';
            var monsterLabel = (state.monster || "MONSTER").toUpperCase();
            ctx.fillText("EIN WILDER " + monsterLabel + " BEGEGNET DIR!", canvas.width / 2, canvas.height - 60);

            ctx.fillStyle = '#64748b';
            ctx.font = 'italic 11px "Courier New", Courier, monospace';
            ctx.fillText("Bereite dich auf den Kampf vor...", canvas.width / 2, canvas.height - 40);
        }
    }

    function wrapText(text, x, y, maxWidth, lineHeight, draw) {
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

    // state: {
    //   health, healthCap, score, progress (string "x/y"),
    //   boss, gauntletPos, gauntletTotal, monster, hoveredOptionIndex,
    //   mode: 'flashcard'|'quiz'|null,
    //   flashcard: { front, back, revealed },
    //   quiz: { question, options: [{ text, checked, isCheckbox }] }
    // }
    // Returns the interactive option boxes for hit-testing.
    function renderCombat(state) {
        var scale = canvas.width / 512.0;

        ctx.fillStyle = '#020617';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        ctx.fillStyle = 'rgba(51, 65, 85, 0.05)';
        for (var y = 0; y < canvas.height; y += Math.floor(4 * scale)) {
            ctx.fillRect(0, y, canvas.width, Math.floor(2 * scale));
        }

        var health = state.health;
        var healthCap = state.healthCap || 5;
        var score = state.score;
        var progress = state.progress || '0/0';
        var activeEncBoss = state.boss;
        var gauntletPos = state.gauntletPos;
        var gauntletTotal = state.gauntletTotal;
        var enemyName = state.isRevenant
            ? ((window.dungeonI18n && window.dungeonI18n.revenantEnemyLabel) || "REVENANT").toUpperCase()
            : (state.monster || (activeEncBoss ? "DRAGON" : "SLIME")).toUpperCase();
        var hoveredIndex = state.hoveredOptionIndex;

        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2 * scale;
        ctx.strokeRect(20 * scale, 20 * scale, 220 * scale, 65 * scale);
        ctx.fillStyle = 'rgba(15, 23, 42, 0.75)';
        ctx.fillRect(20 * scale, 20 * scale, 220 * scale, 65 * scale);

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
        ctx.textAlign = 'left';
        ctx.fillText("HELD (SCORE: " + score + ")", 30 * scale, 40 * scale);

        ctx.fillStyle = '#1e293b';
        ctx.fillRect(30 * scale, 48 * scale, 120 * scale, 12 * scale);
        var healthPercent = Math.max(0, Math.min(1, health / healthCap));
        ctx.fillStyle = healthPercent > 0.5 ? '#10b981' : (healthPercent > 0.2 ? '#f59e0b' : '#ef4444');
        ctx.fillRect(30 * scale, 48 * scale, 120 * scale * healthPercent, 12 * scale);
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1 * scale;
        ctx.strokeRect(30 * scale, 48 * scale, 120 * scale, 12 * scale);

        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold ' + Math.floor(12 * scale) + 'px "Courier New", Courier, monospace';
        ctx.fillText("HP: " + health + "/" + healthCap, 160 * scale, 58 * scale);

        ctx.strokeStyle = activeEncBoss ? '#ef4444' : '#ffffff';
        ctx.lineWidth = 2 * scale;
        ctx.strokeRect(canvas.width - 240 * scale, 20 * scale, 220 * scale, 65 * scale);
        ctx.fillStyle = 'rgba(15, 23, 42, 0.75)';
        ctx.fillRect(canvas.width - 240 * scale, 20 * scale, 220 * scale, 65 * scale);

        ctx.fillStyle = activeEncBoss ? '#ef4444' : '#ffffff';
        ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
        ctx.fillText(enemyName, canvas.width - 230 * scale, 40 * scale);

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

        if (gauntletTotal > 0 && !activeEncBoss) {
            ctx.fillStyle = '#fbbf24';
            ctx.font = 'bold ' + Math.floor(10 * scale) + 'px "Courier New", Courier, monospace';
            ctx.textAlign = 'right';
            ctx.fillText("ELITE " + gauntletPos + "/" + gauntletTotal, canvas.width - 30 * scale, 40 * scale);
            ctx.textAlign = 'left';
        }

        var bob = Math.sin(Date.now() / 180) * 10;
        var monsterSprite = state.monster || (activeEncBoss ? 'DRAGON' : 'SLIME');
        var spriteSize = activeEncBoss ? 128 * scale : 96 * scale;
        var sx = (canvas.width - spriteSize) / 2;
        var sy = 100 * scale + bob * scale;
        if (state.isRevenant) {
            drawGhostSprite(monsterSprite, sx, sy, spriteSize);
        } else {
            drawPixelSprite(monsterSprite, sx, sy, spriteSize);
        }

        var boxX = 20 * scale;
        var boxY = 190 * scale;
        var boxW = canvas.width - 40 * scale;
        var boxH = canvas.height - boxY - 20 * scale;

        ctx.fillStyle = '#020617';
        ctx.fillRect(boxX, boxY, boxW, boxH);

        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 4 * scale;
        ctx.strokeRect(boxX, boxY, boxW, boxH);
        ctx.strokeStyle = '#020617';
        ctx.lineWidth = 2 * scale;
        ctx.strokeRect(boxX + 3 * scale, boxY + 3 * scale, boxW - 6 * scale, boxH - 6 * scale);
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1 * scale;
        ctx.strokeRect(boxX + 5 * scale, boxY + 5 * scale, boxW - 10 * scale, boxH - 10 * scale);

        var isFlashcard = state.mode === 'flashcard';
        var isQuiz = state.mode === 'quiz';

        ctx.fillStyle = '#ffffff';
        ctx.textAlign = 'left';

        var optionBoxes = [];
        var startY = boxY + boxH - 50 * scale;

        if (isFlashcard) {
            var frontText = state.flashcard.front || "";
            var backText = state.flashcard.back || "";
            var isRevealed = state.flashcard.revealed === true;

            var fontSize = 15 * scale;
            var lineHeight = 20 * scale;
            var maxTextHeight = startY - (boxY + 55 * scale);
            var lines = [];

            while (fontSize >= 8 * scale) {
                ctx.font = 'bold ' + Math.floor(fontSize) + 'px "Courier New", Courier, monospace';
                if (!isRevealed) {
                    lines = wrapText("FRAGE: " + frontText, boxX + 20 * scale, boxY + 55 * scale, boxW - 40 * scale, lineHeight, false);
                } else {
                    lines = wrapText(backText, boxX + 20 * scale, boxY + 50 * scale, boxW - 40 * scale, lineHeight, false);
                }

                if (lines.length * lineHeight <= maxTextHeight) {
                    break;
                }
                fontSize -= 0.5 * scale;
                lineHeight -= 0.7 * scale;
            }

            if (!isRevealed) {
                ctx.fillStyle = '#f59e0b';
                ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
                ctx.fillText("⚔️ ENCOUNTER: KARTE GEZOGEN ⚔️", boxX + 20 * scale, boxY + 28 * scale);

                ctx.fillStyle = '#ffffff';
                ctx.font = 'bold ' + Math.floor(fontSize) + 'px "Courier New", Courier, monospace';
                wrapText("FRAGE: " + frontText, boxX + 20 * scale, boxY + 55 * scale, boxW - 40 * scale, lineHeight, true);
            } else {
                ctx.fillStyle = '#2dd4bf';
                ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
                ctx.fillText("ANTWORT:", boxX + 20 * scale, boxY + 28 * scale);

                ctx.fillStyle = '#ffffff';
                ctx.font = 'bold ' + Math.floor(fontSize) + 'px "Courier New", Courier, monospace';
                wrapText(backText, boxX + 20 * scale, boxY + 50 * scale, boxW - 40 * scale, lineHeight, true);
            }

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

                var isHovered = hoveredIndex === 0;
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
                var btnW2 = 110 * scale;
                var btnH2 = 32 * scale;

                var xMissed = boxX + 60 * scale;
                optionBoxes.push({
                    type: 'answer',
                    value: false,
                    x: xMissed,
                    y: startY,
                    w: btnW2,
                    h: btnH2
                });

                var isHoveredMissed = hoveredIndex === 0;
                ctx.fillStyle = isHoveredMissed ? '#7f1d1d' : '#0f172a';
                ctx.fillRect(xMissed, startY, btnW2, btnH2);
                ctx.strokeStyle = '#ef4444';
                ctx.lineWidth = 1 * scale;
                ctx.strokeRect(xMissed, startY, btnW2, btnH2);

                ctx.fillStyle = '#ef4444';
                ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
                ctx.textAlign = 'center';
                ctx.fillText((isHoveredMissed ? "▶ " : "") + "FALSCH [M]", xMissed + btnW2 / 2, startY + 20 * scale);

                var xGot = boxX + boxW - 170 * scale;
                optionBoxes.push({
                    type: 'answer',
                    value: true,
                    x: xGot,
                    y: startY,
                    w: btnW2,
                    h: btnH2
                });

                var isHoveredGot = hoveredIndex === 1;
                ctx.fillStyle = isHoveredGot ? '#064e3b' : '#0f172a';
                ctx.fillRect(xGot, startY, btnW2, btnH2);
                ctx.strokeStyle = '#10b981';
                ctx.lineWidth = 1 * scale;
                ctx.strokeRect(xGot, startY, btnW2, btnH2);

                ctx.fillStyle = '#10b981';
                ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
                ctx.textAlign = 'center';
                ctx.fillText((isHoveredGot ? "▶ " : "") + "RICHTIG [G]", xGot + btnW2 / 2, startY + 20 * scale);

                ctx.textAlign = 'left';
            }

        } else if (isQuiz) {
            var quizQuestion = state.quiz.question || "";
            var rawOptions = state.quiz.options || [];

            var qFontSize = 15 * scale;
            var qLineHeight = 20 * scale;
            var maxTextHeight2 = startY - (boxY + 60 * scale) - (rawOptions.length * 32 * scale);
            var questionLines;

            while (qFontSize >= 9 * scale) {
                ctx.font = 'bold ' + Math.floor(qFontSize) + 'px "Courier New", Courier, monospace';
                questionLines = wrapText(quizQuestion, boxX + 20 * scale, boxY + 52 * scale, boxW - 40 * scale, qLineHeight, false);
                var qH = questionLines.length * qLineHeight;

                if (qH <= maxTextHeight2 || qFontSize <= 9 * scale) {
                    break;
                }
                qFontSize -= 0.5 * scale;
                qLineHeight -= 0.8 * scale;
            }

            ctx.fillStyle = '#2dd4bf';
            ctx.font = 'bold ' + Math.floor(13 * scale) + 'px "Courier New", Courier, monospace';
            ctx.fillText("⚔️ BATTLE MODE: QUIZ FRAGE ⚔️", boxX + 20 * scale, boxY + 28 * scale);

            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold ' + Math.floor(qFontSize) + 'px "Courier New", Courier, monospace';
            var qlines = wrapText(quizQuestion, boxX + 20 * scale, boxY + 52 * scale, boxW - 40 * scale, qLineHeight, true);
            var questionHeight = qlines.length * qLineHeight;

            var optionsStartY = Math.max(300 * scale, boxY + 52 * scale + questionHeight + 12 * scale);
            var optionHeight = 26 * scale;
            var optionSpacing = 5 * scale;

            rawOptions.forEach(function(opt, idx) {
                var optY = optionsStartY + idx * (optionHeight + optionSpacing);
                var isHoveredOpt = hoveredIndex === idx;

                optionBoxes.push({
                    type: 'quiz_option',
                    index: idx,
                    x: boxX + 20 * scale,
                    y: optY,
                    w: boxW - 40 * scale,
                    h: optionHeight
                });

                if (isHoveredOpt) {
                    ctx.fillStyle = 'rgba(51, 65, 85, 0.35)';
                    ctx.fillRect(boxX + 15 * scale, optY - 2 * scale, boxW - 30 * scale, optionHeight + 4 * scale);
                }

                ctx.fillStyle = isHoveredOpt ? '#2dd4bf' : '#ffffff';
                ctx.font = 'bold ' + Math.floor(qFontSize) + 'px "Courier New", Courier, monospace';

                var bullet = opt.isCheckbox ? (opt.checked ? "[X]" : "[ ]") : (opt.checked ? "(•)" : "( )");
                var cursor = isHoveredOpt ? "▶ " : "  ";
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

            var isHoveredSubmit = hoveredIndex === rawOptions.length;
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

        return optionBoxes;
    }

    return {
        init: init,
        renderExploration: renderExploration,
        renderCombat: renderCombat,
        renderSplash: renderSplash,
    };
})();
