/**
 * 加载提供者和模型选择数据
 * @param providerSelectId 提供者选择框ID
 * @param modelSelectId 模型选择框ID
 */
function loadProviders(providerSelectId,defaultProvider,modelSelectId,defaultModel){
    fetch('/api/providers')
        .then(function(r) { return r.json(); })
        .then(function(providers) {
            const providerSelect = document.getElementById(providerSelectId);
            providers.forEach(function(provider) {
                if(provider.url && provider.apiKey){
                    const option = document.createElement('option');
                    option.value = provider.id;
                    option.text = provider.name;
                    if (provider.id === parseInt(defaultProvider)) {
                        option.selected = true;
                    }
                    providerSelect.appendChild(option);
                }
            });

            loadAiModel(providerSelect.value, modelSelectId, defaultModel)
        });
    // 监听提供者选择框变化，动态加载模型选择框数据
    document.getElementById(providerSelectId).addEventListener('change', function() {
        loadAiModel(this.value, modelSelectId, defaultModel)
    });


}
function loadAiModel(providerId,modelSelectId,defaultModel){
    if(!providerId){
        return;
    }
    fetch('/api/providers/' + providerId + '/models')
        .then(function(r) { return r.json(); })
        .then(function(models) {
            const modelSelect = document.getElementById(modelSelectId);
            modelSelect.innerHTML = '';
            models.forEach(function(model) {
                const option = document.createElement('option');
                option.value = model.id;
                option.text = model.modelName;
                if (model.id === parseInt(defaultModel)) {
                    option.selected = true;
                }
                modelSelect.appendChild(option);
            });
        });
}