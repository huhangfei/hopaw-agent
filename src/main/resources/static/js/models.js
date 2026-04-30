function showToast(message, type) {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = 'toast toast-' + (type || 'info');
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(function() {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.3s';
        setTimeout(function() { toast.remove(); }, 300);
    }, 2500);
}

let currentProviderId = null;
let currentModelId = null;
let currentProviderData = null;

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
    document.getElementById('modelsModal').style.display = 'flex';

    loadModels();
}

function closeModelsModal() {
    document.getElementById('modelsModal').style.display = 'none';
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
    document.getElementById('providerModalTitle').textContent = '添加提供商';
    document.getElementById('providerForm').reset();
    document.getElementById('providerId').value = '';
    document.getElementById('providerModal').style.display = 'flex';
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
            document.getElementById('providerModal').style.display = 'flex';
        })
        .catch(error => {
            console.error('获取提供商信息失败:', error);
            showToast('获取提供商信息失败', 'error');
        });
}

function closeProviderModal() {
    document.getElementById('providerModal').style.display = 'none';
}

function submitProvider() {
    const name = document.getElementById('providerName').value.trim();
    const provider = document.getElementById('providerCode').value.trim();
    
    if (!name || !provider) {
        showToast('请填写必填字段', 'error');
        return;
    }
    
    const data = {
        name: name,
        provider: provider,
        url: document.getElementById('providerUrl').value.trim(),
        apiKey: document.getElementById('providerApiKey').value.trim(),
        icon: document.getElementById('providerIcon').value.trim(),
        extParams: document.getElementById('providerExtParams').value.trim()
    };
    
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
            location.reload();
        } else {
            showToast('操作失败', 'error');
        }
    })
    .catch(error => {
        console.error('请求失败:', error);
        showToast('请求失败', 'error');
    });
}

function deleteProvider(id) {
    if (!confirm('确定要删除此提供商及其所有模型吗？')) {
        return;
    }

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
    document.getElementById('modelModal').style.display = 'flex';
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
            document.getElementById('modelVerified').checked = model.verified;
            
            document.querySelectorAll('input[name="capabilities"]').forEach(cb => {
                cb.checked = false;
            });
            
            if (model.capabilities) {
                const caps = model.capabilities.split(',');
                document.querySelectorAll('input[name="capabilities"]').forEach(cb => {
                    if (caps.includes(cb.value)) {
                        cb.checked = true;
                    }
                });
            }
            
            document.getElementById('modelModal').style.display = 'flex';
        })
        .catch(error => {
            console.error('获取模型信息失败:', error);
            showToast('获取模型信息失败', 'error');
        });
}

function closeModelModal() {
    document.getElementById('modelModal').style.display = 'none';
}

function submitModel() {
    const modelName = document.getElementById('modelName').value.trim();
    const capabilities = Array.from(document.querySelectorAll('input[name="capabilities"]:checked')).map(cb => cb.value).join(',');

    if (!modelName || !capabilities) {
        showToast('请填写必填字段', 'error');
        return;
    }
    
    const data = {
        providerId: parseInt(document.getElementById('modelProviderId').value),
        modelName: modelName,
        capabilities: capabilities,
        verified: document.getElementById('modelVerified').checked
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
            showToast('操作失败', 'error');
        }
    })
    .catch(error => {
        console.error('请求失败:', error);
        showToast('请求失败', 'error');
    });
}

function deleteModel(id) {
    if (!confirm('确定要删除此模型吗？')) {
        return;
    }

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
}

document.getElementById('providerModal').addEventListener('click', function(e) {
    if (e.target === this) {
        closeProviderModal();
    }
});

document.getElementById('modelModal').addEventListener('click', function(e) {
    if (e.target === this) {
        closeModelModal();
    }
});

document.getElementById('modelsModal').addEventListener('click', function(e) {
    if (e.target === this) {
        closeModelsModal();
    }
});
