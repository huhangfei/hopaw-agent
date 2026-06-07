function onSettingsLoaded() {
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