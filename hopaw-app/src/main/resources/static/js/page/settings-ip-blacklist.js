function ipblLoad() {
    fetch('/api/ip-blacklist').then(function(r) { return r.json(); }).then(function(res) {
        if (res.code !== 200) return;
        var list = res.data || [];
        var tbody = document.getElementById('ipblTableBody');
        var empty = document.getElementById('ipblEmpty');
        document.getElementById('ipblTotal').textContent = '共 ' + list.length + ' 条';
        if (list.length === 0) {
            tbody.innerHTML = '';
            empty.style.display = '';
            return;
        }
        empty.style.display = 'none';
        var html = '';
        list.forEach(function(item, i) {
            html += '<tr>';
            html += '<td>' + (i + 1) + '</td>';
            html += '<td>' + escapeHtml(item.ip) + '</td>';
            html += '<td>' + escapeHtml(item.remark || '') + '</td>';
            html += '<td>' + escapeHtml(item.createTime || '') + '</td>';
            html += '<td><button class="btn-text-danger" onclick="ipblDelete(' + item.id + ')">删除</button></td>';
            html += '</tr>';
        });
        tbody.innerHTML = html;
    });
}

function ipblShowAdd() {
    document.getElementById('ipblIpInput').value = '';
    document.getElementById('ipblRemarkInput').value = '';
    document.getElementById('ipblAddModal').classList.add('active');
}

function ipblCloseAdd() {
    document.getElementById('ipblAddModal').classList.remove('active');
}

function ipblDoAdd() {
    var ip = document.getElementById('ipblIpInput').value.trim();
    var remark = document.getElementById('ipblRemarkInput').value.trim();
    if (!ip) { showToast('请输入IP地址', 'error'); return; }
    fetch('/api/ip-blacklist', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ip: ip, remark: remark })
    }).then(function(r) { return r.json(); }).then(function(res) {
        if (res.code === 200) {
            ipblCloseAdd();
            ipblLoad();
            showToast('添加成功', 'success');
        } else {
            showToast(res.msg || '添加失败', 'error');
        }
    });
}

function ipblDelete(id) {
    if (!confirm('确定删除该IP黑名单记录？')) return;
    fetch('/api/ip-blacklist/' + id, { method: 'DELETE' }).then(function(r) { return r.json(); }).then(function(res) {
        if (res.code === 200) {
            ipblLoad();
            showToast('删除成功', 'success');
        } else {
            showToast(res.msg || '删除失败', 'error');
        }
    });
}

document.addEventListener('DOMContentLoaded', function() {
    ipblLoad();
});
