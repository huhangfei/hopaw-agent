function startBackup() {
    var btn = document.getElementById('backupBtn');
    var exportSysConfig = document.getElementById('exportSysConfig').checked;
    var exportModelConfig = document.getElementById('exportModelConfig').checked;
    var exportAgentConfig = document.getElementById('exportAgentConfig').checked;
    var exportTtsConfig = document.getElementById('exportTtsConfig').checked;

    if (!exportSysConfig && !exportModelConfig && !exportAgentConfig && !exportTtsConfig) {
        showToast('请至少选择一项备份内容', 'error');
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
            ttsConfig: exportTtsConfig
        })
    })
    .then(function(response) {
        return response.json().then(function(res) {
            if (!response.ok || (res.code !== undefined && res.code !== 0 && res.code !== 200)) {
                throw new Error(res.msg || '备份失败');
            }
            return res.data;
        });
    })
    .then(function(data) {
        // Base64 解码为二进制，触发浏览器下载
        var binary = atob(data.zipBase64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i);
        }
        var blob = new Blob([bytes], { type: 'application/zip' });
        var url = window.URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = data.fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);

        // 弹窗显示后端生成的密码（仅显示一次）
        document.getElementById('generatedPassword').value = data.password;
        Modal.open('passwordModal');
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

function copyGeneratedPassword() {
    var input = document.getElementById('generatedPassword');
    if (!input || !input.value) return;
    // 现代浏览器 Clipboard API
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(input.value).then(function() {
            showToast('密码已复制到剪贴板', 'success');
        }).catch(function() {
            input.select();
            document.execCommand('copy');
            showToast('密码已复制', 'success');
        });
    } else {
        // 旧浏览器回退
        input.select();
        document.execCommand('copy');
        showToast('密码已复制', 'success');
    }
}

// ========== 导入备份 ==========

function showRestoreDialog() {
    // 清空上次输入
    document.getElementById('restoreFile').value = '';
    document.getElementById('restorePassword').value = '';
    Modal.open('restoreModal');
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
        return response.json().then(function(res) {
            if (!response.ok) {
                throw new Error(res.msg || ('导入失败 (HTTP ' + response.status + ')'));
            }
            return res;
        });
    })
    .then(function(res) {
        if (res.code !== undefined && res.code !== 0 && res.code !== 200) {
            throw new Error(res.msg || '导入失败');
        }
        showToast('导入成功：' + (res.data || res.msg || ''), 'success');
        Modal.close('restoreModal');
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
