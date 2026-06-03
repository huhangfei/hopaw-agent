(function () {
    'use strict';

    const searchInput = document.getElementById('loginSearch');
    const accountList = document.getElementById('loginAccountList');

    if (!accountList) return;

    // 搜索过滤
    if (searchInput) {
        searchInput.addEventListener('input', function () {
            const keyword = this.value.trim().toLowerCase();
            const items = accountList.querySelectorAll('.login-account');
            items.forEach(function (el) {
                const haystack = [
                    el.dataset.userId || '',
                    el.dataset.username || '',
                    el.dataset.nickname || ''
                ].join(' ').toLowerCase();
                el.style.display = !keyword || haystack.indexOf(keyword) >= 0 ? '' : 'none';
            });
            const visible = accountList.querySelectorAll('.login-account:not([style*="display: none"])');
            const empty = accountList.querySelector('.login-empty');
            if (empty) {
                empty.style.display = visible.length === 0 ? '' : 'none';
            }
        });
    }

    // 点击登录
    accountList.addEventListener('click', function (e) {
        const item = e.target.closest('.login-account');
        if (!item) return;
        if (item.classList.contains('disabled')) {
            showToast('该账户已被禁用，无法登录', 'warning');
            return;
        }
        const userId = item.dataset.userId;
        if (!userId) return;

        // 防重复点击
        if (item.classList.contains('logging')) return;
        item.classList.add('logging');
        item.style.pointerEvents = 'none';

        fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId: userId })
        }).then(function (r) { return r.json(); })
            .then(function (resp) {
                if (resp && resp.code === 200) {
                    // 登录成功：展示一个 loading，然后跳回来源页
                    showLoading();
                    const url = new URL(window.location.href);
                    const redirect = url.searchParams.get('redirect');
                    window.location.href = redirect && redirect.indexOf('/login') < 0 ? redirect : '/';
                } else {
                    item.classList.remove('logging');
                    item.style.pointerEvents = '';
                    showToast((resp && resp.msg) || '登录失败', 'error');
                }
            })
            .catch(function (err) {
                item.classList.remove('logging');
                item.style.pointerEvents = '';
                showToast('登录失败：' + (err && err.message ? err.message : '网络异常'), 'error');
            });
    });

    function showLoading() {
        const overlay = document.createElement('div');
        overlay.className = 'login-loading-overlay';
        overlay.innerHTML = '<div class="login-loading-spinner"></div><div class="login-loading-text">正在进入…</div>';
        document.body.appendChild(overlay);
    }

    function showToast(msg, type) {
        if (window.Toast && typeof window.Toast.show === 'function') {
            window.Toast.show(msg, type || 'info');
            return;
        }
        // 简易降级提示
        const el = document.createElement('div');
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
