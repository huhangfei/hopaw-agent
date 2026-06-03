var LAppDefine = {
    CANVAS_ID: "avatarCanvas",
    WIDGET_ID: "avatarWidget",
    BUBBLE_ID: "avatarBubble",
    BUBBLE_CONTENT_ID: "avatarBubbleContent",
    INTIMACY_CARD_ID: "avatarIntimacyCard",
    INTIMACY_LEVEL_ID: "avatarIntimacyLevel",
    INTIMACY_TITLE_ID: "avatarIntimacyTitle",
    INTIMACY_PROGRESS_ID: "avatarIntimacyProgress",
    MINIMIZE_BTN_ID: "avatarMinimizeBtn",
    RESTORE_BTN_ID: "avatarRestoreBtn",
    SETTINGS_BTN_ID: "avatarSettingsBtn",
    CHANGE_MODEL_BTN_ID: "avatarChangeModelBtn",
    IS_DRAGABLE: true,
    BUTTON_ID: "Change",
    TEXURE_BUTTON_ID: "texure",
    DRAG_THRESHOLD: 5,
    STORAGE_KEY: "hopaw_avatar_position",
    MINIMIZED_STORAGE_KEY: "hopaw_avatar_minimized",
    WS_URL: "/ws/avatar",
    INTIMACY_API: "/api/avatar/intimacy",
    MODELS_API: "/api/avatar/models/pool",
    SETTINGS_API: "/api/avatar/settings",
    SOUND_BASE_PATH: "/sounds/avatar",
    SOUND_STORAGE_KEY: "hopaw_avatar_sound_enabled",
    CHANGE_MODEL_SOUND_FILE: "change_model.wav",
    BUBBLE_DEFAULT_DURATION: 3500,
    BUBBLE_LONG_DURATION: 6000,
    EVENT_QUEUE_INTERVAL_MS: 180,
    EVENT_QUEUE_MAX_SIZE: 50
};

