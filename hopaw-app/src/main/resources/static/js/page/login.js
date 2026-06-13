(function () {
    'use strict';

    var searchInput = document.getElementById('loginSearch');
    var accountList = document.getElementById('loginAccountList');
    var card = document.querySelector('.login-card');
    var pendingUserId = null;
    var sliding = false;

    if (!accountList || !card) return;

    // 搜索过滤
    if (searchInput) {
        searchInput.addEventListener('input', function () {
            var keyword = this.value.trim().toLowerCase();
            var items = accountList.querySelectorAll('.login-account');
            items.forEach(function (el) {
                var haystack = [
                    el.dataset.userId || '',
                    el.dataset.username || '',
                    el.dataset.nickname || ''
                ].join(' ').toLowerCase();
                el.style.display = !keyword || haystack.indexOf(keyword) >= 0 ? '' : 'none';
            });
            var visible = accountList.querySelectorAll('.login-account:not([style*="display: none"])');
            var empty = accountList.querySelector('.login-empty');
            if (empty) {
                empty.style.display = visible.length === 0 ? '' : 'none';
            }
        });
    }

    // 点击登录
    accountList.addEventListener('click', function (e) {
        var item = e.target.closest('.login-account');
        if (!item) return;
        if (item.classList.contains('disabled')) {
            showToast('该账户已被禁用，无法登录', 'warning');
            return;
        }
        var userId = item.dataset.userId;
        if (!userId) return;

        // 检查是否启用了密码
        var passwordEnabled = item.dataset.passwordEnabled === 'true';
        if (passwordEnabled) {
            pendingUserId = userId;
            var displayName = item.dataset.nickname || item.dataset.username || userId;
            var avatarText = item.querySelector('.login-avatar').textContent || displayName.charAt(0).toUpperCase();
            slideToPwdPanel(userId, displayName, avatarText);
            return;
        }

        // 无密码直接登录
        doLogin(userId);
    });

    function doLogin(userId, password) {
        var body = { userId: userId };
        if (password) {
            body.password = password;
        }
        fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(function (r) { return r.json(); })
            .then(function (resp) {
                if (resp && resp.code === 200) {
                    showLoading();
                    var url = new URL(window.location.href);
                    var redirect = url.searchParams.get('redirect');
                    window.location.href = redirect && redirect.indexOf('/login') < 0 ? redirect : '/';
                } else if (resp && resp.msg === 'password_required') {
                    pendingUserId = userId;
                    slideToPwdPanel(userId, userId, userId.charAt(0).toUpperCase());
                } else if (resp && resp.msg === '密码错误') {
                    showPwdError('密码错误，请重新输入');
                } else {
                    showToast((resp && resp.msg) || '登录失败', 'error');
                }
            })
            .catch(function (err) {
                showToast('登录失败：' + (err && err.message ? err.message : '网络异常'), 'error');
            });
    }

    // ===== 滑动切换 =====
    function slideToPwdPanel(userId, displayName, avatarText) {
        if (sliding) return;
        sliding = true;

        // 填充密码面板内容
        document.getElementById('loginPwdAvatar').textContent = avatarText;
        document.getElementById('loginPwdName').textContent = displayName;
        document.getElementById('loginPwdInput').value = '';
        document.getElementById('loginPwdError').style.display = 'none';

        // 固定 card 当前高度，防止动画期间高度跳变
        card.style.height = card.offsetHeight + 'px';

        card.classList.remove('slide-to-accounts');
        document.getElementById('loginPanelPwd').style.display = 'block';
        card.classList.add('slide-to-pwd');

        setTimeout(function () {
            document.getElementById('loginPanelAccounts').style.display = 'none';
            card.classList.remove('slide-to-pwd');
            // 解除固定高度，让 card 自适应密码面板内容
            card.style.height = '';
            sliding = false;
            document.getElementById('loginPwdInput').focus();
        }, 320);
    }

    window.slideBackToAccounts = function () {
        if (sliding) return;
        sliding = true;
        pendingUserId = null;

        // 固定 card 当前高度
        card.style.height = card.offsetHeight + 'px';

        document.getElementById('loginPanelAccounts').style.display = 'block';
        card.classList.add('slide-to-accounts');

        setTimeout(function () {
            document.getElementById('loginPanelPwd').style.display = 'none';
            card.classList.remove('slide-to-accounts');
            card.style.height = '';
            sliding = false;
        }, 320);
    };

    function showPwdError(msg) {
        var error = document.getElementById('loginPwdError');
        error.textContent = msg;
        error.style.display = '';
        var input = document.getElementById('loginPwdInput');
        input.value = '';
        input.focus();
    }

    window.submitLoginPwd = function () {
        if (!pendingUserId) return;
        var password = document.getElementById('loginPwdInput').value;
        if (!password || password.trim() === '') {
            showPwdError('请输入密码');
            return;
        }
        var btn = document.getElementById('loginPwdSubmit');
        btn.disabled = true;
        doLogin(pendingUserId, password);
        setTimeout(function () { btn.disabled = false; }, 1500);
    };

    // 回车提交密码
    document.getElementById('loginPwdInput').addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            window.submitLoginPwd();
        }
    });

    function showLoading() {
        var overlay = document.createElement('div');
        overlay.className = 'login-loading-overlay';
        overlay.innerHTML = '<div class="login-loading-spinner"></div><div class="login-loading-text">正在进入…</div>';
        document.body.appendChild(overlay);
    }

    function showToast(msg, type) {
        if (window.Toast && typeof window.Toast.show === 'function') {
            window.Toast.show(msg, type || 'info');
            return;
        }
        var el = document.createElement('div');
        el.className = 'login-toast login-toast-' + (type || 'info');
        el.textContent = msg;
        document.body.appendChild(el);
        setTimeout(function () { el.classList.add('show'); }, 10);
        setTimeout(function () {
            el.classList.remove('show');
            setTimeout(function () { el.remove(); }, 300);
        }, 2400);
    }
})();
