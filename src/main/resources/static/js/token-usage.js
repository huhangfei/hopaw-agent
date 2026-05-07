var currentPage = 1;
var pageSize = 15;
var currentStartTime = '';
var currentEndTime = '';

document.addEventListener('DOMContentLoaded', function() {
    var now = new Date();
    var endOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59);
    var endStr = formatDateTime(endOfDay);
    var start = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    var startStr = formatDateTime(start);
    document.getElementById('startTime').value = startStr;
    document.getElementById('endTime').value = endStr;
    queryData();
});

function formatDateTime(date) {
    var y = date.getFullYear();
    var m = String(date.getMonth() + 1).padStart(2, '0');
    var d = String(date.getDate()).padStart(2, '0');
    var h = String(date.getHours()).padStart(2, '0');
    var min = String(date.getMinutes()).padStart(2, '0');
    return y + '-' + m + '-' + d + 'T' + h + ':' + min;
}

function toStandardStr(localVal) {
    if (!localVal) return '';
    return localVal.replace('T', ' ') + ':00';
}

function getAgentName(agentId) {
    if (!agentId || !window.AGENTS) return agentId || '-';
    for (var i = 0; i < AGENTS.length; i++) {
        if (AGENTS[i].id === agentId) return AGENTS[i].name;
    }
    return agentId;
}

function queryData() {
    currentStartTime = toStandardStr(document.getElementById('startTime').value);
    currentEndTime = toStandardStr(document.getElementById('endTime').value);
    currentPage = 1;
    fetchSummary();
    fetchList();
}

function getFilterParams() {
    var params = {
        startTime: currentStartTime,
        endTime: currentEndTime
    };
    var userId = document.getElementById('filterUser').value;
    var agentId = document.getElementById('filterAgent').value;
    var source = document.getElementById('filterSource').value;
    if (userId) params.userId = userId;
    if (agentId) params.agentId = agentId;
    if (source) params.source = source;
    return params;
}

function fetchSummary() {
    var params = new URLSearchParams(getFilterParams());
    fetch('/api/token-usage/summary?' + params.toString())
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            var data = resp.data || {};
            document.getElementById('summaryCount').textContent = data.id || 0;
            document.getElementById('summaryInput').textContent = (data.inputTokens || 0).toLocaleString();
            document.getElementById('summaryOutput').textContent = (data.outputTokens || 0).toLocaleString();
            document.getElementById('summaryTotal').textContent = (data.totalTokens || 0).toLocaleString();
        })
        .catch(function(e) {
            console.error('加载汇总失败:', e);
        });
}

function fetchList() {
    var params = new URLSearchParams(getFilterParams());
    params.set('page', currentPage);
    params.set('size', pageSize);
    fetch('/api/token-usage?' + params.toString())
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            var data = resp.data || {};
            var list = data.list || [];
            var total = data.total || 0;
            var tbody = document.getElementById('usageTableBody');
            var emptyState = document.getElementById('emptyState');
            var pagination = document.getElementById('pagination');

            if (list.length === 0) {
                tbody.innerHTML = '';
                emptyState.style.display = 'block';
                pagination.style.display = 'none';
                return;
            }
            emptyState.style.display = 'none';
            tbody.innerHTML = list.map(function(item) {
                var time = item.createTime || '';
                if (time && time.indexOf('T') !== -1) {
                    time = time.replace('T', ' ');
                }
                return '<tr>' +
                    '<td>' + escapeHtml(time) + '</td>' +
                    '<td>' + escapeHtml(item.userId || '-') + '</td>' +
                    '<td>' + escapeHtml(getAgentName(item.agentId)) + '</td>' +
                    '<td>' + escapeHtml(item.modelName || '-') + '</td>' +
                    '<td>' + escapeHtml(getSourceName(item.source)) + '</td>' +
                    '<td>' + (item.inputTokens || 0).toLocaleString() + '</td>' +
                    '<td>' + (item.outputTokens || 0).toLocaleString() + '</td>' +
                    '<td><strong>' + (item.totalTokens || 0).toLocaleString() + '</strong></td>' +
                    '</tr>';
            }).join('');

            var totalPages = Math.max(1, Math.ceil(total / pageSize));
            document.getElementById('pageInfo').textContent = '第 ' + currentPage + ' / ' + totalPages + ' 页（共 ' + total + ' 条）';
            document.getElementById('prevPage').disabled = currentPage <= 1;
            document.getElementById('nextPage').disabled = currentPage >= totalPages;
            pagination.style.display = 'flex';
        })
        .catch(function(e) {
            console.error('加载用量列表失败:', e);
            showToast('加载用量列表失败', 'error');
        });
}

function goPage(page) {
    currentPage = page;
    fetchList();
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

var SOURCE_MAP = {
    'chat': '会话',
    'model-test': '模型测试',
    'memory-organize': '记忆整理',
    'agentTask': '智能体定时任务'
};

function getSourceName(source) {
    return SOURCE_MAP[source] || source || '-';
}
