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
    if (currentView === 'table') return loadTaskTable();
    if (currentView === 'graph') return loadGraph();
    return loadBoard();
}

/* 切换看板/列表/画布视图（偏好持久化到 localStorage） */
function switchView(view) {
    if (view !== 'board' && view !== 'table' && view !== 'graph') {
        view = 'board';
    }
    currentView = view;
    try {
        localStorage.setItem('tasksBoardView', view);
    } catch (e) { /* 隐私模式下忽略 */ }

    var btnBoard = document.getElementById('viewBtnBoard');
    var btnTable = document.getElementById('viewBtnTable');
    var btnGraph = document.getElementById('viewBtnGraph');
    var boardEl = document.getElementById('tasksBoardColumns');
    var tableEl = document.getElementById('tasksTableView');
    var graphEl = document.getElementById('tasksGraphView');
    if (btnBoard) btnBoard.classList.toggle('active', view === 'board');
    if (btnTable) btnTable.classList.toggle('active', view === 'table');
    if (btnGraph) btnGraph.classList.toggle('active', view === 'graph');
    if (boardEl) boardEl.style.display = view === 'board' ? '' : 'none';
    if (tableEl) tableEl.style.display = view === 'table' ? '' : 'none';
    if (graphEl) graphEl.style.display = view === 'graph' ? '' : 'none';

    if (view === 'graph') {
        // 画布视图必须选中一个项目：未选中时自动选第一个
        ensureGraphProjectSelected(0);
        loadGraph();
        return;
    }
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

/* ========== 画布视图：项目任务依赖关系图（LogicFlow） ========== */
var graphLf = null;
var graphLfInited = false;
/* 任务节点位置缓存：taskId -> {x, y}，自动刷新/数据变更后保留用户拖拽的位置 */
var graphNodePositions = {};
/* 上次渲染数据签名（项目+任务ID+状态+依赖），签名不变则跳过重绘，避免打断交互 */
var graphLastSignature = '';
/* 上次加载的项目ID：切换项目时清空位置缓存并重置视图变换，避免节点留在视口外找不到 */
var graphLastProjectId = null;

var GRAPH_NODE_W = 220;
var GRAPH_NODE_H = 76;
var GRAPH_LAYER_GAP = 300;
var GRAPH_ROW_GAP = 110;

/**
 * 画布视图必须选中项目：未选中时自动选第一个项目。
 * 项目下拉可能尚未加载完成（重试机制，最多约 5 秒）。
 */
function ensureGraphProjectSelected(retryCount) {
    var select = document.getElementById('boardProjectFilter');
    if (!select) return;
    if (select.value) return;
    if (select.options.length > 1) {
        select.selectedIndex = 1; // 跳过"全部项目"占位项
        return;
    }
    if (retryCount < 16) {
        setTimeout(function () {
            ensureGraphProjectSelected(retryCount + 1);
            if (currentView === 'graph') loadGraph();
        }, 300);
    }
}

function graphShowEmptyTip(text) {
    var tip = document.getElementById('graphEmptyTip');
    var canvas = document.getElementById('graphCanvas');
    if (tip) {
        tip.textContent = text;
        tip.style.display = text ? '' : 'none';
    }
    if (canvas) canvas.style.visibility = text ? 'hidden' : 'visible';
}

function loadGraph() {
    var filters = getFilters();
    if (!filters.projectId) {
        graphShowEmptyTip('画布视图需要选择一个项目，暂无可用项目');
        return Promise.resolve();
    }
    // 切换项目：清空节点位置缓存、失效签名并重置视图变换，重新自动布局定位
    if (graphLastProjectId !== null && String(graphLastProjectId) !== String(filters.projectId)) {
        graphNodePositions = {};
        graphLastSignature = '';
        if (graphLf) {
            try {
                graphLf.resetZoom();
                graphLf.resetTranslate();
            } catch (e) { /* 忽略 */ }
        }
    }
    graphLastProjectId = filters.projectId;
    return fetch('/api/workflow/tasks/graph?projectId=' + encodeURIComponent(filters.projectId))
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200) {
                graphShowEmptyTip(res.msg || '加载画布数据失败');
                return;
            }
            renderGraph(res.data || []);
        })
        .catch(function (err) {
            console.error('加载画布数据失败:', err);
            graphShowEmptyTip('加载画布数据失败');
        });
}

