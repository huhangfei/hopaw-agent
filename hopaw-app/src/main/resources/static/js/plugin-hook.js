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
 *   - window.PluginHook.registerHook(name, handler)
 *     注册「页面生命周期」hook（消息收发/流式渲染/回合盒子/统计/历史/会话列表/模型切换等）。
 *     可用 hook 名称与 ctx 结构见下方 HOOK_CATALOG（也可在控制台执行 PluginHook.hooks() 查看）。
 *     返回一个注销函数；可多次注册同一 hook，按注册顺序依次调用。
 *     可取消 hook（cancelable=true）的 handler 返回 false 表示接管默认行为：
 *       message:before-send / ws:message / history:before-node
 *   - window.PluginHook.unregisterHook(name, handler)
 *   - window.PluginHook.hooks()  返回全部可用 hook 的目录（名称/是否可取消/说明）
 *   - window.PluginHook.registerToolRenderHook(phase, toolSetName, handler)
 *     注册「工具执行列表渲染」拦截 hook（工具执行列表项有两类渲染来源，见下）。phase 取值：
 *       beforeRender        ★通用：任何工具项渲染前（实时推送渲染 + 历史列表渲染都触发）
 *                            handler 返回 false 可跳过默认渲染，由插件自行接管
 *       afterRender         ★通用：任何工具项渲染完成后 —— ctx.element/header/body 可直接追加按钮等 DOM
 *       beforeStaticRender  精细点：仅「历史列表（收缩/静态）渲染」前（触发时同时带上 beforeRender 的 hook）
 *       afterStaticRender   精细点：仅「历史列表渲染」后（触发时同时带上 afterRender 的 hook）
 *     只需覆盖所有工具项时，注册 beforeRender / afterRender 即可。
 *     toolNames 为工具集名/工具名过滤（如 'canvas'/'gomoku'，前端已把工具方法名/描述归一到工具集名），
 *     支持传数组以同时声明工具历史名称/别名（工具改名后旧历史记录仍能命中）；
 *     传 null/'' 表示订阅所有工具。handler 统一接收 ctx 对象：
 *       { phase:'live'|'static', event, status, toolCallId, toolName, toolSetName,
 *         toolArguments, data|chat, element, header, body }
 *     toolArguments 为**本次工具调用的入参**（已解析为对象，拿不到为 null）：after 系列 hook
 *     分发前由 index.js 兜底填充（实时取后端 arguments → 历史取 chat.toolArguments → 补挂场景
 *     从工具项 DOM 的参数区反查），插件可据此判断「这次调用是否带了我需要的数据」，
 *     例如 svg 插件据此判断该次调用有无 svgCode、从而决定点击按钮能否恢复那一次的插槽内容。
 *     注意：插件 JS 晚于历史列表渲染加载，注册后 index.js 会对已渲染节点补发一次 after hook（retrofit，
 *     ctx.event === 'retrofit'，data/chat 为 null），因此 hook 必须幂等（勿重复插入 DOM）。
 */
