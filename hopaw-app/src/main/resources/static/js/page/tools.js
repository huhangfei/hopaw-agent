// 插件与工具管理页（两级：插件 → 工具集）
//
// 一级：插件（了解详情 / 启停 / 插件配置 / 卸载 / 导出）
// 二级：工具集（查看工具方法与参数 / 工具配置）

var ACTIVE_TOOL_TAB = 'plugins';

/** 切换「插件 / 内置工具」Tab */
function switchToolTab(tab) {
    ACTIVE_TOOL_TAB = tab;
    var pluginsPane = document.getElementById('panePlugins');
    var builtinPane = document.getElementById('paneBuiltin');
    var pluginsBtn = document.getElementById('tabPluginsBtn');
    var builtinBtn = document.getElementById('tabBuiltinBtn');
    if (!pluginsPane || !builtinPane) return;

    if (tab === 'builtin') {
        pluginsPane.style.display = 'none';
        builtinPane.style.display = '';
        pluginsBtn.classList.remove('active');
        builtinBtn.classList.add('active');
    } else {
        pluginsPane.style.display = '';
        builtinPane.style.display = 'none';
        pluginsBtn.classList.add('active');
        builtinBtn.classList.remove('active');
    }
    filterTools();
}

function activePane() {
    return document.getElementById(ACTIVE_TOOL_TAB === 'builtin' ? 'paneBuiltin' : 'panePlugins');
}

/** 右侧详情面板切换：只显示匹配的详情块（attr 为 data-* 属性名） */
function showDetail(containerSelector, attr, value) {
    document.querySelectorAll('.plugin-detail, .tool-detail').forEach(function (d) {
        d.classList.remove('active');
    });
    var target = null;
    document.querySelectorAll(containerSelector).forEach(function (d) {
        if (target === null && d.getAttribute(attr) === value) target = d;
    });
    if (target) {
        target.classList.add('active');
        var panel = document.querySelector('.tools-detail-panel');
        if (panel) panel.scrollTop = 0;
    }
}

/** 选中插件（一级）：显示插件详情 */
function selectPlugin(header) {
    if (!header) return;
    var pluginId = header.getAttribute('data-plugin-id');
    if (!pluginId) return;

    document.querySelectorAll('.tool-plugin-header').forEach(function (h) {
        h.classList.remove('active', 'has-active-child');
    });
    document.querySelectorAll('.tool-list-item').forEach(function (i) {
        i.classList.remove('active');
    });
    header.classList.add('active');
    showDetail('.plugin-detail', 'data-plugin-id', pluginId);
}

/** 按 pluginId 选中插件（工具集详情里"返回插件"按钮使用） */
function selectPluginById(pluginId) {
    if (!pluginId) return;
    switchToolTab('plugins');
    var header = null;
    document.querySelectorAll('.tool-plugin-header').forEach(function (h) {
        if (header === null && h.getAttribute('data-plugin-id') === pluginId) header = h;
    });
    selectPlugin(header);
}

/** 选中工具集（二级）：显示工具集详情 */
function selectToolSet(el) {
    if (!el || el.classList.contains('is-disabled')) return;
    var toolSetName = el.getAttribute('data-tool-name');
    if (!toolSetName) return;

    document.querySelectorAll('.tool-list-item').forEach(function (i) {
        i.classList.remove('active');
    });
    document.querySelectorAll('.tool-plugin-header').forEach(function (h) {
        h.classList.remove('active', 'has-active-child');
    });
    el.classList.add('active');

    var group = el.closest('.tool-plugin-group');
    if (group) {
        var header = group.querySelector('.tool-plugin-header');
        if (header) header.classList.add('has-active-child');
    }
    showDetail('.tool-detail', 'data-tool-name', toolSetName);
}

/** 按工具集名选中（插件详情里的工具集概览行使用） */
function selectToolSetByName(toolSetName) {
    if (!toolSetName) return;
    var found = null;
    document.querySelectorAll('.tool-list-item[data-tool-name]').forEach(function (i) {
        if (found === null && i.getAttribute('data-tool-name') === toolSetName) found = i;
    });
    if (found) {
        selectToolSet(found);
    } else {
        // 当前 Tab 下找不到（例如插件被禁用），直接定位右侧详情
        showDetail('.tool-detail', 'data-tool-name', toolSetName);
    }
}

