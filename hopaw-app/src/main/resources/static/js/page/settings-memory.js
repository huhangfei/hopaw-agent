function onSettingsLoaded() {
    document.getElementById('memoryPrompt').value = settingsCache['memory_prompt'] || '';
    document.getElementById('taskRecordsArrangeTimeoutHour').value = settingsCache['taskRecordsArrangeTimeoutHour'] || '48';
    document.getElementById('taskRecordsClearTimeoutDay').value = settingsCache['taskRecordsClearTimeoutDay'] || '7';
    document.getElementById('vectorStorePath').value = settingsCache['vector_store_path'] || '';
    document.getElementById('vectorStoreProfile').value = settingsCache['vector_store_profile'] || 'stable';

    // 先加载提供商列表，串行回填已选模型
    loadProviders().then(function() {
        var savedModelId = settingsCache['memory_ai_model_id'];
        if (savedModelId) {
            selectModelById(savedModelId);
        }
    });

    // 任务状态独立加载
    loadMemoryTaskStatus();
}

function loadProviders() {
    var select = document.getElementById('memoryProviderSelect');
    return fetch('/api/providers')
        .then(function(r) { return r.json(); })
        .then(function(providers) {
            select.innerHTML = '<option value="">选择提供商</option>';
            (providers || []).forEach(function(p) {
                if (!p.apiKey) return;
                var opt = document.createElement('option');
                opt.value = p.id;
                opt.textContent = p.name;
                select.appendChild(opt);
            });
        })
        .catch(function(e) {
            console.error('加载提供商列表失败:', e);
        });
}

function setupCascading() {
    var providerSelect = document.getElementById('memoryProviderSelect');
    var modelSelect = document.getElementById('memoryModelSelect');

    providerSelect.addEventListener('change', function() {
        var providerId = this.value;
        modelSelect.innerHTML = '<option value="">选择模型</option>';
        modelSelect.disabled = !providerId;
        if (!providerId) return;

        fetch('/api/providers/' + providerId + '/models')
            .then(function(r) { return r.json(); })
            .then(function(models) {
                (models || []).forEach(function(m) {
                    var opt = document.createElement('option');
                    opt.value = m.id;
                    opt.textContent = m.modelName;
                    modelSelect.appendChild(opt);
                });
            });
    });
}

function selectModelById(modelId) {
    fetch('/api/models/' + modelId)
        .then(function(r) { return r.json(); })
        .then(function(model) {
            if (!model || !model.providerId) return;
            var providerSelect = document.getElementById('memoryProviderSelect');
            var modelSelect = document.getElementById('memoryModelSelect');

            // 同步设置 provider
            providerSelect.value = model.providerId;

            // 串行加载该 provider 的模型列表，加载完成后再回填
            return fetch('/api/providers/' + model.providerId + '/models')
                .then(function(r) { return r.json(); })
                .then(function(models) {
                    modelSelect.innerHTML = '<option value="">选择模型</option>';
                    modelSelect.disabled = false;
                    (models || []).forEach(function(m) {
                        var opt = document.createElement('option');
                        opt.value = m.id;
                        opt.textContent = m.modelName;
                        modelSelect.appendChild(opt);
                    });
                    modelSelect.value = modelId;
                });
        })
        .catch(function(e) {
            console.error('加载模型信息失败:', e);
        });
}

function saveSettings() {
    var modelId = document.getElementById('memoryModelSelect').value;
    var prompt = document.getElementById('memoryPrompt').value.trim();
    var arrangeTimeoutHour = document.getElementById('taskRecordsArrangeTimeoutHour').value.trim();
    var clearTimeoutDay = document.getElementById('taskRecordsClearTimeoutDay').value.trim();

    var saves = [];
    saves.push(saveConfig('memory_ai_model_id', modelId, '记忆整理使用模型'));
    saves.push(saveConfig('memory_prompt', prompt, '记忆整理提示词'));
    saves.push(saveConfig('taskRecordsArrangeTimeoutHour', arrangeTimeoutHour, '近期任务记忆过期时间（单位：小时，用于整理记忆时限制时限）'));
    saves.push(saveConfig('taskRecordsClearTimeoutDay', clearTimeoutDay, '任务记忆过期归档时间（单位：天，过期后从记忆库中删除，但是向量库中永存）'));

    Promise.all(saves).then(function(results) {
        var allOk = results.every(function(r) { return r; });
        if (allOk) {
            showToast('设置保存成功', 'success');
        } else {
            showToast('部分设置保存失败', 'error');
        }
    });
}

function saveVectorStoreSettings() {
    var vectorStorePath = document.getElementById('vectorStorePath').value.trim();
    var vectorStoreProfile = document.getElementById('vectorStoreProfile').value;

    var saves = [];
    saves.push(saveConfig('vector_store_path', vectorStorePath, '向量持久化路径'));
    saves.push(saveConfig('vector_store_profile', vectorStoreProfile, '向量存储预设'));

    Promise.all(saves).then(function(results) {
        var allOk = results.every(function(r) { return r; });
        if (allOk) {
            showToast('向量存储设置保存成功', 'success');
        } else {
            showToast('部分设置保存失败', 'error');
        }
    });
}

function loadMemoryTaskStatus() {
    fetch('/api/tasks/type/longTermMemory')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success' || !resp.data) {
                setTaskStatusUI('error', '获取失败');
                return;
            }
            var running = resp.data.running;
            var task = resp.data.task;
            setTaskStatusUI(running, running ? '运行中' : '已关闭', task.id, task.enabled);
        })
        .catch(function() {
            setTaskStatusUI('error', '获取失败');
        });
}

function setTaskStatusUI(running, label, taskId, enabled) {
    var badge = document.getElementById('memoryTaskStatus');
    var btn = document.getElementById('memoryTaskToggleBtn');
    badge.className = 'task-status-badge ' + (running ? 'running' : 'stopped');
    badge.textContent = label;
    if (taskId) {
        btn.style.display = '';
        btn.textContent = running ? '禁用' : '启用';
        btn.className = 'btn-toggle-task' + (running ? ' running' : '');
        btn._taskId = taskId;
        btn._newEnabled = running ? 0 : 1;
    } else {
        btn.style.display = 'none';
    }
}

function toggleMemoryTask() {
    var btn = document.getElementById('memoryTaskToggleBtn');
    var taskId = btn._taskId;
    var newEnabled = btn._newEnabled;
    var action = newEnabled === 1 ? '启用' : '禁用';

    showConfirm('确定要' + action + '记忆整理定时任务吗？').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/tasks/' + taskId + '/enabled', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ enabled: newEnabled })
        })
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg === 'success') {
                showToast(action + '成功', 'success');
                loadMemoryTaskStatus();
            } else {
                showToast(action + '失败', 'error');
            }
        })
        .catch(function() {
            showToast(action + '失败', 'error');
        });
    });
}

// 初始化
setupCascading();