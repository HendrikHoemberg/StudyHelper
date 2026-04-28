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

/* ---------- Color Picker Hex Sync ---------- */
function syncColorHex(input, target) {
    const targetEl = document.getElementById(target);
    if (targetEl) targetEl.value = input.value;
}
