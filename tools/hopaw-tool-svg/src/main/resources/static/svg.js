/**
 * SVG 工具插件前端逻辑。
 *
 * 通过 PluginHook 监听后端 @Tool 下发的指令：
 *   - action=show   在会话侧边插槽中渲染 SVG 图片（收缩会话区，插槽与会话区平分空间）
 *   - action=close  结束会话、关闭插槽（还原布局）
 *
 * 插槽头部按钮：
 *   - 切换：预览 ↔ 源码（源码视图为可编辑 textarea，编辑时实时重渲染，切回预览即生效）
 *   - 下载：优先把当前（含手工编辑后）的 SVG 光栅化为 PNG 下载；光栅化失败则回退下载 .svg 源文件
 *   - 关闭：隐藏插槽、还原聊天区与侧边栏状态
 *
 * 工具执行列表上的「打开插槽」按钮（由渲染 hook 注入）：
 *   从 ctx.toolArguments（框架统一解析的本次调用入参）判断能否恢复该次 SVG——
 *   入参含 svgCode 时点击即还原那一次的图与标题；只有文件路径时仅打开插槽并提示无法恢复。
 *   实时渲染、历史列表、补挂（retrofit）三种来源的参数都由框架在 ctx.toolArguments 中尽力提供。
 *
 * 尺寸策略：预览 <img> 由 CSS 限制在容器内等比缩放，不改变 SVG 本身尺寸；下载时按 2 倍
 * 光栅化保证清晰度，尺寸取 SVG 自身 width/height（缺失时回退 viewBox，再回退默认值）。
 */
