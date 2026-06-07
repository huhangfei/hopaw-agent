
function showAddAgentModal() {
    fetch('/agent/modal/add')
        .then(function(r) { return r.text(); })
        .then(function(html) {
            var container = document.createElement('div');
            container.innerHTML = html;
            var modalEl = container.firstElementChild;
            document.body.appendChild(modalEl);

            modalEl.addEventListener('click', function(e) {
                if (e.target === modalEl) {
                    closeAndRemoveModal(modalEl);
                }
            });
            modalEl.style.display = 'flex';
            loadProviders('addProviderSelectFragment', null, 'addModelSelectFragment', null);
        });
}

function showEditAgentModal(agentId) {
    fetch('/agent/modal/edit/' + agentId)
        .then(function(r) { return r.text(); })
        .then(function(html) {
            var container = document.createElement('div');
            container.innerHTML = html;
            var modalEl = container.firstElementChild;
            document.body.appendChild(modalEl);

            modalEl.addEventListener('click', function(e) {
                if (e.target === modalEl) {
                    closeAndRemoveModal(modalEl);
                }
            });
            modalEl.style.display = 'flex';
            var defaultProviderId = document.getElementById('editModelProviderId').value;
            var defaultModelId = document.getElementById('editModelId').value;
            loadProviders('editProviderSelectFragment', defaultProviderId, 'editModelSelectFragment', defaultModelId);

        });
}

function hideAddModalFragment() {
    var modal = document.getElementById('addAgentModalFragment');
    if (modal) closeAndRemoveModal(modal);
}

function hideEditModalFragment() {
    var modal = document.getElementById('editAgentModalFragment');
    if (modal) closeAndRemoveModal(modal);
}

function closeAndRemoveModal(modalEl) {
    if (modalEl && modalEl.parentNode) {
        modalEl.parentNode.removeChild(modalEl);
    }
}

function selectAllTools(containerSelector) {
    document.querySelectorAll(containerSelector + ' input[type="checkbox"]').forEach(function(cb) {
        cb.checked = true;
    });
}

function deselectAllTools(containerSelector) {
    document.querySelectorAll(containerSelector + ' input[type="checkbox"]').forEach(function(cb) {
        cb.checked = false;
    });
}

function toggleAllTools(mode) {
    var checkbox, hiddenInput, container;
    if (mode === 'add') {
        checkbox = document.getElementById('addEnableAllToolsCheckboxFragment');
        hiddenInput = document.getElementById('addEnableAllToolsFragment');
        container = document.getElementById('addToolListContainerFragment');
    } else {
        checkbox = document.getElementById('editEnableAllToolsCheckboxFragment');
        hiddenInput = document.getElementById('editEnableAllToolsFragment');
        container = document.getElementById('editToolListContainerFragment');
    }
    if (checkbox && hiddenInput) {
        hiddenInput.value = checkbox.checked ? 'true' : 'false';
    }
    if (container) {
        container.style.display = checkbox && checkbox.checked ? 'none' : '';
    }
}

function submitAddAgentForm(formEl) {
    var name = formEl.querySelector('input[name="name"]').value.trim();
    var description = formEl.querySelector('textarea[name="description"]').value.trim();
    var modelSelect = formEl.querySelector('select[name="aiModelId"]');
    var modelId = modelSelect ? modelSelect.value : '';

    if (!name) {
        showToast('请输入 Agent 名称', 'warning');
        return;
    }
    if (!description) {
        showToast('请输入 Agent 描述', 'warning');
        return;
    }
    if (!modelId) {
        showToast('请选择模型', 'warning');
        return;
    }

    var formData = new FormData(formEl);
    fetch(formEl.action, {
        method: 'POST',
        body: formData
    }).then(function(r) {
        if (r.ok) {
            showToast('创建成功', 'info');
            setTimeout(function() {
                location.reload();
            }, 800);
        } else {
            showToast('创建失败', 'error');
        }
    }).catch(function(err) {
        showToast('请求失败: ' + err.message, 'error');
    });
};

