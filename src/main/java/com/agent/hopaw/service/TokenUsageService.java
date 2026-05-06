package com.agent.hopaw.service;

import com.agent.hopaw.mapper.TokenUsageMapper;
import com.agent.hopaw.model.TokenUsage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TokenUsageService {

    private final TokenUsageMapper tokenUsageMapper;
    private final JdbcTemplate jdbcTemplate;

    public TokenUsageService(TokenUsageMapper tokenUsageMapper, JdbcTemplate jdbcTemplate) {
        this.tokenUsageMapper = tokenUsageMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS token_usage (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "agent_id INTEGER," +
                "model_name TEXT," +
                "input_tokens INTEGER DEFAULT 0," +
                "output_tokens INTEGER DEFAULT 0," +
                "total_tokens INTEGER DEFAULT 0," +
                "user_id TEXT," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")");
    }

    public void save(TokenUsage tokenUsage) {
        if (tokenUsage.getCreateTime() == null) {
            tokenUsage.setCreateTime(LocalDateTime.now());
        }
        tokenUsageMapper.insert(tokenUsage);
    }

    public Map<String, Object> queryPage(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId, int page, int size) {
        int offset = (page - 1) * size;
        List<TokenUsage> list = tokenUsageMapper.findByTimeRange(startTime, endTime, userId, agentId, size, offset);
        long total = tokenUsageMapper.countByTimeRange(startTime, endTime, userId, agentId);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public TokenUsage summary(LocalDateTime startTime, LocalDateTime endTime, String userId, Long agentId) {
        return tokenUsageMapper.summaryByTimeRange(startTime, endTime, userId, agentId);
    }
}
