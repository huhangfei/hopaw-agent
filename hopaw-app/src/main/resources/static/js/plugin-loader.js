/**
 * 插件前端注入器。
 *
 * 职责单一：根据当前页面标识（body[data-page]）向后端拉取该页面需要的插件资源清单，
 * 按 priority 排序后注入 <link>/<script>/<div>。不关心插件内部逻辑。
 *
 * 约定：
 *  - 页面标识由服务端渲染到 <body data-page="...">（复用 activePage）；
 *  - JS 串行注入保证依赖顺序，加载失败不中断其它插件；
 *  - 全部注入完成后触发 document 'plugin:ready' 事件，并暴露 window.PluginLoader。
 *
 * 沙箱模式（plugin-assets.json 里 `sandbox: true`，纯前端插件推荐）：
 *  - 该插件的 css/js/html **不**进入宿主全局作用域，而是整体装进一个
 *    <iframe sandbox="allow-scripts"> 容器（挂在 mount 选择器内，容器默认 0×0 不可见）；
 *  - iframe 与宿主不同源，脚本拿不到宿主 DOM，只能通过 postMessage 调用宿主白名单能力；
 *  - 宿主能力表见 SANDBOX_HOST_APIS，插件还必须在 plugin-assets.json 的
 *    `sandboxApis` 里声明要用哪些能力，两边取交集后才放行；
 *  - 插件侧调用：parent.postMessage({__hopawPlugin:true, plugin, id, capability, payload}, '*')
 *    宿主回执：      {__hopawHost:true, id, ok, result|error}
 *    宿主广播：      {__hopawHost:true, event:'主题/尺寸类事件', payload:{...}}
 */
