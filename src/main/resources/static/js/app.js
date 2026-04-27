/* ============================================================
   StudyHelper — App JavaScript
   Client-side sorting, search, expand/collapse, HTMX wiring
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
    initExplorer();
    initSorting();
    initSearch();
    initDeleteWarnings();
});

// Re-initialize after HTMX swaps
document.body.addEventListener('htmx:afterSwap', (e) => {
    initExplorer();
    initSorting();
    initSearch();
    initDeleteWarnings();
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

/* ---------- Color Picker Hex Sync ---------- */
function syncColorHex(input, target) {
    const targetEl = document.getElementById(target);
    if (targetEl) {
        targetEl.value = input.value;
    }
}
