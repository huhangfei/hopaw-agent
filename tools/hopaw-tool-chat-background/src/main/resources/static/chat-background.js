/**
 * 会话背景色插件（沙箱内脚本）。
 *
 * 运行环境：<iframe sandbox="allow-scripts">，与宿主页面**不同源**。
 * 因此本脚本：
 *   - 拿不到宿主 DOM、localStorage、window 上的任何东西；
 *   - 只能通过 postMessage 调用宿主白名单能力，能力名还必须已在
 *     plugin-assets.json 的 sandboxApis 中声明；
 *   - 背景色的持久化与还原由宿主完成（宿主自己写 localStorage）。
 *
 * 协议：
 *   请求  parent.postMessage({__hopawPlugin:true, plugin, id, capability, payload}, '*')
 *   回执  {__hopawHost:true, id, ok, result|error}
 *   广播  {__hopawHost:true, event:'theme:background', payload:{color}}
 */
(function () {
    'use strict';

    var PLUGIN = (window.__HOPAW_SANDBOX__ && window.__HOPAW_SANDBOX__.plugin) || 'chat-background';

    /** 沙箱容器尺寸：收起 = 圆钮大小；展开 = 设置面板大小（宿主会做上限裁剪） */
    var COLLAPSED = { width: 48, height: 48 };
    var EXPANDED = { width: 284, height: 272 };

    var seq = 0;
    var waiting = {};

    /** 调用宿主能力，返回 Promise */
    function call(capability, payload) {
        return new Promise(function (resolve, reject) {
            var id = ++seq;
            waiting[id] = { resolve: resolve, reject: reject };
            try {
                window.parent.postMessage({
                    __hopawPlugin: true,
                    plugin: PLUGIN,
                    id: id,
                    capability: capability,
                    payload: payload || {}
                }, '*');
            } catch (e) {
                delete waiting[id];
                reject(e);
                return;
            }
            setTimeout(function () {
                if (waiting[id]) {
                    delete waiting[id];
                    reject(new Error('宿主能力调用超时：' + capability));
                }
            }, 3000);
        });
    }

    window.addEventListener('message', function (ev) {
        var msg = ev.data;
        if (!msg || msg.__hopawHost !== true) { return; }
        if (msg.event === 'theme:background') {
            render(msg.payload && msg.payload.color);
            return;
        }
        var slot = waiting[msg.id];
        if (!slot) { return; }
        delete waiting[msg.id];
        if (msg.ok) { slot.resolve(msg.result); }
        else { slot.reject(new Error(msg.error || '调用失败')); }
    });

    var root = document.getElementById('cbRoot');
    var panel = document.getElementById('cbPanel');
    var fab = document.getElementById('cbFab');
    var closeBtn = document.getElementById('cbClose');
    var swatchBox = document.getElementById('cbSwatches');
    var picker = document.getElementById('cbPicker');
    var applyBtn = document.getElementById('cbApply');
    var resetBtn = document.getElementById('cbReset');
    var currentEl = document.getElementById('cbCurrent');

    var opened = false;

    /** 同步界面状态：当前色文本 + 预设高亮 */
    function render(color) {
        currentEl.textContent = color ? ('当前：' + color) : '跟随主题';
        var all = swatchBox.querySelectorAll('.cb-swatch');
        for (var i = 0; i < all.length; i++) {
            var same = !!color && String(all[i].getAttribute('data-color')).toLowerCase() === String(color).toLowerCase();
            all[i].classList.toggle('active', same);
        }
        if (color) { picker.value = color; }
    }

    function setOpen(next) {
        opened = next;
        root.classList.toggle('cb-open', opened);
        panel.hidden = !opened;
        return call('ui.resize', opened ? EXPANDED : COLLAPSED);
    }

    function apply(color) {
        return call('theme.setBackground', { color: color || 'none' })
            .then(function (res) { render(res && res.color); })
            .catch(function (e) { console.warn('[chat-background] ' + e.message); });
    }

    fab.addEventListener('click', function () { setOpen(true); });
    closeBtn.addEventListener('click', function () { setOpen(false); });

    swatchBox.addEventListener('click', function (ev) {
        var btn = ev.target && ev.target.closest ? ev.target.closest('.cb-swatch') : null;
        if (btn) { apply(btn.getAttribute('data-color')); }
    });
    applyBtn.addEventListener('click', function () { apply(picker.value); });
    resetBtn.addEventListener('click', function () { apply('none'); });

    // 启动：先收起到圆钮，再向宿主查询当前背景（宿主负责持久化，沙箱不做本地存储）
    call('ui.resize', COLLAPSED).catch(function () { /* 忽略 */ });
    call('theme.getBackground', {}).then(function (res) {
        render(res && res.color);
    }).catch(function () { /* 忽略 */ });
})();
