/**
 * 任务详情独立页面脚本
 */
var currentTaskId = null;
var currentTask = null;
/* 本任务关联的会话ID集合：用于匹配 WebSocket 消息 */
var taskSessionIds = [];
var taskWs = null;
var taskWsRunning = false;

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

document.addEventListener('DOMContentLoaded', function () {
    var pathParts = window.location.pathname.split('/');
    var id = pathParts[pathParts.length - 1];
    if (!id || isNaN(Number(id))) {
        showToast('无效的任务ID', 'error');
        return;
    }
    currentTaskId = Number(id);
    loadTaskDetail(currentTaskId);
    connectTaskWebSocket();
    // 订阅全局通知：仅刷新当前打开的任务（或所属项目）
    connectNoticeWebSocket(handleTaskDetailNotice);
    // 预加载任务弹框所需下拉数据（智能体/项目/前置任务，公共 task-modal.js）
    loadAgents();
    loadProjects();
    loadTasksForPreconditions();
});

/* 任务弹框保存成功后的刷新回调（供公共 task-modal.js 调用） */
window.onTaskModalSaved = function () {
    if (currentTaskId != null) {
        loadTaskDetail(currentTaskId);
    }
};

/** 全局通知处理：任务状态变更时若正是当前任务则刷新；项目状态变更刷新详情基础信息 */
function handleTaskDetailNotice(data) {
    if (!data || data.subtype !== 'status_change' || currentTaskId == null) return;
    var c = data.content || {};
    if (data.type === 'task' && c.taskId != null && Number(c.taskId) === Number(currentTaskId)) {
        showToast('任务状态已变更，正在刷新', 'success');
        loadTaskDetail(currentTaskId);
    } else if (data.type === 'project' && currentTask && currentTask.projectId != null
        && c.projectId != null && Number(c.projectId) === Number(currentTask.projectId)) {
        loadTaskDetail(currentTaskId);
    }
}

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
            loadTaskSessions(id);
            loadComments(id);
            loadTaskTokenUsage(id);
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
        '<div class="task-detail-info-item"><span class="info-label">创建者</span><span class="info-value">' + taskDetailCreatorHtml(task) + '</span></div>' +
        '<div class="task-detail-info-item"><span class="info-label">创建时间</span><span class="info-value">' + escapeHtml(formatTime(task.createTime)) + '</span></div>' +
        '<div class="task-detail-info-item"><span class="info-label">开始时间</span><span class="info-value">' + escapeHtml(formatTime(task.startTime) || '未设置') + '</span></div>';
    if (task.executionPeriod && task.executionPeriod > 0) {
        infoHtml += '<div class="task-detail-info-item"><span class="info-label">执行时段</span><span class="info-value">' + task.executionPeriod + ' 分钟</span></div>';
    }
    document.getElementById('taskDetailInfo').innerHTML = infoHtml;

    // 驳回/失败原因：独立突出显示区域（任务内容上方）
    var reasonEl = document.getElementById('taskDetailRejectReason');
    var reasonTextEl = document.getElementById('taskDetailRejectReasonText');
    var reasonLabelEl = document.getElementById('taskDetailRejectReasonLabel');
    var reasonTipEl = document.getElementById('taskDetailRejectReasonTip');
    if (task.rejectReason) {
        reasonTextEl.textContent = task.rejectReason;
        reasonLabelEl.textContent = task.status === 'rejected' ? '驳回原因' : '失败原因';
        // 失败状态追加提示：会话上下文异常时建议清空会话历史后重试
        reasonTipEl.style.display = (task.status === 'failed') ? 'flex' : 'none';
        reasonEl.style.display = 'block';
    } else {
        reasonEl.style.display = 'none';
    }

    var contentEl = document.getElementById('taskDetailContent');
    if (task.content) {
        contentEl.classList.remove('empty');
        // 任务内容按 Markdown 渲染（marked 未加载时降级为转义纯文本）
        if (typeof marked !== 'undefined' && marked.parse) {
            contentEl.innerHTML = marked.parse(task.content, { breaks: true, gfm: true });
        } else {
            contentEl.textContent = task.content;
        }
    } else {
        contentEl.classList.add('empty');
        contentEl.textContent = '暂无任务内容';
    }

    renderPreconditions(task.preconditions || []);
    renderTaskActions(task);
}

/* ========== 前置任务展示 ========== */

