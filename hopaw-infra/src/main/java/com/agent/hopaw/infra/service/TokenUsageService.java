package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.event.TokenUsageEvent;
import com.agent.hopaw.infra.mapper.TokenUsageMapper;
import com.agent.hopaw.infra.model.entity.TokenUsage;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
        tokenUsage.setCreateTime(message.getCreateTime() != null ? message.getCreateTime() : LocalDateTime.now(ZoneOffset.UTC));
        tokenUsageMapper.insert(tokenUsage);
    }

    public void save(TokenUsage tokenUsage) {
        if (tokenUsage.getCreateTime() == null) {
            tokenUsage.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        }
        tokenUsageMapper.insert(tokenUsage);
    }

    public Map<String, Object> queryPage(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String modelName, String source, String sessionId, int page, int size) {
        // 入参为本地时间，转 UTC 与存储对齐
        LocalDateTime startUtc = toUtc(startTime);
        LocalDateTime endUtc = toUtc(endTime);
        int offset = (page - 1) * size;
        List<TokenUsage> list = tokenUsageMapper.findByTimeRange(startUtc, endUtc, userId, agentId, modelName, source, sessionId, size, offset);
        long total = tokenUsageMapper.countByTimeRange(startUtc, endUtc, userId, agentId, modelName, source, sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public TokenUsage summary(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String modelName, String source, String sessionId) {
        // 入参为本地时间，转 UTC 与存储对齐
        return tokenUsageMapper.summaryByTimeRange(toUtc(startTime), toUtc(endTime), userId, agentId, modelName, source, sessionId);
    }

    public List<Map<String, Object>> dailyStats(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, String modelName, String source, String sessionId) {
        // 入参为本地时间，转 UTC 与存储对齐；分组时使用本地时区偏移以还原用户感知的"当天"
        int zoneOffsetMinutes = ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
        return tokenUsageMapper.dailyStatsByTimeRange(toUtc(startTime), toUtc(endTime), userId, agentId, modelName, source, sessionId, zoneOffsetMinutes);
    }

    public List<TokenUsage> findTodayByAgentUser(Long agentId, String userId, String source, String sessionId, Long minId, int limit) {
        // create_time 以 UTC 存储，查询时将「用户本地时区当天 [0:00, 次日0:00)」转换为 UTC 时间区间
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDateTime startUtc = today.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endUtc = today.plusDays(1).atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return tokenUsageMapper.findTodayByAgentUser(agentId, userId, source, sessionId, minId, limit, startUtc, endUtc);
    }

    /**
     * 将本地时间 LocalDateTime 转换为 UTC 时间。
     * create_time 以 UTC 存储，查询前必须转换以保证时间区间语义正确。
     */
    private static LocalDateTime toUtc(LocalDateTime local) {
        if (local == null) return null;
        return local.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
