/**
 * 任务看板页面脚本
 */
var statusList = [
    { key: 'pending', label: '待启动', color: '#6b7280' },
    { key: 'pending_execution', label: '待执行', color: '#f59e0b' },
    { key: 'processing', label: '处理中', color: '#3b82f6' },
    { key: 'pending_acceptance', label: '待验收', color: '#8b5cf6' },
    { key: 'completed', label: '已完成', color: '#10b981' },
    { key: 'failed', label: '失败', color: '#ef4444' }
];

var agentsCache = [];
var projectsCache = [];
var tasksCache = [];

/* 前置任务要求状态（多选） */
var PRECONDITION_STATUS_OPTIONS = [
    { key: 'pending_acceptance', label: '待验收' },
    { key: 'completed', label: '已完成' },
    { key: 'failed', label: '失败' }
];
/* 当前编辑中的前置条件：[{ preTaskId, requiredStatus: ['completed', ...] }] */
var preconditionRows = [];

// 自动刷新倒计时
var REFRESH_INTERVAL_SEC = 5;
var refreshLastTickAt = Date.now();

document.addEventListener('DOMContentLoaded', function () {
    applyFiltersFromUrl();
    loadBoard();
    loadAgents();
    loadProjects();
    loadTasksForPreconditions();
    startRefreshCountdown();
});

/* ========== 解析URL参数，默认选中指定项目（如 /tasks-board?projectId=1） ========== */
function applyFiltersFromUrl() {
    var urlParams = new URLSearchParams(window.location.search);
    var projectId = urlParams.get('projectId');
    if (!projectId) return;
    var select = document.getElementById('boardProjectFilter');
    if (!select) return;
    // 项目列表尚未加载完成，先插入临时选项使选中值生效，列表加载后会被真实选项替换
    var opt = document.createElement('option');
    opt.value = projectId;
    opt.textContent = '项目#' + projectId;
    select.appendChild(opt);
    select.value = projectId;
}

function doSearch() {
    loadBoard();
}

/* ========== 5 秒自动刷新倒计时 ========== */
var isBoardRefreshing = false;

function startRefreshCountdown() {
    refreshLastTickAt = Date.now();
    updateRefreshCountdownText();
    setInterval(function () {
        // 刷新进行中：暂停倒计时
        if (isBoardRefreshing) return;
        var elapsed = Math.floor((Date.now() - refreshLastTickAt) / 1000);
        var remaining = REFRESH_INTERVAL_SEC - elapsed;
        if (remaining <= 0) {
            // 倒计时归零：刷新看板（按钮进入刷新中状态），完成后重置计时基准
            triggerAutoRefresh();
            return;
        }
        updateRefreshCountdownText(remaining);
    }, 1000);
}

/** 倒计时归零触发的自动刷新：刷新期间按钮显示动画，完成后恢复倒计时 */
function triggerAutoRefresh() {
    isBoardRefreshing = true;
    setRefreshingState(true);
    Promise.resolve(loadBoard()).finally(function () {
        isBoardRefreshing = false;
        setRefreshingState(false);
        refreshLastTickAt = Date.now();
        updateRefreshCountdownText(REFRESH_INTERVAL_SEC);
    });
}

/** 手动点击刷新按钮：与自动刷新共用同一流程 */
function manualRefreshBoard() {
    if (isBoardRefreshing) return;
    triggerAutoRefresh();
}

/** 切换刷新中状态：刷新按钮图标旋转 + "刷新中"文案，倒计时暂时隐藏 */
function setRefreshingState(refreshing) {
    var btn = document.getElementById('boardRefreshBtn');
    if (btn) {
        btn.classList.toggle('refreshing', refreshing);
        btn.disabled = refreshing;
        var label = btn.querySelector('.refresh-label');
        if (label) {
            label.textContent = refreshing ? '刷新中' : '刷新';
        }
    }
    var countdown = document.getElementById('refreshCountdown');
    if (countdown) {
        countdown.style.visibility = refreshing ? 'hidden' : 'visible';
    }
}

function updateRefreshCountdownText(remaining) {
    if (typeof remaining === 'undefined') {
        remaining = REFRESH_INTERVAL_SEC;
    }
    var el = document.getElementById('refreshCountdown');
    if (el) {
        el.textContent = remaining + 's 后刷新';
    }
}

function getFilters() {
    var projectEl = document.getElementById('boardProjectFilter');
    var agentEl = document.getElementById('boardAgentFilter');
    return {
        projectId: projectEl ? projectEl.value : '',
        agentId: agentEl ? agentEl.value : ''
    };
}

