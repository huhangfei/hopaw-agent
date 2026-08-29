/**
 * 项目管理页面脚本（左右布局）
 */
var currentPage = 1;
var totalPages = 1;
var pageSize = 20;
var currentKeyword = '';
var searchTimer = null;
var currentProjectId = null;     // 当前选中的项目ID
var currentProject = null;        // 当前选中的项目对象
var logsExpanded = true;          // 操作日志展开状态（默认展开）
var tasksExpanded = true;         // 项目任务展开状态（默认展开）
var tokenExpanded = true;         // Token 用量展开状态（默认展开）
var logsCache = [];               // 操作日志缓存（用于类型切换时重渲染）
var logFilter = 'all';            // 日志过滤类型：all=全部 / important=重要
var LOGS_PAGE_SIZE = 10;          // 操作日志分页大小
var logsPage = 1;                 // 操作日志当前页码
var logsTotalPages = 1;           // 操作日志总页数
var projectSessionIds = [];       // 当前项目关联的会话ID集合：用于匹配 WebSocket 消息
var projectWs = null;             // Token 用量监听 WebSocket
var currentProjectAgentSessionId = ''; // 项目管理智能体会话ID：用于 WebSocket 消息匹配
// 空间目录编辑按钮铅笔图标（与迭代提示词编辑按钮风格一致）
var PENCIL_SVG = '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>';

// 空间目录复制按钮图标
var COPY_SVG = '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';
// 项目状态字典
var PROJECT_STATUS = {
    planning:    { label: '规划中', color: '#6b7280' },
    in_progress: { label: '进行中', color: '#10b981' },
    paused:      { label: '已暂停', color: '#f59e0b' },
    completed:   { label: '已完成', color: '#3b82f6' },
    archived:    { label: '已归档', color: '#9ca3af' }
};
// 状态流转规则：当前状态 → 可流转到的目标状态列表
var STATUS_TRANSITIONS = {
    planning:    ['in_progress', 'archived'],
    in_progress: ['paused', 'completed', 'archived'],
    paused:      ['in_progress', 'archived'],
    completed:   ['in_progress', 'archived'],
    archived:    ['planning']
};

document.addEventListener('DOMContentLoaded', function () {
    loadProjects(1);
    // 订阅全局通知：项目/任务状态变更时刷新
    connectNoticeWebSocket(handleProjectNotice);
    // 订阅会话消息：本项目会话产生 token 消耗时刷新用量统计
    connectProjectWebSocket();
    // 预加载任务弹框所需下拉数据（智能体/项目/前置任务，公共 task-modal.js）
    loadAgents();
    loadProjectsForTaskModal();
    loadTasksForPreconditions();
});

/* 任务弹框保存成功后的刷新回调（供公共 task-modal.js 调用） */
window.onTaskModalSaved = function () {
    if (currentProjectId != null) {
        loadProjectDetail(currentProjectId);
    }
};

/** 加载项目下拉数据（复用公共弹框缓存，避免与本页 loadProjects 分页函数重名冲突） */
function loadProjectsForTaskModal() {
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

/** 全局通知处理：项目状态变更刷新详情；任务状态变更仅在影响当前项目时刷新 */
function handleProjectNotice(data) {
    if (!data || data.subtype !== 'status_change') return;
    var c = data.content || {};
    if (data.type === 'project') {
        // 项目列表始终刷新；详情仅刷新当前打开的项目
        loadProjects(currentPage);
        if (currentProjectId != null && Number(c.projectId) === Number(currentProjectId)) {
            loadProjectDetail(currentProjectId);
        }
    } else if (data.type === 'task') {
        // 任务状态变化影响所属项目的详情页（任务列表/统计）
        if (currentProjectId != null && c.projectId != null && Number(c.projectId) === Number(currentProjectId)) {
            loadProjectDetail(currentProjectId);
        }
    }
}

/* ========== 搜索 ========== */
function handleSearchKeyup(event) {
    if (event.key === 'Enter') {
        clearTimeout(searchTimer);
        doSearch();
        return;
    }
    clearTimeout(searchTimer);
    var keyword = event.target.value.trim();
    searchTimer = setTimeout(function () {
        if (keyword !== currentKeyword) {
            doSearch();
        }
    }, 400);
}

function doSearch() {
    var input = document.getElementById('projectSearchInput');
    currentKeyword = input ? input.value.trim() : '';
    currentPage = 1;
    loadProjects(1);
}

/* ========== 左侧项目列表 ========== */
function loadProjects(page) {
    currentPage = page || 1;
    var url = '/api/projects/page?page=' + currentPage + '&size=' + pageSize;
    if (currentKeyword) {
        url += '&keyword=' + encodeURIComponent(currentKeyword);
    }

    fetch(url)
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200) {
                showToast(res.msg || '加载失败', 'error');
                renderProjectsList({ list: [], total: 0 });
                return;
            }
            renderProjectsList(res.data);
        })
        .catch(function (err) {
            console.error('加载项目列表失败:', err);
            showToast('加载失败', 'error');
            renderProjectsList({ list: [], total: 0 });
        });
}

function renderProjectsList(data) {
    data = data || {};
    var list = data.list || [];
    var total = data.total || 0;
    var listEl = document.getElementById('projectsList');
    var emptyEl = document.getElementById('projectsEmpty');

    totalPages = Math.ceil(total / pageSize) || 1;

    if (!list.length) {
        listEl.innerHTML = '';
        emptyEl.style.display = 'block';
        renderPagination(total, currentPage, pageSize);
        return;
    }

    emptyEl.style.display = 'none';
    listEl.innerHTML = list.map(function (project) {
        return buildProjectListItem(project);
    }).join('');

    renderPagination(total, currentPage, pageSize);

    // 列表刷新后，如果之前有选中项目且仍在列表中，保持选中态
    if (currentProjectId) {
        var stillExists = list.some(function (p) { return p.id === currentProjectId; });
        if (stillExists) {
            selectProjectItem(currentProjectId);
        } else {
            // 之前选中的项目已不在当前列表，清空详情
            clearDetail();
        }
    } else if (list.length) {
        // 没有选中项目时，默认选中并展示第一个
        selectProject(list[0].id);
    }
}

function clearDetail() {
    currentProject = null;
    document.getElementById('detailContent').style.display = 'none';
    document.getElementById('detailEmpty').style.display = 'flex';
}

function buildProjectListItem(project) {
    var st = PROJECT_STATUS[project.status] || { label: project.status || '未知', color: '#999' };
    var activeCls = (project.id === currentProjectId) ? ' active' : '';
    // 开启自动迭代：状态前加闪烁机器人图标
    var robotIcon = project.autoIterate
        ? '<span class="list-item-robot" title="自动迭代运行中">&#129302;</span>'
        : '';
    return '<div class="project-list-item' + activeCls + '" onclick="selectProject(' + project.id + ')" data-id="' + project.id + '">' +
        '<div class="list-item-name">' + escapeHtml(project.name || '') + '</div>' +
        robotIcon +
        '<span class="list-item-status" style="color:' + st.color + '">' + st.label + '</span>' +
    '</div>';
}

function selectProject(id) {
    currentProjectId = id;
    selectProjectItem(id);
    loadProjectDetail(id);
}

function selectProjectItem(id) {
    var items = document.querySelectorAll('.project-list-item');
    items.forEach(function (el) {
        if (parseInt(el.getAttribute('data-id'), 10) === id) {
            el.classList.add('active');
        } else {
            el.classList.remove('active');
        }
    });
}

function renderPagination(total, page, size) {
    var paginationEl = document.getElementById('projectsPagination');
    if (total <= size) {
        paginationEl.style.display = 'none';
        return;
    }
    paginationEl.style.display = 'flex';
    document.getElementById('paginationCurrent').textContent = page + ' / ' + totalPages;
    document.getElementById('btnPrevPage').disabled = page <= 1;
    document.getElementById('btnNextPage').disabled = page >= totalPages;
}

function goToPage(page) {
    if (page < 1 || page > totalPages) return;
    loadProjects(page);
}

