package com.agent.hopaw.service;

import com.agent.hopaw.mapper.AiModelProviderMapper;
import com.agent.hopaw.model.AiModelProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiModelProviderService {
    private final AiModelProviderMapper aiModelProviderMapper;

    public AiModelProviderService(AiModelProviderMapper aiModelProviderMapper) {
        this.aiModelProviderMapper = aiModelProviderMapper;
    }

    public AiModelProvider findById(Long id) {
        return aiModelProviderMapper.findById(id);
    }

    public List<AiModelProvider> findAll() {
        return aiModelProviderMapper.findAll();
    }

    public int insert(AiModelProvider aiModelProvider) {
        return aiModelProviderMapper.insert(aiModelProvider);
    }

    public int update(AiModelProvider aiModelProvider) {
        return aiModelProviderMapper.update(aiModelProvider);
    }

    public int deleteById(Long id) {
        return aiModelProviderMapper.deleteById(id);
    }
}
