(function() {
    'use strict';

    var avatarInstance = null;
    var avatarWs = null;
    var levelUpOverlay = null;
    var actionLabel = null;
    var xpFill = null;
    var levelText = null;
    var titleText = null;
    var isMinimized = false;
    var reconnectTimer = null;

    function initAvatarWidget() {
        var widget = document.getElementById('avatarWidget');
        if (!widget) return;

        avatarInstance = Avatar3D.init('avatarCanvas');

        levelText = document.getElementById('avatarLevelText');
        titleText = document.getElementById('avatarTitleText');
        xpFill = document.getElementById('avatarXpFill');
        actionLabel = document.getElementById('avatarActionLabel');
        levelUpOverlay = document.getElementById('avatarLevelUpOverlay');

        document.getElementById('avatarToggleBtn').addEventListener('click', function(e) {
            e.stopPropagation();
            toggleMinimize();
        });

        widget.addEventListener('click', function(e) {
            if (e.target.closest('.avatar-toggle')) return;
            if (isMinimized) {
                toggleMinimize();
            }
        });

        connectAvatarWebSocket();
        loadInitialLevel();
    }

    function toggleMinimize() {
        isMinimized = !isMinimized;
        var widget = document.getElementById('avatarWidget');
        if (isMinimized) {
            widget.classList.add('avatar-minimized');
        } else {
            widget.classList.remove('avatar-minimized');
        }
        if (avatarInstance && avatarInstance.resize) {
            setTimeout(function() { avatarInstance.resize(); }, 350);
        }
    }

    function connectAvatarWebSocket() {
        var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        var wsUrl = protocol + '//' + window.location.host + '/ws/avatar';

        avatarWs = new WebSocket(wsUrl);

        avatarWs.onopen = function() {
            console.log('[Avatar] WebSocket connected');
        };

        avatarWs.onmessage = function(event) {
            var data = JSON.parse(event.data);
            handleAvatarEvent(data);
        };

        avatarWs.onclose = function() {
            console.log('[Avatar] WebSocket disconnected');
            reconnectTimer = setTimeout(function() {
                connectAvatarWebSocket();
            }, 5000);
        };

        avatarWs.onerror = function(error) {
            console.error('[Avatar] WebSocket error:', error);
        };
    }

    function handleAvatarEvent(data) {
        if (!data || !data.type) return;

        if (data.type === 'avatar_action') {
            handleAction(data);
        } else if (data.type === 'avatar_level') {
            handleLevelUp(data);
        }
    }

    function handleAction(data) {
        if (!avatarInstance) return;
        var action = data.action || 'idle';
        avatarInstance.setAction(action);

        if (actionLabel) {
            var labels = {
                idle: '',
                thinking: '思考中...',
                tool_executing: '执行工具中...',
                level_up: '升级了!',
                excited: '兴奋!',
                confused: '困惑中...',
                wave: '挥手打招呼~',
                sleep: 'zzZ...',
                typing: '输入中...',
                celebrate: '庆祝!'
            };
            actionLabel.textContent = labels[action] || '';
        }
    }

    function handleLevelUp(data) {
        if (!data.levelInfo) return;

        var info = data.levelInfo;
        updateLevelUI(info);

        if (avatarInstance) {
            avatarInstance.setAction('level_up');
        }

        if (levelUpOverlay) {
            levelUpOverlay.classList.remove('avatar-level-up-effect');
            void levelUpOverlay.offsetWidth;
            levelUpOverlay.classList.add('avatar-level-up-effect');
        }

        if (actionLabel) {
            actionLabel.textContent = '升级了! Lv.' + info.level + ' ' + info.title;
        }
    }

    function updateLevelUI(info) {
        if (levelText) {
            levelText.textContent = 'Lv.' + info.level;
        }
        if (titleText) {
            titleText.textContent = info.title;
        }
        if (xpFill) {
            xpFill.style.width = (info.levelProgressPercent || 0) + '%';
        }
    }

    function loadInitialLevel() {
        var userId = getCurrentUserId();
        if (!userId) return;

        fetch('/api/avatar/level?userId=' + encodeURIComponent(userId))
            .then(function(res) { return res.json(); })
            .then(function(info) {
                if (info && info.level !== undefined) {
                    updateLevelUI(info);
                }
            })
            .catch(function(err) {
                console.error('[Avatar] Failed to load level:', err);
            });
    }

    function getCurrentUserId() {
        if (typeof currentUserId !== 'undefined') return currentUserId;
        return 'user1';
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAvatarWidget);
    } else {
        initAvatarWidget();
    }
})();