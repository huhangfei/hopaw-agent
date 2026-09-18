package com.agent.hopaw.infra.event;

import java.time.LocalDateTime;
import java.util.Map;

public class TokenUsageEvent {

    private Long agentId;
    private String modelName;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String userId;
    private String sessionId;
    /** 请求编号：关联本次用户请求，供按请求维度统计用量 */
    private String requestId;
    private String source;

    /**
     * 扩展参数
     */
    private Map<String,Object> exData;
    private LocalDateTime createTime;

    public TokenUsageEvent() {
    }

    public TokenUsageEvent(Long agentId, String modelName, Integer inputTokens, Integer outputTokens,
                           Integer totalTokens, String userId, String sessionId, String requestId, String source, LocalDateTime createTime) {
        this.agentId = agentId;
        this.modelName = modelName;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.userId = userId;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.source = source;
        this.createTime = createTime;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public Map<String, Object> getExData() {
        return exData;
    }

    public void setExData(Map<String, Object> exData) {
        this.exData = exData;
    }
}