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
    private final IAgentExecutorService IAgentExecutorService;

    public AiModelController(AiModelProviderService aiModelProviderService, AiModelService aiModelService, IAgentExecutorService IAgentExecutorService) {
        this.aiModelProviderService = aiModelProviderService;
        this.aiModelService = aiModelService;
        this.IAgentExecutorService = IAgentExecutorService;
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
        return aiModelProviderService.findAll();
    }

    @GetMapping("/api/providers/{id}")
    @ResponseBody
    public AiModelProvider getProvider(@PathVariable Long id) {
        return aiModelProviderService.findById(id);
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
                // 内置提供商不能修改 sdkName
                aiModelProvider.setSdkName(existing.getSdkName());
            } else {
                validateCustomSdkName(aiModelProvider);
            }
        }
        aiModelProvider.setId(id);
        aiModelProviderService.update(aiModelProvider);

        List<AiModel> models = aiModelService.findByProviderId(aiModelProvider.getId());
        for (AiModel model : models) {
            IAgentExecutorService.clearAndStopAgentExecutorByAiModel(model.getId());
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
            IAgentExecutorService.clearAndStopAgentExecutorByAiModel(model.getId());
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
        aiModelService.insert(aiModel);
        return aiModel;
    }

    @PutMapping("/api/models/{id}")
    @ResponseBody
    public AiModel updateModel(@PathVariable Long id, @RequestBody AiModel aiModel) {
        aiModel.setId(id);
        aiModelService.update(aiModel);
        IAgentExecutorService.clearAndStopAgentExecutorByAiModel(aiModel.getId());
        return aiModel;
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
        IAgentExecutorService.clearAndStopAgentExecutorByAiModel(id);
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
