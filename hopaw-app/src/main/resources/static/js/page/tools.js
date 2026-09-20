// 工具管理页（智能体能力二级）
//
// 左：全部工具集平铺列表；右：工具集详情（含工具集级 + 方法级禁用开关）
// 工具集级/方法级开关通过 /api/tool-state 持久化

/** 选中工具集：显示对应详情 */
function selectToolSet(el) {
    if (!el) return;
    var toolSetName = el.getAttribute('data-tool-name');
    if (!toolSetName) return;

    document.querySelectorAll('.tool-list-item').forEach(function (i) {
        i.classList.remove('active');
    });
    el.classList.add('active');

    var target = null;
    document.querySelectorAll('.tool-detail').forEach(function (d) {
        d.classList.remove('active');
        if (target === null && d.getAttribute('data-tool-name') === toolSetName) target = d;
    });
    if (target) {
        target.classList.add('active');
        var panel = document.querySelector('.tools-detail-panel');
        if (panel) panel.scrollTop = 0;
    }
}

/** 搜索过滤工具集列表 */
function filterTools() {
    var input = document.getElementById('toolsSearchInput');
    var query = input ? (input.value || '').toLowerCase().trim() : '';

    document.querySelectorAll('#toolsListBody .tool-list-item').forEach(function (item) {
        var text = (item.textContent || '').toLowerCase();
        var matched = query === '' || text.indexOf(query) !== -1;
        if (matched) {
            item.classList.remove('filtered-hidden');
        } else {
            item.classList.add('filtered-hidden');
        }
    });

    // 若当前选中项被隐藏，自动选中第一个可见项
    var active = document.querySelector('#toolsListBody .tool-list-item.active');
    if (active && active.classList.contains('filtered-hidden')) {
        var first = document.querySelector('#toolsListBody .tool-list-item:not(.filtered-hidden)');
        if (first) selectToolSet(first);
    }
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
                // 同步列表项与详情标题的禁用徽标
                var listItem = document.querySelector('#toolsListBody .tool-list-item[data-tool-name="' + cssEscape(toolSetName) + '"]');
                if (listItem) {
                    if (enabled) {
                        listItem.classList.remove('is-disabled');
                        var b1 = listItem.querySelector('.tool-list-name .badge-disabled');
                        if (b1) b1.remove();
                    } else {
                        listItem.classList.add('is-disabled');
                        var nameWrap = listItem.querySelector('.tool-list-name');
                        if (nameWrap && !nameWrap.querySelector('.badge-disabled')) {
                            var span = document.createElement('span');
                            span.className = 'tool-list-badge badge-disabled';
                            span.textContent = '已禁用';
                            nameWrap.appendChild(span);
                        }
                    }
                }
                var detail = document.querySelector('.tool-detail[data-tool-name="' + cssEscape(toolSetName) + '"]');
                if (detail) {
                    var detailBadge = detail.querySelector('.detail-name .badge-disabled');
                    if (!enabled) {
                        if (!detailBadge) {
                            var dn = detail.querySelector('.detail-name');
                            if (dn) {
                                var s = document.createElement('span');
                                s.className = 'tool-list-badge badge-disabled';
                                s.textContent = '已禁用';
                                dn.appendChild(s);
                            }
                        }
                    } else if (detailBadge) {
                        detailBadge.remove();
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
                var row = input.closest('.detail-tool-item');
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

/** 转义 CSS 选择器中的特殊字符（用于 data-* 属性值精确匹配） */
function cssEscape(str) {
    if (typeof CSS !== 'undefined' && CSS.escape) return CSS.escape(str);
    return str.replace(/([^\w-])/g, '\\$1');
}

/** 页面初始化：高亮首个工具集 */
(function initToolsPage() {
    var firstItem = document.querySelector('#toolsListBody .tool-list-item');
    if (firstItem) {
        selectToolSet(firstItem);
    }
})();
