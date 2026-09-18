var currentPage = 1;
var pageSize = 10;
var currentStartTime = '';
var currentEndTime = '';

document.addEventListener('DOMContentLoaded', function() {
    var now = new Date();
    var endOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59);
    var endStr = formatDateTime(endOfDay);
    var start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 7, 0, 0, 0);
    var startStr = formatDateTime(start);
    document.getElementById('startTime').value = startStr;
    document.getElementById('endTime').value = endStr;

    var urlParams = new URLSearchParams(window.location.search);
    var sessionId = urlParams.get('sessionId');
    if (sessionId) {
        document.getElementById('filterSessionId').value = sessionId;
    }

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
    fetchChart();
    fetchList();
}

function getFilterParams() {
    var params = {
        startTime: currentStartTime,
        endTime: currentEndTime
    };
    var userId = document.getElementById('filterUser').value;
    var agentId = document.getElementById('filterAgent').value;
    var modelName = document.getElementById('filterModel').value.trim();
    var source = document.getElementById('filterSource').value;
    var sessionId = document.getElementById('filterSessionId').value.trim();
    if (userId) params.userId = userId;
    if (agentId) params.agentId = agentId;
    if (modelName) params.modelName = modelName;
    if (source) params.source = source;
    if (sessionId) params.sessionId = sessionId;
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

var dailyChart = null;

function fetchChart() {
    var params = new URLSearchParams(getFilterParams());
    fetch('/api/token-usage/daily-stats?' + params.toString())
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            var data = resp.data || [];
            var chartCard = document.getElementById('chartCard');
            var chartEmpty = document.getElementById('chartEmpty');
            var canvas = document.getElementById('dailyChart');

            if (data.length === 0) {
                chartCard.style.display = 'none';
                return;
            }
            chartCard.style.display = 'block';

            var labels = data.map(function(d) { return d.date; });
            var inputData = data.map(function(d) { return d.inputTokens; });
            var outputData = data.map(function(d) { return d.outputTokens; });
            var totalData = data.map(function(d) { return d.totalTokens; });

            if (dailyChart) {
                dailyChart.destroy();
            }

            dailyChart = new Chart(canvas, {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [
                        {
                            label: '输入 Tokens',
                            data: inputData,
                            backgroundColor: 'rgba(102, 126, 234, 0.7)',
                            borderColor: '#667eea',
                            borderWidth: 1
                        },
                        {
                            label: '输出 Tokens',
                            data: outputData,
                            backgroundColor: 'rgba(118, 75, 162, 0.7)',
                            borderColor: '#764ba2',
                            borderWidth: 1
                        },
                        {
                            label: '总 Tokens',
                            data: totalData,
                            backgroundColor: 'rgba(240, 147, 66, 0.7)',
                            borderColor: '#f09342',
                            borderWidth: 1
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            position: 'top'
                        },
                        tooltip: {
                            callbacks: {
                                label: function(ctx) {
                                    return ctx.dataset.label + ': ' + ctx.raw.toLocaleString();
                                }
                            }
                        }
                    },
                    scales: {
                        x: {
                            grid: { display: false }
                        },
                        y: {
                            beginAtZero: true,
                            ticks: {
                                callback: function(v) {
                                    return v >= 1000 ? (v / 1000).toFixed(0) + 'k' : v;
                                }
                            }
                        }
                    }
                }
            });
        })
        .catch(function(e) {
            console.error('加载每日统计失败:', e);
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

var SOURCE_MAP = window.SOURCE_MAP || {};

function getSourceName(source) {
    return SOURCE_MAP[source] || source || '-';
}
