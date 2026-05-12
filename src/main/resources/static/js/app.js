/* ============================================================
   StudyHelper — App JavaScript (HTMX Simplified)
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initLucide();
    initTopnav();
    initSidebarDrawer();
    initSidebarFolderExpand();
    initCsrf();
    initLightbox();
    initShDialog();
});

// Re-run initializations after HTMX swaps
document.body.addEventListener('htmx:afterSwap', () => {
    // No-op for now as iconify-icon is a web component and handles itself
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

    document.body.addEventListener('click', (e) => {
        const trigger = e.target.closest('.sh-lightbox-trigger');
        if (trigger) {
            e.preventDefault();
            e.stopPropagation();
            
            // Try to get image source from src, data-src, or href
            const src = trigger.src || trigger.dataset.src || trigger.href;
            if (src) {
                lbImg.src = src;
                lb.style.display = 'flex';
            }
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') lb.style.display = 'none';
    });
}

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
    if (!toggle || !menu) return;

    toggle.addEventListener('click', () => menu.classList.toggle('open'));

    menu.querySelectorAll('a').forEach(link => {
        link.addEventListener('click', () => menu.classList.remove('open'));
    });
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
        grid.innerHTML = '<div class="sh-sidebar-empty">Type at least 2 chars to search...</div>';
        return;
    }

    grid.innerHTML = '<div class="sh-sidebar-empty">Searching icons...</div>';
    
    const squarePrefixes = 'lucide,heroicons,ph,mdi,tabler,octicon,bi,ri,fluent,carbon,ic';

    iconifySearchTimeout = setTimeout(() => {
        fetch(`https://api.iconify.design/search?query=${encodeURIComponent(query)}&limit=40&prefixes=${squarePrefixes}`)
            .then(res => res.json())
            .then(data => {
                renderIconifyResults(modal, data.icons || []);
            })
            .catch(err => {
                grid.innerHTML = '<div class="sh-sidebar-empty">Error loading icons.</div>';
            });
    }, 500);
}

function renderIconifyResults(modal, icons) {
    const grid = modal.querySelector('.sh-icon-grid');
    const selectedIcon = modal.querySelector('input[name="iconName"]')?.value;
    
    if (icons.length === 0) {
        grid.innerHTML = '<div class="sh-sidebar-empty">No icons found.</div>';
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
        grid.innerHTML = '<div class="sh-sidebar-empty">Start typing to find icons...</div>';
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
    
    const color = modal.querySelector('input[name="colorHex"]')?.value || '#6366f1';
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
        } else if (e.target.id === 'sh-dialog-ok') {
            const input = document.getElementById('sh-dialog-input');
            const isPrompt = input && input.style.display !== 'none';
            shDialogResolve(isPrompt ? input.value : true);
        }
    });

    document.addEventListener('keydown', (e) => {
        if (!dlg.classList.contains('is-open')) return;
        if (e.key === 'Escape') {
            e.preventDefault();
            shDialogResolve(null);
        } else if (e.key === 'Enter') {
            const input = document.getElementById('sh-dialog-input');
            const isPrompt = input && input.style.display !== 'none';
            // For prompt, only submit on Enter when input is focused.
            if (isPrompt && document.activeElement !== input) return;
            e.preventDefault();
            shDialogResolve(isPrompt ? input.value : true);
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

function _shOpenDialog({ title, message, icon, iconKind, confirmText, cancelText, danger, prompt, defaultValue, placeholder, hideCancel }) {
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

        titleEl.textContent = title || (prompt ? 'Enter a value' : (hideCancel ? 'Notice' : 'Confirm'));
        msgEl.textContent = message || '';
        msgEl.style.display = message ? '' : 'none';

        const iconName = icon || (iconKind === 'info' ? 'lucide:info'
            : iconKind === 'edit' ? 'lucide:pencil'
            : danger ? 'lucide:alert-triangle'
            : 'lucide:help-circle');
        iconEl.innerHTML = `<iconify-icon icon="${iconName}"></iconify-icon>`;

        okBtn.textContent = confirmText || (prompt ? 'Save' : 'OK');
        cancelBtn.textContent = cancelText || 'Cancel';
        cancelBtn.style.display = hideCancel ? 'none' : '';

        if (prompt) {
            input.style.display = '';
            input.value = defaultValue || '';
            input.placeholder = placeholder || '';
        } else {
            input.style.display = 'none';
            input.value = '';
        }

        dlg.classList.toggle('is-danger', !!danger);
        dlg.classList.add('is-open');
        dlg.setAttribute('aria-hidden', 'false');

        shDialogState = { resolve };

        // Focus management
        setTimeout(() => {
            if (prompt) input.focus();
            else okBtn.focus();
        }, 0);
    });
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

/* ---------- AI Flashcard Generator ---------- */
document.addEventListener('input', (event) => {
    if (!event.target.matches('#ai-pdf-search')) return;
    const q = event.target.value.toLowerCase();
    document.querySelectorAll('.sh-ai-pdf-row').forEach(row => {
        const text = (row.dataset.name || row.innerText).toLowerCase();
        row.style.display = text.includes(q) ? '' : 'none';
    });
});

