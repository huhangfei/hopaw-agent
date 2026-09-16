var currentPage = 1;
var totalPages = 1;
var pageSize = 10;
var currentKeyword = '';
var modelNameMap = {};
var searchTimer = null;
var avatarEditorAgentId = null;
var avatarEditorOriginalAvatar = null;
var avatarEditorSelectedFile = null;

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
                    modelNameMap[model.id] = model.modelAlias || model.modelName;
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
    var grid = document.getElementById('agentsGrid');
    var emptyEl = document.getElementById('agentsEmpty');

    if (!list || list.length === 0) {
        grid.style.display = 'none';
        emptyEl.style.display = 'block';
        return;
    }

    grid.style.display = '';
    emptyEl.style.display = 'none';

    grid.innerHTML = list.map(function(agent) {
        var desc = agent.description || '';
        if (desc.length > 80) {
            desc = desc.substring(0, 80) + '...';
        }

        var modelName = modelNameMap[agent.aiModelId] || (agent.aiModelId ? 'ID:' + agent.aiModelId : '-');

        var avatarHtml = '';
        if (agent.avatar) {
            avatarHtml = '<div class="agent-card-avatar" onclick="openAvatarEditor(' + agent.id + ', \'' + escapeAttr(agent.avatar) + '\')" title="点击编辑头像">' +
                '<img src="' + escapeAttr(agent.avatar) + '" alt="头像">' +
                '</div>';
        } else {
            avatarHtml = '<div class="agent-card-avatar agent-card-avatar-empty" onclick="openAvatarEditor(' + agent.id + ', \'\')" title="点击设置头像">' +
                '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/></svg>' +
                '</div>';
        }

        var toolsHtml = '';
        if (agent.enableAllTools) {
            toolsHtml = '<span class="agent-tool-tag" style="background:#fff3e0;color:#e65100;">全部工具</span>';
        } else if (agent.tools) {
            var toolNames = agent.tools.split(',').filter(function(t) { return t.trim() !== ''; });
            var displayTools = toolNames.slice(0, 4);
            displayTools.forEach(function(t) {
                var trimmed = t.trim();
                toolsHtml += '<span class="agent-tool-tag">' + escapeHtml(trimmed) + '</span>';
            });
            if (toolNames.length > 4) {
                toolsHtml += '<span class="agent-tool-tag-more">+' + (toolNames.length - 4) + '</span>';
            }
        }

        return '<div class="agent-card">' +
            '<div class="agent-card-header">' +
                avatarHtml +
                '<div class="agent-card-title">' + escapeHtml(agent.name) + '</div>' +
                '<div class="agent-card-actions">' +
                    '<button class="btn-icon btn-avatar-agent" onclick="showAvatarSettingsModal(' + agent.id + ', \'' + escapeAttr(agent.name) + '\')" title="虚拟形象设置">' +
                        '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/></svg>' +
                    '</button>' +
                    '<button class="btn-icon btn-edit-agent" onclick="editAgent(' + agent.id + ')" title="编辑">' +
                        '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>' +
                    '</button>' +
                    '<button class="btn-icon btn-delete-agent" onclick="deleteAgent(' + agent.id + ')" title="删除">' +
                        '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>' +
                    '</button>' +
                '</div>' +
            '</div>' +
            '<div class="agent-card-body">' +
                '<div class="agent-card-desc" title="' + escapeHtml(agent.description || '') + '">' + escapeHtml(desc) + '</div>' +
                '<div class="agent-card-meta">' +
                    '<span class="agent-card-model">' +
                        '<svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14"><path d="M21 11.18V8l-6-4.5L9 8v3.18L3 14v2l6-4.5V19l2 1.5V10.5L21 11.18z"/></svg>' +
                        escapeHtml(modelName) +
                    '</span>' +
                '</div>' +
            '</div>' +
            (toolsHtml ?
            '<div class="agent-card-footer">' +
                '<div class="agent-card-tools">' + toolsHtml + '</div>' +
            '</div>' : '') +
        '</div>';
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
    showEditAgentModal(id);
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

function escapeAttr(value) {
    if (value === null || value === undefined) return '';
    return String(value).replace(/'/g, '&#39;').replace(/"/g, '&quot;');
}

function openAvatarEditor(agentId, currentAvatar) {
    avatarEditorAgentId = agentId;
    avatarEditorOriginalAvatar = currentAvatar;
    avatarEditorSelectedFile = null;

    var modal = document.getElementById('avatarEditorModal');
    var preview = document.getElementById('avatarPreviewLarge');
    var placeholder = document.getElementById('avatarPreviewPlaceholder');
    var clearBtn = document.getElementById('btnAvatarClear');
    var saveBtn = document.getElementById('btnAvatarSave');
    var status = document.getElementById('avatarUploadStatus');
    var fileInput = document.getElementById('avatarFileInput');

    fileInput.value = '';
    status.style.display = 'none';
    saveBtn.disabled = true;

    if (currentAvatar) {
        preview.innerHTML = '<img src="' + escapeAttr(currentAvatar) + '" alt="头像">';
        clearBtn.style.display = '';
    } else {
        preview.innerHTML = '<span id="avatarPreviewPlaceholder">暂无头像</span>';
        clearBtn.style.display = 'none';
    }

    modal.style.display = 'flex';
}

function closeAvatarEditor() {
    var modal = document.getElementById('avatarEditorModal');
    modal.style.display = 'none';
    avatarEditorAgentId = null;
    avatarEditorSelectedFile = null;
}

function onAvatarFileSelected(event) {
    var file = event.target.files[0];
    if (!file) return;

    var allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    if (allowedTypes.indexOf(file.type) === -1) {
        showToast('仅支持 jpg/png/gif/webp 格式', 'warning');
        return;
    }
    if (file.size > 5 * 1024 * 1024) {
        showToast('图片大小不能超过 5MB', 'warning');
        return;
    }

    avatarEditorSelectedFile = file;

    var reader = new FileReader();
    reader.onload = function(e) {
        var preview = document.getElementById('avatarPreviewLarge');
        preview.innerHTML = '<img src="' + e.target.result + '" alt="头像">';
    };
    reader.readAsDataURL(file);

    document.getElementById('btnAvatarSave').disabled = false;
}

function clearAvatarSelection() {
    avatarEditorSelectedFile = null;
    avatarEditorOriginalAvatar = '';

    var preview = document.getElementById('avatarPreviewLarge');
    preview.innerHTML = '<span id="avatarPreviewPlaceholder">暂无头像</span>';
    document.getElementById('btnAvatarClear').style.display = 'none';
    document.getElementById('btnAvatarSave').disabled = false;
    document.getElementById('avatarFileInput').value = '';
}

function saveAvatar() {
    if (!avatarEditorAgentId) return;

    var saveBtn = document.getElementById('btnAvatarSave');
    var status = document.getElementById('avatarUploadStatus');

    if (avatarEditorSelectedFile) {
        saveBtn.disabled = true;
        status.style.display = 'block';
        status.innerHTML = '<span class="avatar-status uploading">上传中...</span>';

        var formData = new FormData();
        formData.append('file', avatarEditorSelectedFile);
        formData.append('agentId', avatarEditorAgentId);

        fetch('/api/agent/avatar', {
            method: 'POST',
            body: formData
        })
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200) {
                status.innerHTML = '<span class="avatar-status success">上传成功</span>';
                setTimeout(function() {
                    closeAvatarEditor();
                    loadAgents();
                }, 500);
            } else {
                status.innerHTML = '<span class="avatar-status error">' + (res.msg || '上传失败') + '</span>';
                saveBtn.disabled = false;
            }
        })
        .catch(function(err) {
            status.innerHTML = '<span class="avatar-status error">上传失败</span>';
            saveBtn.disabled = false;
        });
    } else {
        saveBtn.disabled = true;
        fetch('/api/agent/avatar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'agentId=' + avatarEditorAgentId + '&clear=true'
        })
        .then(function(r) { return r.json(); })
        .then(function(res) {
            if (res.code === 200) {
                closeAvatarEditor();
                loadAgents();
            } else {
                showToast(res.msg || '保存失败', 'error');
                saveBtn.disabled = false;
            }
        })
        .catch(function(err) {
            showToast('保存失败', 'error');
            saveBtn.disabled = false;
        });
    }
}