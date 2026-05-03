var settingsCache = {};

document.addEventListener('DOMContentLoaded', function() {
    loadAllSettings();
    loadProviders();
    setupCascading();
    setupToggleLabel();
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

function setupToggleLabel() {
    var checkbox = document.getElementById('memoryEnabled');
    checkbox.addEventListener('change', function() {
        document.getElementById('memoryEnabledLabel').textContent = this.checked ? '开启' : '关闭';
    });
}

function loadAllSettings() {
    fetch('/api/config')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            (resp.data || []).forEach(function(c) {
                settingsCache[c.configKey] = c.configValue;
            });
            document.getElementById('memoryEnabled').checked = settingsCache['memory_enabled'] === 'true';
            document.getElementById('memoryEnabledLabel').textContent = settingsCache['memory_enabled'] === 'true' ? '开启' : '关闭';
            document.getElementById('memoryFrequency').value = settingsCache['memory_frequency'] || '5';

            var savedModelId = settingsCache['memory_ai_model_id'];
            if (savedModelId) {
                selectModelById(savedModelId);
            }
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
    var enabled = document.getElementById('memoryEnabled').checked ? 'true' : 'false';
    var modelId = document.getElementById('memoryModelSelect').value;
    var frequency = document.getElementById('memoryFrequency').value.trim();

    if (!frequency || parseInt(frequency) < 1) {
        showToast('请输入有效的整理频率', 'error');
        return;
    }

    var saves = [];

    saves.push(saveConfig('memory_enabled', enabled, '是否开启记忆整理'));
    saves.push(saveConfig('memory_ai_model_id', modelId, '记忆整理使用模型'));
    saves.push(saveConfig('memory_frequency', frequency, '记忆整理频率（分钟）'));

    Promise.all(saves).then(function(results) {
        var allOk = results.every(function(r) { return r; });
        if (allOk) {
            showToast('设置保存成功', 'success');
        } else {
            showToast('部分设置保存失败', 'error');
        }
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
