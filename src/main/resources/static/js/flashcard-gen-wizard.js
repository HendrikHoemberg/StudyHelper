(function () {
    'use strict';

    const FORM_ID = 'ai-flashcard-form';
    const PANEL_SELECTOR = '.sh-wizard-panel';
    const STEP_PILL_SELECTOR = '#sh-wizard-steps [data-step-pill]';
    const BACK_BTN_ID = 'ai-flashcard-back';
    const NEXT_BTN_ID = 'ai-flashcard-next';
    const SUBMIT_BTN_ID = 'ai-flashcard-submit';
    const SUBMIT_INSTR_BTN_ID = 'ai-flashcard-submit-with-instructions';
    const ERROR_BANNER_SELECTOR = '[data-ai-generation-error="true"]';

    let currentStep = 1;

    function $(id) { return document.getElementById(id); }

    function panels(form) {
        return form.querySelectorAll(PANEL_SELECTOR);
    }

    function setPanelActive(panel, active, direction) {
        panel.classList.remove('slide-from-right', 'slide-from-left', 'fade-in-up');
        if (active) {
            if (direction === 'forward') panel.classList.add('slide-from-right');
            else if (direction === 'backward') panel.classList.add('slide-from-left');
            else panel.classList.add('fade-in-up');
            panel.classList.add('is-active');
        } else {
            panel.classList.remove('is-active');
        }
    }

    function updateStepPills(step) {
        document.querySelectorAll(STEP_PILL_SELECTOR).forEach((pill) => {
            const pillStep = parseInt(pill.dataset.stepPill, 10);
            pill.classList.toggle('is-active', pillStep === step);
            pill.classList.toggle('is-done', pillStep < step);
            if (pillStep < step) {
                pill.style.cursor = 'pointer';
                pill.onclick = () => showStep(pillStep);
            } else {
                pill.style.cursor = '';
                pill.onclick = null;
            }
        });
    }

    function updateFooter(step) {
        const back = $(BACK_BTN_ID);
        const next = $(NEXT_BTN_ID);
        const submit = $(SUBMIT_BTN_ID);
        const submitInstr = $(SUBMIT_INSTR_BTN_ID);
        if (back) back.style.display = step === 2 ? '' : 'none';
        if (next) next.style.display = step === 1 ? '' : 'none';
        if (submit) submit.style.display = step === 2 ? '' : 'none';
        if (submitInstr) submitInstr.style.display = step === 2 ? '' : 'none';
    }

    function showStep(step) {
        const form = $(FORM_ID);
        if (!form) return;
        const direction = step > currentStep ? 'forward' : step < currentStep ? 'backward' : 'none';
        currentStep = step;
        panels(form).forEach((panel) => {
            const panelStep = parseInt(panel.dataset.step, 10);
            setPanelActive(panel, panelStep === step, direction);
        });
        updateStepPills(step);
        updateFooter(step);
        const stepsIndicator = document.getElementById('sh-wizard-steps');
        if (stepsIndicator) stepsIndicator.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

        if (step === 2) {
            setTimeout(() => {
                const checkedCheckbox = document.querySelector('.sh-ai-destination-body[data-destination-panel="NEW_DECK"] input[name="newDeckFolderId"]:checked');
                if (checkedCheckbox) {
                    let parent = checkedCheckbox.closest('.vb-group, .vb-subgroup');
                    while (parent) {
                        setFolderExpanded(parent, true);
                        parent = parent.parentElement ? parent.parentElement.closest('.vb-group, .vb-subgroup') : null;
                    }
                    const targetElement = checkedCheckbox.closest('.sh-ai-tree-folder') || checkedCheckbox;
                    targetElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }
            }, 150);
        }
    }

    function countSelectedPdfs(form) {
        return form.querySelectorAll('input[name="fileId"]:checked').length;
    }

    function updateNextEnabled() {
        const form = $(FORM_ID);
        const next = $(NEXT_BTN_ID);
        if (!form || !next) return;
        next.disabled = countSelectedPdfs(form) === 0;
    }

    function wire(form) {
        if (form.dataset.wizardWired === 'true') return;
        form.dataset.wizardWired = 'true';

        const back = $(BACK_BTN_ID);
        const next = $(NEXT_BTN_ID);
        if (back) back.addEventListener('click', () => showStep(1));
        if (next) next.addEventListener('click', () => {
            if (next.disabled) return;
            showStep(2);
        });

        form.addEventListener('change', (event) => {
            if (event.target.matches('input[name="fileId"]')) updateNextEnabled();
        });
    }

    function setFolderExpanded(folder, expanded) {
        folder.classList.toggle('is-collapsed', !expanded);
        const toggle = folder.querySelector(':scope > .vb-group-head .vb-folder-toggle, :scope > .vb-subgroup-head .vb-folder-toggle');
        if (toggle) {
            toggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
            toggle.setAttribute('title', expanded ? 'Collapse folder' : 'Expand folder');
        }
    }

    function syncFolderBatchCheckboxes(form) {
        form.querySelectorAll('.vb-group, .vb-subgroup').forEach(folder => {
            const batchCheckbox = folder.querySelector(':scope > .vb-group-head > .sh-folder-batch-checkbox, :scope > .vb-subgroup-head > .sh-folder-batch-checkbox');
            if (!batchCheckbox) return;

            const pdfs = folder.querySelectorAll('.vb-folder-content input[name="fileId"]');
            if (pdfs.length === 0) {
                batchCheckbox.disabled = true;
                batchCheckbox.checked = false;
                batchCheckbox.indeterminate = false;
                return;
            }

            const checkedPdfs = folder.querySelectorAll('.vb-folder-content input[name="fileId"]:checked');
            if (checkedPdfs.length === 0) {
                batchCheckbox.checked = false;
                batchCheckbox.indeterminate = false;
            } else if (checkedPdfs.length === pdfs.length) {
                batchCheckbox.checked = true;
                batchCheckbox.indeterminate = false;
            } else {
                batchCheckbox.checked = false;
                batchCheckbox.indeterminate = true;
            }
        });
    }

    function handleBatchCheckboxChange(batchCheckbox, folder, form) {
        const isChecked = batchCheckbox.checked;

        folder.querySelectorAll('.vb-folder-content input[name="fileId"]').forEach(cb => {
            if (cb.checked !== isChecked) {
                cb.checked = isChecked;
                cb.dispatchEvent(new Event('change', { bubbles: true }));
            }
        });

        folder.querySelectorAll('.vb-folder-content .sh-folder-batch-checkbox').forEach(cb => {
            cb.checked = isChecked;
            cb.indeterminate = false;
        });

        setFolderExpanded(folder, isChecked);
        folder.querySelectorAll('.vb-subgroup').forEach(subfolder => {
            setFolderExpanded(subfolder, isChecked);
        });

        syncFolderBatchCheckboxes(form);
        updateNextEnabled();
    }

    function initFolderTrees(form) {
        form.querySelectorAll('.vb-group, .vb-subgroup').forEach(folder => {
            const toggle = folder.querySelector(':scope > .vb-group-head .vb-folder-toggle, :scope > .vb-subgroup-head .vb-folder-toggle');
            if (toggle && toggle.dataset.initialized !== 'true') {
                toggle.dataset.initialized = 'true';
                toggle.addEventListener('click', event => {
                    event.preventDefault();
                    event.stopPropagation();
                    setFolderExpanded(folder, folder.classList.contains('is-collapsed'));
                });
            }

            const head = folder.querySelector(':scope > .vb-group-head, :scope > .vb-subgroup-head');
            if (head && head.tagName !== 'LABEL' && head.dataset.initialized !== 'true') {
                head.dataset.initialized = 'true';
                head.addEventListener('click', event => {
                    if (event.target.closest('button, a, input')) return;
                    event.preventDefault();
                    setFolderExpanded(folder, folder.classList.contains('is-collapsed'));
                });
            }
        });

        // Setup batch checkbox change listeners
        form.querySelectorAll('.sh-folder-batch-checkbox').forEach(cb => {
            if (cb.dataset.batchWired === 'true') return;
            cb.dataset.batchWired = 'true';
            cb.addEventListener('change', () => {
                const folder = cb.closest('.vb-group, .vb-subgroup');
                if (!folder) return;
                handleBatchCheckboxChange(cb, folder, form);
            });
            cb.addEventListener('click', event => {
                event.stopPropagation();
            });
        });

        // Setup PDF leaf node select change listeners to sync batch checkboxes
        form.querySelectorAll('input[name="fileId"]').forEach(cb => {
            if (cb.dataset.batchSyncWired === 'true') return;
            cb.dataset.batchSyncWired = 'true';
            cb.addEventListener('change', () => {
                syncFolderBatchCheckboxes(form);
            });
        });

        // Expand any folders containing selected inputs on initial render/load
        form.querySelectorAll('.vb-group, .vb-subgroup').forEach(folder => {
            const hasCheckedInput = !!folder.querySelector(':scope > .vb-folder-content input:checked');
            if (hasCheckedInput) {
                setFolderExpanded(folder, true);
            }
        });

        // Run sync on load to set initial indeterminate/checked states based on preselected files
        syncFolderBatchCheckboxes(form);
    }

    function init() {
        const form = $(FORM_ID);
        if (!form) return;
        wire(form);
        initFolderTrees(form);
        updateNextEnabled();
        const hasError = document.querySelector(ERROR_BANNER_SELECTOR);
        showStep(hasError ? 2 : 1);
    }

    document.addEventListener('DOMContentLoaded', init);
    document.body.addEventListener('htmx:load', init);
})();
