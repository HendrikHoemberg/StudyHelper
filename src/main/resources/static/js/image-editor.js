/* ============================================================
   StudyHelper — Image Editor
   window.ImageEditor = { open, close }
   Requires: fabric.js (window.fabric), color-picker.js (window.ColorPicker)
   ============================================================ */
(function () {
    'use strict';

    // ── Module state ─────────────────────────────────────────────────
    var _fc       = null;    // fabric.Canvas instance
    var _opts     = null;    // open() options
    var _objUrl   = null;    // revocable object URL created from File/Blob source
    var _baseImg  = null;    // reference to the background FabricImage object
    var _history  = [];      // serialized canvas snapshots (canvas.toObject())
    var _histPos  = -1;      // current position in _history
    var _histLock = false;   // suppress push during snapshot restore
    var _tool     = 'brush'; // active tool id
    var _color    = '#000000';
    var _wired    = false;   // interactions wired once flag

    // ── Shape drawing state ───────────────────────────────────────────
    var _shapeOrigin  = null;   // {x, y} canvas coords at mouse:down
    var _shapePreview = null;   // temporary Fabric object shown during drag
    var _shapeActive  = false;  // true while dragging

    // ── Zoom / Pan state ─────────────────────────────────────────────
    var _zoom       = 1;       // current zoom level
    var _spaceDown  = false;   // Space key held → pan-ready
    var _isPanning  = false;   // actively panning
    var _panLastX   = 0;       // last pointer X during pan
    var _panLastY   = 0;       // last pointer Y during pan

    var ZOOM_MIN = 0.1;
    var ZOOM_MAX = 8;
    var HIST_MAX = 20;

    // ── DOM helper ───────────────────────────────────────────────────
    function $id(id) { return document.getElementById(id); }

    // ── History ──────────────────────────────────────────────────────
    function _histPush() {
        if (_histLock || !_fc) return;
        // Discard any redo snapshots ahead of current position
        if (_histPos < _history.length - 1) {
            _history = _history.slice(0, _histPos + 1);
        }
        _history.push(_fc.toObject());
        if (_history.length > HIST_MAX) {
            _history.shift();
        } else {
            _histPos++;
        }
        _refreshHistBtns();
    }

    function _undo() {
        if (_histPos <= 0) return;
        _histPos--;
        _loadSnap(_history[_histPos]);
    }

    function _redo() {
        if (_histPos >= _history.length - 1) return;
        _histPos++;
        _loadSnap(_history[_histPos]);
    }

    function _loadSnap(snap) {
        _histLock = true;
        _fc.loadFromJSON(snap).then(function () {
            _fc.renderAll();
            // Base image is always the bottom-most object; re-lock it
            _baseImg = _fc.getObjects()[0] || null;
            if (_baseImg) {
                _baseImg.set({
                    selectable: false, evented: false,
                    hasControls: false, hasBorders: false,
                });
            }
            _histLock = false;
            _refreshHistBtns();
            _applyTool(_tool);
        }).catch(function (err) {
            console.error('ImageEditor: snapshot restore failed', err);
            _histLock = false;
        });
    }

    function _refreshHistBtns() {
        var u = $id('sh-ie-undo-btn');
        var r = $id('sh-ie-redo-btn');
        if (u) u.disabled = (_histPos <= 0);
        if (r) r.disabled = (_histPos >= _history.length - 1);
    }

    // ── Tool management ──────────────────────────────────────────────
    function _applyTool(name) {
        _tool = name;
        document.querySelectorAll('#sh-ie-modal .sh-ie-tool-btn[data-tool]').forEach(function (btn) {
            btn.classList.toggle('is-active', btn.dataset.tool === name);
        });

        // Toggle footer control groups
        var brushCtrl = $id('sh-ie-brush-controls');
        var textCtrl  = $id('sh-ie-text-controls');
        if (brushCtrl) brushCtrl.style.display = (name === 'text') ? 'none' : '';
        if (textCtrl)  textCtrl.style.display  = (name === 'text') ? ''     : 'none';

        if (!_fc) return;

        _unbindShapeEvents();

        if (name === 'brush') {
            _fc.isDrawingMode = true;
            _fc.selection     = false;
            _fc.freeDrawingBrush = new fabric.PencilBrush(_fc);
            _syncBrush();
            _fc.defaultCursor = 'crosshair';
        } else if (name === 'eraser') {
            _fc.isDrawingMode = true;
            _fc.selection     = false;
            _fc.freeDrawingBrush = new fabric.PencilBrush(_fc);
            _fc.freeDrawingBrush.color = '#ffffff';
            var size = parseInt($id('sh-ie-size-slider').value) || 8;
            _fc.freeDrawingBrush.width = size;
            _fc.defaultCursor = 'cell';
        } else if (name === 'rect' || name === 'ellipse' || name === 'line') {
            _fc.isDrawingMode = false;
            _fc.selection     = false;
            _fc.defaultCursor = 'crosshair';
            _bindShapeEvents(name);
        } else if (name === 'text') {
            _fc.isDrawingMode = false;
            _fc.selection     = true;
            _fc.defaultCursor = 'text';
            _bindTextEvents();
        }
    }

    // ── Shape event helpers ──────────────────────────────────────────
    function _shapeStrokeWidth() {
        return Math.max(1, parseInt(($id('sh-ie-size-slider') || {}).value) || 3);
    }

    function _shapeOpacity() {
        return ((parseInt(($id('sh-ie-opacity-slider') || {}).value) || 100) / 100);
    }

    function _unbindShapeEvents() {
        if (!_fc) return;
        _fc.off('mouse:down');
        _fc.off('mouse:move');
        _fc.off('mouse:up');
        _shapeOrigin  = null;
        _shapePreview = null;
        _shapeActive  = false;
    }

    function _getPointer(opt) {
        // Fabric 6: opt.absolutePointer or opt.pointer depending on event
        return opt.absolutePointer || opt.pointer || { x: 0, y: 0 };
    }

    function _bindShapeEvents(shapeName) {
        _fc.on('mouse:down', function (opt) {
            if (opt.target && opt.target !== _baseImg) return; // clicked an existing object
            var pt = _getPointer(opt);
            _shapeOrigin = { x: pt.x, y: pt.y };
            _shapeActive = true;

            var sw  = _shapeStrokeWidth();
            var op  = _shapeOpacity();
            var cfg = {
                left:            pt.x,
                top:             pt.y,
                stroke:          _color,
                strokeWidth:     sw,
                fill:            'transparent',
                opacity:         op,
                selectable:      false,
                evented:         false,
                strokeUniform:   true,
            };

            if (shapeName === 'rect') {
                _shapePreview = new fabric.Rect(Object.assign(cfg, { width: 1, height: 1 }));
            } else if (shapeName === 'ellipse') {
                _shapePreview = new fabric.Ellipse(Object.assign(cfg, { rx: 1, ry: 1, originX: 'left', originY: 'top' }));
            } else if (shapeName === 'line') {
                _shapePreview = new fabric.Line([pt.x, pt.y, pt.x, pt.y], {
                    stroke:        _color,
                    strokeWidth:   sw,
                    opacity:       op,
                    selectable:    false,
                    evented:       false,
                    strokeUniform: true,
                });
            }
            if (_shapePreview) _fc.add(_shapePreview);
        });

        _fc.on('mouse:move', function (opt) {
            if (!_shapeActive || !_shapePreview) return;
            var pt  = _getPointer(opt);
            var ox  = _shapeOrigin.x;
            var oy  = _shapeOrigin.y;
            var dx  = pt.x - ox;
            var dy  = pt.y - oy;

            if (shapeName === 'rect') {
                var l = dx < 0 ? pt.x : ox;
                var t = dy < 0 ? pt.y : oy;
                _shapePreview.set({ left: l, top: t, width: Math.abs(dx), height: Math.abs(dy) });
            } else if (shapeName === 'ellipse') {
                var l = dx < 0 ? pt.x : ox;
                var t = dy < 0 ? pt.y : oy;
                _shapePreview.set({ left: l, top: t, rx: Math.abs(dx) / 2, ry: Math.abs(dy) / 2 });
            } else if (shapeName === 'line') {
                _shapePreview.set({ x2: pt.x, y2: pt.y });
            }
            _fc.renderAll();
        });

        _fc.on('mouse:up', function () {
            if (!_shapeActive || !_shapePreview) return;
            _shapeActive = false;
            // Finalize: make selectable
            _shapePreview.set({ selectable: true, evented: true });
            _shapePreview = null;
            _shapeOrigin  = null;
            _fc.renderAll();
            _histPush();
        });
    }

    function _bindTextEvents() {
        _fc.on('mouse:down', function (opt) {
            // Only place new text if we clicked on empty canvas (not an existing object)
            if (opt.target) return;
            var pt       = _getPointer(opt);
            var fontSize = parseInt(($id('sh-ie-font-slider') || {}).value) || 24;
            var itext    = new fabric.IText('Text', {
                left:     pt.x,
                top:      pt.y,
                fontSize: fontSize,
                fill:     _color,
                fontFamily: 'sans-serif',
                selectable: true,
                evented:    true,
            });
            _fc.add(itext);
            _fc.setActiveObject(itext);
            itext.enterEditing();
            itext.selectAll();
            _fc.renderAll();
            // Push history when text finishes editing
            itext.on('editing:exited', function () { _histPush(); });
        });
    }

    function _syncBrush() {
        if (!_fc || !_fc.freeDrawingBrush || _tool !== 'brush') return;
        var size    = parseInt($id('sh-ie-size-slider').value)     || 8;
        var opacity = (parseInt($id('sh-ie-opacity-slider').value) || 100) / 100;
        var rgb = _hexToRgb(_color);
        _fc.freeDrawingBrush.width = size;
        _fc.freeDrawingBrush.color = 'rgba(' + rgb.r + ',' + rgb.g + ',' + rgb.b + ',' + opacity + ')';
    }

    // ── Color ─────────────────────────────────────────────────────────
    function _setColor(hex) {
        _color = hex;
        var sw = $id('sh-ie-color-swatch');
        if (sw) sw.style.background = hex;
        _syncBrush();
        // Also update the fill/stroke of any currently selected object
        if (_fc) {
            var active = _fc.getActiveObject();
            if (active && active !== _baseImg) {
                if (active.type === 'i-text' || active.type === 'text') {
                    active.set('fill', hex);
                } else {
                    active.set('stroke', hex);
                }
                _fc.renderAll();
            }
        }
    }

    // ── Zoom / Pan helpers ────────────────────────────────────────────
    function _updateZoomDisplay() {
        var el = $id('sh-ie-zoom-val');
        if (el) el.textContent = Math.round(_zoom * 100) + '%';
    }

    function _resetZoom() {
        if (!_fc) return;
        _zoom = 1;
        _fc.setViewportTransform([1, 0, 0, 1, 0, 0]);
        _updateZoomDisplay();
    }

    function _bindZoomPan(wrap) {
        if (wrap._shIeZoomBound) return;   // idempotent — only bind once
        wrap._shIeZoomBound = true;

        // ── Mouse wheel → zoom to cursor ─────────────────────────────
        wrap.addEventListener('wheel', function (e) {
            e.preventDefault();
            if (!_fc) return;

            var delta   = e.deltaY * -0.001;
            var newZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, _zoom * (1 + delta)));
            if (newZoom === _zoom) return;

            // Zoom toward the pointer position
            var rect  = wrap.getBoundingClientRect();
            var point = new fabric.Point(
                e.clientX - rect.left,
                e.clientY - rect.top
            );
            _fc.zoomToPoint(point, newZoom);
            _zoom = newZoom;
            _updateZoomDisplay();
        }, { passive: false });

        // ── Middle-click drag → pan ───────────────────────────────────
        wrap.addEventListener('mousedown', function (e) {
            if (e.button !== 1) return;  // middle button only
            e.preventDefault();
            _startPan(e.clientX, e.clientY, wrap);
        });

        // pointer moves and releases are handled globally so they
        // don't get swallowed by the canvas element
        document.addEventListener('mousemove', function (e) {
            if (!_isPanning) return;
            var dx = e.clientX - _panLastX;
            var dy = e.clientY - _panLastY;
            _panLastX = e.clientX;
            _panLastY = e.clientY;
            if (_fc) _fc.relativePan(new fabric.Point(dx, dy));
        });

        document.addEventListener('mouseup', function () {
            if (_isPanning) {
                var wrapEl = $id('sh-ie-canvas-wrap');
                _endPan(wrapEl);
            }
        });
    }

    function _startPan(clientX, clientY, wrap) {
        _isPanning  = true;
        _panLastX   = clientX;
        _panLastY   = clientY;
        var area = wrap ? wrap.closest('.sh-ie-canvas-area') : null;
        if (area) { area.classList.remove('is-pan-ready'); area.classList.add('is-panning'); }
    }

    function _endPan(wrap) {
        _isPanning = false;
        var area = wrap ? wrap.closest('.sh-ie-canvas-area') : null;
        if (area) {
            area.classList.remove('is-panning');
            if (_spaceDown) area.classList.add('is-pan-ready');
        }
    }

    // ── Canvas bootstrap ─────────────────────────────────────────────
    function _makeCanvas(wrap) {
        // Force a layout flush so clientWidth/Height are accurate
        var w = wrap.clientWidth  || 800;
        var h = wrap.clientHeight || 600;
        var el = document.createElement('canvas');
        wrap.appendChild(el);

        _fc = new fabric.Canvas(el, {
            width:               w,
            height:              h,
            backgroundColor:     null,
            enableRetinaScaling: false,
            selection:           false,
        });

        // Push snapshot after each free-drawing stroke
        _fc.on('path:created', function () { _histPush(); });
    }

    function _loadSource(src) {
        if (!src) { _showError('No image source provided.'); return; }
        var url = (src instanceof Blob || src instanceof File)
            ? (_objUrl = URL.createObjectURL(src))
            : src;

        _showLoading(true);
        _hideError();

        fabric.Image.fromURL(url, { crossOrigin: 'anonymous' })
            .then(function (img) {
                var canvasW = _fc.width;
                var canvasH = _fc.height;
                var scale   = Math.min(canvasW / img.width, canvasH / img.height, 1);

                img.set({
                    scaleX:        scale,
                    scaleY:        scale,
                    left:          (canvasW - img.width  * scale) / 2,
                    top:           (canvasH - img.height * scale) / 2,
                    selectable:    false,
                    evented:       false,
                    hasControls:   false,
                    hasBorders:    false,
                    lockMovementX: true,
                    lockMovementY: true,
                    lockScalingX:  true,
                    lockScalingY:  true,
                    lockRotation:  true,
                });

                _fc.add(img);
                _fc.sendObjectToBack(img);
                _baseImg = img;
                _fc.renderAll();
                _showLoading(false);
                _histPush(); // initial "clean" snapshot
            })
            .catch(function (err) {
                console.error('ImageEditor: image load error', err);
                _showLoading(false);
                _showError('Failed to load image. Please check the source and try again.');
            });
    }

    // ── Export ────────────────────────────────────────────────────────
    function _exportPNG() {
        var dataURL = _fc.toDataURL({ format: 'png', multiplier: 1 });
        return _dataURLToBlob(dataURL);
    }

    function _dataURLToBlob(dataURL) {
        var parts  = dataURL.split(',');
        var mime   = parts[0].match(/:(.*?);/)[1];
        var raw    = atob(parts[1]);
        var arr    = new Uint8Array(raw.length);
        for (var i = 0; i < raw.length; i++) arr[i] = raw.charCodeAt(i);
        return new Blob([arr], { type: mime });
    }

    // ── UI state helpers ─────────────────────────────────────────────
    function _showLoading(on) {
        var el = $id('sh-ie-loading');
        if (el) el.style.display = on ? 'flex' : 'none';
    }

    function _showError(msg) {
        var el    = $id('sh-ie-error');
        var msgEl = $id('sh-ie-error-msg');
        if (el)    el.style.display = 'flex';
        if (msgEl) msgEl.textContent = msg;
    }

    function _hideError() {
        var el = $id('sh-ie-error');
        if (el) el.style.display = 'none';
    }

    function _showFooterError(msg) {
        var el = $id('sh-ie-footer-alert');
        if (!el) return;
        el.textContent   = msg;
        el.style.display = msg ? '' : 'none';
    }

    function _setSaveSpinner(on) {
        var btn   = $id('sh-ie-save-btn');
        var label = $id('sh-ie-save-label');
        if (!btn) return;
        btn.disabled = on;
        var icon = btn.querySelector('[data-lucide]');
        if (icon) {
            var newIcon = document.createElement('i');
            newIcon.setAttribute('data-lucide', on ? 'loader-2' : 'save');
            if (on) newIcon.className = 'sh-ie-spin';
            icon.parentNode.replaceChild(newIcon, icon);
            if (typeof initLucide === 'function') initLucide();
        }
        if (label) label.textContent = on ? 'Saving…' : 'Save';
    }

    // ── Save ─────────────────────────────────────────────────────────
    function _handleSave(choice) {
        if (!_opts || !_opts.onSave) { close(); return; }
        _showFooterError('');
        _setSaveSpinner(true);

        var blob = _exportPNG();
        var result;
        try {
            result = _opts.onSave(blob, choice || 'overwrite');
        } catch (err) {
            _showFooterError(err.message || 'Save failed.');
            _setSaveSpinner(false);
            return;
        }

        if (result && typeof result.then === 'function') {
            result.then(function () {
                close();
            }).catch(function (err) {
                _showFooterError(err.message || 'Save failed. Please try again.');
                _setSaveSpinner(false);
            });
        } else {
            close();
        }
    }

    // ── Unsaved-changes guard ─────────────────────────────────────────
    function _guardedClose() {
        var hasEdits = _history.length > 1;
        if (hasEdits && !confirm('Discard changes?')) return;
        close();
    }

    // ── Keyboard shortcuts ────────────────────────────────────────────
    function _keydown(e) {
        var modal = $id('sh-ie-modal');
        if (!modal || modal.style.display === 'none') return;

        if (e.key === 'Escape') {
            e.preventDefault();
            _guardedClose();
            return;
        }
        if ((e.ctrlKey || e.metaKey) && (e.key === 'z' || e.key === 'Z')) {
            e.preventDefault();
            if (e.shiftKey) _redo(); else _undo();
            return;
        }

        // Space → activate pan mode (pan-ready cursor; actual drag via mousedown)
        if (e.key === ' ' && !e.ctrlKey && !e.metaKey) {
            if (!_spaceDown) {
                _spaceDown = true;
                var area = document.querySelector('.sh-ie-canvas-area');
                if (area) area.classList.add('is-pan-ready');
            }
            // Prevent page scroll only when editor is open
            e.preventDefault();
        }

        // Don't hijack tool shortcuts while typing in an input or on an IText
        if (e.target.matches('input, textarea, select, [contenteditable]')) return;
        if (_fc && _fc.getActiveObject() && _fc.getActiveObject().isEditing) return;

        if (e.key === 'b' || e.key === 'B') _applyTool('brush');
        if (e.key === 'e' || e.key === 'E') _applyTool('eraser');
        if (e.key === 'r' || e.key === 'R') _applyTool('rect');
        if (e.key === 'o' || e.key === 'O') _applyTool('ellipse');
        if (e.key === 'l' || e.key === 'L') _applyTool('line');
        if (e.key === 't' || e.key === 'T') _applyTool('text');

        // 0 → reset zoom
        if (e.key === '0' || e.key === 'Numpad0') _resetZoom();
    }

    function _keyup(e) {
        var modal = $id('sh-ie-modal');
        if (!modal || modal.style.display === 'none') return;
        if (e.key === ' ') {
            _spaceDown = false;
            if (_isPanning) {
                var wrap = $id('sh-ie-canvas-wrap');
                _endPan(wrap);
            }
            var area = document.querySelector('.sh-ie-canvas-area');
            if (area) area.classList.remove('is-pan-ready');
        }
    }

    // ── Wire modal interactions (once) ───────────────────────────────
    function _wire() {
        if (_wired) return;
        var modal = $id('sh-ie-modal');
        if (!modal) return;
        _wired = true;

        // Tool buttons
        modal.querySelectorAll('.sh-ie-tool-btn[data-tool]').forEach(function (btn) {
            btn.addEventListener('click', function () { _applyTool(btn.dataset.tool); });
        });

        // Color swatch → ColorPicker
        var colorBtn = $id('sh-ie-color-btn');
        if (colorBtn && window.ColorPicker) {
            ColorPicker.attach(colorBtn, {
                initialColor: _color,
                paletteKey:   'imageeditor',
                onChange:     function (hex) { _setColor(hex); },
                onCommit:     function (hex) { _setColor(hex); },
            });
        }

        // Undo / Redo
        $id('sh-ie-undo-btn').addEventListener('click', _undo);
        $id('sh-ie-redo-btn').addEventListener('click', _redo);

        // Clear
        $id('sh-ie-clear-btn').addEventListener('click', function () {
            if (!_fc) return;
            if (!confirm('Clear all drawings?')) return;
            _fc.getObjects().slice().forEach(function (obj) {
                if (obj !== _baseImg) _fc.remove(obj);
            });
            _fc.renderAll();
            _histPush();
        });

        // Size slider
        $id('sh-ie-size-slider').addEventListener('input', function () {
            $id('sh-ie-size-val').textContent = this.value;
            if (_tool === 'brush') {
                _syncBrush();
            } else if (_tool === 'eraser' && _fc && _fc.freeDrawingBrush) {
                _fc.freeDrawingBrush.width = parseInt(this.value) || 8;
            }
        });

        // Opacity slider
        $id('sh-ie-opacity-slider').addEventListener('input', function () {
            $id('sh-ie-opacity-val').textContent = this.value + '%';
            _syncBrush();
        });

        // Font size slider (text tool)
        var fontSlider = $id('sh-ie-font-slider');
        if (fontSlider) {
            fontSlider.addEventListener('input', function () {
                $id('sh-ie-font-val').textContent = this.value;
                if (_fc) {
                    var active = _fc.getActiveObject();
                    if (active && (active.type === 'i-text' || active.type === 'text')) {
                        active.set('fontSize', parseInt(this.value) || 24);
                        _fc.renderAll();
                    }
                }
            });
        }

        // Close / Cancel
        $id('sh-ie-close-btn').addEventListener('click',  _guardedClose);
        $id('sh-ie-cancel-btn').addEventListener('click', _guardedClose);

        // Error panel
        $id('sh-ie-retry-btn').addEventListener('click', function () {
            _hideError();
            if (_opts && _opts.source) _loadSource(_opts.source);
        });
        $id('sh-ie-err-cancel-btn').addEventListener('click', function () { close(); });

        // Save / Save-as-new
        $id('sh-ie-save-btn').addEventListener('click',   function () { _handleSave('overwrite'); });
        $id('sh-ie-saveas-btn').addEventListener('click', function () { _handleSave('new'); });

        // Zoom reset button
        var zoomReset = $id('sh-ie-zoom-reset');
        if (zoomReset) zoomReset.addEventListener('click', _resetZoom);

        // Space+drag pan: capture mousedown on canvas wrap
        var wrap = $id('sh-ie-canvas-wrap');
        if (wrap) {
            wrap.addEventListener('mousedown', function (e) {
                if (e.button === 0 && _spaceDown) {
                    e.preventDefault();
                    e.stopPropagation();
                    _startPan(e.clientX, e.clientY, wrap);
                }
            });
        }

        // Global keyboard shortcuts (persisted; guards itself against closed state)
        document.addEventListener('keydown', _keydown);
        document.addEventListener('keyup',   _keyup);
    }

    // ── Public: open ─────────────────────────────────────────────────
    function open(opts) {
        if (typeof fabric === 'undefined') {
            console.error('ImageEditor: fabric.js is not loaded');
            return;
        }
        _opts         = opts || {};
        _history      = [];
        _histPos      = -1;
        _histLock     = false;
        _tool         = 'brush';
        _color        = '#000000';
        _baseImg      = null;
        _shapeOrigin  = null;
        _shapePreview = null;
        _shapeActive  = false;

        var modal = $id('sh-ie-modal');
        if (!modal) { console.error('ImageEditor: #sh-ie-modal not found in DOM'); return; }

        // Set title
        var titleEl = $id('sh-ie-title');
        if (titleEl) {
            titleEl.textContent = _opts.filename
                ? 'Editor — ' + _opts.filename
                : 'Image Editor';
        }

        // Show/hide Save-as-new button
        var saveAsBtn = $id('sh-ie-saveas-btn');
        if (saveAsBtn) saveAsBtn.style.display = (_opts.mode === 'file-existing') ? '' : 'none';

        // Reset controls
        var sizeSlider = $id('sh-ie-size-slider');
        var opSlider   = $id('sh-ie-opacity-slider');
        if (sizeSlider) { sizeSlider.value = '8';   $id('sh-ie-size-val').textContent = '8'; }
        if (opSlider)   { opSlider.value   = '100'; $id('sh-ie-opacity-val').textContent = '100%'; }

        // Reset UI state
        _setColor('#000000');
        _showLoading(false);
        _hideError();
        _showFooterError('');
        _setSaveSpinner(false);
        _refreshHistBtns();

        // Show modal and prevent body scroll
        modal.style.display = 'flex';
        document.body.style.overflow = 'hidden';

        // Wire interactions (idempotent)
        _wire();

        // Reset zoom state
        _zoom      = 1;
        _spaceDown = false;
        _isPanning = false;
        _updateZoomDisplay();
        var canvasArea = document.querySelector('.sh-ie-canvas-area');
        if (canvasArea) { canvasArea.classList.remove('is-pan-ready', 'is-panning'); }

        // (Re-)create Fabric canvas
        var wrap = $id('sh-ie-canvas-wrap');
        wrap.innerHTML = '';
        _makeCanvas(wrap);
        _bindZoomPan(wrap);
        _applyTool('brush');

        // Re-render Lucide icons inside the modal
        if (typeof initLucide === 'function') initLucide();

        // Load image into canvas
        _loadSource(_opts.source);
    }

    // ── Public: close ─────────────────────────────────────────────────
    function close() {
        var onCancel = _opts && _opts.onCancel;

        var modal = $id('sh-ie-modal');
        if (modal) modal.style.display = 'none';
        document.body.style.overflow = '';

        if (_fc) {
            _fc.dispose();
            _fc = null;
        }
        if (_objUrl) {
            URL.revokeObjectURL(_objUrl);
            _objUrl = null;
        }

        _opts         = null;
        _baseImg      = null;
        _history      = [];
        _histPos      = -1;
        _shapeOrigin  = null;
        _shapePreview = null;
        _shapeActive  = false;

        if (onCancel) onCancel();
    }

    // ── Utility ───────────────────────────────────────────────────────
    function _hexToRgb(hex) {
        hex = hex.replace(/^#/, '');
        if (hex.length === 3) hex = hex[0]+hex[0]+hex[1]+hex[1]+hex[2]+hex[2];
        var n = parseInt(hex, 16);
        return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
    }

    // ── CSRF helper ──────────────────────────────────────────────────
    function _getCsrfHeaders() {
        var tokenEl  = document.querySelector('meta[name="_csrf"]');
        var headerEl = document.querySelector('meta[name="_csrf_header"]');
        var h = {};
        if (tokenEl && headerEl) h[headerEl.content] = tokenEl.content;
        return h;
    }

    // ── Reload files table via GET with HTMX-style headers ───────────
    function _reloadFilesTable(folderId) {
        return fetch('/folders/' + folderId, {
            headers: {
                'HX-Request': 'true',
                'HX-Target':  'files-table-container',
            },
        }).then(function(resp) {
            if (!resp.ok) throw new Error('Reload failed');
            return resp.text();
        }).then(function(html) {
            var container = $id('files-table-container');
            if (!container) return;
            container.innerHTML = html;
            if (typeof initLucide === 'function') initLucide();
            if (window.htmx) htmx.process(container);
        });
    }

    // ── POST handler for overwrite / save-as-new ─────────────────────
    function _saveFileEdit(blob, choice, fileId, folderId, filename) {
        var fd      = new FormData();
        var csrfH   = _getCsrfHeaders();
        var pngName = filename.replace(/\.[^.]+$/, '') + '.png';

        if (choice === 'new') {
            fd.append('file', blob, pngName);
            return fetch('/folders/' + folderId + '/files', {
                method:  'POST',
                body:    fd,
                headers: csrfH,
            }).then(function(resp) {
                if (!resp.ok) throw new Error('Upload failed');
                return _reloadFilesTable(folderId);
            });
        } else {
            fd.append('image', blob, pngName);
            return fetch('/files/' + fileId + '/edit', {
                method:  'POST',
                body:    fd,
                headers: csrfH,
            }).then(function(resp) {
                if (!resp.ok) throw new Error('Save failed');
                return resp.text();
            }).then(function(html) {
                var container = $id('files-table-container');
                if (!container) return;
                container.innerHTML = html;
                if (typeof initLucide === 'function') initLucide();
                if (window.htmx) htmx.process(container);
            });
        }
    }

    // ── Delegated handler for folder-detail Edit buttons ─────────────
    function _initFolderDetailEdit() {
        document.addEventListener('click', function(e) {
            var btn = e.target.closest('[data-edit-image]');
            if (!btn) return;
            e.preventDefault();

            var fileId   = btn.dataset.editImage;
            var url      = btn.dataset.editUrl;
            var folderId = btn.dataset.editFolder;
            var filename = btn.dataset.editFilename || 'image.png';

            ImageEditor.open({
                source:   url,
                filename: filename,
                mode:     'file-existing',
                onSave:   function(blob, choice) {
                    return _saveFileEdit(blob, choice, fileId, folderId, filename);
                },
            });
        });
    }

    // ── Auto-wire on DOMContentLoaded ────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        if ($id('sh-ie-modal')) _wire();
        _initFolderDetailEdit();
    });

    window.ImageEditor = { open: open, close: close };
}());
