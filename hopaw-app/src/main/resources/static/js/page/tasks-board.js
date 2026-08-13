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
var pendingAttachmentIds = []; // 模态框中已上传待绑定的附件ID
var pendingAttachmentDetails = []; // 模态框中附件详情（用于显示名称）

document.addEventListener('DOMContentLoaded', function () {
    loadBoard();
    loadAgents();
    loadProjects();
});

function doSearch() {
    loadBoard();
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

    fetch(url)
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

    // 处理中的任务不显示删除按钮
    var deleteBtnHtml = task.status !== 'processing'
        ? '<button class="task-card-delete" onclick="event.stopPropagation(); deleteTask(' + task.id + ')" title="删除任务">' +
            '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                '<polyline points="3 6 5 6 21 6"/>' +
                '<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>' +
            '</svg>' +
          '</button>'
        : '';

    return '<div class="task-card" onclick="window.open(\'/tasks-board/' + task.id + '\', \'_blank\', \'width=900,height=700\')">' +
        deleteBtnHtml +
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
    pendingAttachmentIds = [];
    pendingAttachmentDetails = [];
    renderTaskModalAttList();
    populateAgentSelect('');
    populateProjectSelect('');
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
            // 编辑模式下加载已关联的附件
            pendingAttachmentIds = [];
            pendingAttachmentDetails = [];
            loadTaskModalAttachments(id);
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
                var taskId = id || (res.data && res.data.id);
                if (taskId && pendingAttachmentIds.length) {
                    bindPendingAttachments(taskId);
                } else {
                    showToast(id ? '任务更新成功' : '任务创建成功', 'success');
                    closeTaskModal();
                    loadBoard();
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
}

/* ========== 模态框附件上传 ========== */
function triggerTaskModalUpload() {
    document.getElementById('taskModalFileInput').click();
}

function onTaskModalFileSelected(input) {
    if (!input.files || !input.files.length) return;
    var formData = new FormData();
    for (var i = 0; i < input.files.length; i++) {
        formData.append('files', input.files[i]);
    }
    formData.append('source', 'task');

    fetch('/api/attachments/upload', {
        method: 'POST',
        body: formData
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200 && res.data) {
                var list = Array.isArray(res.data) ? res.data : [res.data];
                list.forEach(function (att) {
                    pendingAttachmentIds.push(att.id);
                    pendingAttachmentDetails.push(att);
                });
                renderTaskModalAttList();
                showToast('上传成功', 'success');
            } else {
                showToast(res.msg || '上传失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('上传附件失败:', err);
            showToast('上传失败', 'error');
        })
        .finally(function () {
            input.value = '';
        });
}

function loadTaskModalAttachments(taskId) {
    fetch('/api/workflow/tasks/' + taskId + '/attachments')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200 && res.data) {
                pendingAttachmentIds = res.data.map(function (att) { return att.attachmentId; });
                pendingAttachmentDetails = res.data.slice();
                renderTaskModalAttList();
            }
        })
        .catch(function (err) {
            console.error('加载任务附件失败:', err);
        });
}

function renderTaskModalAttList() {
    var container = document.getElementById('taskModalAttList');
    if (!pendingAttachmentDetails.length) {
        container.innerHTML = '';
        return;
    }
    var iconMap = {
        image: '🖼️', video: '🎬', audio: '🎵',
        pdf: '📄', markdown: '📝', text: '📃', file: '📦'
    };
    container.innerHTML = pendingAttachmentDetails.map(function (att, idx) {
        var icon = iconMap[att.fileType] || '📦';
        var attId = att.attachmentId || att.id;
        return '<div class="task-modal-att-item">' +
            '<span class="att-icon">' + icon + '</span>' +
            '<span class="att-name" title="' + escapeHtml(att.originalName || '') + '">' + escapeHtml(att.originalName || ('附件#' + attId)) + '</span>' +
            '<button type="button" class="att-remove" onclick="removePendingAttachment(' + idx + ')">&times;</button>' +
        '</div>';
    }).join('');
}

function removePendingAttachment(idx) {
    pendingAttachmentIds.splice(idx, 1);
    pendingAttachmentDetails.splice(idx, 1);
    renderTaskModalAttList();
}

function bindPendingAttachments(taskId) {
    fetch('/api/workflow/tasks/' + taskId + '/attachments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ attachmentIds: pendingAttachmentIds })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            showToast('任务保存成功', 'success');
            closeTaskModal();
            loadBoard();
        })
        .catch(function (err) {
            console.error('绑定附件失败:', err);
            showToast('任务已保存，但附件绑定失败', 'error');
            closeTaskModal();
            loadBoard();
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
