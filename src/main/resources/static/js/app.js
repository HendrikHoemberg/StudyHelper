/* ============================================================
   StudyHelper — App JavaScript (HTMX Simplified)
   ============================================================ */

function t(key) { return (window.i18n && window.i18n[key]) || key; }

const featureScriptPromises = new Map();
let colorPickerPromise = null;
let imageEditorPromise = null;
let pdfViewerPromise = null;
let pdfSplitterPromise = null;
let examRuntimePromise = null;

function loadFeatureScript(src) {
    if (featureScriptPromises.has(src)) return featureScriptPromises.get(src);

    const promise = new Promise((resolve, reject) => {
        const existing = document.querySelector('script[src="' + src + '"]');
        if (existing) {
            if (existing.dataset.loaded === 'true') {
                resolve();
            } else {
                existing.addEventListener('load', resolve, { once: true });
                existing.addEventListener('error', reject, { once: true });
            }
            return;
        }

        const script = document.createElement('script');
        script.src = src;
        script.async = true;
        script.addEventListener('load', () => {
            script.dataset.loaded = 'true';
            resolve();
        }, { once: true });
        script.addEventListener('error', () => reject(new Error('Could not load ' + src)), { once: true });
        document.head.appendChild(script);
    });

    featureScriptPromises.set(src, promise);
    return promise;
}

function ensureColorPicker() {
    if (window.ColorPicker) {
        if (typeof window.ColorPicker.autoInit === 'function') window.ColorPicker.autoInit();
        return Promise.resolve(window.ColorPicker);
    }
    if (!colorPickerPromise) {
        colorPickerPromise = loadFeatureScript('/js/color-picker.js').then(() => {
            if (window.ColorPicker && typeof window.ColorPicker.autoInit === 'function') {
                window.ColorPicker.autoInit();
            }
            return window.ColorPicker;
        });
    }
    return colorPickerPromise;
}

function ensureImageEditor() {
    if (window.ImageEditor && !window.ImageEditor._lazyStub) return Promise.resolve(window.ImageEditor);
    if (!imageEditorPromise) {
        imageEditorPromise = loadFeatureScript('/js/lib/fabric.min.js')
            .then(() => ensureColorPicker())
            .then(() => loadFeatureScript('/js/image-editor.js'))
            .then(() => window.ImageEditor);
    }
    return imageEditorPromise;
}

function ensurePdfViewer() {
    if (window.PdfViewer) return Promise.resolve(window.PdfViewer);
    if (!pdfViewerPromise) {
        pdfViewerPromise = import('/js/pdf-viewer.js').then(() => window.PdfViewer);
    }
    return pdfViewerPromise;
}

function ensurePdfSplitter() {
    if (window.PdfSplitter) return Promise.resolve(window.PdfSplitter);
    if (!pdfSplitterPromise) {
        pdfSplitterPromise = import('/js/pdf-splitter.js').then(() => window.PdfSplitter);
    }
    return pdfSplitterPromise;
}

function ensureExamRuntime() {
    if (window.initExamRuntime) return Promise.resolve(window.initExamRuntime);
    if (!examRuntimePromise) {
        examRuntimePromise = loadFeatureScript('/js/exam.js').then(() => window.initExamRuntime);
    }
    return examRuntimePromise;
}

window.ensureColorPicker = ensureColorPicker;
window.ensureImageEditor = ensureImageEditor;
window.ensurePdfViewer = ensurePdfViewer;
window.ensurePdfSplitter = ensurePdfSplitter;
window.ensureExamRuntime = ensureExamRuntime;

if (!window.ImageEditor) {
    window.ImageEditor = {
        _lazyStub: true,
        open: (opts) => ensureImageEditor().then((editor) => editor.open(opts)),
        close: () => ensureImageEditor().then((editor) => editor.close()),
    };
}

document.addEventListener('DOMContentLoaded', () => {
    registerServiceWorker();
    initTheme();
    initLucide();
    initTopnav();
    initBottomSheet();
    updateActiveNavLink();
    initSidebarDrawer();
    initSidebarFolderExpand();
    initFolderToggleButtons();
    initCsrf();
    initLightbox();
    initLazyFeatureLoaders();
    initShDialog();
    initQuizAnswerForm();
    initLazyExamRuntime(document);
    if (window.initCustomSteppers) window.initCustomSteppers(document);
});

function initLazyFeatureLoaders() {
    document.body.addEventListener('click', (e) => {
        const trigger = e.target.closest('[data-color-picker]');
        if (!trigger || (window.ColorPicker && trigger._shCpAttached)) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        ensureColorPicker()
            .then(() => trigger.click())
            .catch((err) => console.error('Color picker failed to load', err));
    }, true);

    document.body.addEventListener('click', (e) => {
        const trigger = e.target.closest('.sh-pdf-viewer-trigger');
        if (!trigger) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        ensurePdfViewer()
            .then((viewer) => viewer.open({
                fileId: trigger.dataset.fileId,
                url: trigger.dataset.fileUrl,
                name: trigger.dataset.fileName,
            }))
            .catch((err) => console.error('PDF viewer failed to load', err));
    }, true);

    document.body.addEventListener('click', (e) => {
        const trigger = e.target.closest('[data-split-file-id]');
        if (!trigger) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        ensurePdfSplitter()
            .then((splitter) => splitter.open({
                fileId: trigger.dataset.splitFileId,
                url: trigger.dataset.splitUrl,
                name: trigger.dataset.splitFilename,
            }))
            .catch((err) => console.error('PDF splitter failed to load', err));
    }, true);

    document.body.addEventListener('click', (e) => {
        const trigger = e.target.closest('[data-edit-image]');
        if (!trigger) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        ensureImageEditor()
            .then((editor) => editor.openFileEdit(trigger))
            .catch((err) => console.error('Image editor failed to load', err));
    }, true);
}

function initLazyExamRuntime(root) {
    const scope = root || document;
    if (!scope.querySelector || !scope.querySelector('#exam-runtime, .sh-exam-timer, .sh-exam-answer, .sh-exam-loader-text')) {
        return;
    }
    ensureExamRuntime().catch((err) => console.error('Exam runtime failed to load', err));
}

function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) return;

    navigator.serviceWorker.register('/service-worker.js').catch((error) => {
        console.warn('Service worker registration failed:', error);
    });
}