(function () {
    'use strict';

    var HOST_NS = 'http://www.w3.org/2000/svg';
    /** 光栅化放大倍数（下载用） */
    var DOWNLOAD_SCALE = 2;
    /** SVG 无尺寸声明时的兜底像素尺寸 */
    var FALLBACK_SIZE = 300;
    /** 编辑实时重渲染的防抖间隔(ms) */
    var RENDER_DEBOUNCE = 250;

    var els = {};
    var active = false;
    var codeView = false;
    /** 当前展示的 SVG 源码（含用户在源码视图中的编辑结果） */
    var currentSvg = '';
    var renderTimer = null;

    // 插槽打开时侧边栏是否为迷你态（用于关闭时恢复）
    var panelMiniAtStart = false;
    // 插槽强制设置的侧边栏状态（激活时始终收缩为迷你条）
    var panelMiniForced = null;

    function findEls() {
        els.panel = document.querySelector('.plugin-svg-panel');
        els.title = document.querySelector('[data-svg-title]');
        els.status = document.querySelector('[data-svg-status]');
        els.preview = document.querySelector('[data-svg-preview]');
        els.image = document.querySelector('[data-svg-image]');
        els.empty = document.querySelector('[data-svg-empty]');
        els.code = document.querySelector('[data-svg-code]');
        els.codeInput = document.querySelector('[data-svg-code-input]');
        els.toggleBtn = document.querySelector('[data-svg-toggle-code]');
        els.downloadBtn = document.querySelector('[data-svg-download]');
        els.closeBtn = document.querySelector('[data-svg-close]');
    }

    function setStatus(text, isActive) {
        if (els.status) {
            els.status.textContent = text;
            els.status.classList.toggle('active', !!isActive);
        }
    }

    /* ==================== 布局：收缩 / 还原 ==================== */

    function isToolPanelMini() {
        var panel = document.getElementById('toolPanel');
        return !!(panel && panel.classList.contains('tool-panel-mini'));
    }

    function shrinkChatArea() {
        var wrapper = document.querySelector('.chat-wrapper');
        if (wrapper) wrapper.classList.add('svg-active');

        panelMiniAtStart = isToolPanelMini();
        if (typeof window.toggleToolPanelMini === 'function') {
            window.toggleToolPanelMini(true);
        }
        panelMiniForced = true;

        active = true;
        setStatus(codeView ? '源码编辑' : '预览中', true);
    }

    function restoreLayout() {
        var wrapper = document.querySelector('.chat-wrapper');
        if (wrapper) wrapper.classList.remove('svg-active');

        // 若插槽期间用户手动切换过侧边栏，则保持用户当前状态；否则恢复到开始前状态
        var currentMini = isToolPanelMini();
        if (panelMiniForced !== null && currentMini === panelMiniForced) {
            if (typeof window.toggleToolPanelMini === 'function') {
                window.toggleToolPanelMini(panelMiniAtStart);
            }
        }
        panelMiniForced = null;

        active = false;
        setStatus('待命', false);
    }

    /* ==================== SVG 渲染 ==================== */

    /** 补全 xmlns：缺命名空间时浏览器 <img> 渲染 SVG 会失败 */
    function ensureNamespace(svg) {
        if (!svg) return '';
        var code = svg.trim();
        if (code.indexOf('<svg') === -1) return code;
        if (code.indexOf('xmlns') !== -1) return code;
        return code.replace('<svg', '<svg xmlns="' + HOST_NS + '"');
    }

    /** SVG 源码 → data URL */
    function svgToDataUrl(svg) {
        return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(ensureNamespace(svg));
    }

    /** 解析 SVG 尺寸（width/height 属性 → viewBox → 兜底值） */
    function resolveSvgSize(svg) {
        var code = svg || '';
        var w = parseFloat((/width=["']([\d.]+)(?:px)?["']/i.exec(code) || [])[1]);
        var h = parseFloat((/height=["']([\d.]+)(?:px)?["']/i.exec(code) || [])[1]);
        if (!w || !h) {
            var vb = /viewBox=["']\s*[\d.]+\s+[\d.]+\s+([\d.]+)\s+([\d.]+)\s*["']/i.exec(code);
            if (vb) {
                if (!w) w = parseFloat(vb[1]);
                if (!h) h = parseFloat(vb[2]);
            }
        }
        return {
            width: w && w > 0 ? w : FALLBACK_SIZE,
            height: h && h > 0 ? h : FALLBACK_SIZE
        };
    }

    /** 渲染预览图（空内容时显示空态） */
    function renderPreview(svg) {
        currentSvg = svg || '';
        if (!els.image) return;
        if (!currentSvg.trim()) {
            els.image.removeAttribute('src');
            els.image.hidden = true;
            if (els.empty) els.empty.hidden = false;
            return;
        }
        els.image.src = svgToDataUrl(currentSvg);
        els.image.hidden = false;
        if (els.empty) els.empty.hidden = true;
    }

    /* ==================== 视图切换 ==================== */

    function setCodeView(on) {
        codeView = !!on;
        if (els.code) els.code.hidden = !codeView;
        if (els.preview) els.preview.hidden = codeView;
        if (els.toggleBtn) {
            els.toggleBtn.classList.toggle('active', codeView);
            els.toggleBtn.title = codeView ? '返回预览' : '查看 / 编辑源码';
        }
        if (codeView) {
            if (els.codeInput) {
                els.codeInput.value = currentSvg;
                els.codeInput.focus();
            }
            setStatus('源码编辑', active);
        } else {
            setStatus('预览中', active);
        }
    }

    function toggleCodeView() {
        if (!codeView) {
            setCodeView(true);
            return;
        }
        // 返回预览：以编辑框内容为准重渲染
        var edited = els.codeInput ? els.codeInput.value : currentSvg;
        renderPreview(edited);
        setCodeView(false);
    }

    /** 源码编辑实时重渲染（防抖），无需手动应用 */
    function onCodeInput() {
        if (renderTimer) clearTimeout(renderTimer);
        renderTimer = setTimeout(function () {
            renderTimer = null;
            renderPreview(els.codeInput ? els.codeInput.value : '');
        }, RENDER_DEBOUNCE);
    }

    /* ==================== 下载 ==================== */

    function saveBlob(blob, filename) {
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    }

    /** 回退下载：直接下载 .svg 源文件 */
    function downloadSvgSource(svg) {
        saveBlob(new Blob([ensureNamespace(svg)], { type: 'image/svg+xml;charset=utf-8' }),
            'svg-' + Date.now() + '.svg');
    }

    /** 下载：优先光栅化为 PNG，失败回退 SVG 源文件 */
    function downloadSvg() {
        var svg = currentSvg;
        if (!svg || !svg.trim()) {
            setStatus('暂无可下载内容');
            setTimeout(function () { setStatus(codeView ? '源码编辑' : '预览中', active); }, 1500);
            return;
        }
        var size = resolveSvgSize(svg);
        var img = new Image();
        img.onload = function () {
            try {
                var canvas = document.createElement('canvas');
                canvas.width = Math.max(1, Math.round(size.width * DOWNLOAD_SCALE));
                canvas.height = Math.max(1, Math.round(size.height * DOWNLOAD_SCALE));
                var ctx = canvas.getContext('2d');
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                canvas.toBlob(function (blob) {
                    if (blob) {
                        saveBlob(blob, 'svg-' + Date.now() + '.png');
                    } else {
                        downloadSvgSource(svg);
                    }
                }, 'image/png');
            } catch (e) {
                downloadSvgSource(svg);
            }
        };
        img.onerror = function () { downloadSvgSource(svg); };
        img.src = svgToDataUrl(svg);
    }

    /* ==================== 指令分发 ==================== */

    function handleCommand(cmd) {
        if (!cmd || cmd.toolName !== 'svg') return;

        if (cmd.action === 'show') {
            var data = {};
            try { data = JSON.parse(cmd.payload) || {}; } catch (e) { data = {}; }
            showSvgContent(data.svg || '', data.title);
        } else if (cmd.action === 'close') {
            if (active) restoreLayout();
        }
    }

    /** 在插槽中展示 SVG（后端 show 指令与历史工具项「恢复」共用） */
    function showSvgContent(svg, title) {
        if (els.title) {
            els.title.textContent = (title && title.trim()) ? title : 'SVG 预览';
        }
        renderPreview(svg || '');
        setCodeView(false);
        if (!active) shrinkChatArea();
    }

    /**
     * 从渲染 hook 的 ctx 解析本次调用可恢复的 SVG 源码。
     *
     * 框架会把工具入参统一解析到 ctx.toolArguments（实时渲染 / 历史渲染 / 补挂场景都尽力提供），
     * 但并非每次调用都带源码：例如 svg_saveFile 只带文件路径，源码在磁盘上、前端不可见。
     * 因此「能否恢复」由插件自己判断：返回 null 表示本次调用无法还原插槽内容。
     */
    function resolveRecoverableSource(ctx) {
        var args = ctx && ctx.toolArguments;
        if (!args || typeof args !== 'object') return null;
        var code = args.svgCode || args.svg;
        if (typeof code !== 'string' || !code.trim()) return null;
        return {
            svg: code,
            title: (typeof args.title === 'string') ? args.title.trim() : ''
        };
    }

    function closeSvg() {
        if (!active) return;
        restoreLayout();
    }

    function bindButtons() {
        if (els.toggleBtn) els.toggleBtn.addEventListener('click', toggleCodeView);
        if (els.downloadBtn) els.downloadBtn.addEventListener('click', downloadSvg);
        if (els.closeBtn) els.closeBtn.addEventListener('click', closeSvg);
        if (els.codeInput) els.codeInput.addEventListener('input', onCodeInput);
    }

    function init() {
        findEls();
        bindButtons();
        if (window.PluginHook) {
            window.PluginHook.onCommand(handleCommand);
        }
        /**
         * 打开插槽面板（供「工具执行列表」上的插槽按钮调用）。
         * @param {Object|null} [source] 本次调用的可恢复内容 {svg, title}；
         *        传 null 表示「明确来自某个历史工具项但该次调用无源码可还原」，
         *        不传（undefined）表示只打开面板、保留插槽当前内容。
         */
        window.openSvgSlot = function (source) {
            if (source && source.svg) {
                // 历史工具项「恢复」：直接按本次调用的入参渲染，不依赖后端最后一次 show 指令
                showSvgContent(source.svg, source.title);
                setStatus(source.title ? '已恢复：' + source.title : '已恢复该次 SVG', true);
                return;
            }
            if (!active) shrinkChatArea();
            if (source === null) {
                // 该次调用没带 SVG 源码（如 svg_saveFile 只有文件路径），插槽保留最近一次内容并提示
                setStatus('该次调用未携带 SVG 源码，无法恢复', false);
                setTimeout(function () {
                    if (active) setStatus(codeView ? '源码编辑' : '预览中', true);
                }, 1800);
            }
        };
        // 通过渲染 hook 在工具项名称后追加「打开插槽」按钮
        if (window.PluginHook && window.PluginHook.registerToolRenderHook) {
            window.PluginHook.registerToolRenderHook('afterRender', ['svg'], function (ctx) {
                if (!ctx || !ctx.header) return;
                var btn = ctx.header.querySelector('.tool-call-slot-btn');
                if (!btn) {
                    btn = document.createElement('button');
                    btn.type = 'button';
                    btn.className = 'tool-call-slot-btn';
                    btn.innerHTML = '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18"/><path d="M9 21V9"/></svg>';
                    btn.addEventListener('click', function (e) {
                        e.stopPropagation();
                        e.preventDefault();
                        if (typeof window.openSvgSlot === 'function') window.openSvgSlot(btn.svgSlotSource || null);
                    });
                    var nameEl = ctx.header.querySelector('.tool-call-name');
                    if (nameEl) nameEl.parentNode.insertBefore(btn, nameEl.nextSibling);
                    else ctx.header.appendChild(btn);
                }
                // 同一节点会随状态推进多次触发 hook：DOM 创建幂等，但参数必须每次刷新
                // （首帧可能是 preparing、参数未到齐；started/executed 才是完整入参）
                btn.svgSlotSource = resolveRecoverableSource(ctx);
                btn.title = btn.svgSlotSource
                    ? '在插槽中预览该次 SVG' + (btn.svgSlotSource.title ? '：' + btn.svgSlotSource.title : '')
                    : '打开 SVG 插槽（该次调用未携带 SVG 源码）';
            });
        }
    }

    if (window.PluginLoader) {
        window.PluginLoader.onReady(init);
    } else {
        init();
    }
})();
