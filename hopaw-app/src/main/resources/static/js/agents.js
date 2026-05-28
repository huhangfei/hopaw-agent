var currentPage = 1;
var totalPages = 1;
var pageSize = 10;
var currentKeyword = '';
var modelNameMap = {};
var searchTimer = null;

document.addEventListener('DOMContentLoaded', function() {
    loadModelNameMap(function() {
        loadAgents();
    });
});

function loadModelNameMap(callback) {
    fetch('/api/models/all')
        .then(function(r) { return r.json(); })
        .then(function(data) {
            for (var providerId in data) {
                var models = data[providerId];
                models.forEach(function(model) {
                    modelNameMap[model.id] = model.modelName;
                });
            }
            if (callback) callback();
        })
        .catch(function(err) {
            console.error('加载模型列表失败:', err);
            if (callback) callback();
        });
}

function handleSearchKeyup(event) {
    if (event.key === 'Enter') {
        clearTimeout(searchTimer);
        currentKeyword = event.target.value.trim();
        currentPage = 1;
        loadAgents();
        return;
    }
    clearTimeout(searchTimer);
    var keyword = event.target.value.trim();
    searchTimer = setTimeout(function() {
        if (keyword !== currentKeyword) {
            currentKeyword = keyword;
            currentPage = 1;
            loadAgents();
        }
    }, 400);
}

function loadAgents() {
    var url = '/api/agents/page?page=' + currentPage + '&size=' + pageSize;
    if (currentKeyword) {
        url += '&keyword=' + encodeURIComponent(currentKeyword);
    }

    fetch(url)
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code !== 200) {
                showToast(res.msg || '加载失败', 'error');
                return;
            }
            var data = res.data;
            var list = data.list || [];
            var total = data.total || 0;

            totalPages = Math.ceil(total / pageSize) || 1;

            renderAgentList(list);
            renderPagination(total);
        })
        .catch(function(err) {
            console.error('加载智能体列表失败:', err);
            showToast('加载失败', 'error');
        });
}

function renderAgentList(list) {
    var tbody = document.getElementById('agentsTableBody');
    var emptyEl = document.getElementById('agentsEmpty');
    var table = document.querySelector('.agents-table');

    if (!list || list.length === 0) {
        if (table) table.style.display = 'none';
        emptyEl.style.display = 'block';
        return;
    }

    if (table) table.style.display = '';
    emptyEl.style.display = 'none';

    tbody.innerHTML = list.map(function(agent) {
        var desc = agent.description || '';
        if (desc.length > 50) {
            desc = desc.substring(0, 50) + '...';
        }

        var modelName = modelNameMap[agent.aiModelId] || (agent.aiModelId ? 'ID:' + agent.aiModelId : '-');

        var toolsHtml = '';
        if (agent.tools) {
            var toolNames = agent.tools.split(',').filter(function(t) { return t.trim() !== ''; });
            var displayTools = toolNames.slice(0, 3);
            displayTools.forEach(function(t) {
                var trimmed = t.trim();
                toolsHtml += '<span class="agent-tool-tag">' + escapeHtml(trimmed) + '</span>';
            });
            if (toolNames.length > 3) {
                toolsHtml += '<span class="agent-tool-tag-more">+' + (toolNames.length - 3) + '</span>';
            }
        }
        if (!toolsHtml) {
            toolsHtml = '<span style="color:#999;">-</span>';
        }

        return '<tr>' +
            '<td class="agent-name-cell">' + escapeHtml(agent.name) + '</td>' +
            '<td class="agent-desc-cell" title="' + escapeHtml(agent.description || '') + '">' + escapeHtml(desc) + '</td>' +
            '<td class="agent-model-cell">' + escapeHtml(modelName) + '</td>' +
            '<td class="agent-tools-cell">' + toolsHtml + '</td>' +
            '<td>' +
                '<div class="agent-actions">' +
                    '<button class="btn-icon btn-edit-agent" onclick="editAgent(' + agent.id + ')" title="编辑">' +
                        '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>' +
                    '</button>' +
                    '<button class="btn-icon btn-delete-agent" onclick="deleteAgent(' + agent.id + ')" title="删除">' +
                        '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>' +
                    '</button>' +
                '</div>' +
            '</td>' +
        '</tr>';
    }).join('');
}

function renderPagination(total) {
    var paginationEl = document.getElementById('agentsPagination');
    if (total <= pageSize) {
        paginationEl.style.display = 'none';
        return;
    }
    paginationEl.style.display = 'flex';

    document.getElementById('paginationInfo').textContent = '共 ' + total + ' 条记录，第 ' + currentPage + '/' + totalPages + ' 页';
    document.getElementById('paginationCurrent').textContent = currentPage + ' / ' + totalPages;

    document.getElementById('btnFirstPage').disabled = currentPage <= 1;
    document.getElementById('btnPrevPage').disabled = currentPage <= 1;
    document.getElementById('btnNextPage').disabled = currentPage >= totalPages;
    document.getElementById('btnLastPage').disabled = currentPage >= totalPages;
}

function goToPage(page) {
    if (page < 1 || page > totalPages) return;
    currentPage = page;
    loadAgents();
}

function editAgent(id) {
    setCurrentAgentId(id);
    showEditAgentModal();
}

function deleteAgent(id) {
    showConfirm('确定要删除该智能体吗？').then(function(confirmed) {
        if (!confirmed) return;

        fetch('/api/agents/' + id, {
            method: 'DELETE'
        })
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200) {
                showToast('删除成功', 'info');
                setTimeout(function() {
                    location.reload();
                }, 1000);
            } else {
                showToast(res.msg || '删除失败', 'warning');
            }
        })
        .catch(function(err) {
            console.error('删除失败:', err);
            showToast('删除失败', 'error');
        });
    });
}