// Re-run initializations after HTMX swaps
document.body.addEventListener('htmx:afterSwap', () => {
    if (window.initCustomSteppers) window.initCustomSteppers(document);
    initQuizAnswerForm();
    initLazyExamRuntime(document);
    updateActiveNavLink();

    // Auto-close bottom sheet and folders drawer on htmx swaps
    const sheet = document.getElementById('mobile-more-sheet');
    const sheetBackdrop = document.getElementById('sh-bottom-sheet-backdrop');
    const moreBtn = document.getElementById('bottom-more-btn');
    if (sheet) sheet.classList.remove('is-open');
    if (sheetBackdrop) sheetBackdrop.classList.remove('is-open');
    if (moreBtn) moreBtn.classList.remove('active');

    const sidebar = document.getElementById('sidebar-aside');
    const sidebarBackdrop = document.getElementById('sh-sidebar-backdrop');
    if (sidebar) sidebar.classList.remove('is-open');
    if (sidebarBackdrop) sidebarBackdrop.classList.remove('is-open');

    // Toggle sidebar visibility class to prevent flashing
    const shell = document.querySelector('.sh-explorer-shell');
    if (shell) {
        const hasStudy = document.getElementById('study-session-content') !== null ||
                         document.getElementById('quiz-session-content') !== null ||
                         document.querySelector('.sh-flashcard-generator') !== null;
        shell.classList.toggle('sh-hide-sidebar', hasStudy);
    }

    // Trigger celebration confetti for completion screens
    const celebrateEl = document.querySelector('[data-celebrate="true"]');
    if (celebrateEl && !celebrateEl.hasAttribute('data-celebrated')) {
        celebrateEl.setAttribute('data-celebrated', 'true');
        const score = parseInt(celebrateEl.getAttribute('data-score')) || 0;
        // Minor delay for visual settlement
        setTimeout(() => {
            triggerCelebration(score);
        }, 250);
    }
});

// Optional fade-in animation after settle
document.body.addEventListener('htmx:afterSettle', (e) => {
    const target = e.detail.target;
    if (target && target.classList.contains('sh-fade-in')) {
        target.classList.add('animate-fade-in');
    }
});


/* ---------- Global Lightbox ---------- */
function initLightbox() {
    const lb = document.getElementById('sh-lightbox');
    if (!lb) return;

    const lbImg = document.getElementById('sh-lightbox-img');
    let lastTrigger = null;

    function closeLightbox() {
        lb.style.display = 'none';
        lastTrigger = null;
    }

    document.body.addEventListener('click', (e) => {
        const trigger = e.target.closest('.sh-lightbox-trigger');
        if (trigger) {
            e.preventDefault();
            e.stopPropagation();
            
            // Try to get image source from src, data-src, or href
            const src = trigger.src || trigger.dataset.src || trigger.href;
            if (src) {
                lastTrigger = trigger;
                lbImg.src = src + (src.indexOf('?') === -1 ? '?t=' : '&t=') + Date.now();
                lb.style.display = 'flex';
            }
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeLightbox();
    });

    lb.addEventListener('click', closeLightbox);
    document.getElementById('sh-lightbox-close')?.addEventListener('click', closeLightbox);

    document.getElementById('sh-lightbox-download')?.addEventListener('click', () => {
        if (!lastTrigger) return;
        const fileId = lastTrigger.dataset.fileId;
        if (fileId) {
            window.location.href = '/files/' + fileId + '/download';
        } else {
            const src = lbImg.src;
            const match = src.match(/\/files\/(\d+)\/view/);
            if (match) {
                window.location.href = '/files/' + match[1] + '/download';
            }
        }
    });

    document.getElementById('sh-lightbox-edit-btn')?.addEventListener('click', () => {
        if (!lastTrigger) return;
        const src = lbImg.src;
        const fileId = lastTrigger.dataset.fileId;
        const folderId = lastTrigger.dataset.fileFolderId;
        const filename = lastTrigger.dataset.fileName || 'image.png';
        closeLightbox();

        if (window.ImageEditor) {
            var cacheBustSrc = src + (src.indexOf('?') === -1 ? '?t=' : '&t=') + Date.now();
            if (fileId && folderId) {
                window.ImageEditor.open({
                    source: cacheBustSrc,
                    filename: filename,
                    mode: 'file-existing',
                    onSave: function(blob, choice, customName) {
                        var fd = new FormData();
                        var pngName = filename.replace(/\.[^.]+$/, '') + '.png';
                        var tokenEl = document.querySelector('meta[name="_csrf"]');
                        var headerEl = document.querySelector('meta[name="_csrf_header"]');
                        var h = {};
                        if (tokenEl && headerEl) h[headerEl.content] = tokenEl.content;

                        function reloadLibrary() {
                            var isDashboard = !!document.getElementById('library-grid-container');
                            var url = isDashboard ? '/dashboard' : ('/folders/' + folderId + '?tab=files');
                            var targetId = isDashboard ? 'library-grid-container' : 'folder-tabs-section';
                            return fetch(url, {
                                headers: { 'HX-Request': 'true', 'HX-Target': targetId },
                            }).then(function(r) { return r.text(); }).then(function(html) {
                                var container = document.getElementById(targetId);
                                if (!container) return;
                                if (targetId === 'folder-tabs-section') {
                                    container.outerHTML = html;
                                    container = document.getElementById(targetId);
                                } else {
                                    container.innerHTML = html;
                                }
                                if (typeof initLucide === 'function') initLucide();
                                if (window.htmx) htmx.process(container);
                                var t = Date.now();
                                container.querySelectorAll('img[src*="/files/"]').forEach(function (img) {
                                    var s = img.getAttribute('src');
                                    if (s) img.setAttribute('src', s + (s.indexOf('?') === -1 ? '?t=' : '&t=') + t);
                                });
                                container.querySelectorAll('a.sh-lightbox-trigger[href*="/files/"]').forEach(function (a) {
                                    var h = a.getAttribute('href');
                                    if (h) a.setAttribute('href', h + (h.indexOf('?') === -1 ? '?t=' : '&t=') + t);
                                });
                            });
                        }

                        if (choice === 'new') {
                            fd.append('file', blob, pngName);
                            return fetch('/folders/' + folderId + '/files', {
                                method: 'POST',
                                body: fd,
                                headers: h,
                            }).then(function(resp) {
                                if (!resp.ok) throw new Error('Upload failed');
                                return reloadLibrary();
                            });
                        } else {
                            fd.append('image', blob, pngName);
                            return fetch('/files/' + fileId + '/edit', {
                                method: 'POST',
                                body: fd,
                                headers: h,
                            }).then(function(resp) {
                                if (!resp.ok) throw new Error('Save failed');
                                return reloadLibrary();
                            });
                        }
                    },
                });
            } else {
                window.ImageEditor.open({
                    source: cacheBustSrc,
                    filename: filename,
                    mode: 'flashcard-new',
                });
            }
        }
    });
}

/* ---------- Re-init on history restore ---------- */
// HTMX history restoration replaces body.innerHTML, destroying event listeners
// on child elements. This re-binds component-specific listeners after restore.
document.body.addEventListener('htmx:historyRestore', () => {
    setTimeout(() => {
        initTopnav();
        initSidebarDrawer();
        updateActiveNavLink();
    }, 0);
});

/* ---------- CSRF ---------- */
function initCsrf() {
    document.body.addEventListener('htmx:configRequest', (evt) => {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (csrfToken && csrfHeader) {
            evt.detail.headers[csrfHeader] = csrfToken;
        }
    });
}

/* ---------- Theme ---------- */
function initTheme() {
    const saved = localStorage.getItem('theme') || 'light';
    document.documentElement.dataset.theme = saved;
}

function toggleTheme() {
    const current = document.documentElement.dataset.theme || 'light';
    const next = current === 'light' ? 'dark' : 'light';
    document.documentElement.dataset.theme = next;
    localStorage.setItem('theme', next);
}

/* ---------- Icons (Iconify Only) ---------- */
function initIcons() {
    // No-op: iconify-icon web component handles its own lifecycle
}

function initLucide() {
    initIcons();
}

/* ---------- Top Navigation (mobile) ---------- */
function initTopnav() {
    const toggle = document.getElementById('topnav-menu-btn');
    const menu = document.getElementById('topnav-mobile-menu');
    const backdrop = document.getElementById('sh-sidebar-backdrop');
    if (!toggle || !menu) return;

    const closeMenu = () => {
        if (!menu.classList.contains('open')) return;
        menu.classList.remove('open');
        if (backdrop) backdrop.classList.remove('is-open');
    };

    const openMenu = () => {
        // Close folders drawer if open so only one drawer is visible at a time
        const sidebar = document.getElementById('sidebar-aside');
        if (sidebar) sidebar.classList.remove('is-open');
        menu.classList.add('open');
        if (backdrop) backdrop.classList.add('is-open');
    };

    toggle.addEventListener('click', () => {
        if (menu.classList.contains('open')) closeMenu();
        else openMenu();
    });

    menu.querySelectorAll('a').forEach(link => {
        link.addEventListener('click', closeMenu);
    });

    if (backdrop) backdrop.addEventListener('click', closeMenu);

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeMenu();
    });
}

