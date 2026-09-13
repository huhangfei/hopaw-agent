var ttsVendorMap = {};
var ttsConfigList = [];

function onSettingsLoaded() {
    loadTtsVendors();
}

function loadTtsVendors() {
    fetch('/api/tts/vendors')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            ttsVendorMap = resp.data;
            var select = document.getElementById('ttsVendorSelect');
            select.innerHTML = '<option value="">选择厂商</option>';
            for (var code in ttsVendorMap) {
                if (!ttsVendorMap.hasOwnProperty(code)) continue;
                var opt = document.createElement('option');
                opt.value = code;
                opt.textContent = ttsVendorMap[code];
                select.appendChild(opt);
            }
            loadTtsConfigList();
        })
        .catch(function(e) {
            console.error('加载 TTS 厂商列表失败:', e);
        });
}

function loadTtsConfigList() {
    fetch('/api/tts/configs')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') return;
            ttsConfigList = resp.data || [];
            renderTtsTable();
        })
        .catch(function(e) {
            console.error('加载 TTS 配置列表失败:', e);
        });
}

function renderTtsTable() {
    var tbody = document.getElementById('ttsTableBody');
    if (!ttsConfigList || ttsConfigList.length === 0) {
        tbody.innerHTML = '<tr id="ttsEmptyRow"><td colspan="7" class="tts-empty">暂无 TTS 配置，点击"添加配置"开始</td></tr>';
        return;
    }

    var rows = '';
    ttsConfigList.forEach(function(cfg) {
        var vendorName = ttsVendorMap[cfg.vendorCode] || cfg.vendorName || cfg.vendorCode;
        var configPreview = cfg.configJson || '';
        if (configPreview.length > 60) {
            configPreview = configPreview.substring(0, 60) + '...';
        }
        configPreview = escapeHtml(configPreview);
        var enabledBadge = cfg.enabled === 1
            ? '<span class="tts-status-badge enabled">已启用</span>'
            : '<span class="tts-status-badge disabled">已禁用</span>';

        rows += '<tr>';
        rows += '<td>' + escapeHtml(cfg.configName || '-') + '</td>';
        rows += '<td>' + escapeHtml(vendorName) + '</td>';
        rows += '<td><code>' + escapeHtml(cfg.vendorCode) + '</code></td>';
        rows += '<td class="tts-config-cell" title="' + escapeHtml(cfg.configJson || '') + '">' + configPreview + '</td>';
        rows += '<td>' + enabledBadge + '</td>';
        rows += '<td class="tts-voice-count-cell">' + (cfg.voiceCount != null ? cfg.voiceCount : '-') + '</td>';
        rows += '<td class="tts-actions">'
            + '<button class="btn-tts-test" onclick="showTtsTestModal(' + cfg.id + ')">测试</button>'
            + '<button class="btn-tts-edit" onclick="showTtsVoiceModal(' + cfg.id + ')">音色</button>'
            + '<button class="btn-tts-edit" onclick="editTtsConfig(' + cfg.id + ')">编辑</button>'
            + '<button class="btn-tts-delete" onclick="deleteTtsConfig(' + cfg.id + ')">删除</button>'
            + '</td>';
        rows += '</tr>';
    });
    tbody.innerHTML = rows;
}

function showTtsForm() {
    document.getElementById('ttsEditId').value = '';
    document.getElementById('ttsFormTitle').textContent = '添加 TTS 配置';
    document.getElementById('ttsVendorSelect').value = '';
    document.getElementById('ttsConfigName').value = '';
    document.getElementById('ttsConfigJson').value = '';
    document.getElementById('ttsEnabled').checked = true;
    document.getElementById('ttsEditForm').style.display = 'block';
}

function hideTtsForm() {
    document.getElementById('ttsEditForm').style.display = 'none';
    document.getElementById('ttsEditId').value = '';
}

