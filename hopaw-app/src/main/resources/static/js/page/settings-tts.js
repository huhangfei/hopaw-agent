var ttsVendorMap = {};
var ttsConfigList = [];

function onSettingsLoaded() {
    loadTtsVendors();
}

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