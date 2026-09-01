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

/* 任务弹框保存成功后的刷新回调（供公共 task-modal.js 调用） */
window.onTaskModalSaved = function () {
    loadCurrentViewData();
};

/* ========== 视图切换：看板/列表 ========== */
var currentView = 'board';
var TABLE_PAGE_SIZE = 15;
var tablePage = 1;
var tableTotalPages = 1;

/* 表格视图状态元数据（含看板列没有的已关闭/已驳回） */
var statusMetaMap = {};
statusList.forEach(function (s) { statusMetaMap[s.key] = s; });
statusMetaMap.closed = { key: 'closed', label: '已关闭', color: '#9ca3af' };
statusMetaMap.rejected = { key: 'rejected', label: '已驳回', color: '#b45309' };

// 自动刷新倒计时
var REFRESH_INTERVAL_SEC = 5;
var refreshLastTickAt = Date.now();

document.addEventListener('DOMContentLoaded', function () {
    applyFiltersFromUrl();
    // 恢复上次的视图偏好（默认看板），并加载对应视图数据
    switchView(localStorage.getItem('tasksBoardView') || 'board');
    loadAgents();
    loadProjects();
    loadTasksForPreconditions();
    startRefreshCountdown();
    // 订阅全局通知：任务状态变更时复用倒计时刷新流程刷新当前视图
    connectNoticeWebSocket(handleBoardNotice);
});

/** 全局通知处理：任务状态变更时复用倒计时刷新流程立即刷新一次（含防重入与计时基准重置） */
function handleBoardNotice(data) {
    if (!data || data.type !== 'task' || data.subtype !== 'status_change') return;
    manualRefreshBoard();
}

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
    // 切换筛选后表格回到第 1 页
    tablePage = 1;
    loadCurrentViewData();
}

/* 按当前视图加载对应数据 */
function loadCurrentViewData() {
    return currentView === 'table' ? loadTaskTable() : loadBoard();
}

/* 切换看板/列表视图（偏好持久化到 localStorage） */
function switchView(view) {
    if (view !== 'board' && view !== 'table') {
        view = 'board';
    }
    currentView = view;
    try {
        localStorage.setItem('tasksBoardView', view);
    } catch (e) { /* 隐私模式下忽略 */ }

    var btnBoard = document.getElementById('viewBtnBoard');
    var btnTable = document.getElementById('viewBtnTable');
    var boardEl = document.getElementById('tasksBoardColumns');
    var tableEl = document.getElementById('tasksTableView');
    if (btnBoard) btnBoard.classList.toggle('active', view === 'board');
    if (btnTable) btnTable.classList.toggle('active', view === 'table');
    if (boardEl) boardEl.style.display = view === 'board' ? '' : 'none';
    if (tableEl) tableEl.style.display = view === 'table' ? '' : 'none';

    loadCurrentViewData();
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
    Promise.resolve(loadCurrentViewData()).finally(function () {
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

    // 处理中的任务不显示编辑、关闭、删除按钮（右侧竖向排列）
    var actionBtnsHtml = task.status !== 'processing'
        ? '<div class="task-card-actions">' +
            '<button class="task-card-edit" onclick="event.stopPropagation(); showEditTaskModal(' + task.id + ')" title="编辑任务">' +
                '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                    '<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>' +
                    '<path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>' +
                '</svg>' +
            '</button>' +
            '<button class="task-card-close" onclick="event.stopPropagation(); closeBoardTask(' + task.id + ')" title="关闭任务">' +
                '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                    '<circle cx="12" cy="12" r="10"/>' +
                    '<line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>' +
                '</svg>' +
            '</button>' +
            '<button class="task-card-delete" onclick="event.stopPropagation(); deleteTask(' + task.id + ')" title="删除任务">' +
                '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                    '<polyline points="3 6 5 6 21 6"/>' +
                    '<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>' +
                '</svg>' +
            '</button>' +
          '</div>'
        : '';

    return '<div class="task-card' + (task.status === 'processing' ? ' task-card-processing' : '') + '" onclick="window.open(\'/tasks-board/' + task.id + '\', \'_blank\', \'width=900,height=700\')">' +
        actionBtnsHtml +
        '<div class="task-card-title">' + escapeHtml(title) + '</div>' +
        '<div class="task-card-agent">' +
            '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="13" rx="2"/><circle cx="8" cy="12" r="1.5" fill="currentColor"/><circle cx="16" cy="12" r="1.5" fill="currentColor"/></svg>' +
            escapeHtml(agentName) +
        '</div>' +
        taskCreatorHtml(task) +
        projectHtml +
        (timeText ? '<div class="task-card-time">' + escapeHtml(timeText) + '</div>' : '') +
    '</div>';
}

/**
 * 任务创建者展示：智能体创建显示智能体名称（机器人图标），用户创建显示用户信息（人形图标）。
 */
function taskCreatorHtml(task) {
    var isAgent = task.creatorType === 'agent';
    var name;
    if (isAgent) {
        name = task.creatorAgentName || (task.creatorAgentId ? '智能体#' + task.creatorAgentId : '智能体');
    } else {
        name = task.creatorName || '用户';
    }
    var icon = isAgent
        ? '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="8" width="16" height="12" rx="2"/><circle cx="9" cy="13" r="1.2" fill="currentColor"/><circle cx="15" cy="13" r="1.2" fill="currentColor"/><path d="M12 8V5"/><circle cx="12" cy="3.5" r="1.2"/></svg>'
        : '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>';
    return '<div class="task-card-creator ' + (isAgent ? 'creator-agent' : 'creator-user') + '">' + icon +
        '<span>' + escapeHtml(name) + '</span></div>';
}

/* ========== 列表视图：按创建时间倒序分页展示所有任务 ========== */
function loadTaskTable() {
    var filters = getFilters();
    var params = ['page=' + tablePage, 'size=' + TABLE_PAGE_SIZE];
    if (filters.projectId) params.push('projectId=' + encodeURIComponent(filters.projectId));
    if (filters.agentId) params.push('agentId=' + encodeURIComponent(filters.agentId));

    return fetch('/api/workflow/tasks/page?' + params.join('&'))
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200) {
                showToast(res.msg || '加载任务列表失败', 'error');
                renderTableRows([]);
                renderTablePagination(0);
                return;
            }
            var data = res.data || {};
            renderTableRows(data.list || []);
            renderTablePagination(data.total || 0);
        })
        .catch(function (err) {
            console.error('加载任务列表失败:', err);
            showToast('加载任务列表失败', 'error');
        });
}

