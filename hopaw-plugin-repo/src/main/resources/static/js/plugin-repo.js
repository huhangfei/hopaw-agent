var pluginCache = [];
var selectedPlugin = null;
var selectedVersion = null;

function loadPlugins(callback) {
    fetch('/plugin-repo/api/plugins')
        .then(function(r) { return r.json(); })
        .then(function(data) {
            pluginCache = data;
            renderTree(data);
            if (callback) callback();
        })
        .catch(function(err) {
            console.error('Failed to load plugins', err);
        });
}

function refreshPlugins() {
    var btn = document.getElementById('btnRefresh');
    if (btn) {
        btn.classList.add('refreshing');
        btn.disabled = true;
    }
    fetch('/plugin-repo/api/plugins')
        .then(function(r) { return r.json(); })
        .then(function(data) {
            pluginCache = data;
            renderTree(data);
            // 保持当前选中：插件仍在则尽量停在原版本，原版本被删则回退到最新版
            if (selectedPlugin && findPlugin(selectedPlugin)) {
                selectPlugin(selectedPlugin, selectedVersion);
            } else {
                selectedPlugin = null;
                selectedVersion = null;
                showWelcome();
            }
        })
        .catch(function(err) {
            console.error('Failed to refresh plugins', err);
        })
        .finally(function() {
            if (btn) {
                btn.classList.remove('refreshing');
                btn.disabled = false;
            }
        });
}

/**
 * 左侧列表 —— 只展示插件（版本收进详情页的下拉里）。
 * 点击整条即选中该插件，默认展示最新版本。
 */
function renderTree(plugins) {
    var container = document.getElementById('treeContainer');
    if (!container) return;

    if (!plugins || plugins.length === 0) {
        container.innerHTML = '<div class="empty-state">' +
            '<div class="empty-icon">📦</div>' +
            '<p>暂无插件包</p>' +
            '<p class="empty-hint">点击「导入插件包」上传</p>' +
            '</div>';
        return;
    }

    var html = '';
    plugins.forEach(function(plugin) {
        var desc = plugin.description || '';
        var keyword = plugin.keyword || '';
        var infoHtml = '<div class="tree-node-info">';
        if (plugin.id) {
            infoHtml += '<div class="tree-node-id">' + escapeHtml(plugin.id) + '</div>';
        }
        if (desc) {
            infoHtml += '<div class="tree-node-desc" title="' + escapeHtml(desc) + '">' + escapeHtml(desc) + '</div>';
        }
        if (keyword) {
            infoHtml += '<div class="tree-node-keywords">';
            keyword.split(',').forEach(function(kw) {
                var k = kw.trim();
                if (k) {
                    infoHtml += '<span class="keyword-tag">' + escapeHtml(k) + '</span>';
                }
            });
            infoHtml += '</div>';
        }
        infoHtml += '</div>';

        html += '<div class="tree-node"' +
            ' data-id="' + escapeHtml(plugin.id) + '"' +
            ' data-name="' + escapeHtml(plugin.name) + '"' +
            ' onclick="selectPluginNode(this)">' +
            '<div class="tree-node-header">' +
            '<span class="tree-icon">🧩</span>' +
            '<span class="tree-name">' + escapeHtml(plugin.name) + '</span>' +
            '<span class="tree-badge">' + plugin.versions.length + ' 个版本</span>' +
            '</div>' +
            infoHtml +
            '</div>';
    });
    container.innerHTML = html;
    syncActiveNode();

    var wp = document.getElementById('welcomePanel');
    var dp = document.getElementById('detailPanel');
    if (wp) wp.style.display = '';
    if (dp) dp.style.display = '';
}

/** 列表项点击入口（从节点自身取 id，避免把 id 拼进 onclick 字符串） */
function selectPluginNode(node) {
    if (node && !node.classList.contains('tree-node')) {
        node = node.closest('.tree-node');
    }
    if (!node) return;
    selectPlugin(node.getAttribute('data-id'), null);
}

