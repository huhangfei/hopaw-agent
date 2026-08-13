/**
 * 任务详情独立页面脚本
 */
var currentTaskId = null;
var currentTask = null;

var statusMap = {
    pending: { label: '待启动', color: '#6b7280' },
    pending_execution: { label: '待执行', color: '#f59e0b' },
    processing: { label: '处理中', color: '#3b82f6' },
    pending_acceptance: { label: '待验收', color: '#8b5cf6' },
    completed: { label: '已完成', color: '#10b981' },
    failed: { label: '失败', color: '#ef4444' },
    rejected: { label: '已驳回', color: '#ef4444' },
    closed: { label: '已关闭', color: '#9ca3af' }
};

var fileTypeIcons = {
    image: '🖼️', video: '🎬', audio: '🎵',
    pdf: '📄', markdown: '📝', text: '📃', file: '📦'
};

document.addEventListener('DOMContentLoaded', function () {
    var pathParts = window.location.pathname.split('/');
    var id = pathParts[pathParts.length - 1];
    if (!id || isNaN(Number(id))) {
        showToast('无效的任务ID', 'error');
        return;
    }
    currentTaskId = Number(id);
    loadTaskDetail(currentTaskId);
});

function loadTaskDetail(id) {
    fetch('/api/workflow/tasks/' + id)
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200 || !res.data) {
                showToast(res.msg || '加载任务失败', 'error');
                return;
            }
            currentTask = res.data;
            renderTaskDetail(currentTask);
            loadTaskAttachments(id);
            loadTaskSessions(id);
            loadComments(id);
        })
        .catch(function (err) {
            console.error('加载任务失败:', err);
            showToast('加载任务失败', 'error');
        });
}

function renderTaskDetail(task) {
    document.title = (task.title || '任务详情') + ' - HoPaw';
    document.getElementById('taskDetailTitle').textContent = task.title || '未命名任务';

    var statusInfo = statusMap[task.status] || { label: task.status || '-', color: '#6b7280' };
    var statusTag = document.getElementById('taskDetailStatus');
    statusTag.textContent = statusInfo.label;
    statusTag.className = 'task-status-tag status-' + (task.status || 'pending');

    var agentName = task.agentName || (task.agentId ? '智能体#' + task.agentId : '未指定');
    var projectName = task.projectName || (task.projectId ? '项目#' + task.projectId : '无');

    var infoHtml = '' +
        '<div class="task-detail-info-item"><span class="info-label">智能体</span><span class="info-value">' + escapeHtml(agentName) + '</span></div>' +
        '<div class="task-detail-info-item"><span class="info-label">所属项目</span><span class="info-value">' + escapeHtml(projectName) + '</span></div>' +
        '<div class="task-detail-info-item"><span class="info-label">创建时间</span><span class="info-value">' + escapeHtml(formatTime(task.createTime)) + '</span></div>' +
        '<div class="task-detail-info-item"><span class="info-label">开始时间</span><span class="info-value">' + escapeHtml(formatTime(task.startTime) || '未设置') + '</span></div>';
    if (task.executionPeriod && task.executionPeriod > 0) {
        infoHtml += '<div class="task-detail-info-item"><span class="info-label">执行时段</span><span class="info-value">' + task.executionPeriod + ' 分钟</span></div>';
    }
    if (task.rejectReason) {
        infoHtml += '<div class="task-detail-info-item"><span class="info-label">驳回/失败原因</span><span class="info-value">' + escapeHtml(task.rejectReason) + '</span></div>';
    }
    document.getElementById('taskDetailInfo').innerHTML = infoHtml;

    var contentEl = document.getElementById('taskDetailContent');
    if (task.content) {
        contentEl.classList.remove('empty');
        contentEl.textContent = task.content;
    } else {
        contentEl.classList.add('empty');
        contentEl.textContent = '暂无任务内容';
    }

    renderTaskActions(task);
}

function renderTaskActions(task) {
    var actionsEl = document.getElementById('taskDetailActions');
    var status = task.status;
    var html = '';

    if (status === 'pending') {
        html += '<button class="task-action-btn btn-success" onclick="approveTask(' + task.id + ')">审核通过</button>';
    } else if (status === 'pending_acceptance') {
        html += '<button class="task-action-btn btn-success" onclick="acceptTask(' + task.id + ')">验收通过</button>';
        html += '<button class="task-action-btn btn-warning" onclick="rejectTask(' + task.id + ')">打回重做</button>';
        html += '<button class="task-action-btn btn-secondary" onclick="closeTask(' + task.id + ')">关闭任务</button>';
    } else if (status === 'failed') {
        html += '<button class="task-action-btn btn-secondary" onclick="closeTask(' + task.id + ')">关闭任务</button>';
    }

    actionsEl.innerHTML = html;
}

