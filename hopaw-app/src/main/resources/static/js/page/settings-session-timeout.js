// 设置 - 会话超时 tab：配置聊天/项目/工作流任务三类会话的执行器看门狗超时时间
var SETTINGS_KEYS = [
    'chat_execute_timeout_seconds',
    'project_execute_timeout_seconds',
    'workflow_task_execute_timeout_seconds'
];

function onSettingsLoaded() {
    document.getElementById('chatTimeoutSeconds').value = settingsCache['chat_execute_timeout_seconds'] || '';
    document.getElementById('projectTimeoutSeconds').value = settingsCache['project_execute_timeout_seconds'] || '';
    document.getElementById('workflowTaskTimeoutSeconds').value = settingsCache['workflow_task_execute_timeout_seconds'] || '';
}

function saveSessionTimeoutSettings() {
    var chat = parseInt(document.getElementById('chatTimeoutSeconds').value, 10);
    var project = parseInt(document.getElementById('projectTimeoutSeconds').value, 10);
    var workflowTask = parseInt(document.getElementById('workflowTaskTimeoutSeconds').value, 10);

    // 空值回退默认值（与后端默认一致）
    if (isNaN(chat)) chat = 360;
    if (isNaN(project)) project = 360;
    if (isNaN(workflowTask)) workflowTask = 360;

    if (chat < 30 || chat > 86400) { showToast('普通聊天会话超时应在 30~86400 秒之间', 'error'); return; }
    if (project < 30 || project > 86400) { showToast('项目会话超时应在 30~86400 秒之间', 'error'); return; }
    if (workflowTask < 30 || workflowTask > 86400) { showToast('工作流任务会话超时应在 30~86400 秒之间', 'error'); return; }

    var saves = [];
    saves.push(saveConfig('chat_execute_timeout_seconds', String(chat), '普通聊天会话执行超时（秒）'));
    saves.push(saveConfig('project_execute_timeout_seconds', String(project), '项目会话执行超时（秒）'));
    saves.push(saveConfig('workflow_task_execute_timeout_seconds', String(workflowTask), '工作流任务会话执行超时（秒）'));

    Promise.all(saves).then(function(results) {
        if (results.every(function(r) { return r; })) {
            showToast('会话超时设置已保存，下次执行生效', 'success');
        } else {
            showToast('部分配置保存失败', 'error');
        }
    });
}