/* ---------- Mobile Bottom Sheet Controller ---------- */
function initBottomSheet() {
    const moreBtn = document.getElementById('bottom-more-btn');
    const folderBtn = document.getElementById('bottom-folders-btn');
    const sheet = document.getElementById('mobile-more-sheet');
    const backdrop = document.getElementById('sh-bottom-sheet-backdrop');
    
    if (!sheet || !backdrop) return;

    const openSheet = () => {
        // Automatically close Folders sidebar drawer to prevent double overlay
        const sidebar = document.getElementById('sidebar-aside');
        const shBackdrop = document.getElementById('sh-sidebar-backdrop');
        if (sidebar) sidebar.classList.remove('is-open');
        if (shBackdrop) shBackdrop.classList.remove('is-open');
        
        sheet.classList.add('is-open');
        backdrop.classList.add('is-open');
        moreBtn?.classList.add('active');
    };

    const closeSheet = () => {
        sheet.classList.remove('is-open');
        backdrop.classList.remove('is-open');
        moreBtn?.classList.remove('active');
    };

    moreBtn?.addEventListener('click', (e) => {
        e.preventDefault();
        if (sheet.classList.contains('is-open')) closeSheet();
        else openSheet();
    });

    backdrop.addEventListener('click', closeSheet);

    folderBtn?.addEventListener('click', (e) => {
        closeSheet();
        const topnavFoldersBtn = document.getElementById('topnav-folders-btn');
        if (topnavFoldersBtn) {
            topnavFoldersBtn.click();
        }
    });

    sheet.querySelector('.sh-bottom-sheet-handle')?.addEventListener('click', closeSheet);

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeSheet();
    });
}

/* ---------- Active Navigation Indicator ---------- */
function updateActiveNavLink() {
    const path = window.location.pathname;
    
    // Determine which key is active
    let activeKey = null;
    if (path === '/dashboard' || path.startsWith('/folders/') || path.startsWith('/decks/')) {
        activeKey = 'dashboard';
    } else if (path.startsWith('/study/') || path.startsWith('/quiz/') || path.startsWith('/session/') || path.startsWith('/sessions/')) {
        activeKey = 'study';
    } else if (path.startsWith('/flashcards/')) {
        activeKey = 'flashcards';
    } else if (path.startsWith('/exams') || path.startsWith('/exam')) {
        activeKey = 'exams';
    } else if (path.startsWith('/admin')) {
        activeKey = 'admin';
    }
    
    const desktopLinks = document.querySelectorAll('.topnav-link');
    const mobileLinks = document.querySelectorAll('.topnav-mobile-link');
    const bottomLinks = document.querySelectorAll('.bottom-tab');
    
    const updateLinks = (links, isBottomBar = false) => {
        links.forEach(link => {
            const href = link.getAttribute('href');
            let isMatch = false;
            
            const adjustDashboard = isBottomBar && path.startsWith('/folders/');
            
            if (activeKey === 'dashboard' && href === '/dashboard' && !adjustDashboard) {
                isMatch = true;
            } else if (activeKey === 'study' && href === '/study/start') {
                isMatch = true;
            } else if (activeKey === 'flashcards' && href === '/flashcards/generate') {
                isMatch = true;
            } else if (activeKey === 'exams' && href === '/exams') {
                isMatch = true;
            } else if (activeKey === 'admin' && href === '/admin') {
                isMatch = true;
            }
            
            link.classList.toggle('active', isMatch);
        });
    };
    
    updateLinks(desktopLinks);
    updateLinks(mobileLinks);
    updateLinks(bottomLinks, true);
    
    // Specifically handle active state of bottom folders button when in a folders view
    const bottomFoldersBtn = document.getElementById('bottom-folders-btn');
    if (bottomFoldersBtn) {
        bottomFoldersBtn.classList.toggle('active', path.startsWith('/folders/'));
    }
}

/* ---------- Sidebar Drawer (mobile) ---------- */
function initSidebarDrawer() {
    const btn = document.getElementById('topnav-folders-btn');
    const backdrop = document.getElementById('sh-sidebar-backdrop');
    if (!btn || !backdrop) return;

    const getSidebar = () => document.getElementById('sidebar-aside');

    const open = () => {
        const sidebar = getSidebar();
        if (!sidebar) return;
        // Close burger menu drawer if open so only one drawer is visible at a time
        const menu = document.getElementById('topnav-mobile-menu');
        if (menu) menu.classList.remove('open');
        sidebar.classList.add('is-open');
        backdrop.classList.add('is-open');
    };

    const close = () => {
        const sidebar = getSidebar();
        if (sidebar) sidebar.classList.remove('is-open');
        backdrop.classList.remove('is-open');
    };

    btn.addEventListener('click', () => {
        const sidebar = getSidebar();
        if (sidebar && sidebar.classList.contains('is-open')) close();
        else open();
    });

    backdrop.addEventListener('click', close);

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') close();
    });

    // Close when navigating via a sidebar link
    document.body.addEventListener('click', (e) => {
        const sidebar = getSidebar();
        if (!sidebar || !sidebar.classList.contains('is-open')) return;
        const link = e.target.closest('a');
        if (link && sidebar.contains(link)) close();
    });

    // Close when an HTMX swap replaces the sidebar (e.g. OOB refresh after mutation)
    document.body.addEventListener('htmx:afterSwap', () => {
        backdrop.classList.remove('is-open');
        const menu = document.getElementById('topnav-mobile-menu');
        if (menu) menu.classList.remove('open');
    });
}