/** 搜索过滤：插件组按插件文本或其工具集文本命中 */
function filterTools() {
    var input = document.getElementById('toolsSearchInput');
    var query = input ? (input.value || '').toLowerCase().trim() : '';

    document.querySelectorAll('.tool-plugin-group').forEach(function (group) {
        var header = group.querySelector('.tool-plugin-header');
        var headerText = header ? (header.textContent || '').toLowerCase() : '';
        var pluginMatched = query === '' || headerText.indexOf(query) !== -1;
        var anyChild = false;

        group.querySelectorAll('.tool-list-child').forEach(function (child) {
            var childMatched = pluginMatched || (child.textContent || '').toLowerCase().indexOf(query) !== -1;
            if (childMatched) {
                child.classList.remove('filtered-hidden');
                anyChild = true;
            } else {
                child.classList.add('filtered-hidden');
            }
        });

        if (pluginMatched || anyChild) {
            group.classList.remove('filtered-hidden');
        } else {
            group.classList.add('filtered-hidden');
        }
    });

    document.querySelectorAll('#paneBuiltin .tool-list-item').forEach(function (item) {
        var matched = query === '' || (item.textContent || '').toLowerCase().indexOf(query) !== -1;
        if (matched) {
            item.classList.remove('filtered-hidden');
        } else {
            item.classList.add('filtered-hidden');
        }
    });

    selectFirstVisibleInActivePane();
}

/** 过滤后若当前选中项被隐藏，自动选中当前 Tab 下第一个可见项 */
function selectFirstVisibleInActivePane() {
    var pane = activePane();
    if (!pane) return;

    var activeEl = pane.querySelector('.tool-plugin-header.active, .tool-list-item.active');
    if (activeEl && !activeEl.closest('.filtered-hidden')) return;

    if (pane.id === 'panePlugins') {
        var firstGroup = pane.querySelector('.tool-plugin-group:not(.filtered-hidden)');
        if (firstGroup) selectPlugin(firstGroup.querySelector('.tool-plugin-header'));
    } else {
        var firstItem = pane.querySelector('.tool-list-item:not(.filtered-hidden)');
        if (firstItem) selectToolSet(firstItem);
    }
}

/** 启用 / 禁用插件 */
function togglePlugin(btn) {
    var pluginId = btn.getAttribute('data-plugin-id');
    var enabled = btn.getAttribute('data-enabled') === 'true';
    var next = !enabled;
    if (!pluginId) {
        showToast('无法获取插件标识', 'error');
        return;
    }

    var originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = next ? '启用中...' : '禁用中...';

    fetch('/plugins/api/toggle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'pluginId=' + encodeURIComponent(pluginId) + '&enabled=' + next
    })
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.code === 200) {
                showToast(next
                    ? '插件已启用，其工具集与前端组件已恢复可用'
                    : '插件已禁用，已绑定该插件的智能体将无法使用其工具集', 'success');
                setTimeout(function () { location.reload(); }, 900);
            } else {
                showToast(resp.msg || '操作失败', 'error');
                btn.disabled = false;
                btn.textContent = originalText;
            }
        })
        .catch(function () {
            showToast('请求失败', 'error');
            btn.disabled = false;
            btn.textContent = originalText;
        });
}

