/**
 * 会话美化插件（非沙箱，直接运行在宿主页面全局作用域）。
 *
 * 能力：
 *   - 触发按钮：relocate 到会话头部「更多」按钮前，点击向下弹出设置面板；
 *   - 背景色：预设色板 / 自定义取色器，作用到会话区（.chat-area），可还原；
 *   - 背景图：上传本地图片（FileReader → dataURL）铺满会话区，可清除，并支持 0~100% 透明度；
 *   - 消息背景透明度：滑块统一控制 agent / user 消息气泡底色的 alpha（100% 即原色）；
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
    var ALPHA_DEFAULT = 100;
    var IMG_ALPHA_DEFAULT = 100;
    var MAX_IMAGE_BYTES = 4 * 1024 * 1024; // 背景图上限 4MB（localStorage 约 5MB）

    /* 气泡底色基准值：与宿主 index.css 保持一致，按滑块换算 alpha 后重新注入 */
    var AGENT_RGB_LIGHT = [240, 242, 245]; // .message.agent → #f0f2f5
    var AGENT_RGB_DARK = [45, 45, 68];     // body.dark-theme .message.agent → #2d2d44
    var USER_RGB_FROM = [102, 126, 234];   // .message.user 渐变起点 → #667eea
    var USER_RGB_TO = [118, 75, 162];      // .message.user 渐变终点 → #764ba2

    /* 会话区默认底色：未设置背景色时，背景图透明度以它为底衬（与宿主 index.css 一致） */
    var CHAT_BG_LIGHT = [255, 255, 255];   // .chat-area → white
    var CHAT_BG_DARK = [26, 26, 46];       // body.dark-theme .chat-area → #1a1a2e

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
    var imgAlphaRow = null;
    var imgAlphaInput = null;
    var imgAlphaVal = null;
    var alphaInput = null;
    var alphaVal = null;
    var resetAlphaBtn = null;
    var fontThinking = null;
    var fontMessage = null;
    var fontTool = null;
    var fontThinkingVal = null;
    var fontMessageVal = null;
    var fontToolVal = null;
    var resetFontBtn = null;
    var fontStyleEl = null;
    var alphaStyleEl = null;

    var state = {
        backgroundColor: null,
        backgroundImage: null,
        imageAlpha: IMG_ALPHA_DEFAULT,
        msgAlpha: ALPHA_DEFAULT,
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
                state.imageAlpha = clampInt(parsed.imageAlpha, 0, 100, IMG_ALPHA_DEFAULT);
                state.msgAlpha = clampInt(parsed.msgAlpha, 0, 100, ALPHA_DEFAULT);
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
            imageAlpha: state.imageAlpha,
            msgAlpha: state.msgAlpha,
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

    function rgba(rgb, a) {
        return 'rgba(' + rgb[0] + ',' + rgb[1] + ',' + rgb[2] + ',' + a + ')';
    }

    /** 把 #rgb / #rrggbb / rgb() / rgba() 解析为 [r,g,b]；无法解析返回 null */
    function parseRgb(color) {
        if (!color) return null;
        var v = String(color).trim();
        var m = /^#([0-9a-fA-F]{3})$/.exec(v);
        if (m) {
            return [
                parseInt(m[1].charAt(0) + m[1].charAt(0), 16),
                parseInt(m[1].charAt(1) + m[1].charAt(1), 16),
                parseInt(m[1].charAt(2) + m[1].charAt(2), 16)
            ];
        }
        m = /^#([0-9a-fA-F]{6})$/.exec(v);
        if (m) {
            return [
                parseInt(m[1].slice(0, 2), 16),
                parseInt(m[1].slice(2, 4), 16),
                parseInt(m[1].slice(4, 6), 16)
            ];
        }
        m = /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})/.exec(v);
        if (m) return [parseInt(m[1], 10), parseInt(m[2], 10), parseInt(m[3], 10)];
        return null;
    }

    /**
     * 背景图的「底衬色」：用户显式设了背景色就用它；
     * 否则用会话区自身默认底色（随深浅主题变化）。
     */
    function baseRgb() {
        var fromColor = parseRgb(state.backgroundColor);
        if (fromColor) return fromColor;
        var dark = document.body && document.body.classList.contains('dark-theme');
        return dark ? CHAT_BG_DARK : CHAT_BG_LIGHT;
    }

    /* ---------------- 应用 ---------------- */
    function chatArea() {
        return document.querySelector('.chat-area');
    }

    /**
     * 应用背景色 + 背景图。
     *
     * <p>背景图透明度用「一层半透明底衬色渐变 + 图片」两层 background 模拟：
     * 合成结果 = a·图片 + (1-a)·底衬色，等价于把图片按 a 叠在底衬色上。
     * 之所以不改成给元素设 opacity 或加伪元素叠层：前者会连带把消息内容一起变透明，
     * 后者需要处理 .chat-area 内部子元素的层叠与 z-index，容易踩到宿主布局。</p>
     */
    function applyBackground() {
        var area = chatArea();
        if (!area) return;
        area.style.backgroundColor = state.backgroundColor || '';
        if (state.backgroundImage) {
            var url = 'url("' + state.backgroundImage + '")';
            var a = state.imageAlpha / 100;
            if (a >= 1) {
                area.style.backgroundImage = url;
            } else {
                var veil = rgba(baseRgb(), Math.round((1 - a) * 1000) / 1000);
                area.style.backgroundImage = 'linear-gradient(' + veil + ',' + veil + '),' + url;
            }
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

    /**
     * 消息气泡背景透明度：把 agent / user 的底色按当前 alpha 重算成 rgba 后注入。
     *
     * <p>agent 侧必须用 !important —— 宿主 CSS 里 `.agent-turn .message.agent` 把小节底色置为
     * transparent（让整盒视觉连续），不压过它则透明度设置对 agent 消息完全无效。
     * 同时用 :not() 排除错误 / 警告小节，它们自带语义化的红色底（宿主里也是 !important），
     * 不该被透明度设置覆盖。</p>
     */
    function applyMessageAlpha() {
        if (!alphaStyleEl) {
            alphaStyleEl = document.createElement('style');
            alphaStyleEl.id = 'cbMsgAlphaStyle';
            document.head.appendChild(alphaStyleEl);
        }
        var a = state.msgAlpha / 100;
        var agentSel = '.message.agent:not(.error-message):not(.warn-message)';
        alphaStyleEl.textContent =
            agentSel + '{background:' + rgba(AGENT_RGB_LIGHT, a) + ' !important;}' +
            'body.dark-theme ' + agentSel + '{background:' + rgba(AGENT_RGB_DARK, a) + ' !important;}' +
            '.message.user{background:linear-gradient(135deg,' + rgba(USER_RGB_FROM, a) + ' 0%,' + rgba(USER_RGB_TO, a) + ' 100%) !important;}';
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
        if (imagePreview) {
            imagePreview.hidden = !state.backgroundImage;
            if (imageThumb && state.backgroundImage) imageThumb.src = state.backgroundImage;
        }
        // 未设置背景图时把透明度滑块置灰（值仍保留，设置图后立即生效）
        if (imgAlphaRow) imgAlphaRow.classList.toggle('is-muted', !state.backgroundImage);
    }

    function syncImageAlphaControl() {
        if (imgAlphaInput) imgAlphaInput.value = state.imageAlpha;
        if (imgAlphaVal) imgAlphaVal.textContent = state.imageAlpha + '%';
    }

    function syncAlphaControl() {
        if (alphaInput) alphaInput.value = state.msgAlpha;
        if (alphaVal) alphaVal.textContent = state.msgAlpha + '%';
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
        applyMessageAlpha();
        applyFont();
        syncSwatches();
        syncImagePreview();
        syncImageAlphaControl();
        syncAlphaControl();
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

        // 背景图透明度：一个滑块
        if (imgAlphaInput) {
            imgAlphaInput.addEventListener('input', function () {
                state.imageAlpha = clampInt(imgAlphaInput.value, 0, 100, IMG_ALPHA_DEFAULT);
                if (imgAlphaVal) imgAlphaVal.textContent = state.imageAlpha + '%';
                applyBackground();
                save();
            });
        }

        // 消息背景透明度：一个滑块
        if (alphaInput) {
            alphaInput.addEventListener('input', function () {
                state.msgAlpha = clampInt(alphaInput.value, 0, 100, ALPHA_DEFAULT);
                if (alphaVal) alphaVal.textContent = state.msgAlpha + '%';
                applyMessageAlpha();
                save();
            });
        }
        if (resetAlphaBtn) {
            resetAlphaBtn.addEventListener('click', function () {
                state.msgAlpha = ALPHA_DEFAULT;
                applyMessageAlpha();
                syncAlphaControl();
                save();
            });
        }

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
        imgAlphaRow = document.getElementById('cbImgAlphaRow');
        imgAlphaInput = document.getElementById('cbImgAlpha');
        imgAlphaVal = document.getElementById('cbImgAlphaVal');
        alphaInput = document.getElementById('cbMsgAlpha');
        alphaVal = document.getElementById('cbMsgAlphaVal');
        resetAlphaBtn = document.getElementById('cbResetAlpha');
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

    /**
     * 跟随深浅主题切换重算背景：未设背景色时，背景图透明度的底衬色取的是
     * 会话区默认底色（亮 white / 暗 #1a1a2e），主题一变必须重新合成才不出错。
     */
    function observeTheme() {
        if (!window.MutationObserver || !document.body) return;
        new MutationObserver(function () {
            applyBackground();
        }).observe(document.body, { attributes: true, attributeFilter: ['class'] });
    }

    function init() {
        findEls();
        if (!root || !btn || !panel) return;
        load();
        relocate();
        syncAll();
        bindEvents();
        observeTheme();
    }

    if (window.PluginLoader && typeof window.PluginLoader.onReady === 'function') {
        window.PluginLoader.onReady(init);
    } else {
        init();
    }
})();
