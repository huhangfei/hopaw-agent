package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.entity.AiModel;
import com.agent.hopaw.infra.model.entity.AiModelProvider;
import com.agent.hopaw.infra.model.dto.ModelCapabilityTestResult;
import com.agent.hopaw.infra.service.AiModelProviderService;
import com.agent.hopaw.infra.service.AiModelService;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AiModelController {

    private final AiModelProviderService aiModelProviderService;
    private final AiModelService aiModelService;
    private final IAgentExecutorService agentExecutorService;

    public AiModelController(AiModelProviderService aiModelProviderService, AiModelService aiModelService, IAgentExecutorService agentExecutorService) {
        this.aiModelProviderService = aiModelProviderService;
        this.aiModelService = aiModelService;
        this.agentExecutorService = agentExecutorService;
    }

    @GetMapping("/models")
    public String modelsPage(Model model) {
        List<AiModelProvider> providers = aiModelProviderService.findAll();
        model.addAttribute("providers", providers);
        model.addAttribute("defaultAiModelExtParamsJson", aiModelService.getDefaultAiModelExtParamsJson());
        return "models";
    }

    @GetMapping("/api/providers")
    @ResponseBody
    public List<AiModelProvider> getProviders() {
        List<AiModelProvider> providers = aiModelProviderService.findAll();
        providers.forEach(this::maskApiKey);
        return providers;
    }

    @GetMapping("/api/providers/{id}")
    @ResponseBody
    public AiModelProvider getProvider(@PathVariable Long id) {
        AiModelProvider provider = aiModelProviderService.findById(id);
        if (provider != null) {
            maskApiKey(provider);
        }
        return provider;
    }

    private void maskApiKey(AiModelProvider provider) {
        String key = provider.getApiKey();
        if (key == null || key.isEmpty()) {
            provider.setApiKey("");
        } else if (key.length() > 8) {
            provider.setApiKey(key.substring(0, 4) + "****" + key.substring(key.length() - 4));
        } else {
            provider.setApiKey("****");
        }
    }

    @PostMapping("/api/providers")
    @ResponseBody
    public AiModelProvider createProvider(@RequestBody AiModelProvider aiModelProvider) {
        aiModelProvider.setType("custom");
        validateCustomSdkName(aiModelProvider);
        aiModelProviderService.insert(aiModelProvider);
        return aiModelProvider;
    }

    @PutMapping("/api/providers/{id}")
    @ResponseBody
    public AiModelProvider updateProvider(@PathVariable Long id, @RequestBody AiModelProvider aiModelProvider) {
        AiModelProvider existing = aiModelProviderService.findById(id);
        if (existing != null) {
            aiModelProvider.setType(existing.getType());
            if ("builtin".equals(existing.getType())) {
                aiModelProvider.setSdkName(existing.getSdkName());
            } else {
                validateCustomSdkName(aiModelProvider);
            }
            // 前端传回的apiKey是脱敏值，不更新；仅当显式传入明文密钥时才更新
            String incomingKey = aiModelProvider.getApiKey();
            if (incomingKey == null || incomingKey.isEmpty() || incomingKey.contains("****")) {
                aiModelProvider.setApiKey(null);
            }
        }
        aiModelProvider.setId(id);
        aiModelProviderService.update(aiModelProvider);

        List<AiModel> models = aiModelService.findByProviderId(aiModelProvider.getId());
        for (AiModel model : models) {
            agentExecutorService.clearAndStopAgentExecutorByAiModel(model.getId());
        }

        return aiModelProvider;
    }

    private void validateCustomSdkName(AiModelProvider provider) {
        String sdkName = provider.getSdkName();
        if (sdkName == null || sdkName.isBlank()) {
            throw new IllegalArgumentException("自定义提供商必须指定 sdkName");
        }
        if (!"openai".equals(sdkName) && !"anthropic".equals(sdkName)) {
            throw new IllegalArgumentException("自定义提供商的 sdkName 只能为 'openai' 或 'anthropic'");
        }
    }

    @DeleteMapping("/api/providers/{id}")
    @ResponseBody
    public void deleteProvider(@PathVariable Long id) {
        aiModelProviderService.deleteById(id);
        aiModelService.findByProviderId(id).forEach(model -> {
            agentExecutorService.clearAndStopAgentExecutorByAiModel(model.getId());
                aiModelService.deleteById(model.getId());
        });
    }

    @GetMapping("/api/providers/{providerId}/models")
    @ResponseBody
    public List<AiModel> getModelsByProvider(@PathVariable Long providerId) {
        return aiModelService.findByProviderId(providerId);
    }

    @GetMapping("/api/models/{id}")
    @ResponseBody
    public AiModel getModel(@PathVariable Long id) {
        return aiModelService.findById(id);
    }

    @PostMapping("/api/models")
    @ResponseBody
    public AiModel createModel(@RequestBody AiModel aiModel) {
        validateModelAlias(aiModel);
        validateMaxContextTokens(aiModel);
        aiModelService.insert(aiModel);
        return aiModel;
    }

    @PutMapping("/api/models/{id}")
    @ResponseBody
    public AiModel updateModel(@PathVariable Long id, @RequestBody AiModel aiModel) {
        validateModelAlias(aiModel);
        validateMaxContextTokens(aiModel);
        aiModel.setId(id);
        aiModelService.update(aiModel);
        agentExecutorService.clearAndStopAgentExecutorByAiModel(aiModel.getId());
        return aiModel;
    }

    /** 模型别名为必填字段 */
    private void validateModelAlias(AiModel aiModel) {
        if (aiModel.getModelAlias() == null || aiModel.getModelAlias().isBlank()) {
            throw new IllegalArgumentException("模型别名不能为空");
        }
    }

    /** 最大上下文为必填字段（字节） */
    private void validateMaxContextTokens(AiModel aiModel) {
        if (aiModel.getMaxContextTokens() == null || aiModel.getMaxContextTokens() <= 0) {
            throw new IllegalArgumentException("最大上下文不能为空");
        }
    }

    @PostMapping("/api/models/{id}/test")
    @ResponseBody
    public ModelCapabilityTestResult testModel(@PathVariable Long id) {
        return aiModelService.testModel(id);
    }

    @DeleteMapping("/api/models/{id}")
    @ResponseBody
    public void deleteModel(@PathVariable Long id) {
        aiModelService.deleteById(id);
        agentExecutorService.clearAndStopAgentExecutorByAiModel(id);
    }

    @GetMapping("/api/models/all")
    @ResponseBody
    public Map<Long, List<AiModel>> getAllModels() {
        List<AiModelProvider> providers = aiModelProviderService.findAll();
        Map<Long, List<AiModel>> result = new HashMap<>();
        for (AiModelProvider provider : providers) {
            result.put(provider.getId(), aiModelService.findByProviderId(provider.getId()));
        }
        return result;
    }
}
