/* ============================================================
   StudyHelper — App JavaScript
   Client-side sorting, search, expand/collapse, HTMX wiring
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
    initExplorer();
    initSorting();
    initSearch();
    initDeleteWarnings();
    initStudySession();
    initCsrf();
});

// CSRF configuration for HTMX
function initCsrf() {
    document.body.addEventListener('htmx:configRequest', (evt) => {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (csrfToken && csrfHeader) {
            evt.detail.headers[csrfHeader] = csrfToken;
        }
    });
}

// Re-initialize after HTMX swaps
document.body.addEventListener('htmx:afterSwap', (e) => {
    initExplorer();
    initSorting();
    initSearch();
    initDeleteWarnings();
    initStudySession();
});

document.body.addEventListener('htmx:afterSettle', (e) => {
    // Add fade-in animation to swapped content
    const target = e.detail.target;
    if (target) {
        target.classList.add('sh-fade-in');
        target.addEventListener('animationend', () => {
            target.classList.remove('sh-fade-in');
        }, { once: true });
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
            const children = item.querySelector('.sh-tree-children');

            if (children) {
                children.classList.toggle('show');
                toggle.classList.toggle('expanded');
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

            // Toggle sort direction
            const currentDir = th.dataset.sortDir || 'asc';
            const newDir = currentDir === 'asc' ? 'desc' : 'asc';

            // Clear all sort indicators
            th.parentNode.querySelectorAll('th').forEach(h => {
                h.classList.remove('sorted');
                h.dataset.sortDir = '';
            });

            th.classList.add('sorted');
            th.dataset.sortDir = newDir;

            // Update sort indicator arrow
            const indicator = th.querySelector('.sort-indicator');
            if (indicator) {
                indicator.textContent = newDir === 'asc' ? '▲' : '▼';
            }

            rows.sort((a, b) => {
                let aVal = getCellSortValue(a.cells[colIndex], sortKey);
                let bVal = getCellSortValue(b.cells[colIndex], sortKey);

                if (typeof aVal === 'string') {
                    aVal = aVal.toLowerCase();
                    bVal = bVal.toLowerCase();
                }

                let result;
                if (aVal < bVal) result = -1;
                else if (aVal > bVal) result = 1;
                else result = 0;

                return newDir === 'asc' ? result : -result;
            });

            rows.forEach(row => tbody.appendChild(row));
        });
    });
}

function getCellSortValue(cell, sortKey) {
    if (!cell) return '';

    switch (sortKey) {
        case 'name':
            return cell.textContent.trim();
        case 'type':
            return cell.textContent.trim();
        case 'size':
            return parseFloat(cell.dataset.sizeBytes || '0');
        case 'date':
            return cell.dataset.timestamp || cell.textContent.trim();
        default:
            return cell.textContent.trim();
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
                if (query === '' || name.includes(query)) {
                    item.style.display = '';
                } else {
                    item.style.display = 'none';
                }
            });
        });
    });
}

/* ---------- Delete Warning Dialogs ---------- */
function initDeleteWarnings() {
    document.querySelectorAll('[data-confirm]').forEach(el => {
        if (el.dataset.confirmInitialized) return;
        el.dataset.confirmInitialized = 'true';

        el.addEventListener('click', (e) => {
            const message = el.dataset.confirm;
            if (!confirm(message)) {
                e.preventDefault();
                e.stopPropagation();
            }
        });
    });
}

/* ---------- Study Session Setup + Card Behavior ---------- */
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
            return Array.from(orderList.querySelectorAll('li[data-deck-id]'))
                .map(li => li.dataset.deckId);
        }

        function checkedDeckIds() {
            return checkboxes.filter(input => input.checked).map(input => input.value);
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
            selected.forEach(id => {
                if (!next.includes(id)) {
                    next.push(id);
                }
            });

            renderOrderList(next);
        }

        function moveItem(deckId, direction) {
            const order = readCurrentOrder();
            const index = order.indexOf(deckId);
            const targetIndex = index + direction;

            if (index < 0 || targetIndex < 0 || targetIndex >= order.length) {
                return;
            }

            const [item] = order.splice(index, 1);
            order.splice(targetIndex, 0, item);
            renderOrderList(order);
        }

        function syncModeUi() {
            const selectedMode = modeInputs.find(input => input.checked)?.value;
            const showDeckOptions = selectedMode === 'DECK_BY_DECK';
            deckByDeckSections.forEach(section => {
                section.style.display = showDeckOptions ? '' : 'none';
            });
        }

        checkboxes.forEach(input => input.addEventListener('change', syncOrderWithSelection));
        modeInputs.forEach(input => input.addEventListener('change', syncModeUi));

        syncOrderWithSelection();
        syncModeUi();
    });
}

function initStudyCard() {
    document.querySelectorAll('.sh-study-reveal-btn').forEach(btn => {
        if (btn.dataset.initialized) return;
        btn.dataset.initialized = 'true';

        btn.addEventListener('click', () => {
            const root = btn.closest('.sh-study-card-root');
            const flipCard = root?.querySelector('.sh-study-flip-card');
            const answerControls = root?.querySelector('.sh-study-answer-controls');

            if (flipCard && answerControls) {
                flipCard.classList.add('flipped');
                btn.classList.add('d-none');
                answerControls.classList.remove('d-none');
            } else {
                console.warn('Could not find flipCard or answerControls for reveal button', { flipCard, answerControls });
            }
        });
    });
}

/* ---------- Color Picker Hex Sync ---------- */
function syncColorHex(input, target) {
    const targetEl = document.getElementById(target);
    if (targetEl) {
        targetEl.value = input.value;
    }
}
