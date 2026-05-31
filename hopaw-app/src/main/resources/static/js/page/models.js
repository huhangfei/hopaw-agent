let currentProviderId = null;
let currentModelId = null;
let currentProviderData = null;
let currentProviderType = null;

document.addEventListener('DOMContentLoaded', function() {
    loadModelCounts();
});

function loadModelCounts() {
    fetch('/api/models/all')
        .then(response => response.json())
        .then(data => {
            document.querySelectorAll('.model-count-value').forEach(el => {
                const providerId = el.getAttribute('data-provider-id');
                const count = data[providerId] ? data[providerId].length : 0;
                el.textContent = count + ' 个模型';
            });
        })
        .catch(error => {
            console.error('加载模型数量失败:', error);
        });
}

function selectProvider(btn) {
    const element = btn.closest('.provider-card');

    currentProviderId = element.getAttribute('data-id');
    currentProviderData = {
        name: element.querySelector('.provider-name').textContent,
        code: element.querySelector('.provider-code').textContent
    };

    document.getElementById('modelsModalTitle').textContent = currentProviderData.name + ' - 模型列表';
    Modal.open('modelsModal');

    loadModels();
}

function closeModelsModal() {
    Modal.close('modelsModal');
}

function loadModels() {
    if (!currentProviderId) return;
    
    fetch('/api/providers/' + currentProviderId + '/models')
        .then(response => response.json())
        .then(models => {
            const tbody = document.getElementById('modelsTableBody');
            const emptyState = document.getElementById('modelsEmptyState');
            const table = document.getElementById('modelsTable');
            
            if (models.length === 0) {
                table.style.display = 'none';
                emptyState.style.display = 'block';
            } else {
                table.style.display = 'table';
                emptyState.style.display = 'none';
                
                tbody.innerHTML = models.map(model => {
                    const capabilities = model.capabilities ? model.capabilities.split(',').map(cap => {
                        const capNames = {text: '文本', image: '图片', audio: '音频', video: '视频', document: '文档'};
                        return '<span class="capability-tag">' + (capNames[cap] || cap) + '</span>';
                    }).join('') : '';
                    
                    const verified = model.verified ? '<span class="verified-yes">已验证</span>' : '<span class="verified-no">未验证</span>';
                    
                    return '<tr>' +
                        '<td>' + model.modelName + '</td>' +
                        '<td>' + capabilities + '</td>' +
                        '<td>' + verified + '</td>' +
                        '<td>' + (model.createTime || '') + '</td>' +
                        '<td>' +
                            '<button class="btn-icon btn-test-model" onclick="testModel(' + model.id + ')" title="检测能力">' +
                                '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M19.07 4.93a10 10 0 0 0-14.14 0l1.41 1.41a8 8 0 0 1 11.32 0l1.41-1.41z"/><path d="M17.66 7.34a6 6 0 0 0-8.48 0l1.41 1.41a4 4 0 0 1 5.66 0l1.41-1.41z"/><path d="M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"/></svg>' +
                            '</button>' +
                            '<button class="btn-icon btn-edit-model" onclick="showEditModelModal(' + model.id + ')" title="编辑">' +
                                '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>' +
                            '</button>' +
                            '<button class="btn-icon btn-delete-model" onclick="deleteModel(' + model.id + ')" title="删除">' +
                                '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>' +
                            '</button>' +
                        '</td>' +
                    '</tr>';
                }).join('');
            }
        })
        .catch(error => {
            console.error('加载模型列表失败:', error);
        });
}

function showAddProviderModal() {
    currentProviderId = null;
    currentProviderType = 'custom';
    document.getElementById('providerModalTitle').textContent = '添加提供商';
    document.getElementById('providerForm').reset();
    document.getElementById('providerId').value = '';
    document.getElementById('providerSdkNameGroup').style.display = 'block';
    document.getElementById('providerSdkName').required = true;
    document.getElementById('providerSdkName').disabled = false;
    document.getElementById('providerSdkName').value = '';
    Modal.open('providerModal');
}

