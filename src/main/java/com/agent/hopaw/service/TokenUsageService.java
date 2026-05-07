package com.agent.hopaw.service;

import com.agent.hopaw.mapper.TokenUsageMapper;
import com.agent.hopaw.model.TokenUsage;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TokenUsageService {

    private final TokenUsageMapper tokenUsageMapper;

    public TokenUsageService(TokenUsageMapper tokenUsageMapper) {
        this.tokenUsageMapper = tokenUsageMapper;
    }

    public void save(TokenUsage tokenUsage) {
        if (tokenUsage.getCreateTime() == null) {
            tokenUsage.setCreateTime(LocalDateTime.now());
        }
        tokenUsageMapper.insert(tokenUsage);
    }

    public Map<String, Object> queryPage(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String source, int page, int size) {
        int offset = (page - 1) * size;
        List<TokenUsage> list = tokenUsageMapper.findByTimeRange(startTime, endTime, userId, agentId, source, size, offset);
        long total = tokenUsageMapper.countByTimeRange(startTime, endTime, userId, agentId, source);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public TokenUsage summary(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String source) {
        return tokenUsageMapper.summaryByTimeRange(startTime, endTime, userId, agentId, source);
    }
}
