var settingsCache = {};

document.addEventListener('DOMContentLoaded', function() {
    loadAllSettings();
    loadProviders();
    setupCascading();
    loadMemoryTaskStatus();
});

function switchTab(name) {
    document.querySelectorAll('.settings-tab').forEach(function(t) {
        t.classList.remove('active');
    });
    document.querySelectorAll('.settings-panel').forEach(function(p) {
        p.classList.remove('active');
    });
    document.querySelector('.settings-tab[data-tab="' + name + '"]').classList.add('active');
    document.getElementById('tab-' + name).classList.add('active');
}

function loadAllSettings() {
    fetch('/api/config')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            (resp.data || []).forEach(function(c) {
                settingsCache[c.configKey] = c.configValue;
            });
            var savedModelId = settingsCache['memory_ai_model_id'];
            if (savedModelId) {
                selectModelById(savedModelId);
            }
            document.getElementById('memoryPrompt').value = settingsCache['memory_prompt'] || '';
            document.getElementById('taskRecordsArrangeTimeoutHour').value = settingsCache['taskRecordsArrangeTimeoutHour'] || '48';
            document.getElementById('taskRecordsClearTimeoutDay').value = settingsCache['taskRecordsClearTimeoutDay'] || '7';
            document.getElementById('vectorStorePath').value = settingsCache['vector_store_path'] || '';
            document.getElementById('vectorStoreProfile').value = settingsCache['vector_store_profile'] || 'stable';
            loadMailSettings();
            loadPluginStoreSettings();
        })
        .catch(function(e) {
            console.error('加载设置失败:', e);
        });
}

function loadProviders() {
    var select = document.getElementById('memoryProviderSelect');
    fetch('/api/providers')
        .then(function(r) { return r.json(); })
        .then(function(providers) {
            select.innerHTML = '<option value="">选择提供商</option>';
            providers.forEach(function(p) {
                if (!p.apiKey) return;
                var opt = document.createElement('option');
                opt.value = p.id;
                opt.textContent = p.name;
                select.appendChild(opt);
            });
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
                models.forEach(function(m) {
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
            providerSelect.value = model.providerId;
            var changeEvent = new Event('change');
            providerSelect.dispatchEvent(changeEvent);
            setTimeout(function() {
                modelSelect.value = modelId;
            }, 100);
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
    saves.push(saveConfig('taskRecordsArrangeTimeoutHour', arrangeTimeoutHour, '近期任务记忆过期时间（小时）'));
    saves.push(saveConfig('taskRecordsClearTimeoutDay', clearTimeoutDay, '任务记忆过期归档时间（天）'));

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

function loadMailSettings() {
    document.getElementById('mailHost').value = settingsCache['mail_host'] || '';
    document.getElementById('mailPort').value = settingsCache['mail_port'] || '';
    document.getElementById('mailUsername').value = settingsCache['mail_username'] || '';
    document.getElementById('mailPassword').value = settingsCache['mail_password'] || '';
    document.getElementById('mailFrom').value = settingsCache['mail_from'] || '';
}

function saveMailSettings() {
    var saves = [];
    saves.push(saveConfig('mail_host', document.getElementById('mailHost').value.trim(), 'SMTP 服务器'));
    saves.push(saveConfig('mail_port', document.getElementById('mailPort').value.trim(), 'SMTP 端口'));
    saves.push(saveConfig('mail_username', document.getElementById('mailUsername').value.trim(), 'SMTP 用户名'));
    saves.push(saveConfig('mail_password', document.getElementById('mailPassword').value.trim(), 'SMTP 密码'));
    saves.push(saveConfig('mail_from', document.getElementById('mailFrom').value.trim(), '发件人地址'));

    Promise.all(saves).then(function(results) {
        var allOk = results.every(function(r) { return r; });
        if (allOk) {
            showToast('邮件配置保存成功', 'success');
        } else {
            showToast('部分配置保存失败', 'error');
        }
    });
}

function testMailConfig() {
    var btn = document.querySelector('.btn-test-mail');
    btn.disabled = true;
    btn.textContent = '测试中...';

    fetch('/api/mail/test', { method: 'POST' })
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg === 'success') {
                showToast('连接成功', 'success');
            } else {
                showToast('连接失败: ' + (resp.data || resp.msg), 'error');
            }
        })
        .catch(function() {
            showToast('请求失败', 'error');
        })
        .finally(function() {
            btn.disabled = false;
            btn.textContent = '测试连接';
        });
}

function saveConfig(key, value, description) {
    return fetch('/api/config/' + key, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            configKey: key,
            configValue: value,
            description: description
        })
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.msg !== 'success') {
            // 配置不存在则创建
            return fetch('/api/config', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    configKey: key,
                    configValue: value,
                    description: description
                })
            }).then(function(r) { return r.json(); });
        }
        return resp;
    })
    .then(function(resp) {
        return resp.msg === 'success';
    })
    .catch(function() {
        return false;
    });
}

function loadPluginStoreSettings() {
    var sourceUrls = settingsCache['tool.pluginSourceUrls'] || '';
    var list = document.getElementById('pluginSourceList');
    list.innerHTML = '';

    if (sourceUrls) {
        var urls = sourceUrls.split(',');
        urls.forEach(function(url) {
            if (url.trim()) {
                addPluginSourceInput(url.trim());
            }
        });
    } else {
        addPluginSourceInput('');
    }
}

function addPluginSource() {
    addPluginSourceInput('');
}

function addPluginSourceInput(value) {
    var list = document.getElementById('pluginSourceList');
    var row = document.createElement('div');
    row.className = 'search-key-row';
    row.innerHTML = '<input type="text" class="settings-input search-key-input" placeholder="https://example.com/plugins" value="' + escapeHtmlForAttr(value) + '">' +
        '<button class="btn-remove-key" onclick="removePluginSource(this)">×</button>';
    list.appendChild(row);
}

function removePluginSource(btn) {
    var row = btn.closest('.search-key-row');
    if (row) {
        row.remove();
    }
}

function escapeHtmlForAttr(text) {
    if (!text) return '';
    return text.replace(/"/g, '&quot;').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function savePluginStoreSettings() {
    var inputs = document.querySelectorAll('#pluginSourceList .search-key-input');
    var urls = [];
    inputs.forEach(function(input) {
        var value = input.value.trim();
        if (value) {
            urls.push(value);
        }
    });

    saveConfig('tool.pluginSourceUrls', urls.join(','), '插件商店源地址')
        .then(function(success) {
            if (success) {
                showToast('插件商店源设置保存成功', 'success');
                settingsCache['tool.pluginSourceUrls'] = urls.join(',');
            } else {
                showToast('保存失败', 'error');
            }
        });
}
