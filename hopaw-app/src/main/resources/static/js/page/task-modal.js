/**
 * 任务新建/编辑模态框公共脚本（任务看板与项目详情页共用）
 * 依赖：Modal（js/modal.js）、showToast（js/toast.js）、escapeHtml（各页面脚本）
 * 宿主页面可定义 window.onTaskModalSaved()：保存成功后的刷新回调
 */

/* 弹框下拉数据缓存 */
var agentsCache = [];
var projectsCache = [];
var tasksCache = [];
/* 前置任务要求状态（多选）：放出全部状态，按流转顺序排列 */
var PRECONDITION_STATUS_OPTIONS = [
    { key: 'pending', label: '待启动' },
    { key: 'pending_execution', label: '待执行' },
    { key: 'processing', label: '处理中' },
    { key: 'pending_acceptance', label: '待验收' },
    { key: 'completed', label: '已完成' },
    { key: 'failed', label: '失败' }
];
/* 前置任务下拉显示用的任务状态标签 */
var TASK_MODAL_STATUS_LABELS = {
    pending: '待启动',
    pending_execution: '待执行',
    processing: '处理中',
    pending_acceptance: '待验收',
    completed: '已完成',
    failed: '失败'
};
/* 当前编辑中的前置条件：[{ preTaskId, requiredStatus: ['completed', ...] }] */
var preconditionRows = [];

/* ========== 新建/编辑任务 ========== */
/** 打开新建任务弹框；defaultProjectId 用于默认选中所属项目（如项目详情页传入当前项目） */
function showAddTaskModal(defaultProjectId) {
    document.getElementById('taskModalTitle').textContent = '新建任务';
    document.getElementById('taskId').value = '';
    document.getElementById('taskTitle').value = '';
    document.getElementById('taskContent').value = '';
    document.getElementById('taskStartTime').value = '';
    document.getElementById('taskExecStart').value = '';
    document.getElementById('taskExecEnd').value = '';
    populateAgentSelect('');
    populateProjectSelect(defaultProjectId || '');
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
                // 通知宿主页面刷新（看板刷新视图 / 项目页刷新详情）
                if (typeof window.onTaskModalSaved === 'function') {
                    window.onTaskModalSaved(id ? Number(id) : null);
                }
            } else {
                showToast(res.msg || '操作失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('保存任务失败:', err);
            showToast('操作失败', 'error');
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

/* 看板筛选下拉填充（宿主页面无对应元素时跳过） */
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
            Object.keys(TASK_MODAL_STATUS_LABELS).forEach(function (statusKey) {
                (res.data[statusKey] || []).forEach(function (task) {
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
        var statusLabel = TASK_MODAL_STATUS_LABELS[task.status] || '';
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