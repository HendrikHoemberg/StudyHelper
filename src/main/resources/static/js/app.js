/* ============================================================
   StudyHelper — App JavaScript
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initLucide();
    initTopnav();
    initModals();
    initExplorer();
    initSorting();
    initSearch();
    initDeleteWarnings();
    initStudySession();
    initCsrf();
});

document.body.addEventListener('htmx:afterSwap', () => {
    initLucide();
    initModals();
    initExplorer();
    initSorting();
    initSearch();
    initDeleteWarnings();
    initStudySession();
});

document.body.addEventListener('htmx:afterSettle', (e) => {
    const target = e.detail.target;
    if (target) {
        target.classList.add('sh-fade-in');
        target.addEventListener('animationend', () => {
            target.classList.remove('sh-fade-in');
        }, { once: true });
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

/* ---------- Custom Modal System ---------- */
function initModals() {
    document.querySelectorAll('[data-modal-open]').forEach(btn => {
        if (btn.dataset.modalInitialized) return;
        btn.dataset.modalInitialized = 'true';
        btn.addEventListener('click', () => openModal(btn.dataset.modalOpen));
    });

    document.querySelectorAll('[data-modal-close]').forEach(btn => {
        if (btn.dataset.modalInitialized) return;
        btn.dataset.modalInitialized = 'true';
        btn.addEventListener('click', () => {
            const modal = btn.closest('.sh-modal');
            if (modal) closeModal(modal.id);
        });
    });

    document.querySelectorAll('.sh-modal-backdrop').forEach(backdrop => {
        if (backdrop.dataset.modalInitialized) return;
        backdrop.dataset.modalInitialized = 'true';
        backdrop.addEventListener('click', () => {
            const modal = backdrop.closest('.sh-modal');
            if (modal) closeModal(modal.id);
        });
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            document.querySelectorAll('.sh-modal.is-open').forEach(m => closeModal(m.id));
        }
    }, { once: false });
}

function openModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.add('is-open');
        document.body.style.overflow = 'hidden';
        modal.querySelector('input, textarea')?.focus();
    }
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.remove('is-open');
        if (!document.querySelector('.sh-modal.is-open')) {
            document.body.style.overflow = '';
        }
    }
}

/* ---------- Alert Dismiss ---------- */
document.body.addEventListener('click', (e) => {
    const closeBtn = e.target.closest('[data-dismiss-alert]');
    if (closeBtn) {
        closeBtn.closest('.sh-alert')?.remove();
    }
});

/* ---------- Explorer Tree Expand/Collapse ---------- */
function initExplorer() {
    document.querySelectorAll('.sh-tree-toggle-btn').forEach(btn => {
        if (btn.dataset.initialized) return;
        btn.dataset.initialized = 'true';

        btn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();

            const item = btn.closest('.sh-tree-item');
            const toggle = btn.querySelector('.sh-tree-toggle');
            const children = item?.querySelector('.sh-tree-children');

            if (children) {
                const isOpen = children.classList.toggle('show');
                toggle?.classList.toggle('expanded', isOpen);
            }
        });
    });
}

/* ---------- Client-Side File Sorting ---------- */
function initSorting() {
    document.querySelectorAll('.sh-file-table thead th[data-sort]').forEach(th => {
        if (th.dataset.sortInitialized) return;
        th.dataset.sortInitialized = 'true';

        th.addEventListener('click', () => {
            const table = th.closest('table');
            const tbody = table.querySelector('tbody');
            const rows = Array.from(tbody.querySelectorAll('tr'));
            const sortKey = th.dataset.sort;
            const colIndex = Array.from(th.parentNode.children).indexOf(th);

            const currentDir = th.dataset.sortDir || 'asc';
            const newDir = currentDir === 'asc' ? 'desc' : 'asc';

            th.parentNode.querySelectorAll('th').forEach(h => {
                h.classList.remove('sorted');
                h.dataset.sortDir = '';
            });

            th.classList.add('sorted');
            th.dataset.sortDir = newDir;

            const indicator = th.querySelector('.sort-indicator');
            if (indicator) indicator.textContent = newDir === 'asc' ? '▲' : '▼';

            rows.sort((a, b) => {
                let aVal = getCellSortValue(a.cells[colIndex], sortKey);
                let bVal = getCellSortValue(b.cells[colIndex], sortKey);

                if (typeof aVal === 'string') {
                    aVal = aVal.toLowerCase();
                    bVal = bVal.toLowerCase();
                }

                let result = aVal < bVal ? -1 : aVal > bVal ? 1 : 0;
                return newDir === 'asc' ? result : -result;
            });

            rows.forEach(row => tbody.appendChild(row));
        });
    });
}

function getCellSortValue(cell, sortKey) {
    if (!cell) return '';
    switch (sortKey) {
        case 'size': return parseFloat(cell.dataset.sizeBytes || '0');
        case 'date': return cell.dataset.timestamp || cell.textContent.trim();
        default: return cell.textContent.trim();
    }
}