function showEditProviderModal(id) {
    currentProviderId = id;
    document.getElementById('providerModalTitle').textContent = '编辑提供商';

    fetch('/api/providers/' + id)
        .then(response => response.json())
        .then(provider => {
            document.getElementById('providerId').value = provider.id;
            document.getElementById('providerName').value = provider.name;
            document.getElementById('providerCode').value = provider.provider;
            document.getElementById('providerUrl').value = provider.url || '';
            document.getElementById('providerApiKey').value = provider.apiKey || '';
            document.getElementById('providerIcon').value = provider.icon || '';
            document.getElementById('providerExtParams').value = provider.extParams || '';

            // 内置提供商隐藏 sdkName 选项（值不可变）
            currentProviderType = provider.type;
            if (provider.type === 'builtin') {
                document.getElementById('providerSdkNameGroup').style.display = 'none';
                document.getElementById('providerSdkName').required = false;
            } else {
                document.getElementById('providerSdkNameGroup').style.display = 'block';
                document.getElementById('providerSdkName').required = true;
                document.getElementById('providerSdkName').value = provider.sdkName || '';
            }

            Modal.open('providerModal');
        })
        .catch(error => {
            console.error('获取提供商信息失败:', error);
            showToast('获取提供商信息失败', 'error');
        });
}

function closeProviderModal() {
    Modal.close('providerModal');
}