(function initAvatarWidget() {
    var widget = document.getElementById(LAppDefine.WIDGET_ID);
    if (!widget) {
        loadModel();
        return;
    }

    initBubble(widget);
    initDrag(widget);
    initMinimize(widget);
    initIntimacy(widget);
    connectAvatarWebSocket();
    startAvatarEventQueue();
    syncSoundEnabledFromServer();
    var raf = window.requestAnimationFrame || function (cb) { return setTimeout(cb, 16); };
    raf(function () {
        applySavedState();
        loadModel();
    });

    function applySavedState() {
        var minimized = isMinimizedStored();
        if (minimized) {
            enterMinimized(true);
        } else if (LAppDefine.IS_DRAGABLE) {
            loadPosition();
        }
    }

    function isMinimizedStored() {
        try {
            return localStorage.getItem(LAppDefine.MINIMIZED_STORAGE_KEY) === "true";
        } catch (e) {
            return false;
        }
    }

    function setMinimizedStored(value) {
        try {
            if (value) {
                localStorage.setItem(LAppDefine.MINIMIZED_STORAGE_KEY, "true");
            } else {
                localStorage.removeItem(LAppDefine.MINIMIZED_STORAGE_KEY);
            }
        } catch (e) {}
    }

    function enterMinimized(skipTransition) {
        if (widget._cancelMove) widget._cancelMove();
        if (widget._avatarBubble) widget._avatarBubble.hide();
        if (widget._intimacy) widget._intimacy.hide();
        if (skipTransition) {
            var prev = widget.style.transition;
            widget.style.transition = "none";
            widget.classList.add("minimized");
            widget.style.left = "";
            widget.style.top = "";
            widget.style.right = "";
            widget.style.bottom = "";
            void widget.offsetWidth;
            widget.style.transition = prev;
        } else {
            widget.classList.add("minimized");
            widget.style.left = "";
            widget.style.top = "";
            widget.style.right = "";
            widget.style.bottom = "";
        }
        setMinimizedStored(true);
    }

    function exitMinimized() {
        widget.classList.remove("minimized");
        widget.style.left = "";
        widget.style.top = "";
        widget.style.right = "";
        widget.style.bottom = "";
        setMinimizedStored(false);
        if (LAppDefine.IS_DRAGABLE && widget._applyPosition) {
            loadPosition();
        }
    }

    function isMinimized() {
        return widget.classList.contains("minimized");
    }

    function initMinimize() {
        var minBtn = document.getElementById(LAppDefine.MINIMIZE_BTN_ID);
        var resBtn = document.getElementById(LAppDefine.RESTORE_BTN_ID);
        var settingsBtn = document.getElementById(LAppDefine.SETTINGS_BTN_ID);
        var changeModelBtn = document.getElementById(LAppDefine.CHANGE_MODEL_BTN_ID);
        if (minBtn) {
            minBtn.addEventListener("click", function (e) {
                e.stopPropagation();
                e.preventDefault();
                enterMinimized(false);
            });
            minBtn.addEventListener("pointerdown", function (e) {
                e.stopPropagation();
            });
        }
        if (resBtn) {
            resBtn.addEventListener("click", function (e) {
                e.stopPropagation();
                e.preventDefault();
                exitMinimized();
            });
            resBtn.addEventListener("pointerdown", function (e) {
                e.stopPropagation();
            });
        }
        if (settingsBtn) {
            settingsBtn.addEventListener("click", function (e) {
                e.stopPropagation();
                e.preventDefault();
                if (widget.classList.contains("dragging")) return;
                var agentId = (typeof currentAgentId !== "undefined") ? currentAgentId : null;
                if (!agentId) {
                    if (window.AvatarBridge && typeof window.AvatarBridge.getCurrentAgentId === "function") {
                        agentId = window.AvatarBridge.getCurrentAgentId();
                    }
                }
                if (!agentId) {
                    console.warn("未找到当前智能体 ID，无法打开虚拟人设置");
                    return;
                }
                if (window.AvatarSettings && typeof window.AvatarSettings.open === "function") {
                    window.AvatarSettings.open(agentId, "");
                } else if (typeof showAvatarSettingsModal === "function") {
                    showAvatarSettingsModal(agentId, "");
                } else {
                    console.warn("虚拟人设置弹窗脚本未加载");
                }
            });
            settingsBtn.addEventListener("pointerdown", function (e) {
                e.stopPropagation();
            });
        }
        if (changeModelBtn) {
            changeModelBtn.addEventListener("click", function (e) {
                e.stopPropagation();
                e.preventDefault();
                if (widget.classList.contains("dragging")) return;
                if (isMinimized()) return;
                loadModelFromPool({ excludeCurrent: true, random: true });
                playAvatarSound(LAppDefine.CHANGE_MODEL_SOUND_FILE);
            });
            changeModelBtn.addEventListener("pointerdown", function (e) {
                e.stopPropagation();
            });
        }
    }

    function initBubble() {
        var bubble = document.getElementById(LAppDefine.BUBBLE_ID);
        var content = document.getElementById(LAppDefine.BUBBLE_CONTENT_ID);
        if (!bubble || !content) return;
        var closeBtn = bubble.querySelector(".avatar-bubble-close");
        var api = {
            el: bubble,
            content: content,
            timer: null,
            show: function (text, duration) {
                if (!text) return;
                if (isMinimized()) return;
                this._render(text, false);
                bubble.classList.add("visible");
                if (this.timer) {
                    clearTimeout(this.timer);
                }
                var ms = typeof duration === "number" && duration > 0
                    ? duration
                    : LAppDefine.BUBBLE_DEFAULT_DURATION;
                this.timer = setTimeout(function () {
                    bubble.classList.remove("visible");
                    this.timer = null;
                }.bind(this), ms);
            },
            showPersistent: function (text, dismissible) {
                if (!text) return;
                if (isMinimized()) return;
                this._render(text, dismissible === true);
                bubble.classList.add("visible");
                if (this.timer) {
                    clearTimeout(this.timer);
                    this.timer = null;
                }
            },
            _render: function (text, dismissible) {
                content.textContent = text;
                if (dismissible) {
                    if (!closeBtn) {
                        closeBtn = document.createElement("button");
                        closeBtn.type = "button";
                        closeBtn.className = "avatar-bubble-close";
                        closeBtn.setAttribute("aria-label", "关闭");
                        closeBtn.innerHTML = "&times;";
                        bubble.appendChild(closeBtn);
                    }
                    closeBtn.style.display = "";
                    if (!closeBtn._avatarBound) {
                        var onClose = function (e) {
                            try { e.stopPropagation(); e.preventDefault(); } catch (_) {}
                            // 用户主动关闭：清空队列里尚未派发的主动消息，
                            // 并进入短暂冷却，避免后续事件再次弹出
                            dismissUserClosed();
                            api.hide();
                        };
                        // 同时监听 pointerdown / click，覆盖桌面和移动端，
                        // 避免 widget 上 touch-action:none 把 click 吞掉
                        closeBtn.addEventListener("pointerdown", onClose);
                        closeBtn.addEventListener("click", onClose);
                        // 拦截拖拽：关闭按钮按下时不进入拖动状态
                        closeBtn.addEventListener("pointerdown", function (e) {
                            try { e.stopPropagation(); } catch (_) {}
                        }, true);
                        closeBtn._avatarBound = true;
                    }
                    bubble.classList.add("dismissible");
                } else {
                    if (closeBtn) closeBtn.style.display = "none";
                    bubble.classList.remove("dismissible");
                }
            },
            hide: function () {
                bubble.classList.remove("visible");
                bubble.classList.remove("dismissible");
                // 关闭后清空文字，避免下次复用时短暂闪现旧内容
                content.textContent = "";
                if (this.timer) {
                    clearTimeout(this.timer);
                    this.timer = null;
                }
                if (closeBtn) closeBtn.style.display = "none";
            }
        };
        widget._avatarBubble = api;
        return api;
    }

    var currentAvatarWs = null;
    var currentAvatarWsReconnectTimer = null;

    function buildAvatarWsUrl() {
        var protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        var url = protocol + "//" + window.location.host + LAppDefine.WS_URL;
        var userId = getCurrentUserId();
        var agentId = getCurrentAgentId();
        var query = [];
        if (userId) query.push("userId=" + encodeURIComponent(userId));
        if (agentId !== null && agentId !== undefined && agentId !== "") {
            query.push("agentId=" + encodeURIComponent(agentId));
        }
        if (query.length) {
            url += "?" + query.join("&");
        }
        return url;
    }

    function connectAvatarWebSocket() {
        var attempts = 0;
        function open() {
            try {
                currentAvatarWs = new WebSocket(buildAvatarWsUrl());
            } catch (e) {
                console.error("Avatar WS init error:", e);
                scheduleReconnect();
                return;
            }
            currentAvatarWs.onopen = function () {
                attempts = 0;
                console.log("Avatar WS connected");
            };
            currentAvatarWs.onmessage = function (event) {
                try {
                    var data = JSON.parse(event.data);
                    enqueueAvatarEvent(data);
                } catch (e) {
                    console.error("Avatar WS message parse error:", e);
                }
            };
            currentAvatarWs.onclose = function () {
                console.log("Avatar WS closed");
                scheduleReconnect();
            };
            currentAvatarWs.onerror = function (err) {
                console.error("Avatar WS error:", err);
                try { currentAvatarWs.close(); } catch (_) {}
            };
        }

        function scheduleReconnect() {
            attempts++;
            var delay = Math.min(15000, 1000 * Math.pow(1.5, attempts));
            if (currentAvatarWsReconnectTimer) {
                clearTimeout(currentAvatarWsReconnectTimer);
            }
            currentAvatarWsReconnectTimer = setTimeout(open, delay);
        }

        open();
    }

    function reconnectAvatarWebSocket() {
        try {
            if (currentAvatarWs) {
                try { currentAvatarWs.onclose = null; } catch (_) {}
                try { currentAvatarWs.close(); } catch (_) {}
            }
            if (currentAvatarWsReconnectTimer) {
                clearTimeout(currentAvatarWsReconnectTimer);
                currentAvatarWsReconnectTimer = null;
            }
            attempts = 0;
            connectAvatarWebSocket();
        } catch (e) {
            console.warn("reconnectAvatarWebSocket error", e);
        }
    }

    function getCurrentUserId() {
        try {
            if (typeof currentUserId !== "undefined" && currentUserId) {
                return String(currentUserId);
            }
        } catch (e) {}
        var meta = document.querySelector('meta[name="current-user-id"]');
        if (meta && meta.content) return meta.content;
        return null;
    }

    function getCurrentAgentId() {
        try {
            if (typeof currentAgentId !== "undefined" && currentAgentId) {
                return String(currentAgentId);
            }
        } catch (e) {}
        var meta = document.querySelector('meta[name="current-agent-id"]');
        if (meta && meta.content) return meta.content;
        return null;
    }

    // 暴露给 index.js 在智能体切换时调用
    window.AvatarBridge = window.AvatarBridge || {};
    window.AvatarBridge.onAgentChanged = function (agentId) {
        try {
            currentAgentId = agentId;
        } catch (e) {}
        reconnectAvatarWebSocket();
        // 智能体切换后重新拉取该智能体配置的模型分组，并加载对应 Live2D 模型
        try {
            currentLoadedModel = null;
            loadModelFromPool({ excludeCurrent: false, random: false });
        } catch (e) {
            console.warn('切换虚拟人模型失败', e);
        }
        // 智能体切换后重新拉取该智能体的亲密度等信息
        try {
            if (widget && widget._intimacy && typeof widget._intimacy.refresh === 'function') {
                widget._intimacy.refresh();
            }
        } catch (e) {
            console.warn('刷新虚拟人亲密度失败', e);
        }
    };

    function processAvatarEvent(data) {
        if (!data) return;
        if (isMinimized()) return;
        var bubble = widget._avatarBubble;
        if (!bubble) return;
        if (data.userId && currentUserId && data.userId !== currentUserId) {
            return;
        }
        if (data.type === "avatar_intimacy" || data.action === "intimacy_up") {
            if (data.intimacyInfo) {
                widget._intimacy && widget._intimacy.apply(data.intimacyInfo);
            }
            var text = data.message;
            if (text) {
                bubble.show(text, LAppDefine.BUBBLE_LONG_DURATION);
            }
            playAvatarSound(data.soundFile);
            return;
        }
        if (data.type === "avatar_intimacy_update" || data.action === "intimacy_update") {
            if (data.intimacyInfo) {
                widget._intimacy && widget._intimacy.apply(data.intimacyInfo);
            }
            return;
        }
        if (data.type === "avatar_proactive_message" || data.action === "proactive_message") {
            var proactiveText = data.message;
            if (!proactiveText) return;
            // 用户主动关闭弹窗后冷却期内不再弹
            if (isInDismissCooldown()) {
                return;
            }
            var persistent = data.dismissible === true;
            bubble.showPersistent(proactiveText, persistent);
            playAvatarSound(data.soundFile);
            return;
        }
        if (data.type === "avatar_move" || data.action === "move") {
            if (isMinimized()) return;
            var deltaX = typeof data.targetX === "number" ? data.targetX : 0;
            var deltaY = typeof data.targetY === "number" ? data.targetY : 0;
            var duration = typeof data.durationMs === "number" && data.durationMs > 0 ? data.durationMs : 1000;
            animateAvatarMove(deltaX, deltaY, duration);
            playAvatarSound(data.soundFile);
            return;
        }
        if (data.type === "avatar_change_model" || data.action === "change_model") {
            if (isMinimized()) return;
            loadModelFromPool({ excludeCurrent: true, random: true });
            playAvatarSound(data.soundFile);
            return;
        }
        var text = data.message || data.actionDescription;
        if (!text) return;
        var duration = LAppDefine.BUBBLE_DEFAULT_DURATION;
        if (data.action === "level_up" || data.action === "celebrate") {
            duration = LAppDefine.BUBBLE_LONG_DURATION;
        }
        bubble.show(text, duration);
        playAvatarSound(data.soundFile);
    }

    var avatarEventQueue = [];
    var avatarEventQueueTimer = null;
    var avatarEventQueueProcessing = false;

    // 用户主动关闭弹窗后，短时间内忽略新主动消息，避免又被刷出来
    var dismissCooldownMs = 0;
    var dismissCooldownTimer = null;

    function isInDismissCooldown() {
        return dismissCooldownMs > 0;
    }

    // 用户主动关闭弹窗时调用：清空尚未派发的事件，
    // 并开启一个冷却期，期间不再显示新的主动消息
    function dismissUserClosed() {
        avatarEventQueue.length = 0;
        dismissCooldownMs = 3000;
        if (dismissCooldownTimer) {
            clearTimeout(dismissCooldownTimer);
        }
        dismissCooldownTimer = setTimeout(function () {
            dismissCooldownMs = 0;
            dismissCooldownTimer = null;
        }, 3000);
    }

    function enqueueAvatarEvent(data) {
        if (!data) return;
        if (!widget) {
            return;
        }
        if (isMinimized()) {
            return;
        }
        if (data.userId && currentUserId && data.userId !== currentUserId) {
            return;
        }
        var maxSize = LAppDefine.EVENT_QUEUE_MAX_SIZE || 50;
        if (avatarEventQueue.length >= maxSize) {
            avatarEventQueue.shift();
        }
        avatarEventQueue.push(data);
    }

    function startAvatarEventQueue() {
        if (avatarEventQueueTimer) return;
        avatarEventQueueTimer = setInterval(function () {
            if (avatarEventQueueProcessing) return;
            if (!avatarEventQueue.length) return;
            if (isMinimized()) {
                avatarEventQueue.length = 0;
                return;
            }
            var data = avatarEventQueue.shift();
            avatarEventQueueProcessing = true;
            try {
                processAvatarEvent(data);
            } catch (e) {
                console.error("Avatar event process error:", e);
            } finally {
                avatarEventQueueProcessing = false;
            }
        }, LAppDefine.EVENT_QUEUE_INTERVAL_MS || 180);
    }

    function stopAvatarEventQueue() {
        if (avatarEventQueueTimer) {
            clearInterval(avatarEventQueueTimer);
            avatarEventQueueTimer = null;
        }
        avatarEventQueue.length = 0;
    }

    var moveAnimationId = null;
    var moveSavedTransition = null;
    var moveAnimationRaf = window.requestAnimationFrame || function (cb) { return setTimeout(cb, 16); };

    var avatarAudioCache = {};
    var avatarSoundEnabled = readSoundEnabledFromStorage();

    function readSoundEnabledFromStorage() {
        try {
            var v = localStorage.getItem(LAppDefine.SOUND_STORAGE_KEY);
            if (v === "false") return false;
        } catch (e) {}
        return true;
    }

    function writeSoundEnabledToStorage(enabled) {
        try {
            localStorage.setItem(LAppDefine.SOUND_STORAGE_KEY, enabled ? "true" : "false");
        } catch (e) {}
    }

    function isAvatarSoundEnabled() {
        return avatarSoundEnabled !== false;
    }

    function setAvatarSoundEnabled(enabled) {
        avatarSoundEnabled = enabled !== false;
        writeSoundEnabledToStorage(avatarSoundEnabled);
    }

    function syncSoundEnabledFromServer() {
        try {
            var userId = getCurrentUserId();
            var agentId = getCurrentAgentId();
            var url = LAppDefine.SETTINGS_API;
            var qs = [];
            if (agentId !== null && agentId !== undefined && agentId !== "") {
                qs.push("agentId=" + encodeURIComponent(agentId));
            }
            if (userId) {
                qs.push("_t=" + Date.now());
            }
            if (qs.length) {
                url += "?" + qs.join("&");
            }
            fetch(url, { credentials: "same-origin" })
                .then(function (r) { return r.json(); })
                .then(function (resp) {
                    if (!resp || resp.msg !== "success" || !resp.data) return;
                    if (typeof resp.data.soundEnabled === "boolean") {
                        setAvatarSoundEnabled(resp.data.soundEnabled);
                    }
                })
                .catch(function () {});
        } catch (e) {}
    }

    function playAvatarSound(soundFile) {
        if (!isAvatarSoundEnabled()) return;
        if (!soundFile) return;
        var name = String(soundFile).split(/[\\/]/).pop();
        if (!name) return;
        var url = LAppDefine.SOUND_BASE_PATH + "/" + name;
        try {
            var audio = avatarAudioCache[url];
            if (!audio) {
                audio = new Audio(url);
                audio.preload = "auto";
                avatarAudioCache[url] = audio;
            }
            try { audio.currentTime = 0; } catch (e) {}
            var playPromise = audio.play();
            if (playPromise && typeof playPromise.catch === "function") {
                playPromise.catch(function (err) {
                    console.warn("Avatar sound play failed:", err);
                });
            }
        } catch (e) {
            console.warn("Avatar sound error:", e);
        }
    }

    function clampAvatarPosition(x, y) {
        var rect = widget.getBoundingClientRect();
        var canvas = document.getElementById(LAppDefine.CANVAS_ID);
        var w = rect.width || (canvas ? canvas.offsetWidth : 250);
        var h = rect.height || (canvas ? canvas.offsetHeight : 250);
        var maxX = Math.max(0, window.innerWidth - w);
        var maxY = Math.max(0, window.innerHeight - h);
        return {
            x: Math.min(Math.max(0, x), maxX),
            y: Math.min(Math.max(0, y), maxY)
        };
    }

    function setAvatarPosition(x, y) {
        var clamped = clampAvatarPosition(x, y);
        if (widget._applyPosition) {
            widget._applyPosition(clamped.x, clamped.y);
        } else {
            widget.style.left = clamped.x + "px";
            widget.style.top = clamped.y + "px";
            widget.style.right = "auto";
            widget.style.bottom = "auto";
        }
    }

    function cancelMoveAnimation() {
        if (moveAnimationId !== null) {
            cancelAnimationFrame(moveAnimationId);
            moveAnimationId = null;
        }
        if (moveSavedTransition !== null) {
            widget.style.transition = moveSavedTransition;
            moveSavedTransition = null;
        }
    }

    widget._cancelMove = cancelMoveAnimation;

    function animateAvatarMove(deltaX, deltaY, duration) {
        if (!widget) return;
        if (isMinimized()) return;
        cancelMoveAnimation();

        // 预先把终点夹紧到视口，避免动画跑出屏幕
        var rect = widget.getBoundingClientRect();
        var startX = rect.left;
        var startY = rect.top;
        var endClamped = clampAvatarPosition(startX + deltaX, startY + deltaY);
        var endX = endClamped.x;
        var endY = endClamped.y;
        var effectiveDeltaX = endX - startX;
        var effectiveDeltaY = endY - startY;

        // 临时禁用 CSS 过渡（widget 默认有 left/top 0.3s 过渡），避免与 rAF 冲突造成抖动
        moveSavedTransition = widget.style.transition;
        widget.style.transition = "none";

        var startTime = null;
        function step(ts) {
            if (startTime === null) startTime = ts;
            var elapsed = ts - startTime;
            var ratio = duration > 0 ? Math.min(1, elapsed / duration) : 1;
            var eased = 1 - (1 - ratio) * (1 - ratio);
            setAvatarPosition(startX + effectiveDeltaX * eased, startY + effectiveDeltaY * eased);
            if (ratio < 1) {
                moveAnimationId = moveAnimationRaf(step);
            } else {
                moveAnimationId = null;
                if (moveSavedTransition !== null) {
                    widget.style.transition = moveSavedTransition;
                    moveSavedTransition = null;
                }
                if (widget._savePosition) widget._savePosition();
            }
        }
        moveAnimationId = moveAnimationRaf(step);
    }

    function initIntimacy() {
        var card = document.getElementById(LAppDefine.INTIMACY_CARD_ID);
        if (!card) return;
        var levelEl = document.getElementById(LAppDefine.INTIMACY_LEVEL_ID);
        var titleEl = document.getElementById(LAppDefine.INTIMACY_TITLE_ID);
        var progressEl = document.getElementById(LAppDefine.INTIMACY_PROGRESS_ID);
        var hideTimer = null;
        var cached = null;

        function apply(info) {
            if (!info) return;
            cached = info;
            if (levelEl) levelEl.textContent = info.intimacyLevel != null ? info.intimacyLevel : 0;
            if (titleEl) titleEl.textContent = info.title || "";
            if (progressEl) {
                var percent = info.progressPercent;
                if (typeof percent !== "number" || isNaN(percent)) percent = 0;
                if (percent < 0) percent = 0;
                if (percent > 100) percent = 100;
                progressEl.style.width = percent + "%";
            }
        }

        function show() {
            if (isMinimized()) return;
            if (hideTimer) {
                clearTimeout(hideTimer);
                hideTimer = null;
            }
            card.classList.add("visible");
        }

        function hide() {
            if (hideTimer) {
                clearTimeout(hideTimer);
                hideTimer = null;
            }
            card.classList.remove("visible");
        }

        function load() {
            var userId = getCurrentUserId();
            if (!userId) return;
            var url = LAppDefine.INTIMACY_API + "?userId=" + encodeURIComponent(userId) + "&_t=" + Date.now();
            var agentId = getCurrentAgentId();
            if (agentId) {
                url += "&agentId=" + encodeURIComponent(agentId);
            }
            fetch(url, { credentials: "same-origin" })
                .then(function (resp) {
                    if (!resp.ok) throw new Error("intimacy api status " + resp.status);
                    return resp.json();
                })
                .then(function (data) {
                    if (data) apply(data);
                })
                .catch(function (e) {
                    console.warn("Fetch intimacy info failed:", e);
                });
        }

        widget._intimacy = {
            apply: apply,
            show: show,
            hide: hide,
            refresh: load,
            el: card,
            getCached: function () { return cached; }
        };

        widget.addEventListener("mouseenter", show);
        widget.addEventListener("mouseleave", function () {
            if (hideTimer) clearTimeout(hideTimer);
            hideTimer = setTimeout(hide, 120);
        });
        card.addEventListener("mouseenter", function () {
            if (hideTimer) {
                clearTimeout(hideTimer);
                hideTimer = null;
            }
        });
        card.addEventListener("mouseleave", function () {
            hide();
        });

        load();
    }

    function getCurrentUserId() {
        try {
            if (typeof currentUserId !== "undefined" && currentUserId) {
                return String(currentUserId);
            }
        } catch (e) {}
        var meta = document.querySelector('meta[name="current-user-id"]');
        if (meta && meta.content) return meta.content;
        return null;
    }

    function initDrag() {
        if (!LAppDefine.IS_DRAGABLE) return;
        var canvas = document.getElementById(LAppDefine.CANVAS_ID);
        var offsetX = 0;
        var offsetY = 0;
        var startX = 0;
        var startY = 0;
        var pointerId = null;
        var isDragging = false;
        var isMoved = false;

        function applyPosition(x, y) {
            var rect = widget.getBoundingClientRect();
            var w = rect.width || (canvas ? canvas.offsetWidth : 250);
            var h = rect.height || (canvas ? canvas.offsetHeight : 250);
            var maxX = Math.max(0, window.innerWidth - w);
            var maxY = Math.max(0, window.innerHeight - h);
            var clampedX = Math.min(Math.max(0, x), maxX);
            var clampedY = Math.min(Math.max(0, y), maxY);
            widget.style.left = clampedX + "px";
            widget.style.top = clampedY + "px";
            widget.style.right = "auto";
            widget.style.bottom = "auto";
        }

        widget._applyPosition = applyPosition;
        widget._savePosition = function () {
            try {
                var rect = widget.getBoundingClientRect();
                localStorage.setItem(
                    LAppDefine.STORAGE_KEY,
                    JSON.stringify({ x: Math.round(rect.left), y: Math.round(rect.top) })
                );
            } catch (e) {}
        };

        function getPointerXY(e) {
            if (e.touches && e.touches.length > 0) {
                return { x: e.touches[0].clientX, y: e.touches[0].clientY };
            }
            return { x: e.clientX, y: e.clientY };
        }

        function onPointerDown(e) {
            if (isMinimized()) return;
            if (e.button !== undefined && e.button !== 0) return;
            if (e.target.closest && e.target.closest(".avatar-minimize-btn")) {
                return;
            }
            if (e.target.closest && e.target.closest("#" + LAppDefine.SETTINGS_BTN_ID)) {
                return;
            }
            if (e.target.closest && e.target.closest("#" + LAppDefine.CHANGE_MODEL_BTN_ID)) {
                return;
            }
            var pt = getPointerXY(e);
            pointerId = e.pointerId !== undefined ? e.pointerId : null;
            startX = pt.x;
            startY = pt.y;
            var rect = widget.getBoundingClientRect();
            offsetX = pt.x - rect.left;
            offsetY = pt.y - rect.top;
            isDragging = true;
            isMoved = false;
            if (widget.setPointerCapture && pointerId !== null) {
                try { widget.setPointerCapture(pointerId); } catch (_) {}
            }
            widget.style.cursor = "grabbing";
            widget.classList.add("dragging");
        }

        function onPointerMove(e) {
            if (!isDragging) return;
            var pt = getPointerXY(e);
            var dx = pt.x - startX;
            var dy = pt.y - startY;
            if (!isMoved) {
                if (Math.abs(dx) < LAppDefine.DRAG_THRESHOLD && Math.abs(dy) < LAppDefine.DRAG_THRESHOLD) {
                    return;
                }
                isMoved = true;
            }
            if (e.cancelable) e.preventDefault();
            applyPosition(pt.x - offsetX, pt.y - offsetY);
        }

        function onPointerUp() {
            if (!isDragging) return;
            isDragging = false;
            widget.style.cursor = "grab";
            widget.classList.remove("dragging");
            if (pointerId !== null && widget.releasePointerCapture) {
                try { widget.releasePointerCapture(pointerId); } catch (_) {}
            }
            pointerId = null;
            if (isMoved) {
                widget._savePosition();
            }
        }

        widget.style.cursor = "grab";
        widget.style.touchAction = "none";

        if (window.PointerEvent) {
            widget.addEventListener("pointerdown", onPointerDown);
            widget.addEventListener("pointermove", onPointerMove);
            widget.addEventListener("pointerup", onPointerUp);
            widget.addEventListener("pointercancel", onPointerUp);
        } else {
            widget.addEventListener("mousedown", onPointerDown);
            widget.addEventListener("touchstart", onPointerDown, { passive: true });
            widget.addEventListener("touchmove", onPointerMove, { passive: false });
            widget.addEventListener("touchend", onPointerUp);
            widget.addEventListener("touchcancel", onPointerUp);
            window.addEventListener("mousemove", onPointerMove);
            window.addEventListener("mouseup", onPointerUp);
        }

        if (canvas) {
            canvas.addEventListener("click", function (e) {
                if (isMoved) {
                    e.stopPropagation();
                    e.preventDefault();
                    isMoved = false;
                }
            }, true);
        }

        window.addEventListener("resize", function () {
            if (isMinimized()) return;
            var rect = widget.getBoundingClientRect();
            applyPosition(rect.left, rect.top);
            try {
                localStorage.setItem(
                    LAppDefine.STORAGE_KEY,
                    JSON.stringify({ x: Math.round(rect.left), y: Math.round(rect.top) })
                );
            } catch (e) {}
        });
    }

    function loadPosition() {
        try {
            var raw = localStorage.getItem(LAppDefine.STORAGE_KEY);
            if (!raw || !widget._applyPosition) return;
            var pos = JSON.parse(raw);
            if (typeof pos.x === "number" && typeof pos.y === "number") {
                widget._applyPosition(pos.x, pos.y);
            }
        } catch (e) {}
    }

    function buildModelsApiUrl() {
        var url = LAppDefine.MODELS_API;
        var qs = [];
        var agentId = getCurrentAgentId();
        if (agentId !== null && agentId !== undefined && agentId !== "") {
            qs.push("agentId=" + encodeURIComponent(agentId));
        }
        qs.push("_t=" + Date.now());
        return url + "?" + qs.join("&");
    }

    function loadModel() {
        loadModelFromPool({ excludeCurrent: false, random: false });
    }

    function loadModelFromPool(options) {
        var opts = options || {};
        var excludeCurrent = opts.excludeCurrent === true;
        var random = opts.random === true;
        fetch(buildModelsApiUrl(), { credentials: 'same-origin' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                var pool = resp && resp.data && Array.isArray(resp.data.pool) ? resp.data.pool : [];
                var selectedGroup = resp && resp.data ? resp.data.selected : '';
                if (!pool.length) {
                    console.warn('虚拟人模型池为空');
                    return;
                }
                avatarSelectedGroup = selectedGroup || '';
                var candidates = pool;
                if (excludeCurrent && currentLoadedModel && pool.length > 1) {
                    candidates = pool.filter(function(m) { return m !== currentLoadedModel; });
                    if (!candidates.length) {
                        candidates = pool;
                    }
                }
                var model;
                if (random) {
                    var index = Math.floor(Math.random() * candidates.length);
                    model = candidates[index];
                } else {
                    model = candidates[0];
                }
                currentLoadedModel = model;
                loadlive2d(LAppDefine.CANVAS_ID, model);
            })
            .catch(function(e) {
                console.error('加载虚拟人模型池失败', e);
            });
    }

    var currentLoadedModel = null;
    var avatarSelectedGroup = '';
})();
