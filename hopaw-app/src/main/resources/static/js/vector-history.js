var pendingDeleteId = null;

(function init() {
    fetch('/api/vector-history/types')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg === 'success') {
                var sel = document.getElementById('searchMemoryType');
                (resp.data || []).forEach(function(t) {
                    var opt = document.createElement('option');
                    opt.value = t.code;
                    opt.textContent = t.name;
                    sel.appendChild(opt);
                });
            }
        });
})();

function doSearch() {
    var query = document.getElementById('queryText').value.trim();
    if (!query) {
        showToast('请输入查询关键词', 'error');
        return;
    }

    var params = new URLSearchParams();
    params.append('query', query);

    var agentId = document.getElementById('searchAgentId').value;
    if (agentId) {
        params.append('agentId', agentId);
    }

    var userId = document.getElementById('searchUserId').value;
    if (userId) {
        params.append('userId', userId);
    }

    var memoryType = document.getElementById('searchMemoryType').value;
    if (memoryType) {
        params.append('memoryType', memoryType);
    }

    var maxResults = document.getElementById('searchMaxResults').value;
    if (maxResults) {
        params.append('maxResults', maxResults);
    }

    var minScore = document.getElementById('searchMinScore').value;
    if (minScore) {
        params.append('minScore', minScore);
    }

    fetch('/api/vector-history/search?' + params.toString())
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg === 'success') {
                renderResults(resp.data || []);
            } else {
                showToast(resp.data || '查询失败', 'error');
            }
        })
        .catch(function() {
            showToast('查询请求失败', 'error');
        });
}

function renderResults(results) {
    var table = document.getElementById('resultsTable');
    var emptyState = document.getElementById('emptyState');
    var countEl = document.getElementById('resultCount');
    var tbody = document.getElementById('resultsBody');

    countEl.textContent = results.length > 0 ? '共 ' + results.length + ' 条' : '';

    if (results.length === 0) {
        table.style.display = 'none';
        emptyState.style.display = '';
        emptyState.textContent = '未找到匹配结果';
        return;
    }

    emptyState.style.display = 'none';
    table.style.display = '';
    tbody.innerHTML = '';

    results.forEach(function(item, idx) {
        var memType = item.memoryType || '';
        var typeLabel = memType === 'chatHistory' ? '聊天历史'
                      : memType === 'taskRecords' ? '任务记录'
                      : memType || '未知';
        var typeClass = memType || 'unknown';

        var text = item.text || '';
        var textPreview = text.length > 120 ? text.substring(0, 120) + '...' : text;
        textPreview = escapeHtml(textPreview).replace(/\n/g, '<br>');

        var score = item.score != null ? item.score.toFixed(4) : '-';
        var embeddingId = item.embeddingId || '';

        var row = document.createElement('tr');
        row.innerHTML =
            '<td>' + (idx + 1) + '</td>' +
            '<td><span class="type-tag ' + typeClass + '">' + typeLabel + '</span></td>' +
            '<td>' + (item.agentId || '-') + '</td>' +
            '<td>' + (item.userId || '-') + '</td>' +
            '<td><div class="text-preview">' + textPreview + '</div></td>' +
            '<td class="score-cell">' + score + '</td>' +
            '<td class="action-cell">' +
            '  <button class="btn-detail" onclick="showDetail(' + idx + ')">详情</button>' +
            '  <button class="btn-delete" onclick="confirmDelete(\'' + embeddingId + '\', \'' + escapeAttr(text.substring(0, 50)) + '\')">删除</button>' +
            '</td>';
        tbody.appendChild(row);
    });

    window._lastResults = results;
}

function showDetail(idx) {
    var item = window._lastResults && window._lastResults[idx];
    if (!item) return;

    var memType = item.memoryType || '';
    var typeLabel = memType === 'chatHistory' ? '聊天历史'
                  : memType === 'taskRecords' ? '任务记录'
                  : memType || '未知';
    var typeClass = memType || 'unknown';

    document.getElementById('detailType').textContent = typeLabel;
    document.getElementById('detailType').className = 'detail-tag type-tag ' + typeClass;
    document.getElementById('detailEmbeddingId').textContent = item.embeddingId || '-';
    document.getElementById('detailText').textContent = item.text || '(空)';
    document.getElementById('detailModal').style.display = '';
}

function closeDetailModal() {
    document.getElementById('detailModal').style.display = 'none';
}

function confirmDelete(embeddingId, textPreview) {
    if (!embeddingId) {
        showToast('该记录无法删除（无 embeddingId）', 'error');
        return;
    }
    pendingDeleteId = embeddingId;
    document.getElementById('confirmMessage').textContent =
        '确定要删除该向量记录吗？\n\n记录: ' + textPreview + '...\n\n此操作不可恢复。';
    document.getElementById('confirmModal').style.display = '';
    document.getElementById('confirmDeleteBtn').onclick = executeDelete;
}

function executeDelete() {
    if (!pendingDeleteId) return;

    var btn = document.getElementById('confirmDeleteBtn');
    btn.disabled = true;
    btn.textContent = '删除中...';

    fetch('/api/vector-history/' + pendingDeleteId, { method: 'DELETE' })
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg === 'success') {
                showToast('删除成功', 'success');
                closeConfirmModal();
                doSearch();
            } else {
                showToast(resp.data || '删除失败', 'error');
                btn.disabled = false;
                btn.textContent = '确认删除';
            }
        })
        .catch(function() {
            showToast('删除请求失败', 'error');
            btn.disabled = false;
            btn.textContent = '确认删除';
        });
}

function closeConfirmModal() {
    document.getElementById('confirmModal').style.display = 'none';
    pendingDeleteId = null;
}

function clearResults() {
    document.getElementById('resultsTable').style.display = 'none';
    document.getElementById('emptyState').style.display = '';
    document.getElementById('emptyState').textContent = '请输入查询关键词后点击查询';
    document.getElementById('resultCount').textContent = '';
    document.getElementById('resultsBody').innerHTML = '';
    window._lastResults = null;
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;');
}

function escapeAttr(str) {
    if (!str) return '';
    return str.replace(/'/g, "\\'")
              .replace(/"/g, '&quot;')
              .replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;');
}