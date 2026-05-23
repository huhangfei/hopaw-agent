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
            if (selectedPlugin && selectedVersion) {
                var leaf = document.querySelector(
                    '.tree-leaf[data-plugin="' + selectedPlugin + '"][data-version="' + selectedVersion + '"]');
                if (leaf) {
                    selectVersion(leaf, true);
                } else {
                    selectedPlugin = null;
                    selectedVersion = null;
                    showWelcome();
                }
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
        html += '<div class="tree-node" data-name="' + escapeHtml(plugin.name) + '">' +
            '<div class="tree-node-header" onclick="toggleTreeNode(this)">' +
            '<span class="tree-arrow">▸</span>' +
            '<span class="tree-icon">🧩</span>' +
            '<span class="tree-name">' + escapeHtml(plugin.name) + '</span>' +
            '<span class="tree-badge">' + plugin.versions.length + '个版本</span>' +
            '</div>' +
            '<div class="tree-node-children">';
        plugin.versions.forEach(function(v) {
            var hash = v.sha256Hash ? v.sha256Hash.substring(0, 8) : '';
            html += '<div class="tree-leaf"' +
                ' data-plugin="' + escapeHtml(plugin.name) + '"' +
                ' data-version="' + escapeHtml(v.version) + '"' +
                ' onclick="selectVersion(this)">' +
                '<span class="leaf-dot"></span>' +
                '<span class="leaf-version">v' + escapeHtml(v.version) + '</span>' +
                '<span class="leaf-hash">' + escapeHtml(hash) + '</span>' +
                '</div>';
        });
        html += '</div></div>';
    });
    container.innerHTML = html;

    var wp = document.getElementById('welcomePanel');
    var dp = document.getElementById('detailPanel');
    if (wp) wp.style.display = '';
    if (dp) dp.style.display = '';
}

function toggleTreeNode(header) {
    var node = header.parentNode;
    node.classList.toggle('expanded');
}

function selectVersion(leaf, fromRefresh) {
    var detailPanel = document.getElementById('detailPanel');
    var welcomePanel = document.getElementById('welcomePanel');
    if (!detailPanel || !welcomePanel) return;

    document.querySelectorAll('.tree-leaf.active').forEach(function(el) {
        el.classList.remove('active');
    });
    leaf.classList.add('active');

    var node = leaf.closest('.tree-node');
    if (node && !node.classList.contains('expanded')) {
        node.classList.add('expanded');
    }

    selectedPlugin = leaf.getAttribute('data-plugin');
    selectedVersion = leaf.getAttribute('data-version');

    welcomePanel.style.display = 'none';
    detailPanel.style.display = '';

    var plugin = null;
    for (var i = 0; i < pluginCache.length; i++) {
        if (pluginCache[i].name === selectedPlugin) {
            plugin = pluginCache[i];
            break;
        }
    }
    if (!plugin) return;

    var version = null;
    for (var j = 0; j < plugin.versions.length; j++) {
        if (plugin.versions[j].version === selectedVersion) {
            version = plugin.versions[j];
            break;
        }
    }
    if (!version) return;

    var detailName = document.getElementById('detailName');
    var metaVersion = document.getElementById('metaVersion');
    var metaAuthor = document.getElementById('metaAuthor');
    var metaHash = document.getElementById('metaHash');
    var metaFileSize = document.getElementById('metaFileSize');
    var metaDescription = document.getElementById('metaDescription');
    var keywordEl = document.getElementById('detailKeyword');
    var iconEl = document.getElementById('detailIcon');
    var btnDownload = document.getElementById('btnDownload');
    var btnDelete = document.getElementById('btnDelete');

    if (detailName) detailName.textContent = selectedPlugin;
    if (metaVersion) metaVersion.textContent = 'v' + selectedVersion;
    if (metaAuthor) metaAuthor.textContent = version.author || '-';
    if (metaHash) metaHash.textContent = version.sha256Hash || '-';
    if (metaFileSize) metaFileSize.textContent = formatFileSize(version.fileSize);
    if (metaDescription) metaDescription.textContent = plugin.description || version.description || '暂无描述';

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
        if (isAdmin) {
            btnDelete.style.display = '';
        } else {
            btnDelete.style.display = 'none';
        }
    }

    renderToolsList(version.tools);
}

function showWelcome() {
    var wp = document.getElementById('welcomePanel');
    var dp = document.getElementById('detailPanel');
    if (wp) wp.style.display = '';
    if (dp) dp.style.display = '';
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
            selectedPlugin = null;
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

function filterPlugins() {
    var query = document.getElementById('searchInput').value.toLowerCase().trim();
    var nodes = document.querySelectorAll('.tree-node');

    nodes.forEach(function(node) {
        var name = node.getAttribute('data-name').toLowerCase();
        var leaves = node.querySelectorAll('.tree-leaf');

        if (query === '') {
            node.classList.remove('filtered-hidden');
            leaves.forEach(function(l) { l.classList.remove('filtered-hidden'); });
            return;
        }

        var plugin = null;
        for (var i = 0; i < pluginCache.length; i++) {
            if (pluginCache[i].name === node.getAttribute('data-name')) {
                plugin = pluginCache[i];
                break;
            }
        }

        var nameMatch = name.indexOf(query) !== -1;
        var keywordMatch = plugin && plugin.keyword && plugin.keyword.toLowerCase().indexOf(query) !== -1;
        var descMatch = plugin && plugin.description && plugin.description.toLowerCase().indexOf(query) !== -1;
        var anyLeafMatch = false;

        leaves.forEach(function(l) {
            var version = l.getAttribute('data-version').toLowerCase();
            if (nameMatch || keywordMatch || descMatch || version.indexOf(query) !== -1) {
                l.classList.remove('filtered-hidden');
                anyLeafMatch = true;
            } else {
                l.classList.add('filtered-hidden');
            }
        });

        if (nameMatch || keywordMatch || descMatch || anyLeafMatch) {
            node.classList.remove('filtered-hidden');
            if ((nameMatch || keywordMatch || descMatch) && query !== '' && leaves.length > 0) {
                node.classList.add('expanded');
            }
        } else {
            node.classList.add('filtered-hidden');
        }
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

function renderToolsList(tools) {
    var container = document.getElementById('toolsList');
    if (!container) return;

    if (!tools || tools.length === 0) {
        container.innerHTML = '<p class="empty-tools">此插件未包含工具</p>';
        return;
    }

    var html = '';
    tools.forEach(function(tool) {
        html += '<div class="tool-item">';
        html += '<div class="tool-item-header">';
        html += '<span class="tool-item-name">' + escapeHtml(tool.name) + '</span>';
        html += '<span class="tool-item-desc">' + escapeHtml(tool.description || '') + '</span>';
        html += '</div>';

        if (tool.parameters && tool.parameters.length > 0) {
            html += '<table class="tool-param-table">';
            html += '<thead><tr><th>参数名</th><th>类型</th><th>说明</th><th>必填</th></tr></thead>';
            html += '<tbody>';
            tool.parameters.forEach(function(p) {
                html += '<tr>';
                html += '<td class="tp-name">' + escapeHtml(p.name) + '</td>';
                html += '<td class="tp-type">' + escapeHtml(p.type || '-') + '</td>';
                html += '<td class="tp-desc">' + escapeHtml(p.description || '-') + '</td>';
                html += '<td class="tp-required">' + (p.required ? '✓' : '-') + '</td>';
                html += '</tr>';
            });
            html += '</tbody></table>';
        } else {
            html += '<div class="tool-no-params">无参数</div>';
        }

        html += '</div>';
    });

    container.innerHTML = html;
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