/* ---------- Live Search / Filter ---------- */
function initSearch() {
    document.querySelectorAll('.sh-search-input').forEach(input => {
        if (input.dataset.initialized) return;
        input.dataset.initialized = 'true';

        input.addEventListener('input', () => {
            const query = input.value.toLowerCase().trim();
            const targetSelector = input.dataset.filterTarget;
            const items = document.querySelectorAll(targetSelector);

            items.forEach(item => {
                const name = (item.dataset.name || item.textContent).toLowerCase();
                item.style.display = (query === '' || name.includes(query)) ? '' : 'none';
            });
        });
    });
}

/* ---------- Delete Confirmations ---------- */
function initDeleteWarnings() {
    document.querySelectorAll('[data-confirm]').forEach(el => {
        if (el.dataset.confirmInitialized) return;
        el.dataset.confirmInitialized = 'true';

        el.addEventListener('click', (e) => {
            if (!confirm(el.dataset.confirm)) {
                e.preventDefault();
                e.stopPropagation();
            }
        });
    });
}

/* ---------- Study Session ---------- */
function initStudySession() {
    initStudySetup();
    initStudyCard();
}

function initStudySetup() {
    document.querySelectorAll('.sh-study-setup-card').forEach(form => {
        if (form.dataset.initialized) return;
        form.dataset.initialized = 'true';

        const checkboxes = Array.from(form.querySelectorAll('.sh-study-deck-checkbox'));
        const orderList = form.querySelector('#sh-study-order-list');
        const orderedField = form.querySelector('#study-ordered-deck-ids');
        const modeInputs = Array.from(form.querySelectorAll('input[name="sessionMode"]'));
        const deckByDeckSections = Array.from(form.querySelectorAll('.sh-study-deck-options'));

        const selectedMap = new Map();
        checkboxes.forEach(input => {
            selectedMap.set(input.value, {
                id: input.value,
                name: input.closest('label')?.querySelector('.sh-deck-name')?.textContent?.trim() || `Deck ${input.value}`
            });
        });

        function readCurrentOrder() {
            return Array.from(orderList.querySelectorAll('li[data-deck-id]')).map(li => li.dataset.deckId);
        }

        function checkedDeckIds() {
            return checkboxes.filter(cb => cb.checked).map(cb => cb.value);
        }

        function renderOrderList(deckIds) {
            orderList.innerHTML = '';

            if (deckIds.length === 0) {
                const empty = document.createElement('li');
                empty.className = 'sh-study-order-empty';
                empty.textContent = 'Select decks to define manual order.';
                orderList.appendChild(empty);
                orderedField.value = '';
                return;
            }

            deckIds.forEach((deckId, index) => {
                const meta = selectedMap.get(deckId);
                if (!meta) return;

                const li = document.createElement('li');
                li.className = 'sh-study-order-item';
                li.dataset.deckId = deckId;

                const name = document.createElement('span');
                name.className = 'sh-study-order-name';
                name.textContent = `${index + 1}. ${meta.name}`;

                const controls = document.createElement('div');
                controls.className = 'sh-study-order-controls';

                const upBtn = document.createElement('button');
                upBtn.type = 'button';
                upBtn.className = 'sh-btn sh-btn-ghost sh-btn-sm';
                upBtn.textContent = 'Up';
                upBtn.addEventListener('click', () => moveItem(deckId, -1));

                const downBtn = document.createElement('button');
                downBtn.type = 'button';
                downBtn.className = 'sh-btn sh-btn-ghost sh-btn-sm';
                downBtn.textContent = 'Down';
                downBtn.addEventListener('click', () => moveItem(deckId, 1));

                controls.appendChild(upBtn);
                controls.appendChild(downBtn);
                li.appendChild(name);
                li.appendChild(controls);
                orderList.appendChild(li);
            });

            orderedField.value = readCurrentOrder().join(',');
        }

        function syncOrderWithSelection() {
            const selected = checkedDeckIds();
            const existing = readCurrentOrder();
            const next = existing.filter(id => selected.includes(id));
            selected.forEach(id => { if (!next.includes(id)) next.push(id); });
            renderOrderList(next);
        }

        function moveItem(deckId, direction) {
            const order = readCurrentOrder();
            const index = order.indexOf(deckId);
            const targetIndex = index + direction;
            if (index < 0 || targetIndex < 0 || targetIndex >= order.length) return;
            const [item] = order.splice(index, 1);
            order.splice(targetIndex, 0, item);
            renderOrderList(order);
        }

        function syncModeUi() {
            const selectedMode = modeInputs.find(i => i.checked)?.value;
            const showDeckOptions = selectedMode === 'DECK_BY_DECK';
            deckByDeckSections.forEach(section => {
                section.style.display = showDeckOptions ? '' : 'none';
            });
        }

        checkboxes.forEach(cb => cb.addEventListener('change', syncOrderWithSelection));
        modeInputs.forEach(i => i.addEventListener('change', syncModeUi));

        syncOrderWithSelection();
        syncModeUi();
    });
}

function initStudyCard() {
    document.querySelectorAll('.sh-study-reveal-btn').forEach(btn => {
        if (btn.dataset.initialized) return;
        btn.dataset.initialized = 'true';

        btn.addEventListener('click', () => {
            const card = btn.closest('.sh-flip-card') || document.querySelector('.sh-flip-card');
            card?.classList.add('flipped');
        });
    });
}

/* ---------- Color Picker Hex Sync ---------- */
function syncColorHex(input, target) {
    const targetEl = document.getElementById(target);
    if (targetEl) targetEl.value = input.value;
}