/* ========== 右侧项目详情 ========== */
function loadProjectDetail(id) {
    // 切换项目时重置日志过滤，并默认翻到最后一页（渲染时钳制到实际总页数，即最新日志）
    logsPage = Number.MAX_SAFE_INTEGER;
    if (logFilter !== 'all') {
        logFilter = 'all';
        var filterBtns = document.querySelectorAll('#logFilterToggle .log-filter-btn');
        filterBtns.forEach(function (btn) {
            btn.classList.toggle('active', btn.getAttribute('data-filter') === 'all');
        });
    }
    Promise.all([
        fetch('/api/projects/' + id).then(function (r) { return r.json(); }),
        fetch('/api/projects/' + id + '/tasks').then(function (r) { return r.json(); }),
        fetch('/api/projects/' + id + '/logs').then(function (r) { return r.json(); })
    ]).then(function (results) {
        var projRes = results[0], taskRes = results[1], logRes = results[2];
        if (projRes.code !== 200 || !projRes.data) {
            showToast(projRes.msg || '加载项目详情失败', 'error');
            return;
        }
        currentProject = projRes.data;
        currentProjectId = currentProject.id;
        renderDetail(currentProject, (taskRes.data || []), (logRes.data || []));
        // 加载项目空间文件树
        loadProjectFiles(currentProjectId);
        // 加载项目 Token 用量统计
        loadProjectTokenUsage(currentProjectId);
        // 加载项目关联会话ID集合（WebSocket 消息匹配用）
        loadProjectSessionIds(currentProjectId);
        // 加载项目管理智能体会话（标题等展示）
        loadProjectSession(currentProjectId);
    }).catch(function (err) {
        console.error('加载项目详情失败:', err);
        showToast('加载项目详情失败', 'error');
    });
}

function renderDetail(project, tasks, logs) {
    document.getElementById('detailEmpty').style.display = 'none';
    document.getElementById('detailContent').style.display = 'block';

    // 名称
    document.getElementById('detailName').textContent = project.name || '';

    // 状态徽标（突出显示：状态色底 + 同色发光环）
    var st = PROJECT_STATUS[project.status] || { label: project.status || '未知', color: '#999' };
    var badge = document.getElementById('detailStatusBadge');
    badge.textContent = st.label;
    badge.style.background = st.color;
    badge.style.boxShadow = '0 0 0 3px ' + st.color + '33, 0 2px 10px ' + st.color + '55';

    // 创建人 / 创建时间
    var metaEl = document.getElementById('detailMeta');
    metaEl.innerHTML = '<span class="meta-item">创建人：<em>' + escapeHtml(project.creatorName || '未知') + '</em></span>' +
        '<span class="meta-item">创建时间：<em>' + formatTime(project.createTime) + '</em></span>';

    // 状态流转按钮
    renderStatusActions(project.status);

    // 描述（Markdown 渲染，容器带 md-content 样式类）
    document.getElementById('detailDesc').innerHTML = project.description
        ? renderMarkdownText(project.description)
        : '暂无描述';

    // 智能体迭代区块（配置了项目管理智能体时展示）
    renderProjectAgentSection(project);

    // 项目空间目录路径（后跟🖊图标，点击可修改目录地址）
    renderSpaceDirRow(project);

    // 任务列表
    renderDetailTasks(tasks);

    // 操作日志
    renderDetailLogs(logs);
}

function renderStatusActions(currentStatus) {
    var container = document.getElementById('statusActions');
    var targets = STATUS_TRANSITIONS[currentStatus] || [];
    if (!targets.length) {
        container.innerHTML = '<span class="status-tip">当前状态无可用流转</span>';
        return;
    }
    container.innerHTML = targets.map(function (target) {
        var t = PROJECT_STATUS[target] || { label: target, color: '#999' };
        return '<button class="btn-status" style="border-color:' + t.color + ';color:' + t.color + '" ' +
            'onclick="changeProjectStatus(\'' + target + '\')">' +
            '流转至「' + t.label + '」</button>';
    }).join('');
}

function changeProjectStatus(target) {
    if (!currentProjectId) return;
    showConfirm('确定将项目状态变更为「' + (PROJECT_STATUS[target] ? PROJECT_STATUS[target].label : target) + '」吗？').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/projects/' + currentProjectId + '/status', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: target })
        })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('状态变更成功', 'success');
                    loadProjectDetail(currentProjectId);
                    loadProjects(currentPage);
                } else {
                    showToast(res.msg || '状态变更失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('状态变更失败:', err);
                showToast('状态变更失败', 'error');
            });
    });
}

// 任务状态字典（与任务看板保持一致）
var TASK_STATUS = {
    pending:             { label: '待启动',   color: '#6b7280' },
    pending_execution:   { label: '待执行',   color: '#f59e0b' },
    processing:          { label: '处理中',   color: '#3b82f6' },
    pending_acceptance:  { label: '待验收',   color: '#8b5cf6' },
    completed:           { label: '已完成',   color: '#10b981' },
    rejected:            { label: '已驳回',   color: '#ef4444' },
    failed:              { label: '处理失败', color: '#dc2626' },
    closed:              { label: '已关闭',   color: '#9ca3af' }
};

/** 折叠/展开项目任务区（默认展开） */
function toggleProjectTasks() {
    tasksExpanded = !tasksExpanded;
    var container = document.getElementById('detailTasks');
    var iconEl = document.getElementById('tasksToggleIcon');
    container.style.display = tasksExpanded ? 'grid' : 'none';
    iconEl.textContent = tasksExpanded ? '▾' : '▸';
}

/** 折叠/展开 Token 用量区（默认展开） */
function toggleProjectToken() {
    tokenExpanded = !tokenExpanded;
    var chartEl = document.getElementById('projectTokenChart');
    var iconEl = document.getElementById('tokenToggleIcon');
    chartEl.style.display = tokenExpanded ? '' : 'none';
    iconEl.textContent = tokenExpanded ? '▾' : '▸';
}

function renderDetailTasks(tasks) {
    var container = document.getElementById('detailTasks');
    var countEl = document.getElementById('taskCount');
    // 渲染时同步折叠状态（切换项目后保持当前展开/折叠状态）
    container.style.display = tasksExpanded ? 'grid' : 'none';
    document.getElementById('tasksToggleIcon').textContent = tasksExpanded ? '▾' : '▸';
    if (!tasks || !tasks.length) {
        countEl.textContent = '(0)';
        container.innerHTML = '<p class="detail-empty-text">暂无任务</p>';
        return;
    }
    countEl.textContent = '(' + tasks.length + ')';
    container.innerHTML = tasks.map(function (task) {
        var st = TASK_STATUS[task.status] || { label: task.status || '未知', color: '#999' };
        return '<a class="detail-task-card" href="/tasks-board/' + task.id + '" target="_blank" ' +
            'onclick="window.open(\'/tasks-board/' + task.id + '\', \'_blank\', \'width=900,height=700\'); return false;" ' +
            'style="border-left-color:' + st.color + '">' +
            '<div class="task-card-head">' +
                '<span class="task-name" title="' + escapeHtml(task.title || '') + '">' + escapeHtml(task.title || '') + '</span>' +
                '<span class="task-status-badge" style="background:' + st.color + '">' + st.label + '</span>' +
            '</div>' +
            '<div class="task-sub">' +
                '<span class="task-creator">' + escapeHtml(task.creatorName || '未知') + '</span>' +
                '<span class="task-time">' + formatTime(task.createTime) + '</span>' +
            '</div>' +
        '</a>';
    }).join('');
}

/* ========== 项目空间文件树 ========== */
// 缓存最新文件树，供移动模态框列举目录使用
var currentFileTree = [];

function loadProjectFiles(id) {
    if (!id) return;
    var container = document.getElementById('detailFiles');
    container.innerHTML = '<p class="detail-empty-text">加载中...</p>';
    fetch('/api/projects/' + id + '/files')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                currentFileTree = res.data || [];
                renderFileTree(currentFileTree, container);
            } else {
                currentFileTree = [];
                container.innerHTML = '<p class="detail-empty-text">' + escapeHtml(res.msg || '加载失败') + '</p>';
            }
        })
        .catch(function () {
            currentFileTree = [];
            container.innerHTML = '<p class="detail-empty-text">加载失败</p>';
        });
}

function renderFileTree(nodes, container) {
    if (!nodes || !nodes.length) {
        container.innerHTML = '<p class="detail-empty-text">项目空间为空，点击上方“上传文件”或“新建”开始</p>';
        return;
    }
    var html = '<div class="file-tree-root-dropzone" data-target="">' +
        '<span class="file-tree-root-label">/（根目录）</span>' +
        '</div>' +
        '<ul class="file-tree">' + nodes.map(renderFileTreeNode).join('') + '</ul>';
    container.innerHTML = html;
}