document.addEventListener('change', (event) => {
    if (event.target.matches('.sh-ai-pdf-row input[name="fileId"]')) {
        document.querySelectorAll('.sh-ai-pdf-row').forEach(row => row.classList.remove('is-selected'));
        const row = event.target.closest('.sh-ai-pdf-row');
        row?.classList.add('is-selected');
        preselectAiNewDeckFolderForPdf(row);
    }
    if (event.target.matches('input[name="destination"]')) {
        updateAiFlashcardDestinationPanels();
    }
    if (event.target.matches('input[name="existingDeckId"]') || event.target.matches('input[name="newDeckFolderId"]')) {
        const card = event.target.closest('.sh-ai-destination-card');
        const head = card?.querySelector('.sh-ai-destination-head input[name="destination"]');
        if (head && !head.checked) {
            head.checked = true;
            updateAiFlashcardDestinationPanels();
        }
    }
});

document.addEventListener('click', (event) => {
    const btn = event.target.closest('.sh-ai-pdf-mode .vb-pdf-mode-btn');
    if (!btn) return;
    event.preventDefault();
    const group = btn.closest('.sh-ai-pdf-mode');
    const hidden = group?.querySelector('input[name="documentMode"]');
    if (hidden) hidden.value = btn.dataset.mode;
    group?.querySelectorAll('.vb-pdf-mode-btn').forEach(option => {
        const selected = option === btn;
        option.classList.toggle('is-active', selected);
        option.setAttribute('aria-pressed', selected ? 'true' : 'false');
    });
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
    const modal = document.getElementById('ai-generating-modal');
    if (modal) modal.style.display = 'none';
});

document.body.addEventListener('htmx:responseError', (event) => {
    const source = event.detail.elt;
    if (!source?.matches?.('form.sh-ai-flashcard-form, form.sh-study-setup-card')) return;

    const modal = document.getElementById('ai-generating-modal');
    if (modal) modal.style.display = 'none';

    const text = extractAiGenerationError(event.detail.xhr?.responseText);
    shAlert({
        title: 'AI generation failed',
        message: text || 'Please try again. If this keeps happening, reduce the selected sources or switch PDF mode.',
        confirmText: 'Try again',
        danger: true
    });
});

function extractAiGenerationError(responseText) {
    if (!responseText) return '';
    const doc = new DOMParser().parseFromString(responseText, 'text/html');
    const error = doc.querySelector('[data-ai-generation-error="true"] .sh-alert-body, .sh-alert-danger .sh-alert-body');
    if (error?.textContent?.trim()) return error.textContent.trim();
    return doc.body?.textContent?.replace(/^Error:\s*/i, '').trim() || '';
}

function updateAiFlashcardDestinationPanels() {
    const selected = document.querySelector('input[name="destination"]:checked')?.value || 'NEW_DECK';
    document.querySelectorAll('.sh-ai-destination-card').forEach(card => {
        const radio = card.querySelector('.sh-ai-destination-head input[name="destination"]');
        card.classList.toggle('is-selected', radio?.value === selected);
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

function syncAiFolderSelectionWithSelectedPdf() {
    const selectedPdf = document.querySelector('.sh-ai-pdf-row input[name="fileId"]:checked')?.closest('.sh-ai-pdf-row');
    if (!selectedPdf) return;
    preselectAiNewDeckFolderForPdf(selectedPdf);
}
