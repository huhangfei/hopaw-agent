
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
            }, 1500);
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
            }, 1500);
        } else {
            showToast('保存失败', 'error');
        }
    }).catch(function(err) {
        showToast('请求失败: ' + err.message, 'error');
    });
};