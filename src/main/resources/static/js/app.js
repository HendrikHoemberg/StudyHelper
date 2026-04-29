/* ============================================================
   StudyHelper — App JavaScript (HTMX Simplified)
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initLucide();
    initTopnav();
    initCsrf();
    syncSidebarActive();
});

// Re-run initializations after HTMX swaps
document.body.addEventListener('htmx:afterSwap', () => {
    initLucide();
    syncSidebarActive();
});

// Optional fade-in animation after settle
document.body.addEventListener('htmx:afterSettle', (e) => {
    const target = e.detail.target;
    if (target && target.classList.contains('sh-fade-in')) {
        target.classList.add('animate-fade-in'); // Example CSS class
    }
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

/* ---------- Lucide Icons ---------- */
function initLucide() {
    if (window.lucide) {
        lucide.createIcons();
    }
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

/* ---------- Sidebar (Variant D) active sync ---------- */
function syncSidebarActive() {
    const folders = document.querySelectorAll('.sh-d-folder');
    if (!folders.length) return;

    const match = window.location.pathname.match(/\/folders\/(\d+)/);
    const activeId = match ? match[1] : null;

    document.querySelectorAll('.sh-d-folder.is-active, .sh-d-row.is-active')
        .forEach(el => el.classList.remove('is-active'));

    if (!activeId) return;

    folders.forEach(folder => {
        if (folder.dataset.folderId === activeId) {
            folder.classList.add('is-active');
            return;
        }
        const row = folder.querySelector(`.sh-d-row[data-folder-id="${activeId}"]`);
        if (row) {
            row.classList.add('is-active');
            folder.classList.add('is-open');
        }
    });
}

/* ---------- Color Picker Hex Sync ---------- */
function syncColorHex(input, target) {
    const targetEl = document.getElementById(target);
    if (targetEl) targetEl.value = input.value;
}

/* ---------- Folder Modals (Create/Edit) ---------- */

const FOLDER_ICONS = window.lucide ? Object.keys(lucide.icons).sort() : ['folder'];

/**
 * Opens a modal for creating a new folder
 * @param {string} modalId 
 * @param {string} parentId (Optional)
 */
function openCreateFolderModal(modalId, parentId = null) {
    const modal = document.getElementById(modalId);
    if (!modal) return;

    // Reset fields
    const nameInput = modal.querySelector('input[name="name"]');
    const colorInput = modal.querySelector('input[name="colorHex"]');
    const iconInput = modal.querySelector('input[name="iconName"]');
    
    if (nameInput) nameInput.value = '';
    if (colorInput) colorInput.value = '#6366f1';
    if (iconInput) iconInput.value = 'folder';

    updateFolderPreview(modal);
    renderIconGrid(modal, 'folder');

    // Clear search
    const search = modal.querySelector('.sh-icon-search');
    if (search) {
        search.value = '';
        filterFolderIcons(search);
    }

    modal.classList.add('is-open');
    document.body.style.overflow = 'hidden';
    if (nameInput) nameInput.focus();
}

/**
 * Opens a modal for editing an existing folder
 * @param {HTMLElement} btn 
 * @param {string} modalId 
 */
function openEditFolderModal(btn, modalId = 'editFolderModal') {
    const modal = document.getElementById(modalId);
    if (!modal) return;

    const name  = btn.dataset.folderName;
    const color = btn.dataset.folderColor;
    const icon  = btn.dataset.folderIcon || 'folder';

    const nameInput = modal.querySelector('input[name="name"]');
    const colorInput = modal.querySelector('input[name="colorHex"]');
    const iconInput = modal.querySelector('input[name="iconName"]');

    if (nameInput) nameInput.value = name;
    if (colorInput) colorInput.value = color;
    if (iconInput) iconInput.value = icon;

    updateFolderPreview(modal);
    renderIconGrid(modal, icon);

    // clear any previous search
    const search = modal.querySelector('.sh-icon-search');
    if (search) {
        search.value = '';
        filterFolderIcons(search);
    }

    modal.classList.add('is-open');
    document.body.style.overflow = 'hidden';
    if (nameInput) nameInput.focus();
}

function renderIconGrid(modal, selectedIcon) {
    const grid = modal.querySelector('.sh-icon-grid');
    if (!grid) return;

    if (!grid.dataset.rendered) {
        grid.innerHTML = FOLDER_ICONS.map(name =>
            `<button type="button" class="sh-icon-btn" data-icon="${name}" title="${name}" ` +
            `onclick="selectFolderIcon(this,'${name}')"><i data-lucide="${name}"></i></button>`
        ).join('');
        grid.dataset.rendered = '1';
        if (window.lucide) lucide.createIcons({
            attrs: { style: 'width:15px; height:15px;' },
            nameAttr: 'data-lucide',
            root: grid
        });
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
        const matches = q.length === 0 || btn.dataset.icon.includes(q);
        btn.style.display = matches ? 'flex' : 'none';
    });
}

function updateFolderPreview(modal) {
    const preview = modal.querySelector('.sh-edit-preview');
    if (!preview) return;
    
    const color = modal.querySelector('input[name="colorHex"]')?.value || '#6366f1';
    const iconName = modal.querySelector('input[name="iconName"]')?.value || 'folder';
    
    preview.style.color = color;
    preview.innerHTML = `<i data-lucide="${iconName}" style="width:22px;height:22px;"></i>`;
    if (window.lucide) lucide.createIcons({
        attrs: { style: 'width:22px; height:22px;' },
        nameAttr: 'data-lucide',
        root: preview
    });
}
