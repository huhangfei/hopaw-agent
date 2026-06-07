var settingsCache = {};

document.addEventListener('DOMContentLoaded', function() {
    loadAllSettings();
});

function loadAllSettings() {
    fetch('/api/config')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            (resp.data || []).forEach(function(c) {
                settingsCache[c.configKey] = c.configValue;
            });
            if (typeof onSettingsLoaded === 'function') {
                onSettingsLoaded();
            }
        })
        .catch(function(e) {
            console.error('加载设置失败:', e);
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

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeHtmlForAttr(text) {
    if (!text) return '';
    return text.replace(/"/g, '&quot;').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}