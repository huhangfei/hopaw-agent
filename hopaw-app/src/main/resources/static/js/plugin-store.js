var storePlugins = [];
var selectedPlugin = null;
var selectedVersionInfo = null;

function loadStorePlugins() {
    var listBody = document.getElementById('storeListBody');
    var statusEl = document.getElementById('storeStatus');
    listBody.innerHTML = '<div class="tools-list-empty">加载中...</div>';

    fetch('/tools/plugin-store/api/plugins')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code === 200) {
                storePlugins = resp.data || [];
                renderPluginList(storePlugins);

                var noSourceEl = document.getElementById('storeNoSource');
                if (storePlugins.length === 0) {
                    statusEl.textContent = '暂无插件';
                    noSourceEl.style.display = 'block';
                } else {
                    statusEl.textContent = '共 ' + storePlugins.length + ' 个插件';
                    noSourceEl.style.display = 'none';
                }

                if (selectedPlugin) {
                    var found = null;
                    for (var i = 0; i < storePlugins.length; i++) {
                        if (storePlugins[i].name === selectedPlugin.name) {
                            found = storePlugins[i];
                            break;
                        }
                    }
                    if (found) {
                        selectPlugin(found.name);
                    } else {
                        resetDetail();
                    }
                }
            } else {
                listBody.innerHTML = '<div class="tools-list-empty">加载失败: ' + (resp.msg || '未知错误') + '</div>';
                statusEl.textContent = '加载失败';
            }
        })
        .catch(function(err) {
            listBody.innerHTML = '<div class="tools-list-empty">加载失败: ' + err.message + '</div>';
            statusEl.textContent = '加载失败';
        });
}

function renderPluginList(plugins) {
    var listBody = document.getElementById('storeListBody');
    if (!plugins || plugins.length === 0) {
        listBody.innerHTML = '<div class="tools-list-empty">暂无插件数据</div>';
        return;
    }

    var html = '';
    for (var i = 0; i < plugins.length; i++) {
        var p = plugins[i];
        var statusClass = '';
        var statusTag = '';

        if (p.installedVersion) {
            var hasUpdate = false;
            if (p.versions && p.versions.length > 0) {
                for (var j = 0; j < p.versions.length; j++) {
                    if (p.versions[j].version !== p.installedVersion && p.versions[j].version > p.installedVersion) {
                        hasUpdate = true;
                        break;
                    }
                }
            }
            if (hasUpdate) {
                statusClass = 'status-update';
                statusTag = '有更新';
            } else {
                statusClass = 'status-installed';
                statusTag = '已安装';
            }
        }

        var isActive = selectedPlugin && selectedPlugin.name === p.name;

        html += '<div class="tool-list-item' + (isActive ? ' active' : '') + '" data-name="' + escapeHtml(p.name) + '" onclick="selectPlugin(\'' + escapeJsStr(p.name) + '\')">';
        html += '<div class="tool-list-icon">';
        if (p.icon && p.icon.indexOf('<svg') === 0) {
            html += '<span>' + p.icon + '</span>';
        } else if (p.icon) {
            html += '<img src="/icons/tools/' + escapeHtml(p.icon) + '" alt="icon" onerror="this.style.display=\'none\'">';
        } else {
            html += '<span class="plugin-icon-default">🔌</span>';
        }
        html += '</div>';
        html += '<div class="tool-list-info">';
        html += '<div class="tool-list-name">' + escapeHtml(p.name);
        if (statusTag) {
            html += '<span class="store-status-tag ' + statusClass + '">' + statusTag + '</span>';
        }
        html += '</div>';
        html += '<div class="tool-list-desc">' + escapeHtml(p.description || '') + '</div>';
        html += '<div class="tool-list-meta">';
        if (p.versions && p.versions.length > 0) {
            html += '<span class="tool-list-count">' + p.versions.length + ' 个版本</span>';
        }
        if (p.installedVersion) {
            html += '<span class="tool-list-version">已安装 v' + escapeHtml(p.installedVersion) + '</span>';
        }
        html += '</div>';
        html += '</div>';
        html += '</div>';
    }

    listBody.innerHTML = html;
    filterPlugins();
}