function editTtsConfig(id) {
    var cfg = null;
    for (var i = 0; i < ttsConfigList.length; i++) {
        if (ttsConfigList[i].id === id) {
            cfg = ttsConfigList[i];
            break;
        }
    }
    if (!cfg) return;

    document.getElementById('ttsEditId').value = cfg.id;
    document.getElementById('ttsFormTitle').textContent = '编辑 TTS 配置';
    document.getElementById('ttsVendorSelect').value = cfg.vendorCode || '';
    document.getElementById('ttsConfigName').value = cfg.configName || '';
    document.getElementById('ttsConfigJson').value = cfg.configJson || '';
    document.getElementById('ttsEnabled').checked = cfg.enabled === 1;
    document.getElementById('ttsEditForm').style.display = 'block';
}

function saveTtsForm() {
    var id = document.getElementById('ttsEditId').value;
    var vendorCode = document.getElementById('ttsVendorSelect').value;
    var configJson = document.getElementById('ttsConfigJson').value.trim();
    var enabled = document.getElementById('ttsEnabled').checked ? 1 : 0;

    if (!vendorCode) {
        showToast('请选择 TTS 厂商', 'error');
        return;
    }

    var payload = {
        id: id ? parseInt(id) : null,
        vendorCode: vendorCode,
        vendorName: ttsVendorMap[vendorCode] || vendorCode,
        configName: document.getElementById('ttsConfigName').value.trim(),
        configJson: configJson,
        enabled: enabled
    };

    fetch('/api/tts/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(function(r) { return r.json(); })
    .then(function(resp) {
        if (resp.msg === 'success') {
            showToast(id ? 'TTS 配置更新成功' : 'TTS 配置添加成功', 'success');
            hideTtsForm();
            loadTtsConfigList();
        } else {
            showToast('保存失败: ' + (resp.data || ''), 'error');
        }
    })
    .catch(function() {
        showToast('保存失败', 'error');
    });
}

function deleteTtsConfig(id) {
    showConfirm('确定要删除该 TTS 配置吗？').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/tts/config/' + id, { method: 'DELETE' })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.msg === 'success') {
                    showToast('删除成功', 'success');
                    hideTtsForm();
                    loadTtsConfigList();
                } else {
                    showToast('删除失败: ' + (resp.data || ''), 'error');
                }
            })
            .catch(function() {
                showToast('删除失败', 'error');
            });
    });
}

function onTtsVendorChange() {
    // 厂商切换时暂不联动音色，仅做记录
}

// ========== 渠道音色管理 ==========
var ttsVoiceEditingConfigId = null;
var ttsVoiceEditingList = [];

/** 打开某渠道的音色管理弹框 */
function showTtsVoiceModal(configId) {
    ttsVoiceEditingConfigId = configId;
    var cfg = null;
    for (var i = 0; i < ttsConfigList.length; i++) {
        if (ttsConfigList[i].id === configId) {
            cfg = ttsConfigList[i];
            break;
        }
    }
    var title = document.getElementById('ttsVoiceTitle');
    title.textContent = '渠道音色 - ' + ((cfg && cfg.configName) ? cfg.configName : ('#' + configId));
    document.getElementById('ttsVoiceModal').style.display = 'flex';
    renderTtsVoiceRows();
    loadTtsVoices();
}

function hideTtsVoiceModal() {
    document.getElementById('ttsVoiceModal').style.display = 'none';
    ttsVoiceEditingConfigId = null;
    ttsVoiceEditingList = [];
}

/** 拉取该渠道已配置的音色 */
function loadTtsVoices() {
    var tbody = document.getElementById('ttsVoiceTableBody');
    tbody.innerHTML = '<tr><td colspan="6" class="tts-empty">加载中...</td></tr>';
    fetch('/api/tts/config/' + encodeURIComponent(ttsVoiceEditingConfigId) + '/voices')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg !== 'success') {
                tbody.innerHTML = '<tr><td colspan="6" class="tts-empty">加载失败: ' + escapeHtml(resp.msg || '') + '</td></tr>';
                return;
            }
            ttsVoiceEditingList = resp.data || [];
            renderTtsVoiceRows();
        })
        .catch(function(e) {
            console.error('加载渠道音色失败:', e);
            tbody.innerHTML = '<tr><td colspan="6" class="tts-empty">加载失败</td></tr>';
        });
}

