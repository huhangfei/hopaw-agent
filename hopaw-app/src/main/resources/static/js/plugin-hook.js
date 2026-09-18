/**
 * 插件 Hook 分发器。
 *
 * 连接 /ws/plugin 下行通道，把后端 @Tool 下发的指令转发给注册的插件 JS 处理器，
 * 并提供结果上报能力（走 /api/plugins/report HTTP 接口）。
 *
 * 对外 API：
 *   - window.PluginHook.onCommand(handler)  监听后端下发指令（handler(cmd)）
 *   - window.PluginHook.register(toolName, handler) 按工具名订阅（handler(cmd)）
 *   - window.PluginHook.report(requestId, value) 上报结果（string 透传）
 *   - window.PluginHook.callTool(toolName, params) 调用插件通用 invoke API（传 Map 返回 Map）
 */
(function () {
    'use strict';

    var commandHandlers = [];
    var toolHandlers = {};
    var ws = null;
    var retryTimer = null;

    function connect() {
        var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        var url = protocol + '//' + window.location.host + '/ws/plugin';
        try {
            ws = new WebSocket(url);
        } catch (e) {
            scheduleRetry();
            return;
        }

        ws.onmessage = function (ev) {
            var cmd;
            try { cmd = JSON.parse(ev.data); } catch (e) { return; }
            dispatch(cmd);
        };
        ws.onclose = function () { scheduleRetry(); };
        ws.onerror = function () { /* onclose 会触发重连 */ };
    }

    function scheduleRetry() {
        if (retryTimer) return;
        retryTimer = setTimeout(function () {
            retryTimer = null;
            connect();
        }, 3000);
    }

    function dispatch(cmd) {
        var toolName = cmd && cmd.toolName;
        if (toolName && toolHandlers[toolName]) {
            toolHandlers[toolName].forEach(function (h) { try { h(cmd); } catch (e) { console.error('[plugin-hook]', e); } });
        }
        commandHandlers.forEach(function (h) { try { h(cmd); } catch (e) { console.error('[plugin-hook]', e); } });
    }

    window.PluginHook = {
        /** 监听所有后端指令 */
        onCommand: function (handler) {
            if (typeof handler === 'function') commandHandlers.push(handler);
        },
        /** 按工具名订阅指令 */
        register: function (toolName, handler) {
            if (typeof handler !== 'function') return;
            (toolHandlers[toolName] = toolHandlers[toolName] || []).push(handler);
        },
        /** 上报结果到后端暂存服务（string 透传） */
        report: function (requestId, value) {
            return fetch('/api/plugins/report', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ requestId: requestId, value: value })
            }).then(function (r) { return r.json(); });
        },
        /**
         * 调用插件通用 invoke API（POST /api/plugins/{toolName}/invoke）。
         * 向后端插件发送或获取数据：params 为对象，返回插件 invoke 的结果对象（Promise）。
         * 工具不存在时后端返回 404（success=false）；插件异常返回 500。
         */
        callTool: function (toolName, params) {
            return fetch('/api/plugins/' + encodeURIComponent(toolName) + '/invoke', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(params || {})
            }).then(function (r) { return r.json(); });
        }
    };

    connect();
})();
