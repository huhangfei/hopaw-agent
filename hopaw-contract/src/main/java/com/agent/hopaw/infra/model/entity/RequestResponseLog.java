package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 请求响应日志：记录每次模型调用的完整请求与响应细节，用于问题排查
 */
public class RequestResponseLog {
    private Long id;
    private String sessionId;
    private String requestId;
    private String userId;
    private Long agentId;
    /** 模型名称 */
    private String modelName;
    /** 调用来源（AiModelCallSourceEnum.value） */
    private String source;
    /** 完整请求日志 JSON（参数、消息列表） */
    private String requestJson;
    /** 完整响应日志 JSON（回复、思考、工具调用、Token用量） */
    private String responseJson;
    /** 错误信息（失败时记录） */
    private String errorText;
    /** 状态：success / error */
    private String status;
    /** 输入 Token 数 */
    private Integer inputTokens;
    /** 输出 Token 数 */
    private Integer outputTokens;
    /** 总 Token 数 */
    private Integer totalTokens;
    /** 耗时（毫秒） */
    private Long costMs;
    private LocalDateTime createTime;

    public RequestResponseLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }

    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }

    public String getErrorText() { return errorText; }
    public void setErrorText(String errorText) { this.errorText = errorText; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }

    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }

    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }

    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
