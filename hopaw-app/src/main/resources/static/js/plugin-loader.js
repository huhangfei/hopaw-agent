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
 */
(function () {
    'use strict';

    var loadedIds = {};
    var readyCallbacks = [];
    var __pluginReady__ = false;

    function currentPage() {
        var page = document.body && document.body.getAttribute('data-page');
        return page || '';
    }

    function init() {
        var page = currentPage();
        fetch('/api/plugins/assets?page=' + encodeURIComponent(page), { credentials: 'same-origin' })
            .then(function (r) { return r.ok ? r.json() : []; })
            .then(function (assets) {
                assets.sort(function (a, b) { return (a.priority || 1000) - (b.priority || 1000); });
                return assets.reduce(function (p, a) {
                    return p.then(function () { return injectOne(a); });
                }, Promise.resolve());
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

    window.PluginLoader = {
        onReady: function (cb) {
            if (__pluginReady__) { cb(); }
            else { readyCallbacks.push(cb); }
        },
        unload: function (pluginId) {
            var els = document.querySelectorAll('[data-plugin-id="' + pluginId + '"]');
            for (var i = 0; i < els.length; i++) { els[i].remove(); }
            delete loadedIds[pluginId];
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
