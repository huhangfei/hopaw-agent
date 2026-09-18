package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.event.TokenUsageEvent;
import com.agent.hopaw.infra.mapper.BizTokenUsageMapper;
import com.agent.hopaw.infra.model.entity.BizTokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目/工作流任务维度 Token 用量服务：
 * 监听 TokenUsageEvent，读取扩展数据（workflowTaskId / workflowTaskProjectId）入库
 */
@Service
public class BizTokenUsageService implements IBizTokenUsageService {

    private static final Logger logger = LoggerFactory.getLogger(BizTokenUsageService.class);

    private final BizTokenUsageMapper bizTokenUsageMapper;

    public BizTokenUsageService(BizTokenUsageMapper bizTokenUsageMapper) {
        this.bizTokenUsageMapper = bizTokenUsageMapper;
    }

    /**
     * 监听 token 用量消息：扩展数据携带任务编号时，写入项目/任务用量表
     */
    @EventListener
    public void onTokenUsageMessage(TokenUsageEvent message) {
        try {
            Map<String, Object> exData = message.getExData();
            if (exData == null || exData.isEmpty()) {
                return;
            }
            Long taskId = toLong(exData.get("workflowTaskId"));
            if (taskId == null) {
                return;
            }
            Long projectId = toLong(exData.get("workflowTaskProjectId"));

            BizTokenUsage usage = new BizTokenUsage();
            usage.setProjectId(projectId);
            usage.setTaskId(taskId);
            usage.setAgentId(message.getAgentId());
            usage.setModelName(message.getModelName());
            usage.setInputTokens(message.getInputTokens());
            usage.setOutputTokens(message.getOutputTokens());
            usage.setTotalTokens(message.getTotalTokens());
            usage.setUserId(message.getUserId());
            usage.setSessionId(message.getSessionId());
            usage.setSource(message.getSource());
            usage.setCreateTime(message.getCreateTime() != null ? message.getCreateTime() : LocalDateTime.now());
            bizTokenUsageMapper.insert(usage);
        } catch (Exception e) {
            logger.error("写入项目/任务 Token 用量失败", e);
        }
    }

    @Override
    public Map<String, Object> getProjectUsage(Long projectId, int limit) {
        BizTokenUsage summary = bizTokenUsageMapper.summaryByProject(projectId);
        List<BizTokenUsage> list = bizTokenUsageMapper.listByProject(projectId, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary);
        result.put("list", list);
        return result;
    }

    @Override
    public Map<String, Object> getTaskUsage(Long taskId, int limit) {
        BizTokenUsage summary = bizTokenUsageMapper.summaryByTask(taskId);
        List<BizTokenUsage> list = bizTokenUsageMapper.listByTask(taskId, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary);
        result.put("list", list);
        return result;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
