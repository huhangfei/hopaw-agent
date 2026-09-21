/**
 * 会话美化插件（非沙箱，直接运行在宿主页面全局作用域）。
 *
 * 能力：
 *   - 触发按钮：relocate 到会话头部「更多」按钮前，点击向下弹出设置面板；
 *   - 背景色：预设色板 / 自定义取色器，作用到会话区（.chat-area），可还原；
 *   - 背景图：上传本地图片（FileReader → dataURL）铺满会话区，可清除；
 *   - 字体大小：三个滑块分别拖拽调节「思考 / 普通消息 / 工具按钮」字号，实时生效；
 *   - 全部设置持久化到 localStorage（键 hopaw.chatBeautify），页面加载时还原；
 *   - 深浅主题通过 body.dark-theme 由 CSS 适配，JS 无需感知。
 *
 * 与旧版（沙箱 iframe + postMessage）不同：本脚本可直接访问 document / localStorage。
 */
(function () {
    'use strict';

    var STORAGE_KEY = 'hopaw.chatBeautify';
    var LEGACY_KEY = 'hopaw.chatBackground';
    var FONT_DEFAULT = { thinking: 12, message: 14, tool: 14 };
    var MAX_IMAGE_BYTES = 4 * 1024 * 1024; // 背景图上限 4MB（localStorage 约 5MB）

    var root = null;
    var btn = null;
    var panel = null;
    var picker = null;
    var swatchesBox = null;
    var applyColorBtn = null;
    var resetColorBtn = null;
    var imageInput = null;
    var uploadBtn = null;
    var clearImageBtn = null;
    var imagePreview = null;
    var imageThumb = null;
    var fontThinking = null;
    var fontMessage = null;
    var fontTool = null;
    var fontThinkingVal = null;
    var fontMessageVal = null;
    var fontToolVal = null;
    var resetFontBtn = null;
    var fontStyleEl = null;

    var state = {
        backgroundColor: null,
        backgroundImage: null,
        font: { thinking: FONT_DEFAULT.thinking, message: FONT_DEFAULT.message, tool: FONT_DEFAULT.tool }
    };

    /* ---------------- 存储 ---------------- */
    function read(key) {
        try { return localStorage.getItem(key); } catch (e) { return null; }
    }
    function write(key, val) {
        try { localStorage.setItem(key, val); } catch (e) { /* 隐私模式 / 超限忽略 */ }
    }
    function remove(key) {
        try { localStorage.removeItem(key); } catch (e) { /* 忽略 */ }
    }

    function load() {
        var raw = read(STORAGE_KEY);
        if (raw) {
            try {
                var parsed = JSON.parse(raw);
                state.backgroundColor = normalizeColor(parsed.backgroundColor) || null;
                state.backgroundImage = (parsed.backgroundImage && /^data:image\//.test(parsed.backgroundImage)) ? parsed.backgroundImage : null;
                state.font.thinking = clampInt(parsed.font && parsed.font.thinking, 10, 18, FONT_DEFAULT.thinking);
                state.font.message = clampInt(parsed.font && parsed.font.message, 12, 26, FONT_DEFAULT.message);
                state.font.tool = clampInt(parsed.font && parsed.font.tool, 12, 22, FONT_DEFAULT.tool);
                return;
            } catch (e) { /* 解析失败则回退默认 */ }
        }
        // 迁移旧版（沙箱背景色插件）设置：hopaw.chatBackground → 新键背景色
        var legacy = normalizeColor(read(LEGACY_KEY));
        if (legacy) {
            state.backgroundColor = legacy;
            remove(LEGACY_KEY);
        }
    }

    function save() {
        write(STORAGE_KEY, JSON.stringify({
            backgroundColor: state.backgroundColor,
            backgroundImage: state.backgroundImage,
            font: state.font
        }));
    }

    function clampInt(v, min, max, dft) {
        var n = parseInt(v, 10);
        if (isNaN(n)) return dft;
        return Math.max(min, Math.min(max, n));
    }

    function normalizeColor(color) {
        if (!color || color === 'none') return null;
        var v = String(color).trim();
        if (/^#[0-9a-fA-F]{3}$/.test(v) || /^#[0-9a-fA-F]{6}$/.test(v)) return v;
        if (/^rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*(,\s*(0|1|0?\.\d+)\s*)?\)$/.test(v)) return v;
        return null;
    }

    /* ---------------- 应用 ---------------- */
    function chatArea() {
        return document.querySelector('.chat-area');
    }

    function applyBackground() {
        var area = chatArea();
        if (!area) return;
        area.style.backgroundColor = state.backgroundColor || '';
        if (state.backgroundImage) {
            area.style.backgroundImage = 'url("' + state.backgroundImage + '")';
            area.style.backgroundSize = 'cover';
            area.style.backgroundPosition = 'center';
            area.style.backgroundRepeat = 'no-repeat';
        } else {
            area.style.backgroundImage = '';
            area.style.backgroundSize = '';
            area.style.backgroundPosition = '';
            area.style.backgroundRepeat = '';
        }
    }

    function applyFont() {
        if (!fontStyleEl) {
            fontStyleEl = document.createElement('style');
            fontStyleEl.id = 'cbFontStyle';
            document.head.appendChild(fontStyleEl);
        }
        fontStyleEl.textContent =
            '.thinking-content{font-size:' + state.font.thinking + 'px !important;}' +
            '.message,.agent-turn{font-size:' + state.font.message + 'px !important;}' +
            '.tool-call-name{font-size:' + state.font.tool + 'px !important;}';
    }

    /* ---------------- UI 同步 ---------------- */
    function syncSwatches() {
        var swatches = swatchesBox ? swatchesBox.querySelectorAll('.cb-swatch') : [];
        for (var i = 0; i < swatches.length; i++) {
            var same = !!state.backgroundColor
                && String(swatches[i].getAttribute('data-color')).toLowerCase() === state.backgroundColor.toLowerCase();
            swatches[i].classList.toggle('active', same);
        }
        if (state.backgroundColor && picker) {
            picker.value = /^#([0-9a-fA-F]{6})$/.test(state.backgroundColor) ? state.backgroundColor : picker.value;
        }
    }

    function syncImagePreview() {
        if (!imagePreview) return;
        imagePreview.hidden = !state.backgroundImage;
        if (imageThumb && state.backgroundImage) imageThumb.src = state.backgroundImage;
    }

    function syncFontControls() {
        if (fontThinking) fontThinking.value = state.font.thinking;
        if (fontMessage) fontMessage.value = state.font.message;
        if (fontTool) fontTool.value = state.font.tool;
        if (fontThinkingVal) fontThinkingVal.textContent = state.font.thinking + 'px';
        if (fontMessageVal) fontMessageVal.textContent = state.font.message + 'px';
        if (fontToolVal) fontToolVal.textContent = state.font.tool + 'px';
    }

    function syncAll() {
        applyBackground();
        applyFont();
        syncSwatches();
        syncImagePreview();
        syncFontControls();
    }

    /* ---------------- 面板开合 ---------------- */
    function setOpen(next) {
        panel.hidden = !next;
        btn.classList.toggle('active', next);
    }

    /* ---------------- 事件绑定 ---------------- */
    function bindEvents() {
        btn.addEventListener('click', function (e) {
            e.stopPropagation();
            setOpen(panel.hidden);
        });

        // 背景色：色板
        swatchesBox.addEventListener('click', function (ev) {
            var s = ev.target && ev.target.closest ? ev.target.closest('.cb-swatch') : null;
            if (!s) return;
            state.backgroundColor = s.getAttribute('data-color');
            applyBackground();
            syncSwatches();
            save();
        });
        // 背景色：取色器
        applyColorBtn.addEventListener('click', function () {
            state.backgroundColor = normalizeColor(picker.value);
            applyBackground();
            syncSwatches();
            save();
        });
        resetColorBtn.addEventListener('click', function () {
            state.backgroundColor = null;
            applyBackground();
            syncSwatches();
            save();
        });

        // 背景图：上传
        uploadBtn.addEventListener('click', function () { imageInput.click(); });
        imageInput.addEventListener('change', function () {
            var file = imageInput.files && imageInput.files[0];
            imageInput.value = '';
            if (!file) return;
            if (!/^image\//.test(file.type)) return;
            if (file.size > MAX_IMAGE_BYTES) {
                alert('背景图过大（超过 4MB），请换一张更小的图片');
                return;
            }
            var reader = new FileReader();
            reader.onload = function () {
                state.backgroundImage = String(reader.result || '');
                applyBackground();
                syncImagePreview();
                save();
            };
            reader.readAsDataURL(file);
        });
        clearImageBtn.addEventListener('click', function () {
            state.backgroundImage = null;
            applyBackground();
            syncImagePreview();
            save();
        });

        // 字体大小：三个滑块
        bindRange(fontThinking, 'thinking', fontThinkingVal);
        bindRange(fontMessage, 'message', fontMessageVal);
        bindRange(fontTool, 'tool', fontToolVal);
        resetFontBtn.addEventListener('click', function () {
            state.font.thinking = FONT_DEFAULT.thinking;
            state.font.message = FONT_DEFAULT.message;
            state.font.tool = FONT_DEFAULT.tool;
            applyFont();
            syncFontControls();
            save();
        });

        // 点击面板外部关闭
        document.addEventListener('click', function (ev) {
            if (panel.hidden) return;
            if (root.contains(ev.target)) return;
            setOpen(false);
        });
    }

    function bindRange(input, kind, valueEl) {
        if (!input) return;
        input.addEventListener('input', function () {
            state.font[kind] = parseInt(input.value, 10) || FONT_DEFAULT[kind];
            if (valueEl) valueEl.textContent = state.font[kind] + 'px';
            applyFont();
            save();
        });
    }

    /* ---------------- 初始化 ---------------- */
    function findEls() {
        root = document.getElementById('cbRoot');
        btn = document.getElementById('cbBtn');
        panel = document.getElementById('cbPanel');
        picker = document.getElementById('cbPicker');
        swatchesBox = document.getElementById('cbSwatches');
        applyColorBtn = document.getElementById('cbApplyColor');
        resetColorBtn = document.getElementById('cbResetColor');
        imageInput = document.getElementById('cbImageInput');
        uploadBtn = document.getElementById('cbUploadImage');
        clearImageBtn = document.getElementById('cbClearImage');
        imagePreview = document.getElementById('cbImagePreview');
        imageThumb = document.getElementById('cbImageThumb');
        fontThinking = document.getElementById('cbFontThinking');
        fontMessage = document.getElementById('cbFontMessage');
        fontTool = document.getElementById('cbFontTool');
        fontThinkingVal = document.getElementById('cbFontThinkingVal');
        fontMessageVal = document.getElementById('cbFontMessageVal');
        fontToolVal = document.getElementById('cbFontToolVal');
        resetFontBtn = document.getElementById('cbResetFont');
    }

    /** 把按钮 + 面板整体移动到会话头部「更多」按钮前（无 header 时不显示） */
    function relocate() {
        var actions = document.querySelector('.chat-header-actions');
        var more = document.querySelector('.header-dropdown');
        if (actions && more && root && root.parentNode !== actions) {
            actions.insertBefore(root, more);
        }
    }

    function init() {
        findEls();
        if (!root || !btn || !panel) return;
        load();
        relocate();
        syncAll();
        bindEvents();
    }

    if (window.PluginLoader && typeof window.PluginLoader.onReady === 'function') {
        window.PluginLoader.onReady(init);
    } else {
        init();
    }
})();