function submitProvider() {
    const name = document.getElementById('providerName').value.trim();
    const provider = document.getElementById('providerCode').value.trim();
    const providerUrl = document.getElementById('providerUrl').value.trim();
    const isBuiltin = currentProviderType === 'builtin';

    if (!name || !provider) {
        showToast('请填写必填字段', 'error');
        return;
    }
    if (!isBuiltin && !document.getElementById('providerSdkName').value) {
        showToast('请选择 API 兼容类型', 'error');
        return;
    }

    const data = {
        name: name,
        provider: provider,
        url: providerUrl,
        apiKey: document.getElementById('providerApiKey').value.trim(),
        icon: document.getElementById('providerIcon').value.trim(),
        extParams: document.getElementById('providerExtParams').value.trim()
    };
    if (!isBuiltin) {
        data.sdkName = document.getElementById('providerSdkName').value;
    }
    
    let url, method;
    if (currentProviderId) {
        url = '/api/providers/' + currentProviderId;
        method = 'PUT';
        data.id = currentProviderId;
    } else {
        url = '/api/providers';
        method = 'POST';
    }
    
    fetch(url, {
        method: method,
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
    .then(response => {
        if (response.ok) {
            showToast(currentProviderId ? '更新成功' : '添加成功', 'success');
            closeProviderModal();
            setTimeout(function() { location.reload(); }, 800);
        } else {
            response.json().then(function(err) {
                showToast(err.message || '操作失败', 'error');
            }).catch(function() {
                showToast('操作失败', 'error');
            });
        }
    })
    .catch(error => {
        console.error('请求失败:', error);
        showToast('请求失败', 'error');
    });
}

function deleteProvider(id) {
    showConfirm('确定要删除此提供商及其所有模型吗？').then(function(confirmed) {
        if (!confirmed) return;

        fetch('/api/providers/' + id, {
            method: 'DELETE'
        })
        .then(response => {
            if (response.ok) {
                showToast('删除成功', 'success');
                location.reload();
            } else {
                showToast('删除失败', 'error');
            }
        })
        .catch(error => {
            console.error('删除失败:', error);
            showToast('删除失败', 'error');
        });
    });
}

function showAddModelModal() {
    if (!currentProviderId) {
        showToast('请先选择一个提供商', 'error');
        return;
    }

    currentModelId = null;
    document.getElementById('modelModalTitle').textContent = '添加模型';
    document.getElementById('modelForm').reset();
    document.getElementById('modelId').value = '';
    document.getElementById('modelProviderId').value = currentProviderId;
    document.getElementById('modelCapabilitiesDisplay').innerHTML = '<span class="capability-hint">保存后将自动检测</span>';
    document.getElementById('modelVerifiedDisplay').innerHTML = '<span class="capability-hint">保存后将自动验证</span>';
    document.getElementById('modelExtParams').value = '';
    Modal.open('modelModal');
}

function showEditModelModal(id) {
    currentModelId = id;
    document.getElementById('modelModalTitle').textContent = '编辑模型';

    fetch('/api/models/' + id)
        .then(response => response.json())
        .then(model => {
            document.getElementById('modelId').value = model.id;
            document.getElementById('modelProviderId').value = model.providerId;
            document.getElementById('modelName').value = model.modelName;

            // 显示模型能力（只读）
            const capNames = {text: '文本', image: '图片', audio: '音频', video: '视频', document: '文档'};
            if (model.capabilities) {
                const caps = model.capabilities.split(',');
                const html = caps.map(c => '<span class="capability-tag">' + (capNames[c] || c) + '</span>').join('');
                document.getElementById('modelCapabilitiesDisplay').innerHTML = html;
            } else {
                document.getElementById('modelCapabilitiesDisplay').innerHTML = '<span class="capability-hint">无</span>';
            }

            // 显示验证状态（只读）
            const verifiedHtml = model.verified
                ? '<span class="verified-yes">已验证</span>'
                : '<span class="verified-no">未验证</span>';
            document.getElementById('modelVerifiedDisplay').innerHTML = verifiedHtml;

            document.getElementById('modelExtParams').value = model.extParams || '';
            Modal.open('modelModal');
        })
        .catch(error => {
            console.error('获取模型信息失败:', error);
            showToast('获取模型信息失败', 'error');
        });
}

function closeModelModal() {
    Modal.close('modelModal');
}

function submitModel() {
    const modelName = document.getElementById('modelName').value.trim();

    if (!modelName) {
        showToast('请输入模型名称', 'error');
        return;
    }
    
    const data = {
        providerId: parseInt(document.getElementById('modelProviderId').value),
        modelName: modelName,
        extParams: document.getElementById('modelExtParams').value.trim() || null
    };
    
    let url, method;
    if (currentModelId) {
        url = '/api/models/' + currentModelId;
        method = 'PUT';
        data.id = currentModelId;
    } else {
        url = '/api/models';
        method = 'POST';
    }
    
    fetch(url, {
        method: method,
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
    .then(response => {
        if (response.ok) {
            showToast(currentModelId ? '更新成功' : '添加成功', 'success');
            closeModelModal();
            loadModels();
            loadModelCounts();
        } else {
            response.json().then(function(err) {
                showToast(err.message || '操作失败', 'error');
            }).catch(function() {
                showToast('操作失败', 'error');
            });
        }
    })
    .catch(error => {
        console.error('请求失败:', error);
        showToast('请求失败', 'error');
    });
}

function testModel(id) {
    showToast('正在检测模型能力...', 'info');

    fetch('/api/models/' + id + '/test', {
        method: 'POST'
    })
    .then(response => response.json())
    .then(result => {
        showToast(result.message || '检测完成', result.verified ? 'success' : 'warning');
        loadModels();
    })
    .catch(error => {
        console.error('检测失败:', error);
        showToast('检测请求失败', 'error');
    });
}

function deleteModel(id) {
    showConfirm('确定要删除此模型吗？').then(function(confirmed) {
        if (!confirmed) return;

        fetch('/api/models/' + id, {
            method: 'DELETE'
        })
        .then(response => {
            if (response.ok) {
                showToast('删除成功', 'success');
                loadModels();
                loadModelCounts();
            } else {
                showToast('删除失败', 'error');
            }
        })
        .catch(error => {
            console.error('删除失败:', error);
            showToast('删除失败', 'error');
        });
    });
}

