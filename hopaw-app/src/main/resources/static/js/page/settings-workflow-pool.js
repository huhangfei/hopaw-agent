var SETTINGS_KEYS = [
    'workflow_pool_core_size', 'workflow_pool_max_size', 'workflow_pool_queue_capacity',
    'project_pool_core_size', 'project_pool_max_size', 'project_pool_queue_capacity'
];

// 工作流任务线程池设置：保存配置并重建线程池，轮询展示运行状态
var poolStatsTimer = null;

function onSettingsLoaded() {
    document.getElementById('poolCoreSize').value = settingsCache['workflow_pool_core_size'] || '';
    document.getElementById('poolMaxSize').value = settingsCache['workflow_pool_max_size'] || '';
    document.getElementById('poolQueueCapacity').value = settingsCache['workflow_pool_queue_capacity'] || '';
    document.getElementById('projPoolCoreSize').value = settingsCache['project_pool_core_size'] || '';
    document.getElementById('projPoolMaxSize').value = settingsCache['project_pool_max_size'] || '';
    document.getElementById('projPoolQueueCapacity').value = settingsCache['project_pool_queue_capacity'] || '';
    loadPoolStats();
    loadProjectPoolStats();
    // 每 5 秒刷新一次运行状态
    if (poolStatsTimer) {
        clearInterval(poolStatsTimer);
    }
    poolStatsTimer = setInterval(function () {
        loadPoolStats();
        loadProjectPoolStats();
    }, 5000);
}

function saveWorkflowPoolSettings() {
    var core = parseInt(document.getElementById('poolCoreSize').value, 10);
    var max = parseInt(document.getElementById('poolMaxSize').value, 10);
    var queue = parseInt(document.getElementById('poolQueueCapacity').value, 10);

    // 空值回退默认值（与后端默认一致）
    if (isNaN(core)) core = 2;
    if (isNaN(max)) max = 4;
    if (isNaN(queue)) queue = 20;

    if (core < 1 || core > 32) { showToast('核心线程数应在 1~32 之间', 'error'); return; }
    if (max < core || max > 64) { showToast('最大线程数应不小于核心线程数且不超过 64', 'error'); return; }
    if (queue < 0 || queue > 1000) { showToast('排队任务数上限应在 0~1000 之间', 'error'); return; }

    var saves = [];
    saves.push(saveConfig('workflow_pool_core_size', String(core), '工作流线程池核心线程数'));
    saves.push(saveConfig('workflow_pool_max_size', String(max), '工作流线程池最大线程数'));
    saves.push(saveConfig('workflow_pool_queue_capacity', String(queue), '工作流线程池排队任务数上限'));

    Promise.all(saves).then(function(results) {
        if (!results.every(function(r) { return r; })) {
            showToast('部分配置保存失败', 'error');
            return;
        }
        // 配置保存成功后重建线程池使其生效
        fetch('/api/settings/workflow-pool/reload', { method: 'POST' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.msg === 'success') {
                    showToast('线程池配置已保存并生效', 'success');
                    loadPoolStats();
                } else {
                    showToast('配置已保存，但线程池重建失败: ' + (resp.data || resp.msg), 'error');
                }
            })
            .catch(function() {
                showToast('配置已保存，但线程池重建请求失败', 'error');
            });
    });
}

function loadPoolStats() {
    fetch('/api/settings/workflow-pool/status')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success' || !resp.data) return;
            var s = resp.data;
            document.getElementById('statActiveCount').textContent = s.activeCount != null ? s.activeCount : '-';
            document.getElementById('statPoolSize').textContent = s.poolSize != null ? s.poolSize : '-';
            document.getElementById('statQueuedTasks').textContent = s.queuedTasks != null ? s.queuedTasks : '-';
            document.getElementById('statCompletedTasks').textContent = s.completedTasks != null ? s.completedTasks : '-';
            document.getElementById('statPoolConfig').textContent =
                '当前生效配置：核心 ' + (s.corePoolSize != null ? s.corePoolSize : '-') +
                ' / 最大 ' + (s.maxPoolSize != null ? s.maxPoolSize : '-') +
                ' / 排队上限 ' + (s.queueCapacity != null ? s.queueCapacity : '-');
        })
        .catch(function() { /* 静默失败，下轮重试 */ });
}

// 项目线程池设置：保存配置并重建线程池
function saveProjectPoolSettings() {
    var core = parseInt(document.getElementById('projPoolCoreSize').value, 10);
    var max = parseInt(document.getElementById('projPoolMaxSize').value, 10);
    var queue = parseInt(document.getElementById('projPoolQueueCapacity').value, 10);

    // 空值回退默认值（与后端默认一致）
    if (isNaN(core)) core = 2;
    if (isNaN(max)) max = 4;
    if (isNaN(queue)) queue = 20;

    if (core < 1 || core > 32) { showToast('核心线程数应在 1~32 之间', 'error'); return; }
    if (max < core || max > 64) { showToast('最大线程数应不小于核心线程数且不超过 64', 'error'); return; }
    if (queue < 0 || queue > 1000) { showToast('排队项目数上限应在 0~1000 之间', 'error'); return; }

    var saves = [];
    saves.push(saveConfig('project_pool_core_size', String(core), '项目线程池核心线程数'));
    saves.push(saveConfig('project_pool_max_size', String(max), '项目线程池最大线程数'));
    saves.push(saveConfig('project_pool_queue_capacity', String(queue), '项目线程池排队项目数上限'));

    Promise.all(saves).then(function(results) {
        if (!results.every(function(r) { return r; })) {
            showToast('部分配置保存失败', 'error');
            return;
        }
        // 配置保存成功后重建线程池使其生效
        fetch('/api/settings/project-pool/reload', { method: 'POST' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.msg === 'success') {
                    showToast('项目线程池配置已保存并生效', 'success');
                    loadProjectPoolStats();
                } else {
                    showToast('配置已保存，但项目线程池重建失败: ' + (resp.data || resp.msg), 'error');
                }
            })
            .catch(function() {
                showToast('配置已保存，但项目线程池重建请求失败', 'error');
            });
    });
}

function loadProjectPoolStats() {
    fetch('/api/settings/project-pool/status')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success' || !resp.data) return;
            var s = resp.data;
            document.getElementById('projStatActiveCount').textContent = s.activeCount != null ? s.activeCount : '-';
            document.getElementById('projStatPoolSize').textContent = s.poolSize != null ? s.poolSize : '-';
            document.getElementById('projStatQueuedProjects').textContent = s.queuedProjects != null ? s.queuedProjects : '-';
            document.getElementById('projStatCompletedProjects').textContent = s.completedProjects != null ? s.completedProjects : '-';
            document.getElementById('projStatPoolConfig').textContent =
                '当前生效配置：核心 ' + (s.corePoolSize != null ? s.corePoolSize : '-') +
                ' / 最大 ' + (s.maxPoolSize != null ? s.maxPoolSize : '-') +
                ' / 排队上限 ' + (s.queueCapacity != null ? s.queueCapacity : '-');
        })
        .catch(function() { /* 静默失败，下轮重试 */ });
}