function findPlugin(pluginId) {
    if (!pluginId) return null;
    for (var i = 0; i < pluginCache.length; i++) {
        if (pluginCache[i].id === pluginId) return pluginCache[i];
    }
    return null;
}

/** 版本语义化降序副本（服务端已排好，这里兜底，保证下拉恒为「从新到旧」） */
function sortedVersions(plugin) {
    var list = (plugin && plugin.versions ? plugin.versions.slice() : []);
    list.sort(function(a, b) { return compareVersion(b.version, a.version); });
    return list;
}

function findVersion(plugin, version) {
    if (!plugin || !version) return null;
    for (var i = 0; i < plugin.versions.length; i++) {
        if (String(plugin.versions[i].version) === String(version)) return plugin.versions[i];
    }
    return null;
}

/** 选中某插件的某版本（version 为空/不存在时回退到最新版） */
function selectPlugin(pluginId, version) {
    var plugin = findPlugin(pluginId);
    if (!plugin) return;

    var versions = sortedVersions(plugin);
    if (versions.length === 0) return;

    var entry = findVersion(plugin, version) || versions[0];
    selectedPlugin = plugin.id;
    selectedVersion = entry.version;

    syncActiveNode();

    var welcomePanel = document.getElementById('welcomePanel');
    var detailPanel = document.getElementById('detailPanel');
    if (welcomePanel) welcomePanel.style.display = 'none';
    if (detailPanel) detailPanel.style.display = '';

    renderDetail(plugin, entry, versions);
}

function syncActiveNode() {
    document.querySelectorAll('.tree-node').forEach(function(node) {
        if (node.getAttribute('data-id') === selectedPlugin) {
            node.classList.add('active');
        } else {
            node.classList.remove('active');
        }
    });
}

function renderDetail(plugin, entry, versions) {
    var detailName = document.getElementById('detailName');
    var metaAuthor = document.getElementById('metaAuthor');
    var metaHash = document.getElementById('metaHash');
    var metaFileSize = document.getElementById('metaFileSize');
    var metaId = document.getElementById('metaId');
    var metaToolSets = document.getElementById('metaToolSets');
    var metaAssets = document.getElementById('metaAssets');
    var metaInvoke = document.getElementById('metaInvoke');
    var metaDescription = document.getElementById('metaDescription');
    var keywordEl = document.getElementById('detailKeyword');
    var iconEl = document.getElementById('detailIcon');
    var btnDownload = document.getElementById('btnDownload');
    var btnDelete = document.getElementById('btnDelete');

    if (detailName) detailName.textContent = plugin.name || plugin.id;
    if (metaAuthor) metaAuthor.textContent = entry.author || '-';
    if (metaHash) {
        // 字段区已收紧为单行 + 省略号，完整哈希挂 title 供悬停查看
        metaHash.textContent = entry.sha256Hash || '-';
        metaHash.title = entry.sha256Hash || '';
    }
    if (metaFileSize) metaFileSize.textContent = formatFileSize(entry.fileSize);
    if (metaId) metaId.textContent = plugin.id || '-';
    if (metaToolSets) {
        metaToolSets.textContent = entry.toolSetCount > 0 ? entry.toolSetCount + ' 个' : '无（纯前端）';
    }
    if (metaAssets) metaAssets.textContent = entry.frontendAssetCount > 0 ? entry.frontendAssetCount + ' 个' : '无';
    if (metaInvoke) metaInvoke.textContent = entry.invokeSupport ? '支持' : '不支持';
    if (metaDescription) metaDescription.textContent = plugin.description || '暂无描述';

    if (keywordEl) {
        var keyword = plugin.keyword || '';
        if (keyword) {
            keywordEl.textContent = keyword;
            keywordEl.style.display = '';
        } else {
            keywordEl.style.display = 'none';
        }
    }

    if (iconEl) {
        if (plugin.iconIsSvgCode) {
            iconEl.innerHTML = plugin.icon;
        } else if (plugin.icon) {
            iconEl.innerHTML = '<img src="/icons/tools/' + plugin.icon + '" alt="icon" onerror="this.style.display=\'none\'">';
        } else {
            iconEl.innerHTML = '<span style="font-size:32px;">🧩</span>';
        }
    }

    if (btnDownload) {
        btnDownload.setAttribute('data-url',
            '/plugin-repo/api/download/' + encodeURIComponent(selectedPlugin) + '/' + encodeURIComponent(selectedVersion));
    }

    if (btnDelete) {
        btnDelete.style.display = isAdmin ? '' : 'none';
    }

    renderVersionSelect(versions, selectedVersion);
    renderProvides(entry);
}

