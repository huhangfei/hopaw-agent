/**
 * 设置 - 会话清理 tab：分页显示会话列表，支持单行/批量清理历史与删除
 */
var scPage = 1;
var scPageSize = 20;
var scTotal = 0;
var scPageSizeSel = null;

function scFormatTime(t) {
    if (!t) return '-';
    return String(t).replace('T', ' ').substring(0, 19);
}

/**
 * 会话类型标签：按 AgentExecutorBizTypeEnum 的 value 判断
 * chat→聊天 / workflowTaskChat→工作流任务 / projectChat→项目管理
 * 兼容历史数据（旧版写入的 workflow-task-chat / project-chat）
 */
function scBizTypeLabel(bizType) {
    if (bizType === 'workflowTaskChat' || bizType === 'workflow-task-chat') return '工作流任务';
    if (bizType === 'projectChat' || bizType === 'project-chat') return '项目管理';
    return '聊天';
}

function scLoad() {
    fetch('/api/session/stats-page?page=' + scPage + '&pageSize=' + scPageSize)
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code !== 200 || !res.data) {
                showToast(res.message || '加载会话列表失败', 'error');
                return;
            }
            scTotal = res.data.total || 0;
            var list = res.data.list || [];
            scRender(list);
            scRenderPagination();
            scUpdateSelectedTip();
        })
        .catch(function() { showToast('加载会话列表失败', 'error'); });
}

function scRender(list) {
    var tbody = document.getElementById('scTableBody');
    var empty = document.getElementById('scEmptyState');
    var checkAll = document.getElementById('scCheckAll');
    if (checkAll) checkAll.checked = false;
    if (!list.length) {
        tbody.innerHTML = '';
        empty.style.display = '';
        return;
    }
    empty.style.display = 'none';
    var html = '';
    for (var i = 0; i < list.length; i++) {
        var s = list[i];
        html +=
            '<tr data-session-id="' + s.sessionId + '">' +
                '<td class="sc-col-check"><input type="checkbox" class="sc-row-check" onchange="scUpdateSelectedTip()"></td>' +
                '<td class="sc-col-title" title="' + scEscape(s.title || '') + '">' + scEscape(s.title || '(未命名)') + '</td>' +
                '<td>' + scBizTypeLabel(s.bizType) + '</td>' +
                '<td>' + scFormatTime(s.createTime) + '</td>' +
                '<td>' + scFormatTime(s.lastUpdateTime) + '</td>' +
                '<td>' + (s.messageCount || 0) + '</td>' +
                '<td class="sc-col-actions">' +
                    '<button class="sc-action-btn" onclick="scClearRow(this)">清理历史</button>' +
                    '<button class="sc-action-btn sc-action-danger" onclick="scDeleteRow(this)">删除</button>' +
                '</td>' +
            '</tr>';
    }
    tbody.innerHTML = html;
}

function scEscape(text) {
    return String(text)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function scRenderPagination() {
    var pages = Math.max(1, Math.ceil(scTotal / scPageSize));
    if (scPage > pages) scPage = pages;
    document.getElementById('scPageInfo').textContent = scPage + ' / ' + pages;
    document.getElementById('scTotalInfo').textContent = '共 ' + scTotal + ' 条';
    document.getElementById('scPrevBtn').disabled = scPage <= 1;
    document.getElementById('scNextBtn').disabled = scPage >= pages;
}

function scPrevPage() {
    if (scPage > 1) { scPage--; scLoad(); }
}

function scNextPage() {
    if (scPage < Math.max(1, Math.ceil(scTotal / scPageSize))) { scPage++; scLoad(); }
}

/** 全选当页 */
function scToggleCheckAll(master) {
    var checks = document.querySelectorAll('#scTableBody .sc-row-check');
    checks.forEach(function(c) { c.checked = master.checked; });
    scUpdateSelectedTip();
}

function scUpdateSelectedTip() {
    var count = document.querySelectorAll('#scTableBody .sc-row-check:checked').length;
    document.getElementById('scSelectedTip').textContent = '已选 ' + count + ' 项';
}

function scSelectedSessionIds() {
    var ids = [];
    document.querySelectorAll('#scTableBody tr').forEach(function(tr) {
        var check = tr.querySelector('.sc-row-check');
        if (check && check.checked) ids.push(tr.getAttribute('data-session-id'));
    });
    return ids;
}

/** 批量清理历史：调用现有清理逻辑（聊天记录 + 记忆） */
function scBatchClear() {
    var ids = scSelectedSessionIds();
    if (!ids.length) { showToast('请先选择要清理的会话', 'warning'); return; }
    showConfirm('确定清理选中 ' + ids.length + ' 个会话的历史记录吗？清理后聊天记录与记忆将被删除，会话本身保留。').then(function(ok) {
        if (!ok) return;
        scPostBatch('/api/session/batch-clear', ids, '清理历史');
    });
}

/** 批量删除：先清理历史，再删除会话 */
function scBatchDelete() {
    var ids = scSelectedSessionIds();
    if (!ids.length) { showToast('请先选择要删除的会话', 'warning'); return; }
    showConfirm('确定删除选中 ' + ids.length + ' 个会话吗？将先清理历史记录与记忆，再删除会话本身，操作不可恢复。').then(function(ok) {
        if (!ok) return;
        scPostBatch('/api/session/batch-delete', ids, '删除');
    });
}

function scPostBatch(url, ids, actionLabel) {
    fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionIds: ids })
    }).then(function(r) { return r.json(); }).then(function(res) {
        if (res.code === 200 && res.data) {
            var success = res.data.success || 0;
            var failed = (res.data.failed || []).length;
            if (failed > 0) {
                showToast(actionLabel + '完成：成功 ' + success + ' 个，失败 ' + failed + ' 个', 'warning');
            } else {
                showToast(actionLabel + '完成：共 ' + success + ' 个', 'success');
            }
            scLoad();
        } else {
            showToast(res.message || (actionLabel + '失败'), 'error');
        }
    }).catch(function() { showToast(actionLabel + '失败', 'error'); });
}

/** 单行清理历史 */
function scClearRow(btn) {
    var tr = btn.closest('tr');
    var sessionId = tr.getAttribute('data-session-id');
    var title = (tr.querySelector('.sc-col-title') || {}).textContent || '该会话';
    showConfirm('确定清理「' + title + '」的历史记录吗？清理后聊天记录与记忆将被删除，会话本身保留。').then(function(ok) {
        if (!ok) return;
        scPostBatch('/api/session/batch-clear', [sessionId], '清理历史');
    });
}

/** 单行删除：先清理历史再删除会话 */
function scDeleteRow(btn) {
    var tr = btn.closest('tr');
    var sessionId = tr.getAttribute('data-session-id');
    var title = (tr.querySelector('.sc-col-title') || {}).textContent || '该会话';
    showConfirm('确定删除「' + title + '」吗？将先清理历史记录与记忆，再删除会话本身，操作不可恢复。').then(function(ok) {
        if (!ok) return;
        scPostBatch('/api/session/batch-delete', [sessionId], '删除');
    });
}

// 页面加载后初始化（settings tab 为独立页面路由，脚本仅在对应 tab 加载）
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', scLoad);
} else {
    scLoad();
}