/** 判断单条前置条件是否满足：前置任务当前状态命中任一要求状态即满足；前置任务已删除视为满足（与后端调度逻辑一致） */
function isPreconditionSatisfied(pc) {
    if (!pc.preTaskStatus) return true;
    var required = (pc.requiredStatus || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
    return required.indexOf(pc.preTaskStatus) !== -1;
}

/** 渲染前置任务列表：每条展示前置任务标题、当前状态、要求状态及满足与否；无前置任务时隐藏区块 */
function renderPreconditions(preconditions) {
    var sectionEl = document.getElementById('taskDetailPreconditions');
    // 注意：ID 使用 taskDetail 前缀，避免与公共任务弹框(task-modal)内的 preconditionList 重复
    var listEl = document.getElementById('taskDetailPreconditionList');
    var summaryEl = document.getElementById('taskDetailPreconditionSummary');

    if (!preconditions.length) {
        sectionEl.style.display = 'none';
        return;
    }

    var satisfiedCount = 0;
    var html = preconditions.map(function (pc) {
        var satisfied = isPreconditionSatisfied(pc);
        if (satisfied) satisfiedCount++;

        // 前置任务当前状态徽标（已删除的前置任务显示占位）
        var statusInfo = statusMap[pc.preTaskStatus];
        var statusBadge;
        if (pc.preTaskStatus && statusInfo) {
            statusBadge = '<span class="pc-status-tag" style="background:' + statusInfo.color + '">' + statusInfo.label + '</span>';
        } else {
            statusBadge = '<span class="pc-status-tag pc-status-deleted">已删除</span>';
        }

        // 要求状态标签组（多选）
        var required = (pc.requiredStatus || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
        var requiredHtml = required.map(function (code) {
            var info = statusMap[code];
            return '<span class="pc-required-tag' + (code === pc.preTaskStatus ? ' pc-required-hit' : '') + '">' +
                (info ? info.label : code) + '</span>';
        }).join('');

        var title = pc.preTaskTitle ? escapeHtml(pc.preTaskTitle) : ('前置任务 #' + pc.preTaskId);

        return '<div class="pc-item' + (satisfied ? ' pc-item-satisfied' : ' pc-item-unsatisfied') + '">' +
            '<div class="pc-item-head">' +
                '<a class="pc-task-link" href="/tasks-board/' + pc.preTaskId + '" target="_blank" title="查看前置任务详情">' +
                    '#' + pc.preTaskId + ' ' + title +
                '</a>' +
                statusBadge +
                '<span class="pc-satisfied-badge' + (satisfied ? ' pc-satisfied-yes' : ' pc-satisfied-no') + '">' +
                    (satisfied ? '✓ 已满足' : '✗ 未满足') +
                '</span>' +
            '</div>' +
            '<div class="pc-item-required">' +
                '<span class="pc-required-label">要求状态：</span>' +
                (requiredHtml || '<span class="pc-required-none">未设置</span>') +
            '</div>' +
        '</div>';
    }).join('');

    listEl.innerHTML = html;
    summaryEl.textContent = satisfiedCount + '/' + preconditions.length + ' 已满足';
    summaryEl.className = 'precondition-summary' + (satisfiedCount === preconditions.length ? ' all-satisfied' : '');
    sectionEl.style.display = 'block';
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
    } else if (status === 'failed') {
        html += '<button class="task-action-btn btn-primary" onclick="redoTask(' + task.id + ')">重做</button>';
    } else if (status === 'completed') {
        html += '<button class="task-action-btn btn-primary" onclick="redoTask(' + task.id + ')">重做</button>';
    }

    // 除处理中/已关闭外，任何状态均可关闭
    if (status !== 'processing' && status !== 'closed') {
        html += '<button class="task-action-btn btn-secondary" onclick="closeTask(' + task.id + ')">关闭任务</button>';
    }

    actionsEl.innerHTML = html;

    // 头部编辑按钮：处理中不可编辑（与看板规则一致），已关闭可编辑
    var editBtn = document.getElementById('btnEditTask');
    if (editBtn) {
        editBtn.style.display = (status === 'processing') ? 'none' : 'flex';
    }
}

/* ========== 头部编辑/删除 ========== */

/** 编辑当前任务：复用公共任务弹框回显 */
function editCurrentTask() {
    if (currentTaskId == null) return;
    showEditTaskModal(currentTaskId);
}

/** 删除当前任务：确认后调用删除接口，成功后关闭详情窗口 */
function deleteCurrentTask() {
    if (currentTaskId == null) return;
    showConfirm('确定要删除该任务吗？删除后不可恢复。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/tasks/' + currentTaskId, { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('删除成功', 'success');
                    // 详情窗口无返回目标，延迟关闭给用户留出提示时间
                    setTimeout(function () { window.close(); }, 600);
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
    // 记录会话ID集合，供 WebSocket 消息匹配使用
    taskSessionIds = (list || []).map(function (session) {
        return session.sessionId || '';
    }).filter(Boolean);

    var container = document.getElementById('taskDetailSessions');
    if (!list || !list.length) {
        container.innerHTML = '<div class="task-detail-empty">暂无会话记录</div>';
        return;
    }
    container.innerHTML = list.map(function (session) {
        var sid = session.sessionId || '';
        var time = formatTime(session.createTime) || '';
        // 标题优先；无标题时回退到 sessionId
        var displayTitle = session.title ? session.title : (sid ? sid : '会话记录');
        return '<div class="task-session-row">' +
            '<span class="task-session-info" title="' + escapeHtml(sid) + '">' +
                escapeHtml(displayTitle) + (time ? ' · ' + escapeHtml(time) : '') +
            '</span>' +
            '<a class="task-session-link" href="/?sessionId=' + encodeURIComponent(sid) + '" target="_blank">查看记录</a>' +
            '<button class="task-session-link task-session-clear" title="清空该会话的历史记录" onclick="clearTaskSessionHistory(\'' + escapeHtml(sid) + '\')">清空历史</button>' +
        '</div>';
    }).join('');
}

/** 清空指定任务会话的历史记录（复用会话清理接口） */
function clearTaskSessionHistory(sessionId) {
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

/* ========== 会话执行状态监听（WebSocket） ========== */
/* 打字机缓冲区：持续追加内容，超过固定长度时丢弃最左侧旧内容 */
var tickerBuffer = '';
var TICKER_MAX_LEN = 150;
/* 当前正在输出参数的工具调用ID：同一工具的分片不重复拼接标签 */
var currentToolCallId = null;
/* 已收到过流式分片的工具调用集合：全量数据仅在无分片时补充展示 */
var streamSeen = {};

/** 订阅会话 WebSocket：消息的 sessionId 属于本任务时展示执行中指示，结束时刷新任务页 */
function connectTaskWebSocket() {
    var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    try {
        taskWs = new WebSocket(protocol + '//' + window.location.host + '/ws/chat');
    } catch (e) {
        console.error('创建 WebSocket 失败:', e);
        return;
    }
    taskWs.onmessage = function (event) {
        var data;
        try { data = JSON.parse(event.data); } catch (e) { return; }
        if (!data.sessionId || taskSessionIds.indexOf(data.sessionId) === -1) return;

        if (data.type === 'received') {
            showSessionRunning();
            // 新一轮执行：清空打字机缓冲区，从零开始追加
            tickerBuffer = '';
            currentToolCallId = null;
            streamSeen = {};
            var tEl = document.getElementById('taskSessionTicker');
            if (tEl) tEl.textContent = '';
        } else if (data.type === 'chunk') {
            showSessionRunning();
            appendTicker(data.content);
        } else if (data.type === 'tool_call') {
            showSessionRunning();
            handleToolCallTicker(data);
        } else if (data.type === 'thinking') {
            showSessionRunning();
            appendTicker(data.content);
        } else if (data.type === 'token_usage') {
            // 本任务会话产生了 token 消耗：刷新柱状统计图
            loadTaskTokenUsage(currentTaskId);
        } else if (data.type === 'session-title') {
            // 用户意图分析生成了会话标题：刷新会话列表展示新标题
            loadTaskSessions(currentTaskId);
        } else if (data.type === 'task-done' || data.type === 'done' || data.type === 'error') {
            if (taskWsRunning) {
                hideSessionRunning();
                showToast('会话执行结束，已刷新任务信息', 'success');
                loadTaskDetail(currentTaskId);
            }
        }
    };
    taskWs.onclose = function () {
        // 断线重连（页面可能长时间停留在详情页）
        setTimeout(connectTaskWebSocket, 5000);
    };
}

/** 工具调用打字机展示：标签只在新工具时拼接一次，参数/结果分片持续追加 */
function handleToolCallTicker(data) {
    var isNewTool = data.toolCallId && data.toolCallId !== currentToolCallId;
    if (data.status === 'preparing') {
        // 参数流式分片：新工具先拼标签，同一工具的后续分片只追加参数内容
        if (isNewTool) {
            currentToolCallId = data.toolCallId;
            appendTicker(' [调用工具 ' + (data.toolName || '') + '] ');
        }
        if (data.argumentsPartial != null) {
            streamSeen['a:' + data.toolCallId] = true;
            appendTicker(String(data.argumentsPartial));
        }
    } else if (data.status === 'started') {
        if (isNewTool) {
            currentToolCallId = data.toolCallId;
            appendTicker(' [调用工具 ' + (data.toolName || '') + '] ');
        }
        // 全量参数仅在未收到过分片时补充展示（分片已展示则不重复）
        if (data.arguments != null && !streamSeen['a:' + data.toolCallId]) {
            appendTicker(formatTickerJson(data.arguments));
        }
    } else if (data.status === 'running') {
        // 执行结果流式分片
        if (data.resultPartial != null) {
            streamSeen['r:' + data.toolCallId] = true;
            appendTicker(String(data.resultPartial));
        }
    } else if (data.status === 'executed') {
        currentToolCallId = null;
        if (data.result != null && !streamSeen['r:' + data.toolCallId]) {
            appendTicker(' [工具返回] ' + formatTickerJson(data.result));
        }
    } else if (data.status === 'failed' || data.status === 'rejected') {
        currentToolCallId = null;
        appendTicker(' [工具' + (data.status === 'failed' ? '执行失败' : '被拒绝') + '] ');
    }
}

/** 参数/结果对象转紧凑字符串（超长截断） */
function formatTickerJson(obj) {
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
function showSessionRunning() {
    var el = document.getElementById('taskSessionRunning');
    if (!el) return;
    el.style.display = 'flex';
    taskWsRunning = true;
}

/** 隐藏执行中指示器 */
function hideSessionRunning() {
    var el = document.getElementById('taskSessionRunning');
    if (el) el.style.display = 'none';
    taskWsRunning = false;
}

/** 打字机追加：内容持续拼接到缓冲区右侧，超长丢弃最左侧旧内容 */
function appendTicker(content) {
    if (!content) return;
    var text = String(content).replace(/\s+/g, ' ').trim();
    if (!text) return;
    tickerBuffer += text;
    if (tickerBuffer.length > TICKER_MAX_LEN) {
        tickerBuffer = tickerBuffer.slice(tickerBuffer.length - TICKER_MAX_LEN);
    }
    var el = document.getElementById('taskSessionTicker');
    if (el) el.textContent = tickerBuffer;
}

/* ========== 评论 ========== */
// 当前评论类型：default=普通 / summary=总结
var currentCommentType = 'default';

function switchCommentType(type, event) {
    if (event) event.preventDefault();
    currentCommentType = type;
    var btns = document.querySelectorAll('#commentTypeToggle .comment-type-btn');
    btns.forEach(function (btn) {
        btn.classList.toggle('active', btn.getAttribute('data-type') === type);
    });
    var input = document.getElementById('taskCommentInput');
    if (input) {
        input.placeholder = type === 'summary' ? '输入总结评论（将标记为任务关键节点）...' : '输入评论...';
    }
}

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
        // 评论者名称：优先用联查出的名称（智能体名/用户昵称），缺失时回退到ID
        var fallbackName = isAgent ? (c.commenterId ? '智能体#' + c.commenterId : '智能体') : (c.userId || '匿名');
        var who = c.commenterName || fallbackName;
        var isSummary = c.commentType === 'summary';
        return '<div class="comment-item ' + roleClass + (isSummary ? ' comment-item-summary' : '') + '">' +
            '<div class="comment-item-header">' +
                '<span class="comment-item-user">' + escapeHtml(roleLabel + ' · ' + who) + '</span>' +
                '<span class="comment-item-time">' + escapeHtml(formatTime(c.createTime)) + '</span>' +
                (isSummary ? '<span class="comment-summary-badge">总结</span>' : '') +
                '<span class="comment-item-actions">' +
                    '<button type="button" class="comment-action-btn comment-btn-type" title="' + (isSummary ? '转为普通评论' : '转为总结评论（将记录为任务关键节点）') + '" onclick="toggleCommentType(' + c.id + ', \'' + (isSummary ? 'default' : 'summary') + '\')">' + (isSummary ? '☆' : '★') + '</button>' +
                    '<button type="button" class="comment-action-btn comment-btn-delete" title="删除评论" onclick="deleteComment(' + c.id + ')">✕</button>' +
                '</span>' +
            '</div>' +
            '<div class="comment-item-content">' + escapeHtml(c.content || '') + '</div>' +
        '</div>';
    }).join('');
}

/* 删除任务评论 */
function deleteComment(id) {
    if (!id) return;
    showConfirm('确定删除这条评论吗？删除后不可恢复。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/comments/' + id, { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code !== 200) {
                    showToast(res.msg || '删除失败', 'error');
                    return;
                }
                showToast('删除成功', 'success');
                loadComments(currentTaskId);
            })
            .catch(function (err) {
                console.error('删除评论失败:', err);
                showToast('删除失败', 'error');
            });
    });
}