/* ---------- Sidebar folder two-stage click ---------- */
// Folders with subfolders: first click expands, second click navigates.
// Folders without subfolders: navigate immediately (default behavior).
function initSidebarFolderExpand() {
    document.addEventListener('click', (e) => {
        const link = e.target.closest('.sh-d-pill-name');
        if (!link) return;
        const folder = link.closest('.sh-d-folder.has-children');
        if (!folder) return;
        if (folder.classList.contains('is-open')) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        folder.classList.add('is-open');
    }, true);
}

/* ---------- Folder toggle buttons (sidebar cap + explorer tree headers) ---------- */
function initFolderToggleButtons() {
    document.addEventListener('click', (e) => {
        const cap = e.target.closest('button.sh-d-pill-cap');
        if (cap) {
            cap.closest('.sh-d-folder')?.classList.toggle('is-open');
            return;
        }
        const header = e.target.closest('.sh-tree-header');
        if (header) {
            header.closest('.sh-tree-item')?.classList.toggle('expanded');
        }
    });
}

/* ---------- Color Picker Hex Sync ---------- */
function syncColorHex(input, target) {
    const targetEl = document.getElementById(target);
    if (targetEl) targetEl.value = input.value;
}

/* ---------- Folder Icons (Iconify Search) ---------- */

let iconifySearchTimeout = null;

function handleIconSearch(input) {
    debouncedIconifySearch(input);
}

function debouncedIconifySearch(input) {
    clearTimeout(iconifySearchTimeout);
    const query = input.value.trim();
    const modal = input.closest('.sh-modal');
    const grid = modal.querySelector('.sh-icon-grid');

    if (query.length < 2) {
        grid.innerHTML = '<div class="sh-sidebar-empty">' + t('app.icon-search.type-at-least') + '</div>';
        return;
    }

    grid.innerHTML = '<div class="sh-sidebar-empty">' + t('app.icon-search.searching') + '</div>';
    
    const squarePrefixes = 'lucide,heroicons,ph,mdi,tabler,octicon,bi,ri,fluent,carbon,ic';

    iconifySearchTimeout = setTimeout(() => {
        fetch(`https://api.iconify.design/search?query=${encodeURIComponent(query)}&limit=40&prefixes=${squarePrefixes}`)
            .then(res => res.json())
            .then(data => {
                renderIconifyResults(modal, data.icons || []);
            })
            .catch(err => {
                grid.innerHTML = '<div class="sh-sidebar-empty">' + t('app.icon-search.error') + '</div>';
            });
    }, 500);
}

function renderIconifyResults(modal, icons) {
    const grid = modal.querySelector('.sh-icon-grid');
    const selectedIcon = modal.querySelector('input[name="iconName"]')?.value;
    
    if (icons.length === 0) {
        grid.innerHTML = '<div class="sh-sidebar-empty">' + t('app.icon-search.no-icons') + '</div>';
        return;
    }

    grid.innerHTML = icons.map(name =>
        `<button type="button" class="sh-icon-btn" data-icon="${name}" title="${name}" ` +
        `onclick="selectFolderIcon(this,'${name}')"><iconify-icon icon="${name}" style="font-size:20px"></iconify-icon></button>`
    ).join('');

    grid.querySelectorAll('.sh-icon-btn').forEach(b => {
        b.classList.toggle('is-selected', b.dataset.icon === selectedIcon);
    });
}

function renderIconGrid(modal, selectedIcon) {
    if (!modal) return;
    const grid = modal.querySelector('.sh-icon-grid');
    if (!grid) return;

    if (!grid.innerHTML) {
        grid.innerHTML = '<div class="sh-sidebar-empty">' + t('app.icon-search.start-typing') + '</div>';
    }

    grid.querySelectorAll('.sh-icon-btn').forEach(b => {
        b.classList.toggle('is-selected', b.dataset.icon === selectedIcon);
    });
}

function selectFolderIcon(btn, iconName) {
    const modal = btn.closest('.sh-modal');
    modal.querySelectorAll('.sh-icon-btn.is-selected').forEach(b => b.classList.remove('is-selected'));
    btn.classList.add('is-selected');
    
    const iconInput = modal.querySelector('input[name="iconName"]');
    if (iconInput) iconInput.value = iconName;
    
    updateFolderPreview(modal);
}

function filterFolderIcons(input) {
    const q = input.value.toLowerCase();
    const modal = input.closest('.sh-modal');
    const buttons = modal.querySelectorAll('.sh-icon-btn');
    
    buttons.forEach(btn => {
        const matches = q.length === 0 || btn.dataset.icon.toLowerCase().includes(q);
        btn.style.display = matches ? 'flex' : 'none';
    });
}

function updateFolderPreview(modal) {
    const preview = modal.querySelector('.sh-edit-preview');
    if (!preview) return;
    
    const color = modal.querySelector('input[name="colorHex"]')?.value || '#0f766e';
    const iconName = modal.querySelector('input[name="iconName"]')?.value || 'folder';
    
    preview.style.setProperty('--preview-color', color);
    
    const fullIconName = iconName.includes(':') ? iconName : `lucide:${iconName}`;
    preview.innerHTML = `<iconify-icon icon="${fullIconName}" style="font-size:22px;"></iconify-icon>`;
}

/* ---------- Folder tab filters ---------- */
document.addEventListener('input', (event) => {
    const input = event.target.closest('[data-sh-tab-filter]');
    if (!input) return;

    const targetSelector = input.dataset.shTabFilter;
    const grid = document.querySelector(targetSelector);
    if (!grid) return;

    const query = input.value.trim().toLowerCase();
    let visibleCount = 0;

    grid.querySelectorAll('[data-filter-text]').forEach(item => {
        const text = (item.dataset.filterText || item.textContent || '').toLowerCase();
        const visible = query === '' || text.includes(query);
        item.hidden = !visible;
        if (visible) visibleCount += 1;
    });

    document.querySelectorAll(`[data-sh-filter-empty-for="${targetSelector}"]`).forEach(empty => {
        empty.hidden = visibleCount > 0 || query === '';
    });
});

/* ---------- Custom Dialog (replaces native confirm/alert/prompt) ---------- */

let shDialogState = null;

