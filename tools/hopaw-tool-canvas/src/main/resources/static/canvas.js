/**
 * 画布工具插件前端逻辑。
 *
 * 通过 PluginHook 监听后端 @Tool 下发的指令：
 *   - action=start    收缩会话区、新建并排画布（侧边栏收缩为迷你条）
 *   - action=draw     在画布上实时绘制（payload 为 JSON 图形描述）
 *   - action=snapshot 获取当前画布结果图（回传 dataURL，不关闭插件）
 *   - action=close    结束会话、关闭插件（还原布局，不回传结果）
 *
 * 头部按钮：
 *   - 下载：下载当前画布图片（合成白底）
 *   - 关闭：隐藏画布、还原聊天区，并恢复侧边栏状态。
 *     若画布期间用户手动切换过侧边栏，则保持用户当前状态；否则恢复到画布开始前的状态。
 *
 * 画笔工具条：常驻显示在画布右侧竖排，画笔默认选中，可直接自由绘制。
 *
 * 尺寸策略：canvas 像素缓冲固定（800x800），不随面板缩放重建；显示尺寸由 CSS
 * 居中放置，容器不足时等比缩小（pointerPos 按缩放比例换算坐标），内容不丢。
 */
(function () {
    'use strict';

    var board = null;
    var ctx = null;
    var statusEl = null;
    var closeBtn = null;
    var downloadBtn = null;
    var penBtn = null;
    var penbar = null;
    var active = false;
    // 画布打开时侧边栏是否为迷你态（用于关闭时恢复）
    var panelMiniAtStart = false;
    // 画布强制设置的侧边栏状态（激活时始终收缩为迷你条）
    var panelMiniForced = null;

    // ---------- 画笔状态 ----------
    var penEnabled = true; // 画笔默认选中，可直接自由绘制
    var penColor = '#1f2328';
    var penWidth = 3;
    var drawing = null; // 正在绘制的笔画 {color,width,points:[{x,y}]}
    // 操作记录（供撤销与尺寸变化重绘）：
    //   {type:'stroke', color, width, points:[{x,y}...]} —— 用户画笔笔画
    //   {type:'cmd', cmd:{...}}                          —— LLM 下发的绘制命令
    var strokes = [];
    var els = {}; // 画笔工具条元素引用

    function findEls() {
        board = document.querySelector('.plugin-canvas-board');
        statusEl = document.querySelector('[data-canvas-status]');
        closeBtn = document.querySelector('[data-canvas-close]');
        downloadBtn = document.querySelector('[data-canvas-download]');
        penBtn = document.querySelector('[data-canvas-pen]');
        penbar = document.querySelector('[data-canvas-penbar]');
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
        strokes.length = 0;
        drawing = null;
        redrawAll();
    }

    /** 导出当前画布（合成白底，避免透明底在暗色主题/查看器中看不清） */
    function snapshotDataUrl() {
        if (!board) return '';
        var out = document.createElement('canvas');
        out.width = board.width;
        out.height = board.height;
        var octx = out.getContext('2d');
        if (octx) {
            octx.fillStyle = '#ffffff';
            octx.fillRect(0, 0, out.width, out.height);
            octx.drawImage(board, 0, 0);
        }
        return out.toDataURL('image/png');
    }

    /* ========== 画布尺寸（固定缓冲，居中显示） ========== */

    /** 全量重绘：清屏后依 strokes 顺序回放（画笔笔画 + LLM 命令） */
    function redrawAll() {
        if (!ctx) return;
        ctx.clearRect(0, 0, board.width, board.height);
        for (var i = 0; i < strokes.length; i++) {
            var s = strokes[i];
            if (s.type === 'stroke') {
                drawStroke(s);
            } else if (s.type === 'cmd' && s.cmd) {
                renderCommand(s.cmd);
            }
        }
    }

    /* ========== LLM 绘制命令 ========== */

    function renderCommand(cmd) {
        if (!ctx) return;
        switch (cmd.type) {
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

    /* ========== 画笔自由绘制 ========== */

    /** 事件坐标 → 画布缓冲坐标（显示尺寸被 CSS 缩小时按比例换算） */
    function pointerPos(e) {
        var rect = board.getBoundingClientRect();
        var sx = rect.width ? board.width / rect.width : 1;
        var sy = rect.height ? board.height / rect.height : 1;
        return {
            x: Math.round((e.clientX - rect.left) * sx),
            y: Math.round((e.clientY - rect.top) * sy)
        };
    }

    /** 画一段笔画（整段重绘或增量段）；单点时画圆点保证点按可见 */
    function drawStroke(stroke) {
        if (!ctx) return;
        var pts = stroke.points;
        if (!pts || !pts.length) return;
        ctx.strokeStyle = stroke.color;
        ctx.lineWidth = stroke.width;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        if (pts.length === 1) {
            ctx.beginPath();
            ctx.fillStyle = stroke.color;
            ctx.arc(pts[0].x, pts[0].y, stroke.width / 2, 0, Math.PI * 2);
            ctx.fill();
            return;
        }
        ctx.beginPath();
        ctx.moveTo(pts[0].x, pts[0].y);
        for (var i = 1; i < pts.length; i++) {
            ctx.lineTo(pts[i].x, pts[i].y);
        }
        ctx.stroke();
    }

    /** 增量画最后两点之间的线段（绘制中实时反馈） */
    function drawLastSegment(stroke) {
        var pts = stroke.points;
        if (!ctx || pts.length < 2) return;
        ctx.strokeStyle = stroke.color;
        ctx.lineWidth = stroke.width;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.beginPath();
        ctx.moveTo(pts[pts.length - 2].x, pts[pts.length - 2].y);
        ctx.lineTo(pts[pts.length - 1].x, pts[pts.length - 1].y);
        ctx.stroke();
    }

    function onBoardPointerDown(e) {
        if (!penEnabled || !ctx || !board) return;
        if (e.button !== undefined && e.button !== 0) return;
        e.preventDefault();
        var pos = pointerPos(e);
        drawing = { type: 'stroke', color: penColor, width: penWidth, points: [pos] };
        strokes.push(drawing);
        drawStroke(drawing); // 单点立即可见
        try { board.setPointerCapture(e.pointerId); } catch (_) {}
    }

    function onBoardPointerMove(e) {
        if (!drawing || !board) return;
        var pos = pointerPos(e);
        var last = drawing.points[drawing.points.length - 1];
        if (last && last.x === pos.x && last.y === pos.y) return;
        drawing.points.push(pos);
        drawLastSegment(drawing);
    }

    function onBoardPointerUp() {
        drawing = null;
    }

    function setPenEnabled(enabled) {
        penEnabled = !!enabled;
        if (penBtn) penBtn.classList.toggle('active', penEnabled);
        if (board) board.classList.toggle('pen-mode', penEnabled);
    }

    function selectColor(color, swatchEl) {
        penColor = color;
        if (els.colorInput) els.colorInput.value = color;
        var swatches = penbar ? penbar.querySelectorAll('[data-swatch]') : [];
        for (var i = 0; i < swatches.length; i++) {
            swatches[i].classList.toggle('active', swatches[i] === swatchEl);
        }
    }

    function bindPenToolbar() {
        if (!penBtn || !penbar) return;
        els.colorInput = penbar.querySelector('[data-canvas-color]');
        els.widthRange = penbar.querySelector('[data-canvas-width]');
        els.widthValue = penbar.querySelector('[data-canvas-width-value]');
        var swatches = penbar.querySelectorAll('[data-swatch]');
        var undoBtn = penbar.querySelector('[data-canvas-undo]');
        var clearBtn = penbar.querySelector('[data-canvas-clear]');

        penBtn.addEventListener('click', function () {
            setPenEnabled(!penEnabled);
        });

        for (var i = 0; i < swatches.length; i++) {
            (function (btn) {
                btn.addEventListener('click', function () {
                    selectColor(btn.getAttribute('data-color'), btn);
                });
            })(swatches[i]);
        }
        if (els.colorInput) {
            els.colorInput.addEventListener('input', function () {
                selectColor(els.colorInput.value, null);
            });
        }
        if (els.widthRange && els.widthValue) {
            els.widthRange.addEventListener('input', function () {
                penWidth = parseInt(els.widthRange.value, 10) || 3;
                els.widthValue.textContent = String(penWidth);
            });
        }
        if (undoBtn) {
            undoBtn.addEventListener('click', function () {
                strokes.pop();
                redrawAll();
            });
        }
        if (clearBtn) {
            clearBtn.addEventListener('click', function () {
                strokes.length = 0;
                redrawAll();
            });
        }
    }

    /* ========== 指令分发 ========== */

    function handleCommand(cmd) {
        if (!cmd || cmd.toolName !== 'canvas') return;

        if (cmd.action === 'start') {
            initCanvas();
            shrinkChatArea();
        } else if (cmd.action === 'draw') {
            if (!active) { initCanvas(); shrinkChatArea(); }
            var parsed;
            try { parsed = JSON.parse(cmd.payload); } catch (e) { return; }
            // 支持单命令对象或命令数组（LLM 可能一次下发多条命令）
            var cmds = Array.isArray(parsed) ? parsed : [parsed];
            for (var ci = 0; ci < cmds.length; ci++) {
                var c = cmds[ci];
                if (!c || !c.type) continue;
                if (c.type === 'clear') {
                    // 清空命令：同时清空操作记录，保证后续撤销/重绘语义一致
                    strokes.length = 0;
                    redrawAll();
                } else {
                    renderCommand(c);
                    strokes.push({ type: 'cmd', cmd: c });
                }
            }
        } else if (cmd.action === 'snapshot') {
            // 获取当前结果图：回传 dataURL，不关闭插件
            if (!active) { initCanvas(); shrinkChatArea(); }
            var result = snapshotDataUrl();
            if (window.PluginHook && cmd.requestId) {
                window.PluginHook.report(cmd.requestId, result);
            }
        } else if (cmd.action === 'close') {
            // 结束会话：仅还原布局，不回传结果
            if (active) { restoreLayout(); }
        }
    }

    function closeCanvas() {
        if (!active) return;
        restoreLayout();
    }

    function downloadCanvas() {
        if (!board) return;
        var dataUrl = snapshotDataUrl();
        if (!dataUrl) return;
        var a = document.createElement('a');
        a.href = dataUrl;
        a.download = 'canvas-' + Date.now() + '.png';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }

    function bindButtons() {
        if (closeBtn) closeBtn.addEventListener('click', closeCanvas);
        if (downloadBtn) downloadBtn.addEventListener('click', downloadCanvas);
        bindPenToolbar();
    }

    function init() {
        findEls();
        bindButtons();
        if (window.PluginHook) {
            window.PluginHook.onCommand(handleCommand);
        }
        // 打开插槽面板（无画布时先初始化）：供「工具执行列表」上的插槽按钮调用
        window.openCanvasSlot = function () {
            if (!active) {
                initCanvas();
                shrinkChatArea();
            }
        };
        // 通过渲染 hook 在展开/历史工具项名称后追加「打开插槽」按钮
        // 名字匹配：工具集名 canvas（覆盖当前所有方法名/描述）+ 历史工具名 appendDraw（改名前的旧记录）
        if (window.PluginHook && window.PluginHook.registerToolRenderHook) {
            window.PluginHook.registerToolRenderHook('afterRender', ['canvas', 'appendDraw'], function (ctx) {
                if (!ctx || !ctx.header || ctx.header.querySelector('.tool-call-slot-btn')) return;
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'tool-call-slot-btn';
                btn.title = '打开画布插槽';
                btn.innerHTML = '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18"/><path d="M9 21V9"/></svg>';
                btn.addEventListener('click', function (e) {
                    e.stopPropagation();
                    e.preventDefault();
                    if (typeof window.openCanvasSlot === 'function') window.openCanvasSlot();
                });
                var nameEl = ctx.header.querySelector('.tool-call-name');
                if (nameEl) nameEl.parentNode.insertBefore(btn, nameEl.nextSibling);
                else ctx.header.appendChild(btn);
            });
        }
        // 画笔默认选中
        setPenEnabled(true);
        // 画笔事件
        if (board) {
            board.addEventListener('pointerdown', onBoardPointerDown);
            board.addEventListener('pointermove', onBoardPointerMove);
            board.addEventListener('pointerup', onBoardPointerUp);
            board.addEventListener('pointercancel', onBoardPointerUp);
            board.addEventListener('pointerleave', onBoardPointerUp);
        }
    }

    if (window.PluginLoader) {
        window.PluginLoader.onReady(init);
    } else {
        init();
    }
})();