function renderFileTreeNode(node) {
    var icon = node.type === 'directory' ? '📁' : getFileIcon(node.name);
    var pathAttr = escapeHtml(node.path);
    // 节点操作按钮（悬停显示）
    var actions;
    if (node.type === 'directory') {
        actions = '<span class="file-tree-actions">' +
            '<button class="ft-btn" title="下载目录" data-action="download" data-path="' + pathAttr + '">⬇</button>' +
            '<button class="ft-btn" title="在此上传" data-action="upload" data-path="' + pathAttr + '">⬆</button>' +
            '<button class="ft-btn" title="在此新建" data-action="create" data-path="' + pathAttr + '">＋</button>' +
            '<button class="ft-btn" title="移动/重命名" data-action="move" data-path="' + pathAttr + '">✎</button>' +
            '<button class="ft-btn ft-btn-danger" title="删除" data-action="delete" data-path="' + pathAttr + '">🗑</button>' +
            '</span>';
    } else {
        actions = '<span class="file-tree-actions">' +
            '<button class="ft-btn" title="预览" data-action="preview" data-path="' + pathAttr + '">👁</button>' +
            '<button class="ft-btn" title="下载文件" data-action="download" data-path="' + pathAttr + '">⬇</button>' +
            '<button class="ft-btn" title="移动/重命名" data-action="move" data-path="' + pathAttr + '">✎</button>' +
            '<button class="ft-btn ft-btn-danger" title="删除" data-action="delete" data-path="' + pathAttr + '">🗑</button>' +
            '</span>';
    }

    if (node.type === 'directory') {
        var hasChildren = node.children && node.children.length;
        var childHtml = '';
        if (hasChildren) {
            childHtml = '<ul class="file-tree">' + node.children.map(renderFileTreeNode).join('') + '</ul>';
        }
        // 默认折叠；有子节点时显示 +/- 切换符
        var toggleSign = hasChildren
            ? '<span class="file-tree-toggle" onclick="toggleFileTreeNode(this)">＋</span>'
            : '<span class="file-tree-toggle file-tree-toggle-empty"></span>';
        return '<li class="file-tree-item file-tree-dir collapsed">' +
            '<div class="file-tree-row file-tree-dropzone" draggable="true" data-path="' + pathAttr + '" data-target="' + pathAttr + '" data-type="directory">' +
            toggleSign +
            '<span class="file-tree-name" onclick="toggleFileTreeNode(this)"><span class="file-tree-icon">' + icon + '</span>' + escapeHtml(node.name) + '</span>' +
            actions +
            '</div>' +
            childHtml +
            '</li>';
    }
    var sizeText = node.size != null ? formatFileSize(node.size) : '';
    return '<li class="file-tree-item file-tree-file">' +
        '<div class="file-tree-row" draggable="true" data-path="' + pathAttr + '" data-type="file">' +
        '<span class="file-tree-name"><span class="file-tree-icon">' + icon + '</span>' + escapeHtml(node.name) + '</span>' +
        actions +
        '<span class="file-tree-size">' + sizeText + '</span>' +
        '</div>' +
        '</li>';
}

// 文件树节点操作事件委托
document.addEventListener('DOMContentLoaded', function () {
    var container = document.getElementById('detailFiles');
    if (container) {
        container.addEventListener('click', function (e) {
            var btn = e.target.closest('.ft-btn');
            if (!btn) return;
            e.stopPropagation();
            var action = btn.getAttribute('data-action');
            var path = btn.getAttribute('data-path');
            if (action === 'upload') {
                triggerSpaceUpload(path);
            } else if (action === 'create') {
                showCreateFileModal(path);
            } else if (action === 'move') {
                showMoveFileModal(path);
            } else if (action === 'delete') {
                deleteProjectFile(path);
            } else if (action === 'download') {
                downloadProjectFile(path);
            } else if (action === 'preview') {
                previewProjectFile(path);
            }
        });

        // ===== 拖拽移动：拖拽文件/目录到其他目录或根目录 =====
        var dragPath = null; // 当前拖拽的源路径

        container.addEventListener('dragstart', function (e) {
            var row = e.target.closest('.file-tree-row');
            if (!row) return;
            dragPath = row.getAttribute('data-path');
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', dragPath);
            row.classList.add('dragging');
        });

        container.addEventListener('dragend', function (e) {
            var row = e.target.closest('.file-tree-row');
            if (row) row.classList.remove('dragging');
            container.querySelectorAll('.drag-over').forEach(function (el) {
                el.classList.remove('drag-over');
            });
            dragPath = null;
        });

        // dragover：总是 preventDefault 允许 drop，但只对有效目标高亮
        container.addEventListener('dragover', function (e) {
            var dropzone = e.target.closest('.file-tree-dropzone, .file-tree-root-dropzone');
            if (!dropzone) return;
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            var targetPath = dropzone.getAttribute('data-target') || '';
            container.querySelectorAll('.drag-over').forEach(function (el) {
                el.classList.remove('drag-over');
            });
            if (isValidDropTarget(dragPath, targetPath)) {
                dropzone.classList.add('drag-over');
            }
        });

        container.addEventListener('dragleave', function (e) {
            var dropzone = e.target.closest('.file-tree-dropzone, .file-tree-root-dropzone');
            if (dropzone) dropzone.classList.remove('drag-over');
        });

        container.addEventListener('drop', function (e) {
            var dropzone = e.target.closest('.file-tree-dropzone, .file-tree-root-dropzone');
            if (!dropzone) return;
            e.preventDefault();
            dropzone.classList.remove('drag-over');
            var targetPath = dropzone.getAttribute('data-target') || '';
            if (!dragPath) return;
            if (!isValidDropTarget(dragPath, targetPath)) {
                // 给出无效原因提示
                var srcParent = getParentDir(dragPath);
                if (dragPath === targetPath) {
                    showToast('不能移动到自身', 'info');
                } else if (srcParent === targetPath) {
                    showToast('文件已在此目录中', 'info');
                } else if (targetPath.indexOf(dragPath + '/') === 0) {
                    showToast('不能移动到子目录', 'info');
                } else {
                    showToast('无法移动到此目录', 'info');
                }
                return;
            }
            moveFileToDir(dragPath, targetPath);
        });
    }
});

// 校验拖拽目标合法性：不能拖到自身；若是目录，不能拖到自身子目录
function isValidDropTarget(srcPath, targetDir) {
    if (!srcPath) return false;
    // 同一目录（目标父目录等于源父目录）也不算移动，但允许（用于无操作），此处返回 false 避免无效请求
    var srcParent = getParentDir(srcPath);
    if (srcParent === targetDir) return false;
    // 不能拖到自身
    if (srcPath === targetDir) return false;
    // 目标是源的子目录：禁止
    if (targetDir.indexOf(srcPath + '/') === 0) return false;
    return true;
}

// 执行移动：源路径 → 目标目录 + 原名称
function moveFileToDir(srcPath, targetDir) {
    if (!currentProjectId) return;
    var name = srcPath.indexOf('/') >= 0 ? srcPath.substring(srcPath.lastIndexOf('/') + 1) : srcPath;
    var to = targetDir ? targetDir + '/' + name : name;
    if (to === srcPath) return;
    fetch('/api/projects/' + currentProjectId + '/files/move', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ from: srcPath, to: to })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast('已移动到「' + (targetDir ? targetDir : '根目录') + '」', 'success');
                loadProjectFiles(currentProjectId);
                loadProjectDetail(currentProjectId);
            } else {
                showToast(res.msg || '移动失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('移动失败:', err);
            showToast('移动失败', 'error');
        });
}

function toggleFileTreeNode(el) {
    // el 可能是 +/- 符号 span 或 file-tree-name span，它们的 parent 都是 row div
    var row = el.parentElement;
    var li = row.parentElement;
    li.classList.toggle('collapsed');
    // 同步更新 +/- 符号
    var toggle = row.querySelector('.file-tree-toggle');
    if (toggle && !toggle.classList.contains('file-tree-toggle-empty')) {
        toggle.textContent = li.classList.contains('collapsed') ? '＋' : '－';
    }
}

/* ========== 项目空间：下载 ========== */
function downloadProjectFile(path) {
    if (!currentProjectId) return;
    // 直接打开下载链接：浏览器会触发文件下载，不影响当前页面
    var url = '/api/projects/' + currentProjectId + '/files/download';
    if (path) {
        url += '?path=' + encodeURIComponent(path);
    }
    window.location.href = url;
}

function downloadAllProjectFiles() {
    if (!currentProjectId) {
        showToast('请先选择项目', 'error');
        return;
    }
    // path 为空 → 后端打包整个项目空间为 zip
    var url = '/api/projects/' + currentProjectId + '/files/download';
    showToast('正在打包下载，请稍候...', 'info');
    window.location.href = url;
}

/* ========== 项目空间：预览 ========== */
var currentPreviewSrc = '';  // 当前预览页地址（嵌套的公共预览组件页，供新窗口打开）

