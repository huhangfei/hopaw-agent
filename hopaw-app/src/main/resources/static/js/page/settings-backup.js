document.addEventListener('DOMContentLoaded', function() {
    var usePasswordChk = document.getElementById('usePassword');
    var passwordInput = document.getElementById('backupPassword');
    if (usePasswordChk) {
        usePasswordChk.addEventListener('change', function() {
            passwordInput.style.display = this.checked ? 'block' : 'none';
            if (!this.checked) {
                passwordInput.value = '';
            }
        });
    }
});

function startBackup() {
    var btn = document.getElementById('backupBtn');
    var exportSysConfig = document.getElementById('exportSysConfig').checked;
    var exportModelConfig = document.getElementById('exportModelConfig').checked;
    var exportAgentConfig = document.getElementById('exportAgentConfig').checked;
    var usePassword = document.getElementById('usePassword').checked;
    var password = usePassword ? document.getElementById('backupPassword').value : '';

    if (!exportSysConfig && !exportModelConfig && !exportAgentConfig) {
        showToast('请至少选择一项备份内容', 'error');
        return;
    }

    if (usePassword && !password) {
        showToast('已勾选设置解压密码，但密码为空', 'error');
        return;
    }

    btn.disabled = true;
    btn.textContent = '备份中...';

    fetch('/api/backup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            sysConfig: exportSysConfig,
            modelConfig: exportModelConfig,
            agentConfig: exportAgentConfig,
            password: password
        })
    })
    .then(function(response) {
        if (!response.ok) {
            throw new Error('备份失败');
        }
        return response.blob();
    })
    .then(function(blob) {
        var url = window.URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'hopaw_backup_' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + '.zip';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        showToast('备份成功', 'success');
    })
    .catch(function(error) {
        console.error('备份失败:', error);
        showToast('备份失败: ' + error.message, 'error');
    })
    .finally(function() {
        btn.disabled = false;
        btn.textContent = '备份数据';
    });
}

// ========== 导入备份 ==========

function showRestoreDialog() {
    var dialog = document.getElementById('restoreDialog');
    dialog.style.display = 'flex';
    // 清空上次输入
    document.getElementById('restoreFile').value = '';
    document.getElementById('restorePassword').value = '';
}

function closeRestoreDialog() {
    document.getElementById('restoreDialog').style.display = 'none';
}

function confirmRestore() {
    var fileInput = document.getElementById('restoreFile');
    var passwordInput = document.getElementById('restorePassword');
    var btn = document.getElementById('confirmRestoreBtn');

    if (!fileInput.files || fileInput.files.length === 0) {
        showToast('请选择备份文件', 'error');
        return;
    }
    var file = fileInput.files[0];
    var fileName = file.name || '';
    if (!fileName.toLowerCase().endsWith('.zip')) {
        showToast('仅支持 .zip 备份文件', 'error');
        return;
    }

    btn.disabled = true;
    btn.textContent = '导入中...';

    var formData = new FormData();
    formData.append('file', file);
    formData.append('password', passwordInput.value);

    fetch('/api/backup/restore', {
        method: 'POST',
        body: formData
    })
    .then(function(response) {
        if (!response.ok) {
            return response.json().then(function(err) {
                throw new Error(err.message || ('导入失败 (HTTP ' + response.status + ')'));
            });
        }
        return response.json();
    })
    .then(function(res) {
        if (res.code !== undefined && res.code !== 0 && res.code !== 200) {
            throw new Error(res.message || '导入失败');
        }
        showToast('导入成功：' + (res.data || res.message || ''), 'success');
        closeRestoreDialog();
    })
    .catch(function(error) {
        console.error('导入失败:', error);
        showToast('导入失败: ' + error.message, 'error');
    })
    .finally(function() {
        btn.disabled = false;
        btn.textContent = '导入';
    });
}
