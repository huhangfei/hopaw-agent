package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.AiModelProvider;

import java.util.List;

public interface IAiModelProviderService {
    AiModelProvider findById(Long id);
    List<AiModelProvider> findAll();
    int insert(AiModelProvider aiModelProvider);
    int update(AiModelProvider aiModelProvider);
    int deleteById(Long id);
}
