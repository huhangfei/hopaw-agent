package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.event.TokenUsageEvent;
import com.agent.hopaw.infra.mapper.TokenUsageMapper;
import com.agent.hopaw.infra.model.entity.TokenUsage;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TokenUsageService implements ITokenUsageService {

    private final TokenUsageMapper tokenUsageMapper;

    public TokenUsageService(TokenUsageMapper tokenUsageMapper) {
        this.tokenUsageMapper = tokenUsageMapper;
    }

    @EventListener
    public void onTokenUsageMessage(TokenUsageEvent message) {
        TokenUsage tokenUsage = new TokenUsage();
        tokenUsage.setAgentId(message.getAgentId());
        tokenUsage.setModelName(message.getModelName());
        tokenUsage.setInputTokens(message.getInputTokens());
        tokenUsage.setOutputTokens(message.getOutputTokens());
        tokenUsage.setTotalTokens(message.getTotalTokens());
        tokenUsage.setUserId(message.getUserId());
        tokenUsage.setSessionId(message.getSessionId());
        tokenUsage.setSource(message.getSource());
        tokenUsage.setCreateTime(message.getCreateTime() != null ? message.getCreateTime() : LocalDateTime.now());
        tokenUsageMapper.insert(tokenUsage);
    }

    public void save(TokenUsage tokenUsage) {
        if (tokenUsage.getCreateTime() == null) {
            tokenUsage.setCreateTime(LocalDateTime.now());
        }
        tokenUsageMapper.insert(tokenUsage);
    }

    public Map<String, Object> queryPage(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String modelName, String source, String sessionId, int page, int size) {
        int offset = (page - 1) * size;
        List<TokenUsage> list = tokenUsageMapper.findByTimeRange(startTime, endTime, userId, agentId, modelName, source, sessionId, size, offset);
        long total = tokenUsageMapper.countByTimeRange(startTime, endTime, userId, agentId, modelName, source, sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public TokenUsage summary(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String modelName, String source, String sessionId) {
        return tokenUsageMapper.summaryByTimeRange(startTime, endTime, userId, agentId, modelName, source, sessionId);
    }

    public List<Map<String, Object>> dailyStats(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String modelName, String source, String sessionId) {
        return tokenUsageMapper.dailyStatsByTimeRange(startTime, endTime, userId, agentId, modelName, source, sessionId);
    }
    public List<TokenUsage> findTodayByAgentUser(Long agentId, String userId, String source, String sessionId, Long minId, int limit) {
        return tokenUsageMapper.findTodayByAgentUser(agentId, userId, source, sessionId, minId, limit);
    }
}
