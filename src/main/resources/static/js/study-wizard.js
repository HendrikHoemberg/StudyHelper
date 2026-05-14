(function () {
    let currentStep = 1;
    let currentMode = 'FLASHCARDS';

    const STEP_LABELS = {
        FLASHCARDS: ["Mode", "Type", "Decks", "Order"],
        QUIZ:       ["Mode", "Format", "Settings", "Sources"],
        EXAM:       ["Mode", "Depth", "Settings", "Sources", "Layout"],
    };

    function maxSteps() {
        if (currentMode === 'FLASHCARDS') {
            const type = document.querySelector('input[name="sessionMode"]:checked')?.value;
            return type === 'SHUFFLED' ? 3 : 4;
        }
        if (currentMode === 'EXAM') {
            return 5;
        }
        return 4;
    }

    function sourceStep() {
        if (currentMode === 'QUIZ' || currentMode === 'EXAM') return 4;
        return 3;
    }

    window.selectStudyMode = function(element, mode) {
        if (element.classList.contains('is-disabled')) return;
        
        currentMode = mode;
        document.getElementById('study-mode-input').value = mode;
        
        // UI feedback: animate chosen card to center
        const container = element.closest('.sh-study-mode-grid');
        const allCards = container.querySelectorAll('.sh-study-choice');
        const rect = element.getBoundingClientRect();
        const parentRect = container.getBoundingClientRect();
        const slideX = (parentRect.left + parentRect.width/2) - (rect.left + rect.width/2);

        allCards.forEach(card => {
            if (card === element) {
                card.style.setProperty('--slide-x', `${slideX}px`);
                card.classList.add('is-selected-animating');
            } else {
                card.classList.add('is-unselected');
            }
        });

        // HTMX update picker for the new mode.
        // If no checkboxes exist yet (picker was hidden because mode was null on load),
        // fall back to the server-rendered initial preselection stored in #sh-initial-selection.
        const hasRenderedCheckboxes = document.querySelectorAll('.sh-source-checkbox').length > 0;
        let deckIds, fileIds;
        if (hasRenderedCheckboxes) {
            deckIds = Array.from(document.querySelectorAll('input[name="selectedDeckIds"]:checked')).map(cb => cb.value);
            fileIds = Array.from(document.querySelectorAll('input[name="selectedFileIds"]:checked')).map(cb => cb.value);
        } else {
            deckIds = Array.from(document.querySelectorAll('#sh-initial-selection [data-init-deck]')).map(el => el.value);
            fileIds = Array.from(document.querySelectorAll('#sh-initial-selection [data-init-file]')).map(el => el.value);
        }
        htmx.ajax('POST', '/study/setup/update', {
            target: '#setup-picker',
            values: { mode: mode, selectedDeckIds: deckIds, selectedFileIds: fileIds },
            swap: 'outerHTML'
        });

        setTimeout(() => {
            goToStep(2);
            // Reset animation classes after transition
            setTimeout(() => {
                allCards.forEach(c => {
                    c.classList.remove('is-selected-animating', 'is-unselected');
                    c.style.removeProperty('--slide-x');
                });
            }, 800);
        }, 900);
    };

    function goToStep(step) {
        if (currentStep === step) return;

        const activePanel = document.querySelector('.sh-wizard-panel.is-active');
        if (activePanel) {
            activePanel.classList.remove('is-active-fade');
            activePanel.classList.add('is-fading-out');
            activePanel.classList.remove('is-active');
            setTimeout(() => {
                activePanel.classList.remove('is-fading-out');
            }, 500);
        }
        switchContent(step);
    }

    function switchContent(step) {
        currentStep = step;
        
        // Toggle visibility of panels based on step AND mode
        document.querySelectorAll('.sh-wizard-panel').forEach(panel => {
            const panelStep = panel.dataset.sourceStep === 'true'
                ? sourceStep()
                : parseInt(panel.dataset.step);
            const panelMode = panel.dataset.mode;
            
            let isTarget = false;
            if (panelStep === 1 && step === 1) {
                isTarget = true;
            } else if (panelStep === step) {
                if (!panelMode || panelMode === currentMode) {
                    isTarget = true;
                }
            }
            
            panel.classList.toggle('is-active', isTarget);
            if (isTarget) {
                panel.offsetHeight; // trigger reflow
                panel.classList.add('is-active-fade');
            } else {
                panel.classList.remove('is-active-fade');
            }
        });

        updateStepIndicator();
        updateNavButtons();
        if (typeof initLucide === 'function') initLucide();
    }

    function updateStepIndicator() {
        const labels = STEP_LABELS[currentMode];
        const max = maxSteps();
        const container = document.getElementById('sh-wizard-steps');
        if (!container) return;
        
        container.innerHTML = '';

        labels.forEach((label, i) => {
            const stepNum = i + 1;
            if (stepNum > max) return;

            if (i > 0) {
                const conn = document.createElement('div');
                conn.className = 'sh-wizard-step-connector';
                container.appendChild(conn);
            }

            const stepEl = document.createElement('div');
            stepEl.className = 'sh-wizard-step';
            if (stepNum === currentStep) stepEl.classList.add('is-active');
            if (stepNum < currentStep) {
                stepEl.classList.add('is-done');
                stepEl.style.cursor = 'pointer';
                stepEl.onclick = () => goToStep(stepNum);
            }
            stepEl.innerHTML = `<div class="sh-wizard-step-dot">${stepNum}</div><div class="sh-wizard-step-label">${label}</div>`;
            container.appendChild(stepEl);
        });
    }

    function updateNavButtons() {
        const max = maxSteps();
        const isLast = currentStep === max;
        
        const backBtn = document.getElementById('wizard-btn-back');
        const nextBtn = document.getElementById('wizard-btn-next');
        const submitBtn = document.getElementById('wizard-btn-submit');
        const submitWithInstructionsBtn = document.getElementById('wizard-btn-submit-with-instructions');
        const footer = document.querySelector('.sh-wizard-footer');

        if (backBtn) backBtn.style.display = currentStep > 1 ? '' : 'none';
        if (nextBtn) nextBtn.style.display = (isLast || currentStep === 1) ? 'none' : '';
        if (submitBtn) submitBtn.style.display = isLast ? '' : 'none';
        if (submitWithInstructionsBtn) {
            const visible = isLast && (currentMode === 'QUIZ' || currentMode === 'EXAM');
            submitWithInstructionsBtn.style.display = visible ? '' : 'none';
        }
        if (footer) footer.style.display = currentStep === 1 ? 'none' : '';
        
        if (submitBtn) {
            const icon = submitBtn.querySelector('iconify-icon');
            const text = submitBtn.querySelector('.sh-btn-text');
            
            if (currentMode === 'QUIZ') {
                if (icon) icon.setAttribute('icon', 'lucide:sparkles');
                if (text) text.textContent = 'Generate Quiz';
            } else if (currentMode === 'EXAM') {
                if (icon) icon.setAttribute('icon', 'lucide:pencil-line');
                if (text) text.textContent = 'Start Exam';
            } else {
                if (icon) icon.setAttribute('icon', 'lucide:play');
                if (text) text.textContent = 'Start Session';
            }
        }

        if (submitWithInstructionsBtn) {
            if (currentMode === 'QUIZ') {
                submitWithInstructionsBtn.innerHTML = '<iconify-icon icon="lucide:message-square-text"></iconify-icon> Generate with instructions';
            } else if (currentMode === 'EXAM') {
                submitWithInstructionsBtn.innerHTML = '<iconify-icon icon="lucide:message-square-text"></iconify-icon> Start with instructions';
            }
        }
        
        if (typeof initLucide === 'function') initLucide();
    }

    function validateStep(step) {
        if (step === 2) {
            if (currentMode === 'FLASHCARDS') {
                const selected = document.querySelector('input[name="sessionMode"]:checked');
                if (!selected) {
                    shAlert({ title: 'Missing selection', message: 'Please select a study mode.' });
                    return false;
                }
            }
            if (currentMode === 'QUIZ') {
                const selected = document.querySelector('input[name="quizQuestionMode"]:checked');
                if (!selected) {
                    shAlert({ title: 'Missing selection', message: 'Please select a quiz format.' });
                    return false;
                }
            }
            if (currentMode === 'EXAM') {
                const selected = document.querySelector('input[name="questionSize"]:checked');
                if (!selected) {
                    shAlert({ title: 'Missing selection', message: 'Please select an exam question depth.' });
                    return false;
                }
            }
        }
        if (step === 3) {
            if (currentMode === 'QUIZ') {
                const countInput = document.querySelector('input[name="questionCount"]');
                if (countInput && (parseInt(countInput.value) < 1 || parseInt(countInput.value) > 20)) {
                    shAlert({ title: 'Invalid value', message: 'Please enter a question count between 1 and 20.' });
                    return false;
                }
            }
            if (currentMode === 'EXAM') {
                const countInput = document.getElementById('exam-count-input');
                const timerToggle = document.getElementById('exam-timer-toggle');
                const timerInput = document.getElementById('exam-timer-input');

                if (countInput && (parseInt(countInput.value) < 1 || parseInt(countInput.value) > 20)) {
                    shAlert({ title: 'Invalid value', message: 'Please enter a question count between 1 and 20.' });
                    return false;
                }

                if (timerToggle && timerToggle.checked && timerInput) {
                    if (parseInt(timerInput.value) < 1 || parseInt(timerInput.value) > 240) {
                        shAlert({ title: 'Invalid value', message: 'Please enter a timer duration between 1 and 240 minutes.' });
                        return false;
                    }
                }
            }
        }
        if (step === sourceStep()) {
            const checked = document.querySelectorAll('.sh-source-checkbox:checked');
            if (checked.length === 0) {
                shAlert({ title: 'Missing source', message: 'Please select at least one source.' });
                return false;
            }
        }
        if (step === 5) {
            if (currentMode === 'EXAM') {
                const selected = document.querySelector('input[name="layout"]:checked');
                if (!selected) {
                    shAlert({ title: 'Missing selection', message: 'Please select an exam layout.' });
                    return false;
                }
            }
        }
        return true;
    }

    function wizardNext() {
        if (!validateStep(currentStep)) return;
        
        if (currentStep === sourceStep()) {
            const form = document.querySelector('.sh-study-setup-card');
            if (currentMode === 'FLASHCARDS' && document.querySelector('input[name="sessionMode"]:checked').value === 'SHUFFLED') {
                form.requestSubmit();
                return;
            }
            if (currentMode === 'QUIZ') {
                form.requestSubmit();
                return;
            }
            // If Flashcards + Deck-by-deck, we go to step 4 (Order)
            populateOrderList();
        }
        goToStep(currentStep + 1);
    }

    // Drag and drop logic for order (Flashcards step 4)
    function populateOrderList() {
        const list = document.getElementById('sh-study-order-list');
        if (!list) return;
        
        const checked = document.querySelectorAll('input[name="selectedDeckIds"]:checked');
        list.querySelectorAll('.sh-study-drag-item').forEach(el => el.remove());
        
        checked.forEach(cb => {
            const deckLabel = cb.closest('.vb-deck');
            const deckName = deckLabel.querySelector('.vb-deck-name').textContent;
            const li = document.createElement('li');
            li.className = 'sh-study-drag-item';
            li.draggable = true;
            li.dataset.deckId = cb.value;
            li.innerHTML = `<i data-lucide="grip-vertical" class="sh-study-drag-handle"></i><span>${deckName}</span>`;
            
            li.addEventListener('dragstart', e => { e.target.classList.add('is-dragging'); });
            li.addEventListener('dragend', e => { e.target.classList.remove('is-dragging'); updateOrderInput(); });
            list.appendChild(li);
        });

        if (!list.hasAttribute('data-drag-init')) {
            list.setAttribute('data-drag-init', 'true');
            list.addEventListener('dragover', e => {
                e.preventDefault();
                const dragging = document.querySelector('.is-dragging');
                if (!dragging) return;
                
                const afterElement = [...list.querySelectorAll('.sh-study-drag-item:not(.is-dragging)')].reduce((closest, child) => {
                    const box = child.getBoundingClientRect();
                    const offset = e.clientY - box.top - box.height / 2;
                    if (offset < 0 && offset > closest.offset) return { offset: offset, element: child };
                    return closest;
                }, { offset: Number.NEGATIVE_INFINITY }).element;
                
                if (afterElement) list.insertBefore(dragging, afterElement);
                else list.appendChild(dragging);
            });
        }
        updateOrderInput();
    }

    function updateOrderInput() {
        const ids = [...document.querySelectorAll('.sh-study-drag-item')].map(el => el.dataset.deckId);
        const orderInput = document.getElementById('study-ordered-deck-ids');
        if (orderInput) orderInput.value = ids.join(',');
    }

    window.reapplySearch = function() {
        const searchInput = document.getElementById('sh-source-search');
        if (!searchInput) return;
        
        const q = searchInput.value.toLowerCase();
        document.querySelectorAll('.vb-group, .vb-subgroup').forEach(folder => {
            folder.classList.remove('is-search-expanded');
        });

        document.querySelectorAll('.vb-deck, .vb-file').forEach(el => {
            const text = el.innerText.toLowerCase();
            el.style.display = !q || text.includes(q) ? '' : 'none';
        });

        document.querySelectorAll('.vb-group, .vb-subgroup').forEach(folder => {
            const text = folder.innerText.toLowerCase();
            const matches = !q || text.includes(q);
            folder.style.display = matches ? '' : 'none';
            if (q && matches) folder.classList.add('is-search-expanded');
            syncSourceFolderToggle(folder);
        });
    };

    window.updateSelectAllButton = function() {
        const btn = document.getElementById('vb-select-all-btn');
        if (!btn) return;
        
        const checkboxes = document.querySelectorAll('.sh-source-checkbox:not(:disabled)');
        const checked = document.querySelectorAll('.sh-source-checkbox:checked');
        const text = btn.querySelector('.vb-select-all-text');
        
        const allSelected = checkboxes.length > 0 && checked.length === checkboxes.length;

        if (allSelected) {
            text.textContent = 'Deselect all';
        } else {
            text.textContent = 'Select all';
        }
        btn.classList.toggle('is-on', allSelected);
        btn.setAttribute('aria-pressed', allSelected ? 'true' : 'false');
    };

    function applyIndeterminateCheckboxes() {
        document.querySelectorAll('input[type="checkbox"][data-indeterminate]').forEach(checkbox => {
            const value = (checkbox.dataset.indeterminate || '').toLowerCase();
            checkbox.indeterminate = value === 'true' || value === '1';
        });
    }

    function setSourceFolderExpanded(folder, expanded) {
        folder.classList.toggle('is-collapsed', !expanded);
        folder.classList.remove('is-search-expanded');
        const toggle = folder.querySelector(':scope > .vb-group-head .vb-folder-toggle, :scope > .vb-subgroup-head .vb-folder-toggle');
        if (toggle) {
            toggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
            toggle.setAttribute('title', expanded ? 'Collapse folder' : 'Expand folder');
        }
    }

    function syncSourceFolderToggle(folder) {
        const toggle = folder.querySelector(':scope > .vb-group-head .vb-folder-toggle, :scope > .vb-subgroup-head .vb-folder-toggle');
        if (!toggle) return;
        const expanded = !folder.classList.contains('is-collapsed') || folder.classList.contains('is-search-expanded');
        toggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        toggle.setAttribute('title', expanded ? 'Collapse folder' : 'Expand folder');
    }

    window.openFoldersWithSelection = function() {
        document.querySelectorAll('.vb-group, .vb-subgroup').forEach(folder => {
            const hasSelection = !!folder.querySelector(':scope > .vb-folder-content .sh-source-checkbox:checked, :scope > .vb-group-head .sh-source-folder-checkbox:checked, :scope > .vb-subgroup-head .sh-source-folder-checkbox:checked');
            if (hasSelection) setSourceFolderExpanded(folder, true);
            else syncSourceFolderToggle(folder);
        });
    };

    window.initSourceFolderTree = function() {
        const picker = document.getElementById('setup-picker');
        if (!picker) return;

        openFoldersWithSelection();

        picker.querySelectorAll('.vb-folder-toggle').forEach(toggle => {
            if (toggle.dataset.initialized === 'true') return;
            toggle.dataset.initialized = 'true';
            toggle.addEventListener('click', event => {
                event.preventDefault();
                event.stopPropagation();
                const folder = toggle.closest('.vb-group, .vb-subgroup');
                if (!folder) return;
                setSourceFolderExpanded(folder, folder.classList.contains('is-collapsed'));
            });
        });

        picker.querySelectorAll('.vb-folder-name-label').forEach(label => {
            if (label.dataset.initialized === 'true') return;
            label.dataset.initialized = 'true';
            label.addEventListener('click', event => {
                event.preventDefault();
                const checkbox = label.closest('.vb-group-head, .vb-subgroup-head')?.querySelector('.sh-source-folder-checkbox');
                if (!checkbox || checkbox.disabled) return;
                checkbox.click();
            });
        });
    };

    function clampStepperValue(input) {
        const min = parseInt(input.min || '0', 10);
        const max = parseInt(input.max || '999', 10);
        const parsed = parseInt(input.value, 10);
        const value = Number.isNaN(parsed) ? min : Math.min(max, Math.max(min, parsed));
        input.value = value;
        return value;
    }

    function syncStepperButtons(stepper) {
        const input = stepper.querySelector('input[type="number"]');
        if (!input) return;
        const value = clampStepperValue(input);
        const min = parseInt(input.min || '0', 10);
        const max = parseInt(input.max || '999', 10);
        const decrement = stepper.querySelector('[data-stepper-action="decrement"]');
        const increment = stepper.querySelector('[data-stepper-action="increment"]');
        if (decrement) decrement.disabled = value <= min || input.disabled;
        if (increment) increment.disabled = value >= max || input.disabled;
    }

    function initCustomSteppers(root = document) {
        root.querySelectorAll('[data-stepper]').forEach(stepper => {
            if (stepper.dataset.initialized === 'true') {
                syncStepperButtons(stepper);
                return;
            }
            stepper.dataset.initialized = 'true';
            const input = stepper.querySelector('input[type="number"]');
            if (!input) return;

            stepper.addEventListener('click', event => {
                const btn = event.target.closest('[data-stepper-action]');
                if (!btn || btn.disabled || input.disabled) return;
                const direction = btn.dataset.stepperAction === 'increment' ? 1 : -1;
                const step = parseInt(input.step || '1', 10) || 1;
                input.value = (parseInt(input.value, 10) || 0) + direction * step;
                clampStepperValue(input);
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                syncStepperButtons(stepper);
            });

            input.addEventListener('input', () => syncStepperButtons(stepper));
            input.addEventListener('change', () => syncStepperButtons(stepper));
            syncStepperButtons(stepper);
        });
    }

    // Core init — called on DOMContentLoaded AND after HTMX swaps
    window.initStudyWizard = function () {
        const wizardForm = document.querySelector('.sh-study-setup-card');
        if (!wizardForm) return;

        applyIndeterminateCheckboxes();
        initSourceFolderTree();
        initCustomSteppers(wizardForm);

        // If this specific form instance is already initialized, don't reset state.
        // This prevents HTMX partial updates (like source selection) from resetting the step.
        if (wizardForm.dataset.initialized === 'true') return;
        wizardForm.dataset.initialized = 'true';

        // Reset state for fresh load
        currentStep = 1;
        currentMode = 'FLASHCARDS';

        const nextBtn = document.getElementById('wizard-btn-next');
        const backBtn = document.getElementById('wizard-btn-back');

        // Use replaceWith-safe pattern: remove old listeners by cloning
        if (nextBtn) {
            const fresh = nextBtn.cloneNode(true);
            nextBtn.parentNode.replaceChild(fresh, nextBtn);
            fresh.addEventListener('click', wizardNext);
        }
        if (backBtn) {
            const fresh = backBtn.cloneNode(true);
            backBtn.parentNode.replaceChild(fresh, backBtn);
            fresh.addEventListener('click', () => goToStep(currentStep - 1));
        }
        const submitBtn = document.getElementById('wizard-btn-submit');
        const submitWithInstructionsBtn = document.getElementById('wizard-btn-submit-with-instructions');
        if (submitBtn) {
            const fresh = submitBtn.cloneNode(true);
            submitBtn.parentNode.replaceChild(fresh, submitBtn);
            fresh.addEventListener('click', (e) => {
                if (!validateStep(currentStep)) e.preventDefault();
            });
        }
        if (submitWithInstructionsBtn) {
            const fresh = submitWithInstructionsBtn.cloneNode(true);
            submitWithInstructionsBtn.parentNode.replaceChild(fresh, submitWithInstructionsBtn);
            fresh.addEventListener('click', async (e) => {
                e.preventDefault();
                if (!validateStep(currentStep)) return;
                const form = document.querySelector('.sh-study-setup-card');
                if (!form) return;
                const preflightOk = await runAiPreflight('/study/session/preflight', form);
                if (!preflightOk) return;
                const hidden = form.querySelector('input[name="additionalInstructions"]');
                const instructions = await openInstructionDialog('');
                if (instructions === null) return;
                const normalizedInstructions = typeof instructions === 'string' ? instructions.trim() : '';
                if (hidden) hidden.value = normalizedInstructions;
                form.dataset.instructionsSubmit = 'true';
                form.requestSubmit();
            });
        }
        // EXAM Step 2 logic: time estimation
        const TIMER_PER_Q = { SHORT: 2, MEDIUM: 5, LONG: 10, MIXED: 5 };
        const updateExamEstimate = () => {
            const size = document.querySelector('input[name="questionSize"]:checked')?.value || 'MEDIUM';
            const count = parseInt(document.getElementById('exam-count-input')?.value || '5');
            const estimate = TIMER_PER_Q[size] * count;

            const label = document.getElementById('exam-estimate-label');
            if (label) {
                label.innerHTML = `<i data-lucide="clock" style="width:14px;height:14px;vertical-align:middle;margin-right:4px;"></i> Estimated time: ~${estimate} minutes`;
                if (typeof initLucide === 'function') initLucide();
            }

            const timerInput = document.getElementById('exam-timer-input');
            if (timerInput && !timerInput.hasAttribute('data-user-overridden')) {
                timerInput.value = estimate;
                const timerStepper = timerInput.closest('[data-stepper]');
                if (timerStepper) syncStepperButtons(timerStepper);
            }
        };

        const examTimerToggle = document.getElementById('exam-timer-toggle');
        const examTimerInput = document.getElementById('exam-timer-input');
        const examCountInput = document.getElementById('exam-count-input');

        if (examTimerToggle) {
            examTimerToggle.addEventListener('change', () => {
                if (examTimerInput) {
                    examTimerInput.disabled = !examTimerToggle.checked;
                    examTimerInput.closest('#exam-timer-input-wrap').style.opacity = examTimerToggle.checked ? '1' : '0.5';
                    const timerStepper = examTimerInput.closest('[data-stepper]');
                    if (timerStepper) syncStepperButtons(timerStepper);
                }
            });
        }

        if (examTimerInput) {
            examTimerInput.addEventListener('input', () => {
                examTimerInput.setAttribute('data-user-overridden', 'true');
            });
        }

        if (examCountInput) {
            examCountInput.addEventListener('input', updateExamEstimate);
        }

        document.querySelectorAll('input[name="questionSize"]').forEach(input => {
            input.addEventListener('change', updateExamEstimate);
        });

        // Handle preselected mode from server (Skip step 1)
        const modeInput = document.getElementById('study-mode-input');
        if (modeInput && modeInput.value) {
            currentMode = modeInput.value;
            currentStep = 2;
            if (currentMode === 'EXAM') updateExamEstimate();
            switchContent(2);
        } else {
            switchContent(1);
        }
    };

    // Initialize on first page load
    document.addEventListener('DOMContentLoaded', window.initStudyWizard);

    // Re-initialize after HTMX injects the wizard via navigation
    document.body.addEventListener('htmx:afterSettle', function (e) {
        if (document.getElementById('study-mode-input')) {
            window.initStudyWizard();
        }
    });

    document.body.addEventListener('submit', (event) => {
        const form = event.target;
        if (!form.matches?.('form.sh-study-setup-card')) return;
        const hidden = form.querySelector('input[name="additionalInstructions"]');
        if (!hidden) return;
        if (form.dataset.instructionsSubmit === 'true') {
            delete form.dataset.instructionsSubmit;
            return;
        }
        hidden.value = '';
    });

    // Progress message cycler for AI generation (Quiz & Exam)
    const PROGRESS_MESSAGES = {
        QUIZ: ['Generating your quiz with AI…', 'Reading content...', 'Analyzing topics...', 'Asking Gemini...', 'Generating questions...'],
        EXAM: ['Generating your exam with AI…', 'Reading content...', 'Analyzing topics...', 'Generating questions...', 'Setting up exam...'],
    };
    let timer = null;
    document.body.addEventListener('htmx:beforeRequest', (e) => {
        if (!e.detail.elt.matches || !e.detail.elt.matches('form.sh-study-setup-card')) return;

        const modal = document.getElementById('ai-generating-modal');
        if (currentMode === 'QUIZ' || currentMode === 'EXAM') {
            if (modal) modal.style.display = '';
            const messages = PROGRESS_MESSAGES[currentMode];
            let i = 0;
            const el = document.getElementById('ai-gen-title');
            if (el) {
                el.textContent = messages[0];
                timer = setInterval(() => { el.textContent = messages[(++i) % messages.length]; }, 2000);
            }
        } else if (modal) {
            modal.style.display = 'none';
        }
    });
    document.body.addEventListener('htmx:afterRequest', (e) => {
        if (!e.detail.elt?.matches || !e.detail.elt.matches('form.sh-study-setup-card')) return;
        if (timer) { clearInterval(timer); timer = null; }
        const modal = document.getElementById('ai-generating-modal');
        if (modal) modal.style.display = 'none';
    });

    // Abort AI generation when Cancel is clicked
    document.body.addEventListener('click', (e) => {
        if (e.target.closest('#ai-gen-abort-btn')) {
            const form = document.querySelector('.sh-study-setup-card');
            if (form) htmx.trigger(form, 'htmx:abort');
            if (timer) { clearInterval(timer); timer = null; }
        }
    });

    // Handle "Select All" button
    document.addEventListener('click', e => {
        if (e.target.closest('#vb-select-all-btn')) {
            const btn = e.target.closest('#vb-select-all-btn');
            const checkboxes = document.querySelectorAll('.sh-source-checkbox:not(:disabled)');
            const checked = document.querySelectorAll('.sh-source-checkbox:checked');
            const allSelected = checkboxes.length > 0 && checked.length === checkboxes.length;
            const isSelectAll = !allSelected;

            const pdfModeValues = {};
            document.querySelectorAll('input[type="hidden"][name^="pdfMode["]').forEach(input => {
                pdfModeValues[input.name] = input.value;
            });
            
            if (isSelectAll) {
                checkboxes.forEach(cb => cb.checked = true);
                
                const selectedDeckIds = Array.from(document.querySelectorAll('input[name="selectedDeckIds"]:checked')).map(cb => cb.value);
                const selectedFileIds = Array.from(document.querySelectorAll('input[name="selectedFileIds"]:checked')).map(cb => cb.value);

                htmx.ajax('POST', '/study/setup/update', {
                    target: '#setup-picker',
                    values: {
                        selectedDeckIds: selectedDeckIds.length > 0 ? selectedDeckIds : null, 
                        selectedFileIds: selectedFileIds.length > 0 ? selectedFileIds : null,
                        mode: currentMode,
                        ...pdfModeValues
                    },
                    swap: 'outerHTML'
                });
            } else {
                htmx.ajax('POST', '/study/setup/update', {
                    target: '#setup-picker',
                    values: { clearAll: true, mode: currentMode, ...pdfModeValues },
                    swap: 'outerHTML'
                });
            }
        }
    });

    // Keep source search filtering active across HTMX swaps
    document.addEventListener('input', (event) => {
        if (!event.target.matches('#sh-source-search')) return;
        reapplySearch();
    });

// Per-PDF mode toggle in the source picker
document.addEventListener('click', (event) => {
    const btn = event.target.closest('.vb-pdf-mode-btn');
    if (!btn) return;
    event.preventDefault();
    event.stopPropagation();
    if (btn.disabled) return;
    const group = btn.closest('.vb-pdf-mode');
    if (!group || group.classList.contains('is-disabled')) return;
    const mode = btn.dataset.mode;
    const hidden = group.querySelector('input[type="hidden"][name^="pdfMode"]');
    if (hidden) hidden.value = mode;
    group.querySelectorAll('.vb-pdf-mode-btn').forEach(b => {
        b.classList.toggle('is-active', b === btn);
        b.setAttribute('aria-pressed', b === btn ? 'true' : 'false');
    });
});

})();