/** 版本下拉：从新到旧，最新版带「（最新）」标注；仅一个版本时禁用 */
function renderVersionSelect(versions, current) {
    var select = document.getElementById('versionSelect');
    if (!select) return;

    var html = '';
    versions.forEach(function(v, i) {
        var value = escapeHtml(String(v.version));
        html += '<option value="' + value + '"' +
            (String(v.version) === String(current) ? ' selected' : '') + '>v' + value +
            (i === 0 ? '（最新）' : '') +
            '</option>';
    });
    select.innerHTML = html;
    select.value = String(current);
    select.disabled = versions.length <= 1;
    select.title = versions.length <= 1 ? '当前仅一个版本' : '共 ' + versions.length + ' 个版本，可切换查看';
}

function onVersionChange() {
    var select = document.getElementById('versionSelect');
    if (!select || !selectedPlugin) return;
    selectPlugin(selectedPlugin, select.value);
}

function showWelcome() {
    var wp = document.getElementById('welcomePanel');
    var dp = document.getElementById('detailPanel');
    if (wp) wp.style.display = '';
    if (dp) dp.style.display = 'none';
    syncActiveNode();
}

function downloadPlugin() {
    var url = document.getElementById('btnDownload').getAttribute('data-url');
    if (url) {
        window.location.href = url;
    }
}

function deletePlugin() {
    if (!selectedPlugin || !selectedVersion) return;
    if (!confirm('确定要删除插件 "' + selectedPlugin + '" v' + selectedVersion + ' 吗？此操作不可撤销。')) return;

    fetch('/plugin-repo/web/plugin/' + encodeURIComponent(selectedPlugin) + '/' + encodeURIComponent(selectedVersion), {
        method: 'DELETE'
    })
    .then(function(r) { return r.json(); })
    .then(function(d) {
        if (d.type === 'success') {
            // 当前版本已删除 —— 版本置空，刷新后自动回退到该插件的最新版；插件被删空则回到欢迎页
            selectedVersion = null;
            refreshPlugins();
        } else {
            alert(d.message || '删除失败');
        }
    })
    .catch(function() {
        alert('删除失败');
    });
}

/** 搜索：插件维度匹配（名称 / 标识 / 描述 / 关键词 / 工具集名 / 任一带的版本号） */
function filterPlugins() {
    var query = document.getElementById('searchInput').value.toLowerCase().trim();

    document.querySelectorAll('.tree-node').forEach(function(node) {
        if (query === '') {
            node.classList.remove('filtered-hidden');
            return;
        }

        var nodeId = node.getAttribute('data-id') || '';
        var name = (node.getAttribute('data-name') || '').toLowerCase();
        var plugin = findPlugin(nodeId);

        var matched = name.indexOf(query) !== -1 || nodeId.toLowerCase().indexOf(query) !== -1;
        if (!matched && plugin) {
            if (plugin.keyword && plugin.keyword.toLowerCase().indexOf(query) !== -1) matched = true;
            if (!matched && plugin.description && plugin.description.toLowerCase().indexOf(query) !== -1) matched = true;
            for (var p = 0; p < plugin.versions.length && !matched; p++) {
                var ver = plugin.versions[p];
                if (String(ver.version).toLowerCase().indexOf(query) !== -1) {
                    matched = true;
                    break;
                }
                var provides = ver.provides || [];
                for (var q = 0; q < provides.length; q++) {
                    if ((provides[q].name || '').toLowerCase().indexOf(query) !== -1) {
                        matched = true;
                        break;
                    }
                }
            }
        }

        node.classList.toggle('filtered-hidden', !matched);
    });
}