function loadBoard() {
    var filters = getFilters();
    var url = '/api/workflow/tasks/board';
    var params = [];
    if (filters.projectId) params.push('projectId=' + encodeURIComponent(filters.projectId));
    if (filters.agentId) params.push('agentId=' + encodeURIComponent(filters.agentId));
    if (params.length) url += '?' + params.join('&');

    return fetch(url)
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200) {
                showToast(res.msg || '加载看板失败', 'error');
                renderBoard({});
                return;
            }
            renderBoard(res.data || {});
        })
        .catch(function (err) {
            console.error('加载看板失败:', err);
            showToast('加载看板失败', 'error');
            renderBoard({});
        });
}

function renderBoard(data) {
    data = data || {};
    var container = document.getElementById('tasksBoardColumns');
    container.innerHTML = statusList.map(function (status) {
        var tasks = data[status.key] || [];
        return buildColumn(status, tasks);
    }).join('');
}

function buildColumn(status, tasks) {
    var count = tasks.length;
    var bodyHtml = '';
    if (!count) {
        bodyHtml = '<div class="board-column-empty">暂无任务</div>';
    } else {
        bodyHtml = tasks.map(function (task) {
            return renderTaskCard(task);
        }).join('');
    }

    return '<div class="board-column" data-status="' + status.key + '">' +
        '<div class="board-column-header">' +
            '<div class="board-column-title">' +
                '<span class="column-dot" style="background:' + status.color + ';"></span>' +
                escapeHtml(status.label) +
            '</div>' +
            '<span class="board-column-count">' + count + '</span>' +
        '</div>' +
        '<div class="board-column-body">' + bodyHtml + '</div>' +
    '</div>';
}

function renderTaskCard(task) {
    var title = task.title || '未命名任务';
    var agentName = task.agentName || (task.agentId ? '智能体#' + task.agentId : '未指定');
    var projectName = task.projectName || (task.projectId ? '项目#' + task.projectId : '');
    var timeText = formatTime(task.startTime) || formatTime(task.createTime) || '';

    var projectHtml = projectName
        ? '<div class="task-card-project">' +
            '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>' +
            escapeHtml(projectName) +
          '</div>'
        : '';

    // 处理中的任务不显示编辑、删除按钮
    var actionBtnsHtml = task.status !== 'processing'
        ? '<button class="task-card-edit" onclick="event.stopPropagation(); showEditTaskModal(' + task.id + ')" title="编辑任务">' +
            '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                '<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>' +
                '<path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>' +
            '</svg>' +
          '</button>' +
          '<button class="task-card-delete" onclick="event.stopPropagation(); deleteTask(' + task.id + ')" title="删除任务">' +
            '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                '<polyline points="3 6 5 6 21 6"/>' +
                '<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>' +
            '</svg>' +
          '</button>'
        : '';

    return '<div class="task-card" onclick="window.open(\'/tasks-board/' + task.id + '\', \'_blank\', \'width=900,height=700\')">' +
        actionBtnsHtml +
        '<div class="task-card-title">' + escapeHtml(title) + '</div>' +
        '<div class="task-card-agent">' +
            '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="13" rx="2"/><circle cx="8" cy="12" r="1.5" fill="currentColor"/><circle cx="16" cy="12" r="1.5" fill="currentColor"/></svg>' +
            escapeHtml(agentName) +
        '</div>' +
        projectHtml +
        (timeText ? '<div class="task-card-time">' + escapeHtml(timeText) + '</div>' : '') +
    '</div>';
}

/* ========== 新建/编辑任务 ========== */
function showAddTaskModal() {
    document.getElementById('taskModalTitle').textContent = '新建任务';
    document.getElementById('taskId').value = '';
    document.getElementById('taskTitle').value = '';
    document.getElementById('taskContent').value = '';
    document.getElementById('taskStartTime').value = '';
    document.getElementById('taskExecStart').value = '';
    document.getElementById('taskExecEnd').value = '';
    populateAgentSelect('');
    populateProjectSelect('');
    initPreconditionRows([]);
    populatePreconditionTaskSelect('');
    Modal.open('taskModal');
}

