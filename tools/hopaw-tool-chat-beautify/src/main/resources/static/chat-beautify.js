/**
 * 会话美化插件（非沙箱，直接运行在宿主页面全局作用域）。
 *
 * 能力：
 *   - 触发按钮：relocate 到会话头部「更多」按钮前，点击向下弹出设置面板；
 *   - 背景色：预设色板 / 自定义取色器，作用到会话区（.chat-area），可还原；
 *   - 背景图：上传本地图片（FileReader → dataURL）铺满会话区，可清除，并支持 0~100% 透明度；
 *   - 气泡与容器透明度：一个滑块统一控制 agent 回合大盒子（.agent-turn）、用户气泡（.message.user），
 *     以及顶部栏 / 输入区 / 输入框等容器（.chat-header / .chat-input-area / .chat-input-wrapper）
 *     底色的 alpha（100% 即各自原色，调低后背景图 / 背景色会透到这些容器上）；
 *     并按底色与透明度的合成结果自动切换气泡文字深浅，
 *     避免亮色模式下透明度调低后白色文字看不见；
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
    var ALPHA_DEFAULT = 100;   // 气泡与容器透明度默认值
    var IMG_ALPHA_DEFAULT = 100;
    var MAX_IMAGE_BYTES = 4 * 1024 * 1024; // 背景图上限 4MB（localStorage 约 5MB）

    /* 气泡底色基准值：与宿主 index.css 保持一致，按滑块换算 alpha 后重新注入 */
    var AGENT_RGB_LIGHT = [240, 242, 245]; // .agent-turn（亮色）→ #f0f2f5
    var AGENT_RGB_DARK = [45, 45, 68];     // body.dark-theme .agent-turn → #2d2d44
    var USER_RGB_FROM = [102, 126, 234];   // .message.user 渐变起点 → #667eea
    var USER_RGB_TO = [118, 75, 162];      // .message.user 渐变终点 → #764ba2

    /* 气泡文字色：底色与透明度合成后亮度超过阈值就用深色字，否则用浅色字 */
    var TEXT_LUM_THRESHOLD = 0.45;
    var TEXT_DARK = '#333';
    var TEXT_LIGHT_AGENT = '#e0e0e0'; // 与 body.dark-theme .agent-turn 的 color 一致
    var TEXT_LIGHT_USER = '#fff';     // 与 .message.user 的 color 一致

    /* 会话区默认底色：未设置背景色时，背景图透明度以它为底衬（与宿主 index.css 一致） */
    var CHAT_BG_LIGHT = [255, 255, 255];   // .chat-area → white
    var CHAT_BG_DARK = [26, 26, 46];       // body.dark-theme .chat-area → #1a1a2e

    /* 容器底色基准值：会话页里几处不透明的「墙板」，跟「气泡与容器透明度」滑块一起淡出，
       背景图 / 背景色才能透到它们上面；100% 时各自还原成原色，外观与未接入前完全一致 */
    var HEADER_RGB_LIGHT = [250, 250, 250];    // .chat-header / .chat-input-area → #fafafa
    var HEADER_RGB_DARK = [22, 33, 62];        // body.dark-theme 同两者 → #16213e
    var INPUT_BOX_RGB_LIGHT = [255, 255, 255]; // .chat-input-wrapper → #fff
    var INPUT_BOX_RGB_DARK = [30, 58, 95];     // body.dark-theme .chat-input-wrapper → #1e3a5f
    var PAGE_RGB_LIGHT = [240, 242, 245];      // .chat-wrapper 自身无底色，实际透出的是 body → #f0f2f5
    var PAGE_RGB_DARK = [26, 26, 46];          // body.dark-theme → #1a1a2e

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
    var containerStyleEl = null;

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

    /** 当前是否暗色主题（宿主在 body 上挂 dark-theme） */
    function isDarkTheme() {
        return !!(document.body && document.body.classList.contains('dark-theme'));
    }

    /** sRGB 相对亮度（WCAG）：用于判断某个底色上该配深色还是浅色文字 */
    function luminance(rgb) {
        var channel = function (c) {
            c = c / 255;
            return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        };
        return 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2]);
    }

    /** 把 color 按 alpha a 叠到底色 base 上，返回合成后的 [r,g,b] */
    function composite(color, base, a) {
        return [
            Math.round(color[0] * a + base[0] * (1 - a)),
            Math.round(color[1] * a + base[1] * (1 - a)),
            Math.round(color[2] * a + base[2] * (1 - a))
        ];
    }

    /**
     * 气泡文字色：底色按 alpha 往会话区底色淡出后，白字在亮色模式下会越来越看不清。
     * 取候选底色（渐变取两端）合成后较亮的一侧，亮度超过阈值就给深色字，否则给浅色字。
     */
    function pickTextColor(colors, base, a, darkText, lightText) {
        var max = 0;
        for (var i = 0; i < colors.length; i++) {
            var l = luminance(composite(colors[i], base, a));
            if (l > max) max = l;
        }
        return max > TEXT_LUM_THRESHOLD ? darkText : lightText;
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
     * 会话区「底衬色」：用户显式设了背景色就用它；否则用会话区自身默认底色。
     *
     * <p>默认底色随深浅主题变化，而注入的规则是亮 / 暗两套一次写死的，
     * 因此这里按「假定主题」取色（dark 形参），而不是去读当前 DOM 状态。</p>
     */
    function baseRgbFor(dark) {
        var fromColor = parseRgb(state.backgroundColor);
        if (fromColor) return fromColor;
        return dark ? CHAT_BG_DARK : CHAT_BG_LIGHT;
    }

    /** 当前主题下的会话区底衬色（供 .chat-area 行内背景图叠层使用） */
    function baseRgb() {
        return baseRgbFor(isDarkTheme());
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
     * 容器背景透明度：顶部栏 / 输入区 / 输入框这些容器本身是不透明的「墙板」，会把铺在 .chat-area 上的
     * 背景图与背景色挡在外面。这里按「气泡与容器透明度」把它们各自的主题底色换算成 rgba，
     * 随滑块一起淡出，让背景图透上来。
     *
     * <p>宿主把主题开关挂在 body（body.dark-theme）上，所以亮 / 暗两套规则一次注入：
     * 暗色那条选择器以 body.dark-theme 打头、特异性更高且写在后面，天然覆盖亮色那条，
     * 主题切换由 CSS 直接生效，无须 JS 重算。</p>
     *
     * <p>100% 时注入的就是各自原色，外观与未接入前完全一致；调低才逐步透出背景图与消息区。
     * `.chat-input-wrapper` 加 `:not(.disabled)`：禁用态宿主用灰底 + 半透明表达「不可输入」，
     * 那个语义不该被透明度联动覆盖掉。</p>
     */
    function applyContainerAlpha() {
        if (!containerStyleEl) {
            containerStyleEl = document.createElement('style');
            containerStyleEl.id = 'cbContainerAlphaStyle';
            document.head.appendChild(containerStyleEl);
        }
        var a = state.msgAlpha / 100;
        containerStyleEl.textContent =
            containerCss('', HEADER_RGB_LIGHT, INPUT_BOX_RGB_LIGHT, PAGE_RGB_LIGHT, a)
            + containerCss('body.dark-theme ', HEADER_RGB_DARK, INPUT_BOX_RGB_DARK, PAGE_RGB_DARK, a);
    }

    /** 一套（亮或暗）容器底色规则：prefix 空串即亮色默认，'body.dark-theme ' 即暗色覆盖 */
    function containerCss(prefix, headerRgb, inputBoxRgb, pageRgb, a) {
        return prefix + '.chat-wrapper{background:' + rgba(pageRgb, a) + ' !important;}'
            + prefix + '.chat-header{background:' + rgba(headerRgb, a) + ' !important;}'
            + prefix + '.chat-input-area{background:' + rgba(headerRgb, a) + ' !important;}'
            + prefix + '.chat-input-wrapper:not(.disabled){background:' + rgba(inputBoxRgb, a) + ' !important;}';
    }

    /**
     * 气泡与容器透明度：控制 agent 回合大盒子（.agent-turn）、用户气泡（.message.user）
     * 以及顶部栏 / 输入区等容器底色的 alpha，末尾顺带刷新容器那组规则。
     *
     * <p>agent 侧的目标是 .agent-turn 而不是 .message.agent —— 宿主里小节底色已被置为 transparent，
     * 整盒底色统一落在盒子上，注入点必须跟着上移，否则透明度对 agent 完全无效。</p>
     *
     * <p>文字色随透明度一起自适应：底色往会话区底色淡出后，亮色模式下白字会看不见，
     * 因此按合成后的亮度决定用深色还是浅色字；user 气泡里几个原本为白字设计的元素（链接/代码块）
     * 在切到深色字时一并反转，避免低透明度下只剩一块看不清的浅底。</p>
     *
     * <p>亮 / 暗两套规则一次注入：暗色气泡的底色基准与文字深浅都单独按暗色会话区底色算过，
     * 主题切换由 CSS 直接生效，不依赖 MutationObserver 重算。</p>
     */
    function applyMessageAlpha() {
        if (!alphaStyleEl) {
            alphaStyleEl = document.createElement('style');
            alphaStyleEl.id = 'cbMsgAlphaStyle';
            document.head.appendChild(alphaStyleEl);
        }
        var a = state.msgAlpha / 100;
        var userGradient = 'linear-gradient(135deg,' + rgba(USER_RGB_FROM, a) + ' 0%,'
            + rgba(USER_RGB_TO, a) + ' 100%)';

        alphaStyleEl.textContent =
            bubbleCss('', AGENT_RGB_LIGHT, baseRgbFor(false), userGradient, a)
            + bubbleCss('body.dark-theme ', AGENT_RGB_DARK, baseRgbFor(true), userGradient, a);

        applyContainerAlpha();
    }

    /**
     * 一套（亮或暗）气泡底色 + 文字色规则。
     *
     * @param base 该主题下气泡底色淡出后露出的会话区底衬色，用于反推文字该用深色还是浅色
     */
    function bubbleCss(prefix, agentRgb, base, userGradient, a) {
        var agentText = pickTextColor([agentRgb], base, a, TEXT_DARK, TEXT_LIGHT_AGENT);
        var userText = pickTextColor([USER_RGB_FROM, USER_RGB_TO], base, a, TEXT_DARK, TEXT_LIGHT_USER);

        var css = prefix + '.agent-turn{background:' + rgba(agentRgb, a) + ' !important;'
            + 'color:' + agentText + ' !important;}'
            + prefix + '.message.user{background:' + userGradient + ' !important;'
            + 'color:' + userText + ' !important;}';
        if (userText === TEXT_DARK) {
            css += prefix + '.message.user .message-content a{color:inherit !important;}'
                + prefix + '.message.user .message-content code{background:rgba(0,0,0,0.06) !important;}'
                + prefix + '.message.user .message-content pre{background:rgba(0,0,0,0.05) !important;}'
                + prefix + '.message.user .message-content blockquote{border-left-color:currentColor !important;'
                + 'background:rgba(0,0,0,0.04) !important;}';
        }
        return css;
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

        // 气泡与容器透明度：一个滑块（内部会连带刷新容器注入规则）
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
     * 跟随深浅主题切换重算。
     *
     * <p>容器与气泡的透明度规则本身就分了亮 / 暗两套，主题切换由 CSS 直接生效、无须重算；
     * 唯一需要 JS 重算的是 .chat-area 的行内背景图叠层——它的底衬色取的是会话区自身默认底色
     * （亮 white / 暗 #1a1a2e），主题一变必须重新合成才不出错。</p>
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
