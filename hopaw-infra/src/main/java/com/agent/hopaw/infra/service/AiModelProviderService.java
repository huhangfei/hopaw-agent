package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.AiModelProviderMapper;
import com.agent.hopaw.infra.model.entity.AiModelProvider;
import com.agent.hopaw.infra.util.AesEncryptionUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiModelProviderService implements IAiModelProviderService {
    private final AiModelProviderMapper aiModelProviderMapper;

    public AiModelProviderService(AiModelProviderMapper aiModelProviderMapper) {
        this.aiModelProviderMapper = aiModelProviderMapper;
    }

    public AiModelProvider findById(Long id) {
        AiModelProvider provider = aiModelProviderMapper.findById(id);
        if (provider != null) {
            provider.setApiKey(AesEncryptionUtil.decrypt(provider.getApiKey()));
        }
        return provider;
    }

    public List<AiModelProvider> findAll() {
        List<AiModelProvider> providers = aiModelProviderMapper.findAll();
        for (AiModelProvider provider : providers) {
            provider.setApiKey(AesEncryptionUtil.decrypt(provider.getApiKey()));
        }
        return providers;
    }

    public int insert(AiModelProvider aiModelProvider) {
        String plainApiKey = aiModelProvider.getApiKey();
        aiModelProvider.setApiKey(AesEncryptionUtil.encrypt(plainApiKey));
        int result = aiModelProviderMapper.insert(aiModelProvider);
        aiModelProvider.setApiKey(plainApiKey);
        return result;
    }

    public int update(AiModelProvider aiModelProvider) {
        String plainApiKey = aiModelProvider.getApiKey();
        aiModelProvider.setApiKey(AesEncryptionUtil.encrypt(plainApiKey));
        int result = aiModelProviderMapper.update(aiModelProvider);
        aiModelProvider.setApiKey(plainApiKey);
        return result;
    }

    public int deleteById(Long id) {
        return aiModelProviderMapper.deleteById(id);
    }
}
