/* ============================================================
   StudyHelper — Color Picker
   window.ColorPicker = { attach, open, close, parse, format }
   ============================================================ */
(function () {
    'use strict';

    // ── Curated app palette ──────────────────────────────────────────
    var APP_PALETTE = [
        '#0f766e', '#8b5cf6', '#ec4899', '#ef4444',
        '#f59e0b', '#10b981', '#06b6d4', '#3b82f6',
        '#64748b', '#1e293b', '#f8fafc', '#ffffff',
    ];

    var MAX_RECENT = 6;
    var RECENT_PREFIX = 'sh.colorpicker.recent.';

    // ── Color conversions ────────────────────────────────────────────
    function hsvToRgb(h, s, v) {
        s = s / 100; v = v / 100;
        var c = v * s;
        var x = c * (1 - Math.abs((h / 60) % 2 - 1));
        var m = v - c;
        var r = 0, g = 0, b = 0;
        if      (h < 60)  { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else              { r = c; g = 0; b = x; }
        return {
            r: Math.round((r + m) * 255),
            g: Math.round((g + m) * 255),
            b: Math.round((b + m) * 255),
        };
    }

    function rgbToHsv(r, g, b) {
        r = r / 255; g = g / 255; b = b / 255;
        var max = Math.max(r, g, b);
        var min = Math.min(r, g, b);
        var d = max - min;
        var h = 0;
        if (d !== 0) {
            if (max === r) h = ((g - b) / d % 6 + 6) % 6 * 60;
            else if (max === g) h = ((b - r) / d + 2) * 60;
            else h = ((r - g) / d + 4) * 60;
        }
        return {
            h: Math.round(h),
            s: max === 0 ? 0 : Math.round(d / max * 100),
            v: Math.round(max * 100),
        };
    }

    function hexToRgb(hex) {
        hex = hex.replace(/^#/, '');
        if (hex.length === 3) {
            hex = hex[0]+hex[0]+hex[1]+hex[1]+hex[2]+hex[2];
        }
        var n = parseInt(hex, 16);
        return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
    }

    function rgbToHex(r, g, b) {
        return '#' + [r, g, b].map(function (c) {
            return Math.round(c).toString(16).padStart(2, '0');
        }).join('');
    }

    function parseColor(input) {
        if (!input) return null;
        input = String(input).trim();
        if (/^#?[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$/.test(input)) {
            try {
                var rgb = hexToRgb(input.charAt(0) === '#' ? input : '#' + input);
                var hsv = rgbToHsv(rgb.r, rgb.g, rgb.b);
                return { r: rgb.r, g: rgb.g, b: rgb.b, h: hsv.h, s: hsv.s, v: hsv.v,
                         hex: rgbToHex(rgb.r, rgb.g, rgb.b) };
            } catch (e) { return null; }
        }
        return null;
    }

    // ── Canvas rendering ─────────────────────────────────────────────
    function renderSV(canvas, hue) {
        if (!canvas) return;
        var w = canvas.width, h = canvas.height;
        var ctx = canvas.getContext('2d');
        var pure = hsvToRgb(hue, 100, 100);
        var grad1 = ctx.createLinearGradient(0, 0, w, 0);
        grad1.addColorStop(0, '#fff');
        grad1.addColorStop(1, 'rgb(' + pure.r + ',' + pure.g + ',' + pure.b + ')');
        ctx.fillStyle = grad1;
        ctx.fillRect(0, 0, w, h);
        var grad2 = ctx.createLinearGradient(0, 0, 0, h);
        grad2.addColorStop(0, 'rgba(0,0,0,0)');
        grad2.addColorStop(1, 'rgba(0,0,0,1)');
        ctx.fillStyle = grad2;
        ctx.fillRect(0, 0, w, h);
    }

    function renderHue(canvas) {
        if (!canvas) return;
        var w = canvas.width, h = canvas.height;
        var ctx = canvas.getContext('2d');
        var grad = ctx.createLinearGradient(0, 0, 0, h);
        [0, 60, 120, 180, 240, 300, 360].forEach(function (deg) {
            var c = hsvToRgb(deg, 100, 100);
            grad.addColorStop(deg / 360, 'rgb(' + c.r + ',' + c.g + ',' + c.b + ')');
        });
        ctx.fillStyle = grad;
        ctx.fillRect(0, 0, w, h);
    }

    // ── Pointer binding ──────────────────────────────────────────────
    function bindPointer(el, onDrag) {
        var active = false;
        el.addEventListener('pointerdown', function (e) {
            active = true;
            el.setPointerCapture(e.pointerId);
            onDrag(e);
            e.preventDefault();
        });
        el.addEventListener('pointermove', function (e) {
            if (!active) return;
            onDrag(e);
        });
        el.addEventListener('pointerup', function (e) {
            if (!active) return;
            active = false;
            onDrag(e);
        });
    }

    // ── Singleton state ──────────────────────────────────────────────
    var state = null;   // { h, s, v, opts, trigger, rafId }
    var popover = null;
    var currentMode = 'hex';
    var interactionsWired = false;

    function getPopover() {
        if (!popover) popover = document.getElementById('sh-cp-root');
        return popover;
    }

    function currentHex() {
        var rgb = hsvToRgb(state.h, state.s, state.v);
        return rgbToHex(rgb.r, rgb.g, rgb.b);
    }

    // ── Update UI from state ─────────────────────────────────────────
    function updateUI(rebuildSV) {
        var pop = getPopover();
        if (!pop || !state) return;

        var svCanvas  = pop.querySelector('.sh-cp-sv-canvas');
        var svCursor  = pop.querySelector('.sh-cp-sv-cursor');
        var hueCanvas = pop.querySelector('.sh-cp-hue-canvas');
        var hueCursor = pop.querySelector('.sh-cp-hue-cursor');

        if (rebuildSV) renderSV(svCanvas, state.h);

        // Position SV cursor
        svCursor.style.left = (state.s / 100 * svCanvas.width)          + 'px';
        svCursor.style.top  = ((1 - state.v / 100) * svCanvas.height)   + 'px';

        // Position hue cursor
        hueCursor.style.top = (state.h / 360 * hueCanvas.height)        + 'px';

        // Update inputs (skip the focused one to avoid cursor-jump)
        var hex = currentHex();
        var rgb = hexToRgb(hex);
        var hexInput = pop.querySelector('.sh-cp-hex-row input');
        if (hexInput && document.activeElement !== hexInput) hexInput.value = hex;

        pop.querySelectorAll('.sh-cp-rgb-row input').forEach(function (inp) {
            if (document.activeElement === inp) return;
            var ch = inp.dataset.channel;
            inp.value = ch === 'r' ? rgb.r : ch === 'g' ? rgb.g : rgb.b;
        });

        pop.querySelectorAll('.sh-cp-hsb-row input').forEach(function (inp) {
            if (document.activeElement === inp) return;
            var ch = inp.dataset.channel;
            inp.value = ch === 'h' ? Math.round(state.h)
                      : ch === 's' ? Math.round(state.s)
                      : Math.round(state.v);
        });
    }

    function scheduleUpdate(rebuildSV) {
        if (state.rafId) cancelAnimationFrame(state.rafId);
        state.rafId = requestAnimationFrame(function () {
            updateUI(rebuildSV);
            if (state.opts.onChange) state.opts.onChange(currentHex());
        });
    }

    // ── Positioning ──────────────────────────────────────────────────
    function positionPopover(trigger) {
        var pop = getPopover();
        if (!pop || !trigger) return;
        var tr = trigger.getBoundingClientRect();
        var vw = window.innerWidth, vh = window.innerHeight;
        var pw = 410, ph = 390;

        var top  = tr.bottom + 6;
        var left = tr.left;

        if (tr.bottom + ph + 6 > vh) top = tr.top - ph - 6;
        left = Math.max(8, Math.min(left, vw - pw - 8));
        top  = Math.max(8, top);

        pop.style.top  = top  + 'px';
        pop.style.left = left + 'px';
    }

    // ── Recent colors ────────────────────────────────────────────────
    function getRecent(key) {
        try { return JSON.parse(localStorage.getItem(RECENT_PREFIX + key) || '[]'); }
        catch (e) { return []; }
    }

    function pushRecent(key, hex) {
        var list = getRecent(key).filter(function (c) {
            return c.toLowerCase() !== hex.toLowerCase();
        });
        list.unshift(hex);
        localStorage.setItem(RECENT_PREFIX + key, JSON.stringify(list.slice(0, MAX_RECENT)));
    }

    function renderSwatches() {
        var pop = getPopover();
        if (!pop || !state) return;
        var key = (state.opts.paletteKey) || 'default';

        var recentRow = pop.querySelector('.sh-cp-swatch-row');
        var recentEl  = pop.querySelector('.sh-cp-recent-swatches');
        var recent    = getRecent(key);

        if (recent.length === 0) {
            recentRow.style.display = 'none';
        } else {
            recentRow.style.display = '';
            recentEl.innerHTML = recent.map(function (c) {
                return '<button type="button" class="sh-cp-swatch" style="background:' + c + '" title="' + c + '" data-hex="' + c + '"></button>';
            }).join('');
        }

        var paletteEl = pop.querySelector('.sh-cp-palette-swatches');
        paletteEl.innerHTML = APP_PALETTE.map(function (c) {
            return '<button type="button" class="sh-cp-swatch" style="background:' + c + '" title="' + c + '" data-hex="' + c + '"></button>';
        }).join('');
    }

    // ── Wire interactions (once, after first open) ───────────────────
    function wireInteractions() {
        if (interactionsWired) return;
        interactionsWired = true;

        var pop       = getPopover();
        var svCanvas  = pop.querySelector('.sh-cp-sv-canvas');
        var hueCanvas = pop.querySelector('.sh-cp-hue-canvas');

        renderHue(hueCanvas);

        // SV plane drag
        bindPointer(svCanvas, function (e) {
            var rect = svCanvas.getBoundingClientRect();
            state.s = Math.max(0, Math.min(e.clientX - rect.left, rect.width))  / rect.width  * 100;
            state.v = (1 - Math.max(0, Math.min(e.clientY - rect.top, rect.height)) / rect.height) * 100;
            scheduleUpdate(false);
        });

        // Hue strip drag
        bindPointer(hueCanvas, function (e) {
            var rect = hueCanvas.getBoundingClientRect();
            state.h = Math.max(0, Math.min(e.clientY - rect.top, rect.height)) / rect.height * 360;
            scheduleUpdate(true);
        });

        // Mode tabs
        pop.querySelectorAll('.sh-cp-tab').forEach(function (tab) {
            tab.addEventListener('click', function () {
                pop.querySelectorAll('.sh-cp-tab').forEach(function (t) { t.classList.remove('is-active'); });
                tab.classList.add('is-active');
                currentMode = tab.dataset.mode;
                pop.querySelector('.sh-cp-hex-row').style.display = currentMode === 'hex' ? '' : 'none';
                pop.querySelector('.sh-cp-rgb-row').style.display = currentMode === 'rgb' ? '' : 'none';
                pop.querySelector('.sh-cp-hsb-row').style.display = currentMode === 'hsb' ? '' : 'none';
                updateUI(false);
            });
        });

        // HEX input
        var hexInput = pop.querySelector('.sh-cp-hex-row input');
        hexInput.addEventListener('input', function () {
            var c = parseColor(hexInput.value.trim());
            if (c) { state.h = c.h; state.s = c.s; state.v = c.v; scheduleUpdate(true); }
        });
        hexInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); close(); }
        });

        // RGB inputs
        pop.querySelectorAll('.sh-cp-rgb-row input').forEach(function (inp) {
            inp.addEventListener('input', function () {
                var r = clamp(parseInt(pop.querySelector('[data-channel="r"]').value) || 0, 0, 255);
                var g = clamp(parseInt(pop.querySelector('[data-channel="g"]').value) || 0, 0, 255);
                var b = clamp(parseInt(pop.querySelector('[data-channel="b"]').value) || 0, 0, 255);
                var hsv = rgbToHsv(r, g, b);
                state.h = hsv.h; state.s = hsv.s; state.v = hsv.v;
                scheduleUpdate(true);
            });
            inp.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') { e.preventDefault(); close(); }
            });
        });

        // HSB inputs
        pop.querySelectorAll('.sh-cp-hsb-row input').forEach(function (inp) {
            inp.addEventListener('input', function () {
                state.h = clamp(parseInt(pop.querySelector('[data-channel="h"]').value) || 0, 0, 360);
                state.s = clamp(parseInt(pop.querySelector('[data-channel="s"]').value) || 0, 0, 100);
                state.v = clamp(parseInt(pop.querySelector('[data-channel="v"]').value) || 0, 0, 100);
                scheduleUpdate(true);
            });
            inp.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') { e.preventDefault(); close(); }
            });
        });

        // Eyedropper
        var eyeBtn = pop.querySelector('.sh-cp-eyedropper');
        if (window.EyeDropper) {
            eyeBtn.style.display = '';
            eyeBtn.addEventListener('click', function () {
                try {
                    new EyeDropper().open().then(function (result) {
                        var c = parseColor(result.sRGBHex);
                        if (c) { state.h = c.h; state.s = c.s; state.v = c.v; scheduleUpdate(true); }
                    });
                } catch (e) {}
            });
        } else {
            eyeBtn.style.display = 'none';
        }

        // Swatch clicks (delegated)
        pop.addEventListener('click', function (e) {
            var sw = e.target.closest('[data-hex]');
            if (!sw) return;
            var c = parseColor(sw.dataset.hex);
            if (c) { state.h = c.h; state.s = c.s; state.v = c.v; scheduleUpdate(true); }
        });
    }

    function clamp(val, lo, hi) { return Math.max(lo, Math.min(hi, val)); }

    // ── Open / Close ─────────────────────────────────────────────────
    function open(trigger, opts) {
        opts = opts || {};
        if (state) _commitClose();

        var c = parseColor(opts.initialColor) || parseColor('#0f766e');
        state = { h: c.h, s: c.s, v: c.v, opts: opts, trigger: trigger, rafId: null };

        var pop = getPopover();
        if (!pop) return;

        wireInteractions();
        renderSwatches();

        // Sync mode display
        pop.querySelector('.sh-cp-hex-row').style.display = currentMode === 'hex' ? '' : 'none';
        pop.querySelector('.sh-cp-rgb-row').style.display = currentMode === 'rgb' ? '' : 'none';
        pop.querySelector('.sh-cp-hsb-row').style.display = currentMode === 'hsb' ? '' : 'none';
        pop.querySelectorAll('.sh-cp-tab').forEach(function (t) {
            t.classList.toggle('is-active', t.dataset.mode === currentMode);
        });

        pop.style.display = '';
        positionPopover(trigger);
        renderSV(pop.querySelector('.sh-cp-sv-canvas'), state.h);
        updateUI(false);

        trigger.classList.add('sh-cp-is-open');

        // Defer outside-click listener so the opening click isn't caught
        setTimeout(function () {
            document.addEventListener('pointerdown', _outsideHandler);
            document.addEventListener('keydown', _keyHandler);
        }, 0);
    }

    function _commitClose() {
        if (!state) return;
        var hex = currentHex();
        var paletteKey = state.opts.paletteKey;
        if (paletteKey) pushRecent(paletteKey, hex);
        if (state.opts.onCommit) state.opts.onCommit(hex);
        if (state.rafId) cancelAnimationFrame(state.rafId);
        if (state.trigger) state.trigger.classList.remove('sh-cp-is-open');
        state = null;
    }

    function close() {
        _commitClose();
        var pop = getPopover();
        if (pop) pop.style.display = 'none';
        document.removeEventListener('pointerdown', _outsideHandler);
        document.removeEventListener('keydown', _keyHandler);
    }

    function _outsideHandler(e) {
        var pop = getPopover();
        if (!pop) return;
        var isInsidePop     = pop.contains(e.target);
        var isInsideTrigger = state && state.trigger && state.trigger.contains(e.target);
        if (!isInsidePop && !isInsideTrigger) close();
    }

    function _keyHandler(e) {
        if (e.key === 'Escape') close();
    }

    // ── attach() ────────────────────────────────────────────────────
    function attach(trigger, opts) {
        if (trigger._shCpAttached) return;
        trigger._shCpAttached = true;
        trigger.addEventListener('click', function () {
            var pop = getPopover();
            if (pop && pop.style.display !== 'none' && state && state.trigger === trigger) {
                close();
            } else {
                open(trigger, opts);
            }
        });
    }

    // ── Auto-init ─────────────────────────────────────────────────────
    function autoInit() {
        document.querySelectorAll('[data-color-picker]').forEach(function (el) {
            if (el._shCpAttached) return;
            var targetId    = el.dataset.target;
            var paletteKey  = el.dataset.paletteKey || 'default';
            var targetInput = targetId ? document.getElementById(targetId) : null;
            var initialColor = (targetInput && targetInput.value) || el.dataset.initialColor || '#0f766e';

            attach(el, {
                initialColor: initialColor,
                paletteKey:   paletteKey,
                onChange: function (hex) {
                    var swatch   = el.querySelector('.sh-color-swatch');
                    var hexLabel = el.querySelector('.sh-color-hex');
                    if (swatch)   swatch.style.background = hex;
                    if (hexLabel) hexLabel.textContent     = hex.toUpperCase();
                    if (targetInput) targetInput.value = hex;
                    var modal = el.closest('.sh-modal');
                    if (modal && typeof updateFolderPreview === 'function') updateFolderPreview(modal);
                },
                onCommit: function (hex) {
                    if (targetInput) targetInput.value = hex;
                    var modal = el.closest('.sh-modal');
                    if (modal && typeof updateFolderPreview === 'function') updateFolderPreview(modal);
                },
            });
        });
    }

    document.addEventListener('DOMContentLoaded', autoInit);
    document.addEventListener('htmx:afterSwap', autoInit);

    window.ColorPicker = { attach: attach, open: open, close: close, parse: parseColor, format: rgbToHex };
}());