function submitEditAgentForm(formEl) {
    var name = formEl.querySelector('input[name="name"]').value.trim();
    var description = formEl.querySelector('textarea[name="description"]').value.trim();
    var modelSelect = formEl.querySelector('select[name="aiModelId"]');
    var modelId = modelSelect ? modelSelect.value : '';

    if (!name) {
        showToast('请输入 Agent 名称', 'warning');
        return;
    }
    if (!description) {
        showToast('请输入 Agent 描述', 'warning');
        return;
    }
    if (!modelId) {
        showToast('请选择模型', 'warning');
        return;
    }

    var formData = new FormData(formEl);
    fetch(formEl.action, {
        method: 'POST',
        body: formData
    }).then(function(r) {
        if (r.ok) {
            showToast('保存成功', 'info');
            setTimeout(function() {
                location.reload();
            }, 800);
        } else {
            showToast('保存失败', 'error');
        }
    }).catch(function(err) {
        showToast('请求失败: ' + err.message, 'error');
    });
};



// 智能体相关的弹窗（虚拟人设置等），同时被 agents.html 与 index.html 引用。
// 文件名沿用项目内既有的 agents-modal.js 命名风格。

var avatarSettingsModal = null;
var avatarSettingsState = {
    agentId: null,
    agentName: '',
    modelPool: [],
    settings: null
};

function ensureAvatarSettingsModal() {
    if (avatarSettingsModal) return avatarSettingsModal;
    var wrapper = document.createElement('div');
    var html = '' +
        '<div class="modal-overlay" id="avatarSettingsModal" style="display:none;">' +
        '  <div class="modal">' +
        '    <div class="modal-header">' +
        '      <h3 id="avatarSettingsTitle">虚拟形象设置</h3>' +
        '      <button type="button" class="modal-close" onclick="closeAvatarSettingsModal()" aria-label="关闭">&times;</button>' +
        '    </div>' +
        '    <div class="modal-body">' +
        '      <div class="form-group">' +
        '        <label>启用虚拟人</label>' +
        '        <div class="toggle-wrapper">' +
        '          <label class="toggle">' +
        '            <input type="checkbox" id="avatarEnabledInput">' +
        '            <span class="slider"></span>' +
        '          </label>' +
        '          <span class="toggle-label" id="avatarEnabledLabel">已启用</span>' +
        '          <span class="form-hint">关闭后该智能体不再显示虚拟人形象</span>' +
        '        </div>' +
        '      </div>' +
        '      <div class="form-group">' +
        '        <label>启用语音</label>' +
        '        <div class="toggle-wrapper">' +
        '          <label class="toggle">' +
        '            <input type="checkbox" id="avatarSoundInput">' +
        '            <span class="slider"></span>' +
        '          </label>' +
        '          <span class="toggle-label" id="avatarSoundLabel">已启用</span>' +
        '          <span class="form-hint">关闭后虚拟人不播放提示音</span>' +
        '        </div>' +
        '      </div>' +
        '      <div class="form-group">' +
        '        <label for="avatarPromptInput">虚拟人人设</label>' +
        '        <div class="prompt-variables" aria-label="内置变量">' +
        '        <textarea id="avatarPromptInput" rows="4" placeholder="例如：你的人格设定是一个温柔的萝莉，喜欢撒娇和卖萌"></textarea>' +
        '      </div>' +
        '      <div class="form-group">' +
        '        <label for="avatarModelGroupSelect">人物形象模型</label>' +
        '        <select id="avatarModelGroupSelect"></select>' +
        '      </div>' +
        '      <div class="form-group">' +
        '        <label>启用 TTS 语音播报</label>' +
        '        <div class="toggle-wrapper">' +
        '          <label class="toggle">' +
        '            <input type="checkbox" id="avatarTtsEnabledInput">' +
        '            <span class="slider"></span>' +
        '          </label>' +
        '          <span class="toggle-label" id="avatarTtsEnabledLabel">已启用</span>' +
        '          <span class="form-hint">开启后主动消息将合成语音播报</span>' +
        '        </div>' +
        '      </div>' +
        '      <div class="form-group">' +
        '        <label for="avatarTtsVendorSelect">TTS 配置</label>' +
        '        <select id="avatarTtsVendorSelect" class="form-select" onchange="onAvatarTtsConfigChange()">' +
        '          <option value="">选择 TTS 配置</option>' +
        '        </select>' +
        '      </div>' +
        '      <div class="form-group">' +
        '        <label for="avatarTtsVoiceSelect">TTS 音色</label>' +
        '        <select id="avatarTtsVoiceSelect" class="form-select">' +
        '          <option value="">请先选择厂商</option>' +
        '        </select>' +
        '      </div>' +
        '    </div>' +
        '    <div class="modal-footer">' +
        '      <button type="button" class="btn-cancel" onclick="closeAvatarSettingsModal()">取消</button>' +
        '      <button type="button" class="btn-submit" id="avatarSettingsSaveBtn" onclick="saveAvatarSettings()">保存</button>' +
        '    </div>' +
        '  </div>' +
        '</div>';
    wrapper.innerHTML = html;
    document.body.appendChild(wrapper.firstChild);
    avatarSettingsModal = document.getElementById('avatarSettingsModal');
    bindPromptVariableChips(avatarSettingsModal);
    return avatarSettingsModal;
}

