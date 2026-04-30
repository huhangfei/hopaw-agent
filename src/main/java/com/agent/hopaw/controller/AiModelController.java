package com.agent.hopaw.controller;

import com.agent.hopaw.model.AiModel;
import com.agent.hopaw.model.AiModelProvider;
import com.agent.hopaw.service.AiModelProviderService;
import com.agent.hopaw.service.AiModelService;
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

    public AiModelController(AiModelProviderService aiModelProviderService, AiModelService aiModelService) {
        this.aiModelProviderService = aiModelProviderService;
        this.aiModelService = aiModelService;
    }

    @GetMapping("/models")
    public String modelsPage(Model model) {
        List<AiModelProvider> providers = aiModelProviderService.findAll();
        model.addAttribute("providers", providers);
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
        aiModelProviderService.insert(aiModelProvider);
        return aiModelProvider;
    }

    @PutMapping("/api/providers/{id}")
    @ResponseBody
    public AiModelProvider updateProvider(@PathVariable Long id, @RequestBody AiModelProvider aiModelProvider) {
        aiModelProvider.setId(id);
        aiModelProviderService.update(aiModelProvider);
        return aiModelProvider;
    }

    @DeleteMapping("/api/providers/{id}")
    @ResponseBody
    public void deleteProvider(@PathVariable Long id) {
        aiModelProviderService.deleteById(id);
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
        return aiModel;
    }

    @DeleteMapping("/api/models/{id}")
    @ResponseBody
    public void deleteModel(@PathVariable Long id) {
        aiModelService.deleteById(id);
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