/** 渲染音色编辑行（含本地未保存的增删改） */
function renderTtsVoiceRows() {
    var tbody = document.getElementById('ttsVoiceTableBody');
    if (!ttsVoiceEditingList.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="tts-empty">暂无音色，点击"+ 新增音色"添加</td></tr>';
    } else {
        var rows = '';
        ttsVoiceEditingList.forEach(function(v, idx) {
            var emotionsText = (v.emotions && v.emotions.length) ? v.emotions.join(',') : '';
            rows += '<tr>'
                + '<td><input type="text" class="tts-voice-input" value="' + escapeHtml(v.voiceId || '') + '" '
                + 'onchange="ttsVoiceEditingList[' + idx + '].voiceId=this.value" placeholder="音色ID" required></td>'
                + '<td><input type="text" class="tts-voice-input" value="' + escapeHtml(v.voiceName || '') + '" '
                + 'onchange="ttsVoiceEditingList[' + idx + '].voiceName=this.value" placeholder="音色名称"></td>'
                + '<td><input type="text" class="tts-voice-input" value="' + escapeHtml(v.language || '') + '" '
                + 'onchange="ttsVoiceEditingList[' + idx + '].language=this.value" placeholder="如 zh-CN"></td>'
                + '<td><input type="text" class="tts-voice-input" value="' + escapeHtml(v.gender || '') + '" '
                + 'onchange="ttsVoiceEditingList[' + idx + '].gender=this.value" placeholder="male/female"></td>'
                + '<td><input type="text" class="tts-voice-input" value="' + escapeHtml(emotionsText) + '" '
                + 'onchange="ttsVoiceEditingList[' + idx + '].emotions=this.value.split(\',\').map(function(s){return s.trim()}).filter(function(s){return s})" '
                + 'placeholder="happy,sad,neutral"></td>'
                + '<td><button class="btn-tts-voice-del" onclick="removeTtsVoice(' + idx + ')">&times;</button></td>'
                + '</tr>';
        });
        tbody.innerHTML = rows;
    }
    var countEl = document.getElementById('ttsVoiceCount');
    countEl.textContent = '共 ' + ttsVoiceEditingList.length + ' 个音色';
}

/** 本地新增一行音色 */
function addTtsVoiceRow() {
    ttsVoiceEditingList.push({
        id: null,
        voiceId: '',
        voiceName: '',
        language: '',
        gender: '',
        description: '',
        emotions: []
    });
    renderTtsVoiceRows();
    // 聚焦最后一行第一个输入框
    var inputs = document.querySelectorAll('#ttsVoiceTableBody input');
    if (inputs.length) {
        inputs[inputs.length - 5].focus();
    }
}

/** 本地删除一行音色 */
function removeTtsVoice(idx) {
    ttsVoiceEditingList.splice(idx, 1);
    renderTtsVoiceRows();
}

/** 保存音色：整体提交该渠道的音色列表 */
function saveTtsVoices() {
    var invalid = false;
    for (var i = 0; i < ttsVoiceEditingList.length; i++) {
        if (!ttsVoiceEditingList[i].voiceId || !ttsVoiceEditingList[i].voiceId.trim()) {
            invalid = true;
            break;
        }
    }
    if (invalid) {
        showToast('音色ID不能为空', 'error');
        return;
    }
    fetch('/api/tts/config/' + encodeURIComponent(ttsVoiceEditingConfigId) + '/voices', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(ttsVoiceEditingList)
    })
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg === 'success') {
                showToast('音色保存成功（共 ' + ((resp.data && resp.data.count) || 0) + ' 个）', 'success');
                hideTtsVoiceModal();
                loadTtsConfigList();
            } else {
                showToast('保存失败: ' + (resp.msg || ''), 'error');
            }
        })
        .catch(function() {
            showToast('保存失败', 'error');
        });
}

