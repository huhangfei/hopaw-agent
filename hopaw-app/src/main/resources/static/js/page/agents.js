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

var avatarSettingsModal = null;
var avatarSettingsState = {
    agentId: null,
    agentName: '',
    modelPool: [],
    settings: null
};

function ensureAvatarSettingsModal() {
    if (avatarSettingsModal) return avatarSettingsModal;
    var html = '' +
        '<div class="modal-overlay avatar-settings-overlay" id="avatarSettingsModal" style="display:none;">' +
        '  <div class="modal avatar-settings-dialog">' +
        '    <div class="modal-header">' +
        '      <h3 id="avatarSettingsTitle">虚拟形象设置</h3>' +
        '      <button type="button" class="modal-close" onclick="closeAvatarSettingsModal()" aria-label="关闭">&times;</button>' +
        '    </div>' +
        '    <div class="modal-body">' +
        '      <div class="avatar-settings-section">' +
        '        <h4 class="avatar-settings-section-title">基础</h4>' +
        '        <div class="avatar-settings-row">' +
        '          <div class="avatar-settings-row-text">' +
        '            <span class="form-label">启用虚拟人</span>' +
        '            <span class="form-hint">关闭后该智能体不再显示虚拟人形象</span>' +
        '          </div>' +
        '          <label class="switch"><input type="checkbox" id="avatarEnabledInput"><span class="slider"></span></label>' +
        '        </div>' +
        '        <div class="avatar-settings-row">' +
        '          <div class="avatar-settings-row-text">' +
        '            <span class="form-label">启用语音</span>' +
        '            <span class="form-hint">关闭后虚拟人不播放提示音</span>' +
        '          </div>' +
        '          <label class="switch"><input type="checkbox" id="avatarSoundInput"><span class="slider"></span></label>' +
        '        </div>' +
        '      </div>' +
        '      <div class="avatar-settings-section">' +
        '        <h4 class="avatar-settings-section-title">人设与提示词</h4>' +
        '        <div class="form-group">' +
        '          <label class="form-label">人设</label>' +
        '          <textarea id="avatarPersonaInput" rows="3" placeholder="请输入人设描述"></textarea>' +
        '        </div>' +
        '        <div class="form-group">' +
        '          <label class="form-label">主动消息提示词模板</label>' +
        '          <textarea id="avatarPromptInput" rows="4" placeholder="例如：根据当前时间、亲密度和最近聊天，主动发一条消息"></textarea>' +
        '        </div>' +
        '      </div>' +
        '      <div class="avatar-settings-section">' +
        '        <h4 class="avatar-settings-section-title">虚拟人模型</h4>' +
        '        <div class="form-group">' +
        '          <label class="form-label">模型分组</label>' +
        '          <select id="avatarModelGroupSelect"></select>' +
        '        </div>' +
        '      </div>' +
        '    </div>' +
        '    <div class="modal-footer">' +
        '      <button type="button" class="btn-cancel" onclick="closeAvatarSettingsModal()">取消</button>' +
        '      <button type="button" class="btn-submit" id="avatarSettingsSaveBtn" onclick="saveAvatarSettings()">保存</button>' +
        '    </div>' +
        '  </div>' +
        '</div>';
    var wrapper = document.createElement('div');
    wrapper.innerHTML = html;
    document.body.appendChild(wrapper.firstChild);
    avatarSettingsModal = document.getElementById('avatarSettingsModal');
    return avatarSettingsModal;
}

function showAvatarSettingsModal(agentId, agentName) {
    ensureAvatarSettingsModal();
    avatarSettingsState.agentId = agentId;
    avatarSettingsState.agentName = agentName || '';
    var titleEl = document.getElementById('avatarSettingsTitle');
    if (titleEl) {
        titleEl.textContent = '虚拟形象设置 - ' + (agentName || ('智能体 #' + agentId));
    }
    avatarSettingsModal.style.display = 'flex';
    avatarSettingsModal.classList.add('active');
    document.body.style.overflow = 'hidden';
    loadAvatarSettings(agentId);
    // 绑定遮罩点击关闭
    if (!avatarSettingsModal._avatarOverlayBound) {
        avatarSettingsModal.addEventListener('click', function(e) {
            if (e.target === avatarSettingsModal) {
                closeAvatarSettingsModal();
            }
        });
        avatarSettingsModal._avatarOverlayBound = true;
    }
}

function closeAvatarSettingsModal() {
    if (avatarSettingsModal) {
        avatarSettingsModal.style.display = 'none';
        avatarSettingsModal.classList.remove('active');
    }
    document.body.style.overflow = '';
}

