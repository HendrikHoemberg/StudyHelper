/* ============================================================
   StudyHelper — App JavaScript (HTMX Simplified)
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initLucide();
    initTopnav();
    initCsrf();
    initLightbox();
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
