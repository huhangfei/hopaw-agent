/* ============================================================
 * tool-panel 简化视图（迷你窄条）模式
 * 依赖：index.js（会话/工具/token 完整视图渲染）、chart.umd.min.js（本地）
 * 实现：不改动 index.js，通过 MutationObserver 同步完整视图 DOM，
 *      并包装 renderTokenChart / updateTokenTitle 捕获 token 数据。
 * ============================================================ */
(function () {
    'use strict';

    var STORAGE_KEY = 'hopaw_tool_panel_mini';
    var miniMode = false;
    var switching = false;

    var sessionObserver = null;
    var toolObserver = null;

    var miniToolStatsChart = null;
    var miniTokenDonut = null;

    var toolStatusMeta = [
        { key: 'executed',  label: '执行完成', color: '#2ecc71' },
        { key: 'running',   label: '执行中',   color: '#5fa8ec' },
        { key: 'started',   label: '已启动',   color: '#8ea2ff' },
        { key: 'preparing', label: '准备中',   color: '#9ad0ff' },
        { key: 'approval',  label: '等待审批', color: '#f5a623' },
        { key: 'failed',    label: '执行失败', color: '#e74c3c' },
        { key: 'rejected',  label: '已拒绝',   color: '#e67e22' }
    ];
    var statusLabelMap = {
        preparing: '准备中', started: '已启动', running: '执行中', approval: '等待审批',
        executed: '执行完成', failed: '执行失败', rejected: '已拒绝'
    };

    /* ---------------- 小工具 ---------------- */
    function byId(id) { return document.getElementById(id); }

    function cssEscape(v) {
        if (window.CSS && CSS.escape) return CSS.escape(v);
        return String(v).replace(/([^a-zA-Z0-9_\u4e00-\u9fa5-])/g, '\\$1');
    }

    function esc(s) {
        if (typeof escapeHtml === 'function') return escapeHtml(s == null ? '' : String(s));
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function fmtNum(n) {
        n = Number(n || 0);
        if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
        if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
        return String(n);
    }

    function isDark() { return document.body.classList.contains('dark-theme'); }

    /* 会话业务类型 → 筛选类型（与 index.js 保持一致） */
    function sessionFilterType(bizType) {
        if (bizType === 'workflowTaskChat' || bizType === 'workflow-task-chat') return 'task';
        if (bizType === 'projectChat' || bizType === 'project-chat') return 'project';
        return 'chat';
    }

    /* ============================================================
     * 模式切换（含过渡动画编排）
     * ============================================================ */
    function readPref() {
        try { return window.localStorage.getItem(STORAGE_KEY) === '1'; } catch (e) { return false; }
    }
    function writePref(on) {
        try { window.localStorage.setItem(STORAGE_KEY, on ? '1' : '0'); } catch (e) { /* ignore */ }
    }

    window.toggleToolPanelMini = function (on) {
        var panel = byId('toolPanel');
        var full = byId('toolPanelFull');
        var mini = byId('toolPanelMini');
        if (!panel || !full || !mini || switching) return;
        on = !!on;
        if (on === miniMode) return;
        switching = true;
        miniMode = on;
        writePref(on);
        hideMiniPopover();

        if (on) {
            /* 收缩：完整内容淡出 → 宽度收窄 → 迷你条淡入 */
            full.classList.add('fading');
            panel.classList.add('tool-panel-mini');
            setTimeout(function () {
                full.classList.add('hide');
                full.classList.remove('fading');
                mini.classList.remove('hide');
                void mini.offsetWidth; /* 强制回流，保证过渡生效 */
                mini.classList.add('visible');
                initMiniCharts();
                syncMiniSessions();
                syncMiniTools();
                syncMiniToken();
                switching = false;
            }, 210);
        } else {
            /* 展开：迷你条淡出 → 宽度展开 → 完整内容淡入 */
            mini.classList.remove('visible');
            panel.classList.remove('tool-panel-mini');
            setTimeout(function () {
                mini.classList.add('hide');
                full.classList.add('fading'); /* 先置于淡出基态，便于随后的淡入过渡 */
                full.classList.remove('hide');
                void full.offsetWidth;
                full.classList.remove('fading'); /* 淡入 */
                destroyMiniCharts();
                switching = false;
            }, 360);
        }
    };

    /* ============================================================
     * 悬停弹层（向左展开的动画卡片）
     * ============================================================ */
    var popState = { el: null, anchor: null, hideTimer: null };

    function removePopoverEl(el) {
        if (!el || !el.parentNode) return;
        el.classList.remove('show');
        setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 220);
    }

    function hideMiniPopover() {
        if (popState.hideTimer) { clearTimeout(popState.hideTimer); popState.hideTimer = null; }
        if (popState.el) { removePopoverEl(popState.el); popState.el = null; }
        popState.anchor = null;
    }

    function scheduleHidePopover() {
        if (popState.hideTimer) clearTimeout(popState.hideTimer);
        popState.hideTimer = setTimeout(function () {
            if (popState.el) { removePopoverEl(popState.el); popState.el = null; }
            popState.anchor = null;
            popState.hideTimer = null;
        }, 200);
    }

    /* 同步导致的气泡重建后，原锚点若已脱离 DOM 则收起弹层 */
    function checkPopoverAnchor() {
        if (popState.el && popState.anchor && !popState.anchor.isConnected) {
            hideMiniPopover();
        }
    }

    function showMiniPopover(anchor, html) {
        if (popState.hideTimer) { clearTimeout(popState.hideTimer); popState.hideTimer = null; }
        if (popState.el) { removePopoverEl(popState.el); popState.el = null; }
        popState.anchor = anchor;
        var pop = document.createElement('div');
        pop.className = 'mini-popover';
        pop.innerHTML = html;
        document.body.appendChild(pop);
        var rect = anchor.getBoundingClientRect();
        var w = pop.offsetWidth, h = pop.offsetHeight;
        var left = rect.left - w - 10;
        if (left < 8) left = Math.min(rect.right + 10, window.innerWidth - w - 8);
        var top = rect.top + rect.height / 2 - h / 2;
        if (top < 8) top = 8;
        if (top + h > window.innerHeight - 8) top = window.innerHeight - h - 8;
        pop.style.left = left + 'px';
        pop.style.top = top + 'px';
        requestAnimationFrame(function () { pop.classList.add('show'); });
        pop.addEventListener('mouseleave', scheduleHidePopover);
        pop.addEventListener('mouseenter', function () {
            if (popState.hideTimer) { clearTimeout(popState.hideTimer); popState.hideTimer = null; }
        });
        popState.el = pop;
    }

    function bindHoverPopover(el, buildFn) {
        if (!el) return;
        el.addEventListener('mouseenter', function () {
            if (!miniMode) return;
            if (popState.hideTimer) { clearTimeout(popState.hideTimer); popState.hideTimer = null; }
            showMiniPopover(el, buildFn());
        });
        el.addEventListener('mouseleave', scheduleHidePopover);
    }

    /* ============================================================
     * 会话迷你视图：从 #sessionList 同步为两字气泡
     * ============================================================ */
    var lastSessionSig = null;

    function collectSessionItems() {
        var list = byId('sessionList');
        if (!list) return [];
        var items = [];
        Array.prototype.forEach.call(list.querySelectorAll('.session-list-item'), function (item) {
            var id = item.getAttribute('data-session-id');
            if (!id) return;
            var titleEl = item.querySelector('.session-list-item-title');
            items.push({
                id: id,
                title: (titleEl ? titleEl.textContent : '') || '未命名会话',
                time: (item.querySelector('.session-list-item-time') || {}).textContent || '',
                bizType: item.getAttribute('data-biz-type') || '',
                active: item.classList.contains('active'),
                running: !!(typeof runningSessionIds !== 'undefined' && runningSessionIds[id])
                    || !!item.querySelector('.session-tag.session-tag-running')
            });
        });
        return items;
    }

    function syncMiniSessions() {
        var miniList = byId('miniSessionList');
        if (!miniList || !miniMode) return;
        var items = collectSessionItems();

        /* 类型筛选 tab 选中态与完整视图同步 */
        var activeTab = document.querySelector('#sessionTypeFilter .session-type-tab.active');
        var activeType = activeTab ? activeTab.getAttribute('data-type') : 'chat';
        var filterBox = byId('miniTypeFilter');
        if (filterBox) {
            Array.prototype.forEach.call(filterBox.querySelectorAll('.mini-type-tab'), function (tab) {
                tab.classList.toggle('active', tab.getAttribute('data-type') === activeType);
            });
        }

        var sig = items.map(function (s) { return s.id; }).join(',');
        if (sig !== lastSessionSig) {
            /* 结构变化：整体重建（新会话/筛选切换，频率低） */
            lastSessionSig = sig;
            miniList.innerHTML = '';
            if (items.length === 0) {
                var empty = document.createElement('div');
                empty.className = 'mini-session-empty';
                empty.textContent = '空';
                empty.title = '暂无会话';
                miniList.appendChild(empty);
                return;
            }
            items.forEach(function (s, idx) {
                miniList.appendChild(buildSessionBubble(s, idx));
            });
        } else {
            /* 结构未变：原位更新（标题/运行态/选中态），不打断悬浮 */
            if (items.length === 0) return;
            Array.prototype.forEach.call(miniList.querySelectorAll('.mini-session-empty'), function (e) { e.remove(); });
            items.forEach(function (s) {
                var bubble = miniList.querySelector('.mini-session-bubble[data-session-id="' + cssEscape(s.id) + '"]');
                if (!bubble) { bubble = buildSessionBubble(s, 0); miniList.appendChild(bubble); return; }
                updateSessionBubble(bubble, s);
            });
        }
    }

    function buildSessionBubble(s, idx) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'mini-session-bubble';
        b.style.animationDelay = Math.min(idx * 30, 240) + 'ms';
        b.setAttribute('data-session-id', s.id);
        updateSessionBubble(b, s);
        bindHoverPopover(b, function () { return buildSessionPopoverHtml(s.id); });
        b.addEventListener('click', function () {
            if (typeof currentSessionId !== 'undefined' && s.id === currentSessionId) return;
            window.location.href = '/?sessionId=' + encodeURIComponent(s.id);
        });
        return b;
    }

    function updateSessionBubble(bubble, s) {
        var ftype = sessionFilterType(s.bizType);
        var two = (s.title || '').trim().substring(0, 2) || '会话';
        bubble.textContent = two;
        bubble.className = 'mini-session-bubble mini-type-' + ftype
            + (s.active ? ' active' : '') + (s.running ? ' running' : '');
        bubble.setAttribute('data-session-id', s.id);
    }

    function buildSessionPopoverHtml(sessionId) {
        var s = collectSessionItems().filter(function (x) { return x.id === sessionId; })[0];
        if (!s) return '<div class="mini-popover-title">会话</div>';
        var ftype = sessionFilterType(s.bizType);
        var typeLabel = ftype === 'project' ? '项目' : (ftype === 'task' ? '任务' : '聊天');
        var html = '<div class="mini-popover-head">'
            + '<span class="mini-popover-title">' + esc(s.title) + '</span>'
            + '<span class="mini-popover-tag ' + ftype + '">' + typeLabel + '</span>'
            + '<span class="mini-popover-tag ' + (s.running ? 'state-run">运行中' : 'state-idle">空闲') + '</span>'
            + '</div>';
        if (s.time) {
            html += '<div class="mini-popover-row"><span class="mini-popover-label">时间</span>'
                + '<span class="mini-popover-value num">' + esc(s.time) + '</span></div>';
        }
        html += '<div class="mini-popover-hint">点击气泡切换会话</div>';
        return html;
    }

    /* ============================================================
     * 工具执行迷你视图：状态甜甜圈 + 图标气泡
     * ============================================================ */
    var lastToolSig = null;

    function collectToolCalls() {
        var list = byId('toolExecList');
        if (!list) return [];
        var calls = [];
        Array.prototype.forEach.call(list.querySelectorAll('.tool-call'), function (call) {
            var id = call.getAttribute('data-tool-call-id');
            if (!id) return;
            var nameEl = call.querySelector('.tool-call-name');
            calls.push({
                id: id,
                name: (nameEl ? nameEl.textContent : '') || '未知工具',
                status: call.getAttribute('data-status') || ''
            });
        });
        return calls;
    }

    function syncMiniTools() {
        var miniList = byId('miniToolList');
        if (!miniList || !miniMode) return;
        var calls = collectToolCalls();

        var sig = calls.map(function (c) { return c.id + ':' + c.status; }).join(',');
        if (sig !== lastToolSig) {
            lastToolSig = sig;
            miniList.innerHTML = '';
            if (calls.length === 0) {
                var empty = document.createElement('div');
                empty.className = 'mini-session-empty';
                empty.style.width = '30px';
                empty.style.height = '30px';
                empty.style.borderRadius = '10px';
                empty.textContent = '空';
                empty.title = '暂无工具执行';
                miniList.appendChild(empty);
            } else {
                calls.forEach(function (c, idx) {
                    miniList.appendChild(buildToolBubble(c, idx));
                });
            }
            updateMiniToolStats(calls);
        } else {
            updateMiniToolStats(calls);
        }
    }

    function buildToolBubble(c, idx) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'mini-tool-bubble';
        b.style.animationDelay = Math.min(idx * 25, 200) + 'ms';
        b.setAttribute('data-tool-call-id', c.id);
        b.setAttribute('data-status', c.status || '');
        var icon = document.createElement('span');
        icon.className = 'mini-tool-icon';
        b.appendChild(icon);
        try { renderToolInlineIconContent(icon, c.name); } catch (e) { icon.textContent = '🔧'; }
        bindHoverPopover(b, function () { return buildToolPopoverHtml(c.id); });
        b.addEventListener('click', function () { onToolBubbleClick(c.id); });
        return b;
    }

    /* 点击工具气泡：展开完整面板并定位高亮对应工具调用 */
    function onToolBubbleClick(callId) {
        window.toggleToolPanelMini(false);
        setTimeout(function () {
            var call = document.querySelector('.tool-call[data-tool-call-id="' + cssEscape(callId) + '"]');
            if (!call) return;
            var container = byId('toolExecList');
            if (container) {
                var cr = container.getBoundingClientRect();
                var ir = call.getBoundingClientRect();
                container.scrollTop += ir.top - cr.top - 10;
            }
            var body = call.querySelector('.tool-call-body');
            if (body) { body.classList.remove('collapsed'); body.classList.add('open'); }
            var toggle = call.querySelector('.tool-call-toggle');
            if (toggle) toggle.classList.add('open');
            call.classList.remove('flash');
            void call.offsetWidth;
            call.classList.add('flash');
        }, 520);
    }

    function buildToolPopoverHtml(callId) {
        var call = document.querySelector('.tool-call[data-tool-call-id="' + cssEscape(callId) + '"]');
        if (!call) return '<div class="mini-popover-title">工具调用</div>';
        var name = (call.querySelector('.tool-call-name') || {}).textContent || '未知工具';
        var status = call.getAttribute('data-status') || '';
        var statusText = (call.querySelector('.tool-call-status') || {}).textContent || statusLabelMap[status] || status || '未知';
        var html = '<div class="mini-popover-head">'
            + '<span class="mini-popover-title">' + esc(name) + '</span>'
            + '<span class="mini-popover-tag state-' + esc(status) + '">' + esc(statusText) + '</span>'
            + '</div>';
        var argsPre = call.querySelector('.tool-call-args .args-content');
        var argsText = argsPre ? argsPre.textContent : '';
        if (argsText) {
            html += '<div class="mini-popover-row"><span class="mini-popover-label">参数</span></div>'
                + '<pre class="mini-popover-pre">' + esc(argsText) + '</pre>';
        }
        var resultPre = call.querySelector('.tool-call-result .result-content');
        var resultText = resultPre ? resultPre.textContent : '';
        if (resultText) {
            html += '<div class="mini-popover-row" style="margin-top:8px;"><span class="mini-popover-label">输出</span></div>'
                + '<pre class="mini-popover-pre">' + esc(resultText) + '</pre>';
        }
        if (!argsText && !resultText) {
            html += '<div class="mini-popover-row"><span class="mini-popover-label">输出</span>'
                + '<span class="mini-popover-value">暂无输入/输出</span></div>';
        }
        html += '<div class="mini-popover-hint">点击气泡展开完整面板并定位</div>';
        return html;
    }

    /* 工具状态统计：甜甜圈 + 计数 */
    function updateMiniToolStats(calls) {
        var counts = {};
        var active = 0;
        calls.forEach(function (c) {
            counts[c.status] = (counts[c.status] || 0) + 1;
            if (c.status === 'started' || c.status === 'running' || c.status === 'preparing' || c.status === 'approval') active++;
        });

        var countEl = byId('miniToolCount');
        var total = calls.length;
        if (countEl) {
            countEl.textContent = total + (active > 0 ? ' · 活' + active : '');
            countEl.title = '共 ' + total + ' 次工具调用' + (active > 0 ? '，' + active + ' 个进行中' : '');
        }

        if (!miniToolStatsChart || !window.Chart) return;
        var data = [], colors = [], labels = [];
        toolStatusMeta.forEach(function (m) {
            if (counts[m.key]) {
                data.push(counts[m.key]);
                colors.push(m.color);
                labels.push(m.label);
            }
        });
        if (data.length === 0) {
            data = [1]; colors = ['#dfe3ec']; labels = ['暂无'];
        }
        miniToolStatsChart.data.labels = labels;
        miniToolStatsChart.data.datasets[0].data = data;
        miniToolStatsChart.data.datasets[0].backgroundColor = colors;
        miniToolStatsChart.options.plugins.miniCenterText.text = String(total);
        miniToolStatsChart.options.plugins.miniCenterText.color = isDark() ? '#aeb6d3' : '#5a6478';
        miniToolStatsChart.update();
    }

    function buildToolStatsPopoverHtml() {
        var calls = collectToolCalls();
        var counts = {};
        calls.forEach(function (c) { counts[c.status] = (counts[c.status] || 0) + 1; });
        var statsText = (byId('toolExecStats') || {}).textContent || '';
        var m = statsText.match(/共\s*(\d+)\s*次[^\d]*(?:.*?(\d+)\s*\/\s*(\d+|∞))?/);
        var html = '<div class="mini-popover-head"><span class="mini-popover-title">工具执行统计</span></div>';
        if (m) {
            html += '<div class="mini-popover-row"><span class="mini-popover-label">统计</span>'
                + '<span class="mini-popover-value num">共 ' + esc(m[1]) + ' 次 · 本次 ' + esc(m[2] || '0') + '/' + esc(m[3] || '∞') + '</span></div>';
        }
        var hasAny = false;
        toolStatusMeta.forEach(function (meta) {
            if (counts[meta.key]) {
                hasAny = true;
                html += '<div class="mini-popover-row"><span class="mini-popover-label">'
                    + '<span class="mini-popover-legend-dot" style="background:' + meta.color + '"></span></span>'
                    + '<span class="mini-popover-value">' + meta.label + '</span>'
                    + '<span class="mini-popover-value num">' + counts[meta.key] + '</span></div>';
            }
        });
        if (!hasAny) html += '<div class="mini-popover-row"><span class="mini-popover-value">暂无工具调用</span></div>';
        html += '<div class="mini-popover-hint">悬停下方气泡查看参数与输出</div>';
        return html;
    }

    /* ============================================================
     * Token 迷你视图：输入/输出甜甜圈 + 竖向柱状图（实时追加）
     * ============================================================ */
    var tokenState = {
        daily: { input: 0, output: 0, total: 0 },
        chartData: []
    };
    var barsState = { targets: [], disp: [], entries: [], max: 1, raf: null };

    /* 包装完整视图的 token 渲染入口：完整逻辑照旧执行，迷你视图同步取数 */
    if (typeof window.renderTokenChart === 'function') {
        var _origRenderTokenChart = window.renderTokenChart;
        window.renderTokenChart = function (data) {
            _origRenderTokenChart(data);
            tokenState.chartData = (data || []).slice();
            syncMiniToken();
        };
    }
    if (typeof window.updateTokenTitle === 'function') {
        var _origUpdateTokenTitle = window.updateTokenTitle;
        window.updateTokenTitle = function (input, output, total) {
            _origUpdateTokenTitle(input, output, total);
            tokenState.daily = { input: input || 0, output: output || 0, total: total || 0 };
            syncMiniToken();
        };
    }

    function syncMiniToken() {
        if (!miniMode) return;

        /* 甜甜圈：今日输入/输出占比，中心显示总量 */
        if (miniTokenDonut && window.Chart) {
            var d = tokenState.daily;
            var sum = (d.input || 0) + (d.output || 0);
            var data = sum > 0 ? [d.input || 0, d.output || 0] : [1, 0];
            miniTokenDonut.data.datasets[0].data = data;
            miniTokenDonut.data.datasets[0].backgroundColor = sum > 0 ? ['#2196F3', '#4CAF50'] : ['#dfe3ec', '#dfe3ec'];
            miniTokenDonut.options.plugins.miniCenterText.text = fmtNum(d.total || 0);
            miniTokenDonut.options.plugins.miniCenterText.color = isDark() ? '#aeb6d3' : '#5a6478';
            miniTokenDonut.update();
        }
        updateTokenBars();
    }

    /* 竖向柱状图：最近 8 次用量，输入(下/蓝)+输出(上/绿)，新数据缓动生长 */
    function updateTokenBars() {
        var canvas = byId('miniTokenBars');
        if (!canvas) return;
        var entries = (tokenState.chartData || []).slice(-8);
        var max = 1;
        entries.forEach(function (e) {
            var t = (e.inputTokens || 0) + (e.outputTokens || 0);
            if (t > max) max = t;
        });
        barsState.entries = entries;
        barsState.max = max;
        barsState.targets = entries.map(function (e) {
            return ((e.inputTokens || 0) + (e.outputTokens || 0)) / max;
        });
        while (barsState.disp.length < barsState.targets.length) barsState.disp.push(0);
        barsState.disp.length = barsState.targets.length;
        if (barsState.raf) return;
        barsState.raf = requestAnimationFrame(barsFrame);
    }

    function barRect(ctx, x, y, w, h, r) {
        if (h <= 0) return;
        ctx.beginPath();
        if (ctx.roundRect) ctx.roundRect(x, y, w, h, r); else ctx.rect(x, y, w, h);
        ctx.fill();
    }

    function barsFrame() {
        var canvas = byId('miniTokenBars');
        if (!canvas) { barsState.raf = null; return; }
        var ctx = canvas.getContext('2d');
        var W = canvas.width, H = canvas.height, pad = 4;
        var dark = isDark();
        ctx.clearRect(0, 0, W, H);

        var n = barsState.targets.length;
        var gap = 1.5;
        var bw = n > 0 ? Math.max(2, Math.floor((W - pad * 2 - (n - 1) * gap) / n)) : 4;
        var usableH = H - pad * 2 - 3;
        var done = true;

        for (var i = 0; i < n; i++) {
            var target = barsState.targets[i];
            barsState.disp[i] += (target - barsState.disp[i]) * 0.22;
            if (Math.abs(target - barsState.disp[i]) > 0.004) done = false;
            else barsState.disp[i] = target;

            var e = barsState.entries[i] || {};
            var inT = e.inputTokens || 0, outT = e.outputTokens || 0;
            var total = inT + outT;
            var inRatio = total > 0 ? inT / total : 0.5;
            var h = Math.max(barsState.disp[i] * usableH, barsState.disp[i] > 0 ? 2 : 0);
            var hIn = h * inRatio, hOut = h - hIn;
            var x = pad + i * (bw + gap);
            ctx.fillStyle = '#2196F3';
            barRect(ctx, x, H - pad - 3 - hIn, bw, hIn, 1);
            ctx.fillStyle = '#4CAF50';
            barRect(ctx, x, H - pad - 3 - hIn - hOut, bw, hOut, 1);
        }

        /* 基线 */
        ctx.fillStyle = dark ? '#3a3f55' : '#e4e7f0';
        ctx.fillRect(0, H - pad - 2, W, 1);

        barsState.raf = done ? null : requestAnimationFrame(barsFrame);
    }

    function buildTokenPopoverHtml() {
        var d = tokenState.daily;
        var html = '<div class="mini-popover-head"><span class="mini-popover-title">Token 统计</span></div>';
        html += '<div class="mini-popover-row"><span class="mini-popover-label">今日</span>'
            + '<span class="mini-popover-value in">↑ 输入 ' + fmtNum(d.input) + '</span>'
            + '<span class="mini-popover-value out">↓ 输出 ' + fmtNum(d.output) + '</span></div>';
        html += '<div class="mini-popover-row"><span class="mini-popover-label">总量</span>'
            + '<span class="mini-popover-value num">' + fmtNum(d.total) + '</span></div>';
        var last = (tokenState.chartData || []).slice(-1)[0];
        if (last) {
            html += '<div class="mini-popover-section"></div>';
            html += '<div class="mini-popover-row"><span class="mini-popover-label">最新</span>'
                + '<span class="mini-popover-value in">↑ ' + fmtNum(last.inputTokens) + '</span>'
                + '<span class="mini-popover-value out">↓ ' + fmtNum(last.outputTokens) + '</span></div>'
                + '<div class="mini-popover-row"><span class="mini-popover-label">时间</span>'
                + '<span class="mini-popover-value num">' + esc((last.createTime || '').substring(11, 19)) + '</span></div>';
        }
        html += '<div class="mini-popover-legend">'
            + '<span class="mini-popover-legend-item"><span class="mini-popover-legend-dot" style="background:#2196F3"></span>输入</span>'
            + '<span class="mini-popover-legend-item"><span class="mini-popover-legend-dot" style="background:#4CAF50"></span>输出</span>'
            + '</div>';
        html += '<div class="mini-popover-hint">柱状图随会话实时追加（最近 8 次）</div>';
        return html;
    }

    /* ============================================================
     * Chart.js 甜甜圈实例（进入迷你模式时创建，退出时销毁）
     * ============================================================ */
    function centerTextPlugin() {
        return {
            id: 'miniCenterText',
            afterDraw: function (chart, args, opts) {
                if (!opts || opts.text == null) return;
                var ctx = chart.ctx;
                var x = (chart.chartArea.left + chart.chartArea.right) / 2;
                var y = (chart.chartArea.top + chart.chartArea.bottom) / 2;
                ctx.save();
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.font = '700 9px -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif';
                ctx.fillStyle = opts.color || '#5a6478';
                ctx.fillText(String(opts.text), x, y);
                ctx.restore();
            }
        };
    }

    function donutConfig(labels, data, colors) {
        return {
            type: 'doughnut',
            data: {
                labels: labels,
                datasets: [{ data: data, backgroundColor: colors, borderWidth: 1.5, borderColor: isDark() ? '#232637' : '#fff' }]
            },
            options: {
                responsive: false,
                cutout: '64%',
                animation: { duration: 450, easing: 'easeOutQuart' },
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: false }
                }
            },
            plugins: [centerTextPlugin()]
        };
    }

    function initMiniCharts() {
        if (!window.Chart) return;
        destroyMiniCharts();
        var tsc = byId('miniToolStatsChart');
        if (tsc) {
            var cfgT = donutConfig(['暂无'], [1], ['#dfe3ec']);
            cfgT.options.plugins.miniCenterText = { text: '0', color: '#5a6478' };
            miniToolStatsChart = new Chart(tsc, cfgT);
        }
        var tdc = byId('miniTokenDonut');
        if (tdc) {
            var cfgD = donutConfig(['输入', '输出'], [1, 0], ['#dfe3ec', '#dfe3ec']);
            cfgD.options.plugins.miniCenterText = { text: '0', color: '#5a6478' };
            miniTokenDonut = new Chart(tdc, cfgD);
        }
    }

    function destroyMiniCharts() {
        if (miniToolStatsChart) { try { miniToolStatsChart.destroy(); } catch (e) {} miniToolStatsChart = null; }
        if (miniTokenDonut) { try { miniTokenDonut.destroy(); } catch (e) {} miniTokenDonut = null; }
        if (barsState.raf) { cancelAnimationFrame(barsState.raf); barsState.raf = null; }
        barsState.disp = [];
    }

    /* ============================================================
     * 观察完整视图 DOM：变更时增量同步迷你视图
     * ============================================================ */
    var syncScheduled = false;
    function scheduleSync() {
        if (syncScheduled) return;
        syncScheduled = true;
        requestAnimationFrame(function () {
            syncScheduled = false;
            if (!miniMode) { lastSessionSig = null; lastToolSig = null; return; }
            syncMiniSessions();
            syncMiniTools();
            checkPopoverAnchor();
        });
    }

    function startObservers() {
        var sessionList = byId('sessionList');
        if (sessionList && 'MutationObserver' in window) {
            sessionObserver = new MutationObserver(scheduleSync);
            sessionObserver.observe(sessionList, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'data-biz-type'] });
        }
        var toolExecList = byId('toolExecList');
        if (toolExecList && 'MutationObserver' in window) {
            toolObserver = new MutationObserver(scheduleSync);
            toolObserver.observe(toolExecList, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-status'] });
        }
    }

    /* ============================================================
     * 初始化
     * ============================================================ */
    function init() {
        if (byId('toolPanelMini') == null) return; /* 页面结构不匹配则跳过 */
        startObservers();
        bindHoverPopover(byId('miniToolStatsWrap'), buildToolStatsPopoverHtml);
        bindHoverPopover(byId('miniTokenStatsWrap'), buildTokenPopoverHtml);

        /* 恢复上次的模式偏好 */
        if (readPref()) {
            var panel = byId('toolPanel');
            var full = byId('toolPanelFull');
            var mini = byId('toolPanelMini');
            miniMode = true;
            panel.classList.add('tool-panel-mini');
            full.classList.add('hide');
            mini.classList.remove('hide');
            void mini.offsetWidth;
            mini.classList.add('visible');
            initMiniCharts();
            syncMiniSessions();
            syncMiniTools();
            syncMiniToken();
        }
    }

    if (document.readyState === 'complete') {
        init();
    } else {
        window.addEventListener('load', init);
    }
})();
