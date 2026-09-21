var storePlugins = [];
var selectedPlugin = null;
var selectedVersionInfo = null;

function loadStorePlugins() {
    var listBody = document.getElementById('storeListBody');
    var statusEl = document.getElementById('storeStatus');
    listBody.innerHTML = '<div class="tools-list-empty">加载中...</div>';

    fetch('/plugins/store/api/plugins')
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
                        if (storePlugins[i].id === selectedPlugin) {
                            found = storePlugins[i];
                            break;
                        }
                    }
                    if (found) {
                        selectPlugin(found.id);
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
                    if (semverGt(p.versions[j].version, p.installedVersion)) {
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

        var isActive = selectedPlugin === p.id;

        html += '<div class="tool-list-item' + (isActive ? ' active' : '') + '" data-id="' + escapeHtml(p.id) + '" onclick="selectPlugin(\'' + escapeJsStr(p.id) + '\')">';
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
        html += '<div class="tool-list-name">' + escapeHtml(p.name || p.id);
        if (statusTag) {
            html += '<span class="store-status-tag ' + statusClass + '">' + statusTag + '</span>';
        }
        html += '</div>';
        html += '<div class="tool-list-desc">' + escapeHtml(p.description || '') + '</div>';
        html += '<div class="tool-list-meta">';
        var newest = (p.versions && p.versions.length > 0) ? p.versions[0] : null;
        if (newest) {
            html += '<span class="tool-list-count">' + (newest.toolSetCount > 0
                    ? (newest.toolSetCount + ' 个工具集') : '纯前端插件') + '</span>';
            if (newest.frontendAssetCount > 0) {
                html += '<span class="tool-list-count">' + newest.frontendAssetCount + ' 个前端资产</span>';
            }
        }
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
        var id = (items[i].getAttribute('data-id') || '').toLowerCase();

        if (name.indexOf(query) !== -1 || desc.indexOf(query) !== -1 || id.indexOf(query) !== -1) {
            items[i].classList.remove('filtered-hidden');
        } else {
            items[i].classList.add('filtered-hidden');
        }
    }
}