function filterPlugins() {
    var query = document.getElementById('storeSearch').value.toLowerCase().trim();
    var items = document.querySelectorAll('#storeListBody .tool-list-item');

    if (query === '') {
        for (var i = 0; i < items.length; i++) {
            items[i].classList.remove('filtered-hidden');
        }
        return;
    }

    for (var i = 0; i < items.length; i++) {
        var nameEl = items[i].querySelector('.tool-list-name');
        var descEl = items[i].querySelector('.tool-list-desc');
        var name = (nameEl ? nameEl.textContent : '').toLowerCase();
        var desc = (descEl ? descEl.textContent : '').toLowerCase();

        if (name.indexOf(query) !== -1 || desc.indexOf(query) !== -1) {
            items[i].classList.remove('filtered-hidden');
        } else {
            items[i].classList.add('filtered-hidden');
        }
    }
}

function selectPlugin(pluginName) {
    var plugin = null;
    for (var i = 0; i < storePlugins.length; i++) {
        if (storePlugins[i].name === pluginName) {
            plugin = storePlugins[i];
            break;
        }
    }
    if (!plugin) return;

    selectedPlugin = plugin;

    var items = document.querySelectorAll('#storeListBody .tool-list-item');
    for (var j = 0; j < items.length; j++) {
        items[j].classList.remove('active');
        if (items[j].getAttribute('data-name') === pluginName) {
            items[j].classList.add('active');
        }
    }

    if (!plugin.versions || plugin.versions.length === 0) {
        resetDetail();
        return;
    }

    var targetVersion = null;
    if (plugin.installedVersion) {
        for (var k = 0; k < plugin.versions.length; k++) {
            if (plugin.versions[k].version === plugin.installedVersion) {
                targetVersion = plugin.versions[k];
                break;
            }
        }
        if (!targetVersion) {
            targetVersion = plugin.versions[0];
        }
    } else {
        targetVersion = plugin.versions[0];
    }

    document.getElementById('storeWelcome').style.display = 'none';
    document.getElementById('storeDetail').style.display = '';

    renderDetail(plugin, targetVersion);
}

function onVersionSwitch() {
    if (!selectedPlugin) return;

    var selectEl = document.getElementById('storeVersionSelect');
    var selectedVersion = selectEl.value;

    var version = null;
    if (selectedPlugin.versions) {
        for (var i = 0; i < selectedPlugin.versions.length; i++) {
            if (selectedPlugin.versions[i].version === selectedVersion) {
                version = selectedPlugin.versions[i];
                break;
            }
        }
    }
    if (!version) return;

    renderDetail(selectedPlugin, version);
}

function renderDetail(plugin, version) {
    selectedVersionInfo = {
        plugin: plugin,
        version: version
    };

    var iconEl = document.getElementById('storeDetailIcon');
    if (plugin.icon && plugin.icon.indexOf('<svg') === 0) {
        iconEl.innerHTML = '<span>' + plugin.icon + '</span>';
    } else if (plugin.icon) {
        iconEl.innerHTML = '<img src="/icons/tools/' + escapeHtml(plugin.icon) + '" alt="icon" onerror="this.style.display=\'none\'">';
    } else {
        iconEl.innerHTML = '<span style="font-size:28px;">🔌</span>';
    }

    document.getElementById('storeDetailName').textContent = plugin.name;
    document.getElementById('storeDetailDesc').textContent = plugin.description || '';

    var keywordEl = document.getElementById('storeDetailKeyword');
    if (plugin.keyword) {
        keywordEl.style.display = 'flex';
        keywordEl.innerHTML = '<span class="tag">' + escapeHtml(plugin.keyword) + '</span>';
    } else {
        keywordEl.style.display = 'none';
    }

    var selectEl = document.getElementById('storeVersionSelect');
    var optionsHtml = '';
    if (plugin.versions) {
        for (var i = 0; i < plugin.versions.length; i++) {
            var v = plugin.versions[i];
            var selected = v.version === version.version ? ' selected' : '';
            var label = 'v' + escapeHtml(v.version);
            if (plugin.installedVersion && v.version === plugin.installedVersion) {
                label += ' (已安装)';
            }
            optionsHtml += '<option value="' + escapeHtml(v.version) + '"' + selected + '>' + label + '</option>';
        }
    }
    selectEl.innerHTML = optionsHtml;

    var badgeEl = document.getElementById('storeStatusBadge');
    var btnEl = document.getElementById('btnStoreAction');

    if (version.status === 'installed') {
        badgeEl.textContent = '已安装';
        badgeEl.className = 'store-status-badge badge-installed';
        btnEl.textContent = '重新安装';
        btnEl.className = 'btn-store-action btn-install';
        btnEl.style.display = '';
    } else if (version.status === 'update_available') {
        badgeEl.textContent = '可更新';
        badgeEl.className = 'store-status-badge badge-update';
        btnEl.textContent = '更新到 v' + version.version;
        btnEl.className = 'btn-store-action btn-update';
        btnEl.style.display = '';
    } else {
        badgeEl.textContent = '未安装';
        badgeEl.className = 'store-status-badge badge-not-installed';
        btnEl.textContent = '安装 v' + version.version;
        btnEl.className = 'btn-store-action btn-install';
        btnEl.style.display = '';
    }

    var metaHtml = '';
    if (version.author) {
        metaHtml += '<span class="detail-meta-label">作者</span>';
        metaHtml += '<span class="detail-meta-value">' + escapeHtml(version.author) + '</span>';
    }
    if (version.fileSize > 0) {
        metaHtml += '<span class="detail-meta-label">大小</span>';
        metaHtml += '<span class="detail-meta-value">' + formatFileSize(version.fileSize) + '</span>';
    }
    if (version.url) {
        metaHtml += '<span class="detail-meta-label">地址</span>';
        metaHtml += '<a class="detail-meta-link" href="' + escapeHtml(version.url) + '" target="_blank" rel="noopener">' + escapeHtml(version.url) + '</a>';
    }
    if (version.sha256Hash) {
        metaHtml += '<span class="detail-meta-label">SHA256</span>';
        metaHtml += '<span class="detail-meta-value" style="font-size:11px;font-family:monospace;">' + escapeHtml(version.sha256Hash.substring(0, 16)) + '...</span>';
    }
    document.getElementById('storeDetailMeta').innerHTML = metaHtml || '<span class="detail-meta-label">暂无元数据</span>';

    var bodyEl = document.getElementById('storeDetailBody');
    if (version.tools && version.tools.length > 0) {
        renderToolsList(version.tools);
    } else {
        bodyEl.innerHTML = '<div class="detail-empty-state">此版本未包含工具信息</div>';
    }
}

