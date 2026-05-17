/* ============================================================
   StudyHelper — In-app PDF Viewer (PDF.js)
   window.PdfViewer = { open, close }
   ============================================================ */
import * as pdfjsLib from '/js/lib/pdfjs/pdf.min.mjs';

pdfjsLib.GlobalWorkerOptions.workerSrc = '/js/lib/pdfjs/pdf.worker.min.mjs';

const $id = (id) => document.getElementById(id);

let _pdf = null;
let _opts = null;
let _scale = 1;
let _fitBase = 1;
let _baseWidth = 0;
let _observer = null;
let _wired = false;
const _historyKey = { pv: true };

const MIN_SCALE = 0.25;
const MAX_SCALE = 5;

function _show(on) {
    const modal = $id('sh-pv-modal');
    if (modal) modal.style.display = on ? 'flex' : 'none';
    document.body.style.overflow = on ? 'hidden' : '';
}

function _isOpen() {
    const modal = $id('sh-pv-modal');
    return modal && modal.style.display !== 'none';
}

function _setState(state) {
    $id('sh-pv-loading').style.display = state === 'loading' ? 'flex' : 'none';
    $id('sh-pv-error').style.display = state === 'error' ? 'flex' : 'none';
}

async function open(opts) {
    _opts = opts || {};
    _scale = 1;
    const modal = $id('sh-pv-modal');
    if (!modal) { console.error('PdfViewer: #sh-pv-modal not found'); return; }
    _wire();
    $id('sh-pv-title').textContent = _opts.name || 'PDF';
    $id('sh-pv-pages').innerHTML = '';
    $id('sh-pv-page-val').textContent = '\u2013';
    _setState('loading');
    _show(true);
    history.pushState(_historyKey, '');
    try {
        _pdf = await pdfjsLib.getDocument({ url: _opts.url }).promise;
        const first = await _pdf.getPage(1);
        _baseWidth = first.getViewport({ scale: 1 }).width;
        _fitBase = _fitScale();
        _scale = _fitBase;
        _buildPages();
        _setState('ready');
        _updateZoomLabel();
        _updatePageIndicator();
    } catch (err) {
        console.error('PdfViewer: failed to load PDF', err);
        _setState('error');
    }
}

function _fitScale() {
    const body = $id('sh-pv-body');
    const avail = ((body && body.clientWidth) || 800) - 48;
    if (!_baseWidth) return 1;
    return Math.max(MIN_SCALE, Math.min(MAX_SCALE, avail / _baseWidth));
}

function _buildPages() {
    const container = $id('sh-pv-pages');
    container.innerHTML = '';
    if (_observer) _observer.disconnect();
    _observer = new IntersectionObserver(_onIntersect, {
        root: $id('sh-pv-body'),
        rootMargin: '300px 0px',
    });
    for (let n = 1; n <= _pdf.numPages; n++) {
        const pageEl = document.createElement('div');
        pageEl.className = 'sh-pv-page';
        pageEl.dataset.page = String(n);
        pageEl.dataset.rendered = 'false';
        pageEl.appendChild(document.createElement('canvas'));
        container.appendChild(pageEl);
        _observer.observe(pageEl);
    }
}

function _onIntersect(entries) {
    entries.forEach((entry) => {
        if (entry.isIntersecting && entry.target.dataset.rendered === 'false') {
            _renderPage(entry.target);
        }
    });
}

async function _renderPage(pageEl) {
    if (!_pdf) return;
    const n = parseInt(pageEl.dataset.page, 10);
    pageEl.dataset.rendered = 'true';
    try {
        const page = await _pdf.getPage(n);
        const viewport = page.getViewport({ scale: _scale });
        const canvas = pageEl.querySelector('canvas');
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise;
    } catch (err) {
        console.error('PdfViewer: page ' + n + ' render failed', err);
        pageEl.dataset.rendered = 'false';
    }
}

function _rerenderAll() {
    if (!_observer) return;
    document.querySelectorAll('#sh-pv-pages .sh-pv-page').forEach((el) => {
        el.dataset.rendered = 'false';
        _observer.unobserve(el);
        _observer.observe(el);
    });
}

function _zoom(factor) {
    const next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, _scale * factor));
    if (next === _scale) return;
    _scale = next;
    _updateZoomLabel();
    _rerenderAll();
}

function _updateZoomLabel() {
    const pct = _fitBase ? Math.round((_scale / _fitBase) * 100) : 100;
    $id('sh-pv-zoom-val').textContent = pct + '%';
}

function _updatePageIndicator() {
    if (!_pdf) return;
    const body = $id('sh-pv-body');
    const mid = body.scrollTop + body.clientHeight / 2;
    let current = 1;
    document.querySelectorAll('#sh-pv-pages .sh-pv-page').forEach((el) => {
        if (el.offsetTop <= mid) current = parseInt(el.dataset.page, 10);
    });
    $id('sh-pv-page-val').textContent = current + ' / ' + _pdf.numPages;
}

function _toSplitter() {
    const opts = _opts;
    close();
    if (window.PdfSplitter) {
        window.PdfSplitter.open(opts);
    } else if (window.ensurePdfSplitter) {
        window.ensurePdfSplitter().then((splitter) => splitter.open(opts));
    } else {
        console.error('PdfViewer: PdfSplitter is not available');
    }
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
    $id('sh-pv-close-btn').addEventListener('click', close);
    $id('sh-pv-backdrop').addEventListener('click', close);
    $id('sh-pv-error-close').addEventListener('click', close);
    $id('sh-pv-zoom-in').addEventListener('click', () => _zoom(1.25));
    $id('sh-pv-zoom-out').addEventListener('click', () => _zoom(0.8));
    $id('sh-pv-download').addEventListener('click', () => {
        if (_opts) window.location.href = '/files/' + _opts.fileId + '/download';
    });
    $id('sh-pv-split-btn').addEventListener('click', _toSplitter);
    $id('sh-pv-body').addEventListener('scroll', _updatePageIndicator);
    document.addEventListener('keydown', _onKeydown);
    window.addEventListener('popstate', () => { if (_isOpen()) close(); });
}

function close() {
    if (history.state === _historyKey) history.back();
    _show(false);
    if (_observer) { _observer.disconnect(); _observer = null; }
    if (_pdf) { _pdf.destroy(); _pdf = null; }
    const pages = $id('sh-pv-pages');
    if (pages) pages.innerHTML = '';
    _opts = null;
}

window.PdfViewer = { open, close };