function renderTableRows(list) {
    var tbody = document.getElementById('tasksTableBody');
    if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="table-empty">暂无任务</td></tr>';
        return;
    }
    tbody.innerHTML = list.map(function (task) {
        var meta = statusMetaMap[task.status] || { label: task.status, color: '#999' };
        var title = task.title || '未命名任务';
        var agentName = task.agentName || (task.agentId ? '智能体#' + task.agentId : '未指定');
        var projectName = task.projectName || (task.projectId ? '项目#' + task.projectId : '-');
        var startTime = formatTime(task.startTime) || '-';
        var createTime = formatTime(task.createTime) || '-';

        // 操作列：处理中不可操作；已关闭不显示关闭按钮
        var actions = '';
        if (task.status !== 'processing') {
            actions += '<button class="table-action-btn" onclick="event.stopPropagation(); showEditTaskModal(' + task.id + ')">编辑</button>';
            if (task.status !== 'closed') {
                actions += '<button class="table-action-btn warn" onclick="event.stopPropagation(); closeBoardTask(' + task.id + ')">关闭</button>';
            }
            actions += '<button class="table-action-btn danger" onclick="event.stopPropagation(); deleteTask(' + task.id + ')">删除</button>';
        } else {
            actions = '<span class="table-action-muted">执行中</span>';
        }

        return '<tr onclick="window.open(\'/tasks-board/' + task.id + '\', \'_blank\', \'width=900,height=700\')" title="点击查看任务详情">' +
            '<td><span class="table-task-id">#' + task.id + '</span><span class="table-task-title">' + escapeHtml(title) + '</span></td>' +
            '<td><span class="table-status-badge" style="background:' + meta.color + '">' + escapeHtml(meta.label) + '</span></td>' +
            '<td>' + escapeHtml(agentName) + '</td>' +
            '<td>' + taskCreatorHtml(task) + '</td>' +
            '<td>' + escapeHtml(projectName) + '</td>' +
            '<td class="td-time">' + startTime + '</td>' +
            '<td class="td-time">' + createTime + '</td>' +
            '<td class="td-actions">' + actions + '</td>' +
        '</tr>';
    }).join('');
}

function renderTablePagination(total) {
    tableTotalPages = Math.max(1, Math.ceil(total / TABLE_PAGE_SIZE));
    if (tablePage > tableTotalPages) {
        tablePage = tableTotalPages;
    }
    var el = document.getElementById('tablePagination');
    if (total <= TABLE_PAGE_SIZE) {
        el.innerHTML = total ? '<span class="pagination-info">共 ' + total + ' 条</span>' : '';
        return;
    }
    el.innerHTML =
        '<span class="pagination-info">共 ' + total + ' 条 · 第 ' + tablePage + '/' + tableTotalPages + ' 页</span>' +
        '<button class="pagination-btn" ' + (tablePage <= 1 ? 'disabled' : '') + ' onclick="goToTablePage(' + (tablePage - 1) + ')">上一页</button>' +
        '<button class="pagination-btn" ' + (tablePage >= tableTotalPages ? 'disabled' : '') + ' onclick="goToTablePage(' + (tablePage + 1) + ')">下一页</button>';
}

function goToTablePage(page) {
    if (page < 1 || page > tableTotalPages || page === tablePage) return;
    tablePage = page;
    loadTaskTable();
}

/* ========== 任务删除/关闭 ========== */
function deleteTask(id) {
    showConfirm('确定要删除该任务吗？').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/tasks/' + id, { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('删除成功', 'success');
                    loadCurrentViewData();
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

// 关闭任务：除处理中外的任务均可关闭，关闭后从看板移除
function closeBoardTask(id) {
    showConfirm('确定要关闭该任务吗？关闭后将从看板移除，不再执行。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/tasks/' + id + '/close', { method: 'PUT' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('任务已关闭', 'success');
                    loadCurrentViewData();
                } else {
                    showToast(res.msg || '关闭失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('关闭任务失败:', err);
                showToast('关闭失败', 'error');
            });
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

function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
