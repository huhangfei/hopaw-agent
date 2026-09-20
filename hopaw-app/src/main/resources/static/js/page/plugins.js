// 插件管理页（运维管理一级）
//
// 左：插件平铺列表；右：插件详情 + 工具集手风琴（默认收起）
// 插件级：启停 / 卸载 / 导出 / 本地安装 / 商店安装
// 工具集级 + 方法级：手风琴内开关，通过 /api/tool-state 持久化

/** 选中插件：显示对应详情 */
function selectPlugin(el) {
    if (!el) return;
    var pluginId = el.getAttribute('data-plugin-id');
    if (!pluginId) return;

    document.querySelectorAll('.plugin-list-item').forEach(function (i) {
        i.classList.remove('active');
    });
    el.classList.add('active');

    var target = null;
    document.querySelectorAll('.plugin-detail').forEach(function (d) {
        d.classList.remove('active');
        if (target === null && d.getAttribute('data-plugin-id') === pluginId) target = d;
    });
    if (target) {
        target.classList.add('active');
        var panel = document.querySelector('.plugins-detail-panel');
        if (panel) panel.scrollTop = 0;
    }
}

/** 搜索过滤插件列表 */
function filterPlugins() {
    var input = document.getElementById('pluginsSearchInput');
    var query = input ? (input.value || '').toLowerCase().trim() : '';

    document.querySelectorAll('.plugin-list-item').forEach(function (item) {
        var text = (item.textContent || '').toLowerCase();
        var matched = query === '' || text.indexOf(query) !== -1;
        if (matched) {
            item.classList.remove('filtered-hidden');
        } else {
            item.classList.add('filtered-hidden');
        }
    });

    // 若当前选中项被隐藏，自动选中第一个可见项
    var active = document.querySelector('.plugin-list-item.active');
    if (active && active.classList.contains('filtered-hidden')) {
        var first = document.querySelector('.plugin-list-item:not(.filtered-hidden)');
        if (first) selectPlugin(first);
    }
}

/** 展开 / 收起手风琴项 */
function toggleAccordion(header) {
    if (!header || header.classList.contains('accordion-header-static')) return;
    var item = header.closest('.accordion-item');
    if (item) item.classList.toggle('open');
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

/** 工具集级启用/禁用开关 */
function toggleToolSet(input) {
    var toolSetName = input.getAttribute('data-tool-set-name');
    if (!toolSetName) {
        showToast('无法获取工具集标识', 'error');
        return;
    }
    var enabled = input.checked;
    input.disabled = true;

    fetch('/api/tool-state/toolset', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'toolSetName=' + encodeURIComponent(toolSetName) + '&enabled=' + enabled
    })
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            input.disabled = false;
            if (resp.code === 200) {
                showToast(enabled ? '工具集已启用' : '工具集已禁用，智能体将无法选择该工具集', 'success');
                // 同步徽标与行样式，无需整页刷新
                var item = input.closest('.accordion-item');
                if (item) {
                    if (enabled) {
                        item.classList.remove('is-toolset-disabled');
                    } else {
                        item.classList.add('is-toolset-disabled');
                    }
                    var badge = item.querySelector('.accordion-header-name .badge-disabled');
                    if (!enabled) {
                        if (!badge) {
                            var nameWrap = item.querySelector('.accordion-header-name');
                            if (nameWrap) {
                                var span = document.createElement('span');
                                span.className = 'plugin-list-badge badge-disabled';
                                span.textContent = '工具集已禁用';
                                nameWrap.appendChild(span);
                            }
                        }
                    } else if (badge) {
                        badge.remove();
                    }
                }
            } else {
                showToast(resp.msg || '操作失败', 'error');
                input.checked = !enabled;
            }
        })
        .catch(function () {
            input.disabled = false;
            showToast('请求失败', 'error');
            input.checked = !enabled;
        });
}

/** 工具方法级启用/禁用开关 */
function toggleTool(input) {
    var toolSetName = input.getAttribute('data-tool-set-name');
    var toolName = input.getAttribute('data-tool-name');
    if (!toolSetName || !toolName) {
        showToast('无法获取工具方法标识', 'error');
        return;
    }
    var enabled = input.checked;
    input.disabled = true;

    fetch('/api/tool-state/tool', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'toolSetName=' + encodeURIComponent(toolSetName) + '&toolName=' + encodeURIComponent(toolName) + '&enabled=' + enabled
    })
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            input.disabled = false;
            if (resp.code === 200) {
                showToast(enabled ? '工具方法已启用' : '工具方法已禁用', 'success');
                var row = input.closest('.accordion-method-row');
                if (row) {
                    if (enabled) {
                        row.classList.remove('is-method-disabled');
                    } else {
                        row.classList.add('is-method-disabled');
                    }
                }
            } else {
                showToast(resp.msg || '操作失败', 'error');
                input.checked = !enabled;
            }
        })
        .catch(function () {
            input.disabled = false;
            showToast('请求失败', 'error');
            input.checked = !enabled;
        });
}

/** 页面初始化：无插件时显示空态，有插件时高亮首个插件 */
(function initPluginsPage() {
    var firstItem = document.querySelector('.plugin-list-item');
    if (firstItem) {
        selectPlugin(firstItem);
    }
})();
