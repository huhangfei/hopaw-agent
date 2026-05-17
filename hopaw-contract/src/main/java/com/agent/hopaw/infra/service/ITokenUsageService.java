package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.TokenUsage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ITokenUsageService {
    void save(TokenUsage tokenUsage);
    Map<String, Object> queryPage(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String modelName, String source, int page, int size);
    TokenUsage summary(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String modelName, String source);
    List<Map<String, Object>> dailyStats(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String modelName, String source);
    List<TokenUsage> findTodayByAgentUser(Long agentId, String userId, String source, Long minId, int limit);
}
