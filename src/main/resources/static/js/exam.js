/**
 * Exam Runtime Logic
 */
(function() {
    function t(key) { return (window.i18n && window.i18n[key]) || key; }

    let timerInterval = null;
    let loaderInterval = null;

    // --- Timer Logic ---
    function initTimer() {
        const timerEl = document.querySelector('.sh-exam-timer');
        if (!timerEl) {
            if (timerInterval) {
                clearInterval(timerInterval);
                timerInterval = null;
            }
            return;
        }

        // Avoid duplicate timers
        if (timerInterval) return;

        const resumedAtStr = timerEl.dataset.resumedAt;
        const elapsedBeforeResume = parseInt(timerEl.dataset.elapsedBeforeResume || '0');
        const timerMinutes = parseInt(timerEl.dataset.timerMinutes);
        if (!resumedAtStr || isNaN(timerMinutes)) return;

        const resumedAt = new Date(resumedAtStr).getTime();
        const elapsedMs = elapsedBeforeResume * 1000 + (Date.now() - resumedAt);
        const remaining = (timerMinutes * 60 * 1000) - elapsedMs;

        const updateTimer = () => {
            const now = Date.now();
            const remaining = endsAt - now;

            if (remaining <= 0) {
                timerEl.querySelector('span').textContent = t('exam.timer.timeout');
                clearInterval(timerInterval);
                timerInterval = null;
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
        };

        updateTimer();
        timerInterval = setInterval(updateTimer, 1000);
    }

    function autoSubmit() {
        console.log("Timer expired, auto-submitting...");
        window._sh_auto_submitting = true;

        const submitBtn = document.querySelector('button[hx-post="/exam/submit"]');
        if (submitBtn) {
            submitBtn.click();
        } else {
            // In PER_PAGE mode, if we are not on the last page, we still want to submit.
            // We can manually trigger an HTMX request to the submit endpoint.
            const textarea = document.querySelector('.sh-exam-answer');
            const currentIndex = textarea?.closest('[data-q-index]')?.dataset.qIndex;
            
            if (currentIndex !== undefined) {
                htmx.ajax('POST', '/exam/submit', {
                    target: '#exam-runtime',
                    swap: 'outerHTML',
                    indicator: '#grading-loader',
                    values: {
                        index: currentIndex,
                        answer: textarea.value
                    }
                });
            } else {
                // Fallback for SINGLE_PAGE if form trigger fails
                const form = document.querySelector('form[hx-post="/exam/submit"]');
                if (form) htmx.trigger(form, 'submit');
            }
        }
    }

    // --- Textarea Autosize ---
    function initAutosize() {
        const textareas = document.querySelectorAll('.sh-exam-answer');
        textareas.forEach(textarea => {
            if (textarea._sh_autosize_init) return;
            textarea._sh_autosize_init = true;

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
        if (!loaderText) {
            if (loaderInterval) {
                clearInterval(loaderInterval);
                loaderInterval = null;
            }
            return;
        }

        if (loaderInterval) return;

        const messages = [
            t('exam.loader.reading'),
            t('exam.loader.comparing'),
            t('exam.loader.drafting'),
            t('exam.loader.compiling')
        ];
        let index = 0;

        loaderInterval = setInterval(() => {
             index = (index + 1) % messages.length;
             loaderText.textContent = messages[index];
        }, 3000);
    }

    // --- Submit Confirmation ---
    function countEmptyAnswers() {
        let emptyCount = 0;
        document.querySelectorAll('.sh-exam-answer').forEach(ta => {
            if (!ta.value.trim()) emptyCount++;
        });
        return emptyCount;
    }

    // --- Answer Cache (PER_PAGE) ---
    function initAnswerCache() {
        const textarea = document.querySelector('.sh-exam-answer[name="answer"]');
        if (!textarea || textarea._sh_cache_init) return;
        textarea._sh_cache_init = true;

        const index = textarea.closest('[data-q-index]')?.dataset.qIndex;
        const examId = "current-exam"; 

        if (index !== undefined) {
            const cacheKey = `exam_ans_${examId}_${index}`;
            
            // Load from cache if empty
            if (!textarea.value.trim()) {
                const cached = sessionStorage.getItem(cacheKey);
                if (cached) {
                    textarea.value = cached;
                    textarea.dispatchEvent(new Event('input')); // Trigger autosize
                }
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

        // Use HTMX confirm event for better integration
        document.body.addEventListener('htmx:confirm', function(evt) {
            if (evt.detail.path !== '/exam/submit') return;
            if (window._sh_auto_submitting) return;

            const emptyCount = countEmptyAnswers();
            if (emptyCount === 0) return;

            evt.preventDefault();
            shConfirm({
                title: t('exam.confirm.title'),
                message: t('exam.confirm.message').replace('{0}', emptyCount).replace('{1}', emptyCount > 1 ? 's' : ''),
                confirmText: t('exam.confirm.submit'),
                danger: true
            }).then((ok) => { if (ok) evt.detail.issueRequest(true); });
        });

        // Abort grading when Cancel is clicked
        const gradingAbortBtn = document.getElementById('grading-abort-btn');
        if (gradingAbortBtn && !gradingAbortBtn._sh_abort_init) {
            gradingAbortBtn._sh_abort_init = true;
            gradingAbortBtn.addEventListener('click', function() {
                const submitBtn = document.querySelector('button[hx-post="/exam/submit"]');
                const submitForm = document.querySelector('form[hx-post="/exam/submit"]');
                if (submitBtn) htmx.trigger(submitBtn, 'htmx:abort');
                if (submitForm) htmx.trigger(submitForm, 'htmx:abort');
            });
        }
    }

    window.initExamRuntime = init;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    document.addEventListener('htmx:afterSwap', init);

})();
