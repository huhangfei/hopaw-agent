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
        fileName: v.jarFileName || (info.plugin.name + '.jar'),
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

        if (resp.code === 200 && resp.data) {
            var result = resp.data;
            if (result.conflictInfo && result.conflictInfo.conflictingPlugins || result.conflictInfo && result.conflictInfo.conflictingTools) {
                var conflictMsg = result.message || '';
                showToast(conflictMsg, 'warning');
            } else {
                showToast(result.message || (result.isUpgrade ? '更新成功' : '安装成功'), 'success');
            }
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