/** 选中插件（参数为插件标识 pluginId） */
function selectPlugin(pluginId) {
    var plugin = null;
    for (var i = 0; i < storePlugins.length; i++) {
        if (storePlugins[i].id === pluginId) {
            plugin = storePlugins[i];
            break;
        }
    }
    if (!plugin) return;

    selectedPlugin = plugin.id;

    var items = document.querySelectorAll('#storeListBody .tool-list-item');
    for (var j = 0; j < items.length; j++) {
        items[j].classList.remove('active');
        if (items[j].getAttribute('data-id') === pluginId) {
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
    var plugin = findStorePlugin(selectedPlugin);
    if (!plugin) return;

    var selectEl = document.getElementById('storeVersionSelect');
    var selectedVersion = selectEl.value;

    var version = null;
    if (plugin.versions) {
        for (var i = 0; i < plugin.versions.length; i++) {
            if (plugin.versions[i].version === selectedVersion) {
                version = plugin.versions[i];
                break;
            }
        }
    }
    if (!version) return;

    renderDetail(plugin, version);
}

/** 按插件标识（pluginId）在商店列表中查找条目 */
function findStorePlugin(pluginId) {
    if (!pluginId) return null;
    for (var i = 0; i < storePlugins.length; i++) {
        if (storePlugins[i].id === pluginId) {
            return storePlugins[i];
        }
    }
    return null;
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

    document.getElementById('storeDetailName').textContent = plugin.name || plugin.id;
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
    } else if (version.status === 'older') {
        badgeEl.textContent = '低于已安装版本';
        badgeEl.className = 'store-status-badge badge-not-installed';
        btnEl.textContent = '回退到 v' + version.version;
        btnEl.className = 'btn-store-action btn-install';
        btnEl.style.display = '';
    } else {
        badgeEl.textContent = '未安装';
        badgeEl.className = 'store-status-badge badge-not-installed';
        btnEl.textContent = '安装 v' + version.version;
        btnEl.className = 'btn-store-action btn-install';
        btnEl.style.display = '';
    }

    var metaHtml = '';
    metaHtml += '<span class="detail-meta-label">插件标识</span>';
    metaHtml += '<span class="detail-meta-value" style="font-family:monospace;">' + escapeHtml(plugin.id || '-') + '</span>';
    if (version.author) {
        metaHtml += '<span class="detail-meta-label">作者</span>';
        metaHtml += '<span class="detail-meta-value">' + escapeHtml(version.author) + '</span>';
    }
    if (version.fileSize > 0) {
        metaHtml += '<span class="detail-meta-label">大小</span>';
        metaHtml += '<span class="detail-meta-value">' + formatFileSize(version.fileSize) + '</span>';
    }
    metaHtml += '<span class="detail-meta-label">工具集</span>';
    metaHtml += '<span class="detail-meta-value">' + (version.toolSetCount > 0
            ? version.toolSetCount + ' 个' : '无（纯前端）') + '</span>';
    metaHtml += '<span class="detail-meta-label">前端资产</span>';
    metaHtml += '<span class="detail-meta-value">' + (version.frontendAssetCount > 0
            ? version.frontendAssetCount + ' 个' : '无') + '</span>';
    metaHtml += '<span class="detail-meta-label">invoke</span>';
    metaHtml += '<span class="detail-meta-value">' + (version.invokeSupport ? '支持' : '不支持') + '</span>';
    if (version.url) {
        metaHtml += '<span class="detail-meta-label">地址</span>';
        metaHtml += '<a class="detail-meta-link" href="' + escapeHtml(version.url) + '" target="_blank" rel="noopener">' + escapeHtml(version.url) + '</a>';
    }
    if (version.sha256Hash) {
        metaHtml += '<span class="detail-meta-label">SHA256</span>';
        metaHtml += '<span class="detail-meta-value" style="font-size:11px;font-family:monospace;">' + escapeHtml(version.sha256Hash.substring(0, 16)) + '...</span>';
    }
    document.getElementById('storeDetailMeta').innerHTML = metaHtml;

    renderProvides(version);
}

/**
 * 渲染版本的「提供能力」：一排能力徽标 + 工具集（含方法明细）+ 前端资产明细。
 * 清单已带方法名/描述与资产名/大小；旧版清单缺这两项时降级显示提示。
 */
function renderProvides(version) {
    var bodyEl = document.getElementById('storeDetailBody');
    if (!bodyEl) return;

    var provides = version.provides || [];

    var html = '<div class="cap-badges">';
    html += '<span class="cap-badge cap-badge-toolset">' + provides.length + ' 个工具集</span>';
    if (version.frontendAssetCount > 0) {
        html += '<span class="cap-badge cap-badge-frontend">' + version.frontendAssetCount + ' 个前端资产</span>';
    }
    if (version.invokeSupport) {
        html += '<span class="cap-badge cap-badge-invoke">支持 invoke</span>';
    }
    if (version.configItemCount > 0) {
        html += '<span class="cap-badge cap-badge-config">' + version.configItemCount + ' 项插件配置</span>';
    }
    html += '</div>';

    if (provides.length === 0) {
        html += '<div class="detail-empty-state">纯前端插件 —— 不提供任何工具集</div>';
    } else {
        for (var i = 0; i < provides.length; i++) {
            var ts = provides[i];
            html += '<div class="detail-tool-item">';
            html += '<div class="detail-tool-header">';
            html += '<span class="detail-tool-name">' + escapeHtml(ts.name || '') + '</span>';
            html += '<span class="detail-tool-desc">' + escapeHtml(ts.description || '') + '</span>';
            html += '<span class="detail-tool-count">' + (ts.toolCount || 0) + ' 个方法</span>';
            html += '</div>';
            html += renderMethodList(ts.methods);
            html += '</div>';
        }
    }

    html += renderAssetList(version.frontendAssets);
    bodyEl.innerHTML = html;
}

/** 工具方法明细：方法名 + 描述（旧版清单无 methods 时提示重新导出）。 */
function renderMethodList(methods) {
    if (!methods || methods.length === 0) {
        return '<div class="detail-no-params">无方法明细（该版本清单由旧版导出，不含方法信息）</div>';
    }
    var html = '<div class="method-list">';
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        html += '<div class="method-row">';
        html += '<span class="method-name">' + escapeHtml(m.name || '') + '</span>';
        if (m.description) {
            html += '<span class="method-desc">' + escapeHtml(m.description) + '</span>';
        }
        html += '</div>';
    }
    html += '</div>';
    return html;
}

/** 前端资产明细：类型徽标 + 文件名 + 大小（旧版清单无 frontendAssets 时整体不渲染）。 */
function renderAssetList(assets) {
    if (!assets || assets.length === 0) return '';
    var html = '<div class="detail-section-title">前端资产</div>';
    html += '<div class="asset-list">';
    for (var i = 0; i < assets.length; i++) {
        var a = assets[i];
        var type = (a.type || '').toLowerCase();
        html += '<div class="asset-row">';
        html += '<span class="asset-type asset-type-' + escapeHtml(type) + '">' + escapeHtml(type) + '</span>';
        html += '<span class="asset-name" title="' + escapeHtml(a.path || '') + '">' + escapeHtml(a.name || '') + '</span>';
        html += '<span class="asset-size">' + formatAssetSize(a.size) + '</span>';
        html += '</div>';
    }
    html += '</div>';
    return html;
}