function renderToolsList(tools) {
    var bodyEl = document.getElementById('storeDetailBody');
    if (!tools || tools.length === 0) {
        bodyEl.innerHTML = '<div class="detail-empty-state">此版本未包含工具信息</div>';
        return;
    }

    var html = '';
    for (var i = 0; i < tools.length; i++) {
        var tool = tools[i];
        html += '<div class="detail-tool-item">';
        html += '<div class="detail-tool-header">';
        html += '<span class="detail-tool-name">' + escapeHtml(tool.name) + '</span>';
        html += '<span class="detail-tool-desc">' + escapeHtml(tool.description || '') + '</span>';
        html += '</div>';

        if (tool.parameters && tool.parameters.length > 0) {
            html += '<div class="detail-tool-params">';
            html += '<table class="detail-param-table">';
            html += '<thead><tr><th>参数名</th><th>类型</th><th>说明</th><th>必填</th></tr></thead>';
            html += '<tbody>';
            for (var j = 0; j < tool.parameters.length; j++) {
                var p = tool.parameters[j];
                html += '<tr>';
                html += '<td class="dp-name">' + escapeHtml(p.name) + '</td>';
                html += '<td class="dp-type">' + escapeHtml(p.type || '-') + '</td>';
                html += '<td class="dp-desc">' + escapeHtml(p.description || '-') + '</td>';
                html += '<td class="dp-required">';
                html += p.required ? '<span class="tag-required">是</span>' : '<span class="tag-optional">否</span>';
                html += '</td>';
                html += '</tr>';
            }
            html += '</tbody></table>';
            html += '</div>';
        } else {
            html += '<div class="detail-no-params">无参数</div>';
        }

        html += '</div>';
    }

    bodyEl.innerHTML = html;
}

function resetDetail() {
    selectedPlugin = null;
    selectedVersionInfo = null;
    document.getElementById('storeWelcome').style.display = '';
    document.getElementById('storeDetail').style.display = 'none';
}

