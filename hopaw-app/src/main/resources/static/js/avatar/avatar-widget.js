(function() {
    'use strict';

    var avatarInstance = null;
    var isMinimized = false;
    var isDragging = false;
    var dragStartX = 0;
    var dragStartY = 0;
    var widgetStartLeft = 0;
    var widgetStartTop = 0;
    var widgetStartRight = 0;
    var widgetStartBottom = 0;

    var ACTION_LABELS = {
        'idle': '待机中',
        'thinking': '思考中...',
        'tool_executing': '执行工具中...',
        'level_up': '亲密度提升!',
        'excited': '兴奋中',
        'confused': '困惑中',
        'wave': '欢迎!',
        'sleep': '休眠中',
        'typing': '打字中...',
        'celebrate': '庆祝中!'
    };

    var INTIMACY_TITLES = [
        '初识',
        '友好',
        '关注',
        '喜欢',
        '亲密',
        '挚友',
        '知己',
        '灵魂伴侣',
        '心有灵犀',
        '永恒羁绊'
    ];

    function initAvatarWidget() {
        var widget = document.getElementById('avatarWidget');
        if (!widget) return;

        avatarInstance = window.AvatarLive2D.init('avatarCanvas', {
            scale: 0.8,
            width: 170,
            height: 260
        });

        var toggleBtn = document.getElementById('avatarToggleBtn');
        if (toggleBtn) {
            toggleBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                isMinimized = !isMinimized;
                if (isMinimized) {
                    widget.classList.add('avatar-minimized');
                    toggleBtn.textContent = '◱';
                } else {
                    widget.classList.remove('avatar-minimized');
                    toggleBtn.textContent = '◰';
                }
                if (avatarInstance && avatarInstance.resize) {
                    setTimeout(function() { avatarInstance.resize(); }, 300);
                }
            });
        }

        initDrag(widget);
    }

    function initDrag(widget) {
        var container = widget.querySelector('.avatar-container');
        if (!container) return;

        container.addEventListener('mousedown', function(e) {
            if (e.target.closest('.avatar-toggle') || e.target.closest('.avatar-info')) {
                return;
            }
            e.preventDefault();
            isDragging = true;
            widget.classList.add('avatar-dragging');

            var rect = widget.getBoundingClientRect();
            dragStartX = e.clientX;
            dragStartY = e.clientY;
            widgetStartLeft = rect.left;
            widgetStartTop = rect.top;
            widgetStartRight = window.innerWidth - rect.right;
            widgetStartBottom = window.innerHeight - rect.bottom;

            widget.style.right = 'auto';
            widget.style.bottom = 'auto';
            widget.style.left = widgetStartLeft + 'px';
            widget.style.top = widgetStartTop + 'px';
        });

        document.addEventListener('mousemove', function(e) {
            if (!isDragging) return;
            e.preventDefault();

            var dx = e.clientX - dragStartX;
            var dy = e.clientY - dragStartY;

            var newLeft = widgetStartLeft + dx;
            var newTop = widgetStartTop + dy;

            newLeft = Math.max(0, Math.min(newLeft, window.innerWidth - widget.offsetWidth));
            newTop = Math.max(0, Math.min(newTop, window.innerHeight - widget.offsetHeight));

            widget.style.left = newLeft + 'px';
            widget.style.top = newTop + 'px';
        });

        document.addEventListener('mouseup', function() {
            if (!isDragging) return;
            isDragging = false;
            widget.classList.remove('avatar-dragging');
        });
    }

    function connectWebSocket() {
        if (typeof currentUserId === 'undefined' || !currentUserId) {
            console.warn('[AvatarWidget] currentUserId not available');
            return;
        }

        var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        var wsUrl = protocol + '//' + window.location.host + '/ws/avatar?userId=' + encodeURIComponent(currentUserId);

        var socket = new WebSocket(wsUrl);
        socket.onmessage = function(event) {
            try {
                var data = JSON.parse(event.data);
                handleAvatarEvent(data);
            } catch (e) {
                console.warn('[AvatarWidget] Failed to parse WS message:', e);
            }
        };
        socket.onclose = function() {
            console.log('[AvatarWidget] WebSocket closed, reconnecting in 5s...');
            setTimeout(connectWebSocket, 5000);
        };
        socket.onerror = function(err) {
            console.warn('[AvatarWidget] WebSocket error:', err);
        };
    }

    function fetchInitialLevel() {
        if (typeof currentUserId === 'undefined' || !currentUserId) return;

        fetch('/api/avatar/level?userId=' + encodeURIComponent(currentUserId))
            .then(function(res) { return res.json(); })
            .then(function(data) {
                if (data && data.level !== undefined) {
                    updateIntimacyUI(data);
                }
            })
            .catch(function(err) {
                console.warn('[AvatarWidget] Failed to fetch initial level:', err);
            });
    }

    function handleAvatarEvent(data) {
        if (!avatarInstance) return;

        var type = data.type;
        var action = data.action || 'idle';

        if (type === 'avatar_action') {
            avatarInstance.setAction(action);

            var actionLabel = document.getElementById('avatarActionLabel');
            if (actionLabel) {
                actionLabel.textContent = ACTION_LABELS[action] || '';
            }

            if (action === 'typing') {
                triggerHeartBeat();
            }
        } else if (type === 'avatar_level') {
            avatarInstance.setAction('level_up');
            triggerHeartBeat();

            var levelInfo = data.levelInfo;
            if (levelInfo) {
                updateIntimacyUI(levelInfo);
                triggerIntimacyUpEffect();
            }
        }
    }

    function updateIntimacyUI(levelInfo) {
        var intimacyText = document.getElementById('avatarIntimacyText');
        var titleText = document.getElementById('avatarTitleText');
        var xpFill = document.getElementById('avatarXpFill');

        if (intimacyText) {
            intimacyText.textContent = '亲密度 Lv.' + (levelInfo.level || 0);
        }
        if (titleText) {
            var level = levelInfo.level || 0;
            var title = INTIMACY_TITLES[Math.min(level, INTIMACY_TITLES.length - 1)];
            titleText.textContent = title;
        }
        if (xpFill) {
            xpFill.style.width = (levelInfo.levelProgressPercent || 0) + '%';
        }
    }

    function triggerHeartBeat() {
        var heart = document.getElementById('avatarHeartIcon');
        if (!heart) return;
        heart.classList.remove('avatar-heart-beat');
        void heart.offsetWidth;
        heart.classList.add('avatar-heart-beat');
    }

    function triggerIntimacyUpEffect() {
        var overlay = document.getElementById('avatarIntimacyUpOverlay');
        if (!overlay) return;

        overlay.classList.remove('avatar-intimacy-up-effect');
        void overlay.offsetWidth;
        overlay.classList.add('avatar-intimacy-up-effect');
    }

    function init() {
        initAvatarWidget();
        fetchInitialLevel();
        connectWebSocket();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();