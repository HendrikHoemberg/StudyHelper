/* ============================================================
   StudyHelper — App JavaScript (HTMX Simplified)
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initLucide();
    initTopnav();
    initCsrf();
});

// Re-run initializations after HTMX swaps
document.body.addEventListener('htmx:afterSwap', () => {
    initLucide();
});

// Optional fade-in animation after settle
document.body.addEventListener('htmx:afterSettle', (e) => {
    const target = e.detail.target;
    if (target && target.classList.contains('sh-fade-in')) {
        target.classList.add('animate-fade-in');
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

/* ---------- Color Picker Hex Sync ---------- */
function syncColorHex(input, target) {
    const targetEl = document.getElementById(target);
    if (targetEl) targetEl.value = input.value;
}

/* ---------- Folder Icons (Used by Modals) ---------- */

const FOLDER_ICONS = window.lucide ? Object.keys(lucide.icons).sort() : ['folder'];

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
        const matches = q.length === 0 || btn.dataset.icon.toLowerCase().includes(q);
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