function showEditTaskModal(id) {
    fetch('/api/workflow/tasks/' + id)
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200 || !res.data) {
                showToast(res.msg || '加载任务失败', 'error');
                return;
            }
            var task = res.data;
            document.getElementById('taskModalTitle').textContent = '编辑任务';
            document.getElementById('taskId').value = task.id || '';
            document.getElementById('taskTitle').value = task.title || '';
            document.getElementById('taskContent').value = task.content || '';
            document.getElementById('taskStartTime').value = toDateTimeLocal(task.startTime);
            var execRange = toExecTimeRange(task.startTime, task.executionPeriod);
            document.getElementById('taskExecStart').value = execRange.start;
            document.getElementById('taskExecEnd').value = execRange.end;
            populateAgentSelect(task.agentId || '');
            populateProjectSelect(task.projectId || '');
            initPreconditionRows(task.preconditions || []);
            populatePreconditionTaskSelect(task.id);
            Modal.open('taskModal');
        })
        .catch(function (err) {
            console.error('加载任务失败:', err);
            showToast('加载任务失败', 'error');
        });
}

function closeTaskModal() {
    Modal.close('taskModal');
}

function submitTask() {
    var id = document.getElementById('taskId').value;
    var title = document.getElementById('taskTitle').value.trim();
    var content = document.getElementById('taskContent').value.trim();
    var agentId = document.getElementById('taskAgentId').value;
    var projectId = document.getElementById('taskProjectId').value;
    var startTime = document.getElementById('taskStartTime').value;
    var execStart = document.getElementById('taskExecStart').value;
    var execEnd = document.getElementById('taskExecEnd').value;
    var executionPeriod = calcExecMinutes(execStart, execEnd);

    if (!title) {
        showToast('请输入任务标题', 'error');
        return;
    }
    if (!agentId) {
        showToast('请选择智能体', 'error');
        return;
    }

    var payload = {
        title: title,
        content: content,
        agentId: Number(agentId),
        startTime: startTime || null,
        executionPeriod: executionPeriod
    };
    if (projectId) {
        payload.projectId = Number(projectId);
    }
    // 前置条件：勾选状态为空时不提交该条
    var preconditions = buildPreconditionsPayload();
    if (preconditions === null) {
        return;
    }
    if (preconditions.length) {
        payload.preconditions = preconditions;
    }

    var url = id ? '/api/workflow/tasks/' + id : '/api/workflow/tasks';
    var method = id ? 'PUT' : 'POST';

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast(id ? '任务更新成功' : '任务创建成功', 'success');
                closeTaskModal();
                loadBoard();
            } else {
                showToast(res.msg || '操作失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('保存任务失败:', err);
            showToast('操作失败', 'error');
        });
}

function deleteTask(id) {
    showConfirm('确定要删除该任务吗？').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/tasks/' + id, { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('删除成功', 'success');
                    loadBoard();
                } else {
                    showToast(res.msg || '删除失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('删除任务失败:', err);
                showToast('删除失败', 'error');
            });
    });
}

/* ========== 下拉数据加载 ========== */
function loadAgents() {
    fetch('/api/agents/page?page=1&size=100')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200 && res.data) {
                agentsCache = res.data.list || [];
                populateAgentSelect('');
                populateBoardAgentFilter();
            }
        })
        .catch(function (err) {
            console.error('加载智能体列表失败:', err);
        });
}

function loadProjects() {
    fetch('/api/projects/page?page=1&size=100')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200 && res.data) {
                projectsCache = res.data.list || [];
                populateProjectSelect('');
                populateBoardProjectFilter();
            }
        })
        .catch(function (err) {
            console.error('加载项目列表失败:', err);
        });
}

function populateAgentSelect(selectedId) {
    var select = document.getElementById('taskAgentId');
    if (!select) return;
    var html = '<option value="">请选择智能体</option>';
    agentsCache.forEach(function (agent) {
        var sel = String(agent.id) === String(selectedId) ? ' selected' : '';
        html += '<option value="' + agent.id + '"' + sel + '>' + escapeHtml(agent.name || ('智能体#' + agent.id)) + '</option>';
    });
    select.innerHTML = html;
}

function populateProjectSelect(selectedId) {
    var select = document.getElementById('taskProjectId');
    if (!select) return;
    var html = '<option value="">无关联项目</option>';
    projectsCache.forEach(function (project) {
        var sel = String(project.id) === String(selectedId) ? ' selected' : '';
        html += '<option value="' + project.id + '"' + sel + '>' + escapeHtml(project.name || ('项目#' + project.id)) + '</option>';
    });
    select.innerHTML = html;
}

