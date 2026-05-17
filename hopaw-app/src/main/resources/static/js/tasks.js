document.addEventListener('DOMContentLoaded', function() {
    loadTasks();
    setupToggle();
});

function setupToggle() {
    var checkbox = document.getElementById('taskEnabled');
    checkbox.addEventListener('change', function() {
        document.getElementById('taskEnabledLabel').textContent = this.checked ? '开启' : '关闭';
    });
}

function loadTasks() {
    fetch('/api/tasks')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            var tasks = resp.data || [];
            var tbody = document.getElementById('tasksTableBody');
            if (tasks.length === 0) {
                tbody.innerHTML = '';
                document.getElementById('emptyState').style.display = 'block';
                return;
            }
            document.getElementById('emptyState').style.display = 'none';
            tbody.innerHTML = tasks.map(function(t) {
                var statusHtml = t.enabled === 1
                    ? '<span class="task-status-badge enabled">运行中</span>'
                    : '<span class="task-status-badge disabled">已关闭</span>';
                var typeName = t.taskType;
                var builtinBadge = t.builtin === 1 ? '<span class="builtin-badge">内置</span>' : '';
                var identity = t.userId || '系统';
                return '<tr>' +
                    '<td><strong>' + escapeHtml(t.taskName) + '</strong> ' + builtinBadge + '</td>' +
                    '<td><span class="task-type-tag">' + typeName + '</span></td>' +
                    '<td><code>' + escapeHtml(t.cronExpression) + '</code></td>' +
                    '<td>' + statusHtml + '</td>' +
                    '<td>' + escapeHtml(identity) + '</td>' +
                    '<td class="task-actions">' +
                    '  <button class="btn-toggle' + (t.enabled === 1 ? ' enabled' : '') + '" onclick="toggleTask(' + t.id + ',' + t.enabled + ')">' + (t.enabled === 1 ? '禁用' : '启用') + '</button>' +
                    '  <button class="btn-icon btn-edit-provider" onclick="showEditModal(' + t.id + ')" title="编辑">' +
                    '    <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>' +
                    '  </button>' +
                    (t.builtin === 1 ? '' : '  <button class="btn-icon btn-delete-provider" onclick="deleteTask(' + t.id + ')" title="删除">' +
                    '    <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>' +
                    '  </button>') +
                    '</td>' +
                    '</tr>';
            }).join('');
        })
        .catch(function(e) {
            console.error('加载任务列表失败:', e);
            showToast('加载任务列表失败', 'error');
        });
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function showAddModal() {
    document.getElementById('modalTitle').textContent = '添加任务';
    document.getElementById('taskId').value = '';
    document.getElementById('taskName').value = '';
    document.getElementById('taskType').value = '';
    document.getElementById('taskType').style.display = '';
    document.getElementById('taskCron').value = '';
    document.getElementById('taskIdentity').value = '';
    document.getElementById('taskIdentity').disabled = false;
    document.getElementById('taskBuiltin').value = '0';
    document.getElementById('taskEnabled').checked = true;
    document.getElementById('taskEnabledLabel').textContent = '开启';
    document.getElementById('taskDescription').value = '';
    Modal.open('taskModal');
}

function showEditModal(id) {
    fetch('/api/tasks/' + id)
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success' || !resp.data) {
                showToast('加载任务信息失败', 'error');
                return;
            }
            var task = resp.data;
            document.getElementById('modalTitle').textContent = '编辑任务';
            document.getElementById('taskId').value = task.id;
            document.getElementById('taskName').value = task.taskName || '';
            document.getElementById('taskType').value = task.taskType || '';
            document.getElementById('taskType').style.display = 'none';
            document.getElementById('taskCron').value = task.cronExpression || '';
            document.getElementById('taskIdentity').value = task.userId || '';
            document.getElementById('taskIdentity').disabled = true;
            document.getElementById('taskBuiltin').value = task.builtin || 0;
            document.getElementById('taskEnabled').checked = task.enabled === 1;
            document.getElementById('taskEnabledLabel').textContent = task.enabled === 1 ? '开启' : '关闭';
            document.getElementById('taskDescription').value = task.description || '';
            Modal.open('taskModal');
        })
        .catch(function(e) {
            console.error('加载任务失败:', e);
            showToast('加载任务失败', 'error');
        });
}

function closeModal() {
    Modal.close('taskModal');
}

function submitTask() {
    var id = document.getElementById('taskId').value;
    var name = document.getElementById('taskName').value.trim();
    var type = document.getElementById('taskType').value;
    var cron = document.getElementById('taskCron').value.trim();
    var enabled = document.getElementById('taskEnabled').checked ? 1 : 0;
    var desc = document.getElementById('taskDescription').value.trim();

    if (!name) { showToast('请输入任务名称', 'error'); return; }
    if (!type) { showToast('请选择任务类型', 'error'); return; }
    if (!cron) { showToast('请输入Cron表达式', 'error'); return; }

    var agentId = document.getElementById('taskIdentity').value.trim();
    var builtin = parseInt(document.getElementById('taskBuiltin').value) || 0;

    var body = JSON.stringify({
        taskName: name,
        taskType: type,
        cronExpression: cron,
        enabled: enabled,
        description: desc,
        agentId: agentId || null,
        builtin: builtin
    });

    var url = id ? '/api/tasks/' + id : '/api/tasks';
    var method = id ? 'PUT' : 'POST';

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: body
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.msg === 'success') {
            showToast(id ? '任务更新成功' : '任务创建成功', 'success');
            closeModal();
            loadTasks();
        } else {
            showToast(resp.msg || '操作失败', 'error');
        }
    })
    .catch(function() {
        showToast('操作失败', 'error');
    });
}

function deleteTask(id) {
    showConfirm('确定要删除此定时任务吗？').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/tasks/' + id, { method: 'DELETE' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.msg === 'success') {
                    showToast('删除成功', 'success');
                    loadTasks();
                } else {
                    showToast(resp.msg || '删除失败', 'error');
                }
            })
            .catch(function() {
                showToast('删除失败', 'error');
            });
    });
}

function toggleTask(id, currentEnabled) {
    var newEnabled = currentEnabled === 1 ? 0 : 1;
    var action = newEnabled === 1 ? '启用' : '禁用';
    showConfirm('确定要' + action + '此定时任务吗？').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/tasks/' + id)
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.msg !== 'success' || !resp.data) {
                    showToast('加载任务失败', 'error');
                    return;
                }
                var task = resp.data;
                task.enabled = newEnabled;
                return fetch('/api/tasks/' + id, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(task)
                });
            })
            .then(function(r) { return r && r.json(); })
            .then(function(resp) {
                if (resp && resp.msg === 'success') {
                    showToast(action + '成功', 'success');
                    loadTasks();
                } else {
                    showToast(action + '失败', 'error');
                }
            })
            .catch(function() {
                showToast(action + '失败', 'error');
            });
    });
}