function previewProjectFile(path) {
    if (!currentProjectId || !path) return;
    var name = path.indexOf('/') >= 0 ? path.substring(path.lastIndexOf('/') + 1) : path;
    // 构造内联预览 URL（供 iframe 内的公共预览组件加载文件内容）
    var fileUrl = '/api/projects/' + currentProjectId + '/files/preview?path=' + encodeURIComponent(path);
    var previewSrc = '/file-preview?url=' + encodeURIComponent(fileUrl) +
        '&name=' + encodeURIComponent(name);
    currentPreviewSrc = previewSrc;
    var iframe = document.getElementById('projectFilePreviewFrame');
    if (iframe) {
        iframe.src = previewSrc;
    }
    document.getElementById('previewModalTitle').textContent = name;
    Modal.open('projectFilePreviewModal');
}

/* 新页面打开当前预览内容（弹框内嵌套的预览组件页） */
function openPreviewInNewTab() {
    if (!currentPreviewSrc) return;
    window.open(currentPreviewSrc, '_blank');
}

/* ========== 项目空间：上传 ========== */
function triggerSpaceUpload(targetDir) {
    spaceUploadTargetDir = targetDir || '';
    var input = document.getElementById('projectSpaceFileInput');
    if (input) {
        input.value = '';
        input.click();
    }
}

var spaceUploadTargetDir = '';

function onProjectSpaceFileSelected(input) {
    if (!input.files || !input.files.length) return;
    if (!currentProjectId) {
        showToast('请先选择项目', 'error');
        input.value = '';
        return;
    }
    var formData = new FormData();
    for (var i = 0; i < input.files.length; i++) {
        formData.append('files', input.files[i]);
    }
    formData.append('targetDir', spaceUploadTargetDir);

    showToast('上传中...', 'info');
    fetch('/api/projects/' + currentProjectId + '/files/upload', {
        method: 'POST',
        body: formData
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast('上传成功', 'success');
                currentFileTree = res.data || [];
                renderFileTree(currentFileTree, document.getElementById('detailFiles'));
                loadProjectDetail(currentProjectId); // 刷新操作日志
            } else {
                showToast(res.msg || '上传失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('上传失败:', err);
            showToast('上传失败', 'error');
        })
        .finally(function () {
            input.value = '';
        });
}

/* ========== 项目空间：新建文件/目录 ========== */
var spaceCreateTargetDir = '';

function showCreateFileModal(targetDir) {
    if (!currentProjectId) {
        showToast('请先选择项目', 'error');
        return;
    }
    spaceCreateTargetDir = targetDir || '';
    document.getElementById('spaceCreateName').value = '';
    document.getElementById('spaceCreatePath').textContent = spaceCreateTargetDir ? '/' + spaceCreateTargetDir : '/';
    Modal.open('spaceCreateModal');
    setTimeout(function () { document.getElementById('spaceCreateName').focus(); }, 100);
}

function submitSpaceCreate() {
    var name = document.getElementById('spaceCreateName').value.trim();
    if (!name) {
        showToast('请输入名称', 'error');
        return;
    }
    var typeEl = document.querySelector('input[name="spaceCreateType"]:checked');
    var isDir = typeEl ? typeEl.value === 'directory' : true;
    var fullPath = spaceCreateTargetDir ? spaceCreateTargetDir + '/' + name : name;

    fetch('/api/projects/' + currentProjectId + '/files', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path: fullPath, isDirectory: isDir })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast('创建成功', 'success');
                Modal.close('spaceCreateModal');
                currentFileTree = res.data || [];
                renderFileTree(currentFileTree, document.getElementById('detailFiles'));
                loadProjectDetail(currentProjectId);
            } else {
                showToast(res.msg || '创建失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('创建失败:', err);
            showToast('创建失败', 'error');
        });
}

/* ========== 项目空间：删除 ========== */
function deleteProjectFile(path) {
    if (!currentProjectId || !path) return;
    var name = path.indexOf('/') >= 0 ? path.substring(path.lastIndexOf('/') + 1) : path;
    showConfirm('确定要删除「' + name + '」吗？如果是目录将递归删除，且无法恢复。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/projects/' + currentProjectId + '/files?path=' + encodeURIComponent(path), { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('删除成功', 'success');
                    loadProjectFiles(currentProjectId);
                    loadProjectDetail(currentProjectId);
                } else {
                    showToast(res.msg || '删除失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('删除失败:', err);
                showToast('删除失败', 'error');
            });
    });
}

/* ========== 项目空间：重命名（仅改名称，不改路径） ========== */
function getParentDir(path) {
    if (!path || path.indexOf('/') < 0) return '';
    return path.substring(0, path.lastIndexOf('/'));
}

function showMoveFileModal(path) {
    if (!currentProjectId || !path) return;
    document.getElementById('spaceMoveFrom').value = path;
    document.getElementById('spaceMoveCurrentPath').value = '/' + path;
    var name = path.indexOf('/') >= 0 ? path.substring(path.lastIndexOf('/') + 1) : path;
    document.getElementById('spaceMoveName').value = name;
    Modal.open('spaceMoveModal');
    setTimeout(function () { document.getElementById('spaceMoveName').focus(); }, 100);
}

function submitSpaceMove() {
    var from = document.getElementById('spaceMoveFrom').value;
    var name = document.getElementById('spaceMoveName').value.trim();
    if (!from || !name) {
        showToast('请填写新名称', 'error');
        return;
    }
    // 仅改名称：目标 = 原父目录 + 新名称
    var parentDir = getParentDir(from);
    var to = parentDir ? parentDir + '/' + name : name;

    fetch('/api/projects/' + currentProjectId + '/files/move', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ from: from, to: to })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast('操作成功', 'success');
                Modal.close('spaceMoveModal');
                loadProjectFiles(currentProjectId);
                loadProjectDetail(currentProjectId);
            } else {
                showToast(res.msg || '操作失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('重命名失败:', err);
            showToast('操作失败', 'error');
        });
}

function getFileIcon(name) {
    var ext = (name.split('.').pop() || '').toLowerCase();
    var map = {
        txt: '📄', md: '📝', log: '📃',
        jpg: '🖼️', jpeg: '🖼️', png: '🖼️', gif: '🖼️', bmp: '🖼️', svg: '🖼️',
        mp4: '🎬', avi: '🎬', mov: '🎬', mkv: '🎬',
        mp3: '🎵', wav: '🎵', flac: '🎵',
        pdf: '📄', doc: '📄', docx: '📄', xls: '📊', xlsx: '📊',
        zip: '📦', rar: '📦', '7z': '📦', gz: '📦',
        java: '☕', js: '📜', ts: '📜', py: '🐍', html: '🌐', css: '🎨', json: '📋', xml: '📋'
    };
    return map[ext] || '📄';
}

function formatFileSize(bytes) {
    if (!bytes || bytes <= 0) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
}

/* ========== 操作日志（可折叠） ========== */
// 动作类型中文标签
var LOG_ACTION_LABELS = {
    create: '创建',
    update: '更新',
    status_change: '状态变更',
    delete: '删除',
    task_bind: '关联任务',
    task_unbind: '取消关联任务',
    task_status: '任务状态变更',
    task_comment: '任务评论',
    file_create: '新建文件',
    file_delete: '删除文件',
    file_move: '移动/重命名',
    file_upload: '上传文件'
};

function renderDetailLogs(logs) {
    logsCache = logs || [];
    renderLogsByFilter();
}