/** 重置为厂商默认音色（覆盖当前渠道音色） */
function resetTtsVoices() {
    showConfirm('重置将丢弃当前渠道所有音色修改，恢复为该厂商的默认音色列表，确定继续吗？').then(function(confirmed) {
        if (!confirmed) return;
        fetch('/api/tts/config/' + encodeURIComponent(ttsVoiceEditingConfigId) + '/voices/reset', {
            method: 'POST'
        })
            .then(function(r) { return r.json(); })
            .then(function(resp) {
                if (resp.msg === 'success') {
                    showToast('已重置为默认音色（共 ' + ((resp.data && resp.data.count) || 0) + ' 个）', 'success');
                    loadTtsVoices();
                } else {
                    showToast('重置失败: ' + (resp.msg || ''), 'error');
                }
            })
            .catch(function() {
                showToast('重置失败', 'error');
            });
    });
}

// ========== 渠道测试 ==========
var ttsTestConfigId = null;
var ttsTestVoices = [];
var ttsTestSelectedVoiceId = null;
var ttsTestObjectUrl = null;
var ttsTestGenerating = false;

/** 打开渠道测试弹框 */
function showTtsTestModal(configId) {
    ttsTestConfigId = configId;
    ttsTestVoices = [];
    ttsTestSelectedVoiceId = null;

    var cfg = null;
    for (var i = 0; i < ttsConfigList.length; i++) {
        if (ttsConfigList[i].id === configId) {
            cfg = ttsConfigList[i];
            break;
        }
    }
    document.getElementById('ttsTestTitle').textContent = '渠道测试 - ' + ((cfg && cfg.configName) ? cfg.configName : ('#' + configId));
    clearTtsTestAudio();
    document.getElementById('ttsTestText').value = '';
    document.getElementById('ttsTestModal').style.display = 'flex';
    renderTtsTestVoices();
    loadTtsTestVoices();
}

/** 关闭测试弹框并清理音频资源 */
function hideTtsTestModal() {
    clearTtsTestAudio();
    document.getElementById('ttsTestModal').style.display = 'none';
    ttsTestConfigId = null;
    ttsTestVoices = [];
    ttsTestSelectedVoiceId = null;
}

/** 拉取该渠道的音色列表 */
function loadTtsTestVoices() {
    var list = document.getElementById('ttsTestVoiceList');
    list.innerHTML = '<div class="tts-test-empty">加载中...</div>';
    fetch('/api/tts/config/' + encodeURIComponent(ttsTestConfigId) + '/voices')
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (ttsTestConfigId === null) return;
            if (resp.msg !== 'success') {
                list.innerHTML = '<div class="tts-test-empty">加载失败: ' + escapeHtml(resp.msg || '') + '</div>';
                return;
            }
            ttsTestVoices = resp.data || [];
            renderTtsTestVoices();
        })
        .catch(function() {
            list.innerHTML = '<div class="tts-test-empty">加载失败</div>';
        });
}

/** 渲染左侧音色列表 */
function renderTtsTestVoices() {
    var list = document.getElementById('ttsTestVoiceList');
    if (!ttsTestVoices.length) {
        list.innerHTML = '<div class="tts-test-empty">暂无音色</div>';
        return;
    }
    var html = '';
    ttsTestVoices.forEach(function(v) {
        var selected = (v.voiceId === ttsTestSelectedVoiceId) ? ' selected' : '';
        var meta = [];
        if (v.language) meta.push(escapeHtml(v.language));
        if (v.gender) meta.push(escapeHtml(v.gender));
        html += '<div class="tts-test-voice-item' + selected + '" onclick="selectTtsTestVoice(\'' + escapeHtmlForAttr(v.voiceId || '') + '\')">'
            + '<div class="tts-test-voice-name">' + escapeHtml(v.voiceName || v.voiceId || '') + '</div>'
            + '<div class="tts-test-voice-id">' + escapeHtml(v.voiceId || '') + '</div>'
            + (meta.length ? '<div class="tts-test-voice-meta">' + meta.join(' · ') + '</div>' : '')
            + '</div>';
    });
    list.innerHTML = html;
}