function formatFileSize(bytes) {
    if (!bytes || bytes <= 0) return '未知';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeJsStr(str) {
    if (!str) return '';
    return str.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"');
}

function doInstallOrUpgrade() {
    if (!selectedVersionInfo) return;

    var info = selectedVersionInfo;
    var v = info.version;
    var plugin = info.plugin;

    if (!v.downloadUrl) {
        showToast('下载地址为空', 'error');
        return;
    }

    var updateInfo = {
        toolName: plugin.name,
        version: v.version,
        fileName: v.jarFileName || plugin.jarFileName || (plugin.name + '.jar'),
        fileSize: v.fileSize,
        downloadUrl: v.downloadUrl,
        sha256Hash: v.sha256Hash,
        currentVersion: plugin.installedVersion || '',
        installed: v.status === 'installed' || v.status === 'update_available'
    };

    var btn = document.getElementById('btnStoreAction');
    btn.disabled = true;
    btn.textContent = '进行中...';

    var overlay = document.getElementById('storeProgressOverlay');
    var stageEl = document.getElementById('storeProgressStage');
    var barEl = document.getElementById('storeProgressBarFill');
    var percentEl = document.getElementById('storeProgressPercent');

    overlay.style.display = '';
    stageEl.textContent = '准备中...';
    barEl.style.width = '0%';
    percentEl.textContent = '0%';

    var stageLabels = {
        'uninstalling': '正在卸载旧版本...',
        'downloading': '正在下载插件包...',
        'extracting': '正在解压插件包...',
        'verifying': '正在校验文件完整性...',
        'installing': '正在安装插件...'
    };

    fetch('/tools/api/install-upgrade', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updateInfo)
    }).then(function(response) {
        if (!response.ok) {
            throw new Error('HTTP ' + response.status);
        }
        return response.body.getReader();
    }).then(function(reader) {
        var decoder = new TextDecoder();
        var buffer = '';
        var streamEnded = false;
        var currentEvent = null;

        function readStream() {
            return reader.read().then(function(result) {
                if (result.value) {
                    buffer += decoder.decode(result.value, { stream: true });

                    var lines = buffer.split('\n');
                    buffer = lines.pop() || '';

                    for (var i = 0; i < lines.length; i++) {
                        var line = lines[i];
                        if (line.indexOf('event:') === 0) {
                            currentEvent = line.substring(6).trim();
                        } else if (line.indexOf('data:') === 0 && currentEvent) {
                            var dataStr = line.substring(5).trim();
                            try {
                                var data = JSON.parse(dataStr);

                                if (currentEvent === 'stage') {
                                    var label = stageLabels[data.stage] || data.stage;
                                    stageEl.textContent = label;
                                } else if (currentEvent === 'progress') {
                                    var pct = data.percent || 0;
                                    barEl.style.width = pct + '%';
                                    percentEl.textContent = pct + '%';
                                } else if (currentEvent === 'complete') {
                                    streamEnded = true;
                                    handleInstallComplete(data, btn);
                                } else if (currentEvent === 'error') {
                                    streamEnded = true;
                                    handleInstallError(data.message || '安装失败', btn);
                                }
                            } catch (e) {
                                // skip malformed data
                            }
                            currentEvent = null;
                        }
                    }
                }

                if (result.done) {
                    if (!streamEnded) {
                        handleInstallError('连接已关闭，安装可能未完成', btn);
                    }
                    return;
                }

                return readStream();
            });
        }

        return readStream();
    }).catch(function(err) {
        handleInstallError('请求失败: ' + err.message, btn);
    });
}

function handleInstallComplete(result, btn) {
    hideProgress();

    btn.disabled = false;

    if (result.success) {
        if (result.conflictInfo && result.conflictInfo.conflictingPlugins || result.conflictInfo && result.conflictInfo.conflictingTools) {
            showToast(result.message || '', 'warning');
        } else {
            showToast(result.message || (result.isUpgrade ? '更新成功' : '安装成功'), 'success');
        }
        setTimeout(function() { loadStorePlugins(); }, 1500);
    } else {
        showToast(result.message || '操作失败', 'error');
    }
}

function handleInstallError(message, btn) {
    hideProgress();

    btn.disabled = false;

    if (selectedVersionInfo) {
        var v = selectedVersionInfo.version;
        var status = v.status;
        if (status === 'installed') {
            btn.textContent = '重新安装';
            btn.className = 'btn-store-action btn-install';
        } else if (status === 'update_available') {
            btn.textContent = '更新到 v' + v.version;
            btn.className = 'btn-store-action btn-update';
        } else {
            btn.textContent = '安装 v' + v.version;
            btn.className = 'btn-store-action btn-install';
        }
    }

    showToast(message, 'error');
}

function hideProgress() {
    var overlay = document.getElementById('storeProgressOverlay');
    if (overlay) {
        overlay.style.display = 'none';
    }
    var btn = document.getElementById('btnStoreAction');
    if (btn) {
        btn.disabled = false;
    }
}

document.addEventListener('DOMContentLoaded', function() {
    loadStorePlugins();
});