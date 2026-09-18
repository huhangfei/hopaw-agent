/**
 * 画布工具插件前端逻辑。
 *
 * 通过 PluginHook 监听后端 @Tool 下发的指令：
 *   - action=start  收缩会话区、新建并排画布（侧边栏收缩为迷你条）
 *   - action=draw   在画布上实时绘制（payload 为 JSON 图形描述）
 *   - action=finish 还原布局、回传 canvas.toDataURL() 结果
 *
 * 关闭按钮：点击后隐藏画布、还原聊天区，并恢复侧边栏状态。
 *   若画布期间用户手动切换过侧边栏，则保持用户当前状态；否则恢复到画布开始前的状态。
 */
(function () {
    'use strict';

    var board = null;
    var ctx = null;
    var statusEl = null;
    var closeBtn = null;
    var active = false;
    // 画布打开时侧边栏是否为迷你态（用于关闭时恢复）
    var panelMiniAtStart = false;
    // 画布强制设置的侧边栏状态（激活时始终收缩为迷你条）
    var panelMiniForced = null;

    function findEls() {
        board = document.querySelector('.plugin-canvas-board');
        statusEl = document.querySelector('[data-canvas-status]');
        closeBtn = document.querySelector('[data-canvas-close]');
    }

    function setStatus(text, isActive) {
        if (statusEl) {
            statusEl.textContent = text;
            statusEl.classList.toggle('active', !!isActive);
        }
    }

    function isToolPanelMini() {
        var panel = document.getElementById('toolPanel');
        return !!(panel && panel.classList.contains('tool-panel-mini'));
    }

    function shrinkChatArea() {
        var wrapper = document.querySelector('.chat-wrapper');
        if (wrapper) wrapper.classList.add('canvas-active');

        // 记录开始前侧边栏状态，并收缩为迷你条
        panelMiniAtStart = isToolPanelMini();
        if (typeof window.toggleToolPanelMini === 'function') {
            window.toggleToolPanelMini(true);
        }
        panelMiniForced = true;

        active = true;
        setStatus('绘制中', true);
    }

    function restoreLayout() {
        var wrapper = document.querySelector('.chat-wrapper');
        if (wrapper) wrapper.classList.remove('canvas-active');

        // 恢复侧边栏：若画布期间用户切换过侧边栏，则保持当前状态；否则恢复到开始前状态
        var currentMini = isToolPanelMini();
        if (panelMiniForced !== null && currentMini === panelMiniForced) {
            // 侧边栏仍处于画布强制状态（用户未切换）→ 恢复到开始前状态
            if (typeof window.toggleToolPanelMini === 'function') {
                window.toggleToolPanelMini(panelMiniAtStart);
            }
        }
        panelMiniForced = null;

        active = false;
        setStatus('待命', false);
    }

    function initCanvas() {
        findEls();
        if (!board) {
            console.warn('[canvas-plugin] canvas board not found');
            return;
        }
        if (!ctx) {
            ctx = board.getContext('2d');
        }
        ctx.clearRect(0, 0, board.width, board.height);
    }

    function renderCommand(cmdStr) {
        if (!ctx) return;
        var cmd;
        try { cmd = JSON.parse(cmdStr); } catch (e) { return; }

        switch (cmd.type) {
            case 'clear':
                ctx.clearRect(0, 0, board.width, board.height);
                break;
            case 'rect':
                ctx.fillStyle = cmd.color || '#4f66d8';
                ctx.fillRect(cmd.x || 0, cmd.y || 0, cmd.w || 50, cmd.h || 50);
                break;
            case 'circle':
                ctx.beginPath();
                ctx.fillStyle = cmd.color || '#4f66d8';
                ctx.arc(cmd.x || 0, cmd.y || 0, cmd.r || 20, 0, Math.PI * 2);
                ctx.fill();
                break;
            case 'line':
                ctx.beginPath();
                ctx.strokeStyle = cmd.color || '#333';
                ctx.lineWidth = cmd.w || 2;
                ctx.moveTo(cmd.x1 || 0, cmd.y1 || 0);
                ctx.lineTo(cmd.x2 || 0, cmd.y2 || 0);
                ctx.stroke();
                break;
            case 'polygon':
                drawPolygon(cmd);
                break;
            case 'text':
                ctx.fillStyle = cmd.color || '#333';
                ctx.font = (cmd.size || 16) + 'px sans-serif';
                ctx.fillText(cmd.text || '', cmd.x || 0, cmd.y || 0);
                break;
            default:
                console.warn('[canvas-plugin] unknown draw command:', cmd.type);
        }
    }

    function drawPolygon(cmd) {
        var points = cmd.points;
        if (!Array.isArray(points) || points.length < 3) return;
        ctx.beginPath();
        ctx.moveTo(points[0][0], points[0][1]);
        for (var i = 1; i < points.length; i++) {
            ctx.lineTo(points[i][0], points[i][1]);
        }
        ctx.closePath();
        if (cmd.fill !== false) {
            ctx.fillStyle = cmd.color || '#4f66d8';
            ctx.fill();
        }
        if (cmd.stroke) {
            ctx.strokeStyle = cmd.strokeColor || '#333';
            ctx.lineWidth = cmd.lineWidth || 2;
            ctx.stroke();
        }
    }

    function handleCommand(cmd) {
        if (!cmd || cmd.toolName !== 'canvas') return;

        if (cmd.action === 'start') {
            initCanvas();
            shrinkChatArea();
        } else if (cmd.action === 'draw') {
            if (!active) { initCanvas(); shrinkChatArea(); }
            renderCommand(cmd.payload);
        } else if (cmd.action === 'finish') {
            if (!active) { initCanvas(); shrinkChatArea(); }
            var result = board ? board.toDataURL('image/png') : '';
            restoreLayout();
            if (window.PluginHook && cmd.requestId) {
                window.PluginHook.report(cmd.requestId, result);
            }
        }
    }

    function closeCanvas() {
        if (!active) return;
        // 关闭时无需回传结果，仅还原布局与侧边栏
        restoreLayout();
    }

    function bindClose() {
        if (!closeBtn) return;
        closeBtn.addEventListener('click', closeCanvas);
    }

    function init() {
        findEls();
        bindClose();
        if (window.PluginHook) {
            window.PluginHook.onCommand(handleCommand);
        }
    }

    if (window.PluginLoader) {
        window.PluginLoader.onReady(init);
    } else {
        init();
    }
})();
