var settingsCache = {};

document.addEventListener('DOMContentLoaded', function() {
    loadAllSettings();
});

function loadAllSettings() {
    // 按 tab 页声明的 SETTINGS_KEYS 按需查询，未声明 key 的页面不发起请求
    var keys = (typeof SETTINGS_KEYS !== 'undefined' && SETTINGS_KEYS.length) ? SETTINGS_KEYS : null;
    var req;
    if (keys) {
        req = fetch('/api/config/batch', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(keys)
        }).then(function(r) { return r.json(); });
    } else {
        req = Promise.resolve({ msg: 'success', data: [] });
    }
    req.then(function(resp) {
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

function saveConfig(key, value, description, isEncrypted) {
    var body = {
        configKey: key,
        configValue: value,
        description: description,
        isEncrypted: isEncrypted ? 1 : 0
    };
    return fetch('/api/config/' + key, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.msg !== 'success') {
            return fetch('/api/config', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
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