function initShDialog() {
    const dlg = document.getElementById('sh-dialog');
    if (!dlg) return;

    dlg.addEventListener('click', (e) => {
        if (e.target.closest('[data-sh-dialog-cancel]')) {
            shDialogResolve(null);
        } else if (e.target.id === 'sh-dialog-details-toggle') {
            const details = document.getElementById('sh-dialog-details');
            const expanded = details && details.style.display !== 'none';
            if (details) details.style.display = expanded ? 'none' : '';
            e.target.textContent = expanded ? t('app.dialog.toggle-details') : t('app.dialog.toggle-details-hide');
        } else if (e.target.id === 'sh-dialog-ok') {
            const input = document.getElementById('sh-dialog-input');
            const textarea = document.getElementById('sh-dialog-textarea');
            const isTextPrompt = input && input.style.display !== 'none';
            const isTextareaPrompt = textarea && textarea.style.display !== 'none';
            shDialogResolve(isTextPrompt ? input.value : (isTextareaPrompt ? textarea.value : true));
        }
    });

    document.addEventListener('keydown', (e) => {
        if (!dlg.classList.contains('is-open')) return;
        if (e.key === 'Escape') {
            e.preventDefault();
            shDialogResolve(null);
        } else if (e.key === 'Enter') {
            const input = document.getElementById('sh-dialog-input');
            const textarea = document.getElementById('sh-dialog-textarea');
            const isPrompt = input && input.style.display !== 'none';
            const isTextareaPrompt = textarea && textarea.style.display !== 'none';
            if (isTextareaPrompt && document.activeElement === textarea && !(e.ctrlKey || e.metaKey)) return;
            // For prompt, only submit on Enter when input is focused.
            if (isPrompt && document.activeElement !== input) return;
            e.preventDefault();
            shDialogResolve(isTextareaPrompt ? textarea.value : (isPrompt ? input.value : true));
        }
    });

    document.body.addEventListener('htmx:confirm', (evt) => {
        if (!evt.detail.question) return;
        evt.preventDefault();
        shConfirm({ message: evt.detail.question, danger: true })
            .then((ok) => { if (ok) evt.detail.issueRequest(true); });
    });
}

function shDialogResolve(value) {
    const dlg = document.getElementById('sh-dialog');
    if (!dlg) return;
    dlg.classList.remove('is-open', 'is-danger');
    dlg.setAttribute('aria-hidden', 'true');
    const state = shDialogState;
    shDialogState = null;
    if (state) state.resolve(value);
}

function _shOpenDialog({ title, message, icon, iconKind, confirmText, cancelText, danger, prompt, textareaPrompt, defaultValue, placeholder, hideCancel, technicalDetails }) {
    return new Promise((resolve) => {
        // If a previous dialog is open, dismiss it first.
        if (shDialogState) shDialogResolve(null);

        const dlg = document.getElementById('sh-dialog');
        if (!dlg) {
            // Fallback to native if fragment is missing.
            if (prompt) return resolve(window.prompt(message, defaultValue || ''));
            if (hideCancel) { window.alert(message); return resolve(true); }
            return resolve(window.confirm(message));
        }

        const titleEl = document.getElementById('sh-dialog-title');
        const msgEl = document.getElementById('sh-dialog-message');
        const iconEl = document.getElementById('sh-dialog-icon');
        const okBtn = document.getElementById('sh-dialog-ok');
        const cancelBtn = document.getElementById('sh-dialog-cancel');
        const input = document.getElementById('sh-dialog-input');
        const textarea = document.getElementById('sh-dialog-textarea');
        const detailsToggle = document.getElementById('sh-dialog-details-toggle');
        const detailsEl = document.getElementById('sh-dialog-details');

        titleEl.textContent = title || (prompt ? t('app.dialog.enter-value') : (hideCancel ? t('app.dialog.notice') : t('app.dialog.confirm')));
        msgEl.textContent = message || '';
        msgEl.style.display = message ? '' : 'none';

        const iconName = icon || (iconKind === 'info' ? 'lucide:info'
            : iconKind === 'edit' ? 'lucide:pencil'
            : danger ? 'lucide:alert-triangle'
            : 'lucide:help-circle');
        iconEl.innerHTML = `<iconify-icon icon="${iconName}"></iconify-icon>`;

        okBtn.textContent = confirmText || (prompt ? t('app.dialog.save') : t('app.dialog.ok'));
        cancelBtn.textContent = cancelText || t('app.dialog.cancel');
        cancelBtn.style.display = hideCancel ? 'none' : '';

        if (prompt) {
            input.style.display = '';
            input.value = defaultValue || '';
            input.placeholder = placeholder || '';
            textarea.style.display = 'none';
            textarea.value = '';
        } else if (textareaPrompt) {
            input.style.display = 'none';
            input.value = '';
            textarea.style.display = '';
            textarea.value = defaultValue || '';
            textarea.placeholder = placeholder || '';
            textarea.maxLength = 1000;
        } else {
            input.style.display = 'none';
            input.value = '';
            textarea.style.display = 'none';
            textarea.value = '';
        }

        if (detailsToggle && detailsEl) {
            const hasDetails = !!technicalDetails;
            detailsToggle.style.display = hasDetails ? '' : 'none';
            detailsToggle.textContent = t('app.dialog.toggle-details');
            detailsEl.style.display = 'none';
            detailsEl.textContent = hasDetails ? technicalDetails : '';
        }

        dlg.classList.toggle('is-danger', !!danger);
        dlg.classList.add('is-open');
        dlg.setAttribute('aria-hidden', 'false');

        shDialogState = { resolve };

        // Focus management
        setTimeout(() => {
            if (prompt) input.focus();
            else if (textareaPrompt) textarea.focus();
            else okBtn.focus();
        }, 0);
    });
}

/* ---------- Quiz answer form ---------- */
function initQuizAnswerForm() {
    const form = document.querySelector('.sh-quiz-answer-form');
    if (!form || form.dataset.quizAnswerInit === 'true') return;
    form.dataset.quizAnswerInit = 'true';

    const multi = form.dataset.questionType === 'MULTIPLE_SELECT';
    const options = Array.from(form.querySelectorAll('.sh-quiz-option'));
    const submitBtn = form.querySelector('.sh-quiz-submit-btn');
    const hiddenWrap = form.querySelector('.sh-quiz-selected-inputs');
    if (!submitBtn || !hiddenWrap) return;

    function sync() {
        const selected = options.filter(o => o.classList.contains('is-selected'));
        hiddenWrap.innerHTML = '';
        selected.forEach(o => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'selectedOptions';
            input.value = o.dataset.index;
            hiddenWrap.appendChild(input);
        });
        submitBtn.disabled = selected.length === 0;
    }

    options.forEach(opt => {
        opt.addEventListener('click', () => {
            if (multi) {
                opt.classList.toggle('is-selected');
            } else {
                options.forEach(o => o.classList.toggle('is-selected', o === opt));
            }
            sync();
        });
    });
    sync();
}

function shConfirm(opts) {
    if (typeof opts === 'string') opts = { message: opts };
    return _shOpenDialog({ ...opts, prompt: false });
}

function shAlert(opts) {
    if (typeof opts === 'string') opts = { message: opts };
    return _shOpenDialog({ iconKind: 'info', hideCancel: true, ...opts, prompt: false });
}

function shPrompt(opts) {
    if (typeof opts === 'string') opts = { message: opts };
    return _shOpenDialog({ iconKind: 'edit', ...opts, prompt: true });
}

