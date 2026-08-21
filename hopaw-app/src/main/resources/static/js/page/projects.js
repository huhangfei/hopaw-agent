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
var logsCache = [];               // 操作日志缓存（用于类型切换时重渲染）
var logFilter = 'all';            // 日志过滤类型：all=全部 / important=重点

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
});

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
    return '<div class="project-list-item' + activeCls + '" onclick="selectProject(' + project.id + ')" data-id="' + project.id + '">' +
        '<div class="list-item-name">' + escapeHtml(project.name || '') + '</div>' +
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
    // 切换项目时重置日志过滤为全部
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

    // 状态徽标
    var st = PROJECT_STATUS[project.status] || { label: project.status || '未知', color: '#999' };
    var badge = document.getElementById('detailStatusBadge');
    badge.textContent = st.label;
    badge.style.background = st.color;

    // 创建人 / 创建时间
    var metaEl = document.getElementById('detailMeta');
    metaEl.innerHTML = '<span class="meta-item">创建人：<em>' + escapeHtml(project.creatorName || '未知') + '</em></span>' +
        '<span class="meta-item">创建时间：<em>' + formatTime(project.createTime) + '</em></span>';

    // 状态流转按钮
    renderStatusActions(project.status);

    // 描述
    document.getElementById('detailDesc').textContent = project.description || '暂无描述';

    // 项目空间目录路径
    var spaceDirEl = document.getElementById('spaceDirInfo');
    if (project.spaceDir) {
        spaceDirEl.innerHTML = '<span class="space-dir-label">空间目录：</span><code class="space-dir-path">' + escapeHtml(project.spaceDir) + '</code>';
    } else {
        spaceDirEl.innerHTML = '<span class="space-dir-label">空间目录：未创建</span>';
    }

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

function renderDetailTasks(tasks) {
    var container = document.getElementById('detailTasks');
    var countEl = document.getElementById('taskCount');
    if (!tasks || !tasks.length) {
        countEl.textContent = '(0)';
        container.innerHTML = '<p class="detail-empty-text">暂无任务</p>';
        return;
    }
    countEl.textContent = '(' + tasks.length + ')';
    container.innerHTML = tasks.map(function (task) {
        var st = TASK_STATUS[task.status] || { label: task.status || '未知', color: '#999' };
        return '<a class="detail-task-item" href="/tasks-board/' + task.id + '" target="_blank">' +
            '<div class="task-main">' +
                '<span class="task-name" title="' + escapeHtml(task.title || '') + '">' + escapeHtml(task.title || '') + '</span>' +
                '<span class="task-sub">' +
                    '<span class="task-creator">' + escapeHtml(task.creatorName || '未知') + '</span>' +
                    '<span class="task-time">' + formatTime(task.createTime) + '</span>' +
                '</span>' +
            '</div>' +
            '<span class="task-status" style="color:' + st.color + '">' + st.label + '</span>' +
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
function previewProjectFile(path) {
    if (!currentProjectId || !path) return;
    var name = path.indexOf('/') >= 0 ? path.substring(path.lastIndexOf('/') + 1) : path;
    // 构造内联预览 URL（供 iframe 内的公共预览组件加载文件内容）
    var fileUrl = '/api/projects/' + currentProjectId + '/files/preview?path=' + encodeURIComponent(path);
    var previewSrc = '/file-preview?url=' + encodeURIComponent(fileUrl) +
        '&name=' + encodeURIComponent(name);
    var iframe = document.getElementById('projectFilePreviewFrame');
    if (iframe) {
        iframe.src = previewSrc;
    }
    document.getElementById('previewModalTitle').textContent = name;
    Modal.open('projectFilePreviewModal');
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

    if (!logs.length) {
        countEl.textContent = '(' + logs.length + ')';
        container.innerHTML = '<p class="detail-empty-text">' + (logFilter === 'important' ? '暂无重点日志' : '暂无操作日志') + '</p>';
        return;
    }
    countEl.textContent = '(' + logs.length + ')';
    // 后端按时间正序返回（最早在前），直接渲染
    container.innerHTML = logs.map(function (log) {
        var actionLabel = LOG_ACTION_LABELS[log.action] || log.action || '操作';
        var isImportant = log.logType === 'important';
        return '<div class="log-item' + (isImportant ? ' log-item-important' : '') + '">' +
            '<div class="log-dot' + (isImportant ? ' log-dot-important' : '') + '"></div>' +
            '<div class="log-content">' +
                '<div class="log-line">' +
                    '<span class="log-action">' + escapeHtml(actionLabel) + '</span>' +
                    (isImportant ? '<span class="log-important-badge">重点</span>' : '') +
                    '<span class="log-detail">' + escapeHtml(log.detail || '') + '</span>' +
                '</div>' +
                '<div class="log-meta">' +
                    '<span class="log-operator">' + escapeHtml(log.operatorName || '未知') + '</span>' +
                    '<span class="log-time">' + formatTime(log.createTime) + '</span>' +
                '</div>' +
            '</div>' +
        '</div>';
    }).join('');
}

/* 切换日志过滤类型（全部/总结），并同步连体按钮选中态 */
function switchLogFilter(filter, event) {
    if (event) event.stopPropagation();
    logFilter = filter;
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
}

/* ========== 新建/编辑项目 ========== */
function showAddModal() {
    document.getElementById('projectModalTitle').textContent = '新建项目';
    document.getElementById('projectId').value = '';
    document.getElementById('projectName').value = '';
    document.getElementById('projectDescription').value = '';
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
    // 编辑时不允许修改项目空间
    document.getElementById('spaceModeGroup').style.display = 'none';
    Modal.open('projectModal');
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

    var payload = { name: name, description: description };
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