(function () {
    'use strict';

    var commandHandlers = [];
    var toolHandlers = {};
    var ws = null;
    var retryTimer = null;

    /** 工具执行列表渲染 hook 注册表：phase → [{toolName, handler}] */
    var TOOL_RENDER_PHASES = ['beforeRender', 'afterRender', 'beforeStaticRender', 'afterStaticRender'];
    var toolRenderHooks = {};

    /**
     * 渲染 hook 分发（由 index.js 在渲染各生命周期调用，插件无需直接使用）。
     * 语义：beforeRender/afterRender 是所有工具项渲染的通用点；静态渲染额外触发
     * beforeStaticRender/afterStaticRender 精细点，且静态分发时一并带上通用点 hook，
     * 避免插件只注册通用点却漏掉历史列表项。
     * 依次执行命中的 hook；任一 hook 返回 false 则整体返回 false（跳过默认渲染）。
     * toolName 过滤：ctx.toolName 或 ctx.toolSetName 命中即调用。
     */
    function collectHooks(phase) {
        var list = (toolRenderHooks[phase] || []).slice();
        if (phase === 'afterStaticRender') {
            return (toolRenderHooks['afterRender'] || []).concat(list);
        }
        if (phase === 'beforeStaticRender') {
            return (toolRenderHooks['beforeRender'] || []).concat(list);
        }
        return list;
    }

    /** hook 是否命中当前 ctx：names 为空表示所有工具；支持字符串或字符串数组（工具集名/工具名/历史别名） */
    function hookMatches(h, ctx) {
        var names = h.names;
        if (!names || (Array.isArray(names) && !names.length)) return true;
        if (!Array.isArray(names)) names = [names];
        for (var i = 0; i < names.length; i++) {
            var n = names[i];
            if (n && (n === ctx.toolName || n === ctx.toolSetName)) return true;
        }
        return false;
    }

    function dispatchToolRenderHook(phase, ctx) {
        var list = collectHooks(phase);
        if (!list.length) return true;
        var matched = false;
        var cancelled = false;
        for (var i = 0; i < list.length; i++) {
            var h = list[i];
            if (!hookMatches(h, ctx)) continue;
            matched = true;
            try {
                if (h.handler(ctx) === false) cancelled = true;
            } catch (e) {
                console.error('[plugin-hook] toolRenderHook ' + phase + ' error:', e);
            }
        }
        return matched ? !cancelled : true;
    }

    /* =========================================================================
     * 通用页面生命周期 Hook 总线
     *
     * 与「工具执行列表渲染 hook」的区别：
     *   - 工具渲染 hook 按工具集名过滤、有 4 个专门的渲染生命周期点（见上）；
     *   - 本总线覆盖页面级事件（消息、流式、历史、会话、模型…），不做工具过滤。
     *
     * 命名约定：'域:动作'，如 message:sent / stream:text / history:loaded。
     * 注册时校验名称（写错会在控制台告警），并通过 PluginHook.hooks() 可自助查询。
     * handler 统一接收一个 ctx 对象；cancelable 的 hook 返回 false 表示接管默认行为。
     * 所有 hook 都必须幂等：注册晚于 DOM 渲染时会补发（ctx.retrofit === true / event === 'retrofit'）。
     * ========================================================================= */
    var HOOK_CATALOG = {
        /* ── 应用 / 连接 ── */
        'app:ready': {
            cancelable: false,
            desc: '页面初始化完成（window.onload 之后）。ctx: {sessionId, agentId}'
        },
        'ws:open': {
            cancelable: false,
            desc: '聊天 WebSocket 连接建立（连接断开后 3 秒自动重连，会重复触发）'
        },
        'ws:close': {
            cancelable: false,
            desc: '聊天 WebSocket 连接关闭'
        },
        'ws:message': {
            cancelable: true,
            desc: '收到下行聊天消息（已解析为对象）。返回 false 可跳过内置处理，'
                + '用于接管自定义 type。ctx: {data, type}'
        },
        /* ── 消息发送 ── */
        'message:before-send': {
            cancelable: true,
            desc: '用户点击发送、payload 已组装、尚未发出。可直接改 ctx.payload 字段；'
                + '返回 false 取消本次发送。ctx: {sessionId, agentId, modelId, message, payload}'
        },
        'message:sent': {
            cancelable: false,
            desc: '消息已通过 WebSocket 发出。ctx: {sessionId, payload}'
        },
        /* ── 消息生命周期 ── */
        'message:received': {
            cancelable: false,
            desc: '后端已接收，本轮任务开始。ctx: {data, sessionId}'
        },
        'message:user-echo': {
            cancelable: false,
            desc: '用户消息（含图片/附件）回显渲染完成。ctx: {data, element, messagesDiv}'
        },
        'message:error': {
            cancelable: false,
            desc: '错误消息小节渲染完成。ctx: {requestId, message, element, contentEl}'
        },
        'message:warn': {
            cancelable: false,
            desc: '警告消息小节渲染完成。ctx: {requestId, message, element, contentEl}'
        },
        'message:done': {
            cancelable: false,
            desc: '整轮任务结束（task-done）。ctx: {data, requestId}'
        },
        /* ── 流式输出 ── */
        'stream:text': {
            cancelable: false,
            desc: '智能体文本流片段渲染完成（partial/done 都会触发，频率高，handler 请轻量）。'
                + 'ctx: {requestId, messageNo, status, content, element, contentEl}'
        },
        'stream:thinking': {
            cancelable: false,
            desc: '思考流片段渲染完成（频率高，handler 请轻量）。'
                + 'ctx: {requestId, messageNo, status, content, element, contentEl}'
        },
        /* ── Agent 回合盒子 ── */
        'turn:created': {
            cancelable: false,
            desc: '新的智能体回合并列盒子创建。ctx: {element, agentName}'
        },
        'turn:closed': {
            cancelable: false,
            desc: '当前回合盒子关闭（用户消息出现，后续 agent 消息将开新盒子）。ctx: {element}'
        },
        /* ── 统计 ── */
        'stats:task': {
            cancelable: false,
            desc: '任务执行统计到达（耗时/工具次数/Token/速度），已渲染进回合盒子 footer。'
                + 'ctx: {data, turnBox, statsEl}'
        },
        'stats:token': {
            cancelable: false,
            desc: 'Token 用量消息到达，已并入图表数据。ctx: {data, entry, chartData}'
        },
        /* ── 历史消息 ── */
        'history:before-node': {
            cancelable: true,
            desc: '单条历史消息节点渲染前。返回 false 跳过默认内容渲染（保留带 data-msg-id 的空壳，'
                + '游标/分页仍可用），由插件在随后的 history:node 中自行填充。ctx: {chat, element}'
        },
        'history:node': {
            cancelable: false,
            desc: '单条历史消息节点渲染完成（含插件注册晚于渲染时的补挂，此时 ctx.chat 为 null）。'
                + 'ctx: {chat, element, retrofit}'
        },
        'history:loaded': {
            cancelable: false,
            desc: '首次进入会话的历史消息加载并渲染完成。ctx: {list, messagesDiv, sessionId}'
        },
        'history:prepend': {
            cancelable: false,
            desc: '向上翻页，更早的历史消息前插完成。ctx: {list, messagesDiv}'
        },
        /* ── 会话列表 ── */
        'session:list': {
            cancelable: false,
            desc: '左侧会话列表渲染完成。ctx: {sessions, listEl}'
        },
        'session:title': {
            cancelable: false,
            desc: '会话标题更新（含新会话实时到达）。ctx: {sessionId, title, bizType}'
        },
        /* ── 配置 / 输入区 ── */
        'model:changed': {
            cancelable: false,
            desc: '用户切换模型。ctx: {modelId, modelName, model}'
        },
        'input:locked': {
            cancelable: false,
            desc: '输入区被锁定（会话开始运行）'
        },
        'input:unlocked': {
            cancelable: false,
            desc: '输入区解锁（会话结束或被停止）'
        }
    };

    /** 通用 hook 注册表：name → [handler, ...] */
    var hookRegistry = {};

    function normalizeHandlers(handler) {
        if (typeof handler === 'function') return [handler];
        if (Array.isArray(handler)) {
            return handler.filter(function (h) { return typeof h === 'function'; });
        }
        return [];
    }

    /**
     * 分发通用 hook（由 index.js 在对应时机调用）。
     * 未注册任何 handler 时返回 true（默认放行）；任一 cancelable hook 返回 false 则整体返回 false。
     */
    function dispatchHook(name, ctx) {
        var list = hookRegistry[name];
        if (!list || !list.length) return true;
        var ctxObj = ctx || {};
        var cancelled = false;
        for (var i = 0; i < list.length; i++) {
            try {
                if (list[i](ctxObj) === false) cancelled = true;
            } catch (e) {
                console.error('[plugin-hook] hook ' + name + ' error:', e);
            }
        }
        return !cancelled;
    }

    function connect() {
        var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        var url = protocol + '//' + window.location.host + '/ws/plugin';
        try {
            ws = new WebSocket(url);
        } catch (e) {
            scheduleRetry();
            return;
        }

        ws.onopen = function () {
            // 会话注册：声明本连接所属的聊天会话，后端插件指令按 sessionId 定向下发（会话隔离）。
            // 会话切换是整页跳转（/?sessionId=...），新页面会重新连接并注册，无需运行中重注册。
            var sid = window.currentSessionId;
            if (sid) {
                try { ws.send(JSON.stringify({ type: 'register', sessionId: String(sid) })); } catch (e) { /* 忽略 */ }
            }
        };
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
        },
        /**
         * 注册工具执行列表渲染 hook（详见文件头部说明）。
         * @param {string} phase 生命周期点：beforeRender / afterRender / beforeStaticRender / afterStaticRender
         * @param {string|string[]|null} toolNames 工具集名/工具名过滤，支持数组（可加入工具历史名称/别名），
         *        null 或 '' 或 [] 表示所有工具不区分
         * @param {Function} handler handler(ctx)，ctx 含 element/header/body 等；before 系列返回 false 接管默认渲染
         */
        registerToolRenderHook: function (phase, toolNames, handler) {
            if (typeof handler !== 'function' || TOOL_RENDER_PHASES.indexOf(phase) < 0) return;
            var names = (typeof toolNames === 'string') ? toolNames
                : (Array.isArray(toolNames) ? toolNames.filter(function (n) { return !!n; }) : null);
            (toolRenderHooks[phase] = toolRenderHooks[phase] || []).push({ names: names, handler: handler });
            // 补挂：插件 JS 晚于历史列表渲染加载，注册后通知 index.js 对已渲染的工具节点
            // 补发一次 after hook（afterRender/afterStaticRender），保证晚注册的 hook 也能生效。
            try {
                document.dispatchEvent(new CustomEvent('toolrenderhooks:updated'));
            } catch (e) { /* 环境不支持时忽略 */ }
        },
        /**
         * 注册「页面生命周期」hook（可用名称见文件头部 HOOK_CATALOG 或 PluginHook.hooks()）。
         *
         * handler(ctx) 的 ctx 结构随 hook 不同，见目录中的 desc；cancelable hook 返回 false
         * 表示接管默认行为。同一 hook 可注册多个 handler（按注册顺序调用）。
         * 插件 JS 由 plugin-loader 异步注入，可能晚于 DOM 渲染：注册后框架会对相关存量节点
         * 补发一次（ctx.retrofit === true），因此 handler 必须幂等。
         *
         * @param {string} name hook 名称
         * @param {Function|Function[]} handler 处理函数或函数数组
         * @returns {Function} 注销函数（调用即移除本次注册的 handler）
         */
        registerHook: function (name, handler) {
            if (typeof name !== 'string' || !HOOK_CATALOG[name]) {
                console.warn('[plugin-hook] 未知 hook 名称：' + name + '，可用名称见 PluginHook.hooks()');
                return function () {};
            }
            var handlers = normalizeHandlers(handler);
            if (!handlers.length) return function () {};
            var list = (hookRegistry[name] = hookRegistry[name] || []);
            handlers.forEach(function (h) { list.push(h); });
            // 通知 index.js：hook 可能注册晚于相关 DOM 渲染，触发一次存量补挂
            try {
                document.dispatchEvent(new CustomEvent('pluginhooks:updated', { detail: { name: name } }));
            } catch (e) { /* 环境不支持时忽略 */ }
            return function () {
                handlers.forEach(function (h) {
                    var k = list.indexOf(h);
                    if (k >= 0) list.splice(k, 1);
                });
            };
        },

        /** 注销此前注册的 hook handler */
        unregisterHook: function (name, handler) {
            var list = hookRegistry[name];
            if (!list) return;
            var k = list.indexOf(handler);
            if (k >= 0) list.splice(k, 1);
        },

        /**
         * 返回全部可用 hook 的目录（名称 / 是否可取消 / 说明）。
         * 便于在浏览器控制台自助查阅：PluginHook.hooks()
         */
        hooks: function () {
            return Object.keys(HOOK_CATALOG).map(function (k) {
                var d = HOOK_CATALOG[k];
                return { name: k, cancelable: !!d.cancelable, desc: d.desc };
            });
        },

        /** 渲染 hook 分发（内部使用，index.js 调用） */
        dispatchToolRenderHook: dispatchToolRenderHook,

        /** 通用 hook 分发（内部使用，index.js 调用） */
        dispatchHook: dispatchHook
    };

    connect();
})();
