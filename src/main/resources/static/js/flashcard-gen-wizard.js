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

        // Expand any folders containing selected inputs on initial render/load
        form.querySelectorAll('.vb-group, .vb-subgroup').forEach(folder => {
            const hasCheckedInput = !!folder.querySelector(':scope > .vb-folder-content input:checked');
            if (hasCheckedInput) {
                setFolderExpanded(folder, true);
            }
        });
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