function shTextareaPrompt(opts) {
    if (typeof opts === 'string') opts = { message: opts };
    return _shOpenDialog({ iconKind: 'edit', ...opts, textareaPrompt: true });
}

function getCsrfHeaders() {
    const headers = {};
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
    return headers;
}

async function runAiPreflight(url, formEl) {
    const response = await fetch(url, {
        method: 'POST',
        headers: {
            ...getCsrfHeaders()
        },
        body: new URLSearchParams(new FormData(formEl))
    });
    if (response.ok) return true;
    const responseText = await response.text();
    const message = extractAiGenerationError(responseText);
    const details = extractAiGenerationDetails(responseText);
    showAiGenerationFailure(message, details);
    return false;
}

function openInstructionDialog(defaultValue) {
    return shTextareaPrompt({
        title: 'Add generation instructions',
        message: 'Optional: add extra guidance for the AI (focus areas, difficulty, formatting, exclusions, etc.).',
        placeholder: 'Example: Focus on key definitions and include one practical example per topic.',
        confirmText: 'Continue',
        defaultValue: defaultValue || ''
    });
}

/* ---------- AI Flashcard Generator ---------- */
const AI_PDF_SLOW_WARNING_BYTES = 5 * 1024 * 1024;

document.addEventListener('input', (event) => {
    if (!event.target.matches('#ai-pdf-search')) return;
    const q = event.target.value.toLowerCase();
    
    // Show/hide PDF rows
    document.querySelectorAll('.sh-ai-pdf-row').forEach(row => {
        const text = (row.dataset.name || row.innerText).toLowerCase();
        row.style.display = !q || text.includes(q) ? '' : 'none';
    });

    // Dynamically update foldertrees expand/collapse and visibility in Panel 1
    document.querySelectorAll('#ai-flashcard-panel-1 .vb-group, #ai-flashcard-panel-1 .vb-subgroup').forEach(folder => {
        if (!q) {
            folder.style.display = '';
            folder.classList.remove('is-search-expanded');
            return;
        }

        // Check if there are any visible PDF rows inside this folder
        const hasVisiblePdfs = !!folder.querySelector('.sh-ai-pdf-row:not([style*="display: none"])');
        if (hasVisiblePdfs) {
            folder.style.display = '';
            folder.classList.add('is-search-expanded');
        } else {
            folder.style.display = 'none';
            folder.classList.remove('is-search-expanded');
        }
    });
});

document.addEventListener('change', (event) => {
    if (event.target.matches('.sh-ai-pdf-row input[name="fileId"]')) {
        const row = event.target.closest('.sh-ai-pdf-row');
        row?.classList.toggle('is-selected', event.target.checked);
        syncAiFolderSelectionWithSelectedPdf();
    }
    if (event.target.matches('input[name="destination"]')) {
        updateAiFlashcardDestinationPanels();
    }
    if (event.target.matches('input[name="newDeckFolderId"]') && event.target.checked) {
        document.querySelectorAll('input[name="newDeckFolderId"]').forEach(cb => {
            if (cb !== event.target) {
                cb.checked = false;
            }
        });
    }
    if (event.target.matches('input[name="existingDeckId"]') || event.target.matches('input[name="newDeckFolderId"]')) {
        const targetValue = event.target.matches('input[name="existingDeckId"]') ? 'EXISTING_DECK' : 'NEW_DECK';
        const input = document.getElementById('ai-flashcard-destination-input');
        if (input && input.value !== targetValue) {
            input.value = targetValue;
            input.dispatchEvent(new Event('change', { bubbles: true }));
            
            // Sync toggle buttons
            const toggle = document.querySelector('.sh-ai-destination-toggle');
            toggle?.querySelectorAll('.sh-ai-destination-toggle-btn').forEach(btn => {
                btn.classList.toggle('is-active', btn.dataset.value === targetValue);
            });
            
            updateAiFlashcardDestinationPanels();
        }
    }
});

document.addEventListener('click', (event) => {
    const btn = event.target.closest('.sh-ai-global-pdf-mode .vb-pdf-mode-btn');
    if (!btn) return;
    event.preventDefault();
    const group = btn.closest('.sh-ai-global-pdf-mode');
    const hidden = group?.querySelector('input[name="documentMode"]');
    if (hidden) hidden.value = btn.dataset.mode;
    group?.querySelectorAll('.vb-pdf-mode-btn').forEach(option => {
        const selected = option === btn;
        option.classList.toggle('is-active', selected);
        option.setAttribute('aria-pressed', selected ? 'true' : 'false');
    });
    updateAiPdfSizeWarning();
});

document.body.addEventListener('click', (e) => {
    const destBtn = e.target.closest('.sh-ai-destination-toggle-btn');
    if (destBtn) {
        e.preventDefault();
        const val = destBtn.dataset.value;
        const input = document.getElementById('ai-flashcard-destination-input');
        if (input) {
            input.value = val;
            input.dispatchEvent(new Event('change', { bubbles: true }));
        }
        const group = destBtn.closest('.sh-ai-destination-toggle');
        group?.querySelectorAll('.sh-ai-destination-toggle-btn').forEach(b => {
            b.classList.toggle('is-active', b === destBtn);
        });
        updateAiFlashcardDestinationPanels();
    }
    const abortBtn = e.target.closest('#ai-gen-abort-btn');
    if (abortBtn) {
        const jobId = abortBtn.dataset.jobId;
        if (jobId) {
            e.preventDefault();
            shConfirm({
                title: t('flashcard-gen.cancel-confirm-title') || 'Cancel AI Generation',
                message: t('flashcard-gen.cancel-confirm-msg') || 'Are you sure you want to cancel the AI generation?',
                danger: true,
                confirmText: t('flashcard-gen.cancel-confirm-yes') || 'Yes, cancel',
                cancelText: t('flashcard-gen.cancel-confirm-no') || 'No, continue'
            }).then((ok) => {
                if (!ok) return;
                fetch(`/flashcards/generate/jobs/${jobId}/cancel`, {
                    method: 'POST',
                    headers: getCsrfHeaders()
                });
                const form = document.querySelector('form.sh-ai-flashcard-form');
                if (form) htmx.trigger(form, 'htmx:abort');

                const progressTarget = document.getElementById('ai-flashcard-progress-target');
                if (progressTarget) htmx.trigger(progressTarget, 'htmx:abort');
            });
        } else {
            const form = document.querySelector('form.sh-ai-flashcard-form');
            if (form) htmx.trigger(form, 'htmx:abort');
        }
    }
    const withInstructionsBtn = e.target.closest('#ai-flashcard-submit-with-instructions');
    if (withInstructionsBtn) {
        e.preventDefault();
        const form = withInstructionsBtn.closest('form.sh-ai-flashcard-form');
        if (!form) return;
        runAiPreflight('/flashcards/generate/preflight', form).then((ok) => {
            if (!ok) return;
            const hidden = form.querySelector('input[name="additionalInstructions"]');
            openInstructionDialog('').then((instructions) => {
                if (instructions === null) return;
                const normalizedInstructions = typeof instructions === 'string' ? instructions.trim() : '';
                if (hidden) hidden.value = normalizedInstructions;
                form.dataset.instructionsSubmit = 'true';
                form.requestSubmit();
            });
        });
    }
});