function populateBoardAgentFilter() {
    var select = document.getElementById('boardAgentFilter');
    if (!select) return;
    var current = select.value;
    var html = '<option value="">全部智能体</option>';
    agentsCache.forEach(function (agent) {
        html += '<option value="' + agent.id + '">' + escapeHtml(agent.name || ('智能体#' + agent.id)) + '</option>';
    });
    select.innerHTML = html;
    select.value = current;
}

function populateBoardProjectFilter() {
    var select = document.getElementById('boardProjectFilter');
    if (!select) return;
    var current = select.value;
    var html = '<option value="">全部项目</option>';
    projectsCache.forEach(function (project) {
        html += '<option value="' + project.id + '">' + escapeHtml(project.name || ('项目#' + project.id)) + '</option>';
    });
    select.innerHTML = html;
    select.value = current;
    // 原选中值不在项目列表中（如项目已删除或URL参数失效）时，回退为全部项目
    if (select.selectedIndex === -1) {
        select.value = '';
    }
}

/* ========== 前置任务条件 ========== */

/** 加载任务列表用于前置任务选择（复用看板接口，按状态展平） */
function loadTasksForPreconditions() {
    fetch('/api/workflow/tasks/board')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200 || !res.data) return;
            var list = [];
            statusList.forEach(function (status) {
                (res.data[status.key] || []).forEach(function (task) {
                    list.push(task);
                });
            });
            tasksCache = list;
        })
        .catch(function (err) {
            console.error('加载任务列表（前置条件）失败:', err);
        });
}

/** 填充前置任务下拉框；编辑模式下排除任务自身；选择了关联项目时仅显示该项目下的任务 */
function populatePreconditionTaskSelect(excludeId) {
    var select = document.getElementById('preconditionTaskSelect');
    if (!select) return;
    var projectEl = document.getElementById('taskProjectId');
    var projectId = projectEl ? projectEl.value : '';
    var html = '<option value="">请选择前置任务</option>';
    tasksCache.forEach(function (task) {
        if (excludeId && String(task.id) === String(excludeId)) {
            return;
        }
        if (projectId && String(task.projectId || '') !== String(projectId)) {
            return;
        }
        var statusInfo = statusList.filter(function (s) { return s.key === task.status; })[0];
        var statusLabel = statusInfo ? statusInfo.label : '';
        html += '<option value="' + task.id + '">' +
            escapeHtml(task.title || ('任务#' + task.id)) +
            (statusLabel ? '（' + statusLabel + '）' : '') +
            '</option>';
    });
    select.innerHTML = html;
}

/** 关联项目变化时刷新前置任务下拉框（按新项目过滤），并移除不在该项目下的已添加前置条件 */
function onTaskProjectChange() {
    var currentId = document.getElementById('taskId').value;
    populatePreconditionTaskSelect(currentId);
    filterPreconditionRowsByProject();
}

/** 选择了关联项目时，仅保留该项目下的前置条件行；未选择项目时不限制 */
function filterPreconditionRowsByProject() {
    var projectEl = document.getElementById('taskProjectId');
    var projectId = projectEl ? projectEl.value : '';
    if (!projectId) return;
    var before = preconditionRows.length;
    preconditionRows = preconditionRows.filter(function (row) {
        var task = tasksCache.filter(function (t) { return t.id === row.preTaskId; })[0];
        return task && String(task.projectId || '') === String(projectId);
    });
    if (preconditionRows.length !== before) {
        renderPreconditionList();
    }
}

/** 初始化前置条件编辑行（编辑模式回显后端配置） */
function initPreconditionRows(preconditions) {
    preconditionRows = (preconditions || []).map(function (pc) {
        return {
            preTaskId: pc.preTaskId,
            title: pc.preTaskTitle || '',
            requiredStatus: (pc.requiredStatus || '').split(',').filter(function (s) { return s; })
        };
    });
    renderPreconditionList();
}

