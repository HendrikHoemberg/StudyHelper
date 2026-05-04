(function () {
    let currentStep = document.getElementById('study-mode-input').value ? 2 : 1;
    let currentMode = document.getElementById('study-mode-input').value || 'FLASHCARDS';

    const STEP_LABELS = {
        FLASHCARDS: ["Mode", "Type", "Decks", "Order"],
        QUIZ:       ["Mode", "Format", "Sources"],
        EXAM:       ["Mode", "Setup", "Sources", "Layout"],
    };

    function maxSteps() {
        if (currentMode === 'FLASHCARDS') {
            const type = document.querySelector('input[name="sessionMode"]:checked')?.value;
            return type === 'SHUFFLED' ? 3 : 4;
        }
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

        // HTMX update picker for the new mode
        htmx.ajax('POST', '/study/setup/update', {
            target: '#setup-picker',
            values: { mode: mode },
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
                switchContent(step);
            }, 500);
        } else {
            switchContent(step);
        }
    }

    function switchContent(step) {
        currentStep = step;
        
        // Toggle visibility of panels based on step AND mode
        document.querySelectorAll('.sh-wizard-panel').forEach(panel => {
            const panelStep = parseInt(panel.dataset.step);
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
        const footer = document.querySelector('.sh-wizard-footer');

        if (backBtn) backBtn.style.display = currentStep > 1 ? '' : 'none';
        if (nextBtn) nextBtn.style.display = (isLast || currentStep === 1) ? 'none' : '';
        if (submitBtn) submitBtn.style.display = isLast ? '' : 'none';
        if (footer) footer.style.display = currentStep === 1 ? 'none' : '';
        
        if (submitBtn) {
            const icon = submitBtn.querySelector('i');
            const text = submitBtn.querySelector('.sh-btn-text') || submitBtn.childNodes[1];
            
            if (currentMode === 'QUIZ') {
                if (icon) icon.setAttribute('data-lucide', 'sparkles');
                if (text) text.textContent = ' Generate Quiz';
            } else if (currentMode === 'EXAM') {
                if (icon) icon.setAttribute('data-lucide', 'pencil-line');
                if (text) text.textContent = ' Start Exam';
            } else {
                if (icon) icon.setAttribute('data-lucide', 'play');
                if (text) text.textContent = ' Start Session';
            }
        }
        
        if (typeof initLucide === 'function') initLucide();
    }

    function validateStep(step) {
        if (step === 2) {
            if (currentMode === 'FLASHCARDS') {
                const selected = document.querySelector('input[name="sessionMode"]:checked');
                if (!selected) {
                    alert('Please select a study mode.');
                    return false;
                }
            }
            if (currentMode === 'QUIZ') {
                const selected = document.querySelector('input[name="quizQuestionMode"]:checked');
                if (!selected) {
                    alert('Please select a quiz format.');
                    return false;
                }
            }
        }
        if (step === 3) {
            const checked = document.querySelectorAll('.sh-source-checkbox:checked');
            if (checked.length === 0) {
                alert('Please select at least one source.');
                return false;
            }
        }
        return true;
    }

    function wizardNext() {
        if (!validateStep(currentStep)) return;
        
        if (currentStep === 3) {
            if (currentMode === 'FLASHCARDS' && document.querySelector('input[name="sessionMode"]:checked').value === 'SHUFFLED') {
                document.querySelector('.sh-study-setup-card').submit();
                return;
            }
            if (currentMode === 'QUIZ') {
                document.querySelector('.sh-study-setup-card').submit();
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
        document.querySelectorAll('.vb-group, .vb-deck, .vb-file').forEach(el => {
            const text = el.innerText.toLowerCase();
            el.style.display = text.includes(q) ? '' : 'none';
        });
    };

    window.updateSelectAllButton = function() {
        const btn = document.getElementById('vb-select-all-btn');
        if (!btn) return;
        
        const checkboxes = document.querySelectorAll('.sh-source-checkbox:not(:disabled)');
        const checked = document.querySelectorAll('.sh-source-checkbox:checked');
        const text = btn.querySelector('.vb-select-all-text');
        
        if (checkboxes.length > 0 && checked.length === checkboxes.length) {
            text.textContent = 'Deselect all';
        } else {
            text.textContent = 'Select all';
        }
    };

    // Initialize events
    document.addEventListener('DOMContentLoaded', () => {
        const nextBtn = document.getElementById('wizard-btn-next');
        const backBtn = document.getElementById('wizard-btn-back');
        const searchInput = document.getElementById('sh-source-search');
        
        if (nextBtn) nextBtn.addEventListener('click', wizardNext);
        if (backBtn) backBtn.addEventListener('click', () => goToStep(currentStep - 1));
        if (searchInput) searchInput.addEventListener('input', reapplySearch);

        // Handle preselected mode from server (Skip step 1)
        const modeInput = document.getElementById('study-mode-input');
        if (modeInput && modeInput.value) {
            currentMode = modeInput.value;
            currentStep = 2;
            switchContent(2);
        } else {
            switchContent(1);
        }
    });

    // Progress message cycler for Quiz (HTMX integration)
    const messages = ['Reading content...', 'Analyzing topics...', 'Asking Gemini...', 'Generating questions...'];
    let timer = null;
    document.body.addEventListener('htmx:beforeRequest', (e) => {
        if (currentMode === 'QUIZ' && e.detail.elt.matches('form')) {
            let i = 0;
            const el = document.getElementById('quiz-progress-msg');
            if (el) {
                timer = setInterval(() => { el.textContent = messages[i++ % messages.length]; }, 2000);
            }
        }
    });
    document.body.addEventListener('htmx:afterRequest', () => { if (timer) clearInterval(timer); });

    // Handle "Select All" button
    document.addEventListener('click', e => {
        if (e.target.closest('#vb-select-all-btn')) {
            const btn = e.target.closest('#vb-select-all-btn');
            const text = btn.querySelector('.vb-select-all-text');
            const isSelectAll = text.textContent === 'Select all';
            
            if (isSelectAll) {
                const checkboxes = document.querySelectorAll('.sh-source-checkbox:not(:disabled)');
                checkboxes.forEach(cb => cb.checked = true);
                
                const selectedDeckIds = Array.from(document.querySelectorAll('input[name="selectedDeckIds"]:checked')).map(cb => cb.value);
                const selectedFileIds = Array.from(document.querySelectorAll('input[name="selectedFileIds"]:checked')).map(cb => cb.value);

                htmx.ajax('POST', '/study/setup/update', {
                    target: '#setup-picker',
                    values: { 
                        selectedDeckIds: selectedDeckIds.length > 0 ? selectedDeckIds : null, 
                        selectedFileIds: selectedFileIds.length > 0 ? selectedFileIds : null,
                        mode: currentMode 
                    },
                    swap: 'outerHTML'
                });
            } else {
                htmx.ajax('POST', '/study/setup/update', {
                    target: '#setup-picker',
                    values: { clearAll: true, mode: currentMode },
                    swap: 'outerHTML'
                });
            }
        }
    });

})();