/** 资产大小格式化；size<0 表示 JAR 内读不到，显示「未知」。 */
function formatAssetSize(size) {
    if (size === null || size === undefined || size < 0) return '未知';
    return formatFileSize(size);
}

// ==================== 语义化版本比较 ====================
// 不能直接用字符串比较：'1.10.0' > '1.9.0' 会误判为 false

/** 拆出 [主版本, 预发布标识]；忽略 build 元数据（+ 之后） */
function semverSplit(version) {
    var v = (version == null ? '' : String(version)).trim();
    var plus = v.indexOf('+');
    if (plus >= 0) v = v.substring(0, plus);
    var dash = v.indexOf('-');
    if (dash < 0) return [v, null];
    return [v.substring(0, dash), v.substring(dash + 1)];
}

/** 主版本号逐段数值比较 */
function semverCompareCore(a, b) {
    var sa = a.split('.');
    var sb = b.split('.');
    var len = Math.max(sa.length, sb.length);
    for (var i = 0; i < len; i++) {
        var x = i < sa.length ? sa[i] : '0';
        var y = i < sb.length ? sb[i] : '0';
        if (x === y) continue;
        var nx = /^\d+$/.test(x) ? parseInt(x, 10) : null;
        var ny = /^\d+$/.test(y) ? parseInt(y, 10) : null;
        if (nx !== null && ny !== null) {
            if (nx !== ny) return nx < ny ? -1 : 1;
        } else if (x !== y) {
            return x < y ? -1 : 1;
        }
    }
    return 0;
}

/** 预发布标识比较：正式版 > 预发布版；数字段 < 非数字段 */
function semverComparePre(a, b) {
    if (a === null && b === null) return 0;
    if (a === null) return 1;
    if (b === null) return -1;
    var ia = a.split('.');
    var ib = b.split('.');
    var len = Math.max(ia.length, ib.length);
    for (var i = 0; i < len; i++) {
        if (i >= ia.length) return -1;
        if (i >= ib.length) return 1;
        var x = ia[i];
        var y = ib[i];
        if (x === y) continue;
        var nx = /^\d+$/.test(x) ? parseInt(x, 10) : null;
        var ny = /^\d+$/.test(y) ? parseInt(y, 10) : null;
        if (nx !== null && ny !== null) return nx < ny ? -1 : 1;
        if (nx !== null) return -1;
        if (ny !== null) return 1;
        return x < y ? -1 : 1;
    }
    return 0;
}

/** a > b 返回正数，a < b 返回负数，相等返回 0 */
function semverCompare(a, b) {
    var pa = semverSplit(a);
    var pb = semverSplit(b);
    var core = semverCompareCore(pa[0] === '' ? '0.0.0' : pa[0], pb[0] === '' ? '0.0.0' : pb[0]);
    if (core !== 0) return core;
    return semverComparePre(pa[1], pb[1]);
}

/** candidate 是否比 base 新 */
function semverGt(candidate, base) {
    return semverCompare(candidate, base) > 0;
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
    var exp = Math.floor(Math.log(bytes) / Math.log(1024));
    var pre = 'KMGTPE'.charAt(exp - 1) + 'B';
    return (bytes / Math.pow(1024, exp)).toFixed(1) + ' ' + pre;
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
        pluginId: plugin.id,
        version: v.version,
        fileName: v.jarFileName || (plugin.id + '.jar'),
        fileSize: v.fileSize,
        downloadUrl: v.downloadUrl,
        sha256Hash: v.sha256Hash,
        currentVersion: plugin.installedVersion || '',
        // 已安装（含更新与回退场景）时需要先卸载旧包再落盘
        installed: !!plugin.installedVersion,
        allowFrontendOnly: false
    };

    // 纯前端插件（不提供任何工具集）的前端资源会注入聊天页面，第三方来源必须先显式确认
    if (!v.toolSetCount) {
        var confirmed = window.confirm(
            '「' + (plugin.name || plugin.id) + '」是纯前端插件：不含任何后端工具集，'
            + '其前端资源（JS/CSS）会注入到聊天页面。\n\n请确认该来源可信后再继续。是否安装？');
        if (!confirmed) {
            return;
        }
        updateInfo.allowFrontendOnly = true;
    }

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

    fetch('/plugins/api/install-upgrade', {
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
        } else if (status === 'older') {
            btn.textContent = '回退到 v' + v.version;
            btn.className = 'btn-store-action btn-install';
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