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
    Promise.all([
        fetch('/api/projects/' + id).then(function (r) { return r.json(); }),
        fetch('/api/attachments/biz/project/' + id).then(function (r) { return r.json(); }),
        fetch('/api/projects/' + id + '/tasks').then(function (r) { return r.json(); }),
        fetch('/api/projects/' + id + '/logs').then(function (r) { return r.json(); })
    ]).then(function (results) {
        var projRes = results[0], attRes = results[1], taskRes = results[2], logRes = results[3];
        if (projRes.code !== 200 || !projRes.data) {
            showToast(projRes.msg || '加载项目详情失败', 'error');
            return;
        }
        currentProject = projRes.data;
        currentProjectId = currentProject.id;
        renderDetail(currentProject, (attRes.data || []), (taskRes.data || []), (logRes.data || []));
    }).catch(function (err) {
        console.error('加载项目详情失败:', err);
        showToast('加载项目详情失败', 'error');
    });
}

function renderDetail(project, attachments, tasks, logs) {
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

    // 附件列表
    renderDetailAttachments(attachments);

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

function renderDetailAttachments(attachments) {
    var container = document.getElementById('detailAttachments');
    if (!attachments || !attachments.length) {
        container.innerHTML = '<p class="detail-empty-text">暂无附件，点击上方“上传附件”添加</p>';
        return;
    }
    var iconMap = {
        image: '🖼️', video: '🎬', audio: '🎵',
        pdf: '📄', markdown: '📝', text: '📃', file: '📦'
    };
    container.innerHTML = attachments.map(function (att) {
        var icon = iconMap[att.fileType] || '📦';
        return '<div class="detail-att-item">' +
            '<span class="att-icon" onclick="previewAttachment(' + att.id + ')" style="cursor:pointer">' + icon + '</span>' +
            '<span class="att-name" title="' + escapeHtml(att.originalName || '') + '" onclick="previewAttachment(' + att.id + ')" style="cursor:pointer">' + escapeHtml(att.originalName || '') + '</span>' +
            '<button class="btn-att-preview" onclick="previewAttachment(' + att.id + ')" title="预览">预览</button>' +
            '<button class="btn-att-remove" onclick="removeProjectAttachment(' + att.id + ')" title="删除">删除</button>' +
        '</div>';
    }).join('');
}

/* ========== 附件预览（复用公共模块） ========== */
function previewAttachment(id) {
    AttachmentPreview.open(id);
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

/* ========== 操作日志（可折叠） ========== */
// 动作类型中文标签
var LOG_ACTION_LABELS = {
    create: '创建',
    update: '更新',
    status_change: '状态变更',
    delete: '删除',
    attachment_upload: '上传附件',
    attachment_delete: '删除附件',
    attachment_bind: '关联附件',
    attachment_unbind: '取消关联附件',
    task_bind: '关联任务',
    task_unbind: '取消关联任务'
};

function renderDetailLogs(logs) {
    var container = document.getElementById('detailLogs');
    var countEl = document.getElementById('logsCount');
    var iconEl = document.getElementById('logsToggleIcon');

    // 应用折叠状态
    container.style.display = logsExpanded ? 'block' : 'none';
    iconEl.textContent = logsExpanded ? '▾' : '▸';

    if (!logs || !logs.length) {
        countEl.textContent = '(0)';
        container.innerHTML = '<p class="detail-empty-text">暂无操作日志</p>';
        return;
    }
    countEl.textContent = '(' + logs.length + ')';
    // 后端按时间正序返回（最早在前），直接渲染
    container.innerHTML = logs.map(function (log) {
        var actionLabel = LOG_ACTION_LABELS[log.action] || log.action || '操作';
        return '<div class="log-item">' +
            '<div class="log-dot"></div>' +
            '<div class="log-content">' +
                '<div class="log-line">' +
                    '<span class="log-action">' + escapeHtml(actionLabel) + '</span>' +
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
    Modal.open('projectModal');
}

function editCurrentProject() {
    if (!currentProject) return;
    var project = currentProject;
    document.getElementById('projectModalTitle').textContent = '编辑项目';
    document.getElementById('projectId').value = project.id || '';
    document.getElementById('projectName').value = project.name || '';
    document.getElementById('projectDescription').value = project.description || '';
    Modal.open('projectModal');
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

    var body = JSON.stringify({ name: name, description: description });
    var url = id ? '/api/projects/' + id : '/api/projects';
    var method = id ? 'PUT' : 'POST';

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: body
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
            console.error('保存项目失败:', err);
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

/* ========== 附件直接操作（详情页内）========== */
function triggerProjectAttachmentUpload() {
    if (!currentProjectId) {
        showToast('请先选择项目', 'error');
        return;
    }
    document.getElementById('projectAttachmentFileInput').click();
}

function onProjectAttachmentSelected(input) {
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
    formData.append('source', 'project');
    formData.append('bizId', currentProjectId);

    fetch('/api/attachments/upload', {
        method: 'POST',
        body: formData
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast('上传成功', 'success');
                loadProjects(currentPage);
                loadProjectDetail(currentProjectId);
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

function removeProjectAttachment(attId) {
    showConfirm('确定要删除该附件吗？删除后文件将被清除且无法恢复。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/attachments/' + attId, { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('删除成功', 'success');
                    loadProjects(currentPage);
                    if (currentProjectId) {
                        loadProjectDetail(currentProjectId);
                    }
                } else {
                    showToast(res.msg || '删除失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('删除附件失败:', err);
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