/** 渲染前置条件列表：每行显示前置任务标题 + 要求状态多选 + 删除按钮 */
function renderPreconditionList() {
    var container = document.getElementById('preconditionList');
    if (!container) return;
    if (!preconditionRows.length) {
        container.innerHTML = '';
        return;
    }
    container.innerHTML = preconditionRows.map(function (row, idx) {
        var checkboxes = PRECONDITION_STATUS_OPTIONS.map(function (opt) {
            var checked = row.requiredStatus.indexOf(opt.key) >= 0 ? ' checked' : '';
            return '<label class="precondition-status-item">' +
                '<input type="checkbox"' + checked + ' onchange="togglePreconditionStatus(' + idx + ', \'' + opt.key + '\')">' +
                '<span>' + opt.label + '</span>' +
                '</label>';
        }).join('');
        return '<div class="precondition-row">' +
            '<div class="precondition-row-head">' +
                '<span class="precondition-task-title">#' + row.preTaskId + ' ' + escapeHtml(row.title || '') + '</span>' +
                '<button type="button" class="precondition-remove" onclick="removePrecondition(' + idx + ')" title="移除该前置任务">&times;</button>' +
            '</div>' +
            '<div class="precondition-status-group">' + checkboxes + '</div>' +
        '</div>';
    }).join('');
}

/** 下拉选择后添加一条前置条件 */
function addPrecondition() {
    var select = document.getElementById('preconditionTaskSelect');
    var val = select ? select.value : '';
    if (!val) {
        showToast('请选择前置任务', 'error');
        return;
    }
    var preTaskId = Number(val);
    var exists = preconditionRows.some(function (row) { return row.preTaskId === preTaskId; });
    if (exists) {
        showToast('该前置任务已添加', 'error');
        return;
    }
    var task = tasksCache.filter(function (t) { return t.id === preTaskId; })[0];
    preconditionRows.push({
        preTaskId: preTaskId,
        title: task ? (task.title || '') : '',
        requiredStatus: []
    });
    select.value = '';
    renderPreconditionList();
}

/** 切换某条前置条件的要求状态勾选 */
function togglePreconditionStatus(idx, statusKey) {
    var row = preconditionRows[idx];
    if (!row) return;
    var pos = row.requiredStatus.indexOf(statusKey);
    if (pos >= 0) {
        row.requiredStatus.splice(pos, 1);
    } else {
        row.requiredStatus.push(statusKey);
    }
}

/** 移除一条前置条件 */
function removePrecondition(idx) {
    preconditionRows.splice(idx, 1);
    renderPreconditionList();
}

/** 构造提交给后端的 preconditions 数组；存在未勾选状态的行时返回 null 并提示 */
function buildPreconditionsPayload() {
    for (var i = 0; i < preconditionRows.length; i++) {
        var row = preconditionRows[i];
        if (!row.requiredStatus.length) {
            showToast('前置任务「' + (row.title || '#' + row.preTaskId) + '」请至少勾选一个要求状态', 'error');
            return null;
        }
    }
    return preconditionRows.map(function (row) {
        return {
            preTaskId: row.preTaskId,
            requiredStatus: row.requiredStatus.join(',')
        };
    });
}

/* ========== 工具函数 ========== */
function formatTime(timeStr) {
    if (!timeStr) return '';
    var d = new Date(timeStr);
    if (isNaN(d.getTime())) return '';
    var pad = function (n) { return n < 10 ? '0' + n : n; };
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
        ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}

function toDateTimeLocal(timeStr) {
    if (!timeStr) return '';
    var d = new Date(timeStr);
    if (isNaN(d.getTime())) return '';
    var pad = function (n) { return n < 10 ? '0' + n : n; };
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
        'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}

/** 由 startTime + executionPeriod（分钟）反推执行时段 HH:mm~HH:mm */
function toExecTimeRange(startTime, execMinutes) {
    if (!startTime) return { start: '', end: '' };
    var d = new Date(startTime);
    if (isNaN(d.getTime())) return { start: '', end: '' };
    var pad = function (n) { return n < 10 ? '0' + n : n; };
    var start = pad(d.getHours()) + ':' + pad(d.getMinutes());
    if (!execMinutes || execMinutes <= 0) return { start: start, end: '' };
    var endD = new Date(d.getTime() + execMinutes * 60 * 1000);
    var end = pad(endD.getHours()) + ':' + pad(endD.getMinutes());
    return { start: start, end: end };
}

/** 由两个 HH:mm 时间计算分钟差，返回整数或 null */
function calcExecMinutes(execStart, execEnd) {
    if (!execStart || !execEnd) return null;
    var s = execStart.split(':');
    var e = execEnd.split(':');
    if (s.length < 2 || e.length < 2) return null;
    var sMin = Number(s[0]) * 60 + Number(s[1]);
    var eMin = Number(e[0]) * 60 + Number(e[1]);
    if (isNaN(sMin) || isNaN(eMin)) return null;
    var diff = eMin - sMin;
    if (diff <= 0) return null;
    return diff;
}

function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
