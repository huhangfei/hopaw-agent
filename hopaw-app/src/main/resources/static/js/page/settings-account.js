function onAccountPwdToggle() {
    var enabled = document.getElementById('accountPasswordEnabled').checked;
    document.getElementById('accountPwdEnabledLabel').textContent = enabled ? '已启用' : '未启用';
    document.getElementById('accountPwdGroup').style.display = enabled ? '' : 'none';
}

function saveAccountInfo() {
    var username = document.getElementById('accountUsername').value.trim();
    var nickname = document.getElementById('accountNickname').value.trim();

    if (!username) {
        showToast('用户名称不能为空', 'error');
        return;
    }

    fetch('/api/settings/account', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username, nickname: nickname })
    })
    .then(function (r) { return r.json(); })
    .then(function (resp) {
        if (resp.code === 200) {
            showToast('保存成功', 'success');
        } else {
            showToast(resp.msg || '保存失败', 'error');
        }
    })
    .catch(function () {
        showToast('保存失败', 'error');
    });
}

function saveAccountPassword() {
    var enabled = document.getElementById('accountPasswordEnabled').checked;
    var password = document.getElementById('accountPassword').value;

    if (enabled) {
        if (!password || password.trim() === '') {
            showToast('启用密码时必须设置密码', 'error');
            return;
        }
        if (password.length < 4) {
            showToast('密码长度不能少于4位', 'error');
            return;
        }
    }

    fetch('/api/settings/account/password', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            passwordEnabled: enabled ? 1 : 0,
            password: enabled ? password : ''
        })
    })
    .then(function (r) { return r.json(); })
    .then(function (resp) {
        if (resp.code === 200) {
            showToast(enabled ? '密码已启用' : '密码已禁用', 'success');
            document.getElementById('accountPassword').value = '';
        } else {
            showToast(resp.msg || '保存失败', 'error');
        }
    })
    .catch(function () {
        showToast('保存失败', 'error');
    });
}