/**
 * 注册画布任务节点（HtmlNode）：标题 + 状态徽标 + 智能体。
 * 注意：LogicFlow UMD 包不导出 HtmlNode/HtmlNodeModel 全局静态属性，
 * 基类只能通过 register 回调参数获取（官方推荐的函数式注册）。
 */
function registerGraphTaskNode(lf) {
    lf.register('graphTask', function (params) {
        class GraphTaskNodeView extends params.HtmlNode {
            setHtml(rootNode) {
                var props = (this.props && this.props.model && this.props.model.properties) || {};
                rootNode.innerHTML = buildGraphNodeHtml(props);
            }
        }

        class GraphTaskModel extends params.HtmlNodeModel {
            setAttributes() {
                this.width = GRAPH_NODE_W;
                this.height = GRAPH_NODE_H;
                this.anchorsOffset = [];
            }
        }

        return { view: GraphTaskNodeView, model: GraphTaskModel };
    });
}

/** 任务节点 HTML（状态色带 + 标题 + 状态徽标 + 智能体） */
function buildGraphNodeHtml(props) {
    var meta = statusMetaMap[props.status] || { label: props.status || '未知', color: '#9ca3af' };
    var title = props.title || ('任务#' + props.taskId);
    var agentName = props.agentName || (props.agentId ? '智能体#' + props.agentId : '未指定');
    return '<div class="gt-node" data-gt-status="' + escapeHtml(props.status || '') + '">' +
        '<div class="gt-node-color" style="background:' + meta.color + '"></div>' +
        '<div class="gt-node-body">' +
            '<div class="gt-node-title" title="' + escapeHtml(title) + '">' + escapeHtml(title) + '</div>' +
            '<div class="gt-node-meta">' +
                '<span class="gt-node-status" style="background:' + meta.color + '">' + escapeHtml(meta.label) + '</span>' +
                '<span class="gt-node-agent" title="' + escapeHtml(agentName) + '">' + escapeHtml(agentName) + '</span>' +
            '</div>' +
        '</div>' +
    '</div>';
}

/**
 * 依赖分层布局：无前置依赖的任务在第 0 层，其余取前置任务最大层级 + 1（带环保护）
 */
function computeGraphLayout(tasks, edges) {
    var byId = {};
    tasks.forEach(function (t) { byId[t.id] = true; });
    var deps = {};
    edges.forEach(function (e) {
        if (byId[e.from] && byId[e.to]) {
            (deps[e.to] = deps[e.to] || []).push(e.from);
        }
    });

    var depth = {};
    function getDepth(id, stack) {
        if (depth[id] !== undefined) return depth[id];
        var max = 0;
        (deps[id] || []).forEach(function (p) {
            if (stack.indexOf(p) >= 0) return; // 环保护
            stack.push(p);
            var d = getDepth(p, stack) + 1;
            stack.pop();
            if (d > max) max = d;
        });
        depth[id] = max;
        return max;
    }

    var layers = {};
    tasks.forEach(function (t) {
        var d = getDepth(t.id, []);
        (layers[d] = layers[d] || []).push(t.id);
    });

    var positions = {};
    Object.keys(layers).forEach(function (d) {
        layers[d].sort(function (a, b) { return a - b; }).forEach(function (id, idx) {
            positions[id] = {
                x: 60 + Number(d) * GRAPH_LAYER_GAP,
                y: 60 + idx * GRAPH_ROW_GAP
            };
        });
    });
    return positions;
}

/** 数据签名：项目/任务ID/状态/依赖变化才重绘画布 */
function computeGraphSignature(tasks) {
    var filters = getFilters();
    var parts = ['p:' + filters.projectId];
    tasks.forEach(function (t) {
        parts.push(t.id + ':' + t.status + ':' + (t.title || ''));
        (t.preconditions || []).forEach(function (pc) {
            parts.push('e:' + pc.preTaskId + '-' + t.id);
        });
    });
    return parts.join('|');
}