/* ========== 附件 ========== */
function loadTaskAttachments(taskId) {
    fetch('/api/workflow/tasks/' + taskId + '/attachments')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200) {
                document.getElementById('taskDetailAttachments').innerHTML = '<div class="task-detail-empty">加载附件失败</div>';
                return;
            }
            renderTaskAttachments(res.data || []);
        })
        .catch(function (err) {
            console.error('加载附件失败:', err);
            document.getElementById('taskDetailAttachments').innerHTML = '<div class="task-detail-empty">加载附件失败</div>';
        });
}

function renderTaskAttachments(list) {
    var container = document.getElementById('taskDetailAttachments');
    if (!list || !list.length) {
        container.innerHTML = '<div class="task-detail-empty">暂无关联附件</div>';
        return;
    }
    container.innerHTML = list.map(function (att) {
        var icon = fileTypeIcons[att.fileType] || '📦';
        var attId = att.attachmentId;
        return '<div class="task-attachment-row">' +
            '<div class="task-attachment-info" onclick="previewAttachment(' + attId + ')">' +
                '<span class="att-icon">' + icon + '</span>' +
                '<span class="att-name" title="' + escapeHtml(att.originalName || '') + '">' + escapeHtml(att.originalName || '') + '</span>' +
            '</div>' +
            '<div class="task-attachment-actions">' +
                '<button class="btn-mini btn-preview" onclick="previewAttachment(' + attId + ')">预览</button>' +
                '<button class="btn-mini btn-unbind" onclick="unbindAttachment(' + currentTaskId + ', ' + attId + ')">解绑</button>' +
            '</div>' +
        '</div>';
    }).join('');
}

/* ========== 附件上传 ========== */
function triggerTaskDetailUpload() {
    document.getElementById('taskDetailFileInput').click();
}

function onTaskDetailFileSelected(input) {
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
                var ids = list.map(function (att) { return att.id; });
                // 上传完成后绑定到当前任务
                bindAttachmentsToTask(currentTaskId, ids);
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

function bindAttachmentsToTask(taskId, attachmentIds) {
    fetch('/api/workflow/tasks/' + taskId + '/attachments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ attachmentIds: attachmentIds })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast('上传并关联成功', 'success');
                loadTaskAttachments(taskId);
            } else {
                showToast(res.msg || '关联失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('关联附件失败:', err);
            showToast('关联失败', 'error');
        });
}

function unbindAttachment(taskId, attId) {
    showConfirm('确定要解绑该附件吗？').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/tasks/' + taskId + '/attachments/' + attId, { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('解绑成功', 'success');
                    loadTaskAttachments(taskId);
                } else {
                    showToast(res.msg || '解绑失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('解绑附件失败:', err);
                showToast('解绑失败', 'error');
            });
    });
}

/* ========== 附件预览（复用公共模块） ========== */
function previewAttachment(id) {
    AttachmentPreview.open(id);
}

/* ========== 会话记录 ========== */
function loadTaskSessions(taskId) {
    fetch('/api/workflow/tasks/' + taskId + '/sessions')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200) {
                document.getElementById('taskDetailSessions').innerHTML = '<div class="task-detail-empty">加载会话失败</div>';
                return;
            }
            renderTaskSessions(res.data || []);
        })
        .catch(function (err) {
            console.error('加载会话失败:', err);
            document.getElementById('taskDetailSessions').innerHTML = '<div class="task-detail-empty">加载会话失败</div>';
        });
}

function renderTaskSessions(list) {
    var container = document.getElementById('taskDetailSessions');
    if (!list || !list.length) {
        container.innerHTML = '<div class="task-detail-empty">暂无会话记录</div>';
        return;
    }
    container.innerHTML = list.map(function (session) {
        var sid = session.sessionId || '';
        var time = formatTime(session.createTime) || '';
        return '<div class="task-session-row">' +
            '<span class="task-session-info" title="' + escapeHtml(sid) + '">' +
                (sid ? escapeHtml(sid) : '会话记录') + (time ? ' · ' + escapeHtml(time) : '') +
            '</span>' +
            '<a class="task-session-link" href="/?sessionId=' + encodeURIComponent(sid) + '" target="_blank">查看记录</a>' +
        '</div>';
    }).join('');
}

/* ========== 评论 ========== */
function loadComments(taskId) {
    fetch('/api/workflow/tasks/' + taskId + '/comments')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200) {
                document.getElementById('taskDetailComments').innerHTML = '<div class="task-detail-empty">加载评论失败</div>';
                return;
            }
            renderComments(res.data || []);
        })
        .catch(function (err) {
            console.error('加载评论失败:', err);
            document.getElementById('taskDetailComments').innerHTML = '<div class="task-detail-empty">加载评论失败</div>';
        });
}