function showImportDialog() {
    document.getElementById('importModal').style.display = '';
    document.getElementById('importProgress').style.display = 'none';
    document.getElementById('importResult').style.display = 'none';
    document.getElementById('dropZone').style.display = '';
    document.getElementById('fileInput').value = '';
}

function hideImportDialog() {
    document.getElementById('importModal').style.display = 'none';
}

function handleFileSelect(input) {
    if (input.files.length > 0) {
        uploadFile(input.files[0]);
    }
}

function uploadFile(file) {
    var dropZone = document.getElementById('dropZone');
    var progressDiv = document.getElementById('importProgress');
    var resultDiv = document.getElementById('importResult');
    var progressFill = document.getElementById('progressFill');
    var progressText = document.getElementById('progressText');

    dropZone.style.display = 'none';
    progressDiv.style.display = '';
    resultDiv.style.display = 'none';

    var formData = new FormData();
    formData.append('file', file);

    var xhr = new XMLHttpRequest();
    xhr.open('POST', '/plugin-repo/web/import', true);

    xhr.upload.onprogress = function(e) {
        if (e.lengthComputable) {
            var pct = Math.round((e.loaded / e.total) * 100);
            progressFill.style.width = pct + '%';
            progressText.textContent = '正在导入... ' + pct + '%';
        }
    };

    xhr.onload = function() {
        progressDiv.style.display = 'none';
        resultDiv.style.display = '';

        if (xhr.status === 200) {
            resultDiv.className = 'import-result success';
            resultDiv.innerHTML = '导入成功！正在刷新...';
            setTimeout(function() {
                hideImportDialog();
                refreshPlugins();
            }, 800);
        } else {
            resultDiv.className = 'import-result error';
            try {
                var msg = JSON.parse(xhr.responseText);
                resultDiv.textContent = '导入失败: ' + (msg.message || msg);
            } catch(e) {
                resultDiv.textContent = '导入失败';
            }
        }
    };

    xhr.onerror = function() {
        progressDiv.style.display = 'none';
        resultDiv.style.display = '';
        resultDiv.className = 'import-result error';
        resultDiv.textContent = '网络错误，导入失败';
    };

    xhr.send(formData);
}