function loadAvatarSettings(agentId) {
    Promise.all([
        fetch('/api/avatar/settings?agentId=' + encodeURIComponent(agentId) + '&_t=' + Date.now(), { credentials: 'same-origin' }).then(function(r) { return r.json(); }),
        fetch('/api/avatar/models?agentId=' + encodeURIComponent(agentId) + '&_t=' + Date.now(), { credentials: 'same-origin' }).then(function(r) { return r.json(); })
    ]).then(function(results) {
        var settingsResp = results[0];
        var modelsResp = results[1];
        var settings = (settingsResp && settingsResp.code === 200) ? settingsResp.data : null;
        var groups = (modelsResp && modelsResp.code === 200 && modelsResp.data && modelsResp.data.groups) ? modelsResp.data.groups : [];
        var selected = (modelsResp && modelsResp.code === 200 && modelsResp.data && modelsResp.data.selected) ? modelsResp.data.selected : '';
        fillAvatarSettings(settings, groups, selected);
    }).catch(function(err) {
        console.error('加载虚拟人配置失败', err);
        showToast('加载虚拟人配置失败', 'error');
    });
}

function fillAvatarSettings(settings, groups, selectedGroup) {
    if (!settings) settings = {};
    avatarSettingsState.settings = settings;
    avatarSettingsState.modelPool = Array.isArray(groups) ? groups : [];
    if (!selectedGroup) {
        selectedGroup = settings.modelGroup || '';
    }

    document.getElementById('avatarEnabledInput').checked = !settings.disabled;
    document.getElementById('avatarSoundInput').checked = settings.soundEnabled !== false;
    document.getElementById('avatarPersonaInput').value = settings.personaSetting || '';
    document.getElementById('avatarPromptInput').value = settings.avatarAiPrompt || '';

    var groupSelect = document.getElementById('avatarModelGroupSelect');
    groupSelect.innerHTML = '';
    avatarSettingsState.modelPool.forEach(function(g) {
        var opt = document.createElement('option');
        opt.value = g.name;
        opt.textContent = g.name;
        if (selectedGroup && g.name === selectedGroup) {
            opt.selected = true;
        }
        groupSelect.appendChild(opt);
    });
    if (avatarSettingsState.modelPool.length === 0) {
        var opt = document.createElement('option');
        opt.value = '';
        opt.textContent = '暂无可用模型';
        groupSelect.appendChild(opt);
    }
}

function saveAvatarSettings() {
    if (!avatarSettingsState.agentId) {
        showToast('缺少智能体信息', 'warning');
        return;
    }
    // 直接从表单元素重新读取，避免直接信任缓存对象
    var groupSelect = document.getElementById('avatarModelGroupSelect');
    var groupValue = groupSelect ? groupSelect.value : '';
    var payload = {
        disabled: !document.getElementById('avatarEnabledInput').checked,
        soundEnabled: document.getElementById('avatarSoundInput').checked,
        modelSetting: '',
        modelGroup: groupValue,
        personaSetting: document.getElementById('avatarPersonaInput').value || '',
        avatarAiPrompt: document.getElementById('avatarPromptInput').value || ''
    };
    console.log('[avatar-settings] PUT /api/avatar/settings agentId=' + avatarSettingsState.agentId, payload);
    var saveBtn = document.getElementById('avatarSettingsSaveBtn');
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.textContent = '保存中...';
    }
    var requestUrl = '/api/avatar/settings?agentId=' + encodeURIComponent(avatarSettingsState.agentId);
    fetch(requestUrl, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(payload)
    })
    .then(function(r) {
        console.log('[avatar-settings] save response status=' + r.status);
        return r.json();
    })
    .then(function(res) {
        console.log('[avatar-settings] save response body', res);
        if (res && res.code === 200) {
            showToast('保存成功', 'info');
            // 保存后重新拉取一次，确认数据已落地
            return fetch('/api/avatar/settings?agentId=' + encodeURIComponent(avatarSettingsState.agentId) + '&_t=' + Date.now(), { credentials: 'same-origin' })
                .then(function(r) { return r.json(); })
                .then(function(verify) {
                    console.log('[avatar-settings] 重新拉取校验', verify);
                    if (verify && verify.data && verify.data.modelGroup !== payload.modelGroup) {
                        console.warn('[avatar-settings] 保存与回读不一致，发送=' + payload.modelGroup + ' 实际=' + verify.data.modelGroup);
                        showToast('保存成功，但回读校验不一致，请检查后端', 'warning');
                    }
                })
                .catch(function() {})
                .then(function() { closeAvatarSettingsModal(); });
        } else {
            showToast((res && res.msg) || '保存失败', 'warning');
        }
    })
    .catch(function(err) {
        console.error('保存虚拟人配置失败', err);
        showToast('保存失败', 'error');
    })
    .finally(function() {
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.textContent = '保存';
        }
    });
}