/**
 * 给弹框内带 [data-variable] 的快捷插入按钮绑定事件。
 * 点击后在对应 textarea 的光标处插入占位符。
 */
function bindPromptVariableChips(rootEl) {
    if (!rootEl) return;
    var chips = rootEl.querySelectorAll('.prompt-variable-chip');
    chips.forEach(function(chip) {
        if (chip._chipBound) return;
        chip._chipBound = true;
        chip.addEventListener('click', function() {
            var variable = chip.getAttribute('data-variable') || '';
            var targetId = chip.getAttribute('data-target');
            if (!targetId || !variable) return;
            var textarea = document.getElementById(targetId);
            if (!textarea) return;
            insertTextAtCursor(textarea, variable);
        });
    });
}

/**
 * 在 textarea/input 的当前光标处插入文本，保持原光标位置合理。
 * 没有光标时（如刚打开未聚焦）追加到末尾。
 */
function insertTextAtCursor(input, text) {
    if (!input || !text) return;
    var start = input.selectionStart;
    var end = input.selectionEnd;
    var value = input.value || '';
    if (start == null || end == null || isNaN(start) || isNaN(end)) {
        input.value = value + text;
        input.focus();
        return;
    }
    input.value = value.substring(0, start) + text + value.substring(end);
    var caret = start + text.length;
    try {
        input.setSelectionRange(caret, caret);
    } catch (e) {
        // 某些 input 类型不支持 setSelectionRange，忽略即可
    }
    input.focus();
    // 触发 input 事件，方便其他监听者感知到内容变化
    try {
        input.dispatchEvent(new Event('input', { bubbles: true }));
    } catch (e) {
        // 旧浏览器忽略
    }
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
    bindAvatarToggleChange();
    bindAvatarTtsToggleChange();
    loadAvatarTtsConfigs().then(function() {
        loadAvatarSettings(agentId);
    });
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

function bindAvatarToggleChange() {
    if (!avatarSettingsModal || avatarSettingsModal._avatarToggleBound) return;
    var enabledInput = document.getElementById('avatarEnabledInput');
    var soundInput = document.getElementById('avatarSoundInput');
    if (enabledInput) {
        enabledInput.addEventListener('change', updateAvatarToggleLabels);
    }
    if (soundInput) {
        soundInput.addEventListener('change', updateAvatarToggleLabels);
    }
    avatarSettingsModal._avatarToggleBound = true;
}

function bindAvatarTtsToggleChange() {
    var ttsInput = document.getElementById('avatarTtsEnabledInput');
    if (ttsInput && !ttsInput._ttsToggleBound) {
        ttsInput.addEventListener('change', updateAvatarTtsToggleLabel);
        ttsInput._ttsToggleBound = true;
    }
}

function updateAvatarToggleLabels() {
    var enabledInput = document.getElementById('avatarEnabledInput');
    var soundInput = document.getElementById('avatarSoundInput');
    var enabledLabel = document.getElementById('avatarEnabledLabel');
    var soundLabel = document.getElementById('avatarSoundLabel');
    if (enabledInput && enabledLabel) {
        enabledLabel.textContent = enabledInput.checked ? '已启用' : '已关闭';
    }
    if (soundInput && soundLabel) {
        soundLabel.textContent = soundInput.checked ? '已启用' : '已关闭';
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
    updateAvatarToggleLabels();
    document.getElementById('avatarPromptInput').value = settings.personaSetting || '';

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

    // TTS 字段
    document.getElementById('avatarTtsEnabledInput').checked = settings.ttsEnabled === true;
    if (settings.ttsConfigId) {
        document.getElementById('avatarTtsVendorSelect').value = settings.ttsConfigId;
    } else {
        document.getElementById('avatarTtsVendorSelect').value = '';
    }
    updateAvatarTtsToggleLabel();
    if (settings.ttsConfigId) {
        loadAvatarTtsVoicesForConfig(settings.ttsVoiceId);
    }
}

function saveAvatarSettings() {
    if (!avatarSettingsState.agentId) {
        showToast('缺少智能体信息', 'warning');
        return;
    }
    // 当前页面是否已经加载了虚拟形象（index.html 等）
    var hasAvatarCanvas = !!document.getElementById('avatarCanvas');
    // 直接从表单元素重新读取，避免直接信任缓存对象
    var groupSelect = document.getElementById('avatarModelGroupSelect');
    var groupValue = groupSelect ? groupSelect.value : '';
    var payload = {
        disabled: !document.getElementById('avatarEnabledInput').checked,
        soundEnabled: document.getElementById('avatarSoundInput').checked,
        modelSetting: '',
        modelGroup: groupValue,
        personaSetting: document.getElementById('avatarPromptInput').value || '',
        ttsEnabled: document.getElementById('avatarTtsEnabledInput').checked,
        ttsConfigId: document.getElementById('avatarTtsVendorSelect').value
            ? parseInt(document.getElementById('avatarTtsVendorSelect').value) : null,
        ttsVoiceId: document.getElementById('avatarTtsVoiceSelect').value || ''
    };
    // 根据选中的音色自动填入支持的情感列表
    var selectedVoiceId = payload.ttsVoiceId;
    if (selectedVoiceId && ttsVoicesCache.length > 0) {
        for (var i = 0; i < ttsVoicesCache.length; i++) {
            if (ttsVoicesCache[i].voiceId === selectedVoiceId) {
                var emotions = ttsVoicesCache[i].emotions;
                if (emotions && emotions.length > 0) {
                    payload.ttsEmotions = emotions.join(',');
                }
                break;
            }
        }
    }
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
                // 当前页已加载虚拟人形象，配置变更需要重载以重建 Live2D 实例
                if (hasAvatarCanvas) {
                    showToast('配置已更新，正在刷新页面...', 'info');
                    setTimeout(function() {
                        window.location.reload();
                    }, 600);
                    return;
                }
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

// ========== TTS 音色选择 ==========
var ttsVoicesCache = [];
var ttsConfigsCache = []; // 缓存已启用的 TTS 配置列表 [{id, vendorCode, vendorName, configJson}]

function loadAvatarTtsConfigs() {
    return fetch('/api/tts/configs/enabled')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            var select = document.getElementById('avatarTtsVendorSelect');
            if (!select) return;
            // 保留第一个 option
            while (select.options.length > 1) select.remove(1);
            ttsConfigsCache = resp.data || [];
            ttsConfigsCache.forEach(function(cfg) {
                var opt = document.createElement('option');
                opt.value = cfg.id;
                var label = cfg.configName
                    ? cfg.configName + ' (' + cfg.vendorName + ')'
                    : cfg.vendorName + ' (' + cfg.vendorCode + ')';
                opt.textContent = label;
                select.appendChild(opt);
            });
        })
        .catch(function(e) { console.error('加载 TTS 配置列表失败:', e); });
}

function onAvatarTtsConfigChange() {
    var voiceSelect = document.getElementById('avatarTtsVoiceSelect');
    voiceSelect.innerHTML = '<option value="">请选择音色</option>';
    ttsVoicesCache = [];
    var configId = parseInt(document.getElementById('avatarTtsVendorSelect').value);
    if (!configId) return;
    loadAvatarTtsVoicesForConfig();
}

function loadAvatarTtsVoicesForConfig(defaultVoiceId) {
    var configId = parseInt(document.getElementById('avatarTtsVendorSelect').value);
    if (!configId) return;
    var voiceSelect = document.getElementById('avatarTtsVoiceSelect');
    if (!voiceSelect) return;
    voiceSelect.innerHTML = '<option value="">加载中...</option>';

    // 找到对应配置的 vendorCode
    var vendorCode = '';
    for (var i = 0; i < ttsConfigsCache.length; i++) {
        if (ttsConfigsCache[i].id === configId) {
            vendorCode = ttsConfigsCache[i].vendorCode;
            break;
        }
    }
    if (!vendorCode) {
        voiceSelect.innerHTML = '<option value="">未知厂商</option>';
        return;
    }

    return fetch('/api/tts/voices?vendorCode=' + encodeURIComponent(vendorCode) + '&configId=' + configId)
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            voiceSelect.innerHTML = '<option value="">选择音色</option>';
            ttsVoicesCache = [];
            if (resp.msg !== 'success' || !resp.data) return;
            ttsVoicesCache = resp.data;
            resp.data.forEach(function(v) {
                var opt = document.createElement('option');
                opt.value = v.voiceId;
                opt.textContent = v.voiceName;
                voiceSelect.appendChild(opt);
            });
            if (defaultVoiceId) {
                voiceSelect.value = defaultVoiceId;
            }
        })
        .catch(function(e) {
            console.error('加载音色列表失败:', e);
            voiceSelect.innerHTML = '<option value="">加载失败</option>';
        });
}