/** 渲染画布：签名不变跳过；重绘前保留节点位置 */
function renderGraph(tasks) {
    if (!tasks.length) {
        graphShowEmptyTip('该项目暂无任务，可先新建任务');
        return;
    }
    graphShowEmptyTip('');

    var signature = computeGraphSignature(tasks);
    if (graphLfInited && signature === graphLastSignature) {
        return; // 数据无变化，不打断用户交互（拖拽/缩放状态保留）
    }
    graphLastSignature = signature;

    var canvasEl = document.getElementById('graphCanvas');
    if (!canvasEl) return;

    if (!graphLfInited) {
        graphLf = new LogicFlow({
            container: canvasEl,
            grid: { size: 12, visible: true, type: 'dot', config: { color: '#d8dde6', thickness: 1 } },
            edgeType: 'polyline',
            keyboard: { enabled: false },
            adjustEdge: false,        // 禁止拖拽调整连线
            adjustNodePosition: true, // 任务节点可拖拽
            hideAnchors: true,        // 隐藏锚点，避免误创建新连线
            nodeTextEdit: false,
            edgeTextEdit: false,
            hoverOutline: false,
            history: false
        });
        graphLf.setTheme({
            polyline: { stroke: '#94a3b8', strokeWidth: 1.6 },
            arrow: { offset: 10, verticalLength: 5 },
            outline: { stroke: 'transparent', hover: 'transparent' }
        });
        registerGraphTaskNode(graphLf);
        graphLf.on('node:click', function (e) {
            var props = e.data && e.data.properties;
            if (props && props.taskId) {
                window.open('/tasks-board/' + props.taskId, '_blank', 'width=900,height=700');
            }
        });
        graphLfInited = true;
    } else {
        syncGraphPositions();
    }

    // 依赖边：前置任务 -> 当前任务（方向即依赖方向），前置任务不在项目内则跳过
    var byId = {};
    tasks.forEach(function (t) { byId[t.id] = t; });
    var edges = [];
    tasks.forEach(function (t) {
        (t.preconditions || []).forEach(function (pc) {
            if (pc.preTaskId && byId[pc.preTaskId]) {
                edges.push({ from: pc.preTaskId, to: t.id });
            }
        });
    });

    // 布局：优先沿用已保存位置（用户拖拽过），新任务用自动布局
    var autoPositions = computeGraphLayout(tasks, edges);
    var nodes = tasks.map(function (t) {
        var pos = graphNodePositions[t.id] || autoPositions[t.id] || { x: 60, y: 60 };
        return {
            id: 'task-' + t.id,
            type: 'graphTask',
            x: pos.x,
            y: pos.y,
            properties: {
                taskId: t.id,
                title: t.title,
                status: t.status,
                agentId: t.agentId,
                agentName: t.agentName
            }
        };
    });
    var lfEdges = edges.map(function (e) {
        return {
            id: 'edge-' + e.from + '-' + e.to,
            type: 'polyline',
            sourceNodeId: 'task-' + e.from,
            targetNodeId: 'task-' + e.to
        };
    });

    graphLf.render({ nodes: nodes, edges: lfEdges });
    if (!Object.keys(graphNodePositions).length) {
        graphFitView();
    }
}

/** 从画布同步节点位置到缓存（刷新重绘前调用） */
function syncGraphPositions() {
    if (!graphLf) return;
    var data = graphLf.getGraphData();
    (data.nodes || []).forEach(function (n) {
        if (n.id && n.id.indexOf('task-') === 0 && n.properties && n.properties.taskId) {
            graphNodePositions[n.properties.taskId] = { x: n.x, y: n.y };
        }
    });
}

/** 视图复位：重置缩放与平移，节点回到初始自动布局位置 */
function graphFitView() {
    if (!graphLf) return;
    try {
        graphLf.resetZoom();
        graphLf.resetTranslate();
    } catch (e) { /* 忽略内部结构差异 */ }
}

/** 工具栏缩放（LogicFlow zoom 语义：true 放大一档 / false 缩小一档） */
function graphZoom(delta) {
    if (!graphLf) return;
    graphLf.zoom(delta > 0);
}

/** 重排：清除位置缓存并按依赖层级重新自动布局 */
function graphResetLayout() {
    graphNodePositions = {};
    graphLastSignature = '';
    if (currentView === 'graph') {
        loadGraph();
    }
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