/* 更新评论类型：targetType 为 default / summary */
function toggleCommentType(id, targetType) {
    if (!id || !targetType) return;
    fetch('/api/workflow/comments/' + id + '/type', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ commentType: targetType })
    })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code !== 200) {
                showToast(res.msg || '更新失败', 'error');
                return;
            }
            showToast(targetType === 'summary' ? '已转为总结评论' : '已转为普通评论', 'success');
            loadComments(currentTaskId);
        })
        .catch(function (err) {
            console.error('更新评论类型失败:', err);
            showToast('更新失败', 'error');
        });
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
        body: JSON.stringify({ content: content, commentType: currentCommentType })
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
    showPrompt('请输入打回重做的原因：', '请输入原因，Ctrl+Enter 提交').then(function (reason) {
        if (reason === null) return;
        if (!reason) {
            showToast('请输入打回原因', 'error');
            return;
        }
        fetch('/api/workflow/tasks/' + id + '/reject', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reason: reason })
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

function redoTask(id) {
    showConfirm('确定要重做该任务吗？任务将重置为待执行状态并自动重新运行。').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/workflow/tasks/' + id + '/redo', { method: 'PUT' })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res.code === 200) {
                    showToast('任务已重置为待执行', 'success');
                    loadTaskDetail(id);
                } else {
                    showToast(res.msg || '操作失败', 'error');
                }
            })
            .catch(function (err) {
                console.error('重做失败:', err);
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

/**
 * 任务详情页创建者展示：智能体创建显示智能体名称（机器人图标），
 * 用户创建显示用户昵称（人形图标），不同图标突出区分创建者类型。
 */
function taskDetailCreatorHtml(task) {
    var isAgent = task.creatorType === 'agent';
    var name;
    if (isAgent) {
        name = task.creatorAgentName || (task.creatorAgentId ? '智能体#' + task.creatorAgentId : '智能体');
    } else {
        name = task.creatorName || '用户';
    }
    var icon = isAgent
        ? '<svg class="creator-icon" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="8" width="16" height="12" rx="2"/><circle cx="9" cy="13" r="1.2" fill="currentColor"/><circle cx="15" cy="13" r="1.2" fill="currentColor"/><path d="M12 8V5"/><circle cx="12" cy="3.5" r="1.2"/></svg>'
        : '<svg class="creator-icon" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>';
    return '<span class="task-creator-tag ' + (isAgent ? 'creator-agent' : 'creator-user') + '">' + icon + escapeHtml(name) + '</span>';
}

/* ========== 任务 Token 用量统计（参考会话右下角统计样式） ========== */
var taskTokenChart = null;

function loadTaskTokenUsage(taskId) {
    fetch('/api/workflow/tasks/' + taskId + '/token-usage?limit=30')
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.code === 200 && res.data) {
                renderTaskTokenUsage(res.data);
            }
        })
        .catch(function (err) {
            console.error('加载任务 Token 用量失败:', err);
        });
}

