/**
 * 画布工具插件前端逻辑。
 *
 * 通过 PluginHook 监听后端 @Tool 下发的指令：
 *   - action=start  收缩会话区、新建并排画布
 *   - action=draw   在画布上实时绘制（payload 为 JSON 图形描述）
 *   - action=finish 还原布局、回传 canvas.toDataURL() 结果
 */
(function () {
    'use strict';

    var board = null;
    var ctx = null;
    var statusEl = null;
    var active = false;

    function findEls() {
        board = document.querySelector('.plugin-canvas-board');
        statusEl = document.querySelector('[data-canvas-status]');
    }

    function setStatus(text, isActive) {
        if (statusEl) {
            statusEl.textContent = text;
            statusEl.classList.toggle('active', !!isActive);
        }
    }

    function shrinkChatArea() {
        var wrapper = document.querySelector('.chat-wrapper');
        if (wrapper) wrapper.classList.add('canvas-active');
        active = true;
        setStatus('绘制中', true);
    }

    function restoreLayout() {
        var wrapper = document.querySelector('.chat-wrapper');
        if (wrapper) wrapper.classList.remove('canvas-active');
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
            case 'text':
                ctx.fillStyle = cmd.color || '#333';
                ctx.font = (cmd.size || 16) + 'px sans-serif';
                ctx.fillText(cmd.text || '', cmd.x || 0, cmd.y || 0);
                break;
            default:
                console.warn('[canvas-plugin] unknown draw command:', cmd.type);
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

    function init() {
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
