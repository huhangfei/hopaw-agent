var storePlugins = [];
var selectedPlugin = null;
var selectedVersionInfo = null;

function loadStorePlugins() {
    var statusEl = document.getElementById('storeStatus');
    var loadingEl = document.getElementById('storeLoading');

    statusEl.textContent = '加载中...';
    loadingEl.textContent = '加载中...';
    loadingEl.style.display = 'block';

    fetch('/tools/store/api/plugins')
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.code === 200) {
            storePlugins = resp.data || [];
            if (storePlugins.length === 0) {
                var noSource = document.getElementById('storeNoSource');
                if (noSource) noSource.style.display = 'block';
                loadingEl.textContent = '暂无插件数据';
                loadingEl.style.display = 'block';
                statusEl.textContent = '';
                return;
            }
            if (document.getElementById('storeNoSource')) {
                document.getElementById('storeNoSource').style.display = 'none';
            }
            statusEl.textContent = '共 ' + storePlugins.length + ' 个插件';
            renderStoreList();
            loadingEl.style.display = 'none';
        } else {
            statusEl.textContent = '加载失败';
            loadingEl.textContent = '加载失败: ' + (resp.msg || resp.data || '未知错误');
            loadingEl.style.display = 'block';
        }
    })
    .catch(function(err) {
        statusEl.textContent = '加载失败';
        loadingEl.textContent = '网络请求失败';
        loadingEl.style.display = 'block';
    });
}

function renderStoreList() {
    var body = document.getElementById('storeListBody');
    var html = '';

    storePlugins.forEach(function(plugin, idx) {
        var statusClass = '';
        var statusLabel = '未安装';
        var installedVersion = plugin.installedVersion || '';
        var hasNew = false;

        if (installedVersion && plugin.versions && plugin.versions.length > 0) {
            var latest = plugin.versions[0].version;
            if (latest !== installedVersion) {
                statusClass = 'status-update';
                statusLabel = '可更新';
                hasNew = true;
            } else {
                statusClass = 'status-installed';
                statusLabel = '已安装';
            }
        }

        html += '<div class="tool-list-item store-list-item" data-index="' + idx + '" onclick="selectStorePlugin(this)">';
        html += '  <div class="tool-list-icon">';
        if (plugin.iconIsSvgCode) {
            html += '    <span>' + plugin.icon + '</span>';
        } else if (plugin.icon) {
            html += '    <img src="' + escapeHtml(plugin.icon) + '" alt="icon" onerror="this.style.display=\'none\'">';
        }
        html += '  </div>';
        html += '  <div class="tool-list-info">';
        html += '    <div class="tool-list-name">' + escapeHtml(plugin.name);
        html += '      <span class="store-status-tag ' + statusClass + '">' + statusLabel + '</span>';
        if (hasNew) {
            html += '      <span class="store-new-tag">NEW</span>';
        }
        html += '    </div>';
        html += '    <div class="tool-list-desc">' + escapeHtml(plugin.description || '') + '</div>';
        html += '    <div class="tool-list-meta">';
        html += '      <span class="tool-list-count">' + (plugin.versions ? plugin.versions.length : 0) + ' 个版本</span>';
        if (installedVersion) {
            html += '      <span class="tool-list-version">已安装 v' + escapeHtml(installedVersion) + '</span>';
        }
        html += '    </div>';
        html += '  </div>';
        html += '</div>';
    });

    body.innerHTML = html || '<div class="tools-list-empty">暂无可用的插件源，请先在设置中配置</div>';
}

