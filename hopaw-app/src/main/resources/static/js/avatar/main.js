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
    IS_DRAGABLE: true,
    BUTTON_ID: "Change",
    TEXURE_BUTTON_ID: "texure",
    DRAG_THRESHOLD: 5,
    STORAGE_KEY: "hopaw_avatar_position",
    MINIMIZED_STORAGE_KEY: "hopaw_avatar_minimized",
    WS_URL: "/ws/avatar",
    INTIMACY_API: "/api/avatar/intimacy",
    MODELS_API: "/api/avatar/models/pool",
    BUBBLE_DEFAULT_DURATION: 3500,
    BUBBLE_LONG_DURATION: 6000
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
                        closeBtn.addEventListener("click", function (e) {
                            e.stopPropagation();
                            e.preventDefault();
                            api.hide();
                        });
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
                if (this.timer) {
                    clearTimeout(this.timer);
                    this.timer = null;
                }
            }
        };
        widget._avatarBubble = api;
        return api;
    }

    function connectAvatarWebSocket() {
        var protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        var wsUrl = protocol + "//" + window.location.host + LAppDefine.WS_URL;
        var attempts = 0;
        var ws = null;

        function open() {
            try {
                ws = new WebSocket(wsUrl);
            } catch (e) {
                console.error("Avatar WS init error:", e);
                scheduleReconnect();
                return;
            }
            ws.onopen = function () {
                attempts = 0;
                console.log("Avatar WS connected");
            };
            ws.onmessage = function (event) {
                try {
                    var data = JSON.parse(event.data);
                    handleAvatarEvent(data);
                } catch (e) {
                    console.error("Avatar WS message parse error:", e);
                }
            };
            ws.onclose = function () {
                console.log("Avatar WS closed");
                scheduleReconnect();
            };
            ws.onerror = function (err) {
                console.error("Avatar WS error:", err);
                try { ws.close(); } catch (_) {}
            };
        }

        function scheduleReconnect() {
            attempts++;
            var delay = Math.min(15000, 1000 * Math.pow(1.5, attempts));
            setTimeout(open, delay);
        }

        open();
    }

    function handleAvatarEvent(data) {
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
            var persistent = data.dismissible === true;
            bubble.showPersistent(proactiveText, persistent);
            return;
        }
        var text = data.message || data.actionDescription;
        if (!text) return;
        var duration = LAppDefine.BUBBLE_DEFAULT_DURATION;
        if (data.action === "level_up" || data.action === "celebrate") {
            duration = LAppDefine.BUBBLE_LONG_DURATION;
        }
        bubble.show(text, duration);
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

    function loadModel() {
        fetch(LAppDefine.MODELS_API, { credentials: 'same-origin' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                var pool = resp && resp.data && Array.isArray(resp.data.pool) ? resp.data.pool : [];
                if (!pool.length) {
                    console.warn('虚拟人模型池为空');
                    return;
                }
                var index = Math.floor(Math.random() * pool.length);
                var model = pool[index];
                loadlive2d(LAppDefine.CANVAS_ID, model);
            })
            .catch(function(e) {
                console.error('加载虚拟人模型池失败', e);
            });
    }
})();