function renderComments(comments) {
    var container = document.getElementById('taskDetailComments');
    if (!comments || !comments.length) {
        container.innerHTML = '<div class="task-detail-empty">暂无评论</div>';
        return;
    }
    container.innerHTML = comments.map(function (c) {
        // 区分评论者身份：agent=智能体，其他（含 null 旧数据）按用户处理
        var isAgent = c.commenterType === 'agent';
        var roleLabel = isAgent ? '智能体' : '用户';
        var roleClass = isAgent ? 'comment-item-agent' : 'comment-item-user';
        var who = isAgent ? (c.commenterId || '智能体') : (c.userId || '匿名');
        return '<div class="comment-item ' + roleClass + '">' +
            '<div class="comment-item-header">' +
                '<span class="comment-item-user">' + escapeHtml(roleLabel + ' · ' + who) + '</span>' +
                '<span class="comment-item-time">' + escapeHtml(formatTime(c.createTime)) + '</span>' +
            '</div>' +
            '<div class="comment-item-content">' + escapeHtml(c.content || '') + '</div>' +
        '</div>';
    }).join('');
}

function addComment() {
    var input = document.getElementById('taskCommentInput');
    var content = input.value.trim();
    if (!content) {
        showToast('请输入评论内容', 'error');
        return;
    }

    fetch('/api/workflow/tasks/' + currentTaskId + '/comments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: content })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast('评论成功', 'success');
                input.value = '';
                loadComments(currentTaskId);
            } else {
                showToast(res.msg || '评论失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('评论失败:', err);
            showToast('评论失败', 'error');
        });
}

/* ========== 任务操作 ========== */
function approveTask(id) {
    showConfirm('确定审核通过该任务吗？').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/tasks/' + id + '/approve', { method: 'PUT' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('审核通过', 'success');
                    loadTaskDetail(id);
                } else {
                    showToast(res.msg || '操作失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('审核失败:', err);
                showToast('操作失败', 'error');
            });
    });
}

function acceptTask(id) {
    showConfirm('确定验收通过该任务吗？').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/tasks/' + id + '/accept', { method: 'PUT' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('验收通过', 'success');
                    loadTaskDetail(id);
                } else {
                    showToast(res.msg || '操作失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('验收失败:', err);
                showToast('操作失败', 'error');
            });
    });
}

function rejectTask(id) {
    var reason = window.prompt('请输入打回重做的原因：');
    if (reason === null) return;
    if (!reason.trim()) {
        showToast('请输入打回原因', 'error');
        return;
    }
    fetch('/api/workflow/tasks/' + id + '/reject', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: reason.trim() })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200) {
                showToast('已打回重做', 'success');
                loadTaskDetail(id);
            } else {
                showToast(res.msg || '操作失败', 'error');
            }
        })
        .catch(function (err) {
            console.error('打回失败:', err);
            showToast('操作失败', 'error');
        });
}

function closeTask(id) {
    showConfirm('确定要关闭该任务吗？关闭后无法继续操作。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/tasks/' + id + '/close', { method: 'PUT' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('任务已关闭', 'success');
                    loadTaskDetail(id);
                } else {
                    showToast(res.msg || '操作失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('关闭失败:', err);
                showToast('操作失败', 'error');
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

/** 格式化为 HH:mm */
function formatHM(timeStr) {
    if (!timeStr) return '';
    var d = new Date(timeStr);
    if (isNaN(d.getTime())) return '';
    var pad = function (n) { return n < 10 ? '0' + n : n; };
    return pad(d.getHours()) + ':' + pad(d.getMinutes());
}

/** 格式化为 YYYY-MM-DD */
function formatDateOnly(timeStr) {
    if (!timeStr) return '';
    var d = new Date(timeStr);
    if (isNaN(d.getTime())) return '';
    var pad = function (n) { return n < 10 ? '0' + n : n; };
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}

/**
 * 构造执行时段展示 HTML：
 * - 有开始时间 + 执行时段：HH:mm~HH:mm（下方小字显示日期与时长）
 * - 仅有开始时间：HH:mm（下方小字显示日期）
 * - 都没有：未设置
 */
function buildTimeRangeHtml(startTime, executionPeriod) {
    if (!startTime) {
        return '<div class="task-detail-info-item"><span class="info-label">执行时段</span><span class="info-value">未设置</span></div>';
    }
    var startHM = formatHM(startTime);
    var dateStr = formatDateOnly(startTime);
    var badgeText, subText;
    if (executionPeriod && executionPeriod > 0) {
        var endDate = new Date(new Date(startTime).getTime() + executionPeriod * 60 * 1000);
        var endHM = formatHM(endDate);
        badgeText = startHM + '~' + endHM;
        subText = dateStr + ' · ' + executionPeriod + '分钟';
    } else {
        badgeText = startHM;
        subText = dateStr;
    }
    return '<div class="task-detail-info-item">' +
        '<span class="info-label">执行时段</span>' +
        '<span class="info-value time-range-value">' +
            '<span class="time-range-badge">' + escapeHtml(badgeText) + '</span>' +
            '<span class="time-range-sub">' + escapeHtml(subText) + '</span>' +
        '</span>' +
    '</div>';
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