/** 卸载插件（先查配置项，决定是否提示"同时清理配置"） */
function uninstallPlugin(btn) {
    var pluginId = btn.getAttribute('data-plugin-id');
    var pluginName = btn.getAttribute('data-plugin-name') || pluginId;
    var pluginVersion = btn.getAttribute('data-plugin-version') || '';
    if (!pluginId) {
        showToast('无法获取插件标识', 'error');
        return;
    }

    var originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = '检查中...';

    fetch('/plugins/api/config-info?pluginId=' + encodeURIComponent(pluginId))
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            btn.disabled = false;
            btn.textContent = originalText;
            if (resp.code === 200 && resp.data && resp.data.hasConfig) {
                showConfirmWithCheckbox(
                    '确定要卸载插件 "' + pluginName + (pluginVersion ? ' ' + pluginVersion : '') + '" 吗？此操作不可撤销。',
                    '同时清理插件配置项（含各工具集配置）',
                    false
                ).then(function (result) {
                    if (!result.confirmed) return;
                    doUninstall(pluginId, result.checked);
                });
            } else {
                showConfirm('确定要卸载插件 "' + pluginName + (pluginVersion ? ' ' + pluginVersion : '') + '" 吗？此操作不可撤销。').then(function (confirmed) {
                    if (!confirmed) return;
                    doUninstall(pluginId, false);
                });
            }
        })
        .catch(function () {
            btn.disabled = false;
            btn.textContent = originalText;
            showConfirm('确定要卸载插件 "' + pluginName + '" 吗？此操作不可撤销。').then(function (confirmed) {
                if (!confirmed) return;
                doUninstall(pluginId, false);
            });
        });
}

function doUninstall(pluginId, cleanConfig) {
    var btn = document.getElementById('uninstallPlugin_' + pluginId);
    if (btn) {
        btn.disabled = true;
        btn.textContent = '卸载中...';
    }
    fetch('/plugins/api/unload', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'pluginId=' + encodeURIComponent(pluginId) + '&cleanConfig=' + cleanConfig
    })
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.code === 200) {
                showToast(cleanConfig ? '插件卸载成功，配置已清理' : '插件卸载成功', 'success');
                setTimeout(function () { location.reload(); }, 800);
            } else {
                showToast(resp.msg || '卸载失败', 'error');
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = '卸载';
                }
            }
        })
        .catch(function () {
            showToast('请求失败', 'error');
            if (btn) {
                btn.disabled = false;
                btn.textContent = '卸载';
            }
        });
}

/** 导出插件包（按 pluginId 导出整个插件：JAR + 清单 + 资产） */
function exportPlugin(btn) {
    var pluginId = btn.getAttribute('data-plugin-id');
    if (!pluginId) {
        showToast('无法获取插件标识', 'error');
        return;
    }
    window.location.href = '/plugins/api/export/' + encodeURIComponent(pluginId);
}

/** 从本地安装插件包（.jar / .zip） */
function localInstallPlugin(input) {
    if (!input.files || input.files.length === 0) return;
    var file = input.files[0];
    var fileName = file.name.toLowerCase();

    if (fileName.indexOf('.zip') === -1 && fileName.indexOf('.jar') === -1) {
        showToast('仅支持 .zip 或 .jar 格式的插件包', 'error');
        input.value = '';
        return;
    }

    showToast('正在安装插件...', 'info');

    var formData = new FormData();
    formData.append('file', file);

    fetch('/plugins/api/local-install', {
        method: 'POST',
        body: formData
    })
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.code === 200) {
                var result = resp.data;
                var pluginId = result.pluginId || result.fileName || '插件';
                if (result.upgrade) {
                    showToast('插件 ' + pluginId + ' 更新成功 (v' + result.previousVersion + ' → v' + result.version + ')，提供 ' + result.toolCount + ' 个工具集', 'success');
                } else {
                    showToast('插件 ' + pluginId + ' 安装成功 v' + result.version + '，提供 ' + result.toolCount + ' 个工具集', 'success');
                }
                setTimeout(function () { location.reload(); }, 1000);
            } else {
                showToast(resp.msg || '安装失败', 'error');
            }
        })
        .catch(function () {
            showToast('请求失败', 'error');
        })
        .finally(function () {
            input.value = '';
        });
}

/** 页面初始化：无插件时自动落到「内置工具」Tab，有插件时高亮首个插件 */
(function initToolsPage() {
    var hasPlugins = document.querySelectorAll('#panePlugins .tool-plugin-group').length > 0;

    if (!hasPlugins) {
        switchToolTab('builtin');
        var firstBuiltin = document.querySelector('#paneBuiltin .tool-list-item');
        if (firstBuiltin) selectToolSet(firstBuiltin);
    } else {
        var firstHeader = document.querySelector('#panePlugins .tool-plugin-header');
        if (firstHeader) selectPlugin(firstHeader);
    }
})();
