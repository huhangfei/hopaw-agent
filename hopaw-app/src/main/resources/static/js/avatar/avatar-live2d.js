(function() {
    'use strict';

    var AvatarLive2D = {};

    var CDN = {
        WIDGET_JS: 'https://cdn.jsdelivr.net/npm/live2d-widget@3.1.4/lib/L2Dwidget.min.js',
        MODEL: 'https://cdn.jsdelivr.net/npm/live2d-widget-model-hibiki@1.0.5/assets/hibiki.model.json'
    };

    var MESSAGE_POOL = {
        idle: [
            '你好呀~ 有什么可以帮你的吗？',
            '我在哦，随时可以问我~',
            '嘿嘿，今天状态不错~',
            '有什么想聊的吗？',
            '今天天气真好呢~'
        ],
        thinking: [
            '让我想想...',
            '嗯...这个问题有点意思',
            '思考中，请稍等~',
            '让我分析一下...',
            '唔...有思路了！'
        ],
        tool_executing: [
            '正在执行操作，请稍等~',
            '我来帮你处理！',
            '交给我吧，很快就好~',
            '正在处理中...',
            '工具运行中，马上就好！'
        ],
        level_up: [
            '我们的亲密度又提升了！',
            '和你在一起，越来越亲密了~',
            '好感度 up！❤️',
            '哇，更了解你了！',
            '新的羁绊，新的开始！'
        ],
        excited: [
            '太棒了！',
            '好厉害！',
            '哇哦！这个好酷！',
            '我喜欢这个！',
            '太有意思了！'
        ],
        confused: [
            '嗯？这是什么意思？',
            '不太明白呢...',
            '让我再想想...',
            '好复杂呀...',
            '这个有点难...'
        ],
        wave: [
            '你好呀！欢迎回来~',
            '嗨~ 又见面了！',
            '你来啦！',
            '欢迎欢迎！',
            '好久不见~'
        ],
        sleep: [
            'zzZ...',
            '呼...好困...',
            '让我休息一下...',
            'zzZZ...zzZ...',
            '午休时间...'
        ],
        typing: [
            '正在输入...',
            '让我组织一下语言...',
            '马上就好，请稍等~',
            '在打了在打了...',
            '我想想怎么表达...'
        ],
        celebrate: [
            '耶！完成啦！',
            '太棒了！大功告成！',
            '成功！好有成就感！',
            '嘿嘿，搞定啦！',
            '完美收工！'
        ]
    };

    AvatarLive2D.init = function(containerId, options) {
        options = options || {};
        var container = document.getElementById(containerId);
        if (!container) return null;

        var state = {
            currentAction: 'idle',
            targetAction: 'idle',
            messageTimer: null,
            idleMessageTimer: null,
            isSpeaking: false,
            isLoaded: false,
            initAttempts: 0,
            maxAttempts: 30
        };

        var speechBubble = null;
        var live2dWrapper = null;

        function createUI() {
            speechBubble = document.createElement('div');
            speechBubble.className = 'avatar-speech-bubble';
            speechBubble.innerHTML = '<div class="avatar-speech-text"></div><div class="avatar-speech-tail"></div>';
            speechBubble.style.display = 'none';
            container.appendChild(speechBubble);

            live2dWrapper = document.createElement('div');
            live2dWrapper.className = 'avatar-live2d-canvas';
            container.appendChild(live2dWrapper);
        }

        function loadResources(callback) {
            if (typeof L2Dwidget !== 'undefined' && typeof L2Dwidget.init === 'function') {
                callback();
                return;
            }

            var jsEl = document.createElement('script');
            jsEl.src = CDN.WIDGET_JS;
            jsEl.onload = function() {
                setTimeout(callback, 500);
            };
            jsEl.onerror = function() {
                console.warn('[AvatarLive2D] CDN load failed, retrying...');
                var retry = document.createElement('script');
                retry.src = CDN.WIDGET_JS;
                retry.onload = function() { setTimeout(callback, 500); };
                retry.onerror = function() {
                    console.error('[AvatarLive2D] All CDN attempts failed');
                };
                document.head.appendChild(retry);
            };
            document.head.appendChild(jsEl);
        }

        function initWidget() {
            try {
                L2Dwidget.init({
                    model: {
                        jsonPath: CDN.MODEL,
                        scale: options.scale || 1
                    },
                    display: {
                        position: 'right',
                        width: options.width || 150,
                        height: options.height || 280,
                        hOffset: 0,
                        vOffset: 0
                    },
                    mobile: {
                        show: options.showOnMobile || false
                    },
                    react: {
                        opacity: 0.9
                    },
                    dialog: {
                        enable: false
                    },
                    dev: {
                        border: false
                    }
                });

                state.isLoaded = true;
                embedWidget();
                startIdleMessages();
            } catch (e) {
                console.error('[AvatarLive2D] Init error:', e);
            }
        }

        function embedWidget() {
            var widgetEl = document.getElementById('live2d-widget');
            if (!widgetEl) {
                state.initAttempts++;
                if (state.initAttempts < state.maxAttempts) {
                    setTimeout(embedWidget, 200);
                }
                return;
            }

            if (widgetEl.parentNode && widgetEl.parentNode !== live2dWrapper) {
                widgetEl.parentNode.removeChild(widgetEl);
            }
            live2dWrapper.appendChild(widgetEl);

            widgetEl.style.position = 'relative';
            widgetEl.style.margin = '0 auto';
            widgetEl.style.bottom = 'auto';
            widgetEl.style.right = 'auto';
            widgetEl.style.left = 'auto';
            widgetEl.style.top = 'auto';
            widgetEl.style.zIndex = '1';
            widgetEl.style.opacity = '1';
            widgetEl.style.pointerEvents = 'auto';

            var canvas = widgetEl.querySelector('canvas');
            if (canvas) {
                canvas.style.maxWidth = '100%';
                canvas.style.maxHeight = '100%';
                canvas.style.width = 'auto';
                canvas.style.height = 'auto';
            }
        }

        function showMessage(text, duration) {
            if (!speechBubble) return;
            var textEl = speechBubble.querySelector('.avatar-speech-text');
            if (!textEl) return;

            textEl.textContent = text;
            speechBubble.style.display = 'block';
            speechBubble.classList.add('avatar-speech-show');
            state.isSpeaking = true;

            if (state.messageTimer) clearTimeout(state.messageTimer);
            state.messageTimer = setTimeout(hideMessage, duration || 3000);
        }

        function hideMessage() {
            if (!speechBubble) return;
            speechBubble.classList.remove('avatar-speech-show');
            speechBubble.style.display = 'none';
            state.isSpeaking = false;
        }

        function getRandomMessage(action) {
            var pool = MESSAGE_POOL[action] || MESSAGE_POOL['idle'];
            return pool[Math.floor(Math.random() * pool.length)];
        }

        function startIdleMessages() {
            if (state.idleMessageTimer) clearInterval(state.idleMessageTimer);
            state.idleMessageTimer = setInterval(function() {
                if (state.currentAction === 'idle' && !state.isSpeaking && Math.random() < 0.3) {
                    showMessage(getRandomMessage('idle'), 2500);
                }
            }, 15000);
        }

        function setAction(action) {
            if (!action) action = 'idle';
            state.currentAction = action;
            state.targetAction = action;

            if (action === 'wave') {
                showMessage(getRandomMessage('wave'), 2500);
            } else if (action !== 'idle' && action !== 'sleep') {
                showMessage(getRandomMessage(action), 3000);
            }
        }

        function getCurrentAction() {
            return state.currentAction;
        }

        function getTargetAction() {
            return state.targetAction;
        }

        function sayMessage(text, duration) {
            showMessage(text, duration || 3000);
        }

        function resize() {
            embedWidget();
        }

        function dispose() {
            if (state.messageTimer) clearTimeout(state.messageTimer);
            if (state.idleMessageTimer) clearInterval(state.idleMessageTimer);
            hideMessage();
            if (speechBubble && speechBubble.parentNode) {
                speechBubble.parentNode.removeChild(speechBubble);
            }
            var widgetEl = document.getElementById('live2d-widget');
            if (widgetEl && widgetEl.parentNode) {
                widgetEl.parentNode.removeChild(widgetEl);
            }
            if (live2dWrapper) live2dWrapper.innerHTML = '';
            state.isLoaded = false;
        }

        createUI();
        loadResources(initWidget);

        return {
            setAction: setAction,
            getCurrentAction: getCurrentAction,
            getTargetAction: getTargetAction,
            sayMessage: sayMessage,
            resize: resize,
            dispose: dispose
        };
    };

    window.AvatarLive2D = AvatarLive2D;
})();