/** 选中某个音色 */
function selectTtsTestVoice(voiceId) {
    ttsTestSelectedVoiceId = voiceId;
    renderTtsTestVoices();
}

/** 生成测试语音 */
function generateTtsTestAudio() {
    if (ttsTestGenerating) return;
    if (!ttsTestSelectedVoiceId) {
        showToast('请先在左侧选择音色', 'error');
        return;
    }
    var text = document.getElementById('ttsTestText').value.trim();
    if (!text) {
        showToast('请输入测试文本', 'error');
        return;
    }
    var btn = document.getElementById('ttsTestGenBtn');
    ttsTestGenerating = true;
    btn.disabled = true;
    btn.textContent = '生成中...';
    clearTtsTestAudio();

    fetch('/api/tts/config/' + encodeURIComponent(ttsTestConfigId) + '/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ voiceId: ttsTestSelectedVoiceId, text: text })
    })
        .then(function(r) { return r.json(); })
        .then(function(resp) {
            if (resp.msg === 'success' && resp.data && resp.data.audio) {
                playTtsTestAudio(resp.data.audio, resp.data.format, resp.data.bytes);
            } else {
                showToast('合成失败: ' + (resp.data || resp.msg || ''), 'error');
            }
        })
        .catch(function() {
            showToast('合成失败，请检查网络或服务状态', 'error');
        })
        .finally(function() {
            ttsTestGenerating = false;
            btn.disabled = false;
            btn.textContent = '生成语音';
        });
}

/** base64 音频写入播放器并自动播放 */
function playTtsTestAudio(base64, format, bytes) {
    try {
        var binary = atob(base64);
        var len = binary.length;
        var bytesArr = new Uint8Array(len);
        for (var i = 0; i < len; i++) {
            bytesArr[i] = binary.charCodeAt(i);
        }
        var blob = new Blob([bytesArr], { type: 'audio/' + (format === 'wav' ? 'wav' : 'mpeg') });
        ttsTestObjectUrl = URL.createObjectURL(blob);

        var audio = document.getElementById('ttsTestAudio');
        audio.src = ttsTestObjectUrl;
        var sizeKb = bytes ? Math.round(bytes / 1024) : 0;
        document.getElementById('ttsTestAudioInfo').textContent = '已生成 ' + (format || '').toUpperCase() + ' 音频，约 ' + sizeKb + ' KB';
        document.getElementById('ttsTestPlayer').style.display = 'block';
        var playPromise = audio.play();
        if (playPromise && typeof playPromise.catch === 'function') {
            playPromise.catch(function() { /* 浏览器策略拦截时用户手动点击播放 */ });
        }
    } catch (e) {
        console.error('播放测试音频失败:', e);
        showToast('音频解析失败', 'error');
    }
}

/** 重播：从头播放 */
function replayTtsTestAudio() {
    var audio = document.getElementById('ttsTestAudio');
    if (!audio.src) return;
    try { audio.currentTime = 0; } catch (e) {}
    var playPromise = audio.play();
    if (playPromise && typeof playPromise.catch === 'function') {
        playPromise.catch(function() {});
    }
}

/** 清理播放器与 objectURL */
function clearTtsTestAudio() {
    var audio = document.getElementById('ttsTestAudio');
    try { audio.pause(); } catch (e) {}
    audio.removeAttribute('src');
    try { audio.load(); } catch (e) {}
    if (ttsTestObjectUrl) {
        URL.revokeObjectURL(ttsTestObjectUrl);
        ttsTestObjectUrl = null;
    }
    document.getElementById('ttsTestPlayer').style.display = 'none';
    document.getElementById('ttsTestAudioInfo').textContent = '';
}