document.body.addEventListener('submit', (event) => {
    const form = event.target;
    if (!form.matches?.('form.sh-ai-flashcard-form')) return;
    if (hasUncheckedHighRiskAcknowledgement(form)) {
        event.preventDefault();
        showAiGenerationFailure('Please confirm the high-risk generation warning before continuing.');
        return;
    }
    const hidden = form.querySelector('input[name="additionalInstructions"]');
    if (!hidden) return;
    if (form.dataset.instructionsSubmit === 'true') {
        delete form.dataset.instructionsSubmit;
        return;
    }
    hidden.value = '';
});

document.body.addEventListener('htmx:afterSettle', () => {
    updateAiFlashcardDestinationPanels();
    syncAiFolderSelectionWithSelectedPdf();
});

document.body.addEventListener('htmx:beforeRequest', (event) => {
    if (!event.detail.elt?.matches?.('form.sh-ai-flashcard-form')) return;
    const modal = document.getElementById('ai-generating-modal');
    if (modal) modal.style.display = 'flex';
});

document.body.addEventListener('htmx:afterRequest', (event) => {
    if (!event.detail.elt?.matches?.('form.sh-ai-flashcard-form')) return;
    if (event.detail.successful) return; // Keep modal open during polling
    const modal = document.getElementById('ai-generating-modal');
    if (modal) modal.style.display = 'none';
});

function refreshFlashcardGenerationEstimate(form) {
    if (!form) return;
    const target = form.querySelector('#ai-generation-estimate');
    if (!target) return;
    fetch('/flashcards/generate/estimate', {
        method: 'POST',
        headers: {
            ...getCsrfHeaders(),
            'HX-Request': 'true'
        },
        body: new URLSearchParams(new FormData(form))
    })
        .then((response) => response.text())
        .then((html) => {
            target.outerHTML = html;
            const freshTarget = document.getElementById('ai-generation-estimate');
            if (window.htmx && freshTarget) {
                htmx.process(freshTarget);
            }
        })
        .catch(() => {});
}

window.refreshFlashcardGenerationEstimate = refreshFlashcardGenerationEstimate;

function hasUncheckedHighRiskAcknowledgement(form) {
    const acknowledgement = form?.querySelector?.('input[name="highRiskAcknowledged"]');
    return !!acknowledgement && !acknowledgement.checked;
}

document.body.addEventListener('change', (event) => {
    const form = event.target.closest?.('form.sh-ai-flashcard-form');
    if (!form) return;
    if (event.target.matches('input[name="fileId"], input[name="documentMode"], .sh-ai-global-pdf-mode input[name="documentMode"]')) {
        refreshFlashcardGenerationEstimate(form);
    }
});

function hideAiGenerationModal() {
    const modal = document.getElementById('ai-generating-modal');
    if (modal) modal.style.display = 'none';
}

function isAiGenerationRequest(source) {
    return source?.matches?.('form.sh-ai-flashcard-form, form.sh-study-setup-card, #ai-flashcard-progress-target');
}

function showAiGenerationFailure(message, technicalDetails) {
    hideAiGenerationModal();
    shAlert({
        title: 'AI generation failed',
        message: message || 'Please try again. If this keeps happening, reduce the selected sources or switch PDF mode.',
        technicalDetails,
        confirmText: 'Try again',
        danger: true
    });
}

function handleAiGenerationFailure(event) {
    const source = event.detail.elt;
    if (!isAiGenerationRequest(source)) return;
    const text = extractAiGenerationError(event.detail.xhr?.responseText);
    const details = extractAiGenerationDetails(event.detail.xhr?.responseText);
    showAiGenerationFailure(text, details);
}

document.body.addEventListener('htmx:responseError', handleAiGenerationFailure);

document.body.addEventListener('htmx:sendError', (event) => {
    const source = event.detail.elt;
    if (!isAiGenerationRequest(source)) return;
    showAiGenerationFailure('The AI request could not be sent. Please check your connection and try again.');
});

document.body.addEventListener('htmx:timeout', (event) => {
    const source = event.detail.elt;
    if (!isAiGenerationRequest(source)) return;
    showAiGenerationFailure('The AI request timed out. Please retry with fewer or smaller sources.');
});

document.body.addEventListener('htmx:abort', (event) => {
    const source = event.detail.elt;
    if (!isAiGenerationRequest(source)) return;
    hideAiGenerationModal();
    shAlert({
        title: 'AI generation cancelled',
        message: 'The AI generation request was cancelled.',
        confirmText: 'OK'
    });
});

function extractAiGenerationError(responseText) {
    if (!responseText) return '';
    const doc = new DOMParser().parseFromString(responseText, 'text/html');
    const error = doc.querySelector('[data-ai-generation-error="true"] .sh-alert-body, .sh-alert-danger .sh-alert-body');
    if (error?.textContent?.trim()) return error.textContent.trim();
    return doc.body?.textContent?.replace(/^Error:\s*/i, '').trim() || '';
}

function extractAiGenerationDetails(responseText) {
    if (!responseText) return '';
    const doc = new DOMParser().parseFromString(responseText, 'text/html');
    const details = doc.querySelector('[data-ai-generation-details="true"]');
    return details?.textContent?.trim() || '';
}

function updateAiFlashcardDestinationPanels() {
    const input = document.getElementById('ai-flashcard-destination-input');
    const selected = input?.value || document.querySelector('input[name="destination"]:checked')?.value || 'NEW_DECK';
    
    document.querySelectorAll('.sh-ai-destination-body').forEach(panel => {
        const isActive = panel.dataset.destinationPanel === selected;
        panel.style.display = isActive ? 'flex' : 'none';
        panel.classList.toggle('is-active', isActive);
    });
}

function preselectAiNewDeckFolderForPdf(pdfRow) {
    const folderId = pdfRow?.dataset?.folderId;
    if (!folderId) return;
    const folderRadio = document.querySelector(`.sh-ai-tree input[name="newDeckFolderId"][value="${folderId}"]`);
    if (!folderRadio || folderRadio.checked) return;
    folderRadio.checked = true;
    folderRadio.dispatchEvent(new Event('change', { bubbles: true }));
}

function prefillAiNewDeckNameForPdf(pdfRow) {
    const deckNameInput = document.querySelector('.sh-ai-flashcard-form input[name="newDeckName"]');
    const filename = pdfRow?.dataset?.deckName?.trim();
    if (!deckNameInput || !filename) return;
    deckNameInput.value = filename.replace(/\.pdf$/i, '');
}

