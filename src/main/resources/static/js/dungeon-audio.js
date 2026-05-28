// dungeon-audio.js - Web Audio API synthesizer for dungeon sound effects
var DungeonAudio = (function () {
    var ctx = null;

    function init() {
        if (!ctx) {
            var AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (AudioContextClass) {
                ctx = new AudioContextClass();
            }
        }
        if (ctx && ctx.state === 'suspended') {
            ctx.resume();
        }
    }

    return {
        init: init,
        playMove: function () {
            init();
            if (!ctx) return;
            try {
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.connect(gain);
                gain.connect(ctx.destination);

                osc.type = 'triangle';
                osc.frequency.setValueAtTime(140, ctx.currentTime);
                osc.frequency.exponentialRampToValueAtTime(320, ctx.currentTime + 0.08);

                gain.gain.setValueAtTime(0.03, ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.08);

                osc.start();
                osc.stop(ctx.currentTime + 0.08);
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        },
        playBattleStart: function () {
            init();
            if (!ctx) return;
            try {
                var now = ctx.currentTime;
                // Double oscillator riser for dramatic tension
                [100, 150].forEach(function (baseFreq) {
                    var osc = ctx.createOscillator();
                    var gain = ctx.createGain();
                    osc.connect(gain);
                    gain.connect(ctx.destination);

                    osc.type = 'sawtooth';
                    osc.frequency.setValueAtTime(baseFreq, now);
                    osc.frequency.exponentialRampToValueAtTime(baseFreq * 6, now + 0.9);

                    gain.gain.setValueAtTime(0.04, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.9);

                    osc.start();
                    osc.stop(now + 0.9);
                });
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        },
        playSlash: function () {
            init();
            if (!ctx) return;
            try {
                var now = ctx.currentTime;
                // 1. Procedural Noise buffer for metal friction/slice
                var bufferSize = ctx.sampleRate * 0.15;
                var buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
                var data = buffer.getChannelData(0);
                for (var i = 0; i < bufferSize; i++) {
                    data[i] = Math.random() * 2 - 1;
                }

                var noise = ctx.createBufferSource();
                noise.buffer = buffer;

                var filter = ctx.createBiquadFilter();
                filter.type = 'bandpass';
                filter.frequency.setValueAtTime(800, now);
                filter.frequency.exponentialRampToValueAtTime(3200, now + 0.15);

                var noiseGain = ctx.createGain();
                noiseGain.gain.setValueAtTime(0.06, now);
                noiseGain.gain.exponentialRampToValueAtTime(0.001, now + 0.15);

                noise.connect(filter);
                filter.connect(noiseGain);
                noiseGain.connect(ctx.destination);
                noise.start();

                // 2. High-pitch chime oscillator sweep for blade ring
                var osc = ctx.createOscillator();
                var oscGain = ctx.createGain();
                osc.connect(oscGain);
                oscGain.connect(ctx.destination);

                osc.type = 'sine';
                osc.frequency.setValueAtTime(1200, now);
                osc.frequency.exponentialRampToValueAtTime(200, now + 0.18);

                oscGain.gain.setValueAtTime(0.04, now);
                oscGain.gain.exponentialRampToValueAtTime(0.001, now + 0.18);

                osc.start();
                osc.stop(now + 0.18);
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        },
        playDamage: function () {
            init();
            if (!ctx) return;
            try {
                var now = ctx.currentTime;
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.connect(gain);
                gain.connect(ctx.destination);

                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(110, now);
                osc.frequency.linearRampToValueAtTime(35, now + 0.25);

                gain.gain.setValueAtTime(0.12, now);
                gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);

                osc.start();
                osc.stop(now + 0.25);
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        },
        playVictory: function () {
            init();
            if (!ctx) return;
            try {
                var now = ctx.currentTime;
                // Upbeat ascending C Major chord
                var notes = [261.63, 329.63, 392.00, 523.25, 659.25]; // C4, E4, G4, C5, E5
                notes.forEach(function (freq, index) {
                    var osc = ctx.createOscillator();
                    var gain = ctx.createGain();
                    osc.connect(gain);
                    gain.connect(ctx.destination);

                    osc.type = 'triangle';
                    osc.frequency.setValueAtTime(freq, now + index * 0.08);

                    gain.gain.setValueAtTime(0, now + index * 0.08);
                    gain.gain.linearRampToValueAtTime(0.04, now + index * 0.08 + 0.02);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + index * 0.08 + 0.28);

                    osc.start(now + index * 0.08);
                    osc.stop(now + index * 0.08 + 0.28);
                });
            } catch (e) {
                console.warn("Audio play failed:", e);
            }
        },
        playSelect: function () {
            init();
            if (!ctx) return;
            try {
                var now = ctx.currentTime;
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.connect(gain);
                gain.connect(ctx.destination);

                osc.type = 'sine';
                osc.frequency.setValueAtTime(600, now);
                osc.frequency.exponentialRampToValueAtTime(900, now + 0.05);

                gain.gain.setValueAtTime(0.02, now);
                gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05);

                osc.start();
                osc.stop(now + 0.05);
            } catch (e) {
                console.warn("Audio play select failed:", e);
            }
        },
        playReveal: function () {
            init();
            if (!ctx) return;
            try {
                var now = ctx.currentTime;
                [0, 0.05].forEach(function (delay) {
                    var osc = ctx.createOscillator();
                    var gain = ctx.createGain();
                    osc.connect(gain);
                    gain.connect(ctx.destination);

                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(523.25, now + delay);
                    osc.frequency.exponentialRampToValueAtTime(1046.50, now + delay + 0.2);

                    gain.gain.setValueAtTime(0, now + delay);
                    gain.gain.linearRampToValueAtTime(0.03, now + delay + 0.04);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + delay + 0.2);

                    osc.start(now + delay);
                    osc.stop(now + delay + 0.2);
                });
            } catch (e) {
                console.warn("Audio play reveal failed:", e);
            }
        },
        playSubmit: function () {
            init();
            if (!ctx) return;
            try {
                var now = ctx.currentTime;
                [523.25, 783.99].forEach(function (freq) {
                    var osc = ctx.createOscillator();
                    var gain = ctx.createGain();
                    osc.connect(gain);
                    gain.connect(ctx.destination);

                    osc.type = 'triangle';
                    osc.frequency.setValueAtTime(freq, now);

                    gain.gain.setValueAtTime(0.04, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3);

                    osc.start();
                    osc.stop(now + 0.3);
                });
            } catch (e) {
                console.warn("Audio play submit failed:", e);
            }
        },
        playShieldBreak: function () {
            if (!ctx) return;
            [800, 400, 200].forEach(function (freq, i) {
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(freq, ctx.currentTime + i * 0.04);
                gain.gain.setValueAtTime(0.18, ctx.currentTime + i * 0.04);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.04 + 0.15);
                osc.connect(gain); gain.connect(ctx.destination);
                osc.start(ctx.currentTime + i * 0.04);
                osc.stop(ctx.currentTime + i * 0.04 + 0.16);
            });
        },
        playShieldGain: function () {
            if (!ctx) return;
            [523.25, 659.25, 783.99].forEach(function (freq, i) {
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(freq, ctx.currentTime + i * 0.07);
                gain.gain.setValueAtTime(0.12, ctx.currentTime + i * 0.07);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.07 + 0.25);
                osc.connect(gain); gain.connect(ctx.destination);
                osc.start(ctx.currentTime + i * 0.07);
                osc.stop(ctx.currentTime + i * 0.07 + 0.26);
            });
        }
    };
})();
