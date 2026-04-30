package com.agent.hopaw.service;

import com.agent.hopaw.mapper.AiModelMapper;
import com.agent.hopaw.model.AiModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiModelService {
    private final AiModelMapper aiModelMapper;

    public AiModelService(AiModelMapper aiModelMapper) {
        this.aiModelMapper = aiModelMapper;
    }

    public AiModel findById(Long id) {
        return aiModelMapper.findById(id);
    }

    public List<AiModel> findByProviderId(Long providerId) {
        return aiModelMapper.findByProviderId(providerId);
    }

    public int insert(AiModel aiModel) {
        return aiModelMapper.insert(aiModel);
    }

    public int update(AiModel aiModel) {
        return aiModelMapper.update(aiModel);
    }

    public int deleteById(Long id) {
        return aiModelMapper.deleteById(id);
    }
}
