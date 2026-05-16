/* ============================================================
   StudyHelper — PDF Splitter (PDF.js)
   window.PdfSplitter = { open, close }
   ============================================================ */
import * as pdfjsLib from '/js/lib/pdfjs/pdf.min.mjs';

pdfjsLib.GlobalWorkerOptions.workerSrc = '/js/lib/pdfjs/pdf.worker.min.mjs';

const $id = (id) => document.getElementById(id);
const THUMB_WIDTH = 150;
const TINT_COUNT = 6;

let _pdf = null;        // PDFDocumentProxy
let _opts = null;       // { fileId, url, name }
let _numPages = 0;
let _breaks = new Set();  // page numbers AFTER which a split occurs
let _wired = false;

function _baseName(name) {
    return (name || 'document.pdf').replace(/\.[^.]+$/, '');
}

function _show(on) {
    const modal = $id('sh-ps-modal');
    if (modal) modal.style.display = on ? 'flex' : 'none';
    document.body.style.overflow = on ? 'hidden' : '';
}

function _isOpen() {
    const modal = $id('sh-ps-modal');
    return modal && modal.style.display !== 'none';
}

function _setState(state) {
    $id('sh-ps-loading').style.display = state === 'loading' ? 'flex' : 'none';
    $id('sh-ps-error').style.display = state === 'error' ? 'flex' : 'none';
    $id('sh-ps-single').style.display = state === 'single' ? 'flex' : 'none';
    $id('sh-ps-workspace').style.display = state === 'ready' ? 'flex' : 'none';
}

function _showFooterError(msg) {
    const el = $id('sh-ps-footer-alert');
    if (!el) return;
    el.textContent = msg || '';
    el.style.display = msg ? '' : 'none';
}

async function open(opts) {
    _opts = opts || {};
    _breaks = new Set();
    const modal = $id('sh-ps-modal');
    if (!modal) { console.error('PdfSplitter: #sh-ps-modal not found'); return; }
    _wire();
    $id('sh-ps-title').textContent = 'Split \u2014 ' + (_opts.name || 'PDF');
    $id('sh-ps-pages').innerHTML = '';
    $id('sh-ps-parts').innerHTML = '';
    _showFooterError('');
    _setState('loading');
    _show(true);
    try {
        _pdf = await pdfjsLib.getDocument({ url: _opts.url }).promise;
        _numPages = _pdf.numPages;
        if (_numPages < 2) {
            _setState('single');
            return;
        }
        await _renderThumbnails();
        _setState('ready');
        _refreshParts();
    } catch (err) {
        console.error('PdfSplitter: failed to load PDF', err);
        _setState('error');
    }
}

async function _renderThumbnails() {
    const grid = $id('sh-ps-pages');
    grid.innerHTML = '';
    for (let n = 1; n <= _numPages; n++) {
        const page = await _pdf.getPage(n);
        const baseVp = page.getViewport({ scale: 1 });
        const viewport = page.getViewport({ scale: THUMB_WIDTH / baseVp.width });
        const canvas = document.createElement('canvas');
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise;

        const card = document.createElement('div');
        card.className = 'sh-ps-page';
        card.dataset.page = String(n);

        const thumb = document.createElement('div');
        thumb.className = 'sh-ps-thumb';
        thumb.appendChild(canvas);
        card.appendChild(thumb);

        const label = document.createElement('div');
        label.className = 'sh-ps-page-label';
        label.textContent = 'Page ' + n;
        card.appendChild(label);

        if (n < _numPages) {
            const brk = document.createElement('button');
            brk.type = 'button';
            brk.className = 'sh-ps-break';
            brk.dataset.after = String(n);
            brk.setAttribute('aria-label', 'Toggle split after page ' + n);
            brk.innerHTML = '<iconify-icon icon="lucide:scissors"></iconify-icon>';
            brk.addEventListener('click', () => _toggleBreak(n));
            card.appendChild(brk);
        }
        grid.appendChild(card);
    }
}

function _toggleBreak(afterPage) {
    if (_breaks.has(afterPage)) _breaks.delete(afterPage);
    else _breaks.add(afterPage);
    _refreshParts();
}

function _computeParts() {
    const sorted = Array.from(_breaks).sort((a, b) => a - b);
    const ranges = [];
    let start = 1;
    sorted.forEach((b) => {
        ranges.push({ startPage: start, endPage: b });
        start = b + 1;
    });
    ranges.push({ startPage: start, endPage: _numPages });
    return ranges;
}