function loadAvatarTtsVoices(vendorCode, defaultVoiceId) {
    // 保留兼容旧逻辑
    var voiceSelect = document.getElementById('avatarTtsVoiceSelect');
    if (!voiceSelect) return;
    voiceSelect.innerHTML = '<option value="">加载中...</option>';
    fetch('/api/tts/voices?vendorCode=' + encodeURIComponent(vendorCode))
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            voiceSelect.innerHTML = '<option value="">选择音色</option>';
            ttsVoicesCache = [];
            if (resp.msg !== 'success' || !resp.data) return;
            ttsVoicesCache = resp.data;
            resp.data.forEach(function(v) {
                var opt = document.createElement('option');
                opt.value = v.voiceId;
                opt.textContent = v.voiceName;
                voiceSelect.appendChild(opt);
            });
            if (defaultVoiceId) {
                voiceSelect.value = defaultVoiceId;
            }
        })
        .catch(function(e) {
            console.error('加载音色列表失败:', e);
            voiceSelect.innerHTML = '<option value="">加载失败</option>';
        });
}

function updateAvatarTtsToggleLabel() {
    var input = document.getElementById('avatarTtsEnabledInput');
    var label = document.getElementById('avatarTtsEnabledLabel');
    if (input && label) {
        label.textContent = input.checked ? '已启用' : '已禁用';
    }
}

// 暴露给其他页面（如 index.html 上的虚拟人设置按钮）调用。
window.AvatarSettings = {
    open: function(agentId, agentName) {
        if (!agentId) {
            showToast('缺少智能体信息', 'warning');
            return;
        }
        showAvatarSettingsModal(agentId, agentName);
    },
    close: closeAvatarSettingsModal
};
