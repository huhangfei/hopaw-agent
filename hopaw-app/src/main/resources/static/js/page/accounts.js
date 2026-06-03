document.addEventListener('DOMContentLoaded', function () {
    loadAccounts();
    setupStatusToggle();
});

function setupStatusToggle() {
    var checkbox = document.getElementById('accountStatus');
    checkbox.addEventListener('change', function () {
        document.getElementById('accountStatusLabel').textContent = this.checked ? '启用' : '禁用';
    });
}

function loadAccounts() {
    fetch('/api/accounts')
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.code !== 200) {
                showToast(resp.msg || '加载失败', 'error');
                return;
            }
            renderAccounts(resp.data || []);
        })
        .catch(function () {
            showToast('请求失败', 'error');
        });
}

function renderAccounts(accounts) {
    var tbody = document.getElementById('accountsTableBody');
    if (!accounts || accounts.length === 0) {
        tbody.innerHTML = '';
        document.getElementById('emptyState').style.display = 'block';
        return;
    }
    document.getElementById('emptyState').style.display = 'none';
    tbody.innerHTML = accounts.map(function (a) {
        var statusHtml = a.status === 1
            ? '<span class="account-status enabled">启用</span>'
            : '<span class="account-status disabled">禁用</span>';
        return '<tr>' +
            '<td><strong>' + escapeHtml(a.userId) + '</strong></td>' +
            '<td>' + escapeHtml(a.username || '') + '</td>' +
            '<td>' + escapeHtml(a.nickname || '-') + '</td>' +
            '<td>' + statusHtml + '</td>' +
            '<td title="' + escapeAttr(a.remark || '') + '">' + escapeHtml(truncate(a.remark || '-', 30)) + '</td>' +
            '<td>' + escapeHtml(a.createTime || '-') + '</td>' +
            '<td>' + escapeHtml(a.updateTime || '-') + '</td>' +
            '<td class="account-actions">' +
            '  <button class="btn-icon btn-view" onclick="viewAccount(' + a.id + ')" title="查看">' +
            '    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>' +
            '  </button>' +
            '  <button class="btn-icon btn-edit" onclick="showEditModal(' + a.id + ')" title="编辑">' +
            '    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>' +
            '  </button>' +
            '  <button class="btn-icon btn-delete" onclick="deleteAccount(' + a.id + ', \'' + escapeAttr(a.userId) + '\')" title="删除">' +
            '    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' +
            '  </button>' +
            '</td>' +
            '</tr>';
    }).join('');
}

function showAddModal() {
    document.getElementById('modalTitle').textContent = '新增账户';
    document.getElementById('accountId').value = '';
    document.getElementById('accountUserId').value = '';
    document.getElementById('accountUserId').disabled = false;
    document.getElementById('accountUsername').value = '';
    document.getElementById('accountNickname').value = '';
    document.getElementById('accountStatus').checked = true;
    document.getElementById('accountStatusLabel').textContent = '启用';
    document.getElementById('accountRemark').value = '';
    Modal.open('accountModal');
}

function showEditModal(id) {
    fetch('/api/accounts/' + id)
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.code !== 200 || !resp.data) {
                showToast(resp.msg || '加载账户失败', 'error');
                return;
            }
            var a = resp.data;
            document.getElementById('modalTitle').textContent = '编辑账户';
            document.getElementById('accountId').value = a.id;
            document.getElementById('accountUserId').value = a.userId || '';
            // 默认账户 user1 不可修改用户编号
            document.getElementById('accountUserId').disabled = a.userId === 'user1';
            document.getElementById('accountUsername').value = a.username || '';
            document.getElementById('accountNickname').value = a.nickname || '';
            document.getElementById('accountStatus').checked = a.status === 1;
            document.getElementById('accountStatusLabel').textContent = a.status === 1 ? '启用' : '禁用';
            document.getElementById('accountRemark').value = a.remark || '';
            Modal.open('accountModal');
        })
        .catch(function () {
            showToast('加载账户失败', 'error');
        });
}

function viewAccount(id) {
    fetch('/api/accounts/' + id)
        .then(function (r) { return r.json(); })
        .then(function (resp) {
            if (resp.code !== 200 || !resp.data) {
                showToast(resp.msg || '加载账户失败', 'error');
                return;
            }
            var a = resp.data;
            document.getElementById('viewUserId').textContent = a.userId || '-';
            document.getElementById('viewUsername').textContent = a.username || '-';
            document.getElementById('viewNickname').textContent = a.nickname || '-';
            document.getElementById('viewStatus').innerHTML = a.status === 1
                ? '<span class="account-status enabled">启用</span>'
                : '<span class="account-status disabled">禁用</span>';
            document.getElementById('viewRemark').textContent = a.remark || '-';
            document.getElementById('viewCreateTime').textContent = a.createTime || '-';
            document.getElementById('viewUpdateTime').textContent = a.updateTime || '-';
            Modal.open('accountViewModal');
        })
        .catch(function () {
            showToast('加载账户失败', 'error');
        });
}

function closeModal() {
    Modal.close('accountModal');
}

function submitAccount() {
    var id = document.getElementById('accountId').value;
    var userId = document.getElementById('accountUserId').value.trim();
    var username = document.getElementById('accountUsername').value.trim();
    var nickname = document.getElementById('accountNickname').value.trim();
    var status = document.getElementById('accountStatus').checked ? 1 : 0;
    var remark = document.getElementById('accountRemark').value.trim();

    if (!userId) { showToast('请输入用户编号', 'error'); return; }
    if (!username) { showToast('请输入用户名称', 'error'); return; }

    var body = JSON.stringify({
        userId: userId,
        username: username,
        nickname: nickname,
        status: status,
        remark: remark
    });

    var url = id ? '/api/accounts/' + id : '/api/accounts';
    var method = id ? 'PUT' : 'POST';

    var btn = document.getElementById('btnSaveAccount');
    btn.disabled = true;
    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: body
    })
    .then(function (r) { return r.json(); })
    .then(function (resp) {
        if (resp.code === 200) {
            showToast(id ? '账户更新成功' : '账户创建成功', 'success');
            closeModal();
            loadAccounts();
        } else {
            showToast(resp.msg || '操作失败', 'error');
        }
    })
    .catch(function () {
        showToast('操作失败', 'error');
    })
    .finally(function () {
        btn.disabled = false;
    });
}

function deleteAccount(id, userId) {
    if (userId === 'user1') {
        showToast('默认账户不可删除', 'error');
        return;
    }
    showConfirm('确定要删除账户「' + userId + '」吗？').then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/accounts/' + id, { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (resp) {
                if (resp.code === 200) {
                    showToast('删除成功', 'success');
                    loadAccounts();
                } else {
                    showToast(resp.msg || '删除失败', 'error');
                }
            })
            .catch(function () {
                showToast('删除失败', 'error');
            });
    });
}

function truncate(s, n) {
    if (!s) return '';
    return s.length > n ? s.substring(0, n) + '...' : s;
}

function escapeHtml(str) {
    if (str == null) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function escapeAttr(str) {
    if (str == null) return '';
    return String(str).replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}