/* 按当前过滤类型渲染日志：all=全部 / important=仅重点类型 */
function renderLogsByFilter() {
    var container = document.getElementById('detailLogs');
    var countEl = document.getElementById('logsCount');
    var iconEl = document.getElementById('logsToggleIcon');

    // 应用折叠状态
    container.style.display = logsExpanded ? 'block' : 'none';
    iconEl.textContent = logsExpanded ? '▾' : '▸';

    var logs = logsCache;
    if (logFilter === 'important') {
        logs = logs.filter(function (log) { return log.logType === 'important'; });
    }

    countEl.textContent = '(' + logs.length + ')';

    if (!logs.length) {
        logsTotalPages = 1;
        logsPage = 1;
        container.innerHTML = '<p class="detail-empty-text">' + (logFilter === 'important' ? '暂无重点日志' : '暂无操作日志') + '</p>';
        updateLogsPagination();
        return;
    }

    // 计算总页数并截取当前页数据（每页 LOGS_PAGE_SIZE 条）
    logsTotalPages = Math.ceil(logs.length / LOGS_PAGE_SIZE) || 1;
    if (logsPage > logsTotalPages) logsPage = logsTotalPages;
    var pageLogs = logs.slice((logsPage - 1) * LOGS_PAGE_SIZE, logsPage * LOGS_PAGE_SIZE);

    // 后端按时间正序返回（最早在前），渲染当前页
    container.innerHTML = pageLogs.map(function (log) {
        var actionLabel = LOG_ACTION_LABELS[log.action] || log.action || '操作';
        var isImportant = log.logType === 'important';
        return '<div class="log-item' + (isImportant ? ' log-item-important' : '') + '">' +
            '<div class="log-dot' + (isImportant ? ' log-dot-important' : '') + '"></div>' +
            '<div class="log-content">' +
                '<div class="log-line">' +
                    '<span class="log-action">' + escapeHtml(actionLabel) + '</span>' +
                    (isImportant ? '<span class="log-important-badge">重点</span>' : '') +
                    '<span class="log-actions">' +
                        '<button type="button" class="log-action-btn log-btn-type" title="' + (isImportant ? '转为默认日志' : '转为重点日志') + '" onclick="toggleLogType(' + log.id + ', \'' + (isImportant ? 'default' : 'important') + '\')">' + (isImportant ? '☆' : '★') + '</button>' +
                        '<button type="button" class="log-action-btn log-btn-delete" title="删除日志" onclick="deleteProjectLog(' + log.id + ')">✕</button>' +
                    '</span>' +
                '</div>' +
                '<div class="log-detail">' + renderLogDetail(log.detail) + '</div>' +
                '<div class="log-meta">' +
                    '<span class="log-operator">' + escapeHtml(log.operatorName || '未知') + '</span>' +
                    '<span class="log-time">' + formatTime(log.createTime) + '</span>' +
                '</div>' +
            '</div>' +
        '</div>';
    }).join('');
    updateLogsPagination();
}

/* 删除项目操作日志 */
function deleteProjectLog(logId) {
    if (!currentProjectId || !logId) return;
    showConfirm('确定删除这条操作日志吗？删除后不可恢复。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/projects/' + currentProjectId + '/logs/' + logId, { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code !== 200) {
                    showToast(res.msg || '删除失败', 'error');
                    return;
                }
                showToast('删除成功', 'success');
                // 从缓存中移除后重渲染（保持当前过滤/分页状态）
                logsCache = logsCache.filter(function (l) { return l.id !== logId; });
                renderLogsByFilter();
            })
            .catch(function (err) {
                console.error('删除日志失败:', err);
                showToast('删除失败', 'error');
            });
    });
}

/* 更新日志类型：targetType 为 default / important */
function toggleLogType(logId, targetType) {
    if (!currentProjectId || !logId || !targetType) return;
    fetch('/api/projects/' + currentProjectId + '/logs/' + logId + '/type', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ logType: targetType })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200) {
                showToast(res.msg || '更新失败', 'error');
                return;
            }
            showToast(targetType === 'important' ? '已标记为重点日志' : '已转为默认日志', 'success');
            // 更新缓存后重渲染
            logsCache.forEach(function (l) {
                if (l.id === logId) l.logType = targetType;
            });
            renderLogsByFilter();
        })
        .catch(function (err) {
            console.error('更新日志类型失败:', err);
            showToast('更新失败', 'error');
        });
}

/* 通用 Markdown 渲染：无内容时返回空串，marked 未加载时降级为转义纯文本 */
function renderMarkdownText(text) {
    if (!text) return '';
    if (typeof marked !== 'undefined' && marked.parse) {
        // breaks:true 保留单换行（与聊天页/日志渲染行为保持一致）
        return marked.parse(text, { breaks: true, gfm: true });
    }
    return escapeHtml(text);
}

/* 日志内容 Markdown 渲染 */
function renderLogDetail(detail) {
    return renderMarkdownText(detail);
}

/* 更新日志分页控件显示状态：折叠或不足一页时隐藏 */
function updateLogsPagination() {
    var paginationEl = document.getElementById('logsPagination');
    if (!paginationEl) return;
    if (!logsExpanded || logsTotalPages <= 1) {
        paginationEl.style.display = 'none';
        return;
    }
    paginationEl.style.display = 'flex';
    document.getElementById('logsPaginationCurrent').textContent = logsPage + ' / ' + logsTotalPages;
    document.getElementById('btnLogPrevPage').disabled = logsPage <= 1;
    document.getElementById('btnLogNextPage').disabled = logsPage >= logsTotalPages;
}

/* 日志翻页 */
function goToLogPage(page) {
    if (page < 1 || page > logsTotalPages || page === logsPage) return;
    logsPage = page;
    renderLogsByFilter();
}

/* 切换日志过滤类型（全部/总结），并同步连体按钮选中态 */
function switchLogFilter(filter, event) {
    if (event) event.stopPropagation();
    logFilter = filter;
    logsPage = Number.MAX_SAFE_INTEGER;  // 切换过滤类型时默认翻到最后一页（最新日志）
    var btns = document.querySelectorAll('#logFilterToggle .log-filter-btn');
    btns.forEach(function (btn) {
        btn.classList.toggle('active', btn.getAttribute('data-filter') === filter);
    });
    renderLogsByFilter();
}

function toggleProjectLogs() {
    logsExpanded = !logsExpanded;
    var container = document.getElementById('detailLogs');
    var iconEl = document.getElementById('logsToggleIcon');
    container.style.display = logsExpanded ? 'block' : 'none';
    iconEl.textContent = logsExpanded ? '▾' : '▸';
    updateLogsPagination();
}

/* ========== 新建/编辑项目 ========== */
function showAddModal() {
    document.getElementById('projectModalTitle').textContent = '新建项目';
    document.getElementById('projectId').value = '';
    document.getElementById('projectName').value = '';
    document.getElementById('projectDescription').value = '';
    // 项目管理智能体与自动迭代默认关闭
    populateProjectAgentSelect('');
    document.getElementById('projectAutoIterate').checked = false;
    document.getElementById('projectIteratePrompt').value = '';
    onProjectAgentChange();
    // 新建时显示项目空间选项，默认自动创建
    document.getElementById('spaceModeGroup').style.display = '';
    var autoRadio = document.querySelector('input[name="spaceMode"][value="auto"]');
    if (autoRadio) autoRadio.checked = true;
    document.getElementById('localSpaceBox').style.display = 'none';
    document.getElementById('projectLocalSpaceDir').value = '';
    Modal.open('projectModal');
}

/** 跳转到任务看板并默认选中当前项目 */
function goToTaskBoard() {
    if (!currentProjectId) return;
    window.location.href = '/tasks-board?projectId=' + encodeURIComponent(currentProjectId);
}

function editCurrentProject() {
    if (!currentProject) return;
    var project = currentProject;
    document.getElementById('projectModalTitle').textContent = '编辑项目';
    document.getElementById('projectId').value = project.id || '';
    document.getElementById('projectName').value = project.name || '';
    document.getElementById('projectDescription').value = project.description || '';
    // 回填项目管理智能体与自动迭代设置
    populateProjectAgentSelect(project.agentId || '');
    document.getElementById('projectAutoIterate').checked = !!project.autoIterate;
    document.getElementById('projectIteratePrompt').value = project.iteratePrompt || '';
    onProjectAgentChange();
    // 编辑时不允许修改项目空间
    document.getElementById('spaceModeGroup').style.display = 'none';
    Modal.open('projectModal');
}

/** 自动迭代勾选切换：控制迭代要求提示词输入框显隐 */
function onProjectAutoIterateChange() {
}

/** 项目管理智能体选择切换：未选择智能体时隐藏自动迭代与迭代要求（自动迭代依赖智能体） */
function onProjectAgentChange() {
    var agentSel = document.getElementById('projectAgentId');
    var hasAgent = !!(agentSel && agentSel.value);
    var iterateGroup = document.getElementById('projectAutoIterateGroup');
    if (iterateGroup) {
        iterateGroup.style.display = hasAgent ? 'block' : 'none';
    }
    var promptGroup = document.getElementById('projectIteratePromptGroup');
    if (promptGroup) {
        promptGroup.style.display = hasAgent ? 'block' : 'none';
    }
    if (!hasAgent) {
        // 未选智能体：强制关闭自动迭代（提示词保留输入内容，仅隐藏）
        var autoCheck = document.getElementById('projectAutoIterate');
        if (autoCheck && autoCheck.checked) {
            autoCheck.checked = false;
        }
    }
}
function onSpaceModeChange() {
    var localRadio = document.querySelector('input[name="spaceMode"][value="local"]');
    var box = document.getElementById('localSpaceBox');
    box.style.display = (localRadio && localRadio.checked) ? 'block' : 'none';
}