function filterPlugins() {
    var search = (document.getElementById('storeSearch').value || '').toLowerCase();
    var items = document.querySelectorAll('#storeListBody .store-list-item');
    items.forEach(function(item) {
        var idx = parseInt(item.getAttribute('data-index'));
        var plugin = storePlugins[idx];
        if (!plugin) { item.style.display = 'none'; return; }
        var match = !search
            || (plugin.name && plugin.name.toLowerCase().indexOf(search) >= 0)
            || (plugin.description && plugin.description.toLowerCase().indexOf(search) >= 0)
            || (plugin.keyword && plugin.keyword.toLowerCase().indexOf(search) >= 0);
        var versionMatch = false;
        if (!match && plugin.versions) {
            versionMatch = plugin.versions.some(function(v) {
                return v.version && v.version.toLowerCase().indexOf(search) >= 0;
            });
        }
        item.style.display = (match || versionMatch) ? '' : 'none';
    });
}

function selectStorePlugin(el) {
    var items = document.querySelectorAll('.store-list-item');
    items.forEach(function(item) { item.classList.remove('active'); });
    el.classList.add('active');

    var index = parseInt(el.getAttribute('data-index'));
    selectedPlugin = storePlugins[index];
    if (!selectedPlugin) return;

    showPluginDetail();
}

function showPluginDetail() {
    var welcome = document.getElementById('storeWelcome');
    var detail = document.getElementById('storeDetail');
    if (welcome) welcome.style.display = 'none';
    detail.style.display = 'block';

    var p = selectedPlugin;

    document.getElementById('storeDetailName').textContent = p.name || '';
    document.getElementById('storeDetailDesc').textContent = p.description || '';

    var iconEl = document.getElementById('storeDetailIcon');
    if (p.iconIsSvgCode) {
        iconEl.innerHTML = p.icon;
    } else if (p.icon) {
        iconEl.innerHTML = '<img src="' + escapeHtml(p.icon) + '" alt="icon" style="width:44px;height:44px;border-radius:12px;" onerror="this.style.display=\'none\'">';
    } else {
        iconEl.innerHTML = '';
    }

    var kwEl = document.getElementById('storeDetailKeyword');
    if (p.keyword) {
        kwEl.style.display = '';
        kwEl.innerHTML = p.keyword.split(',').map(function(k) {
            return '<span class="tag">' + escapeHtml(k.trim()) + '</span>';
        }).join('');
    } else {
        kwEl.style.display = 'none';
    }

    var sel = document.getElementById('storeVersionSelect');
    sel.innerHTML = '';
    if (p.versions) {
        p.versions.forEach(function(v, i) {
            var opt = document.createElement('option');
            opt.value = i;
            opt.textContent = 'v' + v.version;
            sel.appendChild(opt);
        });
    }

    onVersionSwitch();
}

function onVersionSwitch() {
    if (!selectedPlugin) return;

    var p = selectedPlugin;
    var sel = document.getElementById('storeVersionSelect');
    var idx = parseInt(sel.value) || 0;
    var version = p.versions ? p.versions[idx] : null;
    if (!version) return;

    selectedVersionInfo = { plugin: p, version: version, versionIndex: idx };

    var status = version.status || 'not_installed';
    var badge = document.getElementById('storeStatusBadge');
    var btn = document.getElementById('btnStoreAction');

    badge.className = 'store-status-badge';
    btn.style.display = 'block';

    if (status === 'installed') {
        badge.classList.add('badge-installed');
        badge.textContent = '已安装';
        btn.style.display = 'none';
    } else if (status === 'update_available') {
        badge.classList.add('badge-update');
        badge.textContent = '有新版本';
        btn.className = 'btn-store-action btn-update';
        btn.textContent = '更新';
        btn.disabled = false;
    } else {
        badge.classList.add('badge-not-installed');
        badge.textContent = '未安装';
        btn.className = 'btn-store-action btn-install';
        btn.textContent = '安装';
        btn.disabled = false;
    }

    updateDetailMeta(version);
    updateDetailTools(version);
}

