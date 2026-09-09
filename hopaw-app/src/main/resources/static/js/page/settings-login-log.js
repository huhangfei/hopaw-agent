var llPage = 1;
var llPageSize = 20;

function llLoad() {
    var userId = document.getElementById('llFilterUserId').value.trim();
    var ip = document.getElementById('llFilterIp').value.trim();
    var result = document.getElementById('llFilterResult').value;
    var params = new URLSearchParams();
    if (userId) params.set('userId', userId);
    if (ip) params.set('ip', ip);
    if (result) params.set('result', result);
    params.set('page', llPage);
    params.set('size', llPageSize);

    fetch('/api/login-log/page?' + params.toString()).then(function(r) { return r.json(); }).then(function(res) {
        if (res.code !== 200) return;
        var data = res.data || {};
        var list = data.list || [];
        var total = data.total || 0;
        var totalPages = Math.max(1, Math.ceil(total / llPageSize));

        var tbody = document.getElementById('llTableBody');
        var empty = document.getElementById('llEmpty');
        if (list.length === 0) {
            tbody.innerHTML = '';
            empty.style.display = '';
        } else {
            empty.style.display = 'none';
            var html = '';
            var startNum = (llPage - 1) * llPageSize;
            list.forEach(function(item, i) {
                var resultClass = item.result === 'success' ? 'll-result-success' : 'll-result-failed';
                var resultText = item.result === 'success' ? '成功' : '失败';
                html += '<tr>';
                html += '<td>' + (startNum + i + 1) + '</td>';
                html += '<td>' + escapeHtml(item.userId || '') + '</td>';
                html += '<td>' + escapeHtml(item.username || '') + '</td>';
                html += '<td>' + escapeHtml(item.ip || '') + '</td>';
                html += '<td><span class="' + resultClass + '">' + resultText + '</span></td>';
                html += '<td>' + escapeHtml(item.failReason || '-') + '</td>';
                html += '<td>' + escapeHtml(item.createTime || '') + '</td>';
                html += '</tr>';
            });
            tbody.innerHTML = html;
        }

        document.getElementById('llPageInfo').textContent = llPage + ' / ' + totalPages;
        document.getElementById('llTotalInfo').textContent = '共 ' + total + ' 条';
        document.getElementById('llPrevBtn').disabled = llPage <= 1;
        document.getElementById('llNextBtn').disabled = llPage >= totalPages;
    });
}

function llSearch() {
    llPage = 1;
    llLoad();
}

function llPrevPage() {
    if (llPage > 1) { llPage--; llLoad(); }
}

function llNextPage() {
    llPage++;
    llLoad();
}

function llClearAll() {
    if (!confirm('确定清空所有登录日志？此操作不可恢复。')) return;
    fetch('/api/login-log/all', { method: 'DELETE' }).then(function(r) { return r.json(); }).then(function(res) {
        if (res.code === 200) {
            llPage = 1;
            llLoad();
            showToast('已清空', 'success');
        }
    });
}

function llShowConfig() {
    // 加载渠道列表
    fetch('/api/notify/channels').then(function(r) { return r.json(); }).then(function(res) {
        var channels = (res.code === 200 && res.data) ? res.data : [];
        // 加载当前配置
        fetch('/api/login-log/exception-config').then(function(r) { return r.json(); }).then(function(cres) {
            var config = cres.data || {};
            var selectedIds = (config.notifyChannels || '').split(',').filter(Boolean);
            var html = '';
            channels.forEach(function(ch) {
                var checked = selectedIds.indexOf(String(ch.id)) >= 0 ? 'checked' : '';
                html += '<label class="ll-channel-item"><input type="checkbox" value="' + ch.id + '" ' + checked + '> ' + escapeHtml(ch.name) + '（' + escapeHtml(ch.type) + '）</label>';
            });
            if (channels.length === 0) {
                html = '<div class="form-hint">暂无通知渠道，请先在"通知渠道"页配置</div>';
            }
            document.getElementById('llChannelCheckboxes').innerHTML = html;
            document.getElementById('llMaxUserFailures').value = config.maxUserFailures || 10;
            document.getElementById('llMaxIpFailures').value = config.maxIpFailures || 20;
            document.getElementById('llConfigModal').classList.add('active');
        });
    });
}

function llCloseConfig() {
    document.getElementById('llConfigModal').classList.remove('active');
}

function llSaveConfig() {
    var maxUser = parseInt(document.getElementById('llMaxUserFailures').value) || 10;
    var maxIp = parseInt(document.getElementById('llMaxIpFailures').value) || 20;
    var channelIds = [];
    document.querySelectorAll('#llChannelCheckboxes input[type="checkbox"]:checked').forEach(function(cb) {
        channelIds.push(cb.value);
    });
    fetch('/api/login-log/exception-config', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            maxUserFailures: maxUser,
            maxIpFailures: maxIp,
            notifyChannels: channelIds.join(',')
        })
    }).then(function(r) { return r.json(); }).then(function(res) {
        if (res.code === 200) {
            llCloseConfig();
            showToast('保存成功', 'success');
        } else {
            showToast(res.msg || '保存失败', 'error');
        }
    });
}

document.addEventListener('DOMContentLoaded', function() {
    llLoad();
});