function updateAiPdfSizeWarning() {
    const warning = document.querySelector('.sh-ai-pdf-size-warning');
    if (!warning) return;

    const selectedRows = Array.from(document.querySelectorAll('.sh-ai-pdf-row input[name="fileId"]:checked'))
        .map(input => input.closest('.sh-ai-pdf-row'))
        .filter(Boolean);
    const showWarning = selectedRows.some(row => Number(row?.dataset?.fileSize || 0) >= AI_PDF_SLOW_WARNING_BYTES);
    warning.hidden = !showWarning;
}

function syncAiFolderSelectionWithSelectedPdf() {
    const selectedRows = Array.from(document.querySelectorAll('.sh-ai-pdf-row input[name="fileId"]:checked'))
        .map(input => input.closest('.sh-ai-pdf-row'))
        .filter(Boolean);
    document.querySelectorAll('.sh-ai-pdf-row').forEach(row => {
        const checkbox = row.querySelector('input[name="fileId"]');
        row.classList.toggle('is-selected', !!checkbox?.checked);
    });

    if (selectedRows.length === 1) {
        prefillAiNewDeckNameForPdf(selectedRows[0]);
        preselectAiNewDeckFolderForPdf(selectedRows[0]);
    } else if (selectedRows.length > 1) {
        const deckNameInput = document.querySelector('.sh-ai-flashcard-form input[name="newDeckName"]');
        if (deckNameInput && !deckNameInput.value.trim()) deckNameInput.value = 'Generated Flashcards';
    }
    updateAiPdfSizeWarning();
}

/* ---------- Premium Canvas Confetti Engine (Self-contained, Offline-ready) ---------- */
function triggerCelebration(score) {
    // Option A Cutoff: Do not spawn any confetti for a flat 0% score
    if (score <= 0) return;

    // Create a full-screen pointer-events-free canvas
    const canvas = document.createElement('canvas');
    canvas.style.position = 'fixed';
    canvas.style.top = '0';
    canvas.style.left = '0';
    canvas.style.width = '100vw';
    canvas.style.height = '100vh';
    canvas.style.pointerEvents = 'none';
    canvas.style.zIndex = '99999';
    document.body.appendChild(canvas);

    const ctx = canvas.getContext('2d');
    let width = canvas.width = window.innerWidth;
    let height = canvas.height = window.innerHeight;

    const handleResize = () => {
        width = canvas.width = window.innerWidth;
        height = canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', handleResize);

    const colors = [
        '#06b6d4', '#0891b2', '#0e766e', // Teals
        '#f59e0b', '#d97706', // Yellow/Ambers
        '#ec4899', '#db2777', // Pinks
        '#8b5cf6', '#7c3aed', // Violets
        '#10b981', '#059669'  // Greens
    ];

    const shapes = ['square', 'circle', 'triangle', 'wavy'];
    const particles = [];

    class ConfettiParticle {
        constructor(x, y, vx, vy, color, shape) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.shape = shape;
            this.size = Math.random() * 8 + 6;
            this.rotation = Math.random() * 360;
            this.rotationSpeed = Math.random() * 4 - 2;
            this.opacity = 1;
            this.gravity = 0.2;
            this.drag = 0.985;
            this.wobble = Math.random() * 10;
            this.wobbleSpeed = Math.random() * 0.1 + 0.05;
        }

        update() {
            this.vy += this.gravity;
            this.vx *= this.drag;
            this.vy *= this.drag;
            this.x += this.vx + Math.sin(this.wobble) * 0.5;
            this.y += this.vy;
            this.rotation += this.rotationSpeed;
            this.wobble += this.wobbleSpeed;
            
            if (this.y > height - 100) {
                this.opacity -= 0.015;
            }
        }

        draw() {
            ctx.save();
            ctx.translate(this.x, this.y);
            ctx.rotate(this.rotation * Math.PI / 180);
            ctx.globalAlpha = this.opacity;
            ctx.fillStyle = this.color;
            ctx.strokeStyle = this.color;
            ctx.lineWidth = 2;

            if (this.shape === 'square') {
                ctx.fillRect(-this.size / 2, -this.size / 2, this.size, this.size);
            } else if (this.shape === 'circle') {
                ctx.beginPath();
                ctx.arc(0, 0, this.size / 2, 0, Math.PI * 2);
                ctx.fill();
            } else if (this.shape === 'triangle') {
                ctx.beginPath();
                ctx.moveTo(0, -this.size / 2);
                ctx.lineTo(this.size / 2, this.size / 2);
                ctx.lineTo(-this.size / 2, this.size / 2);
                ctx.closePath();
                ctx.fill();
            } else if (this.shape === 'wavy') {
                ctx.beginPath();
                ctx.moveTo(-this.size / 2, 0);
                ctx.bezierCurveTo(-this.size / 4, -this.size / 2, this.size / 4, this.size / 2, this.size / 2, 0);
                ctx.stroke();
            }

            ctx.restore();
        }
    }

    function spawnCannons() {
        for (let i = 0; i < 45; i++) {
            particles.push(new ConfettiParticle(
                0, height,
                Math.random() * 12 + 8, -(Math.random() * 16 + 10),
                colors[Math.floor(Math.random() * colors.length)],
                shapes[Math.floor(Math.random() * shapes.length)]
            ));
        }
        for (let i = 0; i < 45; i++) {
            particles.push(new ConfettiParticle(
                width, height,
                -(Math.random() * 12 + 8), -(Math.random() * 16 + 10),
                colors[Math.floor(Math.random() * colors.length)],
                shapes[Math.floor(Math.random() * shapes.length)]
            ));
        }
    }

    function spawnCascade() {
        for (let i = 0; i < 80; i++) {
            particles.push(new ConfettiParticle(
                Math.random() * width, -20,
                Math.random() * 4 - 2, Math.random() * 5 + 2,
                colors[Math.floor(Math.random() * colors.length)],
                shapes[Math.floor(Math.random() * shapes.length)]
            ));
        }
    }

    function spawnFloatingRise() {
        for (let i = 0; i < 35; i++) {
            const p = new ConfettiParticle(
                Math.random() * width, height + 20,
                Math.random() * 2 - 1, -(Math.random() * 3 + 2),
                colors[Math.floor(Math.random() * colors.length)],
                shapes[Math.floor(Math.random() * shapes.length)]
            );
            p.gravity = -0.04;
            particles.push(p);
        }
    }

    if (score >= 80) {
        spawnCannons();
        setTimeout(spawnCannons, 350);
        setTimeout(spawnCannons, 700);
    } else if (score >= 60) {
        spawnCascade();
        setTimeout(spawnCascade, 400);
    } else {
        spawnFloatingRise();
    }

    function animate() {
        ctx.clearRect(0, 0, width, height);

        for (let i = particles.length - 1; i >= 0; i--) {
            const p = particles[i];
            p.update();
            p.draw();

            if (p.opacity <= 0 || p.x < -50 || p.x > width + 50 || p.y > height + 50) {
                particles.splice(i, 1);
            }
        }

        if (particles.length > 0) {
            requestAnimationFrame(animate);
        } else {
            window.removeEventListener('resize', handleResize);
            canvas.remove();
        }
    }

    animate();
}