function renderTaskTokenUsage(data) {
    var summaryEl = document.getElementById('taskTokenSummary');
    var chartEl = document.getElementById('taskTokenChart');
    if (!summaryEl || !chartEl) return;

    var summary = data.summary || {};
    var list = data.list || [];

    if (!list.length) {
        summaryEl.innerHTML = '';
        if (taskTokenChart) {
            taskTokenChart.destroy();
            taskTokenChart = null;
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
    renderTaskTokenChart(chartEl, ordered);
}

function renderTaskTokenChart(container, data) {
    container.innerHTML = '<canvas id="taskTokenChartCanvas"></canvas>';
    var canvas = document.getElementById('taskTokenChartCanvas');
    var isDark = document.body.classList.contains('dark-theme');

    var labels = data.map(function (d) {
        return d.createTime ? String(d.createTime).replace('T', ' ').substring(5, 16) : '';
    });
    var inputData = data.map(function (d) { return d.inputTokens || 0; });
    var outputData = data.map(function (d) { return d.outputTokens || 0; });

    if (taskTokenChart) {
        taskTokenChart.destroy();
        taskTokenChart = null;
    }
    taskTokenChart = new Chart(canvas, {
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
}

/** token 数量格式化（1.2w 形式） */
function formatTokenCount(n) {
    n = Number(n) || 0;
    if (n >= 10000) return (n / 10000).toFixed(1) + 'w';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
    return String(n);
}