function closeProjectModal() {
    Modal.close('projectModal');
}

function submitProject() {
    var id = document.getElementById('projectId').value;
    var name = document.getElementById('projectName').value.trim();
    var description = document.getElementById('projectDescription').value.trim();

    if (!name) {
        showToast('请输入项目名称', 'error');
        return;
    }

    var agentSel = document.getElementById('projectAgentId');
    var payload = {
        name: name,
        description: description,
        agentId: agentSel && agentSel.value ? Number(agentSel.value) : null,
        // 未选智能体时强制关闭自动迭代；迭代要求提示词独立提交（手动下发指令时同样生效，不随勾选清空）
        autoIterate: !!(agentSel && agentSel.value) && document.getElementById('projectAutoIterate').checked,
        iteratePrompt: (document.getElementById('projectIteratePrompt').value || '').trim()
    };
    // 仅新建时提交项目空间设置
    if (!id) {
        var localRadio = document.querySelector('input[name="spaceMode"][value="local"]');
        if (localRadio && localRadio.checked) {
            var localDir = document.getElementById('projectLocalSpaceDir').value.trim();
            if (!localDir) {
                showToast('请输入本地目录路径', 'error');
                return;
            }
            payload.spaceDir = localDir;
        }
    }

    var url = id ? '/api/projects/' + id : '/api/projects';
    var method = id ? 'PUT' : 'POST';

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast(id ? '项目更新成功' : '项目创建成功', 'success');
                closeProjectModal();
                loadProjects(currentPage);
                // 如果是编辑当前选中项目，刷新详情
                if (id && parseInt(id, 10) === currentProjectId) {
                    loadProjectDetail(currentProjectId);
                }
            } else {
                showToast(res.msg || '操作失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('提交失败:', err);
            showToast('操作失败', 'error');
        });
}

/**
 * 修改项目空间目录：弹框输入新地址（支持相对路径与绝对路径），
 * 修改成功后刷新详情与文件树。
 */
function editProjectSpaceDir() {
    if (!currentProjectId || !currentProject) return;
    var placeholder = '相对路径（如 my-space 或 project-spaces/101）或绝对路径（如 D:\\data\\project）';
    showPrompt('修改空间目录（当前: ' + (currentProject.spaceDirAbs || currentProject.spaceDir || '未设置') + '，相对路径以服务运行目录为起点）', placeholder, currentProject.spaceDir || '')
        .then(function (newDir) {
            if (newDir === null) return; // 取消
            if (!newDir) {
                showToast('空间目录不能为空', 'error');
                return;
            }
            fetch('/api/projects/' + currentProjectId + '/space-dir', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ spaceDir: newDir })
            })
                .then(function (r) { return r.json(); })
                .then(function (res) {
                    if (res.code === 200) {
                        showToast('空间目录已修改', 'success');
                        // 更新当前项目对象并重渲染目录行，然后刷新文件树
                        currentProject = res.data || currentProject;
                        renderSpaceDirRow(currentProject);
                        loadProjectFiles(currentProjectId);
                    } else {
                        showToast(res.msg || '修改失败', 'error');
                    }
                })
                .catch(function (err) {
                    console.error('修改空间目录失败:', err);
                    showToast('修改失败', 'error');
                });
        });
}

/** 渲染空间目录行（标题栏内：显示绝对路径 + 复制按钮 + 修改按钮） */
function renderSpaceDirRow(project) {
    var spaceDirEl = document.getElementById('spaceDirInfo');
    if (!spaceDirEl) return;
    // 显示解析后的绝对路径（存储值保持不变），悬停提示存储值与解析路径
    var display = project.spaceDirAbs || project.spaceDir;
    if (display) {
        var tip = (project.spaceDir && project.spaceDir !== display)
            ? '存储: ' + project.spaceDir + '\n路径: ' + display
            : display;
        spaceDirEl.innerHTML = '<code class="space-dir-path" title="' + escapeHtml(tip) + '">' + escapeHtml(display) + '</code>' +
            '<button class="space-dir-copy-btn" title="复制目录地址" onclick="copySpaceDir()">' + COPY_SVG + '</button>' +
            '<button class="space-dir-edit-btn" title="修改空间目录" onclick="editProjectSpaceDir()">' + PENCIL_SVG + '</button>';
    } else {
        spaceDirEl.innerHTML = '<span class="space-dir-label">未设置</span>' +
            '<button class="space-dir-edit-btn" title="设置空间目录" onclick="editProjectSpaceDir()">' + PENCIL_SVG + '</button>';
    }
}

/** 复制空间目录地址到剪贴板（Clipboard API 优先，降级 execCommand） */
function copySpaceDir() {
    if (!currentProject) return;
    var text = currentProject.spaceDirAbs || currentProject.spaceDir || '';
    if (!text) return;
    function done() { showToast('目录地址已复制', 'success'); }
    function fail() { showToast('复制失败', 'error'); }
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done).catch(function () {
            if (fallbackCopyText(text)) { done(); } else { fail(); }
        });
    } else {
        if (fallbackCopyText(text)) { done(); } else { fail(); }
    }
}

/** 兼容旧浏览器的复制降级方案 */
function fallbackCopyText(text) {
    try {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        var ok = document.execCommand('copy');
        document.body.removeChild(ta);
        return ok;
    } catch (e) {
        return false;
    }
}
function deleteCurrentProject() {
    if (!currentProjectId) return;
    showConfirm('确定要删除该项目吗？删除后无法恢复。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/projects/' + currentProjectId, { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('删除成功', 'success');
                    currentProjectId = null;
                    currentProject = null;
                    document.getElementById('detailContent').style.display = 'none';
                    document.getElementById('detailEmpty').style.display = 'flex';
                    loadProjects(currentPage);
                } else {
                    showToast(res.msg || '删除失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('删除项目失败:', err);
                showToast('删除失败', 'error');
            });
    });
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

/** 格式化时间为 YYYY-MM-DD HH:mm */
function formatTime(time) {
    if (!time) return '';
    var t = String(time).replace('T', ' ');
    // 截取到分钟
    return t.substring(0, 16);
}

/* ========== 项目 Token 用量统计 ========== */
var projectTokenChart = null;

/** 加载项目关联的所有会话ID：用于过滤 WebSocket 消息 */
function loadProjectSessionIds(projectId) {
    if (!projectId) return;
    fetch('/api/projects/' + projectId + '/session-ids').then(function (r) { return r.json(); }).then(function (res) {
        if (res.code === 200) {
            projectSessionIds = (res.data || []).filter(Boolean);
        }
    }).catch(function (err) {
        console.error('加载项目会话ID失败:', err);
    });
}

/** 监听会话 WebSocket：本项目会话产生 token 消耗时实时刷新柱状统计图 */
function connectProjectWebSocket() {
    var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    try {
        projectWs = new WebSocket(protocol + '//' + window.location.host + '/ws/chat');
    } catch (e) {
        console.error('创建 WebSocket 失败:', e);
        return;
    }
    projectWs.onmessage = function (event) {
        var data;
        try { data = JSON.parse(event.data); } catch (e) { return; }
        if (!data.sessionId) return;
        var isAgentSession = (data.sessionId === currentProjectAgentSessionId);
        if (data.type === 'session-title') {
            // 项目管理智能体会话生成了标题：刷新会话展示
            if (isAgentSession && currentProjectId != null) {
                loadProjectSession(currentProjectId);
            }
            return;
        }
        if (data.type === 'token_usage') {
            if (!isAgentSession && projectSessionIds.indexOf(data.sessionId) === -1) return;
            // 本项目会话产生了 token 消耗：刷新柱状统计图
            if (currentProjectId != null) {
                loadProjectTokenUsage(currentProjectId);
            }
            return;
        }
        // 执行内容实时展示（参考任务详情页打字机实现）：仅针对项目管理智能体会话
        if (!isAgentSession) return;
        if (data.type === 'received') {
            // 新一轮执行：重置打字机并显示执行中指示
            resetProjectSessionTicker();
        } else if (data.type === 'chunk') {
            showProjectSessionRunning();
            appendProjectSessionTicker(data.content);
        } else if (data.type === 'tool_call') {
            showProjectSessionRunning();
            handleProjectToolCallTicker(data);
        } else if (data.type === 'thinking') {
            showProjectSessionRunning();
            appendProjectSessionTicker(data.content);
        } else if (data.type === 'task-done' || data.type === 'done' || data.type === 'error') {
            // 项目智能体一轮迭代结束：隐藏执行中指示并刷新项目详情（任务/状态可能已更新）
            if (projectWsRunning) {
                hideProjectSessionRunning();
                showToast('项目智能体执行结束，已刷新项目信息', 'success');
            }
            if (currentProjectId != null) {
                loadProjectDetail(currentProjectId);
            }
        }
    };
    projectWs.onclose = function () {
        // 断线重连（页面可能长时间停留）
        setTimeout(connectProjectWebSocket, 5000);
    };
}

