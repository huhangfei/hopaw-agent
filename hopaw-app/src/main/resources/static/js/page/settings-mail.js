function onSettingsLoaded() {
    document.getElementById('mailHost').value = settingsCache['mail_host'] || '';
    document.getElementById('mailPort').value = settingsCache['mail_port'] || '';
    document.getElementById('mailUsername').value = settingsCache['mail_username'] || '';
    document.getElementById('mailPassword').value = settingsCache['mail_password'] || '';
    document.getElementById('mailFrom').value = settingsCache['mail_from'] || '';
}

function saveMailSettings() {
    var saves = [];
    saves.push(saveConfig('mail_host', document.getElementById('mailHost').value.trim(), 'SMTP 服务器'));
    saves.push(saveConfig('mail_port', document.getElementById('mailPort').value.trim(), 'SMTP 端口'));
    saves.push(saveConfig('mail_username', document.getElementById('mailUsername').value.trim(), 'SMTP 用户名'));
    saves.push(saveConfig('mail_password', document.getElementById('mailPassword').value.trim(), 'SMTP 密码'));
    saves.push(saveConfig('mail_from', document.getElementById('mailFrom').value.trim(), '发件人地址'));

    Promise.all(saves).then(function(results) {
        var allOk = results.every(function(r) { return r; });
        if (allOk) {
            showToast('邮件配置保存成功', 'success');
        } else {
            showToast('部分配置保存失败', 'error');
        }
    });
}

function testMailConfig() {
    var btn = document.querySelector('.btn-test-mail');
    btn.disabled = true;
    btn.textContent = '测试中...';

    fetch('/api/mail/test', { method: 'POST' })
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg === 'success') {
                showToast('连接成功', 'success');
            } else {
                showToast('连接失败: ' + (resp.data || resp.msg), 'error');
            }
        })
        .catch(function() {
            showToast('请求失败', 'error');
        })
        .finally(function() {
            btn.disabled = false;
            btn.textContent = '测试连接';
        });
}