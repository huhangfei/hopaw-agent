var settingsCache = {};

document.addEventListener('DOMContentLoaded', function() {
    loadAllSettings();
    loadProviders();
    setupCascading();
    loadMemoryTaskStatus();
    loadTtsVendors();
    initTabFromUrl();
});

function initTabFromUrl() {
    var params = new URLSearchParams(window.location.search);
    var tab = params.get('tab');
    if (tab && ['memory', 'mail', 'tts', 'pluginStore'].includes(tab)) {
        switchTab(tab);
    }
}

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

// ========== TTS 设置（列表形式） ==========
var ttsVendorMap = {};
var ttsConfigList = [];

function loadTtsVendors() {
    fetch('/api/tts/vendors')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            ttsVendorMap = resp.data;
            var select = document.getElementById('ttsVendorSelect');
            select.innerHTML = '<option value="">选择厂商</option>';
            for (var code in ttsVendorMap) {
                if (!ttsVendorMap.hasOwnProperty(code)) continue;
                var opt = document.createElement('option');
                opt.value = code;
                opt.textContent = ttsVendorMap[code];
                select.appendChild(opt);
            }
            loadTtsConfigList();
        })
        .catch(function(e) {
            console.error('加载 TTS 厂商列表失败:', e);
        });
}

function loadTtsConfigList() {
    fetch('/api/tts/configs')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            ttsConfigList = resp.data || [];
            renderTtsTable();
        })
        .catch(function(e) {
            console.error('加载 TTS 配置列表失败:', e);
        });
}

function renderTtsTable() {
    var tbody = document.getElementById('ttsTableBody');
    if (!ttsConfigList || ttsConfigList.length === 0) {
        tbody.innerHTML = '<tr id="ttsEmptyRow"><td colspan="6" class="tts-empty">暂无 TTS 配置，点击"添加配置"开始</td></tr>';
        return;
    }

    var rows = '';
    ttsConfigList.forEach(function(cfg) {
        var vendorName = ttsVendorMap[cfg.vendorCode] || cfg.vendorName || cfg.vendorCode;
        var configPreview = cfg.configJson || '';
        if (configPreview.length > 60) {
            configPreview = configPreview.substring(0, 60) + '...';
        }
        configPreview = escapeHtml(configPreview);
        var enabledBadge = cfg.enabled === 1
            ? '<span class="tts-status-badge enabled">已启用</span>'
            : '<span class="tts-status-badge disabled">已禁用</span>';

        rows += '<tr>';
        rows += '<td>' + escapeHtml(cfg.configName || '-') + '</td>';
        rows += '<td>' + escapeHtml(vendorName) + '</td>';
        rows += '<td><code>' + escapeHtml(cfg.vendorCode) + '</code></td>';
        rows += '<td class="tts-config-cell" title="' + escapeHtml(cfg.configJson || '') + '">' + configPreview + '</td>';
        rows += '<td>' + enabledBadge + '</td>';
        rows += '<td class="tts-actions">'
            + '<button class="btn-tts-edit" onclick="editTtsConfig(' + cfg.id + ')">编辑</button>'
            + '<button class="btn-tts-delete" onclick="deleteTtsConfig(' + cfg.id + ')">删除</button>'
            + '</td>';
        rows += '</tr>';
    });
    tbody.innerHTML = rows;
}

function showTtsForm() {
    document.getElementById('ttsEditId').value = '';
    document.getElementById('ttsFormTitle').textContent = '添加 TTS 配置';
    document.getElementById('ttsVendorSelect').value = '';
    document.getElementById('ttsConfigName').value = '';
    document.getElementById('ttsConfigJson').value = '';
    document.getElementById('ttsEnabled').checked = true;
    document.getElementById('ttsEditForm').style.display = 'block';
}

function hideTtsForm() {
    document.getElementById('ttsEditForm').style.display = 'none';
    document.getElementById('ttsEditId').value = '';
}

function editTtsConfig(id) {
    var cfg = null;
    for (var i = 0; i < ttsConfigList.length; i++) {
        if (ttsConfigList[i].id === id) {
            cfg = ttsConfigList[i];
            break;
        }
    }
    if (!cfg) return;

    document.getElementById('ttsEditId').value = cfg.id;
    document.getElementById('ttsFormTitle').textContent = '编辑 TTS 配置';
    document.getElementById('ttsVendorSelect').value = cfg.vendorCode || '';
    document.getElementById('ttsConfigName').value = cfg.configName || '';
    document.getElementById('ttsConfigJson').value = cfg.configJson || '';
    document.getElementById('ttsEnabled').checked = cfg.enabled === 1;
    document.getElementById('ttsEditForm').style.display = 'block';
}

function saveTtsForm() {
    var id = document.getElementById('ttsEditId').value;
    var vendorCode = document.getElementById('ttsVendorSelect').value;
    var configJson = document.getElementById('ttsConfigJson').value.trim();
    var enabled = document.getElementById('ttsEnabled').checked ? 1 : 0;

    if (!vendorCode) {
        showToast('请选择 TTS 厂商', 'error');
        return;
    }

    var payload = {
        id: id ? parseInt(id) : null,
        vendorCode: vendorCode,
        vendorName: ttsVendorMap[vendorCode] || vendorCode,
        configName: document.getElementById('ttsConfigName').value.trim(),
        configJson: configJson,
        enabled: enabled
    };

    fetch('/api/tts/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.msg === 'success') {
            showToast(id ? 'TTS 配置更新成功' : 'TTS 配置添加成功', 'success');
            hideTtsForm();
            loadTtsConfigList();
        } else {
            showToast('保存失败: ' + (resp.data || ''), 'error');
        }
    })
    .catch(function() {
        showToast('保存失败', 'error');
    });
}

function deleteTtsConfig(id) {
    showConfirm('确定要删除该 TTS 配置吗？').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/tts/config/' + id, { method: 'DELETE' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.msg === 'success') {
                    showToast('删除成功', 'success');
                    hideTtsForm();
                    loadTtsConfigList();
                } else {
                    showToast('删除失败: ' + (resp.data || ''), 'error');
                }
            })
            .catch(function() {
                showToast('删除失败', 'error');
            });
    });
}

function onTtsVendorChange() {
    // 厂商切换时暂不联动音色，仅做记录
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
