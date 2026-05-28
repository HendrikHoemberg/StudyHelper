// dungeon-input.js — keyboard and mouse input handler
var DungeonInput = (function () {
    var globalInputBound = false;

    function init() {
        if (!globalInputBound) {
            globalInputBound = true;

            document.addEventListener('keydown', movementKeydown);
            document.addEventListener('keyup', movementKeyup);
            document.addEventListener('click', directionClick);
            document.addEventListener('keydown', encounterKeyboard);
        }

        var canvas = document.getElementById('dungeon-room-canvas');
        if (canvas && !canvas.dungeonListenersBound) {
            canvas.dungeonListenersBound = true;
            bindCanvasInteractiveListeners(canvas);
        }
    }

    function movementKeydown(e) {
        var key = e.key.toLowerCase();
        if (['arrowup', 'arrowdown', 'arrowleft', 'arrowright', 'w', 'a', 's', 'd'].indexOf(key) !== -1) {
            window.dungeonActiveKeys[e.key] = true;
            window.dungeonActiveKeys[key] = true;
            var canvas = document.getElementById('dungeon-room-canvas');
            if (canvas && !canvas.dataset.activeEncounterId && !window.dungeonSplashActive) {
                e.preventDefault();
            }
        }
    }

    function movementKeyup(e) {
        var key = e.key.toLowerCase();
        window.dungeonActiveKeys[e.key] = false;
        window.dungeonActiveKeys[key] = false;
    }

    function directionClick(e) {
        var btn = e.target.closest('.sh-dungeon-dir');
        if (btn) {
            window.dungeonLastMoveDirection = btn.value || btn.getAttribute('value');
        }
    }

    function encounterKeyboard(e) {
        var canvas = document.getElementById('dungeon-room-canvas');
        if (!canvas) return;

        var activeEncId = canvas.dataset.activeEncounterId;
        if (activeEncId && !window.dungeonSplashActive) {
            var isFlashcard = !!document.querySelector('.sh-dungeon-flashcard');
            var isQuiz = !!document.querySelector('.sh-dungeon-quiz-form');

            if (isQuiz) {
                if (['1', '2', '3', '4'].indexOf(e.key) !== -1) {
                    e.preventDefault();
                    var idx = parseInt(e.key, 10) - 1;
                    var form = document.querySelector('.sh-dungeon-quiz-form');
                    if (form) {
                        var inputs = form.querySelectorAll('input');
                        if (inputs[idx]) {
                            inputs[idx].checked = !inputs[idx].checked;
                            DungeonAudio.playSelect();
                            window.dungeonTriggerDraw();
                        }
                    }
                    return;
                }
                if (e.key === 'Enter') {
                    e.preventDefault();
                    var form = document.querySelector('.sh-dungeon-quiz-form');
                    if (form) {
                        DungeonAudio.playSubmit();
                        htmx.trigger(form, 'submit');
                    }
                    return;
                }
            }

            if (isFlashcard) {
                var details = document.querySelector('.sh-dungeon-flashcard-details');
                var isRevealed = details ? details.open : false;

                if (e.key === ' ' || e.key === 'Spacebar') {
                    e.preventDefault();
                    if (details && !isRevealed) {
                        details.open = true;
                        DungeonAudio.playReveal();
                        window.dungeonTriggerDraw();
                    }
                    return;
                }
                if (isRevealed) {
                    if (e.key === 'g' || e.key === 'G') {
                        e.preventDefault();
                        var button = document.querySelector('.sh-dungeon-answer-row button[value="true"]');
                        if (button) {
                            DungeonAudio.playSubmit();
                            button.click();
                        }
                        return;
                    }
                    if (e.key === 'm' || e.key === 'M') {
                        e.preventDefault();
                        var button = document.querySelector('.sh-dungeon-answer-row button[value="false"]');
                        if (button) {
                            DungeonAudio.playSubmit();
                            button.click();
                        }
                        return;
                    }
                }
            }
            return;
        }

        if (window.dungeonSplashActive) return;
    }

    function bindCanvasInteractiveListeners(canvas) {
        canvas.addEventListener('mousemove', function (e) {
            var activeEncId = canvas.dataset.activeEncounterId;
            if (!activeEncId || window.dungeonSplashActive) return;

            var rect = canvas.getBoundingClientRect();
            var mouseX = (e.clientX - rect.left) * (canvas.width / rect.width);
            var mouseY = (e.clientY - rect.top) * (canvas.height / rect.height);

            var hoveredIdx = -1;
            var boxes = window.dungeonOptionBoxes || [];
            for (var i = 0; i < boxes.length; i++) {
                var box = boxes[i];
                if (mouseX >= box.x && mouseX <= box.x + box.w &&
                    mouseY >= box.y && mouseY <= box.y + box.h) {
                    if (box.type === 'quiz_option') {
                        hoveredIdx = box.index;
                    } else if (box.type === 'reveal') {
                        hoveredIdx = 0;
                    } else if (box.type === 'answer') {
                        hoveredIdx = box.value ? 1 : 0;
                    } else if (box.type === 'submit') {
                        hoveredIdx = boxes.length - 1;
                    }
                    break;
                }
            }

            if (hoveredIdx !== window.dungeonHoveredOptionIndex) {
                window.dungeonHoveredOptionIndex = hoveredIdx;
                if (hoveredIdx !== -1) {
                    DungeonAudio.playSelect();
                }
                window.dungeonTriggerDraw();
            }
        });

        canvas.addEventListener('click', function (e) {
            var activeEncId = canvas.dataset.activeEncounterId;
            if (!activeEncId || window.dungeonSplashActive) return;

            var rect = canvas.getBoundingClientRect();
            var mouseX = (e.clientX - rect.left) * (canvas.width / rect.width);
            var mouseY = (e.clientY - rect.top) * (canvas.height / rect.height);

            var boxes = window.dungeonOptionBoxes || [];
            for (var i = 0; i < boxes.length; i++) {
                var box = boxes[i];
                if (mouseX >= box.x && mouseX <= box.x + box.w &&
                    mouseY >= box.y && mouseY <= box.y + box.h) {
                    
                    if (box.type === 'quiz_option') {
                        var form = document.querySelector('.sh-dungeon-quiz-form');
                        if (form) {
                            var inputs = form.querySelectorAll('input');
                            if (inputs[box.index]) {
                                inputs[box.index].checked = !inputs[box.index].checked;
                                DungeonAudio.playSelect();
                                window.dungeonTriggerDraw();
                            }
                        }
                    } else if (box.type === 'submit') {
                        var form = document.querySelector('.sh-dungeon-quiz-form');
                        if (form) {
                            DungeonAudio.playSubmit();
                            htmx.trigger(form, 'submit');
                        }
                    } else if (box.type === 'reveal') {
                        var details = document.querySelector('.sh-dungeon-flashcard-details');
                        if (details) {
                            details.open = true;
                            DungeonAudio.playReveal();
                            window.dungeonTriggerDraw();
                        }
                    } else if (box.type === 'answer') {
                        var valStr = box.value ? 'true' : 'false';
                        var button = document.querySelector('.sh-dungeon-answer-row button[value="' + valStr + '"]');
                        if (button) {
                            DungeonAudio.playSubmit();
                            button.click();
                        }
                    }
                    break;
                }
            }
        });
    }

    return {
        init: init,
    };
})();