(function () {
    'use strict';

    var loadedIds = {};
    var readyCallbacks = [];
    var __pluginReady__ = false;

    /** 容器尺寸上限：插件通过 ui.resize 申请，宿主裁剪到安全范围 */
    var MAX_SANDBOX_WIDTH = 480;
    var MAX_SANDBOX_HEIGHT = 640;

    /** 会话页背景色持久化键（由宿主负责落盘与还原，沙箱插件不接触 localStorage） */
    var BG_STORAGE_KEY = 'hopaw.chatBackground';

    /**
     * 宿主内置能力表（沙箱插件可申请的**全部**能力，超出即拒绝）。
     * 每个能力负责把 payload 落到宿主页面上，插件自身无法直接触碰 DOM。
     */
    var SANDBOX_HOST_APIS = {
        /** 调整沙箱容器尺寸：{width, height}（0 表示收起） */
        'ui.resize': function (payload, ctx) {
            var w = clamp(payload && payload.width, 0, MAX_SANDBOX_WIDTH);
            var h = clamp(payload && payload.height, 0, MAX_SANDBOX_HEIGHT);
            ctx.host.style.width = w + 'px';
            ctx.host.style.height = h + 'px';
            return { width: w, height: h };
        },
        /** 设置会话页背景：{color}（'none'/空 表示还原） */
        'theme.setBackground': function (payload) {
            var color = normalizeColor(payload && payload.color);
            applyBackground(color);
            try {
                if (color) { localStorage.setItem(BG_STORAGE_KEY, color); }
                else { localStorage.removeItem(BG_STORAGE_KEY); }
            } catch (e) { /* 隐私模式下忽略 */ }
            return { color: color || null };
        },
        /** 查询当前会话页背景：{} */
        'theme.getBackground': function () {
            return { color: readStoredBackground() };
        }
    };

    function clamp(value, min, max) {
        var n = parseInt(value, 10);
        if (isNaN(n)) { return min; }
        return Math.max(min, Math.min(max, n));
    }

    /** 只接受 #rgb / #rrggbb / rgb()/rgba()，其余（含 'none'）返回 null 表示"还原" */
    function normalizeColor(color) {
        if (!color || color === 'none') { return null; }
        var v = String(color).trim();
        if (/^#[0-9a-fA-F]{3}$/.test(v) || /^#[0-9a-fA-F]{6}$/.test(v)) { return v; }
        if (/^rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*(,\s*(0|1|0?\.\d+)\s*)?\)$/.test(v)) { return v; }
        return null;
    }

    function chatSurface() {
        return document.querySelector('.chat-wrapper');
    }

    function applyBackground(color) {
        var el = chatSurface();
        if (!el) { return; }
        if (color) {
            el.style.backgroundColor = color;
        } else {
            el.style.backgroundColor = '';
        }
        document.dispatchEvent(new CustomEvent('plugin:background-changed', { detail: { color: color } }));
    }

    function readStoredBackground() {
        try { return localStorage.getItem(BG_STORAGE_KEY); } catch (e) { return null; }
    }

    /** 页面加载时还原上次设置的背景（与插件是否启用无关，仅还原用户设置） */
    function restoreBackground() {
        var color = normalizeColor(readStoredBackground());
        if (color) { applyBackground(color); }
    }

    function currentPage() {
        var page = document.body && document.body.getAttribute('data-page');
        return page || '';
    }

    function init() {
        restoreBackground();

        var page = currentPage();
        fetch('/api/plugins/assets?page=' + encodeURIComponent(page), { credentials: 'same-origin' })
            .then(function (r) { return r.ok ? r.json() : []; })
            .then(function (assets) {
                assets.sort(function (a, b) { return (a.priority || 1000) - (b.priority || 1000); });

                // 沙箱资产不进全局注入：按插件聚合成一个 iframe 容器，统一挂载
                var sandboxGroups = {};
                var inlineAssets = [];
                assets.forEach(function (a) {
                    if (a && a.sandbox) {
                        var key = a.plugin || a.id;
                        (sandboxGroups[key] = sandboxGroups[key] || []).push(a);
                    } else {
                        inlineAssets.push(a);
                    }
                });

                return inlineAssets.reduce(function (p, a) {
                    return p.then(function () { return injectOne(a); });
                }, Promise.resolve()).then(function () {
                    return Object.keys(sandboxGroups).reduce(function (p, key) {
                        return p.then(function () { return injectSandbox(sandboxGroups[key]); });
                    }, Promise.resolve());
                });
            })
            .catch(function (e) {
                console.error('[plugin-loader] failed to load assets', e);
            })
            .then(function () {
                __pluginReady__ = true;
                document.dispatchEvent(new CustomEvent('plugin:ready', {
                    detail: { assets: Object.keys(loadedIds) }
                }));
                readyCallbacks.splice(0).forEach(function (cb) { try { cb(); } catch (e) {} });
            });
    }

    function injectOne(a) {
        if (!a || !a.id || loadedIds[a.id]) return Promise.resolve();
        loadedIds[a.id] = true;

        if (a.type === 'css') { injectCss(a); return Promise.resolve(); }
        if (a.type === 'js') { return injectJs(a); }
        if (a.type === 'html') { return injectHtml(a); }
        return Promise.resolve();
    }

    function injectCss(a) {
        var link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = a.url;
        link.dataset.pluginId = a.id;
        link.dataset.plugin = a.plugin;
        document.head.appendChild(link);
    }

    function injectJs(a) {
        return new Promise(function (resolve) {
            var s = document.createElement('script');
            s.src = a.url;
            s.dataset.pluginId = a.id;
            s.dataset.plugin = a.plugin;
            if (a.defer) s.defer = true;
            s.onload = function () { resolve(); };
            s.onerror = function () {
                console.error('[plugin-loader] js load failed:', a.url);
                resolve();
            };
            var target = (a.position === 'head') ? document.head : document.body;
            target.appendChild(s);
        });
    }

    function injectHtml(a) {
        return fetch(a.url, { credentials: 'same-origin' })
            .then(function (r) { return r.text(); })
            .then(function (html) {
                var slot = document.querySelector(a.mount);
                if (!slot) {
                    console.warn('[plugin-loader] mount not found:', a.mount, 'for', a.id);
                    return;
                }
                var wrapper = document.createElement('div');
                wrapper.dataset.pluginId = a.id;
                wrapper.dataset.plugin = a.plugin;
                wrapper.innerHTML = html;

                if (a.mode === 'replace') { slot.innerHTML = ''; slot.appendChild(wrapper); }
                else if (a.mode === 'prepend') { slot.insertBefore(wrapper, slot.firstChild); }
                else { slot.appendChild(wrapper); }

                wrapper.dispatchEvent(new CustomEvent('plugin:mounted', {
                    bubbles: true,
                    detail: { id: a.id, plugin: a.plugin }
                }));
            });
    }

    /* ==================== 沙箱容器 ==================== */

    /**
     * 把同一插件的全部沙箱资产合并成一个 iframe。
     * 容器默认 0×0 不可见，由插件通过 ui.resize 能力自行展开/收起。
     */
    function injectSandbox(assets) {
        if (!assets || !assets.length) { return Promise.resolve(); }

        var pluginId = assets[0].plugin || '';
        var mountSel = assets[0].mount;
        if (!mountSel) {
            console.warn('[plugin-loader] sandbox plugin without mount, skipped:', pluginId);
            return Promise.resolve();
        }
        var slot = document.querySelector(mountSel);
        if (!slot) {
            console.warn('[plugin-loader] mount not found:', mountSel, 'for sandbox plugin', pluginId);
            return Promise.resolve();
        }

        var declaredApis = [];
        assets.forEach(function (a) {
            (a.sandboxApis || []).forEach(function (api) {
                if (declaredApis.indexOf(api) < 0) { declaredApis.push(api); }
            });
            loadedIds[a.id] = true;
        });

        var cssParts = [];
        var jsParts = [];
        var htmlParts = [];

        return assets.reduce(function (p, a) {
            return p.then(function () {
                return fetch(a.url, { credentials: 'same-origin' })
                    .then(function (r) { return r.ok ? r.text() : ''; })
                    .then(function (content) {
                        if (a.type === 'css') { cssParts.push(content); }
                        else if (a.type === 'js') { jsParts.push(content); }
                        else if (a.type === 'html') { htmlParts.push(content); }
                    })
                    .catch(function (e) {
                        console.error('[plugin-loader] sandbox asset load failed:', a.url, e);
                    });
            });
        }, Promise.resolve()).then(function () {
            var host = document.createElement('div');
            host.className = 'hopaw-plugin-sandbox';
            host.dataset.pluginId = pluginId;
            host.dataset.plugin = pluginId;
            // 容器位置由宿主固定（左下角，避开右下角虚拟人挂件），插件只能申请尺寸、不能改位置
            host.style.cssText = 'position:fixed;left:24px;bottom:24px;width:0;height:0;'
                + 'z-index:150;overflow:hidden;transition:width .18s ease,height .18s ease;';

            var frame = document.createElement('iframe');
            frame.setAttribute('sandbox', 'allow-scripts');
            frame.setAttribute('title', '插件沙箱：' + pluginId);
            frame.style.cssText = 'width:100%;height:100%;border:0;background:transparent;display:block;';
            frame.srcdoc = buildSandboxDoc(pluginId, cssParts, htmlParts, jsParts);

            host.appendChild(frame);
            slot.appendChild(host);

            // 宿主侧能力桥：只认本 iframe、只在白名单∩声明范围内放行
            var iframeApis = declaredApis.filter(function (api) {
                var allowed = !!SANDBOX_HOST_APIS[api];
                if (!allowed) {
                    console.warn('[plugin-loader] sandbox plugin', pluginId, '声明了未知能力，已拒绝：', api);
                }
                return allowed;
            });

            window.addEventListener('message', function (ev) {
                if (ev.source !== frame.contentWindow) { return; }
                var msg = ev.data;
                if (!msg || msg.__hopawPlugin !== true) { return; }

                var reply = { __hopawHost: true, id: msg.id };
                var capability = msg.capability;
                if (iframeApis.indexOf(capability) < 0) {
                    reply.ok = false;
                    reply.error = '能力未授权：' + capability;
                    console.warn('[plugin-loader] sandbox capability denied:', pluginId, capability);
                } else {
                    try {
                        reply.result = SANDBOX_HOST_APIS[capability](msg.payload, { host: host, frame: frame, pluginId: pluginId });
                        reply.ok = true;
                    } catch (e) {
                        reply.ok = false;
                        reply.error = String(e && e.message ? e.message : e);
                    }
                }
                try { frame.contentWindow.postMessage(reply, '*'); } catch (e) { /* 忽略 */ }
            });

            // 宿主 → 沙箱广播：让沙箱面板同步当前状态（如背景色被其它途径改变）
            document.addEventListener('plugin:background-changed', function (ev) {
                try {
                    frame.contentWindow.postMessage({
                        __hopawHost: true,
                        event: 'theme:background',
                        payload: { color: (ev.detail && ev.detail.color) || null }
                    }, '*');
                } catch (e) { /* 忽略 */ }
            });

            host.dispatchEvent(new CustomEvent('plugin:mounted', {
                bubbles: true,
                detail: { id: pluginId, plugin: pluginId, sandbox: true }
            }));
        });
    }

    /**
     * 拼装沙箱文档。CSS/HTML/JS 全部内联：iframe 为 opaque origin，宿主无法再向其文档注入内容。
     */
    function buildSandboxDoc(pluginId, cssParts, htmlParts, jsParts) {
        var escapes = function (s) {
            // 结束标签必须转义，否则会提前闭合宿主 <script>
            return String(s || '').replace(/<\/script/gi, '<\\/script');
        };
        var base = '<base target="_blank">';
        var bootstrap = '<script>window.__HOPAW_SANDBOX__={plugin:'
            + JSON.stringify(pluginId) + '};</script>';
        return '<!doctype html><html><head><meta charset="utf-8">' + base
            + '<style>html,body{margin:0;padding:0;background:transparent;'
            + 'font-family:system-ui,-apple-system,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;}'
            + '</style>'
            + '<style>' + escapes(cssParts.join('\n')) + '</style>'
            + '</head><body>'
            + htmlParts.join('\n')
            + bootstrap
            + '<script>' + escapes(jsParts.join('\n;')) + '</script>'
            + '</body></html>';
    }

    window.PluginLoader = {
        onReady: function (cb) {
            if (__pluginReady__) { cb(); }
            else { readyCallbacks.push(cb); }
        },
        unload: function (pluginId) {
            var els = document.querySelectorAll('[data-plugin-id="' + pluginId + '"]');
            for (var i = 0; i < els.length; i++) { els[i].remove(); }
            delete loadedIds[pluginId];
        },
        /** 宿主能力表名（只读，供调试与文档核对） */
        sandboxApis: function () {
            return Object.keys(SANDBOX_HOST_APIS);
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