function _refreshParts() {
    const ranges = _computeParts();

    document.querySelectorAll('#sh-ps-pages .sh-ps-page').forEach((card) => {
        const pageNum = parseInt(card.dataset.page, 10);
        const idx = ranges.findIndex((r) => pageNum >= r.startPage && pageNum <= r.endPage);
        card.dataset.part = String(idx % TINT_COUNT);
        const brk = card.querySelector('.sh-ps-break');
        if (brk) brk.classList.toggle('is-active', _breaks.has(pageNum));
    });

    const panel = $id('sh-ps-parts');
    const prev = [];
    panel.querySelectorAll('.sh-ps-part-name').forEach((inp) => prev.push(inp.value));
    panel.innerHTML = '';
    const base = _baseName(_opts.name);
    ranges.forEach((r, i) => {
        const row = document.createElement('div');
        row.className = 'sh-ps-part';
        row.dataset.part = String(i % TINT_COUNT);

        const head = document.createElement('div');
        head.className = 'sh-ps-part-head';
        head.textContent = r.endPage > r.startPage
            ? 'Part ' + (i + 1) + ' \u00b7 pages ' + r.startPage + '\u2013' + r.endPage
            : 'Part ' + (i + 1) + ' \u00b7 page ' + r.startPage;
        row.appendChild(head);

        const input = document.createElement('input');
        input.type = 'text';
        input.className = 'sh-input sh-ps-part-name';
        input.value = prev[i] !== undefined ? prev[i] : base + '-' + (i + 1) + '.pdf';
        row.appendChild(input);

        panel.appendChild(row);
    });

    $id('sh-ps-save-count').textContent = String(ranges.length);
    $id('sh-ps-save-btn').disabled = ranges.length < 2;
}

function _csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    const out = {};
    if (token && header) out[header.content] = token.content;
    return out;
}

function _reloadFilesView(folderId) {
    const isDashboard = !!$id('library-grid-container');
    const url = isDashboard ? '/dashboard' : ('/folders/' + folderId + '?tab=files');
    const targetId = isDashboard ? 'library-grid-container' : 'folder-tabs-section';
    return fetch(url, { headers: { 'HX-Request': 'true', 'HX-Target': targetId } })
        .then((resp) => {
            if (!resp.ok) throw new Error('Reload failed');
            return resp.text();
        })
        .then((html) => {
            const container = $id(targetId);
            if (!container) return;
            if (targetId === 'folder-tabs-section') container.outerHTML = html;
            else container.innerHTML = html;
            const fresh = $id(targetId);
            if (typeof initLucide === 'function') initLucide();
            if (window.htmx && fresh) window.htmx.process(fresh);
        });
}

function _save() {
    const ranges = _computeParts();
    if (ranges.length < 2) return;
    const inputs = document.querySelectorAll('#sh-ps-parts .sh-ps-part-name');
    const base = _baseName(_opts.name);
    const parts = ranges.map((r, i) => {
        let name = (inputs[i] ? inputs[i].value : '').trim();
        if (name === '') name = base + '-' + (i + 1) + '.pdf';
        if (!/\.pdf$/i.test(name)) name += '.pdf';
        return { name: name, startPage: r.startPage, endPage: r.endPage };
    });

    _showFooterError('');
    $id('sh-ps-save-btn').disabled = true;

    fetch('/files/' + _opts.fileId + '/split', {
        method: 'POST',
        headers: Object.assign({ 'Content-Type': 'application/json' }, _csrfHeaders()),
        body: JSON.stringify({ parts: parts }),
    })
        .then((resp) => {
            if (!resp.ok) {
                return resp.json().catch(() => ({})).then((data) => {
                    throw new Error(data.error || 'Could not split this PDF.');
                });
            }
            return resp.json();
        })
        .then((data) => _reloadFilesView(data.folderId))
        .then(() => close())
        .catch((err) => {
            _showFooterError(err.message || 'Could not split this PDF.');
            $id('sh-ps-save-btn').disabled = false;
        });
}

function _onKeydown(e) {
    if (_isOpen() && e.key === 'Escape') {
        e.preventDefault();
        close();
    }
}

function _wire() {
    if (_wired) return;
    _wired = true;
    $id('sh-ps-close-btn').addEventListener('click', close);
    $id('sh-ps-cancel-btn').addEventListener('click', close);
    $id('sh-ps-save-btn').addEventListener('click', _save);
    document.addEventListener('keydown', _onKeydown);
}

function close() {
    _show(false);
    if (_pdf) { _pdf.destroy(); _pdf = null; }
    const pages = $id('sh-ps-pages');
    const parts = $id('sh-ps-parts');
    if (pages) pages.innerHTML = '';
    if (parts) parts.innerHTML = '';
    _breaks = new Set();
    _opts = null;
}

document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-split-file-id]');
    if (!btn) return;
    e.preventDefault();
    open({
        fileId: btn.dataset.splitFileId,
        url: btn.dataset.splitUrl,
        name: btn.dataset.splitFilename,
    });
});

window.PdfSplitter = { open, close };