/* ========== 项目智能体会话实时输出（打字机，参考任务详情页） ========== */
/* 打字机缓冲区：持续追加内容，超过固定长度时丢弃最左侧旧内容 */
var projectTickerBuffer = '';
var PROJECT_TICKER_MAX_LEN = 150;
/* 当前正在输出参数的工具调用ID：同一工具的分片不重复拼接标签 */
var projectCurrentToolCallId = null;
/* 已收到过流式分片的工具调用集合：全量数据仅在无分片时补充展示 */
var projectStreamSeen = {};
/* 执行中指示状态：用于结束时判断是否需要提示与刷新 */
var projectWsRunning = false;

/** 新一轮执行：清空打字机缓冲并显示执行中指示 */
function resetProjectSessionTicker() {
    showProjectSessionRunning();
    projectTickerBuffer = '';
    projectCurrentToolCallId = null;
    projectStreamSeen = {};
    var tEl = document.getElementById('projectSessionTicker');
    if (tEl) tEl.textContent = '';
}

/** 工具调用打字机展示：标签只在新工具时拼接一次，参数/结果分片持续追加 */
function handleProjectToolCallTicker(data) {
    var isNewTool = data.toolCallId && data.toolCallId !== projectCurrentToolCallId;
    if (data.status === 'preparing') {
        // 参数流式分片：新工具先拼标签，同一工具的后续分片只追加参数内容
        if (isNewTool) {
            projectCurrentToolCallId = data.toolCallId;
            appendProjectSessionTicker(' [调用工具 ' + (data.toolName || '') + '] ');
        }
        if (data.argumentsPartial != null) {
            projectStreamSeen['a:' + data.toolCallId] = true;
            appendProjectSessionTicker(String(data.argumentsPartial));
        }
    } else if (data.status === 'started') {
        if (isNewTool) {
            projectCurrentToolCallId = data.toolCallId;
            appendProjectSessionTicker(' [调用工具 ' + (data.toolName || '') + '] ');
        }
        // 全量参数仅在未收到过分片时补充展示（分片已展示则不重复）
        if (data.arguments != null && !projectStreamSeen['a:' + data.toolCallId]) {
            appendProjectSessionTicker(formatProjectTickerJson(data.arguments));
        }
    } else if (data.status === 'running') {
        // 执行结果流式分片
        if (data.resultPartial != null) {
            projectStreamSeen['r:' + data.toolCallId] = true;
            appendProjectSessionTicker(String(data.resultPartial));
        }
    } else if (data.status === 'executed') {
        projectCurrentToolCallId = null;
        if (data.result != null && !projectStreamSeen['r:' + data.toolCallId]) {
            appendProjectSessionTicker(' [工具返回] ' + formatProjectTickerJson(data.result));
        }
    } else if (data.status === 'failed' || data.status === 'rejected') {
        projectCurrentToolCallId = null;
        appendProjectSessionTicker(' [工具' + (data.status === 'failed' ? '执行失败' : '被拒绝') + '] ');
    }
}

/** 参数/结果对象转紧凑字符串（超长截断） */
function formatProjectTickerJson(obj) {
    if (obj == null) return '';
    var text;
    if (typeof obj === 'string') {
        text = obj;
    } else {
        try { text = JSON.stringify(obj); } catch (e) { text = String(obj); }
    }
    return text.length > 200 ? text.slice(0, 200) + '…' : text;
}

/** 显示"执行中"指示器 */
function showProjectSessionRunning() {
    var el = document.getElementById('projectSessionRunning');
    if (!el) return;
    el.style.display = 'flex';
    projectWsRunning = true;
}

/** 隐藏执行中指示器 */
function hideProjectSessionRunning() {
    var el = document.getElementById('projectSessionRunning');
    if (el) el.style.display = 'none';
    projectWsRunning = false;
}

/** 打字机追加：内容持续拼接到缓冲区右侧，超长丢弃最左侧旧内容 */
function appendProjectSessionTicker(content) {
    if (!content) return;
    var text = String(content).replace(/\s+/g, ' ').trim();
    if (!text) return;
    projectTickerBuffer += text;
    if (projectTickerBuffer.length > PROJECT_TICKER_MAX_LEN) {
        projectTickerBuffer = projectTickerBuffer.slice(projectTickerBuffer.length - PROJECT_TICKER_MAX_LEN);
    }
    var el = document.getElementById('projectSessionTicker');
    if (el) el.textContent = projectTickerBuffer;
}

function loadProjectTokenUsage(projectId) {
    fetch('/api/projects/' + projectId + '/token-usage?limit=30').then(function (r) { return r.json(); }).then(function (res) {
        if (res.code === 200 && res.data) {
            renderProjectTokenUsage(res.data);
        }
    }).catch(function (err) {
        console.error('加载项目 Token 用量失败:', err);
    });
}

function renderProjectTokenUsage(data) {
    var summaryEl = document.getElementById('projectTokenSummary');
    var chartEl = document.getElementById('projectTokenChart');
    if (!summaryEl || !chartEl) return;

    // 渲染时同步折叠状态（切换项目后保持当前展开/折叠状态）
    chartEl.style.display = tokenExpanded ? '' : 'none';
    document.getElementById('tokenToggleIcon').textContent = tokenExpanded ? '▾' : '▸';

    var summary = data.summary || {};
    var list = data.list || [];

    if (!list.length) {
        summaryEl.innerHTML = '';
        if (projectTokenChart) {
            projectTokenChart.destroy();
            projectTokenChart = null;
        }
        chartEl.innerHTML = '<div class="token-chart-empty">暂无用量记录</div>';
        return;
    }

    summaryEl.innerHTML = '<span class="ts-in">↑ ' + formatTokenCount(summary.inputTokens || 0) + '</span>'
        + '<span class="ts-out">↓ ' + formatTokenCount(summary.outputTokens || 0) + '</span>'
        + '<span class="ts-total">总 ' + formatTokenCount(summary.totalTokens || 0) + '</span>'
        + '<span class="ts-count">' + (summary.id || 0) + ' 次</span>';

    // 按时间正序展示最近 30 条
    var ordered = list.slice().reverse();
    renderBizTokenChart(chartEl, ordered, 'projectTokenChartCanvas', function (chart) {
        projectTokenChart = chart;
    });
}

/** 项目/任务详情页通用 token 柱状图渲染（参考会话右下角统计样式） */
function renderBizTokenChart(container, data, canvasId, onCreated) {
    var existing = document.getElementById(canvasId);
    if (existing) {
        existing.parentNode.innerHTML = '<canvas id="' + canvasId + '"></canvas>';
    } else {
        container.innerHTML = '<canvas id="' + canvasId + '"></canvas>';
    }
    var canvas = document.getElementById(canvasId);
    var isDark = document.body.classList.contains('dark-theme');

    var labels = data.map(function (d) {
        return d.createTime ? String(d.createTime).replace('T', ' ').substring(5, 16) : '';
    });
    var inputData = data.map(function (d) { return d.inputTokens || 0; });
    var outputData = data.map(function (d) { return d.outputTokens || 0; });

    var chart = new Chart(canvas, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                { label: '输入', data: inputData, backgroundColor: '#2196F3', borderRadius: 3 },
                { label: '输出', data: outputData, backgroundColor: '#4CAF50', borderRadius: 3 }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: { duration: 600, easing: 'easeOutQuart' },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: function (ctx) {
                            return ctx.dataset.label + ': ' + ctx.raw.toLocaleString();
                        },
                        footer: function (items) {
                            var total = items.reduce(function (sum, item) { return sum + item.raw; }, 0);
                            return '总量: ' + total.toLocaleString();
                        }
                    }
                }
            },
            scales: {
                x: {
                    stacked: true,
                    grid: { display: false },
                    ticks: { font: { size: 9 }, color: isDark ? '#888' : '#999', maxRotation: 45 }
                },
                y: {
                    stacked: true,
                    beginAtZero: true,
                    ticks: {
                        font: { size: 9 },
                        color: isDark ? '#888' : '#999',
                        callback: function (v) {
                            return v >= 1000 ? (v / 1000).toFixed(0) + 'k' : v;
                        }
                    },
                    grid: { color: isDark ? '#2d2d44' : '#f0f0f0' }
                }
            },
            interaction: { intersect: false, mode: 'index' }
        }
    });
    onCreated(chart);
}

