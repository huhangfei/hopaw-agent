var SETTINGS_KEYS = ['system_prompt'];

function onSettingsLoaded() {
    document.getElementById('systemPrompt').value = settingsCache['system_prompt'] || '';
}

function savePromptSettings() {
    var prompt = document.getElementById('systemPrompt').value.trim();

    saveConfig('system_prompt', prompt, '自定义系统提示词').then(function(ok) {
        if (ok) {
            showToast('提示词设置保存成功', 'success');
        } else {
            showToast('保存失败', 'error');
        }
    });
}
