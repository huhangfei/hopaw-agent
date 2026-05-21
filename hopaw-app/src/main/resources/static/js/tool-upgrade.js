var toolName = '';
var currentVersion = '';
var jarFileName = '';
var updateInfo = null;

function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
    return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
}

function showLoading(text) {
    document.getElementById('upgradeLoading').style.display = 'flex';
    document.getElementById('upgradeLoading').querySelector('#loadingText').textContent = text || '处理中...';
    document.getElementById('upgradeResult').style.display = 'none';
    document.getElementById('upgradeError').style.display = 'none';
}

function hideLoading() {
    document.getElementById('upgradeLoading').style.display = 'none';
}

function showError(message) {
    hideLoading();
    document.getElementById('upgradeError').style.display = 'block';
    document.getElementById('upgradeError').querySelector('#errorText').textContent = message;
}

function showResult() {
    hideLoading();
    document.getElementById('upgradeResult').style.display = 'block';
    document.getElementById('upgradeError').style.display = 'none';
}

function checkUpdate() {
    var checkUrl = document.getElementById('checkUrl').value.trim();

    toolName = document.querySelector('.info-value').textContent;
    currentVersion = document.getElementById('currentVersion').textContent;
    jarFileName = document.getElementById('jarFileName').textContent;
    jarFileName = jarFileName === '-' ? '' : jarFileName;

    if (!checkUrl) {
        showError('请输入检测接口地址');
        return;
    }

    if (!toolName) {
        showError('无法获取工具名称');
        return;
    }

    var btnCheck = document.getElementById('btnCheck');
    btnCheck.disabled = true;
    btnCheck.textContent = '检测中...';
    showLoading('正在检测...');

    fetch('/tools/api/check-update', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: 'checkUrl=' + encodeURIComponent(checkUrl) +
              '&toolName=' + encodeURIComponent(toolName) +
              '&currentVersion=' + encodeURIComponent(currentVersion) +
              '&jarFileName=' + encodeURIComponent(jarFileName)
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        btnCheck.disabled = false;
        btnCheck.textContent = '检测';

        if (resp.code === 200) {
            updateInfo = resp.data;
            displayUpdateResult(updateInfo);
        } else {
            showError(resp.msg || '检测失败');
        }
    })
    .catch(function(err) {
        btnCheck.disabled = false;
        btnCheck.textContent = '检测';
        showError('请求失败: ' + err.message);
    });
}

function displayUpdateResult(info) {
    showResult();

    var statusEl = document.getElementById('resultStatus');
    var btnInstall = document.getElementById('btnInstall');

    if (!info.version || info.version === '') {
        statusEl.className = 'result-status no-update';
        statusEl.textContent = '无可用更新';
        btnInstall.style.display = 'none';
        document.getElementById('newVersion').textContent = '未知';
        document.getElementById('fileName').textContent = '-';
        document.getElementById('fileSize').textContent = '-';
        document.getElementById('downloadUrl').textContent = '-';
        document.getElementById('sha256Hash').textContent = '-';
        return;
    }

    document.getElementById('newVersion').textContent = info.version || '未知';
    document.getElementById('fileName').textContent = info.fileName || '-';
    document.getElementById('fileSize').textContent = formatFileSize(info.fileSize);
    document.getElementById('downloadUrl').textContent = info.downloadUrl || '-';
    document.getElementById('sha256Hash').textContent = info.sha256Hash || '-';

    if (info.installed) {
        if (info.needUpgrade) {
            statusEl.className = 'result-status need-upgrade';
            statusEl.textContent = '发现新版本';
            btnInstall.className = 'btn-install upgrade';
            btnInstall.textContent = '升级';
            btnInstall.style.display = 'block';
        } else {
            statusEl.className = 'result-status up-to-date';
            statusEl.textContent = '已是最新版本';
            btnInstall.style.display = 'none';
        }
    } else {
        statusEl.className = 'result-status not-installed';
        statusEl.textContent = '未安装';
        btnInstall.className = 'btn-install install';
        btnInstall.textContent = '安装';
        btnInstall.style.display = 'block';
    }
}

function installOrUpgrade() {
    if (!updateInfo) {
        showError('请先检测更新');
        return;
    }

    if (!updateInfo.downloadUrl || updateInfo.downloadUrl === '') {
        showError('下载地址为空');
        return;
    }

    if (!updateInfo.sha256Hash || updateInfo.sha256Hash === '') {
        showError('SHA256 哈希值为空');
        return;
    }

    var btnInstall = document.getElementById('btnInstall');
    btnInstall.disabled = true;
    btnInstall.textContent = updateInfo.installed ? '升级中...' : '安装中...';

    showLoading(updateInfo.installed ? '正在升级...' : '正在安装...');

    fetch('/tools/api/install-upgrade', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(updateInfo)
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        btnInstall.disabled = false;
        btnInstall.textContent = updateInfo.installed ? '升级' : '安装';

        if (resp.code === 200) {
            showLoading('安装/升级成功！正在刷新...');
            setTimeout(function() {
                location.href = '/tools';
            }, 1500);
        } else {
            showError(resp.msg || '安装/升级失败');
        }
    })
    .catch(function(err) {
        btnInstall.disabled = false;
        btnInstall.textContent = updateInfo.installed ? '升级' : '安装';
        showError('请求失败: ' + err.message);
    });
}

document.addEventListener('DOMContentLoaded', function() {
    toolName = document.querySelector('.info-value').textContent;
    currentVersion = document.getElementById('currentVersion').textContent;
    jarFileName = document.getElementById('jarFileName').textContent;
});