/** token 数量格式化（1.2w 形式） */
function formatTokenCount(n) {
    n = Number(n) || 0;
    if (n >= 10000) return (n / 10000).toFixed(1) + 'w';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
    return String(n);
}

/* ========== 项目管理智能体迭代 ========== */

/** 渲染智能体迭代区块（配置了项目管理智能体时展示） */
function renderProjectAgentSection(project) {
    var sectionEl = document.getElementById('projectAgentSection');
    var infoEl = document.getElementById('projectAgentInfo');
    if (!sectionEl || !infoEl) return;

    if (!project.agentId) {
        sectionEl.style.display = 'none';
        currentProjectAgentSessionId = '';
        return;
    }

    sectionEl.style.display = 'block';
    // 智能体名称：优先从 agentsCache 匹配，回退显示编号
    var agentName = '智能体#' + project.agentId;
    (typeof agentsCache !== 'undefined' ? agentsCache : []).forEach(function (agent) {
        if (String(agent.id) === String(project.agentId)) {
            agentName = agent.name || agentName;
        }
    });
    var autoIterateText = project.autoIterate ? '已启用' : '未启用';
    // 操作按钮：开启/关闭自动迭代 + 提示词管理
    var toggleBtn = project.autoIterate
        ? '<button class="btn-agent-action" onclick="toggleProjectAutoIterate(false)">关闭迭代</button>'
        : '<button class="btn-agent-action btn-agent-action-enable" onclick="toggleProjectAutoIterate(true)">开启迭代</button>';
    var promptBtn = '<button class="btn-agent-action btn-agent-action-icon" title="管理提示词" onclick="manageProjectIteratePrompt()">&#128393;</button>';
    // 已配置项目管理智能体即可手动下发指令（不依赖自动迭代开关）
    var runBtn = project.agentId
        ? '<button class="btn-agent-action btn-agent-action-run" id="btnRunIterate" onclick="showProjectIteratePrompt()">下发指令</button>'
        : '';
    var infoHtml = '<div class="agent-info-item">' +
            '<span class="info-label">项目管理智能体：</span><span class="info-value">' + escapeHtml(agentName) + '</span>' +
            runBtn +
        '</div>' +
        '<div class="agent-info-item">' +
            '<span class="info-label">自动迭代：</span><span class="info-value' + (project.autoIterate ? ' agent-enabled' : '') + '">' + autoIterateText + '</span>' +
            toggleBtn +
        '</div>' +
        '<div class="agent-info-item">' +
            '<span class="info-label">迭代要求：</span><span class="info-value">' + (project.iteratePrompt ? escapeHtml(project.iteratePrompt) : '未设置') + '</span>' +
            promptBtn +
        '</div>';
    infoEl.innerHTML = infoHtml;
}

/** 项目详情页：弹出指令输入框，下发一轮项目迭代（同步调用，返回执行结果与失败原因） */
function showProjectIteratePrompt() {
    if (!currentProjectId) return;
    showPrompt('下发指令', '输入要下发给项目管理智能体的指令（留空则执行默认项目迭代）：').then(function (input) {
        if (input === null) return; // 取消
        runProjectIterate(input);
    });
}

/** 执行一轮项目迭代：userMessage 为空时走默认迭代指令 */
function runProjectIterate(userMessage) {
    if (!currentProjectId) return;
    var btn = document.getElementById('btnRunIterate');
    if (btn) {
        btn.disabled = true;
        btn.textContent = '执行中…';
        btn.classList.remove('btn-agent-action-run');
    }
    fetch('/api/projects/' + currentProjectId + '/iterate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userMessage: userMessage || '' })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200 && res.data) {
                // data: { success, message }
                showToast(res.data.message || (res.data.success ? '迭代执行完成' : '迭代执行失败'), res.data.success ? 'success' : 'error');
            } else {
                showToast(res.msg || '执行失败', 'error');
            }
            // 刷新详情：更新会话面板/任务列表/操作日志
            loadProjectDetail(currentProjectId);
        })
        .catch(function (err) {
            console.error('手动执行项目迭代失败:', err);
            showToast('执行请求失败', 'error');
            if (btn) { btn.disabled = false; btn.textContent = '下发指令'; btn.classList.add('btn-agent-action-run'); }
        });
}

/** 项目详情页：开启/关闭自动迭代（二次确认后提交） */
function toggleProjectAutoIterate(enabled) {
    if (!currentProjectId) return;
    var confirmMsg = enabled
        ? '开启后，系统将按调度周期自动驱动项目管理智能体迭代本项目，是否确认开启？'
        : '关闭后，项目管理智能体将不再自动迭代本项目，是否确认关闭？';
    showConfirm(confirmMsg).then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/projects/' + currentProjectId + '/iterate-config', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ autoIterate: enabled })
        }).then(function (r) { return r.json(); }).then(function (res) {
            if (res.code === 200) {
                showToast(enabled ? '自动迭代已开启' : '自动迭代已关闭', 'success');
                loadProjectDetail(currentProjectId);
                // 同步刷新项目列表（列表机器人图标随开关状态变化）
                loadProjects(currentPage);
            } else {
                showToast(res.msg || '操作失败', 'error');
            }
        }).catch(function (err) {
            console.error('更新自动迭代配置失败:', err);
            showToast('操作失败', 'error');
        });
    });
}

/** 项目详情页：管理迭代要求提示词（多行输入弹框） */
function manageProjectIteratePrompt() {
    if (!currentProject) return;
    var current = currentProject.iteratePrompt || '';
    showPrompt('编辑迭代要求提示词（自动迭代与手动下发指令时注入项目管理智能体）',
        '如：每轮迭代优先处理待验收任务、产出物写入 docs 目录等', current
    ).then(function (val) {
        if (val === null) return; // 取消
        fetch('/api/projects/' + currentProjectId + '/iterate-config', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ iteratePrompt: val })
        }).then(function (r) { return r.json(); }).then(function (res) {
            if (res.code === 200) {
                showToast('迭代要求提示词已保存', 'success');
                loadProjectDetail(currentProjectId);
            } else {
                showToast(res.msg || '保存失败', 'error');
            }
        }).catch(function (err) {
            console.error('保存迭代要求提示词失败:', err);
            showToast('保存失败', 'error');
        });
    });
}

/** 加载项目管理智能体会话（标题等展示） */
function loadProjectSession(projectId) {
    if (!projectId) return;
    fetch('/api/projects/' + projectId + '/session')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                renderProjectSession(res.data);
            }
        })
        .catch(function (err) {
            console.error('加载项目会话失败:', err);
        });
}

/** 渲染项目管理智能体会话面板（参考任务详情页会话展示逻辑） */
function renderProjectSession(session) {
    var panelEl = document.getElementById('projectSessionPanel');
    if (!panelEl) return;

    if (!session || !session.sessionId) {
        currentProjectAgentSessionId = '';
        panelEl.innerHTML = '<div class="project-session-empty">暂无迭代会话（启用自动迭代后由调度任务自动创建）</div>';
        return;
    }

    var sid = session.sessionId;
    currentProjectAgentSessionId = sid;
    var time = formatTime(session.createTime) || '';
    // 标题优先；无标题时回退到 sessionId
    var displayTitle = session.title ? session.title : sid;
    var rowHtml = '<div class="project-session-row">' +
            '<span class="project-session-info" title="' + escapeHtml(sid) + '">' +
                escapeHtml(displayTitle) + (time ? ' · ' + escapeHtml(time) : '') +
            '</span>' +
            '<a class="project-session-link" href="/?sessionId=' + encodeURIComponent(sid) + '" target="_blank">查看记录</a>' +
            '<button class="project-session-link project-session-clear" title="清空该会话的历史记录" data-sid="' + escapeHtml(sid) + '">清空历史</button>' +
        '</div>';
    panelEl.innerHTML = rowHtml;
    var btn = panelEl.querySelector('.project-session-clear');
    if (btn) {
        btn.onclick = function () { clearProjectSessionHistory(this.getAttribute('data-sid')); };
    }
}

/** 清空项目管理智能体会话的历史记录（复用会话清理接口） */
function clearProjectSessionHistory(sessionId) {
    if (!sessionId) return;
    showConfirm('确定清空该会话的历史记录吗？清空后智能体将丢失该会话的上下文记忆，且不可恢复。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/session/' + encodeURIComponent(sessionId) + '/clear', { method: 'POST' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('会话历史已清空', 'success');
                } else {
                    showToast(res.msg || '清空失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('清空会话历史失败:', err);
                showToast('清空失败', 'error');
            });
    });
}
