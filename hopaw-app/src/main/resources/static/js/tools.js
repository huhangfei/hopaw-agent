// Tool management page
function selectToolSet(el) {
    var index = el.getAttribute('data-index');

    // left panel: remove active from all, add to clicked
    var items = document.querySelectorAll('.tool-list-item');
    items.forEach(function(item) { item.classList.remove('active'); });
    el.classList.add('active');

    // right panel: hide all details, show selected
    var details = document.querySelectorAll('.tool-detail');
    details.forEach(function(d) { d.classList.remove('active'); });
    var target = document.querySelector('.tool-detail[data-index="' + index + '"]');
    if (target) target.classList.add('active');
}

function filterTools() {
    var query = document.getElementById('toolsSearchInput').value.toLowerCase().trim();
    var items = document.querySelectorAll('.tool-list-item');

    if (query === '') {
        items.forEach(function(item) {
            item.classList.remove('filtered-hidden');
        });
        return;
    }

    items.forEach(function(item) {
        var name = (item.querySelector('.tool-list-name') || item.querySelector('.detail-name') || {}).textContent || '';
        var desc = (item.querySelector('.tool-list-desc') || {}).textContent || '';
        name = name.toLowerCase();
        desc = desc.toLowerCase();

        if (name.indexOf(query) !== -1 || desc.indexOf(query) !== -1) {
            item.classList.remove('filtered-hidden');
        } else {
            item.classList.add('filtered-hidden');
        }
    });

    // auto-select first visible item
    var visibleItems = document.querySelectorAll('.tool-list-item:not(.filtered-hidden)');
    if (visibleItems.length > 0) {
        var firstVisible = visibleItems[0];
        document.querySelectorAll('.tool-list-item').forEach(function(i) { i.classList.remove('active'); });
        document.querySelectorAll('.tool-detail').forEach(function(d) { d.classList.remove('active'); });
        firstVisible.classList.add('active');
        var idx = firstVisible.getAttribute('data-index');
        var detail = document.querySelector('.tool-detail[data-index="' + idx + '"]');
        if (detail) detail.classList.add('active');
    }
}

function uninstallPlugin(btn) {
    var jarFileName = btn.getAttribute('data-jar');
    if (!jarFileName) {
        showToast('无法获取插件文件名', 'error');
        return;
    }
    btn.disabled = true;
    btn.textContent = '检查中...';

    fetch('/tools/api/plugin-config-info?jarFileName=' + encodeURIComponent(jarFileName))
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            btn.disabled = false;
            btn.textContent = '卸载';
            if (resp.code === 200 && resp.data && resp.data.hasConfig) {
                showConfirmWithCheckbox(
                    '确定要卸载插件 "' + jarFileName + '" 吗？此操作不可撤销。',
                    '同时清理插件配置项',
                    false
                ).then(function(result) {
                    if (!result.confirmed) return;
                    doUninstall(jarFileName, result.checked);
                });
            } else {
                showConfirm('确定要卸载插件 "' + jarFileName + '" 吗？此操作不可撤销。').then(function(confirmed) {
                    if (!confirmed) return;
                    doUninstall(jarFileName, false);
                });
            }
        })
        .catch(function() {
            btn.disabled = false;
            btn.textContent = '卸载';
            showConfirm('确定要卸载插件 "' + jarFileName + '" 吗？此操作不可撤销。').then(function(confirmed) {
                if (!confirmed) return;
                doUninstall(jarFileName, false);
            });
        });
}

function doUninstall(jarFileName, cleanConfig) {
    var btn = document.querySelector('.btn-unload-plugin[data-jar="' + jarFileName + '"]');
    if (btn) {
        btn.disabled = true;
        btn.textContent = '卸载中...';
    }
    fetch('/tools/api/unload', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'jarFileName=' + encodeURIComponent(jarFileName) + '&cleanConfig=' + cleanConfig
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.code === 200) {
            showToast(cleanConfig ? '插件卸载成功，配置已清理' : '插件卸载成功', 'success');
            setTimeout(function() { location.reload(); }, 800);
        } else {
            showToast(resp.msg || '卸载失败', 'error');
            if (btn) {
                btn.disabled = false;
                btn.textContent = '卸载';
            }
        }
    })
    .catch(function() {
        showToast('请求失败', 'error');
        if (btn) {
            btn.disabled = false;
            btn.textContent = '卸载';
        }
    });
}

function exportPlugin(btn) {
    var jarFileName = btn.getAttribute('data-jar');
    if (!jarFileName) {
        showToast('无法获取插件文件名', 'error');
        return;
    }
    window.location.href = '/tools/api/export/' + encodeURIComponent(jarFileName);
}
