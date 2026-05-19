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

function uninstallPlugin(btn) {
    var jarFileName = btn.getAttribute('data-jar');
    if (!jarFileName) {
        showToast('无法获取插件文件名', 'error');
        return;
    }
    showConfirm('确定要卸载插件 "' + jarFileName + '" 吗？此操作不可撤销。').then(function(confirmed) {
        if (!confirmed) return;
        btn.disabled = true;
        btn.textContent = '卸载中...';
        fetch('/tools/api/unload', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'jarFileName=' + encodeURIComponent(jarFileName)
        })
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.code === 200) {
                showToast(resp.data || '卸载成功', 'success');
                setTimeout(function() { location.reload(); }, 800);
            } else {
                showToast(resp.msg || '卸载失败', 'error');
                btn.disabled = false;
                btn.textContent = '卸载';
            }
        })
        .catch(function() {
            showToast('请求失败', 'error');
            btn.disabled = false;
            btn.textContent = '卸载';
        });
    });
}