function formatFileSize(bytes) {
    if (!bytes || bytes <= 0) return '-';
    if (bytes < 1024) return bytes + ' B';
    var exp = Math.floor(Math.log(bytes) / Math.log(1024));
    var pre = 'KMGTPE'.charAt(exp - 1) + 'B';
    return (bytes / Math.pow(1024, exp)).toFixed(1) + ' ' + pre;
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ==================== 语义化版本比较 ====================
// 不能直接用字符串比较：'1.10.0' > '1.9.0' 会误判为 false

/** 拆出 [数字段数组, 预发布标识]；忽略 build 元数据（+ 之后） */
function parseVersion(str) {
    var s = String(str == null ? '' : str).trim();
    if (s.charAt(0) === 'v' || s.charAt(0) === 'V') s = s.substring(1);
    var plus = s.indexOf('+');
    if (plus !== -1) s = s.substring(0, plus);
    var dash = s.indexOf('-');
    var core = dash === -1 ? s : s.substring(0, dash);
    var pre = dash === -1 ? '' : s.substring(dash + 1);
    var nums = core.split('.').map(function(n) {
        var v = parseInt(n, 10);
        return isNaN(v) ? 0 : v;
    });
    return { nums: nums, pre: pre };
}

/** 返回 <0 / 0 / >0，遵循 SemVer：预发布版本小于同段正式版 */
function compareVersion(a, b) {
    var pa = parseVersion(a);
    var pb = parseVersion(b);
    var len = Math.max(pa.nums.length, pb.nums.length);
    for (var i = 0; i < len; i++) {
        var na = pa.nums[i] || 0;
        var nb = pb.nums[i] || 0;
        if (na !== nb) return na < nb ? -1 : 1;
    }
    if (pa.pre === pb.pre) return 0;
    if (pa.pre === '') return 1;
    if (pb.pre === '') return -1;
    return pa.pre < pb.pre ? -1 : 1;
}

/**
 * 渲染某版本的「提供能力」：
 * 顶部为一排能力徽标（工具集 / 前端资产 / invoke / 配置项），
 * 下面逐个列出工具集（含方法明细）与前端资产明细（名称 + 类型 + 大小）。
 */
function renderProvides(version) {
    var badgeContainer = document.getElementById('capBadges');
    var container = document.getElementById('providesList');
    if (!container) return;

    var provides = version.provides || [];

    if (badgeContainer) {
        var badges = '';
        badges += '<span class="cap-badge cap-badge-toolset">' + provides.length + ' 个工具集</span>';
        if (version.frontendAssetCount > 0) {
            badges += '<span class="cap-badge cap-badge-frontend">' + version.frontendAssetCount + ' 个前端资产</span>';
        }
        if (version.invokeSupport) {
            badges += '<span class="cap-badge cap-badge-invoke">支持 invoke</span>';
        }
        if (version.configItemCount > 0) {
            badges += '<span class="cap-badge cap-badge-config">' + version.configItemCount + ' 项插件配置</span>';
        }
        badgeContainer.innerHTML = badges;
    }

    var html = '';

    if (provides.length === 0) {
        html += '<p class="empty-tools">纯前端插件 —— 不提供任何工具集</p>';
    } else {
        provides.forEach(function(ts) {
            html += '<div class="tool-item">';
            html += '<div class="tool-item-header">';
            html += '<span class="tool-item-name">' + escapeHtml(ts.name || '') + '</span>';
            html += '<span class="tool-item-desc">' + escapeHtml(ts.description || '') + '</span>';
            html += '<span class="tool-item-count">' + (ts.toolCount || 0) + ' 个方法</span>';
            html += '</div>';
            html += renderMethodList(ts.methods);
            html += '</div>';
        });
    }

    html += renderAssetList(version.frontendAssets);
    container.innerHTML = html;
}

/** 工具方法明细：方法名 + 描述（旧版清单无 methods 时提示重新导出）。 */
function renderMethodList(methods) {
    if (!methods || methods.length === 0) {
        return '<div class="no-params">无方法明细（该版本清单由旧版导出，不含方法信息）</div>';
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
    var html = '<div class="asset-section-title">前端资产</div>';
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

document.addEventListener('DOMContentLoaded', function() {
    loadPlugins(function() {
        var dp = document.getElementById('detailPanel');
        if (dp) dp.style.display = 'none';
    });

    var dropZone = document.getElementById('dropZone');
    if (dropZone) {
        dropZone.addEventListener('click', function() {
            document.getElementById('fileInput').click();
        });

        dropZone.addEventListener('dragover', function(e) {
            e.preventDefault();
            dropZone.classList.add('dragover');
        });

        dropZone.addEventListener('dragleave', function() {
            dropZone.classList.remove('dragover');
        });

        dropZone.addEventListener('drop', function(e) {
            e.preventDefault();
            dropZone.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) {
                var file = e.dataTransfer.files[0];
                if (file.name.toLowerCase().endsWith('.zip')) {
                    uploadFile(file);
                } else {
                    alert('仅支持 .zip 格式文件');
                }
            }
        });
    }

    var modal = document.getElementById('importModal');
    if (modal) {
        modal.addEventListener('click', function(e) {
            if (e.target === modal) {
                hideImportDialog();
            }
        });
    }

    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            hideImportDialog();
        }
    });
});
