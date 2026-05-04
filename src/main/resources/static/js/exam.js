/**
 * Exam Runtime Logic
 */
(function() {
    // --- Timer Logic ---
    function initTimer() {
        const timerEl = document.querySelector('.sh-exam-timer');
        if (!timerEl) return;

        const startedAtStr = timerEl.dataset.startedAt;
        const timerMinutes = parseInt(timerEl.dataset.timerMinutes);
        if (!startedAtStr || isNaN(timerMinutes)) return;

        const startedAt = new Date(startedAtStr).getTime();
        const endsAt = startedAt + (timerMinutes * 60 * 1000);

        const updateTimer = () => {
            const now = Date.now();
            const remaining = endsAt - now;

            if (remaining <= 0) {
                timerEl.querySelector('span').textContent = "00:00";
                autoSubmit();
                return;
            }

            const mins = Math.floor(remaining / 60000);
            const secs = Math.floor((remaining % 60000) / 1000);
            
            timerEl.querySelector('span').textContent = 
                `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;

            if (remaining <= 60000) { // 1 min
                timerEl.classList.add('is-danger');
                timerEl.classList.remove('is-warn');
            } else if (remaining <= 300000) { // 5 mins
                timerEl.classList.add('is-warn');
            }

            setTimeout(updateTimer, 1000);
        };

        updateTimer();
    }

    function autoSubmit() {
        console.log("Timer expired, auto-submitting...");
        const submitBtn = document.querySelector('button[hx-post="/exam/submit"]');
        if (submitBtn) {
            submitBtn.click();
        } else {
            const form = document.querySelector('form[hx-post="/exam/submit"]');
            if (form) {
                htmx.trigger(form, 'submit');
            }
        }
    }

    // --- Textarea Autosize ---
    function initAutosize() {
        const textareas = document.querySelectorAll('.sh-exam-answer');
        textareas.forEach(textarea => {
            const adjustHeight = () => {
                textarea.style.height = 'auto';
                textarea.style.height = textarea.scrollHeight + 'px';
            };
            textarea.addEventListener('input', adjustHeight);
            // Initial adjust
            adjustHeight();
        });
    }

    // --- Loader Rotator ---
    function initLoaderRotator() {
        const loaderText = document.querySelector('.sh-exam-loader-text');
        if (!loaderText) return;

        const messages = [
            "Reading your answers...",
            "Comparing with source material...",
            "Drafting feedback...",
            "Compiling report..."
        ];
        let index = 0;

        setInterval(() => {
             index = (index + 1) % messages.length;
             loaderText.textContent = messages[index];
        }, 3000);
    }

    // --- Submit Confirmation ---
    function handleConfirmSubmit(e) {
        const textareas = document.querySelectorAll('.sh-exam-answer');
        let emptyCount = 0;
        textareas.forEach(ta => {
            if (!ta.value.trim()) emptyCount++;
        });

        if (emptyCount > 0) {
            if (!confirm(`Submit with ${emptyCount} unanswered question${emptyCount > 1 ? 's' : ''}?`)) {
                e.preventDefault();
                e.stopPropagation();
                return false;
            }
        }
        return true;
    }

    // --- Answer Cache (PER_PAGE) ---
    function initAnswerCache() {
        const textarea = document.querySelector('.sh-exam-answer[name="answer"]');
        if (!textarea) return;

        const index = textarea.closest('[data-q-index]')?.dataset.qIndex;
        const examId = "current-exam"; // Could be more specific if we had the ID in state

        if (index !== undefined) {
            const cacheKey = `exam_ans_${examId}_${index}`;
            
            // Load from cache if empty
            if (!textarea.value.trim()) {
                const cached = sessionStorage.getItem(cacheKey);
                if (cached) textarea.value = cached;
            }

            // Save to cache on input
            textarea.addEventListener('input', () => {
                sessionStorage.setItem(cacheKey, textarea.value);
            });
        }
    }

    // Initialize on page load and HTMX swaps
    function init() {
        initTimer();
        initAutosize();
        initLoaderRotator();
        initAnswerCache();

        // Attach confirm handler to submit buttons
        document.querySelectorAll('button[hx-post="/exam/submit"]').forEach(btn => {
            btn.addEventListener('click', function(e) {
                if (!handleConfirmSubmit(e)) {
                    // HTMX might still trigger, so we need to prevent it more strongly if needed
                    // Actually, returning false or stopPropagation usually works with standard listeners.
                }
            });
        });
        
        // For the single page form
        document.querySelectorAll('form[hx-post="/exam/submit"]').forEach(form => {
            form.addEventListener('htmx:confirm', function(evt) {
                if (!handleConfirmSubmit(evt)) {
                    evt.preventDefault();
                }
            });
        });
    }

    document.addEventListener('DOMContentLoaded', init);
    document.addEventListener('htmx:afterSwap', init);

})();