function updateDetailMeta(version) {
    var meta = document.getElementById('storeDetailMeta');
    var html = '';
    html += '<span class="detail-meta-label">版本</span><span class="detail-meta-value">v' + escapeHtml(version.version || '') + '</span>';
    if (version.author) {
        html += '<span class="detail-meta-label">作者</span><span class="detail-meta-value">' + escapeHtml(version.author) + '</span>';
    }
    html += '<span class="detail-meta-label">大小</span><span class="detail-meta-value">' + formatStoreFileSize(version.fileSize) + '</span>';
    if (version.sha256Hash) {
        html += '<span class="detail-meta-label">SHA256</span><span class="detail-meta-tag">' + escapeHtml(version.sha256Hash.substring(0, 16)) + '</span>';
    }
    meta.innerHTML = html;
}

function updateDetailTools(version) {
    var body = document.getElementById('storeDetailBody');
    if (!version.tools || version.tools.length === 0) {
        body.innerHTML = '<div class="detail-empty-state">该版本没有工具方法</div>';
        return;
    }

    var html = '';
    version.tools.forEach(function(tool) {
        html += '<div class="detail-tool-item">';
        html += '  <div class="detail-tool-header">';
        html += '    <span class="detail-tool-name">' + escapeHtml(tool.name) + '</span>';
        html += '    <span class="detail-tool-desc">' + escapeHtml(tool.description || '') + '</span>';
        html += '  </div>';
        if (tool.parameters && tool.parameters.length > 0) {
            html += '  <div class="detail-tool-params">';
            html += '    <table class="detail-param-table">';
            html += '      <thead><tr><th>参数名</th><th>类型</th><th>说明</th><th>必填</th></tr></thead>';
            html += '      <tbody>';
            tool.parameters.forEach(function(p) {
                html += '        <tr>';
                html += '          <td class="dp-name">' + escapeHtml(p.name) + '</td>';
                html += '          <td class="dp-type">' + escapeHtml(p.type || '') + '</td>';
                html += '          <td class="dp-desc">' + escapeHtml(p.description || '') + '</td>';
                html += '          <td class="dp-required">';
                html += p.required ? '<span class="tag-required">是</span>' : '<span class="tag-optional">否</span>';
                html += '          </td>';
                html += '        </tr>';
            });
            html += '      </tbody>';
            html += '    </table>';
            html += '  </div>';
        } else {
            html += '  <div class="detail-no-params">无参数</div>';
        }
        html += '</div>';
    });
    body.innerHTML = html;
}

function doInstallOrUpgrade() {
    if (!selectedVersionInfo) return;

    var info = selectedVersionInfo;
    var v = info.version;

    if (!v.downloadUrl) {
        showToast('下载地址为空', 'error');
        return;
    }

    var updateInfo = {
        toolName: info.plugin.name,
        version: v.version,
        fileName: info.plugin.name + '.jar',
        fileSize: v.fileSize,
        downloadUrl: v.downloadUrl,
        sha256Hash: v.sha256Hash,
        currentVersion: info.plugin.installedVersion || '',
        installed: v.status === 'installed' || v.status === 'update_available'
    };

    var btn = document.getElementById('btnStoreAction');
    btn.disabled = true;
    var originalText = btn.textContent;
    btn.textContent = (updateInfo.installed ? '更新中...' : '安装中...');

    fetch('/tools/api/install-upgrade', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updateInfo)
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        btn.disabled = false;
        btn.textContent = originalText;

        if (resp.code === 200) {
            showToast(updateInfo.installed ? '更新成功，正在刷新列表...' : '安装成功，正在刷新列表...', 'success');
            setTimeout(function() { loadStorePlugins(); }, 1500);
        } else {
            showToast(resp.msg || '操作失败', 'error');
        }
    })
    .catch(function(err) {
        btn.disabled = false;
        btn.textContent = originalText;
        showToast('请求失败: ' + err.message, 'error');
    });
}

function formatStoreFileSize(bytes) {
    if (!bytes) return '-';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

document.addEventListener('DOMContentLoaded', function() {
    loadStorePlugins();
});