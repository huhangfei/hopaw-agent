
window.submitAddAgentForm = function(formEl) {
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

window.submitEditAgentForm = function(formEl) {
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